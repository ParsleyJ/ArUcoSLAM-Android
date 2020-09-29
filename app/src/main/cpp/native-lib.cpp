

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
        jint fixedMarkerCount,
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
            fixedMarkersIDs,
            0,
            fixedMarkerCount
    );

    pushjDoubleArrayToVectorOfVec3ds(
            env,
            fixed_tvects,
            fixedMarkersTvecs,
            0,
            fixedMarkerCount
    );
    pushjDoubleArrayToVectorOfVec3ds(
            env,
            fixed_rvects,
            fixedMarkersRvecs,
            0,
            fixedMarkerCount);

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

//    draw2DBoxFrame(tmpMat);


//    for (int i = 0; i < foundMarkersIDs.size(); i++) {
    p_for(i, foundMarkersIDs.size()) {
        int foundMarkerID = foundMarkersIDs[i];
        auto findFixedMarkerIndex = std::find(fixedMarkersIDs.begin(), fixedMarkersIDs.end(),
                                              foundMarkerID);
//        double x, y, theta;
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
            if (markerConfidenceMap.find(fixedMarkersIDs[fixedMarkerIndex]) !=
                markerConfidenceMap.end()) {
                markerConfidence = markerConfidenceMap[fixedMarkersIDs[fixedMarkerIndex]];
            }

            cv::aruco::drawAxis(tmpMat, cameraMatrix, distCoeffs,
                                recomputedRvec, recomputedTvec, (float) fixedLenght);

            p_for_criticalSectionBegin

                positionTvecs.push_back(recomputedTvec);
                positionRvecs.push_back(recomputedRvec);
                positionConfidences.push_back(markerConfidence);

            p_for_criticalSectionEnd


//            fromRTvectsTo2Dpose(recomputedRvec, recomputedTvec, x, y, theta);
//            drawObjectPosition(tmpMat, x, y, theta,
//                               cv::Scalar(100, 0, 0), cv::MarkerTypes::MARKER_TILTED_CROSS, 5,
//                               10.0, cv::Scalar(100, 100, 100));

//            fromRTvectsTo2Dpose(fixedMarkersRvecs[fixedMarkerIndex],
//                                fixedMarkersTvecs[fixedMarkerIndex],
//                                x, y, theta);
//
//
//            drawObjectPosition(tmpMat, x, y, theta,
//                               cv::Scalar(0, 255, 0), cv::MarkerTypes::MARKER_SQUARE, 5,
//                               0.0);
        }
    };

    cv::cvtColor(tmpMat, inputMat, CV_RGB2RGBA);

    int inliersCount;
    cv::Vec3d cameraRvec, cameraTvec;
    inliersCount = estimateCameraPose(positionRvecs, positionTvecs,
                                      cameraRvec, cameraTvec,
                                      positionConfidences //TODO
    );

    fromVec3dTojDoubleArray(env, cameraRvec, outRvec);
    fromVec3dTojDoubleArray(env, cameraTvec, outTvec);

//    double estimatedX, estimatedY, estimatedTheta;
//    fromRTvectsTo2Dpose(cameraRvec, cameraTvec, estimatedX, estimatedY, estimatedTheta);

    std::ostringstream a;
    a << "INLIERS=" << inliersCount;
    int side = inputMat.rows / 2;
    cv::Point2f topLeftCorner = cv::Point2f(inputMat.cols - side, inputMat.rows - side);
    cv::putText(inputMat, a.str(), topLeftCorner + cv::Point2f(0, -30),
                CV_FONT_HERSHEY_COMPLEX_SMALL, 1.0,
                cv::Scalar(0, 0, 255));


//    drawObjectPosition(inputMat, estimatedX, estimatedY, estimatedTheta);
//    drawCameraRoll(inputMat, cameraRvec[1], 30);
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


void invertRT(
        const cv::Vec3d &inR,
        const cv::Vec3d &inT,
        cv::Vec3d &outR,
        cv::Vec3d &outT
) {
    cv::Mat Rmatrix;
    cv::Rodrigues(inR, Rmatrix);
    cv::Rodrigues(Rmatrix.t(), outR);
    cv::Mat x(-(Rmatrix.t() * cv::Mat(inT)));

    outT[0] = x.at<double>(0, 0);
    outT[1] = x.at<double>(1, 0);
    outT[2] = x.at<double>(2, 0);
}

double cotan(double i) {
    return 1.0 / tan(i);
}

extern "C"
JNIEXPORT void JNICALL
Java_parsleyj_arucoslam_NativeMethods_renderMap(
        JNIEnv *env,
        jclass clazz,
        jdoubleArray marker_rvects,
        jdoubleArray marker_tvects,
        jdoubleArray mapCameraRotation_j,
        jdoubleArray mapCameraTranslation_j,
        jdoubleArray phonePositionRvect_j,
        jdoubleArray phonePositionTvect_j,
        jdouble mapCameraFovX,
        jdouble mapCameraFovY,
        jdouble mapCameraApertureX,
        jdouble mapCameraApertureY,
        jint mapCameraPixelsX,
        jint mapCameraPixelsY,
        jint mapTopLeftCornerX,
        jint mapTopLeftCornerY,
        jlong result_mat_addr
) {
    //TODO define or obtain map camera
    //TODO inputs: fovx, fovy, aperturex, aperturey, pixelsx, pixelsy
    double f_x = mapCameraApertureX / 2.0 * cotan(mapCameraFovX / 2.0);
    double f_y = mapCameraApertureY / 2.0 * cotan(mapCameraFovY / 2.0);
    double c_x = static_cast<double>(mapCameraPixelsX) / 2.0;
    double c_y = static_cast<double>(mapCameraPixelsY) / 2.0;

    cv::Mat mapCameraMatrix = (cv::Mat_<double>(3, 3) <<
                                                      f_x, 0.0, c_x,
            0.0, f_y, c_y,
            0.0, 0.0, 1.0
    );
    cv::Mat emptyDistortionCoeffs = cv::Mat::zeros(1,4,CV_64FC1);

    // Transformation to switch from room's coord sys to camera's coord sys
    cv::Vec3d mapCameraRotation, mapCameraTranslation;
    fromjDoubleArrayToVec3d(env, mapCameraRotation_j, mapCameraRotation);
    fromjDoubleArrayToVec3d(env, mapCameraTranslation_j, mapCameraTranslation);

    cv::Vec3d phonePositionRvect, phonePositionTvect;
    fromjDoubleArrayToVec3d(env, phonePositionRvect_j, phonePositionRvect);
    fromjDoubleArrayToVec3d(env, phonePositionTvect_j, phonePositionTvect);

    std::vector<cv::Vec3d> markersTvecs, markersRvecs;
    pushjDoubleArrayToVectorOfVec3ds(
            env,
            marker_tvects,
            markersTvecs
    );

    pushjDoubleArrayToVectorOfVec3ds(
            env,
            marker_rvects,
            markersRvecs
    );

    std::vector<cv::Point3f> origin;
    origin.emplace_back(0.0, 0.0, 0.0);
    origin.emplace_back(0.1, 0.0, 0.0);
    origin.emplace_back(0.0, 0.1, 0.0);
    origin.emplace_back(0.0, 0.0, 0.1);
    std::vector<cv::Point2f> markersImagePoints;

    p_for(i, markersRvecs.size()) {
//         Transformation to switch from marker's coord sys to room's coord sys
        cv::Vec3d invertedMarkerRvec, invertedMarkerTvec;
        invertRT(markersRvecs[i], markersTvecs[i],
                 invertedMarkerRvec, invertedMarkerTvec);

        // Transformation to switch from marker's coord sys to mapCamera's coord sys
        cv::Vec3d fromMarkerToMapR, fromMarkerToMapT;
        cv::composeRT(
                invertedMarkerRvec, invertedMarkerTvec,
//                markersRvecs[i], markersTvecs[i],
                mapCameraRotation, mapCameraTranslation,
                fromMarkerToMapR, fromMarkerToMapT
        );
        std::vector<cv::Point2f> projectedPoints;
        __android_log_print(ANDROID_LOG_DEBUG, "ABABBABABABBABABABABB", "a");
        cv::projectPoints(
                origin,
                fromMarkerToMapR,
                fromMarkerToMapT,
                mapCameraMatrix,
                emptyDistortionCoeffs,
                projectedPoints
        );
        __android_log_print(ANDROID_LOG_DEBUG, "ABABBABABABBABABABABB", "b");

        p_for_criticalSectionBegin
            markersImagePoints.push_back(projectedPoints[0]);
            markersImagePoints.push_back(projectedPoints[1]);
            markersImagePoints.push_back(projectedPoints[2]);
            markersImagePoints.push_back(projectedPoints[3]);
        p_for_criticalSectionEnd
    };

    cv::Mat imageMat = *castToMatPtr(result_mat_addr);
    cv::Point2f topLeftCorner = cv::Point2f(mapTopLeftCornerX, mapTopLeftCornerY);
    draw2DBoxFrame(imageMat, topLeftCorner);

    for (auto &markerPoint:markersImagePoints) {
        cv::drawMarker(imageMat, topLeftCorner + markerPoint,
                       cv::Scalar(255, 0, 0),
                       cv::MARKER_STAR, 10);
    }

    cv::line(imageMat, topLeftCorner + markersImagePoints[0], topLeftCorner + markersImagePoints[1], cv::Scalar(0, 0, 255), 3);
    cv::line(imageMat, topLeftCorner + markersImagePoints[0], topLeftCorner + markersImagePoints[2], cv::Scalar(0, 255, 0), 3);
    cv::line(imageMat, topLeftCorner + markersImagePoints[0], topLeftCorner + markersImagePoints[3], cv::Scalar(255, 0, 0), 3);


}