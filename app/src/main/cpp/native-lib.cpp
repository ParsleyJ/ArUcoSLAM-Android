#include <jni.h>
#include <string>
#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>
#include <opencv2/aruco.hpp>
#include <android/log.h>

extern "C" JNIEXPORT jstring JNICALL
Java_parsleyj_arucoslam_MainActivity_getHelloString(
        JNIEnv *env,
        jobject /* this */) {
    std::string hello = "JNILibOk";
    return env->NewStringUTF(hello.c_str());
}

jlong unboxLong(JNIEnv *env, jobject boxedLong) {
    auto longBoxClass = env->GetObjectClass(boxedLong);
    return env->GetLongField(boxedLong, env->GetFieldID(
            longBoxClass,
            "value",
            "J"
    ));
}

cv::Mat *unboxMat(JNIEnv *env, jobject boxedAddress) {
    auto longBoxClass = env->GetObjectClass(boxedAddress);
    return (cv::Mat *) env->GetLongField(boxedAddress, env->GetFieldID(
            longBoxClass,
            "value",
            "J"
    ));
}

extern "C"
JNIEXPORT void JNICALL
Java_parsleyj_arucoslam_MainActivity_processCameraFrame(
        JNIEnv *env,
        jobject thiz,
        jobject input_mat_addr,
        jobject result_mat_addr
) {
    cv::Mat inputMat = *(cv::Mat *) unboxLong(env, input_mat_addr);
    cv::Mat resultMat = *(cv::Mat *) unboxLong(env, result_mat_addr);


    //todo move outside
    auto dictionary = cv::aruco::getPredefinedDictionary(cv::aruco::DICT_6X6_250);

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
//        cv::aruco::drawDetectedMarkers(tmpMat, corners, ids);
    }

//    float intrinsicData[9] = {
//            163.4545858698673, 0, 127.5,
//            0, 163.4545858698673, 71.5,
//            0,0,1
//    };
//    float distData[5] = {
//            0.0430674624,
//            -0.124233284
//    };
//    cv::Mat cameraMatrix(3, 3, CV_32F, intrinsicData);
//    cv::Mat distCoeffs(5, 1, CV_32F, distData);
//
//    std::vector<cv::Vec3d> rvecs, tvecs;
//    cv::aruco::estimatePoseSingleMarkers(
//            corners, 0.05, cameraMatrix, distCoeffs, rvecs, tvecs);
//    for (int i = 0; i < rvecs.size(); i++) {
//        auto rvec = rvecs[i];
//        auto tvec = tvecs[i];
//        cv::aruco::drawAxis(tmpMat, cameraMatrix, distCoeffs, rvec, tvec, 0.1);
//    }

    cv::cvtColor(tmpMat, resultMat, CV_RGB2RGBA);

    __android_log_print(ANDROID_LOG_VERBOSE, "AAAAAAAAAAAAAAAAA",
                        "resultMat.size() == %d x %d", resultMat.size().width,
                        resultMat.size().height);
}



extern "C"
JNIEXPORT jdouble JNICALL
Java_parsleyj_arucoslam_MainActivity_calibrate(
        JNIEnv *env,
        jobject thiz,
        jobject input_mats_addr_boxed,
        jobject out_camera_matrix_boxed,
        jobject out_camera_distortion_boxed
) {

    cv::Mat cameraMatrix = *unboxMat(env, out_camera_matrix_boxed);
    cv::Mat cameraDistortion = *unboxMat(env, out_camera_distortion_boxed);
    std::vector<cv::Mat> mats; //TODO get from input
    int markersX = 8;
    int markersY = 5;
    float markerLength = 50;
    float markerSeparation = 20;
    int dictionaryId = 10;
    auto dictionary = cv::aruco::getPredefinedDictionary(dictionaryId);

    cv::Ptr<cv::aruco::GridBoard> gridboard =
            cv::aruco::GridBoard::create(
                    markersX,
                    markersY,
                    markerLength,
                    markerSeparation,
                    dictionary
            ); // create aruco board
    cv::Ptr<cv::aruco::Board> board = gridboard.staticCast<cv::aruco::Board>();

    std::vector<std::vector<std::vector<cv::Point2f>>> allCorners;
    std::vector<std::vector<int>> allIds;
    cv::Size imgSize;

    for(auto& image: mats){
        cv::Mat imageCopy;

        std::vector<int> ids;
        std::vector<std::vector<cv::Point2f>> corners, rejected;

        cv::aruco::detectMarkers(image, dictionary, corners, ids, cv::aruco::DetectorParameters::create(), rejected);

        image.copyTo(imageCopy);

        allCorners.push_back(corners);
        allIds.push_back(ids);
        imgSize = image.size();
    }

    if(allIds.empty()) {
//        cerr << "Not enough captures for calibration" << endl;
        return;
    }


    std::vector<cv::Mat> rvecs, tvecs;
    double repError;


    // prepare data for calibration
    std::vector<std::vector<cv::Point2f>> allCornersConcatenated;
    std::vector<int> allIdsConcatenated;
    std::vector<int> markerCounterPerFrame;
    markerCounterPerFrame.reserve(allCorners.size());
    for(unsigned int i = 0; i < allCorners.size(); i++) {
        markerCounterPerFrame.push_back((int)allCorners[i].size());
        for(unsigned int j = 0; j < allCorners[i].size(); j++) {
            allCornersConcatenated.push_back(allCorners[i][j]);
            allIdsConcatenated.push_back(allIds[i][j]);
        }
    }
    // calibrate camera
    repError = cv::aruco::calibrateCameraAruco(allCornersConcatenated, allIdsConcatenated,
                                           markerCounterPerFrame, board, imgSize, cameraMatrix,
                                           cameraDistortion, rvecs, tvecs, 0);

    return repError;
}