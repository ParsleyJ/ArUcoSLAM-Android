package parsleyj.arucoslam.datamodel

import parsleyj.arucoslam.NativeMethods


inline class Pose3d(private val pair: Pair<Vec3d, Vec3d>) {
    constructor(
        rVec: Vec3d,
        tVec: Vec3d,
    ) : this(
        Pair(
            rVec,
            tVec
        )
    )

    constructor() : this(Vec3d(), Vec3d())



    val rotationVector: Vec3d
        get() = pair.first

    val translationVector: Vec3d
        get() = pair.second

    fun asPairOfVec3d() = pair

    operator fun times(other: Pose3d): Pose3d {
        val r = doubleArrayOf(0.0, 0.0, 0.0)
        val t = doubleArrayOf(0.0, 0.0, 0.0)
        NativeMethods.composeRT(
            this.rotationVector.asDoubleArray(),
            this.translationVector.asDoubleArray(),
            other.rotationVector.asDoubleArray(),
            other.translationVector.asDoubleArray(),
            r,
            t
        )
        return Pose3d(Vec3d(r), Vec3d(t))
    }

    fun translateTo(translationVector: Vec3d): Pose3d {
        return this * Pose3d(Vec3d.ORIGIN, translationVector)
    }

    fun rotate(rotationVector: Vec3d): Pose3d {
        return this * Pose3d(rotationVector, Vec3d.ORIGIN)
    }

    fun invert():Pose3d{
        val result = Pose3d()
        NativeMethods.invertRT(
            rotationVector.asDoubleArray(),
            translationVector.asDoubleArray(),
            result.rotationVector.asDoubleArray(),
            result.translationVector.asDoubleArray()
        )
        return result
    }

    companion object {
        val ORIGIN = Pose3d(Vec3d.ORIGIN, Vec3d.ORIGIN)
    }
}