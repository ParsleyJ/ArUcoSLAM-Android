//
// Created by pj on 21/08/2020.
//

#ifndef ARUCOSLAM_OPENCV_EXTENSIONS_H
#define ARUCOSLAM_OPENCV_EXTENSIONS_H

#include <opencv2/core/core.hpp>
#include <opencv2/aruco.hpp>
#include <opencv2/calib3d.hpp>


static void _getSingleMarkerObjectPoints(float markerLength, cv::OutputArray _objPoints) {

    CV_Assert(markerLength > 0);

    _objPoints.create(4, 1, CV_32FC3);
    cv::Mat objPoints = _objPoints.getMat();
    // set coordinate system in the middle of the marker, with Z pointing out
    objPoints.ptr<cv::Vec3f>(0)[0] = cv::Vec3f(-markerLength / 2.f, markerLength / 2.f, 0);
    objPoints.ptr<cv::Vec3f>(0)[1] = cv::Vec3f(markerLength / 2.f, markerLength / 2.f, 0);
    objPoints.ptr<cv::Vec3f>(0)[2] = cv::Vec3f(markerLength / 2.f, -markerLength / 2.f, 0);
    objPoints.ptr<cv::Vec3f>(0)[3] = cv::Vec3f(-markerLength / 2.f, -markerLength / 2.f, 0);
}

void estimatePoseSingleMarkers(cv::InputArrayOfArrays _corners, const std::vector<double>& markerLengths,
                               cv::InputArray _cameraMatrix, cv::InputArray _distCoeffs,
                               cv::OutputArray _rvecs, cv::OutputArray _tvecs) {


    int nMarkers = (int) _corners.total();
    _rvecs.create(nMarkers, 1, CV_64FC3);
    _tvecs.create(nMarkers, 1, CV_64FC3);

    cv::Mat rvecs = _rvecs.getMat(), tvecs = _tvecs.getMat();

    // for each marker, calculate its pose
    parallel_for_(cv::Range(0, nMarkers), [&](const cv::Range &range) {
        const int begin = range.start;
        const int end = range.end;


        for (int i = begin; i < end; i++) {
            cv::Mat markerObjPoints;
            _getSingleMarkerObjectPoints(markerLengths[i], markerObjPoints);
            cv::solvePnP(markerObjPoints, _corners.getMat(i), _cameraMatrix, _distCoeffs,
                     rvecs.at<cv::Vec3d>(i),
                     tvecs.at<cv::Vec3d>(i));
        }
    });
}

#endif //ARUCOSLAM_OPENCV_EXTENSIONS_H
