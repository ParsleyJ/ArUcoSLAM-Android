package parsleyj.arucoslam.datamodel

data class FixedMarker(
    val markerId: Int,
    val pose3d: Pose3d,
    val markerSideLength: Double
) {
    infix fun movedTo(pose: Pose3d): FixedMarker {
        return FixedMarker(markerId, pose3d * pose, markerSideLength)
    }

    infix fun movedTo(translation: Vec3d): FixedMarker {
        return this movedTo Pose3d(Vec3d.ORIGIN, translation)
    }
}