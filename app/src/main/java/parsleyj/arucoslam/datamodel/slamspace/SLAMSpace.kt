package parsleyj.arucoslam.datamodel.slamspace

import parsleyj.arucoslam.datamodel.ArucoDictionary
import java.lang.RuntimeException

class SLAMSpace (
    val dictionary: ArucoDictionary,
    val commonLength: Double,
) {
    private val markerMap = mutableMapOf<Int, SLAMMarker>()

    val markers : Iterable<SLAMMarker>
    get() = markerMap.values

    constructor(
        dictionary: ArucoDictionary,
        commonLength: Double,
        markers: List<SLAMMarker>,
    ): this(dictionary, commonLength){
        for (m in markers) {
            markerMap[m.markerId] = m
        }
    }

    fun checkAndAdd(marker:SLAMMarker){
        val markerInMap = markerMap[marker.markerId]
        if(markerInMap == null || markerInMap.markerConfidence < marker.markerConfidence){
            markerMap[marker.markerId] = marker
        }
    }

    operator fun get(id: Int) = markerMap[id]

    fun getMarker(id: Int) = this[id]
}