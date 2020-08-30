package parsleyj.arucoslam

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
//import android.support.v4.app.ActivityCompat
//import android.support.v4.content.ContextCompat
//import android.support.v7.app.AppCompatActivity
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


class MainActivity : AppCompatActivity(), FixedCameraBridgeViewBase.CvCameraViewListener2 {

    companion object {
        const val TAG = "MainActivity"
        const val CALIBRATION_REQUEST = 1
        const val DETECTED_MARKERS_MAX_OUTPUT = 50
    }

    val cameraParameters: CalibData by lazy {
        CalibData.xiaomiMiA1RearCamera
    }

    private lateinit var frameStreamProcessor: FrameStreamProcessor<FrameRecyclableData>


    private var markerSpace = MarkerTaggedSpace.singleMarker(
        dictionary = ArucoDictionary.DICT_6X6_250,
        id = 3,
        markerLength = 0.079
    )


    private val fixedMarkerIds: IntArray
        get() = markerSpace.markers
            .map { it.markerId }
            .toIntArray()


    private val fixedMarkerRvects: DoubleArray
        get() = markerSpace.markers
            .map { it.pose3d.rotationVector }
            .flattenVecs()
            .toDoubleArray()


    private val fixedMarkerTvects: DoubleArray
        get() = markerSpace.markers
            .map { it.pose3d.translationVector }
            .flattenVecs()
            .toDoubleArray()


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


    private var frameCounter = 0L

    private fun countFrame(): Long {
        return frameCounter++
    }

    data class FrameRecyclableData(
        val foundIDs: IntArray,
        val foundRVecs: DoubleArray,
        val foundTVecs: DoubleArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as FrameRecyclableData

            if (!foundIDs.contentEquals(other.foundIDs)) return false
            if (!foundRVecs.contentEquals(other.foundRVecs)) return false
            if (!foundTVecs.contentEquals(other.foundTVecs)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = foundIDs.contentHashCode()
            result = 31 * result + foundRVecs.contentHashCode()
            result = 31 * result + foundTVecs.contentHashCode()
            return result
        }
    }




    override fun onCameraFrame(inputFrame: FixedCameraBridgeViewBase.CvCameraViewFrame?): Mat? {
        if (inputFrame != null) {
            Log.v(TAG, "inputFrame != null")
            val inputMat = inputFrame.rgba()

            if (!this::frameStreamProcessor.isInitialized) {
                frameStreamProcessor = FrameStreamProcessor(
                    inputMat.size(),
                    inputMat.type(),
                    3, // number of parallel processors on frames
                    instantiateOtherData = { // lambda that tells the FrameStreamProcessor how
                                             //  to create a recyclable support data structure
                        FrameRecyclableData(
                            foundIDs = IntArray(DETECTED_MARKERS_MAX_OUTPUT) { 0 },
                            foundRVecs = DoubleArray(DETECTED_MARKERS_MAX_OUTPUT * 3) { 0.0 },
                            foundTVecs = DoubleArray(DETECTED_MARKERS_MAX_OUTPUT * 3) { 0.0 }
                        )
                    }
                ) { inMat, outMat, (foundIDs, foundRvecs, foundTvecs) ->

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


            frameStreamProcessor.supply(inputMat, countFrame())
            Log.d(TAG, "FrameStream usage = ${frameStreamProcessor.usage()}")
            return frameStreamProcessor.retrieve()
        } else {
//            Log.v(TAG, "inputFrame is null, returning null to view")
            return null
        }

    }
}



