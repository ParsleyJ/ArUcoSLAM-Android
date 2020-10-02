package parsleyj.arucoslam.framepipeline

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import org.opencv.core.Core.FONT_HERSHEY_COMPLEX_SMALL
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import parsleyj.arucoslam.*
import parsleyj.arucoslam.NativeMethods.*
import parsleyj.arucoslam.datamodel.CalibData
import parsleyj.arucoslam.datamodel.Pose3d
import parsleyj.arucoslam.datamodel.Track
import parsleyj.arucoslam.datamodel.Vec3d
import parsleyj.arucoslam.datamodel.slamspace.SLAMMarker
import parsleyj.arucoslam.datamodel.slamspace.SLAMSpace
import parsleyj.arucoslam.pipeline.RenderingWorkerPool
import java.time.Instant
import java.util.*
import kotlin.math.PI

/**
 * A specialized version of a [RenderingWorkerPool] used to process the frames for the SLAM app.
 *
 * @param maxMarkersPerFrame max number of markers detected by the detector at each frame
 * @param calibDataSupplier function that supplies the phone's camera parameters
 * @param markerSpace mutable data structure that defines a 3D world of ArUco markers with their poses
 * @param track mutable data structure used to store the history of positions
 * @param poseValidityConstraints set of data used to define the constraints to determine if a new
 *                                computed pose is valid
 * @param frameSize size of the frames
 * @param frameType OpenCV type of the frames
 * @param maxWorkers maximum number of parallel workers
 * @param coroutineScope the scope on which the jobs of the workers are launched
 * @param jobTimeout time in milliseconds after which a job is cancelled
 * @param mapCameraRotation orientation of the virtual camera used to render the map
 * @param mapCameraTranslation position of the virtual camera used to render the map
 * @param isFullScreenMode callback used to check if the map should be rendered in fullscreen mode
 */
class SLAMFrameRenderer(
    private val maxMarkersPerFrame: Int,
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
    isFullScreenMode: () -> Boolean,
) : RenderingWorkerPool<Mat, Mat, FrameRecyclableData>(
    maxWorkers,
    { Mat.zeros(frameSize, frameType) },
    supplyEmptySupportData = { // lambda that tells the FrameStreamProcessor how
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
    block = block@{ inMat, outMat, (foundIDs, foundRvecs, foundTvecs, estimatedPose), frameNumber, frameTimeStamp ->
        fun staleJob() = System.currentTimeMillis() - frameTimeStamp > jobTimeout

        // get the known markers as arrays (no copy is done here)
        val (
            fixedMarkerIds,
            fixedMarkerRvects,
            fixedMarkerTvects,
            fixedMarkerCount,
        ) = markerSpace.asArrays()

        val (estimatedPositionRVec, estimatedPositionTVec) = estimatedPose.asPairOfVec3d()

        if (staleJob()) {
            return@block
        }

        val fullScreenMode = isFullScreenMode()
        val mapSizeInPixels = inMat.rows() / 2

        if (fullScreenMode) {
            val lastPoseWithTimestamp: Pair<Pose3d, Long>? = track.lastPose()
            val lastPoseAvailable = lastPoseWithTimestamp != null
            lastPoseWithTimestamp?.let { (lastPose, _) -> lastPose.copyTo(estimatedPose) }

            renderMap(
                // currently known markers:
                markerSpace.commonLength,
                fixedMarkerRvects,
                fixedMarkerTvects,
                fixedMarkerCount,

                // pose of the "virtual" map camera
                mapCameraRotation.asDoubleArray(),
                mapCameraTranslation.asDoubleArray(),

                // horizontal and vertical FOV angles of the virtual camera
                PI / 2.0,
                PI / 2.0,

                // horizontal and vertical sensor aperture of the virtual camera
                2400.0,
                2400.0,

                // info about the current phone pose
                if (lastPoseAvailable) PHONE_POSE_STATUS_LAST_KNOWN else PHONE_POSE_STATUS_UNAVAILABLE,
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
                outMat.nativeObjAddr,
                true //full screen mode
            )
        } else {

            // find all the markers in the image and estimate their poses w.r.t. camera
            val foundMarkersCount = detectMarkers(
                markerSpace.dictionary.toInt(),
                calibDataSupplier().cameraMatrix.nativeObjAddr,
                calibDataSupplier().distCoeffs.nativeObjAddr,
                inMat.nativeObjAddr,
                outMat.nativeObjAddr,
                markerSpace.commonLength,
                maxMarkersPerFrame,
                foundIDs,
                foundRvecs,
                foundTvecs
            )


            val newPhonePoseAvailable: Boolean
            val validNewPhonePoseAvailable: Boolean
            val lastPoseWithTimestamp: Pair<Pose3d, Long>? = track.lastPose()
            val lastPoseAvailable = lastPoseWithTimestamp != null

            if (staleJob()) {
                return@block
            }
            // if markers are found
            if (foundMarkersCount > 0) {
                // attempt to estimate a new phone pose
                val inliersCount = estimateCameraPosition(
                    calibDataSupplier().cameraMatrix.nativeObjAddr, //in
                    calibDataSupplier().distCoeffs.nativeObjAddr, //in
                    outMat.nativeObjAddr, //in&out
                    fixedMarkerCount, //in
                    fixedMarkerIds, //in
                    fixedMarkerRvects, //in
                    fixedMarkerTvects, //in
                    markerSpace.commonLength, //in
                    foundMarkersCount, //in
                    foundIDs, //in
                    foundRvecs, //in
                    foundTvecs, //in
                    estimatedPositionRVec.asDoubleArray(), //out
                    estimatedPositionTVec.asDoubleArray(), //out
                    0.05, //(RANSAC) tvec inlier threshold (meters)
                    0.1, //(RANSAC) tvec outlier probability
                    PI/8.0, //(RANSAC) rvec inlier threshold (radians)
                    0.1, //(RANSAC) rvec outlier pobability,
                    100, //(RANSAC) max RANSAC iterations
                    0.9, //(RANSAC) target probability to get the optimal model
                )
                newPhonePoseAvailable = true
                if (staleJob()) {
                    return@block
                }
                var knownMarkersFoundCount = 0
                for (i in 0 until foundMarkersCount) {
                    if (foundIDs[i] in fixedMarkerIds) {
                        knownMarkersFoundCount++
                    }
                }

                // evaluate the validity of the estimate;
                // if the estimated pose is valid
                validNewPhonePoseAvailable = poseValidityConstraints.estimatedPoseIsValid(
                    frameTimeStamp,
                    estimatedPose,
                    track,
                    knownMarkersFoundCount,
                    inliersCount
                )
                if (staleJob()) {
                    return@block
                }
            } else {
                newPhonePoseAvailable = false
                validNewPhonePoseAvailable = false
            }

            Imgproc.putText(
                outMat,
                "KNOWN MARKERS: ${markerSpace.size}",
                Point(30.0, 30.0),
                FONT_HERSHEY_COMPLEX_SMALL,
                0.8,
                Scalar(50.0, 255.0, 50.0),
                1
            )

            Imgproc.putText(
                outMat,
                "FRAME NUMBER = $frameNumber",
                Point(30.0, 70.0),
                FONT_HERSHEY_COMPLEX_SMALL,
                0.8,
                Scalar(50.0, 255.0, 50.0),
                1
            )
            if (staleJob()) {
                return@block
            }
            if (newPhonePoseAvailable) {
                Log.d("SLAMFramePipeline", "pose estimate: $estimatedPose " +
                        "at time ${Date.from(Instant.ofEpochMilli(frameTimeStamp))}" +
                        "for frame $frameNumber")
            }
            if (staleJob()) {
                return@block
            }
            if (validNewPhonePoseAvailable) {
                // update the track
                track.addPose(estimatedPose, frameTimeStamp)

                // update new markers found
                for (i in 0 until foundMarkersCount) {
                    if (staleJob()) {
                        return@block
                    }
                    markerSpace.addIfNotPresent(
                        SLAMMarker(
                            foundIDs[i],
                            estimatedPose * Pose3d(
                                Vec3d(
                                    foundRvecs[i * 3],
                                    foundRvecs[i * 3 + 1],
                                    foundRvecs[i * 3 + 2],
                                ),
                                Vec3d(
                                    foundTvecs[i * 3],
                                    foundTvecs[i * 3 + 1],
                                    foundTvecs[i * 3 + 2],
                                )
                            ).invertInPlace(),
                        )
                    )
                }
            }
            if (staleJob()) {
                return@block
            }


            val phonePoseStatus = when {
                // found a new pose estimate, but it's invalid
                newPhonePoseAvailable &&
                        !validNewPhonePoseAvailable -> PHONE_POSE_STATUS_INVALID
                // found a new pose estimate and it's valid
                validNewPhonePoseAvailable -> PHONE_POSE_STATUS_UPDATED
                // not found a new pose estimate, however the last pose is known
                lastPoseAvailable -> PHONE_POSE_STATUS_LAST_KNOWN
                // no pose found yet
                else -> PHONE_POSE_STATUS_UNAVAILABLE
            }

            if (phonePoseStatus == PHONE_POSE_STATUS_LAST_KNOWN) {
                lastPoseWithTimestamp?.let { (lastPose, _) -> lastPose.copyTo(estimatedPose) }
            }

            renderMap(
                // currently known markers:
                markerSpace.commonLength,
                fixedMarkerRvects,
                fixedMarkerTvects,
                fixedMarkerCount,

                // pose of the "virtual" map camera
                mapCameraRotation.asDoubleArray(),
                mapCameraTranslation.asDoubleArray(),

                // horizontal and vertical FOV angles of the virtual camera
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
                outMat.nativeObjAddr,
                false
            )
            if (staleJob()) {
                return@block
            }
        }
    },
    onCannotProcess = { _, input -> input }
)

