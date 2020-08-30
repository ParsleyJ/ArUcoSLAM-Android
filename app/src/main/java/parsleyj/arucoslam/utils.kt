@file:Suppress("ClassName")

package parsleyj.arucoslam

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.opencv.core.Mat
import parsleyj.arucoslam.datamodel.Vec3d
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.DoubleBuffer
import java.nio.FloatBuffer
import kotlin.properties.Delegates
import kotlin.reflect.KProperty



object list
object mutList
object doubleArray
object intArray
object doubleBuffer

operator fun <T> list.get(vararg values: T): List<T> {
    return listOf(*values)
}

operator fun <T> mutList.get(vararg values: T): MutableList<T> {
    return mutableListOf(*values)
}


operator fun doubleArray.get(vararg values: Double): DoubleArray {
    return doubleArrayOf(*values)
}

operator fun intArray.get(vararg values: Int): IntArray {
    return intArrayOf(*values)
}

operator fun doubleBuffer.get(vararg values:Double): DoubleBuffer {
    return doubleArrayOf(*values).asDoubleBuffer()
}




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

fun DoubleArray.asDoubleBuffer(): DoubleBuffer = DoubleBuffer.wrap(this)

fun FloatArray.asFloatBuffer() = FloatBuffer.wrap(this)

fun newByteBuffer(paramInt: Int): ByteBuffer {
    val localByteBuffer = ByteBuffer.allocateDirect(paramInt)
    localByteBuffer.order(ByteOrder.nativeOrder())
    return localByteBuffer
}

object initMat

operator fun initMat.get(vararg values : Double):ByteBuffer{
    return doubleArrayOf(*values).asDoubleBuffer().copyToNewByteBuffer()
}

fun Double.format(digits: Int) = "%.${digits}f".format(this)

fun Double.format(digits: Int, maxWidth: Int) = "%${maxWidth}.${digits}f".format(this)

inline fun <T> Mat.withCameraMatrix(crossinline block:(fx:Double, fy:Double, cx:Double, cy:Double)->T) :T {
    return block(this[0, 0][0], this[1, 1][0], this[0, 2][0], this[1, 2][0])
}

operator fun <T> Array<T>.set(vararg indices:Int, takeValuesFrom:Iterable<T>){
    for((x, v) in takeValuesFrom.withIndex()){
        this[indices[x]] = v
    }
}

operator fun DoubleArray.set(vararg indices:Int, takeValuesFrom: Iterable<Double>){
    for ((x, v) in takeValuesFrom.withIndex()) {
        this[indices[x]] = v
    }
}

fun List<Vec3d>.flattenVecs(): List<Double> {
    return this.flatMap { list[it.x, it.y, it.z] }
}

