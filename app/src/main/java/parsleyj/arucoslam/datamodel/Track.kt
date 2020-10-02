package parsleyj.arucoslam.datamodel

import parsleyj.arucoslam.NativeMethods
import parsleyj.kotutils.with

class Track(
    val recentPoseInterval: Long,
    val recentPosesMaxSize: Int,
) {


    val recentPosesRvecs: DoubleArray = DoubleArray(recentPosesMaxSize * 3)
    val recentPosesTvecs: DoubleArray = DoubleArray(recentPosesMaxSize * 3)
    val recentPosesTimestamps: MutableList<Long> = mutableListOf()
    val longTermTrackRvecs: ContiguousDoubleList = ContiguousDoubleList(0) { 0.0 }
    val longTermTrackTvecs: ContiguousDoubleList = ContiguousDoubleList(0) { 0.0 }
    val longTermTrackTimestamps: MutableList<Long> = mutableListOf()

    var recentPosesSize = 0
        private set


    // used as recyclable data structures for compress()
    private var centroidRvect = DoubleArray(3) { 0.0 }
    private var centroidTvect = DoubleArray(3) { 0.0 }

    fun addPose(
        pose: Pose3d,
        timestamp: Long = System.currentTimeMillis(),
    ): Unit = synchronized(this) {
        val oldestRecentPoseTimestamp = if (recentPosesTimestamps.isEmpty()) {
            null
        } else {
            recentPosesTimestamps[0]
        }
        if (recentPosesSize >= recentPosesMaxSize || (oldestRecentPoseTimestamp != null
                    && timestamp - oldestRecentPoseTimestamp >= recentPoseInterval)
        ) {
            compress()
        }
        recentPosesRvecs[recentPosesSize * 3] = pose.rotationVector.x
        recentPosesRvecs[recentPosesSize * 3 + 1] = pose.rotationVector.y
        recentPosesRvecs[recentPosesSize * 3 + 2] = pose.rotationVector.z
        recentPosesTvecs[recentPosesSize * 3] = pose.translationVector.x
        recentPosesTvecs[recentPosesSize * 3 + 1] = pose.translationVector.y
        recentPosesTvecs[recentPosesSize * 3 + 2] = pose.translationVector.z
        recentPosesTimestamps.add(timestamp)
        recentPosesSize++
    }

    fun lastPose(): Pair<Pose3d, Long>? = synchronized(this) {
        when {
            recentPosesSize > 0 -> {
                val lastIndex = recentPosesSize - 1
                return Pose3d(
                    Vec3d(
                        recentPosesRvecs[lastIndex * 3],
                        recentPosesRvecs[lastIndex * 3 + 1],
                        recentPosesRvecs[lastIndex * 3 + 2],
                    ),
                    Vec3d(
                        recentPosesTvecs[lastIndex * 3],
                        recentPosesTvecs[lastIndex * 3 + 1],
                        recentPosesTvecs[lastIndex * 3 + 2],
                    )
                ) with recentPosesTimestamps[lastIndex]
            }
            longTermTrackTimestamps.isNotEmpty() -> {
                val lastIndex = longTermTrackTimestamps.size - 1
                return Pose3d(
                    Vec3d(
                        longTermTrackRvecs[lastIndex * 3],
                        longTermTrackRvecs[lastIndex * 3 + 1],
                        longTermTrackRvecs[lastIndex * 3 + 2],
                    ),
                    Vec3d(
                        longTermTrackTvecs[lastIndex * 3],
                        longTermTrackTvecs[lastIndex * 3 + 1],
                        longTermTrackTvecs[lastIndex * 3 + 2],
                    )
                ) with longTermTrackTimestamps[lastIndex]
            }
            else -> {
                return null
            }
        }
    }

    private fun compress() {
        if(recentPosesSize!= 0) {
            if (recentPosesSize == 1) {
                centroidRvect[0] = recentPosesRvecs[0]
                centroidRvect[1] = recentPosesRvecs[1]
                centroidRvect[2] = recentPosesRvecs[2]
                centroidTvect[0] = recentPosesTvecs[0]
                centroidTvect[1] = recentPosesTvecs[1]
                centroidTvect[2] = recentPosesTvecs[2]
            } else {
                NativeMethods.poseCentroid(
                    recentPosesRvecs,
                    recentPosesTvecs,
                    0,
                    recentPosesSize,
                    centroidRvect,
                    centroidTvect,
                )
            }
            longTermTrackRvecs.addFromArray(centroidRvect)
            longTermTrackTvecs.addFromArray(centroidTvect)
            longTermTrackTimestamps.add(
                recentPosesTimestamps.average().toLong()
            )
            recentPosesTimestamps.clear()
            recentPosesSize = 0
        }
    }
}