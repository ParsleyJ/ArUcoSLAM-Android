//
// Created by pj on 19/08/2020.
//

#ifndef ARUCOSLAM_POSITIONRANSAC_H
#define ARUCOSLAM_POSITIONRANSAC_H

#include <mutex>
#include <opencv2/core/core.hpp>
#include "utils.h"


template<typename ITERABLE>
double meanAngle(const ITERABLE &c) {
    auto it = std::begin(c);
    auto end = std::end(c);

    double x = 0.0;
    double y = 0.0;
    while (it != end) {
        x += cos(*it);
        y += sin(*it);
        it = std::next(it);
    }

    return atan2(y, x);
}

void computeAngleCentroid(
        const std::vector<cv::Vec3d> &rvecs,
        cv::Vec3d &angleCentroid
) {
    std::vector<double> tmp;
    tmp.reserve(rvecs.size());
    std::transform(rvecs.begin(), rvecs.end(), std::back_inserter(tmp),
                   [&](cv::Vec3d v) { return v[0]; });
    angleCentroid[0] = meanAngle(tmp);
    tmp.clear();
    std::transform(rvecs.begin(), rvecs.end(), std::back_inserter(tmp),
                   [&](cv::Vec3d v) { return v[1]; });
    angleCentroid[1] = meanAngle(tmp);
    tmp.clear();
    std::transform(rvecs.begin(), rvecs.end(), std::back_inserter(tmp),
                   [&](cv::Vec3d v) { return v[2]; });
    angleCentroid[2] = meanAngle(tmp);
    tmp.clear();
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

void vectorRansac(
        const std::vector<cv::Vec3d> &vecs,
        cv::Vec3d &foundModel,
        double inlierThreshold,
        double outlierProbability,
        uint maxN,
        int &inliers,
        const std::function<double(const cv::Vec3d &, const cv::Vec3d &)> &distanceFunction
        = [](const cv::Vec3d &v1, const cv::Vec3d &v2) { return cv::norm(v1 - v2); },
        const std::function<void(const std::vector<cv::Vec3d> &, cv::Vec3d &)> &centroidComputer
        = &computeCentroid
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
    if(vecs.size() == 2){
        inliers = 2;
        computeCentroid(vecs, foundModel);
    }
    size_t subSetSize;
    if (vecs.size() <= 10) {
        subSetSize = 2;
    } else {
        subSetSize = 5;
    }

    // target probability to get a subset which generates a model without outliers
    double prob = 0.9;

    uint attempts = log(1.0 - prob) / log(1.0 - pow(1.0 - outlierProbability, double(subSetSize)));
    if (attempts == 0) {
        attempts = 1;
    }
    if (attempts > maxN) {
        attempts = maxN;
    }


    std::vector<cv::Vec3d> bestInliers;
    std::mutex mut;

    p_for(attempt_I, attempts) {
        std::vector<cv::Vec3d> foundInliers;
        foundInliers.reserve(vecs.size());
        std::vector<cv::Vec3d> subset;
        randomSubset(vecs, subset, subSetSize);
        cv::Vec3d centroid;
        centroidComputer(subset, centroid);
        for (const auto &vec:vecs) {
            if (distanceFunction(centroid, vec) <= inlierThreshold) {
                foundInliers.push_back(vec);
            }
        }

        {
            std::unique_lock<std::mutex> ul(mut);

            if (foundInliers.size() > bestInliers.size()) {
                bestInliers = foundInliers;
            }
        }
    };

    inliers = bestInliers.size();
    centroidComputer(bestInliers, foundModel);
}


int fitPositionModel(
        const std::vector<cv::Vec3d> &rvecs,
        const std::vector<cv::Vec3d> &tvecs,
        cv::Vec3d &modelRvec,
        cv::Vec3d &modelTvec
) {
    int inliers = -1;

    vectorRansac(
            tvecs,
            modelTvec,
            0.05,
            0.1,
            100,
            inliers
    );

    constexpr double angleInlierThreshold = M_PI / 8.0;
    vectorRansac(
            rvecs,
            modelRvec,
            angleInlierThreshold,
            0.1,
            100,
            inliers,
            [&](const cv::Vec3d &p1, const cv::Vec3d &p2) {
                return cv::norm(cv::Vec3d(
                        atan2(sin(p1[0] - p2[0]), cos(p1[0] - p2[0])),
                        atan2(sin(p1[1] - p2[1]), cos(p1[1] - p2[1])),
                        atan2(sin(p1[2] - p2[2]), cos(p1[2] - p2[2]))
                ));
            },
            &computeAngleCentroid
    );

    return inliers;
}

#endif //ARUCOSLAM_POSITIONRANSAC_H
