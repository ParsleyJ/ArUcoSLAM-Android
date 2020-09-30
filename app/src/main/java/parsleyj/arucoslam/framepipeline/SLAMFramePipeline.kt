package parsleyj.arucoslam.framepipeline

import kotlinx.coroutines.CoroutineScope
import org.opencv.core.Mat
import org.opencv.core.Size
import parsleyj.arucoslam.*
import parsleyj.arucoslam.datamodel.CalibData
import parsleyj.arucoslam.datamodel.Pose3d
import parsleyj.arucoslam.datamodel.Track
import parsleyj.arucoslam.datamodel.Vec3d
import parsleyj.arucoslam.datamodel.slamspace.SLAMMarker
import parsleyj.arucoslam.datamodel.slamspace.SLAMSpace
import parsleyj.arucoslam.pipeline.WorkerPipelinePool
import kotlin.math.PI


class SLAMFramePipeline(
    private val maxMarkersPerFrame: Int, // how many markers are considered by the detector at each frame
    calibDataSupplier: () -> CalibData,
    private val markerSpace: SLAMSpace,
    private val track: Track,
    private val poseValidityConstraints: PoseValidityConstraints,
    private val frameSize: Size,
    private val frameType: Int,
    private val maxWorkers: Int,
    coroutineScope: CoroutineScope = defaultDispatcher,
    private val jobTimeout: Long = 1000L,
    var mapCameraRotation: Vec3d = Vec3d(-PI / 2.0, 0.0, 0.0),
    var mapCameraTranslation: Vec3d = Vec3d(0.0, -1.0, 10.0),
) : WorkerPipelinePool<Mat, Mat, FrameRecyclableData>(
    maxWorkers,
    { Mat.zeros(frameSize, frameType) },
    instantiateSupportData = { // lambda that tells the FrameStreamProcessor how
        //  to create a recyclable support data structure
        FrameRecyclableData(
            foundIDs = IntArray(maxMarkersPerFrame) { 0 },
            foundRVecs = DoubleArray(maxMarkersPerFrame * 3) { 0.0 },
            foundTVecs = DoubleArray(maxMarkersPerFrame * 3) { 0.0 },
            estimatedPhonePosition = Pose3d(
                Vec3d(0.0, 0.0, 0.0),
                Vec3d(0.0, 0.0, 0.0)
            )
        )
    },
    coroutineScope,
    jobTimeout,
    block = { inMat, outMat, (foundIDs, foundRvecs, foundTvecs, estimatedPose) ->
        val currentTimestamp = System.currentTimeMillis()
        val (estimatedPositionRVec, estimatedPositionTVec) = estimatedPose.asPairOfVec3d()

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

        // get the known markers as arrays (no copy is done here)
        val (
            fixedMarkerIds,
            fixedMarkerRvects,
            fixedMarkerTvects,
            fixedMarkerConfidences,
            fixedMarkerCount,
        ) = markerSpace.asArrays()

        val newPhonePoseAvailable: Boolean
        val validNewPhonePoseAvailable: Boolean
        val lastPoseWithTimestamp: Pair<Pose3d, Long>? = track.lastPose()
        val lastPoseAvailable = lastPoseWithTimestamp != null
        val renderedPose: Pose3d?

        // if markers are found
        if (foundMarkersCount > 0) {
            // attempt to estimate a new phone pose
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
                estimatedPositionRVec.asDoubleArray(), //out
                estimatedPositionTVec.asDoubleArray(), //out
            )

            newPhonePoseAvailable = true

            var knownMarkersFoundCount = 0
            for(i in 0 until foundMarkersCount){
                if(foundIDs[i] in fixedMarkerIds){
                    knownMarkersFoundCount++
                }
            }

            // evaluate the validity of the estimate;
            // if the estimated pose is valid
            validNewPhonePoseAvailable = poseValidityConstraints.estimatedPoseIsValid(
                currentTimestamp,
                estimatedPose,
                track,
                knownMarkersFoundCount,
                inliersCount
            )

        } else {
            newPhonePoseAvailable = false
            validNewPhonePoseAvailable = false
        }

        if(validNewPhonePoseAvailable){
            // update the track
            track.addPose(estimatedPose, currentTimestamp)

            // update new markers found
            for(i in 0 until foundMarkersCount){
                if(foundIDs[i] !in fixedMarkerIds){
                    markerSpace.addIfNotPresent(
                        SLAMMarker(
                            foundIDs[i],
                            estimatedPose * Pose3d(
                                Vec3d(
                                    foundRvecs[i*3],
                                    foundRvecs[i*3+1],
                                    foundRvecs[i*3+2],
                                ),
                                Vec3d(
                                    foundTvecs[i*3],
                                    foundTvecs[i*3+1],
                                    foundTvecs[i*3+2],
                                )
                            ).invert(), //TODO invert in place
                            1.0 //TODO confidence
                        )
                    )
                }
            }
        }

        val mapSizeInPixels = inMat.rows() / 2

        val phonePoseStatus = when {
            // found a new pose estimate, but it's invalid
            newPhonePoseAvailable &&
                    !validNewPhonePoseAvailable -> NativeMethods.PHONE_POSE_STATUS_INVALID
            // found a new pose estimate and it's valid
            validNewPhonePoseAvailable -> NativeMethods.PHONE_POSE_STATUS_UPDATED
            // not found a new pose estimate, however the last pose is known
            lastPoseAvailable -> NativeMethods.PHONE_POSE_STATUS_LAST_KNOWN
            // no pose found yet
            else -> NativeMethods.PHONE_POSE_STATUS_UNAVAILABLE
        }

        NativeMethods.renderMap(
            // currently known markers:
            fixedMarkerRvects,
            fixedMarkerTvects,

            // pose of the "virtual" map camera
            mapCameraRotation.asDoubleArray(),
            mapCameraTranslation.asDoubleArray(),

            // horizontal and vertical FOV of the virtual camera
            PI / 2.0,
            PI / 2.0,

            // horizontal and vertical sensor aperture of the virtual camera
            2400.0,
            2400.0,

            // info about the current phone pose
            phonePoseStatus,
            estimatedPositionRVec.asDoubleArray(),
            estimatedPositionTVec.asDoubleArray(),

            // history of positions
            track.longTermTrackTimestamps.size,
            track.longTermTrackRvecs.elementData,
            track.longTermTrackTvecs.elementData,

            // size and topLeft corner position of the map box
            mapSizeInPixels,
            mapSizeInPixels,
            inMat.cols() - mapSizeInPixels,
            inMat.rows() - mapSizeInPixels,

            // mat on which the map box will be rendered
            outMat.nativeObjAddr
        )
    },
    onCannotProcess = { _, input -> input }
)

