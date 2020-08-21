package parsleyj.arucoslam

import android.Manifest
import android.app.Activity
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
//        const val requiredMarkerCountForCalibrationStep = 30

        //TODO
        const val requiredMarkerCountForCalibrationStep = 15

        // need to collect marker data from 8 (good) frames before calibration.
        const val requiredFramesForCalibration = 8

        const val TAG = "CalibActivity"

        val currentlyFoundMatrix = doubleArrayOf(
            1040.906372070312, 0.0, 0.0,
            0.0, 162.9655609130859, 0.0,
            0.0, 0.0, 1.0
        )

    }

    private val collectedCorners = mutableListOf<Array<FloatArray>>()
    private val collectedIds = mutableListOf<IntArray>()

    //TODO
    private val collectedFrames = mutableListOf<Mat>()

    private var imgSize: Size = Size(0.0, 0.0)
    private var cameraMatrix by CRCCheckedMat {
        PersistentCameraParameters.retrieveSavedCameraMatrix(this@CalibActivity)
    }
    private var distCoeffs by CRCCheckedMat {
        PersistentCameraParameters.retrieveSavedDistCoefficients(this@CalibActivity)
    }
    private var reprErr = Double.NaN

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
        snap_button.setOnClickListener {
            synchronized(this@CalibActivity) {
                pickThisFrame = true
            }
        }
    }

    override fun onResume() {
        super.onResume()
        OpenCVLoader.initDebug()
        Log.i(TAG, "OpenCV loaded successfully")
        val (mat, dist) = PersistentCameraParameters.loadCameraParameters(applicationContext)
        cameraMatrix = mat
        distCoeffs = dist
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

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.action_accept_calibration -> {
            if (cameraMatrix != null && distCoeffs != null) {
                PersistentCameraParameters.saveCameraParameters(
                    applicationContext,
                    cameraMatrix,
                    distCoeffs
                )
                    .invokeOnCompletion {
                        guiExec {
                            Toast.makeText(this@CalibActivity, "SAVED!", Toast.LENGTH_SHORT).show()
                        }
                    }

                this@CalibActivity.setResult(Activity.RESULT_OK)
                this@CalibActivity.finish()
            } else {
                Toast.makeText(this@CalibActivity, "NOT CALIBRATED YET!", Toast.LENGTH_SHORT).show()
            }
            true
        }
        R.id.action_restart_calibration -> {
            cameraMatrix = null
            distCoeffs = null
            frameStreamProcessor = null
            reprErr = Double.NaN
            discardCounter = 50
            collectedCorners.clear()
            collectedIds.clear()
            collectedFrames.clear()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }


    private fun collectCalibrationCorners(image: Mat) = backgroundExec {
        val cornersAcceptor = Array(40) { FloatArray(8) { 0.0f } }
        val idsAcceptor = IntArray(40) { 0 }
        val sizeAcceptor = IntArray(2) { 0 }
        val inputMat = Mat()




        Imgproc.cvtColor(image, inputMat, Imgproc.COLOR_RGBA2RGB)

        val foundMarkers = NativeMethods.detectCalibrationCorners(
            inputMat.nativeObjAddr,
            cornersAcceptor,
            idsAcceptor,
            sizeAcceptor,
            40
        )

        Log.v(TAG, "foundMarkers == $foundMarkers")

        if (foundMarkers >= requiredMarkerCountForCalibrationStep) {
            Log.v(TAG, "enough found markers, collecting data...")
            val cornersSet: Array<FloatArray> = Array(foundMarkers, cornersAcceptor::get)
            val idSet = IntArray(foundMarkers, idsAcceptor::get)
            synchronized(this) {
                imgSize = Size(sizeAcceptor[0].toDouble(), sizeAcceptor[1].toDouble())
                collectedCorners.add(cornersSet)
                collectedIds.add(idSet)
                //TODO
                collectedFrames.add(inputMat)
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
        calibCamera.disableView()
        synchronized(this) {
//            val camMat = Mat(3, 3, CvType.CV_64FC1, doubleArrayOf(
//                imgSize.width, 0.0, 0.0,
//                0.0, imgSize.height, 0.0,
//                0.0, 0.0, 1.0
//            ).asDoubleBuffer().copyToNewByteBuffer())


            val (camMat, distMat) = Mat(3, 3, CvType.CV_64FC1) to Mat(1, 5, CvType.CV_64FC1)

            //TODO
            val reprErr = NativeMethods.calibrateChArUco(
                collectedCorners.toTypedArray(),
                collectedIds.toTypedArray(),
                collectedFrames.map { it.nativeObjAddr }.toLongArray(),
                imgSize.height.toInt(),
                imgSize.width.toInt(),
                camMat.nativeObjAddr,
                distMat.nativeObjAddr
            )

//            val reprErr = NativeMethods.calibrate(
//                collectedCorners.toTypedArray(),
//                collectedIds.toTypedArray(),
//                imgSize.height.toInt(),
//                imgSize.width.toInt(),
//                camMat.nativeObjAddr,
//                distMat.nativeObjAddr
//            )
            cameraMatrix = camMat
            distCoeffs = distMat
            this.reprErr = reprErr
        }
        calibCamera.enableView()
        guiExec {
            Toast.makeText(this@CalibActivity, "CALIBRATED!", Toast.LENGTH_SHORT).show()
        }


        Log.i(TAG, "cameraMatrix = " + cameraMatrix!!)
        Log.i(TAG, "distCoeffs   = " + distCoeffs!!)
    }

    override fun onCameraViewStarted(width: Int, height: Int) {
    }

    override fun onCameraViewStopped() {
    }

    private var frameCounter = 0L
    private fun countFrame(): Long {
        return frameCounter++
    }

    private var pickThisFrame = false

    var frameStreamProcessor: FrameStreamProcessor? = null
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
                    discardCounter = 50
                } else if (synchronized(this) { pickThisFrame }) {
                    synchronized(this) {
                        pickThisFrame = false
                    }
                    collectCalibrationCorners(inputMat)
                    Log.v(MainActivity.TAG, "Collecting corner request sent")
                }

                return inputMat
            } else {

                if (synchronized(this) { pickThisFrame }) {
                    synchronized(this) {
                        pickThisFrame = false
                    }
                    collectCalibrationCorners(inputMat)
                    Log.v(MainActivity.TAG, "Collecting corner request sent")
                    calibrateAttempt()
                }



                if (frameStreamProcessor == null) {
                    Log.v(MainActivity.TAG, "we have camera matrix and dist coeffs")
                    Log.v(MainActivity.TAG, "started to process camera frame...")
                    frameStreamProcessor = FrameStreamProcessor(
                        inputMat.size(),
                        inputMat.type(),
                        4 // number of parallel processors on frames
                    ) { inMat, outMat, outIdsVec, outRvecs, outTvecs ->
                        NativeMethods.processCameraFrame(
                            cameraMatrix!!.nativeObjAddr,
                            distCoeffs!!.nativeObjAddr,
                            inMat.nativeObjAddr,
                            outMat.nativeObjAddr,
                            (0 until 40).toList().toIntArray(),
                            DoubleArray(40){0.03},
                            MainActivity.DETECTED_MARKERS_MAX_OUTPUT,
                            outIdsVec,
                            outRvecs,
                            outTvecs
                        )
                        Log.v(MainActivity.TAG, "frame processed!")

                        val camMat = cameraMatrix!!
                        val distMat = distCoeffs!!

                        Imgproc.putText(
                            outMat,
                            "[${camMat[0, 0][0].format(5)}, ${camMat[0, 1][0].format(5)}, ${camMat[0, 2][0].format(
                                5
                            )}",
                            Point(30.0, 30.0),
                            Core.FONT_HERSHEY_COMPLEX_SMALL,
                            0.8,
                            Scalar(255.0, 50.0, 50.0),
                            1
                        )

                        Imgproc.putText(
                            outMat,
                            "${camMat[1, 0][0].format(5)}, ${camMat[1, 1][0].format(5)}, ${camMat[1, 2][0].format(
                                5
                            )}",
                            Point(30.0, 50.0),
                            Core.FONT_HERSHEY_COMPLEX_SMALL,
                            0.8,
                            Scalar(255.0, 50.0, 50.0),
                            1
                        )

                        Imgproc.putText(
                            outMat,
                            "${camMat[2, 0][0].format(5)}, ${camMat[2, 1][0].format(5)}, ${camMat[2, 2][0].format(
                                5
                            )}]",
                            Point(30.0, 70.0),
                            Core.FONT_HERSHEY_COMPLEX_SMALL,
                            0.8,
                            Scalar(255.0, 50.0, 50.0),
                            1
                        )

                        Imgproc.putText(
                            outMat,
                            "[${distMat[0, 0][0].format(5)}, ${distMat[0, 1][0].format(5)}, ${distMat[0, 2][0].format(
                                5
                            )}, " +
                                    "${distMat[0, 3][0].format(5)}, ${distMat[0, 4][0].format(5)}]",
                            Point(30.0, 90.0),
                            Core.FONT_HERSHEY_COMPLEX_SMALL,
                            0.8,
                            Scalar(255.0, 50.0, 50.0),
                            1
                        )

                        Imgproc.putText(
                            outMat,
                            "REPR_ERR = $reprErr",
                            Point(30.0, 110.0),
                            Core.FONT_HERSHEY_COMPLEX_SMALL,
                            0.8,
                            Scalar(255.0, 50.0, 50.0),
                            1
                        )

                    }

                }

                frameStreamProcessor!!.supply(inputMat, countFrame())
                Log.d(MainActivity.TAG, "FrameStream usage = ${frameStreamProcessor!!.usage()}")
                return frameStreamProcessor!!.retrieve()
            }
        } else {
            Log.v(MainActivity.TAG, "inputFrame is null, returning null to view")
            return null
        }
    }
}