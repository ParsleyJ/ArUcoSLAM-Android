//
// Created by pj on 19/08/2020.
//

#ifndef ARUCOSLAM_UTILS_H
#define ARUCOSLAM_UTILS_H

#include <jni.h>
#include <android/log.h>

#include <opencv2/core/core.hpp>


#define declareAndInitMat(T, varName, rows, cols, cvType, ...) cv::Mat  varName = (cv::Mat_<T>(rows, cols, cvType) << __VA_ARGS__)

//TODO getCalibSizeRatio, angleRatio from activity
constexpr double calibSizeRatio = (480.0 / 720.0); //(864.0 / 1280.0)

auto min(int a, int b) -> int {
    return a <= b ? a : b;
}


cv::Mat *castToMatPtr(jlong addr) {
    return (cv::Mat *) addr;
}


template<typename T>
void randomSubset(
        const std::vector<T> &set,
        std::vector<T> &subset,
        size_t subsetSize
) {
    subset = set;
    while (subset.size() > subsetSize) {
        subset.erase(subset.begin() + rand() % subset.size());
    }
}

void logCameraParameters(const char *tag, const cv::Mat &cameraMatrix, const cv::Mat &distCoeffs) {
    std::ostringstream a, b;
    a << cameraMatrix << std::endl;
    __android_log_print(ANDROID_LOG_DEBUG, tag,
                        "cameraMatrix == \n%s\n", a.str().c_str());
    b << distCoeffs << std::endl;
    __android_log_print(ANDROID_LOG_DEBUG, tag,
                        "distCoeffics == \n%s\n", b.str().c_str());
}


#endif //ARUCOSLAM_UTILS_H
