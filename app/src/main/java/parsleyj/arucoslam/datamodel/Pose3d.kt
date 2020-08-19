package parsleyj.arucoslam.datamodel

inline class Pose3d(private val pair: Pair<Vec3d, Vec3d>) {
    constructor(
        rVec: Vec3d,
        tVec: Vec3d
    ) : this(
        Pair(
            rVec,
            tVec
        )
    )

    val rotationVector: Vec3d
    get() = pair.first

    val translationVector: Vec3d
    get() = pair.second

    fun asPairOfVec3d() = pair
}