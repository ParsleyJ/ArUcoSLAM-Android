@file:Suppress("ClassName")

package parsleyj.arucoslam

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import parsleyj.arucoslam.datamodel.Vec3d
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.DoubleBuffer
import java.nio.FloatBuffer


object list
object mutList

operator fun <T> list.get(vararg values: T): List<T> {
    return listOf(*values)
}

operator fun <T> mutList.get(vararg values: T): MutableList<T> {
    return mutableListOf(*values)
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

fun DoubleArray.asDoubleBuffer() = DoubleBuffer.wrap(this)

fun FloatArray.asFloatBuffer() = FloatBuffer.wrap(this)

fun newByteBuffer(paramInt: Int): ByteBuffer {
    val localByteBuffer = ByteBuffer.allocateDirect(paramInt)
    localByteBuffer.order(ByteOrder.nativeOrder())
    return localByteBuffer
}


fun Double.format(digits: Int) = "%.${digits}f".format(this)

fun Double.format(digits: Int, maxWidth: Int) = "%${maxWidth}.${digits}f".format(this)


fun List<Vec3d>.flattenVecs(): List<Double> {
    return this.flatMap { list[it.x, it.y, it.z] }.toList()
}

