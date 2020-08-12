package parsleyj.arucoslam

import org.opencv.core.Mat
import org.opencv.core.Size

class FrameStream(
    private val size: Size,
    private val frameType: Int,
    private val maxProcessors: Int,
    private val block: suspend (Mat, Mat) -> Unit
) {
    private var lastResult = Mat.zeros(size, frameType)
    var lastResultToken = -1L
    private val processors = mutableListOf<FrameProcessor>()

    fun supply(input: Mat?, token: Long) {
        // if there are no free processors, discard the frame
        val proc = getFreeProcessor() ?: return
        proc.assignFrame(input, token)
        proc.compute()
    }

    fun usage(): Double {
        return processors.size.toDouble() / maxProcessors.toDouble()
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
                synchronized(this@FrameStream) {
                    if (lastResultToken < orderToken) {
                        lastResult = retrieveResult()
                        lastResultToken = orderToken
                    }
                }
            }
        )
    }
}