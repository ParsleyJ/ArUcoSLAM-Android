package parsleyj.arucoslam.framepipeline

import parsleyj.arucoslam.datamodel.Pose3d
import parsleyj.arucoslam.datamodel.Track
import kotlin.math.abs

class PoseValidityConstraints(
    val maxSpeed: Double, // in meters per second
//    val maxAngularSpeed: Double, // in radians per second
) {



    fun estimatedPoseIsValid(
        currentPoseTimestamp: Long,
        currentPoseEstimate: Pose3d,
        track: Track,
        detectedKnownMarkersCount: Int,
        inliersCount: Int,
    ): Boolean {
        //todo frame quality constraints

        val lastPoseWithTimestamp = track.lastPose()

        if (lastPoseWithTimestamp != null) {
            val (lastPose, lastPoseTimestamp) = lastPoseWithTimestamp
            val timeElapsed =
                (currentPoseTimestamp - lastPoseTimestamp).toDouble() / 1000.0 //in seconds
            val distance = (
                    currentPoseEstimate.invert().translationVector
                            - lastPose.invert().translationVector).euclideanNorm()
            val translationSpeed = abs(distance / timeElapsed)
            if (translationSpeed > maxSpeed) {
                return false
            }

            //todo compute angular distance? (???)

            //todo compute angular speed? (???)

            //todo check on angular speed? (???)
        }

        return true
    }
}
