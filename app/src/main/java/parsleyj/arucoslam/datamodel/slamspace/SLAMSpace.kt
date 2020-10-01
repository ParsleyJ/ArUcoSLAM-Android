package parsleyj.arucoslam.datamodel.slamspace

import parsleyj.arucoslam.datamodel.*
import parsleyj.arucoslam.flattenVecs
import parsleyj.kotutils.Tuple4
import parsleyj.kotutils.Tuple5
import parsleyj.kotutils.itMap
import parsleyj.kotutils.tuple
import java.lang.Integer.min


class SLAMSpace(
    val dictionary: ArucoDictionary,
    val markerIDs: MonotonicIntList,
    val markerRVects: MonotonicDoubleList,
    val markerTVects: MonotonicDoubleList,
    val commonLength: Double,
) : Iterable<SLAMMarker> {
    private val markerIdToIndexMap = mutableMapOf<Int, Int>()

    init {
        for ((index, id) in markerIDs.withIndex()) {
            markerIdToIndexMap[id] = index
        }
    }


    constructor(
        dictionary: ArucoDictionary,
        commonLength: Double,
        markers: List<SLAMMarker> = emptyList(),
    ) : this(
        dictionary,
        MonotonicIntList(markers.map { it.markerId }),
        MonotonicDoubleList(markers.map { it.pose3d.rotationVector }.flattenVecs()),
        MonotonicDoubleList(markers.map { it.pose3d.translationVector }.flattenVecs()),
        commonLength,
    )

    val size:Int
        get() = markerIdToIndexMap.size

    fun getById(id: Int) = markerIdToIndexMap[id]?.let { getByIndex(it) }

    operator fun get(id: Int) = getById(id)

    fun getByIndex(index: Int): SLAMMarker? = synchronized(this) {
        return if (index in 0 until markerIDs.size) {
            SLAMMarker(
                markerIDs[index],
                Pose3d(
                    rVec = Vec3d(
                        markerRVects[index * 3],
                        markerRVects[index * 3 + 1],
                        markerRVects[index * 3 + 2],
                    ),
                    tVec = Vec3d(
                        markerTVects[index * 3],
                        markerTVects[index * 3 + 1],
                        markerTVects[index * 3 + 2],
                    )
                ),
            )
        } else null
    }

    override fun iterator(): Iterator<SLAMMarker> {
        return (0 until synchronized(this@SLAMSpace){markerIDs.size}).itMap { index ->
            synchronized(this@SLAMSpace) {
                getByIndex(index)!!
            }
        }.iterator()
    }

    fun addIfNotPresent(marker: SLAMMarker): Boolean = synchronized(this) {
        return if (marker.markerId in markerIdToIndexMap) {
            false
        } else {
            val newIndex = markerIDs.size
            markerIDs.add(marker.markerId)
            markerRVects.addFromArray(marker.pose3d.rotationVector.asDoubleArray())
            markerTVects.addFromArray(marker.pose3d.translationVector.asDoubleArray())

            markerIdToIndexMap[marker.markerId] = newIndex
            true
        }
    }

    fun copyToArrays(
        idsArr: IntArray,
        rvects: DoubleArray,
        tvects: DoubleArray,
        confidences: DoubleArray,
    ) = synchronized(this) {
        System.arraycopy(markerIDs.elementData, 0,
            idsArr, 0, min(markerIDs.size, idsArr.size))
        System.arraycopy(markerRVects.elementData, 0,
            rvects, 0, min(markerRVects.size, rvects.size))
        System.arraycopy(markerTVects.elementData, 0,
            tvects, 0, min(markerTVects.size, tvects.size))
    }

    fun asArrays(): Tuple4<IntArray, DoubleArray, DoubleArray, Int> = synchronized(this) {
        return tuple(
            this.markerIDs.elementData,
            this.markerRVects.elementData,
            this.markerTVects.elementData,
            this.size,
        )
    }

}