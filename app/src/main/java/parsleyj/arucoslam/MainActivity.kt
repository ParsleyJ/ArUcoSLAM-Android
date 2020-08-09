package parsleyj.arucoslam

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
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
import org.opencv.imgproc.Imgproc
import org.opencv.imgproc.Imgproc.cvtColor


const val ArucoSLAMCameraActivityTag = "ArucoSLAMCameraActivity"

const val requiredMarkerCountForCalibrationStep = 10


class MainActivity : AppCompatActivity(), FixedCameraBridgeViewBase.CvCameraViewListener2 {


    private val collectedCorners = mutableListOf<Array<FloatArray>>()
    private val collectedIds = mutableListOf<IntArray>()
    private var imgSize: Size = Size(0.0, 0.0)
    private var cameraMatrix: Mat? = null
    private var distCoeffs: Mat? = null



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
        cameraMatrixAddr: Long,
        distCoeffsAddr: Long,
        inputMatAddr: Long,
        resultMatAddr: Long
    )




    private fun collectCalibrationCorners(image: Mat) {
        Thread{
            val cornersAcceptor = Array(40) { FloatArray(8) { 0.0f } }
            val idsAcceptor = IntArray(40) { 0 }
            val sizeAcceptor = IntArray(2) { 0 }
            val inputMat2 = Mat()
            cvtColor(image, inputMat2, Imgproc.COLOR_RGBA2RGB)

            val foundMarkers = NativeMethods.detectCalibrationCorners(
                inputMat2.nativeObjAddr,
                cornersAcceptor,
                idsAcceptor,
                sizeAcceptor,
                40
            )

            Log.v(ArucoSLAMCameraActivityTag, "foundMarkers == $foundMarkers")

            if (foundMarkers > requiredMarkerCountForCalibrationStep) {
                Log.v(ArucoSLAMCameraActivityTag, "enough found markers, collecting data...")
                val cornersSet: Array<FloatArray> = Array(foundMarkers, cornersAcceptor::get)
                val idSet = IntArray(foundMarkers, idsAcceptor::get)
                synchronized(this) {
                    imgSize = Size(sizeAcceptor[0].toDouble(), sizeAcceptor[1].toDouble())
                    collectedCorners.add(cornersSet)
                    collectedIds.add(idSet)
                    Unit
                }
                Log.v(ArucoSLAMCameraActivityTag, "collected data")
            } else {
                Log.v(ArucoSLAMCameraActivityTag, "not enough found markers, discarded frame")
            }

        }.start()
    }

    private fun calibrateAttempt(){
        Thread {
            synchronized(this) {
                cameraMatrix = Mat()
                distCoeffs = Mat()
                NativeMethods.calibrate(
                    collectedCorners.toTypedArray(),
                    collectedIds.toTypedArray(),
                    imgSize.height.toInt(),
                    imgSize.width.toInt(),
                    longArrayOf(cameraMatrix!!.nativeObjAddr, distCoeffs!!.nativeObjAddr)
                )
            }
            Looper.prepare()
            Toast.makeText(this, "CALIBRATED!", Toast.LENGTH_SHORT).show()
        }.start()
    }

    override fun onCameraViewStarted(width: Int, height: Int) {

    }

    override fun onCameraViewStopped() {

    }

    private var cachedResultMat: Mat? = null


    private var discardCounter = 50




    override fun onCameraFrame(inputFrame: FixedCameraBridgeViewBase.CvCameraViewFrame?): Mat? {
        if (discardCounter > 0) {
            discardCounter--
            Log.v(ArucoSLAMCameraActivityTag, "discardedFrame")
            return inputFrame?.rgba()
        }


        if (inputFrame != null) {
            Log.v(ArucoSLAMCameraActivityTag, "inputFrame != null")
            val inputMat = inputFrame.rgba()

            if (cameraMatrix == null || distCoeffs == null) {
                Log.v(ArucoSLAMCameraActivityTag, "camera matrix and dist coeffs are null")

                if(collectedIds.size>3){
                    Log.v(ArucoSLAMCameraActivityTag, "Starting calibration attempt")
                    calibrateAttempt()
                }else{
                    collectCalibrationCorners(inputMat)
                    Log.v(ArucoSLAMCameraActivityTag, "Collecting corner request sent")
                }

                discardCounter = 50
                return inputMat
            } else {

                if (cachedResultMat == null) {
                    cachedResultMat = Mat.zeros(inputMat.size(), inputMat.type())
                    Log.d(ArucoSLAMCameraActivityTag, "Created new result mat")
                }
                val result: Mat = cachedResultMat!!

                Log.v(ArucoSLAMCameraActivityTag, "we have camera matrix and dist coeffs")
                Log.v(ArucoSLAMCameraActivityTag, "started to process camera frame...")
                processCameraFrame(
                    cameraMatrix!!.nativeObjAddr,
                    distCoeffs!!.nativeObjAddr,
                    inputMat.nativeObjAddr,
                    result.nativeObjAddr
                )
                Log.v(ArucoSLAMCameraActivityTag, "frame processed!")
                return result
//                return inputMat
            }
        } else {
            Log.v(ArucoSLAMCameraActivityTag, "inputFrame is null, returning null to view")
            return null
        }

    }
}
