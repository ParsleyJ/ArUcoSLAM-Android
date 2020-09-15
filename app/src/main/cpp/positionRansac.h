//
// Created by pj on 19/08/2020.
//

#ifndef ARUCOSLAM_POSITIONRANSAC_H
#define ARUCOSLAM_POSITIONRANSAC_H

#include <mutex>
#include <opencv2/core/core.hpp>
#include "utils.h"


double getWeight(int i, const std::vector<double>& weights = std::vector<double>()){
    if(weights.size() <= i){
        return 1.0;
    }else{
        return weights[i];
    }
}

template<typename ITERABLE>
double meanAngle(const ITERABLE &c, const std::vector<double>& weights = std::vector<double>()) {
    auto it = std::begin(c);
    auto end = std::end(c);


    double x = 0.0;
    double y = 0.0;
    int i = 0;
    while (it != end) {
        x += cos(*it * weights[i]);
        y += sin(*it * weights[i]);
        it = std::next(it);
        i++;
    }

    return atan2(y, x);
}

void computeAngleCentroid(
        const std::vector<cv::Vec3d> &rvecs,
        cv::Vec3d &angleCentroid,
        std::vector<double> weights = std::vector<double>()
) {
    std::vector<double> tmp;
    tmp.reserve(rvecs.size());


    for (int j = 0; j < 3; j++) {
        double weightSum = 0.0;
        for (int i = 0; i < rvecs.size(); i++) {
            double weight = getWeight(i);
            weightSum += weight;
            tmp.push_back(rvecs[i][j] * weight);
        }
        angleCentroid[j] = meanAngle(tmp);
        tmp.clear();
    }
}


void computeCentroid(
        const std::vector<cv::Vec3d> &vecs,
        cv::Vec3d &centre,
        const std::vector<double> &weights = std::vector<double>()
) {

    double weightSum = 0.0;
    centre[0] = 0;
    centre[1] = 0;
    centre[2] = 0;
    for (int i = 0; i < vecs.size(); i++) {
        weightSum += getWeight(i);
        centre[0] += vecs[i][0] * getWeight(i);
        centre[1] += vecs[i][1] * getWeight(i);
        centre[2] += vecs[i][2] * getWeight(i);
    }
    centre[0] /= weightSum;
    centre[1] /= weightSum;
    centre[2] /= weightSum;
}

void vectorRansac(
        const std::vector<cv::Vec3d> &vecs,
        cv::Vec3d &foundModel,
        double inlierThreshold,
        double outlierProbability,
        uint maxN,
        int &inliers,
        const std::vector<double> &weights
        = std::vector<double>(),
        const std::function<double(const cv::Vec3d &, const cv::Vec3d &)> &distanceFunction
        = [](const cv::Vec3d &v1, const cv::Vec3d &v2) { return cv::norm(v1 - v2); },
        const std::function<void(const std::vector<cv::Vec3d> &,
                                 cv::Vec3d &, const std::vector<double> &)> &centroidComputer
        = &computeCentroid
) {


    if (vecs.empty()) {
        inliers = 0;
        return;
    }
    if (vecs.size() == 1) {
        inliers = 1;
        foundModel = getWeight(0) * vecs[0];
        return;
    }
    if (vecs.size() == 2) {
        inliers = 2;
        computeCentroid(vecs, foundModel, weights);
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
    std::vector<double> bestWeigths;


    p_for(attempt_I, attempts) {
        std::vector<cv::Vec3d> foundInliers;
        std::vector<double> foundWeights;
        foundInliers.reserve(vecs.size());
        foundWeights.reserve(vecs.size());

        std::vector<int> subsetOfIndices;
        randomUniqueIndices(vecs.size(), subSetSize, subsetOfIndices);

        std::vector<cv::Vec3d> subset;
        std::vector<double> weightsSubset;
        for (auto i : subsetOfIndices) {
            subset.push_back(vecs[i]);
            weightsSubset.push_back(getWeight(i));
        }

        cv::Vec3d centroid;
        centroidComputer(subset, centroid, weightsSubset);
        for (int i = 0; i < vecs.size(); i++) {
            const cv::Vec3d &vec = vecs[i];
            if (distanceFunction(centroid, vec) <= inlierThreshold) {
                foundInliers.push_back(vec);
                foundWeights.push_back(getWeight(i));
            }
        }

        p_for_criticalSectionBegin

            if (foundInliers.size() > bestInliers.size()) {
                bestInliers = foundInliers;
                bestWeigths = foundWeights;
            }

        p_for_criticalSectionEnd
    };

    inliers = bestInliers.size();
    centroidComputer(bestInliers, foundModel, bestWeigths);
}


int fitPositionModel(
        const std::vector<cv::Vec3d> &rvecs,
        const std::vector<cv::Vec3d> &tvecs,
        cv::Vec3d &modelRvec,
        cv::Vec3d &modelTvec,
        const std::vector<double> &confidences = std::vector<double>()
) {
    int inliers = -1;

    vectorRansac(
            tvecs,
            modelTvec,
            0.05,
            0.1,
            100,
            inliers,
            confidences
    );

    constexpr double angleInlierThreshold = M_PI / 8.0;
    vectorRansac(
            rvecs,
            modelRvec,
            angleInlierThreshold,
            0.1,
            100,
            inliers,
            confidences,
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
