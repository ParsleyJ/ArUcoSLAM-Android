package parsleyj.arucoslam.datamodel

import parsleyj.arucoslam.NativeMethods
import kotlin.math.sqrt

/**
 * A three-component vector where each component is a double precision floating point number.
 *
 * Note that this is an inline class: this means that on the JVM at runtime this class does not
 * exist and that all the methods are compiled into Java methods that work on a [DoubleArray] of
 * size 3.
 */
inline class Vec3d(private val d: DoubleArray) {
    companion object {
        val ORIGIN = Vec3d(0.0, 0.0, 0.0)
    }

    constructor(x: Double, y: Double, z: Double) : this(
        doubleArrayOf(x, y, z)
    )

    constructor() : this(0.0, 0.0, 0.0)


    var x: Double
        get() = d[0]
        set(value) {
            d[0] = value
        }

    var y: Double
        get() = d[1]
        set(value) {
            d[1] = value
        }

    var z: Double
        get() = d[2]
        set(value) {
            d[2] = value
        }

    /**
     * This function does not exist: if the Kotlin compiler is smart enough, this is compiled
     * directly to a reference of the array [d].
     */
    fun asDoubleArray() = d

    operator fun plus(other: Vec3d): Vec3d {
        return Vec3d(x + other.x, y + other.y, z + other.z)
    }

    operator fun minus(other: Vec3d): Vec3d {
        return Vec3d(x - other.x, y - other.y, z - other.z)
    }

    fun euclideanNorm() = sqrt(x * x + y * y + z * z)

    /**
     * If this vector represents a triple of euler angles, this function computes the overall
     * "angular distance" between the origin orientation and this orientation.
     */
    fun angularDistance(other: Vec3d): Double {
        return NativeMethods.angularDistance(this.asDoubleArray(), other.asDoubleArray())
    }

    fun copyTo(other: Vec3d){
        other.x = this.x
        other.y = this.y
        other.z = this.z
    }

    override fun toString() = """
        Vec3d($x, $y, $z)
    """.trimIndent()
}