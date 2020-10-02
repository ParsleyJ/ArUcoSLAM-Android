//
// Created by pj on 19/08/2020.
//

#ifndef ARUCOSLAM_POSITIONRANSAC_H
#define ARUCOSLAM_POSITIONRANSAC_H

#include <mutex>
#include <opencv2/core/core.hpp>
#include "utils.h"
/**
 * utility function used to get the i-th number of a vector if such element existed, otherwise,
 * returns 1.0
 */
double getWeight(int i, const std::vector<double>& weights = std::vector<double>()){
    if(weights.size() <= i){
        return 1.0;
    }else{
        return weights[i];
    }
}

/**
 * Computes the weighted mean angle in an iterable of angles.
 */
template<typename ITERABLE>
double meanAngle(const ITERABLE &c, const std::vector<double>& weights = std::vector<double>()) {
    auto it = std::begin(c);
    auto end = std::end(c);

    double x = 0.0;
    double y = 0.0;
    int i = 0;
    while (it != end) {
        x += cos(*it * getWeight(i, weights));
        y += sin(*it * getWeight(i, weights));
        it = std::next(it);
        i++;
    }

    return atan2(y, x);
}

/**
 * Computes the weighted average rotation in a collection of rotations.
 */
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
            double weight = getWeight(i, weights);
            weightSum += weight;
            tmp.push_back(rvecs[i][j] * weight);
        }
        angleCentroid[j] = meanAngle(tmp);
        tmp.clear();
    }
}

/**
 * Computes the weighted centroid of various 3D points in space.
 */
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

/**
 * Computes a vector which is an estimate of 3D vectors by using the RANSAC method.
 * @param vecs the collection of input vectors
 * @param foundModel the computed vector estimate
 * @param inlierThreshold if the evaluation of the distanceFunction between the centroid and a point
 *                        in the space is greater than this parameter, that point is an outlier
 * @param outlierProbability the probability that a random subset of the input vectors contains at
 *                           least one outlier
 * @param targetOptimalModelProbability the desired probability that the found estimate is the
 *                                      optimal one - used to determine the number of iterations
 * @param maxN max number of iterations
 * @param inliers reference on which the number of the inliers is written
 * @param distanceFunction function that takes two vector and returns their distance; defaults to the
 *                          euclidean norm of the difference of the two vectors
 * @param centroidComputer function that computes the weighted average vector of a set of vectors
 * @param weights vector of the weight used to compute the averages; defaults to an empty vector,
 *                          which means that all the weigths are 1.0.
 */
void vectorRansac(
        const std::vector<cv::Vec3d> &vecs,
        cv::Vec3d &foundModel,
        double inlierThreshold,
        double outlierProbability,
        double targetOptimalModelProbability,
        uint maxN,
        int &inliers,
        const std::function<double(const cv::Vec3d &, const cv::Vec3d &)> &distanceFunction
        = [](const cv::Vec3d &v1, const cv::Vec3d &v2) { return cv::norm(v1 - v2); },
        const std::function<void(const std::vector<cv::Vec3d> &,
                                 cv::Vec3d &, const std::vector<double> &)> &centroidComputer
        = &computeCentroid,
        const std::vector<double> &weights = std::vector<double>()
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
        return;
    }
    size_t subSetSize;
    if (vecs.size() <= 10) {
        subSetSize = 2;
    } else {
        subSetSize = 5;
    }

    // target probability to get a subset which generates a model without outliers


    uint attempts = log(1.0 - targetOptimalModelProbability) / log(1.0 - pow(1.0 - outlierProbability, double(subSetSize)));
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

/**
 * Computes the "rotational" distance between two orientations.
 */
double angularDistance(const cv::Vec3d &p1, const cv::Vec3d &p2){
    return cv::norm(cv::Vec3d(
            atan2(sin(p1[0] - p2[0]), cos(p1[0] - p2[0])),
            atan2(sin(p1[1] - p2[1]), cos(p1[1] - p2[1])),
            atan2(sin(p1[2] - p2[2]), cos(p1[2] - p2[2]))
    ));
}

/**
 * Estimates a pose by using two RANSACs, one for the translation vectors and one for the rotation
 * vectors.
 */
int estimateCameraPose(
        const std::vector<cv::Vec3d> &rvecs,
        const std::vector<cv::Vec3d> &tvecs,
        cv::Vec3d &modelRvec,
        cv::Vec3d &modelTvec,
        double tvecInlierTreshold = 0.05,
        double tvecOutlierProbability = 0.1,
        double rvecInlierTreshold = M_PI / 8.0,
        double rvecOutlierProbability = 0.1,
        uint maxRansacIterations = 100,
        double optimalModelTargetProbability = 0.9
) {
    int inliers = -1;

    vectorRansac(
            tvecs,
            modelTvec,
            tvecInlierTreshold,
            tvecOutlierProbability,
            optimalModelTargetProbability,
            maxRansacIterations,
            inliers
    );

    vectorRansac(
            rvecs,
            modelRvec,
            rvecInlierTreshold,
            rvecOutlierProbability,
            optimalModelTargetProbability,
            maxRansacIterations,
            inliers,
            &angularDistance,
            &computeAngleCentroid
    );

    return inliers;
}

#endif //ARUCOSLAM_POSITIONRANSAC_H
