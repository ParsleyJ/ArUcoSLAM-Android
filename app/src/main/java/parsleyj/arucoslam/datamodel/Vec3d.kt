package parsleyj.arucoslam.datamodel

inline class Vec3d(private val d: DoubleArray) {
    companion object {
        val ORIGIN = Vec3d(0.0, 0.0, 0.0)
    }

    constructor(x: Double, y: Double, z: Double) : this(
        doubleArrayOf(x, y, z)
    )

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
}