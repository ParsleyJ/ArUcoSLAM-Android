package parsleyj.arucoslam.datamodel

inline class Vec3d(private val d: DoubleArray) {
    constructor(x: Double, y: Double, z: Double) : this(
        doubleArrayOf(x, y, z)
    )

    val x: Double
        get() = d[0]
    val y: Double
        get() = d[1]
    val z: Double
        get() = d[2]

    fun asDoubleArray() = d
}