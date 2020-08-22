//
// Created by pj on 19/08/2020.
//

#ifndef ARUCOSLAM_UTILS_H
#define ARUCOSLAM_UTILS_H

#include <jni.h>
#include <android/log.h>

#include <opencv2/core/core.hpp>


#define declareAndInitMat(T, varName, rows, cols, cvType, ...) cv::Mat  varName = (cv::Mat_<T>(rows, cols, cvType) << __VA_ARGS__)


/**
 * Custom definition of the "parallel for" construct which implements the fork-join paradigm.
 *
 * Usage:
 * p_for(i, iterations){
 *      ...
 * };
 *
 * This is equivalent to:
 *
 * for(int i = 0; i < iterations; i++) {
 *      ...
 * }
 *
 * but with the iterations executed in a pool of threads managed by OpenCV.
 * The binary operator |= has been overloaded in order to accept an integer and a lambda to
 * implement this.
 */
#define p_for(varName, howManyIterations) (howManyIterations) |= [&](int (varName))

/**
 * Operator overloading exploited to implement the "parallel for" construct. See the comment before #define p_for(...)
 */
void operator|=(int numberOfIterations, const std::function<void(int)> &lambda){
    cv::parallel_for_(cv::Range(0, numberOfIterations), [&](const cv::Range &range) {
        for (int x = range.start; x < range.end; x++) {
            lambda(x);
        }
    });
}


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
