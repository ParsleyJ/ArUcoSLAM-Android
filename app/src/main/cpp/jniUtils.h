//
// Created by pj on 19/08/2020.
//

#ifndef ARUCOSLAM_JNIUTILS_H
#define ARUCOSLAM_JNIUTILS_H

#include <jni.h>
#include <android/log.h>

#include <opencv2/core/core.hpp>

template<typename inputArrayT, typename inputElementT, typename outputVectorElementT>
void fromJavaArrayToStdVector(
        JNIEnv *env,
        inputArrayT in,
        const std::function<inputElementT *(JNIEnv *, inputArrayT, jboolean *)> &bufferResolver,
        std::vector<outputVectorElementT> &out,
        int offset = 0,
        int count = -1,
        int collectBy = 1,
        const std::function<void(const inputElementT *, outputVectorElementT &)> &collector =
        [](const inputElementT *i, outputVectorElementT &e) {
            e = *i;
        }) {

    if (count == -1) {
        count = env->GetArrayLength(in);
    }

    assert((count - offset) % collectBy == 0);
    int resultSize = count - offset / collectBy;
    out.reserve(resultSize);

    jboolean isCopy = false;

    inputElementT *buffer = bufferResolver(env, in, &isCopy);

    for (int j = offset; j < resultSize; j += collectBy) {
        outputVectorElementT resultElement;

        collector(buffer + j, resultElement);

        out.push_back(resultElement);
    }
}

void fromjDoubleArrayToVectorOfVec3ds(
        JNIEnv *env,
        jdoubleArray inArray,
        std::vector<cv::Vec3d> &outVectors,
        int offset = 0,
        int count = -1
) {
    fromJavaArrayToStdVector<jdoubleArray, jdouble, cv::Vec3d>(
            env,
            inArray,
            &JNIEnv::GetDoubleArrayElements,
            outVectors,
            0,
            -1,
            3,
            [&](const jdouble *buf, cv::Vec3d &outVec) {
                outVec[0] = buf[0];
                outVec[1] = buf[1];
                outVec[2] = buf[2];
            }
    );
}

#endif //ARUCOSLAM_JNIUTILS_H
