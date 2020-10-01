package parsleyj.arucoslam.datamodel

import parsleyj.arucoslam.NativeMethods
import kotlin.math.sqrt

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

    fun asDoubleArray() = d

    operator fun plus(other: Vec3d): Vec3d {
        return Vec3d(x + other.x, y + other.y, z + other.z)
    }

    operator fun minus(other: Vec3d): Vec3d {
        return Vec3d(x - other.x, y - other.y, z - other.z)
    }

    fun euclideanNorm() = sqrt(x * x + y * y + z * z)

    fun angularDistance(other: Vec3d): Double {
        return NativeMethods.angularDistance(this.asDoubleArray(), other.asDoubleArray())
    }

    override fun toString() = """
        Vec3d($x, $y, $z)
    """.trimIndent()
}