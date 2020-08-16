package parsleyj.arucoslam

import kotlinx.coroutines.Job
import org.opencv.core.Mat
import org.opencv.core.Size

class FrameProcessor(
    frameSize: Size,
    frameType: Int,
    val block: suspend (Mat, Mat) -> Unit,
    val onDone: FrameProcessor.() -> Unit
) {
    private val resultMat: Mat =
        Mat.zeros(frameSize, frameType)
    private var inputMat: Mat? = null
    var orderToken = -1L
    private var currentJob: Job? = null

    fun assignFrame(inputMat: Mat?, token: Long) {
        if (inputMat != null) {
            this.inputMat = inputMat
        }
        orderToken = token
    }

    private fun done() {
        this.onDone()
    }

    suspend fun compute() {
        currentJob = backgroundExec {
            val inMat = inputMat
            if (inMat == null) {
                done()
            } else {
                try {
                    block(inMat, resultMat)
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
                done()
            }
        }
    }


    fun isBusy() = currentJob?.isActive == true

    /**
     * Returns the last validly computed resultMat
     */
    fun retrieveResult() = resultMat
}