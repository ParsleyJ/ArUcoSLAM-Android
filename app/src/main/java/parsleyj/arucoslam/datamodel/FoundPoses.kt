package parsleyj.arucoslam.framepipeline

import parsleyj.arucoslam.datamodel.Pose3d

class FoundPoses(
    var phonePose: Pose3d,
    var phonePoseConfidence: Double,
    var foundMarkersPoses: MutableList<Pose3d>
)