package parsleyj.arucoslam.framepipeline

import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Size
import parsleyj.arucoslam.pipeline.ProcessorPool

class MatBlenderComponent(
    matSize: Size,
    matType: Int,
    alpha: Double,
    beta: Double,
):ProcessorPool<
        Unit,
        Pair<Mat, Mat>,
        Mat,
        Unit,> (
    Unit,
    3,
    {Mat.zeros(matSize,matType)},
    {},
    onCannotProcess ={ _, (leftImg, _) -> leftImg },
    block = { _, (leftImg, rightImg), resultImg, _ ->
        Core.addWeighted(leftImg, alpha, rightImg, beta, 0.0, resultImg)
    },
)