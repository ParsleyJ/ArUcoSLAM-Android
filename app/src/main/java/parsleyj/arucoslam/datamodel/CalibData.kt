package parsleyj.arucoslam.datamodel

import android.content.Context
import android.util.Log
import org.opencv.core.CvType
import org.opencv.core.Mat
import parsleyj.arucoslam.*
import java.nio.DoubleBuffer

/**
 * A data structure containing camera parameter data obtained from a calibration.
 */
class CalibData(
    cameraMatrix: Mat,
    distCoeffs: Mat,
    val resolutionWidth: Double,
    val resolutionHeight: Double
) {
    private val cameraMatrixData = doubleArray[0.0, 0.0, 0.0, 0.0]
    private val distCoeffData: DoubleArray
    val fx: Double
        get() = cameraMatrixData[0]
    val fy: Double
        get() = cameraMatrixData[1]
    val cx: Double
        get() = cameraMatrixData[2]
    val cy: Double
        get() = cameraMatrixData[3]

    init {
        cameraMatrix.withCameraMatrix { fx, fy, cx, cy ->
            cameraMatrixData[0, 1, 2, 3] = list[fx, fy, cx, cy]
        }

        val tmpArr = DoubleArray(distCoeffs.total().toInt()) { 0.0 }
        distCoeffs.get(0, 0, tmpArr)
        distCoeffData = tmpArr
    }

    val cameraMatrix by CRCCheckedMat {
        Mat(
            3, 3, CvType.CV_64FC1, initMat[
                    fx, 0.0, cx,
                    0.0, fy, cy,
                    0.0, 0.0, 1.0
            ]
        )
    }

    val distCoeffs by CRCCheckedMat {
        Mat(
            1, 5, CvType.CV_64FC1, distCoeffData
                .asDoubleBuffer()
                .copyToNewByteBuffer()
        )
    }

    fun scaleToResolution(newWidth: Double, newHeight: Double): CalibData {
        return CalibData(
            cameraMatrix.scaleCameraMatrixResolution(
                resolutionWidth,
                resolutionHeight,
                newWidth,
                newHeight
            ),
            distCoeffs,
            newWidth,
            newHeight
        )
    }

    fun saveData(context: Context){
        saveCameraParameters(context, cameraMatrix, distCoeffs, resolutionWidth, resolutionHeight)
    }


    companion object{
        private const val CALIB_WIDTH_KEY = "CALIB_WIDTH_KEY"
        private const val CALIB_HEIGHT_KEY = "CALIB_HEIGHT_KEY"
        private const val CAMERA_MATRIX_F_X_KEY = "CAMERA_MATRIX_F_X_KEY"
        private const val CAMERA_MATRIX_F_Y_KEY = "CAMERA_MATRIX_F_Y_KEY"
        private const val CAMERA_MATRIX_C_X_KEY = "CAMERA_MATRIX_C_X_KEY"
        private const val CAMERA_MATRIX_C_Y_KEY = "CAMERA_MATRIX_C_Y_KEY"
        private const val DISTORTION_COEFFICIENTS_ARR_KEY = "DISTORTION_COEFFICIENTS_ARR_KEY"
        private const val TAG = "PersistentCameraParams"

        private fun retrieveSavedCameraMatrix(context: Context): Mat? {
            val sharedPreferences = context.getSharedPreferences(
                "parsleyj.arucoslam.CALIB_DATA", Context.MODE_PRIVATE
            )
            val fX = sharedPreferences.getFloat(CAMERA_MATRIX_F_X_KEY, 0f).toDouble()
            val fY = sharedPreferences.getFloat(CAMERA_MATRIX_F_Y_KEY, 0f).toDouble()
            val cX = sharedPreferences.getFloat(CAMERA_MATRIX_C_X_KEY, 0f).toDouble()
            val cY = sharedPreferences.getFloat(CAMERA_MATRIX_C_Y_KEY, 0f).toDouble()
            if (fX == 0.0 && fY == 0.0 && cX == 0.0 && cY == 0.0) {
                Log.i(TAG, "no saved camera matrix found, calibration needed")
                return null
            }
            val cameraMatrixBuffer = DoubleBuffer.allocate(9)


            list[
                    fX, 0.0, cX,
                    0.0, fY, cY,
                    0.0, 0.0, 1.0
            ].withIndex().forEach { (index, value) -> cameraMatrixBuffer.put(index, value) }

            return Mat(3, 3, CvType.CV_64FC1, cameraMatrixBuffer.copyToNewByteBuffer())
        }

        private fun retrieveSavedDistCoefficients(context: Context): Mat? {
            val sharedPreferences = context.getSharedPreferences(
                "parsleyj.arucoslam.CALIB_DATA", Context.MODE_PRIVATE
            )
            val size =
                sharedPreferences.getInt(DISTORTION_COEFFICIENTS_ARR_KEY + "_size", 0)
            if (size <= 0) {
                Log.i(TAG, "no distortion coefficients array found, calibration needed")
                return null
            }

            val coeffs: List<Double> = (0 until size).map { i ->
                sharedPreferences.getFloat(DISTORTION_COEFFICIENTS_ARR_KEY + "_" + i, 0f)
                    .toDouble()
            }

            return Mat(
                1, 5, CvType.CV_64FC1, DoubleBuffer.wrap(coeffs.toDoubleArray())
                    .copyToNewByteBuffer()
            )
        }

        fun loadCameraParameters(context: Context): CalibData? {
            val sharedPreferences = context.getSharedPreferences(
                "parsleyj.arucoslam.CALIB_DATA", Context.MODE_PRIVATE
            )
            val cameraMatrix = retrieveSavedCameraMatrix(context)
            val distCoeffs = retrieveSavedDistCoefficients(context)

            val width = sharedPreferences.getFloat(CALIB_WIDTH_KEY, -1.0f).toDouble()
            val height = sharedPreferences.getFloat(CALIB_HEIGHT_KEY, -1.0f).toDouble()

            if(cameraMatrix == null || distCoeffs == null || width < 0.0 || height < 0.0){
                Log.i(TAG, "could not load necessary data from preference store.")
                return null
            }

            return CalibData(cameraMatrix, distCoeffs, width, height)
        }

        fun saveCameraParameters(context: Context, camMat: Mat?, distMat: Mat?, width:Double, height:Double) = backgroundExec {
            if (camMat == null || distMat == null) {
                return@backgroundExec
            }
            val sharedPreferences = context.getSharedPreferences(
                "parsleyj.arucoslam.CALIB_DATA", Context.MODE_PRIVATE
            )

            val editor = sharedPreferences.edit()
            synchronized(this) {
                editor.putFloat(CALIB_WIDTH_KEY, width.toFloat())
                editor.putFloat(CALIB_HEIGHT_KEY, height.toFloat())
                editor.putFloat(CAMERA_MATRIX_F_X_KEY, camMat[0, 0][0].toFloat())
                editor.putFloat(CAMERA_MATRIX_F_Y_KEY, camMat[1, 1][0].toFloat())
                editor.putFloat(CAMERA_MATRIX_C_X_KEY, camMat[0, 2][0].toFloat())
                editor.putFloat(CAMERA_MATRIX_C_Y_KEY, camMat[1, 2][0].toFloat())
                editor.putInt(DISTORTION_COEFFICIENTS_ARR_KEY + "_size", distMat.cols())
                for (i in 0 until distMat.cols()) {
                    editor.putFloat(
                        DISTORTION_COEFFICIENTS_ARR_KEY + "_" + i,
                        distMat[0, i][0].toFloat()
                    )
                }
            }
            editor.apply()
        }


        val xiaomiMiA1RearCamera = CalibData(
            cameraMatrix = Mat(
                3, 3, CvType.CV_64FC1, initMat[
                        1032.8829095671827, 0.0, 633.4940320469335,
                        0.0, 1031.2002634811117, 353.94940985894704,
                        0.0, 0.0, 1.0
                ]
            ),
            distCoeffs = Mat(
                1, 5, CvType.CV_64FC1, initMat[
                    0.138768877522313,
                    -0.6601745137551255,
                    -0.0007624348695516956,
                    -0.000016450434278321715,
                    0.819925063225912
                ]
            ),
            resolutionWidth = 1280.0,
            resolutionHeight = 720.0
        ).scaleToResolution(
            newWidth = 864.0,
            newHeight = 480.0
        )
    }

}

fun Mat.scaleCameraMatrixResolution(
    oldWidth: Double,
    oldHeight: Double,
    newWidth: Double,
    newHeight: Double
): Mat {
    val wratio = newWidth / oldWidth
    val hratio = newHeight / oldHeight
    return this.withCameraMatrix { fx, fy, cx, cy ->
        Mat(
            3, 3, CvType.CV_64FC1, initMat[
                    fx * wratio, 0.0, cx * wratio,
                    0.0, fy * hratio, cy * hratio,
                    0.0, 0.0, 1.0
            ]
        )
    }
}