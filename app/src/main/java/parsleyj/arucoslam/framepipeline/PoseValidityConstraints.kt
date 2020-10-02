package parsleyj.arucoslam.framepipeline

import parsleyj.arucoslam.datamodel.Pose3d
import parsleyj.arucoslam.datamodel.Track
import kotlin.math.abs

/**
 * Set of data used to check whether a new found pose is valid.
 *
 * @param minimumInliersRatio a pose is valid if the RANSAC inliers/outliers ratio is higher than this
 * @param maxSpeed a pose is valid if the position did not change more quickly than this
 * @param maxAngularSpeed a pose is valid if the orientation did not change more quickly than this
 */
class PoseValidityConstraints(
    val minimumInliersRatio: Double,
    val maxSpeed: Double, // in meters per second
    val maxAngularSpeed: Double, // in radians per second
) {

    fun estimatedPoseIsValid(
        currentPoseTimestamp: Long,
        currentPoseEstimate: Pose3d,
        track: Track,
        detectedKnownMarkersCount: Int,
        inliersCount: Int,
    ): Boolean {
        if(currentPoseEstimate.rotationVector.asDoubleArray().any(Double::isNaN)){
           return false
        }

        if(currentPoseEstimate.translationVector.asDoubleArray().any(Double::isNaN)){
            return false
        }

        if(detectedKnownMarkersCount <= 0 || inliersCount <= 0){
            return false
        }

        if(inliersCount.toDouble() / detectedKnownMarkersCount.toDouble() < minimumInliersRatio){
            return false
        }

        val lastPoseWithTimestamp = track.lastPose()

        if (lastPoseWithTimestamp != null) {
            val (lastPose, lastPoseTimestamp) = lastPoseWithTimestamp
            val timeElapsed =
                (currentPoseTimestamp - lastPoseTimestamp).toDouble() / 1000.0 //in seconds
            val inverseCurrentPose = currentPoseEstimate.invert()
            val inverseLastPose = lastPose.invert()
            val distance = (inverseCurrentPose.translationVector
                            - inverseLastPose.translationVector).euclideanNorm()
            val translationSpeed = abs(distance / timeElapsed)
            if (translationSpeed > maxSpeed) {
                return false
            }

            val angularDistance = inverseCurrentPose.rotationVector
                .angularDistance(inverseLastPose.rotationVector)

            val rotationSpeed = abs(angularDistance / timeElapsed)
            if(rotationSpeed > maxAngularSpeed){
                return false
            }
        }

        return true
    }
}
