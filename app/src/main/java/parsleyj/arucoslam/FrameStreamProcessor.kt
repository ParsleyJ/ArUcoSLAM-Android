package parsleyj.arucoslam

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import org.opencv.core.Mat
import org.opencv.core.Size

/**
 * Manages a pool of "Frame Processors" in order to supply each frame of a video feed, process it
 * concurrently, and returning the last frame processed.
 */
class FrameStreamProcessor<FPOtherDataT>(
    private val frameSize: Size,
    private val frameType: Int,
    private val maxProcessors: Int,
    private val instantiateOtherData: ()->FPOtherDataT,
    private val jobTimeout: Long = 1000L,
    private val block: suspend (Mat, Mat, FPOtherDataT) -> Unit
) {

    private var lastResult = Mat.zeros(frameSize, frameType)
    private var lastResultToken = -1L
    private val processors = mutableListOf<FrameProcessor<FPOtherDataT>>()


    fun supply(input: Mat?, token: Long) {
        // if there are no free processors, do not process the frame.
        val proc = getFreeProcessor()
        if (proc == null) {
            // we cannot find a free processor.
            // let's just return the input mat unprocessed
            lastResult = input
        } else {
            // we have a free frame processor
            // we assign the input frame to it
            proc.assignFrame(input, token)
            // let's asynchronously start the job
            backgroundExec {
                val job = proc.compute()
                //if after 'jobTimeout' milliseconds the job is not done, it is cancelled
                delay(jobTimeout)
                job?.cancel()
            }
        }
    }

    /**
     * Returns the percentage of free processors
     */
    fun usage(): Double {
        return (processors.filter { it.isBusy() }.count().toDouble()
                / maxProcessors.toDouble()) * 100.0
    }

    /**
     * Retrieves the last computed result
     */
    fun retrieve(): Mat {
        return lastResult
    }

    /**
     * Sorts processors by bringing the free ones at the beginning
     */
    private fun sortProcessors() {
        processors.sortBy { if (it.isBusy()) 1 else -1 }
    }

    /**
     * It finds a free processor if available. If not, it creates new frame processor.
     * If the creation is not possible, it returns null.
     */
    private fun getFreeProcessor(): FrameProcessor<FPOtherDataT>? {
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

    private fun createFrameProcessor(): FrameProcessor<FPOtherDataT> {
        return FrameProcessor(
            frameSize,
            frameType,
            instantiateOtherData(),
            block, // what to do when a input frame frame is supplied
            onDone = { // what to do when a frame processing is done
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