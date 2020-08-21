package parsleyj.arucoslam.datamodel

import android.util.Log

class MarkerTaggedSpace(
    val dictionary: ArucoDictionary,
    val markers: List<FixedMarker>
) {
    companion object {
        /**
         * Generates a tagged space with origin at the center of an aruco board with the specified
         * parameters.
         */
        fun arucoBoard(
            dictionary: ArucoDictionary,
            markersX: Int,
            markersY: Int,
            markerLength: Double,
            markerSeparation: Double
        ): MarkerTaggedSpace {

            val markers = mutableListOf<FixedMarker>()

            val mX = (markersX.toDouble() - 1.0) / 2.0
            val mY = (markersY.toDouble() - 1.0) / 2.0

            val tX = mX * markerLength + mX * markerSeparation
            val tY = -mY * markerLength - mY * markerSeparation

            for (i in 0 until markersX * markersY) {
                val row: Int = i / 8
                val col: Int = i % 8
                val rvec = Vec3d(0.0, 0.0, 0.0)
                val tvec = Vec3d(
                    tX + -col * (markerLength + markerSeparation),
                    tY + row * (markerLength + markerSeparation),
                    0.0
                )
                Log.i("FixedMarkersOnBoard", "id=${i} => tvec=(${tvec.x}, ${tvec.y}, ${tvec.z})")
                markers.add(FixedMarker(i, Pose3d(rvec, tvec), markerLength))
            }



            return MarkerTaggedSpace(dictionary, markers)
        }

        fun threeStackedMarkers(
            dictionary: ArucoDictionary,
            ids: Triple<Int, Int, Int>,
            markerLength: Double,
            markerSeparation: Double
        ): MarkerTaggedSpace {
            return MarkerTaggedSpace(dictionary, listOf(
                FixedMarker(
                    ids.first, Pose3d(
                        rVec = Vec3d(0.0, 0.0, 0.0),
                        tVec = Vec3d(0.0, -markerLength - markerSeparation, 0.0)
                    ), markerLength
                ),
                FixedMarker(
                    ids.second, Pose3d(
                        rVec = Vec3d(0.0, 0.0, 0.0),
                        tVec = Vec3d(0.0, 0.0, 0.0)
                    ), markerLength
                ),
                FixedMarker(
                    ids.third, Pose3d(
                        rVec = Vec3d(0.0, 0.0, 0.0),
                        tVec = Vec3d(0.0, +markerLength + markerSeparation, 0.0)
                    ), markerLength
                )
            ))
        }
    }


    private val markerMap: Map<Int, FixedMarker> by lazy {
        markers.map { it.markerId to it }.toMap()
    }

    operator fun get(id: Int) = markerMap[id]

    fun getMarkerSpecs(id: Int) = this[id]
}