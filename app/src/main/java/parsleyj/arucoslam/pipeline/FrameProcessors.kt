package parsleyj.arucoslam.pipeline

import kotlinx.coroutines.CoroutineScope
import org.opencv.core.Mat
import org.opencv.core.Size
import parsleyj.arucoslam.FrameProcessor
import parsleyj.arucoslam.defaultDispatcher
import parsleyj.kotutils.with


class FrameProcessorPool<OtherDataT>(
    private val frameSize: Size,
    private val frameType: Int,
    private val maxProcessors: Int,
    private val instantiateOtherData: () -> OtherDataT,
    coroutineScope: CoroutineScope = defaultDispatcher,
    private val jobTimeout: Long = 1000L,
    private val block: suspend (Size, Int, Mat, Mat, OtherDataT) -> Unit,
) : ProcessorPool<Pair<Size, Int>, Mat, Mat, OtherDataT>(
    frameSize with frameType,
    maxProcessors,
    { Mat.zeros(frameSize, frameType) },
    instantiateOtherData,
    coroutineScope,
    jobTimeout,
    block = { (size, type), inputMat, resultMat, other ->
        block(size, type, inputMat, resultMat, other)
    },
    onCannotProcess = { _, input -> input }
)


