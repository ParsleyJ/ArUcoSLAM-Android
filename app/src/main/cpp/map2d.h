//
// Created by pj on 19/08/2020.
//

#ifndef ARUCOSLAM_MAP2D_H
#define ARUCOSLAM_MAP2D_H

#include <opencv2/core/core.hpp>
#include "utils.h"

void drawCameraPosition(
        cv::Mat &_image,
        double X,
        double Y,
        double theta,
        bool drawBackground = true
) {
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

void fromRTvectsTo2Dpose(
        const cv::Vec3d &rVec,
        const cv::Vec3d &tVec,
        double &resultX,
        double &resultY,
        double &resultAngle
) {

    resultAngle = (rVec[0] < 0.0 ? -1.0 : 1.0) * rVec[2] * (90.0 / 130.0);
    resultX = -tVec[0] * calibSizeRatio;
    resultY = tVec[2] * calibSizeRatio;
}


#endif //ARUCOSLAM_MAP2D_H
