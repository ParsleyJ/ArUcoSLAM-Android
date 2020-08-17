package parsleyj.arucoslam

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import org.opencv.core.Mat
import org.opencv.core.Size

class FrameStreamProcessor(
    private val size: Size,
    private val frameType: Int,
    private val maxProcessors: Int,
    private val jobTimeout: Long = 1000L,
    private val block: suspend (Mat, Mat, IntArray, DoubleArray, DoubleArray) -> Unit
) {
    private var lastResult = Mat.zeros(size, frameType)
    private var lastResultToken = -1L
    private val processors = mutableListOf<FrameProcessor>()


    fun supply(input: Mat?, token: Long) {
        // if there are no free processors, do not process the frame.
        val proc = getFreeProcessor()
        if (proc == null) {
            lastResult = input
        } else {
            proc.assignFrame(input, token)
            backgroundExec {
                val job = proc.compute()
                delay(jobTimeout)
                job?.cancel()
            }
        }
    }

    fun usage(): Double {
        return (processors.filter { it.isBusy() }.count()
            .toDouble() / maxProcessors.toDouble()) * 100.0
    }

    fun retrieve(): Mat {
        return lastResult
    }

    // sorts processors by bringing the free ones at the beginning
    private fun sortProcessors() {
        processors.sortBy { if (it.isBusy()) 1 else -1 }
    }

    private fun getFreeProcessor(): FrameProcessor? {
        if (processors.isEmpty()) {
            val frameProcessor = createFrameProcessor()
            processors.add(frameProcessor)
            return frameProcessor
        }

        sortProcessors()

        return if (processors.first().isBusy()) {
            // if the first one is busy, all of them are.
            // let's try to instantiate one processor
            if (processors.size < maxProcessors) {
                // we can create a new processor
                val frameProcessor = createFrameProcessor()
                processors.add(frameProcessor)
                // returning the new processor
                frameProcessor
            } else {
                // all busy, cannot instantiate more, return null
                null
            }
        } else {
            // simply return the first (which is not busy)
            processors.first()
        }

    }

    private fun createFrameProcessor(): FrameProcessor {
        return FrameProcessor(
            size,
            frameType,
            block, // what to do on the frame
            onDone = { // what to do at the end
                //  (sets as last result if the order is correct)
                synchronized(this@FrameStreamProcessor) {
                    if (lastResultToken < orderToken) {
                        lastResult = retrieveResult()
                        lastResultToken = orderToken
                    }
                }
            }
        )
    }
}