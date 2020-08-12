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
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc.putText


class MainActivity : AppCompatActivity(), FixedCameraBridgeViewBase.CvCameraViewListener2 {

    companion object {
        const val TAG = "MainActivity"
        const val CALIBRATION_REQUEST = 1

    }

    // since Android GC deletes underlying data in field Mats, i use this property delegator
    // to reload the correct data from storage when the mats become corrupt.
    private var cameraMatrix by CRCCheckedMat {
        PersistentCameraParameters.retrieveSavedCameraMatrix(applicationContext)
    }
    private var distCoeffs by CRCCheckedMat {
        PersistentCameraParameters.retrieveSavedDistCoefficients(applicationContext)
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
        setCameraParameters(PersistentCameraParameters.loadCameraParameters(applicationContext))
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
    private var outRvec = doubleArrayOf(0.0, 0.0, 0.0)


    private var outTvec = doubleArrayOf(0.0, 0.0, 0.0)
    private var frameCounter = 0L

    private fun countFrame(): Long {
        return frameCounter++
    }


    var frameStream: FrameStream? = null

    override fun onCameraFrame(inputFrame: FixedCameraBridgeViewBase.CvCameraViewFrame?): Mat? {
        if (inputFrame != null) {
            Log.v(TAG, "inputFrame != null")
            val inputMat = inputFrame.rgba()

            val camMatrix = cameraMatrix
            val camDistCoeffs = distCoeffs
            if (camMatrix == null || camDistCoeffs == null) {
                Log.v(TAG, "camera matrix and dist coeffs are null")
            } else {
                if (frameStream == null) {
                    Log.v(TAG, "we have camera matrix and dist coeffs")
                    Log.v(TAG, "started to process camera frame...")
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
                        Log.v(TAG, "frame processed!")

                        putText(
                            outMat,
                            "RVECT(${outRvec[0].format(5)}, ${outRvec[1].format(5)}, ${outRvec[2].format(
                                5
                            )})",
                            Point(30.0, 30.0),
                            FONT_HERSHEY_COMPLEX_SMALL,
                            0.8,
                            Scalar(255.0, 50.0, 50.0),
                            1
                        )

                        putText(
                            outMat,
                            "TVECT(${outTvec[0].format(5)}, ${outTvec[1].format(5)} ,${outTvec[2].format(
                                5
                            )})",
                            Point(30.0, 50.0),
                            FONT_HERSHEY_COMPLEX_SMALL,
                            0.8,
                            Scalar(255.0, 50.0, 50.0),
                            1
                        )
                    }
                }

                frameStream!!.supply(inputMat, countFrame())
                Log.d(TAG, "FrameStream usage = ${frameStream!!.usage()}")
                return frameStream!!.retrieve()
            }
            return inputMat
        } else {
            Log.v(TAG, "inputFrame is null, returning null to view")
            return null
        }

    }
}


