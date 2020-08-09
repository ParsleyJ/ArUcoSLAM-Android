#include <jni.h>
#include <string>
#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>
#include <opencv2/aruco.hpp>
#include <android/log.h>
#include <cmath>

extern "C" JNIEXPORT jstring JNICALL
Java_parsleyj_arucoslam_MainActivity_ndkLibReadyCheck(
        JNIEnv *env,
        jobject /* this */) {
    std::string hello = "JNILibOk";
    return env->NewStringUTF(hello.c_str());
}


cv::Mat *castToMatPtr(jlong addr) {
    return (cv::Mat *) addr;
}

template<typename T>
cv::Ptr<T> jlongToCvPtr(jlong addr) {
    return cv::Ptr<T>((T *) addr);
}

template<typename T>
jlong cvPtrToJlong(cv::Ptr<T> ptr) {
    return (jlong) ptr.get();
}


extern "C"
JNIEXPORT jlong JNICALL
Java_parsleyj_arucoslam_MainActivity_genDictionary(JNIEnv *env, jobject thiz) {
    return cvPtrToJlong(cv::aruco::getPredefinedDictionary(cv::aruco::DICT_6X6_250));
}

extern "C"
JNIEXPORT jlong JNICALL
Java_parsleyj_arucoslam_MainActivity_genCalibrationBoard(JNIEnv *env, jobject thiz, jint markersX,
                                                         jint markersY, jfloat markerLength,
                                                         jfloat markerSeparation,
                                                         jlong ditionaryAddr) {

    cv::Ptr<cv::aruco::Dictionary> dictionary = jlongToCvPtr<cv::aruco::Dictionary>(ditionaryAddr);


    cv::Ptr<cv::aruco::GridBoard> gridboard =
            cv::aruco::GridBoard::create(
                    markersX,
                    markersY,
                    markerLength,
                    markerSeparation,
                    dictionary
            ); // create aruco board
    return cvPtrToJlong(gridboard.staticCast<cv::aruco::Board>());
}




extern "C"
JNIEXPORT void JNICALL
Java_parsleyj_arucoslam_MainActivity_processCameraFrame(
        JNIEnv *env,
        jobject thiz,
        jlong dictAddr,
        jlong cameraMatrixAddr,
        jlong distCoeffsAddr,
        jlong input_mat_addr,
        jlong result_mat_addr
) {
    cv::Ptr<cv::aruco::Dictionary> dictionary = jlongToCvPtr<cv::aruco::Dictionary>(dictAddr);
    cv::Mat inputMat = *castToMatPtr(input_mat_addr);
    cv::Mat resultMat = *castToMatPtr(result_mat_addr);
    cv::Mat cameraMatrix = *castToMatPtr(cameraMatrixAddr);
    cv::Mat distCoeffs = *castToMatPtr(distCoeffsAddr);


    std::vector<cv::Mat> channels(3);
    cv::split(inputMat, channels);
//    cv::Mat tmp;
//    tmp = channels[0];
//    channels[0] = channels[1];
//    channels[1] = channels[2];
//    channels[2] = tmp;
    cv::merge(channels, resultMat);


//    resultMat = inputMat; //apparently does not work
//    inputMat.copyTo(resultMat); //apparently does not work
//    cv::Mat tmpMat(inputMat.rows, inputMat.cols, CV_32FC3);
    cv::cvtColor(inputMat, inputMat, CV_RGBA2RGB);
    std::vector<int> ids;
    std::vector<std::vector<cv::Point2f>> corners;


    __android_log_print(ANDROID_LOG_DEBUG, "AAAAAAAAAAAAAAAAA",
                        "inputMat.channels() == %d", inputMat.channels());
    cv::aruco::detectMarkers(inputMat, dictionary, corners, ids);

    __android_log_print(ANDROID_LOG_DEBUG, "AAAAAAAAAAAAAAAAA",
                        "detectedMarkers == %d", ids.size());

    cv::Mat tmpMat;
//     if at least one marker detected
    cv::cvtColor(resultMat, tmpMat, CV_RGBA2RGB);

    if (!ids.empty()) {
        __android_log_print(ANDROID_LOG_DEBUG, "AAAAAAAAAAAAAAAAA",
                            "DRAWING DETECTORS");
        cv::aruco::drawDetectedMarkers(tmpMat, corners, ids);
    }

//
    std::vector<cv::Vec3d> rvecs, tvecs;
    cv::aruco::estimatePoseSingleMarkers(
            corners, 0.05, cameraMatrix, distCoeffs, rvecs, tvecs);
    for (int i = 0; i < rvecs.size(); i++) {
        auto rvec = rvecs[i];
        auto tvec = tvecs[i];
        cv::aruco::drawAxis(tmpMat, cameraMatrix, distCoeffs, rvec, tvec, 0.1);
    }

    cv::cvtColor(tmpMat, resultMat, CV_RGB2RGBA);

    __android_log_print(ANDROID_LOG_VERBOSE, "AAAAAAAAAAAAAAAAA",
                        "resultMat.size() == %d x %d", resultMat.size().width,
                        resultMat.size().height);
}





extern "C"
JNIEXPORT int JNICALL
Java_parsleyj_arucoslam_NativeMethods_detectCalibrationCorners(
        JNIEnv *env,
        jclass clazz,
        jlong input_mat_addr,
        jlong dictAddr,
        jobjectArray cornersPoints,
        jintArray idsVect,
        jintArray size,
        jint maxMarkers
) {


    auto image = *castToMatPtr(input_mat_addr);
    auto dictionary = jlongToCvPtr<cv::aruco::Dictionary>(dictAddr);

    std::vector<int> ids;
    std::vector<std::vector<cv::Point2f>> corners, rejected;

    cv::aruco::detectMarkers(image, dictionary, corners, ids,
                             cv::aruco::DetectorParameters::create(), rejected);

    if (ids.empty()) {
        return 0;
    }

    for (int csi = 0; csi < fmin(corners.size(), maxMarkers); csi++) {
        std::vector<cv::Point2f> &cornerSet = corners[csi];

        auto fourCorners = env->NewFloatArray(8);
        const cv::Ptr<cv::Point2f> &ptr = cv::Ptr<cv::Point2f>(cornerSet.data());

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
        jlong dict_addr,
        jlong calib_board_addr,
        jobjectArray collected_corners,
        jobjectArray collectedIDs,
        jint size_rows,
        jint size_cols,
        jlongArray results_addresses
) {

    auto dictionary = jlongToCvPtr<cv::aruco::Dictionary>(dict_addr);
    auto board = jlongToCvPtr<cv::aruco::Board>(calib_board_addr);


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
        __android_log_print(ANDROID_LOG_ERROR, "native-lib.cpp",
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

    jlong *resultAddresses = env->GetLongArrayElements(results_addresses, &isNotCopy);
    // calibrate camera
    repError = cv::aruco::calibrateCameraAruco(
            allCornersConcatenated,
            allIdsConcatenated,
            markerCounterPerFrame,
            board,
            imgSize,
            *castToMatPtr(resultAddresses[0]),
            *castToMatPtr(resultAddresses[1]),
            rvecs,
            tvecs,
            0);


    return repError;

}