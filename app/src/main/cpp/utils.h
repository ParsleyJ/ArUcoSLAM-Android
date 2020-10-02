//
// Created by pj on 19/08/2020.
//

#ifndef ARUCOSLAM_UTILS_H
#define ARUCOSLAM_UTILS_H

#include <jni.h>
#include <android/log.h>

#include <opencv2/core/core.hpp>
#include <mutex>


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
 *
 * This p_for comes with an "implicit mutex parameter" (__p_for_mutex).
 * Use p_for_criticalSectionBegin e p_for_criticalSectionEnd to delimit a critical section inside
 * the p_for construct.
 */
#define p_for(varName, howManyIterations) (howManyIterations) |= [&](int (varName), std::mutex &__p_for_mutex)

#define p_for_criticalSectionBegin { std::unique_lock<std::mutex> __p_for_ul(__p_for_mutex);

#define p_for_criticalSectionEnd }

/**
 * Operator overloading exploited to implement the "parallel for" construct. See the comment before #define p_for(...)
 */
void operator|=(int numberOfIterations, const std::function<void(int, std::mutex &)> &lambda) {
    std::mutex mut;
    cv::parallel_for_(cv::Range(0, numberOfIterations), [&](const cv::Range &range) {
        for (int x = range.start; x < range.end; x++) {
            lambda(x, mut);
        }
    });
}


auto min(int a, int b) -> int {
    return a <= b ? a : b;
}


cv::Mat *castToMatPtr(jlong addr) {
    return (cv::Mat *) addr;
}

void randomUniqueIndices(
        int originalSize,
        int subsetSize,
        std::vector<int> &out
){
    out = std::vector<int>();
    for (int i = 0; i < originalSize; i++) {
        out.push_back(i);
    }
    while (out.size() > subsetSize) {
        out.erase(out.begin() + rand() % out.size());
    }
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
