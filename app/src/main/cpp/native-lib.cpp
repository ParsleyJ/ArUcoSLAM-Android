#pragma clang diagnostic push
#pragma ide diagnostic ignored "hicpp-signed-bitwise"

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

#include <opencv2/calib3d.hpp>
#include <string>
#include <sstream>
#include <cmath>
#include <memory>

// X -> blue
// Y -> green
// Z -> red


extern "C"
JNIEXPORT jint JNICALL
Java_parsleyj_arucoslam_NativeMethods_processCameraFrame(
        JNIEnv *env,
        jclass,
        jlong cameraMatrixAddr,
        jlong distCoeffsAddr,
        jlong input_mat_addr,
        jlong result_mat_addr,
        jint maxMarkers,
        jintArray detectedIDsVect,
        jdoubleArray outrvecs,
        jdoubleArray outtvecs
) {
    auto dictionary = cv::aruco::getPredefinedDictionary(cv::aruco::DICT_6X6_250);
    cv::Mat inputMat = *castToMatPtr(input_mat_addr);
    cv::Mat resultMat = *castToMatPtr(result_mat_addr);
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

    if (!ids.empty()) {
        cv::aruco::drawDetectedMarkers(tmpMat, corners, ids);
    }

    std::vector<cv::Vec3d> rvecs, tvecs;
    cv::aruco::estimatePoseSingleMarkers(
            corners,
            0.08,
            cameraMatrix,
            distCoeffs,
            rvecs,
            tvecs
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
        jint foundPosesCount,
        jintArray inMarkers,
        jdoubleArray in_rvects,
        jdoubleArray in_tvects,
        jdoubleArray previous2d_positions,
        jdoubleArray previous2d_orientations,
        jdoubleArray new_position
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
    cv::Mat tmpMat;
    cv::cvtColor(inputMat, tmpMat, CV_RGBA2RGB);

    draw2DBoxFrame(tmpMat);

    for (int i = 0; i < foundMarkersIDs.size(); i++) {
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

            cv::aruco::drawAxis(tmpMat, cameraMatrix, distCoeffs,
                                recomputedRvec, recomputedTvec, 0.08);

            positionTvecs.push_back(recomputedTvec);
            positionRvecs.push_back(recomputedRvec);


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
    }
    cv::cvtColor(tmpMat, inputMat, CV_RGB2RGBA);

    int inliersCount;
    cv::Vec3d modelRvec, modelTvec;
    inliersCount = fitPositionModel(positionRvecs, positionTvecs,
                                    modelRvec, modelTvec);


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
    return inliersCount;
}


extern "C"
JNIEXPORT int JNICALL
Java_parsleyj_arucoslam_NativeMethods_detectCalibrationCorners(
        JNIEnv *env,
        jclass clazz,
        jlong input_mat_addr,
        jobjectArray cornersPoints,
        jintArray idsVect,
        jintArray size,
        jint maxMarkers
) {

    auto image = *castToMatPtr(input_mat_addr);
    auto dictionary = cv::aruco::getPredefinedDictionary(cv::aruco::DICT_6X6_250);


    std::vector<int> ids;
    std::vector<std::vector<cv::Point2f>> corners;

    cv::aruco::detectMarkers(image, dictionary, corners, ids);


    if (ids.empty()) {
        __android_log_print(ANDROID_LOG_VERBOSE, "native-lib.cpp:detectCalibrationCorners",
                            "Empty ids!");
        return 0;
    }


    __android_log_print(ANDROID_LOG_VERBOSE, "native-lib.cpp:detectCalibrationCorners",
                        "ids.size == %d", ids.size());

    for (int csi = 0; csi < fmin(corners.size(), maxMarkers); csi++) {
        std::vector<cv::Point2f> &cornerSet = corners[csi];
        auto fourCorners = env->NewFloatArray(8);

        for (int i = 0; i < 8; i += 2) {
            cv::Point2f &point = cornerSet[i];
            env->SetFloatArrayRegion(fourCorners, i, 1, &point.x);
            env->SetFloatArrayRegion(fourCorners, i + 1, 1, &point.y);
        }

        env->SetObjectArrayElement(cornersPoints, csi, fourCorners);
    }

    for (int i = 0; i < fmin(ids.size(), maxMarkers); i++) {
        env->SetIntArrayRegion(idsVect, i, 1, &ids[i]);
    }


    env->SetIntArrayRegion(size, 0, 1, &image.rows);
    env->SetIntArrayRegion(size, 1, 1, &image.cols);

    return ids.size();
}



extern "C"
JNIEXPORT jdouble JNICALL
Java_parsleyj_arucoslam_NativeMethods_calibrate(
        JNIEnv *env,
        jclass clazz,
        jobjectArray collected_corners,
        jobjectArray collectedIDs,
        jint size_rows,
        jint size_cols,
        jlong camMatrixAddr,
        jlong distCoeffsAddr
) {

    auto dictionary = cv::aruco::getPredefinedDictionary(cv::aruco::DICT_6X6_250);
    cv::Ptr<cv::aruco::GridBoard> gridboard = cv::aruco::GridBoard::create(
            8, 5, 500, 100,
            dictionary
    );
    auto board = gridboard.staticCast<cv::aruco::Board>();

    std::vector<std::vector<std::vector<cv::Point2f>>> allCorners;
    std::vector<std::vector<int>> allIds;

    jboolean isNotCopy = false;
    for (int ci = 0; ci < env->GetArrayLength(collected_corners); ci++) {
        auto frameCorners = jobjectArray(env->GetObjectArrayElement(
                collected_corners, ci));
        std::vector<std::vector<cv::Point2f>> frameCornersExtracted;
        for (int ci2 = 0; ci2 < env->GetArrayLength(frameCorners); ci2++) {

            float *markerCorners = env->GetFloatArrayElements(
                    jfloatArray(env->GetObjectArrayElement(
                            frameCorners, ci2
                    )),
                    &isNotCopy);
            std::vector<cv::Point2f> markerCornersExtracted;
            for (int ci3 = 0; ci3 < 8; ci3 += 2) {
                markerCornersExtracted.emplace_back(markerCorners[ci3], markerCorners[ci3 + 1]);
            }
            frameCornersExtracted.push_back(markerCornersExtracted);
        }

        allCorners.push_back(frameCornersExtracted);
    }

    for (int i = 0; i < env->GetArrayLength(collectedIDs); i++) {
        auto intArray = jintArray(env->GetObjectArrayElement(
                collectedIDs, i
        ));
        int *frameIDs = env->GetIntArrayElements(
                intArray,
                &isNotCopy);
        jsize length = env->GetArrayLength(intArray);
        std::vector<int> frameIDsExtracted(length);
        for (int i2 = 0; i2 < length; i2++) {
            frameIDsExtracted.push_back(frameIDs[i2]);
        }
        allIds.push_back(frameIDsExtracted);
    }

    cv::Size imgSize(size_cols, size_rows);
    if (allIds.empty()) {
        __android_log_print(ANDROID_LOG_ERROR, "native-lib.cpp:calibrate",
                            "Not enough captures for calibration");
        return 0.0;
    }
    std::vector<cv::Mat> rvecs, tvecs;
    double repError;
    // prepare data for calibration
    std::vector<std::vector<cv::Point2f>> allCornersConcatenated;
    std::vector<int> allIdsConcatenated;
    std::vector<int> markerCounterPerFrame;
    markerCounterPerFrame.reserve(allCorners.size());
    for (unsigned int i = 0; i < allCorners.size(); i++) {
        markerCounterPerFrame.push_back((int) allCorners[i].size());
        for (unsigned int j = 0; j < allCorners[i].size(); j++) {
            allCornersConcatenated.push_back(allCorners[i][j]);
            allIdsConcatenated.push_back(allIds[i][j]);
        }
    }

    cv::Mat cameraMatrix = *castToMatPtr(camMatrixAddr);
    cv::Mat distCoeffs = *castToMatPtr(distCoeffsAddr);
    // calibrate camera
    repError = cv::aruco::calibrateCameraAruco(
            allCornersConcatenated,
            allIdsConcatenated,
            markerCounterPerFrame,
            board,
            imgSize,
            cameraMatrix,
            distCoeffs,
            rvecs,
            tvecs,
            0);


    return repError;

}

extern "C"
JNIEXPORT jdouble JNICALL
Java_parsleyj_arucoslam_NativeMethods_calibrateChArUco(
        JNIEnv *env,
        jclass clazz,
        jobjectArray collected_corners,
        jobjectArray collectedIDs,
        jlongArray collectedFramesAddresses,
        jint size_rows,
        jint size_cols,
        jlong camMatrixAddr,
        jlong distCoeffsAddr
) {
    auto dictionary = cv::aruco::getPredefinedDictionary(cv::aruco::DICT_6X6_250);


    cv::Ptr<cv::aruco::CharucoBoard> charucoboard =
            cv::aruco::CharucoBoard::create(
                    8,
                    5,
                    300,
                    200,
                    dictionary);
    cv::Ptr<cv::aruco::Board> board = charucoboard.staticCast<cv::aruco::Board>();

    std::vector<std::vector<std::vector<cv::Point2f>>> allCorners;
    std::vector<std::vector<int>> allIds;
    std::vector<cv::Mat> allFrames;

    jboolean isNotCopy = false;
    for (int ci = 0; ci < env->GetArrayLength(collected_corners); ci++) {
        auto frameCorners = jobjectArray(env->GetObjectArrayElement(
                collected_corners, ci));
        std::vector<std::vector<cv::Point2f>> frameCornersExtracted;
        for (int ci2 = 0; ci2 < env->GetArrayLength(frameCorners); ci2++) {

            float *markerCorners = env->GetFloatArrayElements(
                    jfloatArray(env->GetObjectArrayElement(
                            frameCorners, ci2
                    )),
                    &isNotCopy);
            std::vector<cv::Point2f> markerCornersExtracted;
            for (int ci3 = 0; ci3 < 8; ci3 += 2) {
                markerCornersExtracted.emplace_back(markerCorners[ci3], markerCorners[ci3 + 1]);
            }
            frameCornersExtracted.push_back(markerCornersExtracted);
        }

        allCorners.push_back(frameCornersExtracted);
    }

    for (int i = 0; i < env->GetArrayLength(collectedIDs); i++) {
        auto intArray = jintArray(env->GetObjectArrayElement(
                collectedIDs, i
        ));
        int *frameIDs = env->GetIntArrayElements(
                intArray,
                &isNotCopy);
        jsize length = env->GetArrayLength(intArray);
        std::vector<int> frameIDsExtracted(length);
        for (int i2 = 0; i2 < length; i2++) {
            frameIDsExtracted.push_back(frameIDs[i2]);
        }
        allIds.push_back(frameIDsExtracted);
    }


    jsize nFrames = env->GetArrayLength(collectedFramesAddresses);
    jlong *framesAddresses = env->GetLongArrayElements(
            collectedFramesAddresses,
            &isNotCopy
    );
    allFrames.reserve(nFrames);
    for (int i = 0; i < nFrames; i++) {
        allFrames.push_back(*castToMatPtr(framesAddresses[i]));
    }

    cv::Size imgSize(size_cols, size_rows);
    if (allIds.empty()) {
        __android_log_print(ANDROID_LOG_ERROR, "native-lib.cpp:calibrateChArUco",
                            "Not enough captures for calibration");
        return 0.0;
    }


    std::vector<cv::Mat> allCharucoCorners;
    std::vector<cv::Mat> allCharucoIds;
    std::vector<cv::Mat> filteredImages;
    allCharucoCorners.reserve(nFrames);
    allCharucoIds.reserve(nFrames);

    cv::Mat cameraMatrix = *castToMatPtr(camMatrixAddr);
    cv::Mat distCoeffs = *castToMatPtr(distCoeffsAddr);



    // prepare data for aruco calibration
    std::vector<std::vector<cv::Point2f>> allCornersConcatenated;
    std::vector<int> allIdsConcatenated;
    std::vector<int> markerCounterPerFrame;
    markerCounterPerFrame.reserve(allCorners.size());
    for (unsigned int i = 0; i < allCorners.size(); i++) {
        markerCounterPerFrame.push_back((int) allCorners[i].size());
        for (unsigned int j = 0; j < allCorners[i].size(); j++) {
            allCornersConcatenated.push_back(allCorners[i][j]);
            allIdsConcatenated.push_back(allIds[i][j]);
        }
    }

    double arucoRepError;
    // calibrate camera
    arucoRepError = cv::aruco::calibrateCameraAruco(
            allCornersConcatenated,
            allIdsConcatenated,
            markerCounterPerFrame,
            board,
            imgSize,
            cameraMatrix,
            distCoeffs,
            cv::noArray(),
            cv::noArray(),
            0);

    __android_log_print(ANDROID_LOG_ERROR, "native-lib.cpp:calibrateChArUco",
                        "aruco rep error = %f", arucoRepError);

    for (int i = 0; i < nFrames; i++) {
        // interpolate using camera parameters
        cv::Mat currentCharucoCorners, currentCharucoIds;
        cv::aruco::interpolateCornersCharuco(allCorners[i], allIds[i], allFrames[i], charucoboard,
                                             currentCharucoCorners, currentCharucoIds, cameraMatrix,
                                             distCoeffs);

        allCharucoCorners.push_back(currentCharucoCorners);
        allCharucoIds.push_back(currentCharucoIds);
        filteredImages.push_back(allFrames[i]);
    }

    if (allCharucoCorners.size() < 4) {
        __android_log_print(ANDROID_LOG_ERROR, "native-lib.cpp:calibrateChArUco",
                            "Not enough corners for calibration");
        return 0;
    }

    double repError;
    std::vector<cv::Mat> rvecs, tvecs;
    // calibrate camera using charuco
    repError = cv::aruco::calibrateCameraCharuco(
            allCharucoCorners,
            allCharucoIds,
            charucoboard,
            imgSize,
            cameraMatrix,
            distCoeffs,
            rvecs,
            tvecs,
            0);

    __android_log_print(ANDROID_LOG_ERROR, "native-lib.cpp:calibrateChArUco",
                        "rep error = %f", repError);

    return repError;
}


#pragma clang diagnostic pop