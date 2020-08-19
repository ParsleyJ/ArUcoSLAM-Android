//
// Created by pj on 19/08/2020.
//

#ifndef ARUCOSLAM_POSITIONRANSAC_H
#define ARUCOSLAM_POSITIONRANSAC_H

#include <opencv2/core/core.hpp>
#include "utils.h"

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

void vectorRansac(
        const std::vector<cv::Vec3d> &vecs,
        cv::Vec3d &foundModel,
        const std::function<double(const cv::Vec3d &, const cv::Vec3d &)> &distanceFunction,
        double inlierThreshold,
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


    std::vector<cv::Vec3d> bestInliers;
    for (int attempt_I = 0; attempt_I < attempts; attempt_I++) {
        std::vector<cv::Vec3d> foundInliers;
        foundInliers.reserve(vecs.size());
        std::vector<cv::Vec3d> subset;
        randomSubset(vecs, subset, subSetSize);
        cv::Vec3d centroid;
        computeCentroid(subset, centroid);
        for (const auto &vec:vecs) {
            if (distanceFunction(centroid, vec) <= inlierThreshold) {
                foundInliers.push_back(vec);
            }
        }
        if (attempt_I == 0 || foundInliers.size() > bestInliers.size()) {
            bestInliers = foundInliers;
        }
    }

    inliers = bestInliers.size();
    computeCentroid(bestInliers, foundModel);
}


int fitPositionModel(
        const std::vector<cv::Vec3d> &rvecs,
        const std::vector<cv::Vec3d> &tvecs,
        cv::Vec3d &modelRvec,
        cv::Vec3d &modelTvec
) {
    int inliers = -1;
    vectorRansac(rvecs, modelRvec, [&](const cv::Vec3d &p1, const cv::Vec3d &p2) {
        return cv::norm(cv::Vec3d(
                atan2(sin(p1[0] - p2[0]), cos(p1[0] - p2[0])),
                atan2(sin(p1[1] - p2[1]), cos(p1[1] - p2[1])),
                atan2(sin(p1[2] - p2[2]), cos(p1[2] - p2[2]))
        ));
    }, 0.05, 100, inliers);
    vectorRansac(tvecs, modelTvec, [&](const cv::Vec3d &p1, const cv::Vec3d &p2) {
        return cv::norm(p1 - p2);
    }, 0.05, 100, inliers);

//    computeCentroid(tvecs, tVec);
//    computeCentroid(rvecs, modelRvec);
    return inliers;
}

#endif //ARUCOSLAM_POSITIONRANSAC_H
