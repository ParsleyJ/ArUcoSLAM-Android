package parsleyj.arucoslam.datamodel.fixedSpace

import android.util.Log
import parsleyj.arucoslam.datamodel.ArucoDictionary
import parsleyj.arucoslam.datamodel.Pose3d
import parsleyj.arucoslam.datamodel.Vec3d
import parsleyj.arucoslam.datamodel.slamspace.SLAMMarker
import parsleyj.arucoslam.datamodel.slamspace.SLAMSpace
import parsleyj.arucoslam.get
import parsleyj.arucoslam.list
import java.lang.RuntimeException

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
            id1: Int,
            id2: Int,
            id3: Int,
            markerLength: Double,
            markerSeparation: Double
        ): MarkerTaggedSpace {
            return MarkerTaggedSpace(
                dictionary, list[
                        FixedMarker(
                            id1, Pose3d(
                                rVec = Vec3d(0.0, 0.0, 0.0),
                                tVec = Vec3d(0.0, -markerLength - markerSeparation, 0.0)
                            ), markerLength
                        ),
                        FixedMarker(
                            id2, Pose3d(
                                rVec = Vec3d(0.0, 0.0, 0.0),
                                tVec = Vec3d(0.0, 0.0, 0.0)
                            ), markerLength
                        ),
                        FixedMarker(
                            id3, Pose3d(
                                rVec = Vec3d(0.0, 0.0, 0.0),
                                tVec = Vec3d(0.0, +markerLength + markerSeparation, 0.0)
                            ), markerLength
                        )
                ]
            )
        }

        fun singleMarker(
            dictionary: ArucoDictionary,
            id: Int,
            markerLength: Double
        ) = MarkerTaggedSpace(
            dictionary,
            list[FixedMarker(id, Pose3d(Vec3d.ORIGIN, Vec3d.ORIGIN), markerLength)]
        )

    }


    private val markerMap: Map<Int, FixedMarker> by lazy {
        markers.map { it.markerId to it }.toMap()
    }

    operator fun get(id: Int) = markerMap[id]

    infix fun movedTo(translationVector: Vec3d): MarkerTaggedSpace {
        return this movedTo Pose3d(Vec3d.ORIGIN, translationVector)
    }

    infix fun movedTo(pose: Pose3d): MarkerTaggedSpace {
        return MarkerTaggedSpace(
            dictionary,
            markers.map { it movedTo pose }
        )
    }


    operator fun plus(otherSpace: MarkerTaggedSpace): MarkerTaggedSpace {
        if (otherSpace.dictionary != this.dictionary) {
            throw RuntimeException(
                "Cannot perform union of two marker tagged spaces " +
                        "with different ArUco dictionaries"

            )
        }

        val resultMarkers = mutableListOf<FixedMarker>()
        resultMarkers.addAll(this.markers)
        for (m in otherSpace.markers) {
            if (resultMarkers.any { it.markerId == m.markerId && it != m }) {
                throw RuntimeException(
                    "Cannot perform union of two marker tagged spaces which contain markers with same" +
                            "ids but different carachteristics"
                )
            }
            resultMarkers.add(m)
        }

        return MarkerTaggedSpace(this.dictionary, resultMarkers)
    }

    operator fun minus(ids:Collection<Int>): MarkerTaggedSpace {
        return MarkerTaggedSpace(dictionary, markers.filter { it.markerId !in ids })
    }

    fun getMarkerSpecs(id: Int) = this[id]

    fun toSLAMSpace(commonLength: Double = -1.0): SLAMSpace {
        return SLAMSpace(
            dictionary,
            if(commonLength<=0) {
                markers.firstOrNull()?.markerSideLength?:0.1
            } else {
                commonLength
            },
            markers.map{ SLAMMarker(it.markerId, it.pose3d, 1.0) }
        )
    }
}