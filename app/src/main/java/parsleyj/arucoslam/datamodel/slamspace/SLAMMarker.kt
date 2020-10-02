package parsleyj.arucoslam.datamodel.slamspace

import parsleyj.arucoslam.datamodel.Pose3d
import parsleyj.arucoslam.datamodel.Vec3d

/**
 * A Marker, identified by an id, with its pose.
 */
data class SLAMMarker(
    val markerId: Int,
    val pose3d: Pose3d,
) {

    infix fun moveTo(pose: Pose3d): SLAMMarker {
        return SLAMMarker(markerId, pose3d * pose)
    }

    infix fun translateTo(translation: Vec3d):SLAMMarker {
        return this moveTo Pose3d(Vec3d.ORIGIN, translation)
    }
}