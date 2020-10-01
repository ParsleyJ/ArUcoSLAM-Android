package parsleyj.arucoslam

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.SurfaceView
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_main.*
import org.opencv.android.FixedCameraBridgeViewBase
import org.opencv.android.OpenCVLoader
import org.opencv.core.Mat
import parsleyj.arucoslam.datamodel.*
import parsleyj.arucoslam.datamodel.fixedSpace.MarkerTaggedSpace
import parsleyj.arucoslam.framepipeline.PoseValidityConstraints
import parsleyj.arucoslam.framepipeline.SLAMFramePipeline
import parsleyj.kotutils.joinWithSeparator
import kotlin.math.PI


class MainActivity : AppCompatActivity(), FixedCameraBridgeViewBase.CvCameraViewListener2 {


    companion object {
        const val TAG = "MainActivity"
        const val CALIBRATION_REQUEST = 1
        const val DETECTED_MARKERS_MAX_OUTPUT = 50
    }

    private val cameraParameters: CalibData by lazy {
        CalibData.xiaomiMiA1RearCamera
    }

    private lateinit var slamFramePipeline: SLAMFramePipeline


    private val markerSpace by lazy {
        MarkerTaggedSpace.singleMarker(
            dictionary = ArucoDictionary.DICT_6X6_250,
            id = 3,
            markerLength = 0.079
        ).toSLAMSpace()
    }

    private val track by lazy {
        Track(
            1000L, // collecting with high granularity for 1 second
            40 // we dont'expect to collect more than 40 poses in a second
        )
    }

    private val poseValidityConstraints by lazy {
        PoseValidityConstraints(
            0.5, // a pose estimate obtained with RANSAC should have at least 50% of inliers
            0.8, // not expected to exceed 0.8 meters per second when detecting a new pose
            2.0*PI/3.0 // not expected to exceed 120 degrees per second of rotation
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
        opencvCamera.visibility = SurfaceView.VISIBLE
        opencvCamera.setCvCameraViewListener(this)
        opencvCamera.setMaxFrameSize(2000, 2000)
    }


    override fun onResume() {
        super.onResume()
        OpenCVLoader.initDebug()
        Log.i(TAG, "OpenCV loaded successfully")
        System.loadLibrary("gnustl_shared")
        System.loadLibrary("native-lib")
        System.loadLibrary("nonfree")
        Log.i(TAG, "NDK Libs loaded successfully")
        opencvCamera.enableView()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        else -> super.onOptionsItemSelected(item)
    }

    private fun setCameraParameters(calibData: CalibData?) {
        val cm = calibData?.cameraMatrix
        val dcs = calibData?.distCoeffs
//        this.cameraParameters = calibData
        Log.i(TAG, "Returned from calibration. cameraMatrix = $cm")
        Log.i(TAG, "Returned from calibration. distCoeffs   = $dcs")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            CALIBRATION_REQUEST -> {
                setCameraParameters(
                    CalibData.loadCameraParameters(
                        applicationContext
                    )
                )
            }
            else -> {

            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onCameraViewStarted(width: Int, height: Int) {
//        if (cameraParameters==null) {
//            startActivityForResult(Intent(this, CalibActivity::class.java), CALIBRATION_REQUEST)
//        }
    }

    override fun onCameraViewStopped() {

    }


    

    override fun onCameraFrame(inputFrame: FixedCameraBridgeViewBase.CvCameraViewFrame?): Mat? {
        if (inputFrame != null) {
            Log.v(TAG, "inputFrame != null")
            val inputMat = inputFrame.rgba()

            if (!this::slamFramePipeline.isInitialized) {
                slamFramePipeline = SLAMFramePipeline(
                    DETECTED_MARKERS_MAX_OUTPUT,
                    { cameraParameters },
                    markerSpace,
                    track,
                    poseValidityConstraints,
                    inputMat.size(),
                    inputMat.type(),
                    3, // number of parallel workers on frames
                )
            }


            slamFramePipeline.supply(inputMat)
            val usage = slamFramePipeline.usage()
            Log.d(TAG, "FrameStream usage = $usage")
            if (usage > 51.0) {
                for (i in 0 until 10) {
                    Log.d(TAG, "USAGE > 51%!" + (0 until i).map { "!" }.joinWithSeparator(""))
                }
            }
            return slamFramePipeline.retrieve()


        } else {
            return null
        }

    }
}





