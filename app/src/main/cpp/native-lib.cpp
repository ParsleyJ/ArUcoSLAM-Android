

#include <jni.h>
#include <android/log.h>

#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>
#include <opencv2/aruco.hpp>
#include <opencv2/aruco/charuco.hpp>

#include "utils.h"
#include "jniUtils.h"
#include "positionRansac.h"
#include "map2d.h"
#include "opencv-extensions.h"

#include <opencv2/calib3d.hpp>
#include <string>
#include <sstream>
#include <cmath>
#include <memory>


extern "C"
JNIEXPORT jint JNICALL
Java_parsleyj_arucoslam_NativeMethods_detectMarkers(
        JNIEnv *env,
        jclass,
        jlong cameraMatrixAddr, // in
        jlong distCoeffsAddr, // in
        jlong inputMatAddr, // in
        jlong resultMatAddr, // in
        jdouble markerLength, // in
        jint maxMarkers, // in
        jintArray detectedIDsVect, // out
        jdoubleArray outrvecs, // out
        jdoubleArray outtvecs // out
) {
    auto dictionary = cv::aruco::getPredefinedDictionary(cv::aruco::DICT_6X6_250);
    cv::Mat inputMat = *castToMatPtr(inputMatAddr);
    cv::Mat resultMat = *castToMatPtr(resultMatAddr);
    cv::Mat cameraMatrix = *castToMatPtr(cameraMatrixAddr);
    cv::Mat distCoeffs = *castToMatPtr(distCoeffsAddr);


    logCameraParameters("native-lib:processCameraFrame", cameraMatrix, distCoeffs);

    std::vector<cv::Mat> channels(3);
    cv::split(inputMat, channels);
    cv::merge(channels, resultMat);

    cv::cvtColor(inputMat, inputMat, CV_RGBA2GRAY);
    std::vector<int> ids;
    std::vector<std::vector<cv::Point2f>> corners;

    cv::aruco::detectMarkers(inputMat, dictionary, corners, ids,
                             cv::aruco::DetectorParameters::create(), cv::noArray(), cameraMatrix,
                             distCoeffs);
    cv::Mat tmpMat;
    cv::cvtColor(resultMat, tmpMat, CV_RGBA2RGB);

    cv::aruco::drawDetectedMarkers(tmpMat, corners, ids);


    std::vector<cv::Vec3d> rvecs, tvecs;
    cv::aruco::estimatePoseSingleMarkers(
            corners, markerLength,
            cameraMatrix, distCoeffs,
            rvecs, tvecs
    );


    for (int i = 0; i < min(int(rvecs.size()), maxMarkers); i++) {
        auto rvec = rvecs[i];
        auto tvec = tvecs[i];

        env->SetIntArrayRegion(detectedIDsVect, i, 1, &ids[i]);
        for (int vi = 0; vi < 3; vi++) {
            env->SetDoubleArrayRegion(outrvecs, i * 3 + vi, 1, &rvec[vi]);
            env->SetDoubleArrayRegion(outtvecs, i * 3 + vi, 1, &tvec[vi]);
        }

    }

    cv::cvtColor(tmpMat, resultMat, CV_RGB2RGBA);

    return ids.size();
}



extern "C"
JNIEXPORT jint JNICALL
Java_parsleyj_arucoslam_NativeMethods_estimateCameraPosition(
        JNIEnv *env,
        jclass,
        jlong cameraMatrixAddr,
        jlong distCoeffsAddr,
        jlong inputMatAddr,
        jintArray fixed_markers,
        jdoubleArray fixed_rvects,
        jdoubleArray fixed_tvects,
        jdoubleArray fixedMarkerConfidences,
        jdouble fixedLenght,
        jint foundPosesCount,
        jintArray inMarkers,
        jdoubleArray in_rvects,
        jdoubleArray in_tvects,
        jdoubleArray outRvec,
        jdoubleArray outTvec
) {
    cv::Mat inputMat = *castToMatPtr(inputMatAddr);
    cv::Mat cameraMatrix = *castToMatPtr(cameraMatrixAddr);
    cv::Mat distCoeffs = *castToMatPtr(distCoeffsAddr);

    std::vector<int> fixedMarkersIDs;
    std::vector<cv::Vec3d> fixedMarkersTvecs;
    std::vector<cv::Vec3d> fixedMarkersRvecs;


    pushJavaArrayToStdVector<jintArray, jint, int>(
            env,
            fixed_markers,
            &JNIEnv::GetIntArrayElements,
            fixedMarkersIDs
    );

    pushjDoubleArrayToVectorOfVec3ds(env, fixed_tvects, fixedMarkersTvecs);
    pushjDoubleArrayToVectorOfVec3ds(env, fixed_rvects, fixedMarkersRvecs);

    std::unordered_map<int, double> markerConfidenceMap;
    populateMapFromJavaArrays<jintArray, jdoubleArray, int, double>(
            env,
            fixed_markers,
            fixedMarkerConfidences,
            &JNIEnv::GetIntArrayElements,
            &JNIEnv::GetDoubleArrayElements,
            markerConfidenceMap
    );

    std::vector<int> foundMarkersIDs;
    std::vector<cv::Vec3d> foundMarkersTvecs;
    std::vector<cv::Vec3d> foundMarkersRvecs;
    pushJavaArrayToStdVector<jintArray, jint, int>(
            env,
            inMarkers,
            &JNIEnv::GetIntArrayElements,
            foundMarkersIDs,
            0,
            foundPosesCount
    );

    pushjDoubleArrayToVectorOfVec3ds(env, in_tvects, foundMarkersTvecs, 0, foundPosesCount);
    pushjDoubleArrayToVectorOfVec3ds(env, in_rvects, foundMarkersRvecs, 0, foundPosesCount);

    std::vector<cv::Vec3d> positionRvecs;
    std::vector<cv::Vec3d> positionTvecs;
    std::vector<double> positionConfidences;
    cv::Mat tmpMat;
    cv::cvtColor(inputMat, tmpMat, CV_RGBA2RGB);

    draw2DBoxFrame(tmpMat);


//    for (int i = 0; i < foundMarkersIDs.size(); i++) {
    p_for(i, foundMarkersIDs.size()) {
        int foundMarkerID = foundMarkersIDs[i];
        auto findFixedMarkerIndex = std::find(fixedMarkersIDs.begin(), fixedMarkersIDs.end(),
                                              foundMarkerID);
        double x, y, theta;
        if (findFixedMarkerIndex != fixedMarkersIDs.end()) {
            int fixedMarkerIndex = std::distance(fixedMarkersIDs.begin(), findFixedMarkerIndex);

            cv::Vec3d recomputedTvec;
            cv::Vec3d recomputedRvec;


            cv::composeRT(
                    // Transformation to switch from room's coord sys to marker's coord sys
                    fixedMarkersRvecs[fixedMarkerIndex], fixedMarkersTvecs[fixedMarkerIndex],
                    // Transformation to switch from marker's coord sys to camera's coord sys
                    foundMarkersRvecs[i], foundMarkersTvecs[i],
                    // (result) Transf to change from room's coord sys to camera's coord sys
                    recomputedRvec, recomputedTvec
            );

            double markerConfidence = 0.0;
            if (markerConfidenceMap.find(fixedMarkersIDs[fixedMarkerIndex]) != markerConfidenceMap.end()) {
                markerConfidence = markerConfidenceMap[fixedMarkersIDs[fixedMarkerIndex]];
            }

            cv::aruco::drawAxis(tmpMat, cameraMatrix, distCoeffs,
                                recomputedRvec, recomputedTvec, (float) fixedLenght);

            p_for_criticalSectionBegin

                positionTvecs.push_back(recomputedTvec);
                positionRvecs.push_back(recomputedRvec);
                positionConfidences.push_back(markerConfidence);

            p_for_criticalSectionEnd


            fromRTvectsTo2Dpose(recomputedRvec, recomputedTvec, x, y, theta);
            drawObjectPosition(tmpMat, x, y, theta,
                               cv::Scalar(100, 0, 0), cv::MarkerTypes::MARKER_TILTED_CROSS, 5,
                               10.0, cv::Scalar(100, 100, 100));

            fromRTvectsTo2Dpose(fixedMarkersRvecs[fixedMarkerIndex],
                                fixedMarkersTvecs[fixedMarkerIndex],
                                x, y, theta);


            drawObjectPosition(tmpMat, x, y, theta,
                               cv::Scalar(0, 255, 0), cv::MarkerTypes::MARKER_SQUARE, 5,
                               0.0);
        }
    };

    cv::cvtColor(tmpMat, inputMat, CV_RGB2RGBA);

    int inliersCount;
    cv::Vec3d modelRvec, modelTvec;
    inliersCount = fitPositionModel(positionRvecs, positionTvecs,
                                    modelRvec, modelTvec
                                    , positionConfidences //TODO
                                    );

    fromVec3dTojDoubleArray(env, modelRvec, outRvec);
    fromVec3dTojDoubleArray(env, modelTvec, outTvec);

    double estimatedX, estimatedY, estimatedTheta;
    fromRTvectsTo2Dpose(modelRvec, modelTvec, estimatedX, estimatedY, estimatedTheta);

    std::ostringstream a;
    a << "INLIERS=" << inliersCount;
    int side = inputMat.rows / 2;
    cv::Point2f topLeftCorner = cv::Point2f(inputMat.cols - side, inputMat.rows - side);
    cv::putText(inputMat, a.str(), topLeftCorner + cv::Point2f(0, -30),
                CV_FONT_HERSHEY_COMPLEX_SMALL, 1.0,
                cv::Scalar(0, 0, 255));


    drawObjectPosition(inputMat, estimatedX, estimatedY, estimatedTheta);
    drawCameraRoll(inputMat, modelRvec[1], 30);
    return inliersCount;
}


extern "C"
JNIEXPORT void JNICALL
Java_parsleyj_arucoslam_NativeMethods_composeRT(
        JNIEnv *env,
        jclass clazz,
        jdoubleArray in_rvec1,
        jdoubleArray in_tvec1,
        jdoubleArray in_rvec2,
        jdoubleArray in_tvec2,
        jdoubleArray out_rvec,
        jdoubleArray out_tvec
) {
    cv::Vec3d rvec1, tvec1, rvec2, tvec2, rvec, tvec;
    fromjDoubleArrayToVec3d(env, in_rvec1, rvec1);
    fromjDoubleArrayToVec3d(env, in_tvec1, tvec1);
    fromjDoubleArrayToVec3d(env, in_rvec2, rvec2);
    fromjDoubleArrayToVec3d(env, in_tvec2, tvec2);
    cv::composeRT(
            rvec1,
            tvec1,
            rvec2,
            tvec2,
            rvec,
            tvec
    );
    fromVec3dTojDoubleArray(env, tvec, out_tvec);
    fromVec3dTojDoubleArray(env, rvec, out_rvec);
}