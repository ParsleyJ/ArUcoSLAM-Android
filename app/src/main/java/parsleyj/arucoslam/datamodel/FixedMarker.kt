package parsleyj.arucoslam.datamodel

data class FixedMarker(
    val markerId: Int,
    val pose3d: Pose3d,
    val markerSideLength: Double
)