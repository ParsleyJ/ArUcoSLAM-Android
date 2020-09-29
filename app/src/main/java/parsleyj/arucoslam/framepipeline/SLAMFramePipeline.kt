package parsleyj.arucoslam.pipeline

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import org.opencv.core.Mat
import org.opencv.core.Size
import parsleyj.arucoslam.*
import parsleyj.arucoslam.datamodel.CalibData
import parsleyj.arucoslam.datamodel.slamspace.SLAMSpace
import parsleyj.kotutils.generateIt



class SLAMFramePipeline(
    private val maxMarkersPerFrame: Int, // how many markers are considered by the detector at each frame
    calibDataSupplier: () -> CalibData,
    private val markerSpace: SLAMSpace,
    private val frameSize: Size,
    private val frameType: Int,
    private val maxWorkers: Int,
    coroutineScope: CoroutineScope = defaultDispatcher,
    private val jobTimeout: Long = 1000L,
) : WorkerPool<Mat, Mat, FrameRecyclableData>(
    maxWorkers,
    { Mat.zeros(frameSize, frameType) },
    instantiateSupportData = { // lambda that tells the FrameStreamProcessor how
        //  to create a recyclable support data structure
        FrameRecyclableData(
            foundIDs = IntArray(maxMarkersPerFrame) { 0 },
            foundRVecs = DoubleArray(maxMarkersPerFrame * 3) { 0.0 },
            foundTVecs = DoubleArray(maxMarkersPerFrame * 3) { 0.0 }
        )
    },
    coroutineScope,
    jobTimeout,
    block = { inMat, outMat, (foundIDs, foundRvecs, foundTvecs) ->

        val foundPosesCount = NativeMethods.detectMarkers(
            calibDataSupplier().cameraMatrix.nativeObjAddr,
            calibDataSupplier().distCoeffs.nativeObjAddr,
            inMat.nativeObjAddr,
            outMat.nativeObjAddr,
            0.079,
            maxMarkersPerFrame,
            foundIDs,
            foundRvecs,
            foundTvecs
        )

        val (fixedMarkerIds, fixedMarkerRvects, fixedMarkerTvects, fixedMarkerConfidences)
            = markerSpace.asArrays()

        if (foundPosesCount > 0) {
            val estimatedPositionRVec = DoubleArray(3) { 0.0 }
            val estimatedPositionTVec = DoubleArray(3) { 0.0 }

            val inliersCount = NativeMethods.estimateCameraPosition(
                calibDataSupplier().cameraMatrix.nativeObjAddr, //in
                calibDataSupplier().distCoeffs.nativeObjAddr, //in
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
    onCannotProcess = { _, input -> input }
)
