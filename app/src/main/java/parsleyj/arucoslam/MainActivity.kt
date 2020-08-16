package parsleyj.arucoslam

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.SurfaceView
import android.view.WindowManager
import kotlinx.android.synthetic.main.activity_main.*
import org.opencv.android.FixedCameraBridgeViewBase
import org.opencv.android.OpenCVLoader
import org.opencv.core.Core.FONT_HERSHEY_COMPLEX_SMALL
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc.putText
import kotlin.math.roundToLong


class MainActivity : AppCompatActivity(), FixedCameraBridgeViewBase.CvCameraViewListener2 {

    companion object {
        const val TAG = "MainActivity"
        const val CALIBRATION_REQUEST = 1
        const val DETECTED_MARKERS_MAX_OUTPUT = 50

    }

    // since Android GC deletes underlying data in field Mats, i use this property delegator
    // to reload the correct data from storage when the mats become corrupt.
    private var cameraMatrix by CRCCheckedMat {
        PersistentCameraParameters.retrieveSavedCameraMatrix(applicationContext)
    }
    private var distCoeffs by CRCCheckedMat {
        PersistentCameraParameters.retrieveSavedDistCoefficients(applicationContext)
    }
    private val calibSizeRatio = (480.0 / 720.0)//(864.0 / 1280.0)

    fun setFoundCamParams(){
        cameraMatrix = Mat(3,3, CvType.CV_64FC1, doubleArrayOf(
            1032.8829095671827, 0.0, 633.4940320469335*calibSizeRatio,
            0.0, 1031.2002634811117, 353.94940985894704*calibSizeRatio,
            0.0, 0.0, 1.0
        ).asDoubleBuffer().copyToNewByteBuffer())
        distCoeffs = Mat(1, 5, CvType.CV_64FC1, doubleArrayOf(
            0.138768877522313,
            -0.6601745137551255,
            -0.0007624348695516956,
            -0.000016450434278321715,
            0.819925063225912
        ).asDoubleBuffer().copyToNewByteBuffer())
        PersistentCameraParameters.saveCameraParameters(this, cameraMatrix, distCoeffs)
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
        opencvCamera.visibility = SurfaceView.VISIBLE
        opencvCamera.setCvCameraViewListener(this)
        opencvCamera.setMaxFrameSize(2000, 2000)
    }




    override fun onResume() {
        super.onResume()
        OpenCVLoader.initDebug()
        Log.i(TAG, "OpenCV loaded successfully")
        setFoundCamParams()
//        setCameraParameters(PersistentCameraParameters.loadCameraParameters(applicationContext))
        System.loadLibrary("gnustl_shared")
        System.loadLibrary("native-lib")
        System.loadLibrary("nonfree")
        Log.i(TAG, "NDK Libs loaded successfully")
        opencvCamera.enableView()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) =
        when (item.itemId) {
            R.id.action_start_calibration -> {
                startActivityForResult(Intent(this, CalibActivity::class.java), CALIBRATION_REQUEST)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    private fun setCameraParameters(pair: Pair<Mat?, Mat?>){
        val (cm, dcs) = pair
        cameraMatrix = cm
        distCoeffs = dcs
        Log.i(TAG, "Returned from calibration. cameraMatrix = $cm")
        Log.i(TAG, "Returned from calibration. distCoeffs   = $dcs")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) =
        when (requestCode) {
            CALIBRATION_REQUEST -> {
                setCameraParameters(PersistentCameraParameters.loadCameraParameters(applicationContext))
            }
            else -> {
            }
        }

    override fun onCameraViewStarted(width: Int, height: Int) {
        if(cameraMatrix == null || distCoeffs == null) {
            startActivityForResult(Intent(this, CalibActivity::class.java), CALIBRATION_REQUEST)
        }
    }

    override fun onCameraViewStopped() {

    }


    private var outIdsVec = IntArray(DETECTED_MARKERS_MAX_OUTPUT) { 0 }
    private var outRvecs = DoubleArray(DETECTED_MARKERS_MAX_OUTPUT*3) {0.0}
    private var outTvecs = DoubleArray(DETECTED_MARKERS_MAX_OUTPUT*3) {0.0}

    private var frameCounter = 0L

    private fun countFrame(): Long {
        return frameCounter++
    }


    var frameStreamProcessor: FrameStreamProcessor? = null


    override fun onCameraFrame(inputFrame: FixedCameraBridgeViewBase.CvCameraViewFrame?): Mat? {
        if (inputFrame != null) {
            Log.v(TAG, "inputFrame != null")
            val inputMat = inputFrame.rgba()

            val camMatrix = cameraMatrix
            val camDistCoeffs = distCoeffs
            if (camMatrix == null || camDistCoeffs == null) {
                Log.v(TAG, "camera matrix and dist coeffs are null")
            } else {
                if (frameStreamProcessor == null) {
                    Log.v(TAG, "we have camera matrix and dist coeffs")
                    Log.v(TAG, "started to process camera frame...")
//                    val inMat = inputMat
//                    val outMat = Mat.zeros(inMat.size(), inMat.type())
                    frameStreamProcessor = FrameStreamProcessor(
                        inputMat.size(),
                        inputMat.type(),
                        2 // number of parallel processors on frames
                    ) { inMat, outMat ->
                        NativeMethods.processCameraFrame(
                            cameraMatrix!!.nativeObjAddr,
                            distCoeffs!!.nativeObjAddr,
                            inMat.nativeObjAddr,
                            outMat.nativeObjAddr,
                            DETECTED_MARKERS_MAX_OUTPUT,
                            outIdsVec,
                            outRvecs,
                            outTvecs
                        )
                        Log.v(TAG, "frame processed!")

                        putText(
                            outMat,
                            "RVECT(${
                            (outRvecs[0] * 180.0 / Math.PI).roundToLong().toDouble().format(0, 4)
                            },${
                            (outRvecs[1] * 180.0 / Math.PI).roundToLong().toDouble().format(0, 4)
                            },${
                            (outRvecs[2] * 180.0 / Math.PI).roundToLong().toDouble().format(0, 4)
                            })",
                            Point(30.0, 30.0),
                            FONT_HERSHEY_COMPLEX_SMALL,
                            0.8,
                            Scalar(255.0, 50.0, 50.0),
                            1
                        )

                        putText(
                            outMat,
                            "TVECT(${
                            (outTvecs[0] * calibSizeRatio).format(7, 5)
                            },${
                            (outTvecs[1] * calibSizeRatio).format(7, 5)
                            },${
                            (outTvecs[2] * calibSizeRatio).format(7, 5)
                            })",
                            Point(30.0, 50.0),
                            FONT_HERSHEY_COMPLEX_SMALL,
                            0.8,
                            Scalar(255.0, 50.0, 50.0),
                            1
                        )

                        putText(
                            outMat,
                            "ID = ${outIdsVec[0]}",
                            Point(30.0, 70.0),
                            FONT_HERSHEY_COMPLEX_SMALL,
                            0.8,
                            Scalar(255.0, 50.0, 50.0),
                            1
                        )
                    }
//                    return outMat
                }

                frameStreamProcessor!!.supply(inputMat, countFrame())
                Log.d(TAG, "FrameStream usage = ${frameStreamProcessor!!.usage()}")
                return frameStreamProcessor!!.retrieve()
            }
            return inputMat
        } else {
            Log.v(TAG, "inputFrame is null, returning null to view")
            return null
        }

    }
}


