package parsleyj.arucoslam

import parsleyj.arucoslam.datamodel.Pose3d
import java.util.*

/**
 * Class that manages an history of positions in order to draw a path in space.
 */
class PositionHistory(
    positionPrecisionThreshold: Double
) {

    data class EstimatedPose3d(val pose: Pose3d, val confidence: Double)

    /**
     * Maps of time -> (confidence, pose) sorted by keeping the most recent position at beginning
     */
    private val recentHistory = TreeMap<Long, EstimatedPose3d> { t1, t2 -> (t2-t1).toInt() }

    fun submit(pose: Pose3d, confidence: Double) {
        recentHistory[System.currentTimeMillis()] = EstimatedPose3d(pose, confidence)
    }

    fun lastPositionAt(time:Long): EstimatedPose3d?{
        return recentHistory.floorEntry(time)?.value
    }


    private fun update(){

    }




}