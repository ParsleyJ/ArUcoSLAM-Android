package parsleyj.arucoslam.datamodel

import parsleyj.arucoslam.datamodel.Pose3d
import parsleyj.arucoslam.datamodel.slamspace.SLAMMarker

class FoundPoses(
    var phonePose: Pose3d,
    var phonePoseConfidence: Double,
    var foundMarkersPoses: MutableList<SLAMMarker>
){
    constructor():this(
        Pose3d.ORIGIN,
        1.0,
        mutableListOf()
    )
}