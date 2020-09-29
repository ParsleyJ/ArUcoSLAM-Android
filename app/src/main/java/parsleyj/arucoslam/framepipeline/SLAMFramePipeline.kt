package parsleyj.arucoslam.framepipeline

import kotlinx.coroutines.CoroutineScope
import org.opencv.core.Mat
import org.opencv.core.Size
import parsleyj.arucoslam.*
import parsleyj.arucoslam.datamodel.CalibData
import parsleyj.arucoslam.datamodel.Vec3d
import parsleyj.arucoslam.datamodel.slamspace.SLAMSpace
import parsleyj.arucoslam.pipeline.WorkerPool
import kotlin.math.PI


val mapCameraRotation = Vec3d(-PI / 2.0, 0.0, 0.0).asDoubleArray()
val mapCameraTranslation = Vec3d(0.0, -1.0, 10.0).asDoubleArray()

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

        // find all the markers in the image and estimate their poses w.r.t. camera
        val foundMarkersCount = NativeMethods.detectMarkers(
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

        // get the known markers as arrays
        val (
            fixedMarkerIds,
            fixedMarkerRvects,
            fixedMarkerTvects,
            fixedMarkerConfidences,
            fixedMarkerCount,
        ) = markerSpace.asArrays()


        if (foundMarkersCount > 0) {
            val estimatedPositionRVec = DoubleArray(3) { 0.0 }
            val estimatedPositionTVec = DoubleArray(3) { 0.0 }

            val inliersCount = NativeMethods.estimateCameraPosition(
                calibDataSupplier().cameraMatrix.nativeObjAddr, //in
                calibDataSupplier().distCoeffs.nativeObjAddr, //in
                outMat.nativeObjAddr, //in&out
                fixedMarkerCount, //in
                fixedMarkerIds, //in
                fixedMarkerRvects, //in
                fixedMarkerTvects, //in
                fixedMarkerConfidences, //in
                markerSpace.commonLength, //in
                foundMarkersCount, //in
                foundIDs, //in
                foundRvecs, //in
                foundTvecs, //in
                estimatedPositionRVec, //out
                estimatedPositionTVec //out
            )
        }

        val mapSizeInPixels = inMat.rows() / 2
        NativeMethods.renderMap(
            fixedMarkerRvects,
            fixedMarkerTvects,
            mapCameraRotation,
            mapCameraTranslation,
            Vec3d.ORIGIN.asDoubleArray(),
            Vec3d.ORIGIN.asDoubleArray(),
            PI/2.0,
            PI/2.0,
            2400.0,
            2400.0,
            mapSizeInPixels,
            mapSizeInPixels,
            inMat.cols() - mapSizeInPixels,
            inMat.rows() - mapSizeInPixels,
            outMat.nativeObjAddr
        )
    },
    onCannotProcess = { _, input -> input }
)
