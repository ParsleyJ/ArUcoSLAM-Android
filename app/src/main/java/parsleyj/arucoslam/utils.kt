package parsleyj.arucoslam

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.DoubleBuffer
import java.nio.FloatBuffer

val defaultDispatcher = CoroutineScope(Dispatchers.Default)
val mainDispatcher = CoroutineScope(Dispatchers.Main)
val ioDispatcher = CoroutineScope(Dispatchers.IO)


/**
 * the block will be executed on background
 */
fun backgroundExec(codeBlock: suspend () -> Unit): Job = defaultDispatcher.launch {
    codeBlock()
}

/**
 * the codeBlock will be executed on gui thread
 */
fun guiExec(codeBlock: suspend () -> Unit): Job = mainDispatcher.launch {
    codeBlock()
}

/**
 * the codeBlock will be executed on a thread dedicated to I/O operations
 */
fun ioExec(codeBlock: suspend () -> Unit): Job = ioDispatcher.launch {
    codeBlock()
}


fun DoubleBuffer.copyToNewByteBuffer(): ByteBuffer {
    val localByteBuffer = newByteBuffer(
        remaining() * 8
    )
    mark()
    localByteBuffer.asDoubleBuffer().put(this)
    reset()
    localByteBuffer.rewind()
    return localByteBuffer
}

fun FloatBuffer.copyToNewByteBuffer(): ByteBuffer {
    val localByteBuffer = newByteBuffer(
        remaining() * 4
    )
    mark()
    localByteBuffer.asFloatBuffer().put(this)
    reset()
    localByteBuffer.rewind()
    return localByteBuffer
}

fun DoubleArray.asDoubleBuffer() = DoubleBuffer.wrap(this)

fun FloatArray.asFloatBuffer() = FloatBuffer.wrap(this)

fun newByteBuffer(paramInt: Int): ByteBuffer {
    val localByteBuffer = ByteBuffer.allocateDirect(paramInt)
    localByteBuffer.order(ByteOrder.nativeOrder())
    return localByteBuffer
}


fun Double.format(digits: Int) = "%.${digits}f".format(this)

fun Double.format(digits: Int, maxWidth: Int) = "%${maxWidth}.${digits}f".format(this)


// inline classes! this class does not exist at runtime. It is just a triple. The compiler converts
//      the method/property calls for us.
inline class FixedMarkersOnBoard(private val boardData: Triple<List<Int>, List<Vec3d>, List<Vec3d>>) {
    constructor(ids: List<Int>, rvecs: List<Vec3d>, tvecs: List<Vec3d>) : this(
        Triple(
            ids,
            rvecs,
            tvecs
        )
    )

    val ids: List<Int>
        get() = boardData.first

    val rvecs: List<Vec3d>
        get() = boardData.second

    val tvecs: List<Vec3d>
        get() = boardData.third

}

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

fun arucoBoardFixedMarkers(): FixedMarkersOnBoard {
    val ids = (0 until 40).toList()
    val rvecs = mutableListOf<Vec3d>()
    val tvecs = mutableListOf<Vec3d>()

    val markerLength = 0.05
    val markerSeparation = 0.01

    val tX = -3.5 * markerLength - 3.5 * markerSeparation
    val tY = -2.0 * markerLength - 2.0 * markerSeparation

    for (i in ids) {
        val row = i / 8
        val col = i % 8
        rvecs.add(Vec3d(0.0, 0.0, 0.0))
        tvecs.add(Vec3d(tX + row*(markerLength+markerSeparation), tY + col*(markerLength+markerSeparation), 0.0))
    }

    return FixedMarkersOnBoard(ids, rvecs, tvecs)
}

fun List<Vec3d>.flattenVecs():List<Double>{
    return this.flatMap {
        listOf(it.x, it.y, it.z)
    }.toList()
}