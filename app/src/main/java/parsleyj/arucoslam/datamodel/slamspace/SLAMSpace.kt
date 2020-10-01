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
        get() = synchronized(this){ markerIDs.size }


    fun getByIndex(index: Int): SLAMMarker? = synchronized(this) {
        return if (index in 0 until size) {
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
        return (0 until size).itMap { index ->
            synchronized(this@SLAMSpace) {
                getByIndex(index)!!
            }
        }.iterator()
    }

    fun addIfNotPresent(marker: SLAMMarker): Boolean = synchronized(this) {
        return if (marker.markerId in markerIDs) {
            false
        } else {
            markerIDs.add(marker.markerId)
            markerRVects.addFromArray(marker.pose3d.rotationVector.asDoubleArray())
            markerTVects.addFromArray(marker.pose3d.translationVector.asDoubleArray())
            true
        }
    }

    fun copyToArrays(
        idsArr: IntArray,
        rvects: DoubleArray,
        tvects: DoubleArray,
    ) = synchronized(this) {
        System.arraycopy(markerIDs.elementData, 0,
            idsArr, 0, min(size, idsArr.size))
        System.arraycopy(markerRVects.elementData, 0,
            rvects, 0, min(size, rvects.size))
        System.arraycopy(markerTVects.elementData, 0,
            tvects, 0, min(size, tvects.size))
    }

    fun asArrays():Tuple4<IntArray, DoubleArray, DoubleArray, Int> = synchronized(this) {
        return@synchronized tuple(
            this.markerIDs.elementData,
            this.markerRVects.elementData,
            this.markerTVects.elementData,
            this.size,
        )
    }

    fun removeLastMarker(){
        this.markerIDs.removeLast()
        this.markerRVects.removeLast()
        this.markerTVects.removeLast()
    }

}