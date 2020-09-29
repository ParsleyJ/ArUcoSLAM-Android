package parsleyj.arucoslam.framepipeline



data class FrameRecyclableData(
    val foundIDs: IntArray,
    val foundRVecs: DoubleArray,
    val foundTVecs: DoubleArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FrameRecyclableData

        if (!foundIDs.contentEquals(other.foundIDs)) return false
        if (!foundRVecs.contentEquals(other.foundRVecs)) return false
        if (!foundTVecs.contentEquals(other.foundTVecs)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = foundIDs.contentHashCode()
        result = 31 * result + foundRVecs.contentHashCode()
        result = 31 * result + foundTVecs.contentHashCode()
        return result
    }


}