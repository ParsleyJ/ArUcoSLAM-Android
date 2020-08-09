package parsleyj.arucoslam

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*
import org.opencv.android.BaseLoaderCallback
import org.opencv.android.FixedCameraBridgeViewBase
import org.opencv.android.LoaderCallbackInterface
import org.opencv.android.OpenCVLoader
import org.opencv.core.Mat
import org.opencv.core.Size


const val ArucoSLAMCameraActivityTag = "ArucoSLAMCameraActivity"

const val requiredMarkerCountForCalibrationStep = 10
const val requiredFramesCountForCalibration = 3


class MainActivity : AppCompatActivity(), FixedCameraBridgeViewBase.CvCameraViewListener2 {

    private val dictionary: Long by lazy { genDictionary() }
    private val collectedCorners = mutableListOf<Array<FloatArray>>()
    private val collectedIds = mutableListOf<IntArray>()
    private var imgSize: Size = Size(0.0, 0.0)
    private var cameraMatrix: Mat? = null
    private var distCoeffs: Mat? = null


    private val calibrationBoard: Long by lazy {
        val markersX = 8
        val markersY = 5
        val markerLength = 50f
        val markerSeparation = 20f
        return@lazy genCalibrationBoard(
            markersX,
            markersY,
            markerLength,
            markerSeparation,
            dictionary
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_DENIED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1)
        }

        // Metti la view come visibile
        opencvCamera.visibility = SurfaceView.VISIBLE
        // Registra l'attività this come quella che risponde all'oggetto callback
        opencvCamera.setCvCameraViewListener(this)

        opencvCamera.setMaxFrameSize(2000, 2000)

        // Example of a call to a nativ6e method
//        sample_text.text = stringFromJNI()
    }

    override fun onResume() {
        super.onResume()
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_4_0, this, mLoaderCallback)
    }

    private val mLoaderCallback: BaseLoaderCallback = object : BaseLoaderCallback(this) {
        override fun onManagerConnected(status: Int) = when (status) {
            LoaderCallbackInterface.SUCCESS -> {
                Log.i(ArucoSLAMCameraActivityTag, "OpenCV loaded successfully")
                System.loadLibrary("gnustl_shared")
                System.loadLibrary("native-lib")
                System.loadLibrary("nonfree")
                Toast.makeText(super.mAppContext, ndkLibReadyCheck(), Toast.LENGTH_SHORT).show()
                Log.i(ArucoSLAMCameraActivityTag, "NDK Lib loaded successfully")
                opencvCamera.enableView()
            }
            else -> super.onManagerConnected(status)
        }
    }

    private external fun ndkLibReadyCheck(): String

    private external fun processCameraFrame(
        dictionary: Long,
        cameraMatrixAddr: Long,
        distCoeffsAddr: Long,
        inputMatAddr: Long,
        resultMatAddr: Long
    )


    private external fun genDictionary(): Long

    private external fun genCalibrationBoard(
        markersX: Int,
        markersY: Int,
        markersLength: Float,
        markersSeparation: Float,
        dictionaryAddr: Long
    ): Long


    private fun collectCalibrationCorners(image: Mat) {
        val cornersAcceptor = Array(40) { FloatArray(8) { 0.0f } }
        val idsAcceptor = IntArray(40) { 0 }
        val sizeAcceptor = IntArray(2) { 0 }

        val foundMarkers = NativeMethods.detectCalibrationCorners(
            image.nativeObjAddr,
            dictionary,
            cornersAcceptor,
            idsAcceptor,
            sizeAcceptor,
            40
        )

        if (foundMarkers > requiredMarkerCountForCalibrationStep) {
            val cornersSet: Array<FloatArray> = Array(foundMarkers, cornersAcceptor::get)
            val idSet = IntArray(foundMarkers, idsAcceptor::get)
            imgSize = Size(sizeAcceptor[0].toDouble(), sizeAcceptor[1].toDouble())
            collectedCorners.add(cornersSet)
            collectedIds.add(idSet)
        }
    }

    private fun calibrateAttempt(): Boolean {
        if (collectedIds.size < requiredFramesCountForCalibration) {
            return false
        }
        cameraMatrix = Mat()
        distCoeffs = Mat()
        val calibrate = NativeMethods.calibrate(
            dictionary,
            calibrationBoard,
            collectedCorners.toTypedArray(),
            collectedIds.toTypedArray(),
            imgSize.height.toInt(),
            imgSize.width.toInt(),
            longArrayOf(cameraMatrix!!.nativeObjAddr, distCoeffs!!.nativeObjAddr)
        )
        return true
    }

    override fun onCameraViewStarted(width: Int, height: Int) {

    }

    override fun onCameraViewStopped() {

    }

    private var cachedResultMat: Mat? = null


    override fun onCameraFrame(inputFrame: FixedCameraBridgeViewBase.CvCameraViewFrame?): Mat? {

//        return if(inputFrame != null){
//            val rgba = inputFrame.rgba()
//            val destSize = Size(rgba.size().height, rgba.size().width)
//            val result = Mat.zeros(destSize, rgba.type())
//            processCameraFrame(LongBox(rgba.nativeObjAddr), LongBox(result.nativeObjAddr))
//            result
//        }else null

//        return if (inputFrame != null) {
//            val mRgba = inputFrame.rgba()
//            val mRgbaT = Mat.zeros(mRgba.size(), mRgba.type())
//            val mRgbaF = Mat.zeros(mRgba.size(), mRgba.type())
//            Core.transpose(mRgba, mRgbaT)
//            Imgproc.resize(mRgbaT, mRgbaF, mRgba.size(), 0.0, 0.0, 0)
//            Core.flip(mRgbaF, mRgba, 1)
//            mRgba
//        } else null

        if (inputFrame != null) {
            val inputMat = inputFrame.rgba()

            if (cameraMatrix == null || distCoeffs == null) {
                val calibrateAttemptResult = calibrateAttempt()
                if (!calibrateAttemptResult) {
                    collectCalibrationCorners(inputMat)
                    // attempt to collect this frame marker data for eventual calibration.
                }
                return inputMat
            } else {

                if (cachedResultMat == null) {
                    cachedResultMat = Mat.zeros(inputMat.size(), inputMat.type())
                    Log.d(ArucoSLAMCameraActivityTag, "Created new result mat")
                }
                val result: Mat = cachedResultMat!!


                processCameraFrame(
                    dictionary,
                    cameraMatrix!!.nativeObjAddr,
                    distCoeffs!!.nativeObjAddr,
                    inputMat.nativeObjAddr,
                    result.nativeObjAddr
                )
                return result
            }
        } else {
            return null
        }

    }
}
