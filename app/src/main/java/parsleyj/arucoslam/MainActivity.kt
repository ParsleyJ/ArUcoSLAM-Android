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
import org.opencv.core.Core
import org.opencv.core.Mat
import parsleyj.arucoslam.datamodel.*
import parsleyj.arucoslam.datamodel.fixedSpace.MarkerTaggedSpace
import parsleyj.arucoslam.framepipeline.*
import parsleyj.arucoslam.pipeline.Pipeline
import parsleyj.arucoslam.pipeline.pipeline
import parsleyj.kotutils.with


class MainActivity : AppCompatActivity(), FixedCameraBridgeViewBase.CvCameraViewListener2 {


    companion object {
        const val TAG = "MainActivity"
        const val CALIBRATION_REQUEST = 1
        const val DETECTED_MARKERS_MAX_OUTPUT = 50
    }

    private val cameraParameters: CalibData by lazy {
        CalibData.xiaomiMiA1RearCamera
    }

    private lateinit var frameStreamProcessor: FrameStreamProcessor<FrameRecyclableData>


    private val markerSpace by lazy {
        MarkerTaggedSpace.singleMarker(
            dictionary = ArucoDictionary.DICT_6X6_250,
            id = 3,
            markerLength = 0.079
        ).toSLAMSpace()
    }

    //TODO do it only when marker space changes.
    private val fixedMarkerIds
        get() = markerSpace.markers
            .map { it.markerId }
            .toIntArray()

    private val fixedMarkerRvects
        get() = markerSpace.markers
            .map { it.pose3d.rotationVector }
            .flattenVecs()
            .toDoubleArray()

    private val fixedMarkerTvects
        get() = markerSpace.markers
            .map { it.pose3d.translationVector }
            .flattenVecs()
            .toDoubleArray()

    private val fixedMarkerConfidences
        get() = markerSpace.markers
            .map { it.markerConfidence }
            .toDoubleArray()

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


    lateinit var pipeline: Pipeline<Mat, Mat>


    override fun onCameraFrame(inputFrame: FixedCameraBridgeViewBase.CvCameraViewFrame?): Mat? {
        if (inputFrame != null) {
            Log.v(TAG, "inputFrame != null")
            val inputMat = inputFrame.rgba()

            if (!this::pipeline.isInitialized) {

                val poseEstimator = PoseEstimatorComponent(
                    inputMat.size(),
                    inputMat.type(),
                    cameraParameters
                )

                val stateUpdater = SLAMStateUpdaterComponent()


                val slamState = SLAMStateMaintainerComponent()

                val mapRenderer = MapRendererComponent(inputMat.size(), inputMat.type())

                val merger = pipeline<Pair<Mat, Mat>, Mat>(
                    supplyEmptyOutput = { Mat.zeros(inputMat.size(), inputMat.type()) },
                    maxProcessors = 4,
                    block = { (leftImg, rightImg), resultImg ->
                        Core.addWeighted(leftImg, 1.0, rightImg, 0.7, 0.0, resultImg)
                    }
                )

                pipeline = object:Pipeline<Mat, Mat>{
                    override fun supply(input: Mat?) {
                        poseEstimator.supply(input)
                    }

                    override fun retrieve(): Mat {
                        val (enrichedMat, foundPoses) = poseEstimator.retrieve()
                        val (prevTrack, prevAllPoses) = slamState.retrieve()
                        stateUpdater.supply(Triple(foundPoses, prevTrack, prevAllPoses))
                        val (updatedTrack, updatedAllPoses) = stateUpdater.retrieve()
                        slamState.supply(updatedTrack with foundPoses)
                        mapRenderer.supply(Triple(updatedTrack, foundPoses, updatedAllPoses))
                        merger.supply(enrichedMat with mapRenderer.retrieve())
                        return merger.retrieve()
                    }

                }
            }

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

                    val foundPosesCount = NativeMethods.detectMarkers(
                        cameraParameters.cameraMatrix.nativeObjAddr,
                        cameraParameters.distCoeffs.nativeObjAddr,
                        inMat.nativeObjAddr,
                        outMat.nativeObjAddr,
                        0.079,
                        DETECTED_MARKERS_MAX_OUTPUT,
                        foundIDs,
                        foundRvecs,
                        foundTvecs
                    )


                    if (foundPosesCount > 0) {
                        val estimatedPositionRVec = DoubleArray(3) { 0.0 }
                        val estimatedPositionTVec = DoubleArray(3) { 0.0 }

                        val inliersCount = NativeMethods.estimateCameraPosition(
                            cameraParameters.cameraMatrix.nativeObjAddr, //in
                            cameraParameters.distCoeffs.nativeObjAddr, //in
                            outMat.nativeObjAddr, //in&out
                            fixedMarkerIds, //in
                            fixedMarkerRvects, //in
                            fixedMarkerTvects, //in
                            fixedMarkerConfidences, //in
                            markerSpace.commonLength, //in
                            foundPosesCount, //in
                            foundIDs, //in
                            foundRvecs, //in
                            foundTvecs, //in
                            estimatedPositionRVec, //out
                            estimatedPositionTVec //out
                        )
                    }
                }
            }


            frameStreamProcessor.supply(inputMat, countFrame())
            Log.d(TAG, "FrameStream usage = ${frameStreamProcessor.usage()}")
            return frameStreamProcessor.retrieve()
        } else {
            return null
        }

    }
}





