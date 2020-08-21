package parsleyj.arucoslam

import android.content.Context
import android.preference.PreferenceManager
import android.util.Log
import org.opencv.core.CvType
import org.opencv.core.Mat
import java.nio.DoubleBuffer

object PersistentCameraParameters {
    private const val CAMERA_MATRIX_F_X_KEY = "CAMERA_MATRIX_F_X_KEY"
    private const val CAMERA_MATRIX_F_Y_KEY = "CAMERA_MATRIX_F_Y_KEY"
    private const val CAMERA_MATRIX_C_X_KEY = "CAMERA_MATRIX_C_X_KEY"
    private const val CAMERA_MATRIX_C_Y_KEY = "CAMERA_MATRIX_C_Y_KEY"
    private const val DISTORTION_COEFFICIENTS_ARR_KEY = "DISTORTION_COEFFICIENTS_ARR_KEY"
    private const val TAG = "PersistentCameraParams"

    fun retrieveSavedCameraMatrix(context: Context): Mat? {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
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

    fun retrieveSavedDistCoefficients(context: Context): Mat? {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
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

    fun loadCameraParameters(context: Context): Pair<Mat?, Mat?> {
        val cameraMatrix = retrieveSavedCameraMatrix(context)
        val distCoeffs = retrieveSavedDistCoefficients(context)

        return Pair(cameraMatrix, distCoeffs)
    }

    fun saveCameraParameters(context: Context, camMat: Mat?, distMat: Mat?) = backgroundExec {
        if (camMat == null || distMat == null) {
            return@backgroundExec
        }
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

        val editor = sharedPreferences.edit()
        synchronized(this) {
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
}