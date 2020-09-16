package parsleyj.arucoslam.framepipeline

import org.opencv.core.Mat
import org.opencv.core.Size
import parsleyj.arucoslam.pipeline.ProcessorPool

class MapRendererComponent(
    matSize: Size,
    matType: Int,
):ProcessorPool<
        Unit,
        Triple<Track, FoundPoses?, AllPoses>,
        Mat,
        Unit>(
    Unit,
    3,
    {Mat.zeros(matSize, matType)},
    {},
    onCannotProcess = { lastResult, _ -> lastResult },
    block = { _, (inputTrack, foundPoses, allPoses), resultMat, _ ->
        TODO()
    }
)