//
// Created by pj on 19/08/2020.
//

#include <vector>
#include <opencv2/core/core.hpp>
#include "MarkerTaggedSpaceInterface.h"


MarkerTaggedSpaceInterface::MarkerTaggedSpaceInterface(JNIEnv *env, jobject jthis)
        : jthis(jthis), env(env) {

}

bool MarkerTaggedSpaceInterface::getMarkerSpecs(int id, std::vector<cv::Vec3d> &outRvec,
                                                std::vector<cv::Vec3d> &outTvec,
                                                double &markerLength) const {

    jclass jclass = this->getClass();

    jmethodID met = getMethodId("getMarkerSpecs",
                                "(I)Lparsleyj/arucoslam/datamodel/FixedMarker");
    return this->jthis;
}
