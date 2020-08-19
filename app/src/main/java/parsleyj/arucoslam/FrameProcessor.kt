package parsleyj.arucoslam

import kotlinx.coroutines.Job
import org.opencv.core.Mat
import org.opencv.core.Size

class FrameProcessor(
    frameSize: Size,
    frameType: Int,
    val block: suspend (Mat, Mat, IntArray, DoubleArray, DoubleArray) -> Unit,
    val onDone: FrameProcessor.() -> Unit
) {
    private val resultMat: Mat =
        Mat.zeros(frameSize, frameType)
    private var inputMat: Mat? = null
    var orderToken = -1L
    private var currentJob: Job? = null

    private var idsVec = IntArray(MainActivity.DETECTED_MARKERS_MAX_OUTPUT) { -1 }
    private var rvecs = DoubleArray(MainActivity.DETECTED_MARKERS_MAX_OUTPUT * 3) { 0.0 }
    private var tvecs = DoubleArray(MainActivity.DETECTED_MARKERS_MAX_OUTPUT * 3) { 0.0 }


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
                    block(inMat, resultMat, idsVec, rvecs, tvecs)
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