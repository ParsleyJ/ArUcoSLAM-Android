package parsleyj.arucoslam.framepipeline

import parsleyj.arucoslam.datamodel.Pose3d


data class FrameRecyclableData(
    val foundIDs: IntArray,
    val foundRVecs: DoubleArray,
    val foundTVecs: DoubleArray,
    val estimatedPhonePosition: Pose3d
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FrameRecyclableData

        if (!foundIDs.contentEquals(other.foundIDs)) return false
        if (!foundRVecs.contentEquals(other.foundRVecs)) return false
        if (!foundTVecs.contentEquals(other.foundTVecs)) return false
        if (!estimatedPhonePosition.rotationVector.asDoubleArray()
                .contentEquals(other.estimatedPhonePosition.rotationVector.asDoubleArray())) {
            return false
        }
        if (!estimatedPhonePosition.translationVector.asDoubleArray()
                .contentEquals(other.estimatedPhonePosition.translationVector.asDoubleArray())) {
            return false
        }
        return true
    }

    override fun hashCode(): Int {
        var result = foundIDs.contentHashCode()
        result = 31 * result + foundRVecs.contentHashCode()
        result = 31 * result + foundTVecs.contentHashCode()
        result = 31 * result + estimatedPhonePosition.rotationVector.asDoubleArray().contentHashCode()
        result = 31 * result + estimatedPhonePosition.translationVector.asDoubleArray().contentHashCode()
        return result
    }


}