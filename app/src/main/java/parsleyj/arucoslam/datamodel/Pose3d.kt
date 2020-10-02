package parsleyj.arucoslam.datamodel

import parsleyj.arucoslam.NativeMethods

/**
 * A pose in a 3D world is a pair of two three-component vectors: the first one represents the
 * rotation of the orientation from the "origin" orientation; the second one represents the
 * translation of the position from the "origin" position.
 * Note that this is an inline class: this means that on the JVM at runtime this class does not
 * exist and that all the methods are compiled into Java methods that work on a pair of [Vec3d]s
 * (which are also inline classes in their turn).
 */
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

    /**
     * Returns the composition of the RT-transformation of this pose with the one of the [other]
     * pose.
     */
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

    /**
     * Returns a pose which is the inverse of the RT-transformation represented by this pose.
     */
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

    fun copyTo(other: Pose3d){
        this.translationVector.copyTo(other.translationVector)
        this.rotationVector.copyTo(other.rotationVector)
    }

    /**
     * Inverts the RT trasformation associated with this pose by not allocating any support
     * structures in the heap, and by directly changing the data in this pose.
     * The returned value is simply a reference to this pose.
     */
    fun invertInPlace():Pose3d{
        NativeMethods.invertRT(
            rotationVector.asDoubleArray(),
            translationVector.asDoubleArray(),
            rotationVector.asDoubleArray(),
            translationVector.asDoubleArray(),
        )
        return this
    }

    companion object {
        val ORIGIN = Pose3d(Vec3d.ORIGIN, Vec3d.ORIGIN)
    }

    override fun toString() = """
        Pose3d{
            rvec=$rotationVector; 
            tvec=$translationVector
        }
    """.trimIndent()
}