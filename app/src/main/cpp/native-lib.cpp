#pragma clang diagnostic push
#pragma ide diagnostic ignored "hicpp-signed-bitwise"

#include <jni.h>
#include <android/log.h>

#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>
#include <opencv2/aruco.hpp>
#include <opencv2/aruco/charuco.hpp>

#include <opencv2/calib3d.hpp>
#include <string>
#include <sstream>
#include <cmath>
#include <memory>

constexpr double calibSizeRatio = (480.0 / 720.0); //(864.0 / 1280.0)

extern "C" JNIEXPORT jstring JNICALL
Java_parsleyj_arucoslam_MainActivity_ndkLibReadyCheck(
        JNIEnv *env,
        jobject /* this */) {
    std::string hello = "JNILibOk";
    return env->NewStringUTF(hello.c_str());
}

auto min(int a, int b) -> int {
    return a <= b ? a : b;
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

cv::Vec3d rotationMatrixToEulerAngles(const cv::Mat &R) {
    double sy = sqrt(
            R.at<double>(0, 0) * R.at<double>(0, 0)
            + R.at<double>(1, 0) * R.at<double>(1, 0)
    );

    bool singular = sy < 1e-6;
    double x, y, z;
    if (!singular) {
        x = atan2(R.at<double>(2, 1), R.at<double>(2, 2));
        y = atan2(-R.at<double>(2, 0), sy);
        z = atan2(R.at<double>(1, 0), R.at<double>(0, 0));
    } else {
        x = atan2(-R.at<double>(1, 2), R.at<double>(1, 1));
        y = atan2(-R.at<double>(2, 0), sy);
        z = 0;
    }
    return cv::Vec3d(x, y, z);
}


/*
 * [ R   T ]-1     [ Rt  -Rt*T ]
 * [ 0   1 ]    =  [  0     1  ]
 */
void invertChangeOfReferenceMatrix(const cv::Mat &inMat, cv::Mat &outMat) {
    cv::Mat R(3, 3, CV_64FC1);
    cv::Rect rArea(0, 0, 3, 3);
    inMat(rArea).copyTo(R);
    cv::Mat T;
    cv::Rect tArea(3, 0, 1, 3);
    cv::Mat t(3, 1, CV_64FC1);
    inMat(tArea).copyTo(t);
    R = R.t();
    t = -R * t;
    R.copyTo(outMat(rArea));
    t.copyTo(outMat(tArea));
    outMat.at<double>(3, 0)
            = outMat.at<double>(3, 1)
            = outMat.at<double>(3, 2)
            = 0.0;
    outMat.at<double>(3, 3) = 1.0;
}


void genChangeOfReferenceMatrix(const cv::Vec3d &tvec, const cv::Vec3d &rvec, cv::Mat &out) {
    double data1[9] = {
            1, 0, 0,
            0, cos(rvec[0]), -sin(rvec[0]),
            0, sin(rvec[0]), cos(rvec[0])
    };
    double data2[9] = {
            cos(rvec[1]), 0, sin(rvec[1]),
            0, 1, 0,
            -sin(rvec[1]), 0, cos(rvec[1])
    };
    double data3[9] = {
            cos(rvec[2]), -sin(rvec[2]), 0,
            sin(rvec[2]), cos(rvec[2]), 0,
            0, 0, 1
    };

    cv::Mat R;
    R = cv::Mat(3, 3, CV_64FC1, data1)
        * cv::Mat(3, 3, CV_64FC1, data2)
        * cv::Mat(3, 3, CV_64FC1, data3);


    for (int r = 0; r < 4; r++) {
        for (int c = 0; c < 4; c++) {
            if (0 <= r && r <= 2) {
                if (0 <= c && c <= 2) {
                    out.at<double>(r, c) = R.at<double>(r, c);
                } else {
                    out.at<double>(r, c) = tvec[r];
                }
            } else {
                if (0 <= c && c <= 2) {
                    out.at<double>(r, c) = 0.0;
                } else {
                    out.at<double>(r, c) = 1.0;
                }
            }
        }
    }
}

void arucoBoardPositions(
        std::vector<cv::Vec3d> &postions
) {
    for (int i = 0; i < 40; i++) {
        int row = i / 8 - 2;
        int col = i % 8 - 3;

    }
}

void computeCentroid(
        const std::vector<cv::Vec3d> &vecs,
        cv::Vec3d &centre
) {
    centre[0] = 0;
    centre[1] = 0;
    centre[2] = 0;
    for (const auto &vect: vecs) {
        centre[0] += vect[0] / double(vecs.size());
        centre[1] += vect[1] / double(vecs.size());
        centre[2] += vect[2] / double(vecs.size());
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

void vectorRansac(
        const std::vector<cv::Vec3d> &vecs,
        cv::Vec3d &foundModel,
        const std::function<double(const cv::Vec3d &, const cv::Vec3d &)> &distanceFunction,
        double maxModelDistance,
        uint maxN,
        int &inliers
) {
    if (vecs.empty()) {
        inliers = 0;
        return;
    }
    if (vecs.size() == 1) {
        inliers = 1;
        foundModel = vecs[0];
        return;
    }
    size_t subSetSize;
    if (vecs.size() <= 4) {
        subSetSize = 1;
    } else if (vecs.size() <= 10) {
        subSetSize = 2;
    } else {
        subSetSize = 5;
    }

    // target probability to get a subset which generates a model without outliers
    double prob = 0.8;

    uint attempts = log(1.0 - prob) / log(1.0 - pow(1.0 - M_E, double(subSetSize)));
    if (attempts == 0) {
        attempts = 1;
    }
    if (attempts > maxN) {
        attempts = maxN;
    }

    cv::Vec3d bestCentroid;
    int bestCount = 0;
    for (int attempt_I = 0; attempt_I < attempts; attempt_I++) {
        std::vector<cv::Vec3d> subset;
        randomSubset(vecs, subset, subSetSize);
        cv::Vec3d centroid;
        computeCentroid(subset, centroid);
        int inlierCount = 0;
        for (const auto &vec:vecs) {
            if (distanceFunction(centroid, vec) <= maxModelDistance) {
                inlierCount++;
            }
        }
        if (attempt_I == 0 || inlierCount > bestCount) {
            bestCentroid = centroid;
            bestCount = inlierCount;
        }
    }

    foundModel = bestCentroid;
    inliers = bestCount;
}


void fromRTvectsTo2Dpose(
        const cv::Vec3d &rVec,
        const cv::Vec3d &tVec,
        double &resultX,
        double &resultY,
        double &resultAngle
) {

    resultAngle = (rVec[0] < 0 ? 1 : -1) * rVec[2] * (90.0 / 130.0);
    resultX = -tVec[0] * calibSizeRatio;
    resultY = tVec[2] * calibSizeRatio;
}


void fitPositionModel(
        const std::vector<cv::Vec3d> &rvecs,
        const std::vector<cv::Vec3d> &tvecs,
        int &inliers,
        double &resultX,
        double &resultY,
        double &resultAngle
) {
    cv::Vec3d tVec;
    cv::Vec3d rVec;
//    vectorRansac(rvecs, rVec, [&](const cv::Vec3d &p1, const cv::Vec3d &p2) {
//        return cv::norm(cv::Vec3d(
//                atan2(sin(p1[0] - p2[0]), cos(p1[0] - p2[0])),
//                atan2(sin(p1[1] - p2[1]), cos(p1[1] - p2[1])),
//                atan2(sin(p1[2] - p2[2]), cos(p1[2] - p2[2]))
//        ));
//    }, 0.05, 100, inliers);
    vectorRansac(tvecs, tVec, [&](const cv::Vec3d &p1, const cv::Vec3d &p2) {
        return cv::norm(p1 - p2);
    }, 0.05, 100, inliers);

//    computeCentroid(tvecs, tVec);
    computeCentroid(rvecs, rVec);

    fromRTvectsTo2Dpose(rVec, tVec, resultX, resultY, resultAngle);
}


extern "C"
JNIEXPORT jint JNICALL
Java_parsleyj_arucoslam_NativeMethods_processCameraFrame(
        JNIEnv *env,
        jclass clazz,
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

    std::ostringstream a, b;
    a << cameraMatrix << std::endl;
    __android_log_print(ANDROID_LOG_DEBUG, "native-lib:processCameraFrame",
                        "cameraMatrix == %s\n", a.str().c_str());
    b << distCoeffs << std::endl;
    __android_log_print(ANDROID_LOG_DEBUG, "native-lib:processCameraFrame",
                        "distCoeffics == %s\n", b.str().c_str());

    std::vector<cv::Mat> channels(3);
    cv::split(inputMat, channels);
    cv::merge(channels, resultMat);

    cv::cvtColor(inputMat, inputMat, CV_RGBA2GRAY);
    std::vector<int> ids;
    std::vector<std::vector<cv::Point2f>> corners;

    __android_log_print(ANDROID_LOG_DEBUG, "native-lib:processCameraFrame",
                        "inputMat.channels() == %d", inputMat.channels());

    cv::aruco::detectMarkers(inputMat, dictionary, corners, ids,
                             cv::aruco::DetectorParameters::create(), cv::noArray(), cameraMatrix,
                             distCoeffs);

    __android_log_print(ANDROID_LOG_DEBUG, "native-lib:processCameraFrame",
                        "detectedMarkers == %d", ids.size());

    cv::Mat tmpMat;
    cv::cvtColor(resultMat, tmpMat, CV_RGBA2RGB);

    if (!ids.empty()) {
        __android_log_print(ANDROID_LOG_DEBUG, "native-lib:processCameraFrame",
                            "DRAWING DETECTORS");
        cv::aruco::drawDetectedMarkers(tmpMat, corners, ids);
    }

    std::vector<cv::Vec3d> rvecs, tvecs;
    cv::aruco::estimatePoseSingleMarkers(
            corners,
            0.05,
            cameraMatrix,
            distCoeffs,
            rvecs,
            tvecs
    );

    for (int i = 0; i < min(int(rvecs.size()), maxMarkers); i++) {
        auto rvec = rvecs[i];
        auto tvec = tvecs[i];

        __android_log_print(ANDROID_LOG_DEBUG, "native-lib:processCameraFrame",
                            "RVEC = [%f, %f, %f]", rvec[0], rvec[1], rvec[2]);
        __android_log_print(ANDROID_LOG_DEBUG, "native-lib:processCameraFrame",
                            "TVEC = [%f, %f, %f]", tvec[0], tvec[1], tvec[2]);

        env->SetIntArrayRegion(detectedIDsVect, i, 1, &ids[i]);
        for (int vi = 0; vi < 3; vi++) {
            env->SetDoubleArrayRegion(outrvecs, i * 3 + vi, 1, &rvec[vi]);
            env->SetDoubleArrayRegion(outtvecs, i * 3 + vi, 1, &tvec[vi]);
        }

//        cv::aruco::drawAxis(tmpMat, cameraMatrix, distCoeffs, rvec, tvec, 0.08);
    }

//    double markerLength = 0.05;
//    double markerSeparation = 0.01;
//    cv::Ptr<cv::aruco::GridBoard> gridboard = cv::aruco::GridBoard::create(
////            markersX,
////            markersY,
////            markerLength,
////            markerSeparation,
//            8, 5, markerLength, markerSeparation,
//            dictionary
//    ); // create aruco board
//    auto board = gridboard.staticCast<cv::aruco::Board>();
//
//    cv::Vec3d rvec(0, 0, 0), tvec(0, 0, 0);
//    int nMarkerContributedToPose = cv::aruco::estimatePoseBoard(corners, ids, board, cameraMatrix,
//                                                                distCoeffs,
//                                                                rvec, tvec);
//    if (nMarkerContributedToPose > 0) {
//        cv::aruco::drawAxis(tmpMat, cameraMatrix, distCoeffs, rvec, tvec, 0.08);
//        rvecs.emplace_back(rvec[0], rvec[1], rvec[2]);
//        tvecs.emplace_back(tvec[0], tvec[1], tvec[2]);
//
//        __android_log_print(ANDROID_LOG_DEBUG, "native-lib:processCameraFrame",
//                            "RVEC = [%f, %f, %f]", rvec[0], rvec[1], rvec[2]);
//        __android_log_print(ANDROID_LOG_DEBUG, "native-lib:processCameraFrame",
//                            "TVEC = [%f, %f, %f]", tvec[0], tvec[1], tvec[2]);
//
//        int zero = 0;
//        env->SetIntArrayRegion(detectedIDsVect, 0, 1, &zero);
//        for (int vi = 0; vi < 3; vi++) {
//            env->SetDoubleArrayRegion(outrvecs, 0 * 3 + vi, 1, &rvec[vi]);
//            env->SetDoubleArrayRegion(outtvecs, 0 * 3 + vi, 1, &tvec[vi]);
//        }
//
//        cv::aruco::drawAxis(tmpMat, cameraMatrix, distCoeffs, rvec, tvec, 0.08);
//
//
//        drawCameraPosition(tmpMat, cameraMatrix, distCoeffs, rvecs, tvecs, 0.01);
//    } else {
//        __android_log_print(ANDROID_LOG_DEBUG, "native-lib:processCameraFrame",
//                            "DID NOT ESTIMATE BOARD POSE!!!!");
//    }


    cv::cvtColor(tmpMat, resultMat, CV_RGB2RGBA);
    __android_log_print(ANDROID_LOG_VERBOSE, "native-lib:processCameraFrame",
                        "resultMat.size() == %d x %d", resultMat.size().width,
                        resultMat.size().height);
    return ids.size();
}

void drawCameraPosition(
        cv::Mat &_image,
        double X,
        double Y,
        double theta,
        bool drawBackground = true
) {
//    cv::Vec3d cameraTVec = cv::Vec3d(0.05, 0.02, 0.20);
//    cv::Vec3d negatedRvec = cv::Vec3d(_rvec[0], _rvec[1], -_rvec[2]);
    // project axis points
//    std::vector<cv::Point3f> axisPoints;
//    axisPoints.emplace_back(0, 0, 0);
//    axisPoints.emplace_back(length, 0, 0);
//    axisPoints.emplace_back(0, length, 0);
//    axisPoints.emplace_back(0, 0, length);
//    std::vector<cv::Point2f> imagePoints;
//    projectPoints(axisPoints, negatedRvec, cameraTVec, _cameraMatrix, _distCoeffs, imagePoints);
//
//     draw axis lines
//    line(_image, imagePoints[0], imagePoints[1], cv::Scalar(0, 0, 255), 3);
//    line(_image, imagePoints[0], imagePoints[2], cv::Scalar(0, 255, 0), 3);
//    line(_image, imagePoints[0], imagePoints[3], cv::Scalar(255, 0, 0), 3);

    int side = _image.rows / 2;
    double areaSide = 2; //meters
    cv::Point2f origin((_image.cols - side / 2), _image.rows - side);
    double aMeterInPixels = (double) side / areaSide;

    if (drawBackground) {
        // draw box
        cv::Point2f topLeftCorner = cv::Point2f(_image.cols - side, _image.rows - side);
        cv::line(_image,
                 topLeftCorner, cv::Point2f(_image.cols, _image.rows - side),
                 cv::Scalar(0, 255, 0), 3);
        cv::line(_image,
                 topLeftCorner, cv::Point2f(_image.cols - side, _image.rows),
                 cv::Scalar(0, 255, 0), 3);
        cv::rectangle(_image, topLeftCorner, cv::Point(_image.cols, _image.rows),
                      cv::Scalar(0, 0, 0), cv::FILLED);

        cv::drawMarker(_image, origin,
                       cv::Scalar(255, 0, 0), cv::MARKER_SQUARE, 5);
    }

    double Xnew = X * cos(theta) - Y * sin(theta);
    double Ynew = X * sin(theta) + Y * cos(theta);
    __android_log_print(ANDROID_LOG_DEBUG, "BOHBOHBOHBOHBOHBOHBOHBOH",
                        "position=(%f, %f); theta=%f",
                        Xnew * aMeterInPixels, Ynew * aMeterInPixels, theta);
    cv::Point2f position = origin + cv::Point2f(Xnew * aMeterInPixels, Ynew * aMeterInPixels);
    cv::drawMarker(_image, position,
                   cv::Scalar(0, 255, 0), cv::MARKER_TILTED_CROSS, 10);


    double arrowHeadX = position.x + 30.0 * cos(theta + (3.0 / 2.0) * CV_PI);
    double arrowHeadY = position.y + 30.0 * sin(theta + (3.0 / 2.0) * CV_PI);
    cv::arrowedLine(_image, position, cv::Point2f(arrowHeadX, arrowHeadY),
                    cv::Scalar(255, 255, 255));

}


extern "C"
JNIEXPORT jint JNICALL
Java_parsleyj_arucoslam_NativeMethods_estimateCameraPosition(
        JNIEnv *env,
        jclass clazz,
        jlong cameraMatrixAddr,
        jlong distCoeffsAddr,
        jlong inputMatAddr,
        jintArray fixed_markers,
        jdoubleArray fixed_rvects,
        jdoubleArray fixed_tvects,
        jintArray inMarkers,
        jdoubleArray in_rvects,
        jdoubleArray in_tvects,
        jdoubleArray previous2d_positions,
        jdoubleArray previous2d_orientations,
        jdoubleArray new_position
) {
    cv::Mat inputMat = *castToMatPtr(inputMatAddr);
    const cv::Mat &resultMat = inputMat;
    cv::Mat cameraMatrix = *castToMatPtr(cameraMatrixAddr);
    cv::Mat distCoeffs = *castToMatPtr(distCoeffsAddr);

    std::vector<int> fixedMarkersIDs;
    std::vector<cv::Vec3d> fixedMarkersTvecs;
    std::vector<cv::Vec3d> fixedMarkersRvecs;
    int fixedMarkersSize = env->GetArrayLength(fixed_markers);
    fixedMarkersIDs.reserve(fixedMarkersSize);
    jboolean isNotCopy = false;
    for (int i = 0; i < fixedMarkersSize; i++) {
        fixedMarkersIDs.push_back(env->GetIntArrayElements(fixed_markers, &isNotCopy)[i]);
        fixedMarkersRvecs.emplace_back(
                env->GetDoubleArrayElements(fixed_rvects, &isNotCopy)[i * 3],
                env->GetDoubleArrayElements(fixed_rvects, &isNotCopy)[i * 3 + 1],
                env->GetDoubleArrayElements(fixed_rvects, &isNotCopy)[i * 3 + 2]
        );
        fixedMarkersTvecs.emplace_back(
                env->GetDoubleArrayElements(fixed_tvects, &isNotCopy)[i * 3],
                env->GetDoubleArrayElements(fixed_tvects, &isNotCopy)[i * 3 + 1],
                env->GetDoubleArrayElements(fixed_tvects, &isNotCopy)[i * 3 + 2]
        );
    }

    std::vector<int> foundMarkersIDs;
    std::vector<cv::Vec3d> foundMarkersTvecs;
    std::vector<cv::Vec3d> foundMarkersRvecs;
    int foundMarkersSize = env->GetArrayLength(inMarkers);
    foundMarkersIDs.reserve(foundMarkersSize);
    for (int i = 0; i < foundMarkersSize; i++) {
        foundMarkersIDs.push_back(env->GetIntArrayElements(inMarkers, &isNotCopy)[i]);
        foundMarkersRvecs.emplace_back(
                env->GetDoubleArrayElements(in_rvects, &isNotCopy)[i * 3],
                env->GetDoubleArrayElements(in_rvects, &isNotCopy)[i * 3 + 1],
                env->GetDoubleArrayElements(in_rvects, &isNotCopy)[i * 3 + 2]
        );
        foundMarkersTvecs.emplace_back(
                env->GetDoubleArrayElements(in_tvects, &isNotCopy)[i * 3],
                env->GetDoubleArrayElements(in_tvects, &isNotCopy)[i * 3 + 1],
                env->GetDoubleArrayElements(in_tvects, &isNotCopy)[i * 3 + 2]
        );
    }

    std::vector<cv::Vec3d> positionRvecs;
    std::vector<cv::Vec3d> positionTvecs;

    for (int i = 0; i < foundMarkersIDs.size(); i++) {
        int foundMarkerID = foundMarkersIDs[i];
        auto findFixedMarkerIndex = std::find(fixedMarkersIDs.begin(), fixedMarkersIDs.end(),
                                              foundMarkerID);

        if (findFixedMarkerIndex != fixedMarkersIDs.end()) {
            int fixedMarkerIndex = std::distance(fixedMarkersIDs.begin(), findFixedMarkerIndex);
            // Transformation to change from room's coord sys to marker's coord sys
            const cv::Vec3d &fixedMarkerTvect = fixedMarkersTvecs[fixedMarkerIndex];
            const cv::Vec3d &fixedMarkerRvect = fixedMarkersRvecs[fixedMarkerIndex];

            // Transformation to change from marker's coord sys to camera's coord sys
            const cv::Vec3d &foundMarkerTvect = foundMarkersTvecs[i];
            const cv::Vec3d &foundMarkerRvect = foundMarkersRvecs[i];

            // (to be computed) Transf to change from room's coord sys to camera's coord sys
            cv::Vec3d recomputedTvec;
            cv::Vec3d recomputedRvec;


            cv::composeRT(
                    foundMarkerRvect, foundMarkerTvect,
                    fixedMarkerRvect, fixedMarkerTvect,
                    recomputedRvec, recomputedTvec
            );


//            cv::Mat fromRoomToMarker(4, 4, CV_64FC1);
//            genChangeOfReferenceMatrix(fixedMarkerTvect, fixedMarkerRvect, fromRoomToMarker);
//
//            cv::Mat fromMarkerToCamera(4, 4, CV_64FC1);
//            genChangeOfReferenceMatrix(foundMarkerTvect, foundMarkerRvect, fromMarkerToCamera);
//            cv::Mat fromRoomToCamera;
//            fromRoomToCamera = fromRoomToMarker * fromMarkerToCamera;

//            recomputedTvec << fromRoomToCamera.at<double>(0, 3),
//                    fromRoomToCamera.at<double>(1, 3),
//                    fromRoomToCamera.at<double>(2, 3);
//
//            recomputedRvec = rotationMatrixToEulerAngles(
//                    fromRoomToCamera(cv::Rect(0, 0, 3, 3))
//            );


            positionTvecs.push_back(recomputedTvec);
            positionRvecs.push_back(recomputedRvec);
        }
    }

    int inliersCount = -1;
    double estimatedX, estimatedY, estimatedTheta;

    for (int i = 0; i < positionRvecs.size(); i++) {
        fromRTvectsTo2Dpose(positionRvecs[i], positionTvecs[i], estimatedX, estimatedY,
                            estimatedTheta);


//    fitPositionModel(positionRvecs, positionTvecs, inliersCount,
//                     estimatedX, estimatedY, estimatedTheta);
//
//
//    std::ostringstream a;
//    a << "INLIERS=" << inliersCount;
//    int side = inputMat.rows / 2;
//    cv::Point2f topLeftCorner = cv::Point2f(inputMat.cols - side, inputMat.rows - side);
//    cv::putText(inputMat, a.str(), topLeftCorner + cv::Point2f(0, -30),
//                CV_FONT_HERSHEY_COMPLEX_SMALL, 1.0,
//                cv::Scalar(0, 0, 255));



        drawCameraPosition(inputMat, estimatedX, estimatedY, estimatedTheta, i == 0);
    }
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
    __android_log_print(ANDROID_LOG_VERBOSE, "native-lib.cpp",
                        "Invoked detect calibration corners");

    auto image = *castToMatPtr(input_mat_addr);
    auto dictionary = cv::aruco::getPredefinedDictionary(cv::aruco::DICT_6X6_250);

    __android_log_print(ANDROID_LOG_VERBOSE, "native-lib.cpp",
                        "Input casted");

    std::vector<int> ids;
    std::vector<std::vector<cv::Point2f>> corners;

    cv::aruco::detectMarkers(image, dictionary, corners, ids);

    __android_log_print(ANDROID_LOG_VERBOSE, "native-lib.cpp",
                        "Corners detected");

    if (ids.empty()) {
        __android_log_print(ANDROID_LOG_VERBOSE, "native-lib.cpp",
                            "Empty ids!");
        return 0;
    }


    __android_log_print(ANDROID_LOG_VERBOSE, "native-lib.cpp",
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

    __android_log_print(ANDROID_LOG_VERBOSE, "native-lib.cpp",
                        "Populated arrays");

    env->SetIntArrayRegion(size, 0, 1, &image.rows);
    env->SetIntArrayRegion(size, 1, 1, &image.cols);

    __android_log_print(ANDROID_LOG_VERBOSE, "native-lib.cpp",
                        "Populated size");
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
//            markersX,
//            markersY,
//            markerLength,
//            markerSeparation,
            8, 5, 500, 100,
            dictionary
    ); // create aruco board
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
    for (int i = 0; i < nFrames; i++) {
        allFrames.push_back(*castToMatPtr(framesAddresses[i]));
    }

    cv::Size imgSize(size_cols, size_rows);
    if (allIds.empty()) {
        __android_log_print(ANDROID_LOG_ERROR, "native-lib.cpp",
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

    __android_log_print(ANDROID_LOG_ERROR, "native-lib.cpp",
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
        __android_log_print(ANDROID_LOG_ERROR, "native-lib.cpp",
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

    __android_log_print(ANDROID_LOG_ERROR, "native-lib.cpp",
                        "rep error = %f", repError);

    return repError;
}
#pragma clang diagnostic pop