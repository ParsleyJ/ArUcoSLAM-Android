package parsleyj.arucoslam.framepipeline

import org.opencv.core.Mat
import org.opencv.core.Size
import parsleyj.arucoslam.MainActivity
import parsleyj.arucoslam.NativeMethods
import parsleyj.arucoslam.datamodel.CalibData
import parsleyj.arucoslam.pipeline.ProcessorPool
import parsleyj.kotutils.with

class PoseEstimatorComponent(
    matSize: Size,
    matType: Int,
    cameraParameters: CalibData,
) : ProcessorPool<
        Pair<Size, Int>,// type of parametric data
        Mat, // type of input data
        Pair<Mat, FoundPoses?>, // type of resulting data
        FrameRecyclableData>( // type of recyclable data structures
    matSize with matType,
    3,
    instantiateSupportData = {
        FrameRecyclableData(
            foundIDs = IntArray(MainActivity.DETECTED_MARKERS_MAX_OUTPUT) { 0 },
            foundRVecs = DoubleArray(MainActivity.DETECTED_MARKERS_MAX_OUTPUT * 3) { 0.0 },
            foundTVecs = DoubleArray(MainActivity.DETECTED_MARKERS_MAX_OUTPUT * 3) { 0.0 },
        )
    },
    block = { (_, _), inMat, (outMat, trackUpdaterInput), (foundIDs, foundRvecs, foundTvecs) ->
        val foundPosesCount = NativeMethods.detectMarkers(
            cameraParameters.cameraMatrix.nativeObjAddr,
            cameraParameters.distCoeffs.nativeObjAddr,
            inMat.nativeObjAddr,
            outMat.nativeObjAddr,
            0.079,
            MainActivity.DETECTED_MARKERS_MAX_OUTPUT,
            foundIDs,
            foundRvecs,
            foundTvecs
        )


        if (foundPosesCount > 0) {
            val estimatedPositionRVec = DoubleArray(3) { 0.0 }
            val estimatedPositionTVec = DoubleArray(3) { 0.0 }

            val inliersCount = NativeMethods.estimateCameraPosition(
                cameraParameters.cameraMatrix.nativeObjAddr, //in
                cameraParameters.distCoeffs.nativeObjAddr, //in
                outMat.nativeObjAddr, //in&out
                fixedMarkerIds, //in
                fixedMarkerRvects, //in
                fixedMarkerTvects, //in
                fixedMarkerConfidences, //in
                markerSpace.commonLength, //in
                foundPosesCount, //in
                foundIDs, //in
                foundRvecs, //in
                foundTvecs, //in
                estimatedPositionRVec, //out
                estimatedPositionTVec //out
            )


        }
    },
    supplyEmptyOutput = { Mat.zeros(matSize, matType) with null },
    onCannotProcess = { lastResult, input -> input with lastResult.second }
)