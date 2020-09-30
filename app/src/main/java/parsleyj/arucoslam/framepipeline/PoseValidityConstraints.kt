package parsleyj.arucoslam.framepipeline

import parsleyj.arucoslam.datamodel.Pose3d
import parsleyj.arucoslam.datamodel.Track

class PoseValidityConstraints {

    fun estimatedPoseIsValid(
        pose3d: Pose3d,
        track: Track,
        detectedKnownMarkersCount: Int,
        inliersCount: Int,
    ): Boolean {
        return true //TODO
    }
}
