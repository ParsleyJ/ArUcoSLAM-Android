package parsleyj.arucoslam

import kotlinx.coroutines.Job
import org.opencv.core.Mat
import org.opencv.core.Size


class FrameProcessor<OtherData>(
    frameSize: Size,
    frameType: Int,
    val recycledData: OtherData,
    val block: suspend (Mat, Mat, OtherData) -> Unit,
    val onDone: FrameProcessor<OtherData>.() -> Unit
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

    suspend fun compute() :Job? {
        currentJob = backgroundExec {
            val inMat = inputMat
            if (inMat == null) {
                done()
            } else {
                try {
                    block(inMat, resultMat, recycledData)
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
                done()
            }
        }
        return currentJob
    }


    fun isBusy() = currentJob?.isActive == true

    /**
     * Returns the last validly computed resultMat
     */
    fun retrieveResult() = resultMat
}