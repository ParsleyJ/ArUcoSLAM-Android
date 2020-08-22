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
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*
import org.opencv.android.FixedCameraBridgeViewBase
import org.opencv.android.OpenCVLoader
import org.opencv.core.Mat
import parsleyj.arucoslam.datamodel.*


class MainActivity : AppCompatActivity(), FixedCameraBridgeViewBase.CvCameraViewListener2 {

    companion object {
        const val TAG = "MainActivity"
        const val CALIBRATION_REQUEST = 1
        const val DETECTED_MARKERS_MAX_OUTPUT = 50
    }

    val cameraParameters: CalibData by lazy {
        CalibData.xiaomiMiA1RearCamera
    }

    //    private val markerSpace = MarkerTaggedSpace.arucoBoard(
//        dictionary = ArucoDictionary.DICT_6X6_250,
//        markersX = 8,
//        markersY = 5,
//        markerLength = 0.0295,
//        markerSeparation = 0.006
//    )
    private val markerSpace by lazy {
        MarkerTaggedSpace.threeStackedMarkers(
            dictionary = ArucoDictionary.DICT_6X6_250,
            id1 = 4,
            id2 = 5,
            id3 = 6,
            markerLength = 0.079,
            markerSeparation = 0.0055
        ) + MarkerTaggedSpace.singleMarker(
            dictionary = ArucoDictionary.DICT_6X6_250,
            id = 0,
            markerLength = 0.083
        ).movedTo(
            Vec3d(+0.38, -0.079, -0.03)
        ) + MarkerTaggedSpace.singleMarker(
            dictionary = ArucoDictionary.DICT_6X6_250,
            id = 3,
            markerLength = 0.079
        ).movedTo(
//            Pose3d(Vec3d(0.0, Math.PI/2.0, 0.0), Vec3d(-0.63/4.0,-0.084/2.0,+0.18/2.0))
            Pose3d(Vec3d(0.0, Math.PI/2.0, 0.0), Vec3d(-0.18,-0.084,+0.63))
        ) + (MarkerTaggedSpace.arucoBoard(
            dictionary = ArucoDictionary.DICT_6X6_250,
            markersX = 8,
            markersY = 5,
            markerLength = 0.0295,
            markerSeparation = 0.006
        ).movedTo(
            Vec3d(+0.166, -0.31, 0.0)
        ) - list[0, 3, 4, 5, 6])
    }

//    private val markerSpace by lazy {
//        MarkerTaggedSpace.singleMarker(
//            dictionary = ArucoDictionary.DICT_6X6_250,
//            id = 3,
//            markerLength = 0.079
//        )+ MarkerTaggedSpace.singleMarker(
//            dictionary = ArucoDictionary.DICT_6X6_250,
//            id = 2,
//            markerLength = 0.079
//        ).movedTo(Vec3d(0.0, -0.168, 0.0))
//    }

    private val fixedMarkerIds by lazy {
        markerSpace.markers
            .map { it.markerId }
            .toIntArray()
    }
    private val fixedMarkerRvects by lazy {
        markerSpace.markers
            .map { it.pose3d.rotationVector }
            .flattenVecs()
            .toDoubleArray()
    }
    private val fixedMarkerTvects by lazy {
        markerSpace.markers
            .map { it.pose3d.translationVector }
            .flattenVecs()
            .toDoubleArray()
    }
    private val fixedMarkerLengths by lazy {
        markerSpace.markers
            .map { it.markerSideLength }
            .toDoubleArray()
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) =
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

    override fun onCameraViewStarted(width: Int, height: Int) {
//        if (cameraParameters==null) {
//            startActivityForResult(Intent(this, CalibActivity::class.java), CALIBRATION_REQUEST)
//        }
    }

    override fun onCameraViewStopped() {

    }


    private var frameCounter = 0L

    private fun countFrame(): Long {
        return frameCounter++
    }


    var frameStreamProcessor: FrameStreamProcessor? = null


    override fun onCameraFrame(inputFrame: FixedCameraBridgeViewBase.CvCameraViewFrame?): Mat? {
        if (inputFrame != null) {
            Log.v(TAG, "inputFrame != null")
            val inputMat = inputFrame.rgba()

            if (frameStreamProcessor == null) {
                frameStreamProcessor = FrameStreamProcessor(
                    inputMat.size(),
                    inputMat.type(),
                    2 // number of parallel processors on frames
                ) { inMat, outMat, foundIDs, foundRvecs, foundTvecs ->

                    val foundPoses = NativeMethods.processCameraFrame(
                        cameraParameters.cameraMatrix.nativeObjAddr,
                        cameraParameters.distCoeffs.nativeObjAddr,
                        inMat.nativeObjAddr,
                        outMat.nativeObjAddr,
                        fixedMarkerIds,
                        fixedMarkerLengths,
                        DETECTED_MARKERS_MAX_OUTPUT,
                        foundIDs,
                        foundRvecs,
                        foundTvecs
                    )
                    Log.v(TAG, "frame processed!")

                    if (foundPoses > 0) {
                        val estimatedPosition = DoubleArray(3) { 0.0 }


                        val inliersCount = NativeMethods.estimateCameraPosition(
                            cameraParameters.cameraMatrix.nativeObjAddr,
                            cameraParameters.distCoeffs.nativeObjAddr,
                            outMat.nativeObjAddr,
                            fixedMarkerIds,
                            fixedMarkerRvects,
                            fixedMarkerTvects,
                            fixedMarkerLengths,
                            foundPoses,
                            foundIDs,
                            foundRvecs,
                            foundTvecs,
                            null,
                            null,
                            estimatedPosition
                        )
                    }
                }
            }


            frameStreamProcessor!!.supply(inputMat, countFrame())
            Log.d(TAG, "FrameStream usage = ${frameStreamProcessor!!.usage()}")
            return frameStreamProcessor!!.retrieve()
        } else {
//            Log.v(TAG, "inputFrame is null, returning null to view")
            return null
        }

    }
}



