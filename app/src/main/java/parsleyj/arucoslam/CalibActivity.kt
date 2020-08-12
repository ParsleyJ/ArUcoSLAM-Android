package parsleyj.arucoslam

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_calib.*
import org.opencv.android.FixedCameraBridgeViewBase
import org.opencv.android.OpenCVLoader
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

class CalibActivity : AppCompatActivity(), FixedCameraBridgeViewBase.CvCameraViewListener2 {

    companion object {
        // when collecting frames for calibration, any frame with less than 30 markers detected
        // is discarded.
        const val requiredMarkerCountForCalibrationStep = 30

        // need to collect marker data from 3 (good) frames before calibration.
        const val requiredFramesForCalibration = 4

        const val TAG = "CalibActivity"


    }

    private val collectedCorners = mutableListOf<Array<FloatArray>>()
    private val collectedIds = mutableListOf<IntArray>()
    private var imgSize: Size = Size(0.0, 0.0)
    private var cameraMatrix by CRCCheckedMat {
        PersistentCameraParameters.retrieveSavedCameraMatrix(this@CalibActivity)
    }
    private var distCoeffs by CRCCheckedMat {
        PersistentCameraParameters.retrieveSavedDistCoefficients(this@CalibActivity)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calib)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_DENIED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1)
        }
        calibCamera.visibility = SurfaceView.VISIBLE
        calibCamera.setCvCameraViewListener(this)
        calibCamera.setMaxFrameSize(2000, 2000)
        setTitle("Camera Calibration")
    }

    override fun onResume() {
        super.onResume()
        OpenCVLoader.initDebug()
        Log.i(TAG, "OpenCV loaded successfully")
        PersistentCameraParameters.loadCameraParameters(applicationContext)
        System.loadLibrary("gnustl_shared")
        System.loadLibrary("native-lib")
        System.loadLibrary("nonfree")
        Log.i(TAG, "NDK Libs loaded successfully")
        calibCamera.enableView()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.calib_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) =
        when (item.itemId) {
            R.id.action_accept_calibration -> {
                if(cameraMatrix!=null && distCoeffs!=null){
                    Toast.makeText(this@CalibActivity, "NOT CALIBRATED YET!", Toast.LENGTH_SHORT).show()
                }else {
                    this@CalibActivity.setResult(Activity.RESULT_OK)
                    this@CalibActivity.finish()
                }
                true
            }
            R.id.action_restart_calibration -> {
                cameraMatrix = null
                distCoeffs = null
                frameStream = null
                discardCounter = 50
                collectedCorners.clear()
                collectedIds.clear()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }


    private fun collectCalibrationCorners(image: Mat) = backgroundExec {
        val cornersAcceptor = Array(40) { FloatArray(8) { 0.0f } }
        val idsAcceptor = IntArray(40) { 0 }
        val sizeAcceptor = IntArray(2) { 0 }
        val inputMat2 = Mat()
        Imgproc.cvtColor(image, inputMat2, Imgproc.COLOR_RGBA2RGB)

        val foundMarkers = NativeMethods.detectCalibrationCorners(
            inputMat2.nativeObjAddr,
            cornersAcceptor,
            idsAcceptor,
            sizeAcceptor,
            40
        )

        Log.v(TAG, "foundMarkers == $foundMarkers")

        if (foundMarkers > requiredMarkerCountForCalibrationStep) {
            Log.v(TAG, "enough found markers, collecting data...")
            val cornersSet: Array<FloatArray> = Array(foundMarkers, cornersAcceptor::get)
            val idSet = IntArray(foundMarkers, idsAcceptor::get)
            synchronized(this) {
                imgSize = Size(sizeAcceptor[0].toDouble(), sizeAcceptor[1].toDouble())
                collectedCorners.add(cornersSet)
                collectedIds.add(idSet)
                Unit
            }
            Log.v(TAG, "collected data")
            guiExec {
                Toast.makeText(
                    this,
                    "FRAME COLLECTED (" + collectedIds.size + "/" + requiredFramesForCalibration + ")",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else {
            Log.v(TAG, "not enough found markers, discarded frame")
        }

    }

    private fun calibrateAttempt() = backgroundExec {
        synchronized(this) {
            val camMat = Mat()
            val distMat = Mat()
            NativeMethods.calibrate(
                collectedCorners.toTypedArray(),
                collectedIds.toTypedArray(),
                imgSize.width.toInt(),
                imgSize.height.toInt(),
                longArrayOf(camMat.nativeObjAddr, distMat.nativeObjAddr)
            )
            cameraMatrix = camMat
            distCoeffs = distMat
        }

        PersistentCameraParameters.saveCameraParameters(
            applicationContext,
            cameraMatrix,
            distCoeffs
        )
            .invokeOnCompletion {
                guiExec {
                    Toast.makeText(this@CalibActivity, "CALIBRATED!", Toast.LENGTH_SHORT).show()
                }
            }

        Log.i(TAG, "cameraMatrix = " + cameraMatrix!!)
        Log.i(TAG, "distCoeffs   = " + distCoeffs!!)
    }

    override fun onCameraViewStarted(width: Int, height: Int) {
    }

    override fun onCameraViewStopped() {
    }


    private var outRvec = doubleArrayOf(0.0, 0.0, 0.0)
    private var outTvec = doubleArrayOf(0.0, 0.0, 0.0)
    private var frameCounter = 0L
    private fun countFrame(): Long {
        return frameCounter++
    }

    var frameStream: FrameStream? = null
    private var discardCounter = 50

    override fun onCameraFrame(inputFrame: FixedCameraBridgeViewBase.CvCameraViewFrame?): Mat? {
        if (discardCounter > 0) {
            discardCounter--
            Log.v(MainActivity.TAG, "discardedFrame")
            return inputFrame?.rgba()
        }

        if (inputFrame != null) {
            Log.v(MainActivity.TAG, "inputFrame != null")
            val inputMat = inputFrame.rgba()

            val camMatrix = cameraMatrix
            val camDistCoeffs = distCoeffs
            if (camMatrix == null || camDistCoeffs == null) {
                Log.v(MainActivity.TAG, "camera matrix and dist coeffs are null")

                if (collectedIds.size >= requiredFramesForCalibration) {
                    Log.v(MainActivity.TAG, "Starting calibration attempt")
                    calibrateAttempt()
                } else {
                    collectCalibrationCorners(inputMat)
                    Log.v(MainActivity.TAG, "Collecting corner request sent")
                }

                discardCounter = 50
                return inputMat
            } else {
                if (frameStream == null) {
                    Log.v(MainActivity.TAG, "we have camera matrix and dist coeffs")
                    Log.v(MainActivity.TAG, "started to process camera frame...")
                    frameStream = FrameStream(
                        inputMat.size(),
                        inputMat.type(),
                        4 // number of parallel processors on frames
                    ) { inMat, outMat ->
                        NativeMethods.processCameraFrame(
                            cameraMatrix!!.nativeObjAddr,
                            distCoeffs!!.nativeObjAddr,
                            inMat.nativeObjAddr,
                            outMat.nativeObjAddr,
                            outRvec,
                            outTvec
                        )
                        Log.v(MainActivity.TAG, "frame processed!")


                    }
                }

                frameStream!!.supply(inputMat, countFrame())
                Log.d(MainActivity.TAG, "FrameStream usage = ${frameStream!!.usage()}")
                return frameStream!!.retrieve()
            }
        } else {
            Log.v(MainActivity.TAG, "inputFrame is null, returning null to view")
            return null
        }
    }
}