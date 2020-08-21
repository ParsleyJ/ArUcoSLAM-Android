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
import org.opencv.core.CvType
import org.opencv.core.Mat
import parsleyj.arucoslam.datamodel.ArucoDictionary
import parsleyj.arucoslam.datamodel.MarkerTaggedSpace
import parsleyj.arucoslam.datamodel.Pose3d
import parsleyj.arucoslam.datamodel.Vec3d


class MainActivity : AppCompatActivity(), FixedCameraBridgeViewBase.CvCameraViewListener2 {

    companion object {
        const val TAG = "MainActivity"
        const val CALIBRATION_REQUEST = 1
        const val DETECTED_MARKERS_MAX_OUTPUT = 50
    }

    // since Android GC deletes underlying data in field Mats, i use this property delegator
    // to reload the correct data from storage/hardcoded data when the mats become corrupt.
    private var cameraMatrix by CRCCheckedMat {
//        PersistentCameraParameters.retrieveSavedCameraMatrix(applicationContext)
        getCameraMat()
    }

    private var distCoeffs by CRCCheckedMat {
//        PersistentCameraParameters.retrieveSavedDistCoefficients(applicationContext)
        getDistCoeffsMat()
    }

    private val calibSizeRatio = (480.0 / 720.0)//(864.0 / 1280.0)

    //    private val markerSpace = MarkerTaggedSpace.arucoBoard(
//        dictionary = ArucoDictionary.DICT_6X6_250,
//        markersX = 8,
//        markersY = 5,
//        markerLength = 0.0295,
//        markerSeparation = 0.006
//    )
    private val markerSpace by lazy {
        MarkerTaggedSpace.threeStackedMarkers(
            ArucoDictionary.DICT_6X6_250, 4, 5, 6, 0.079, 0.0055
        ) + MarkerTaggedSpace.singleMarker(
            ArucoDictionary.DICT_6X6_250, 0, 0.083, Pose3d(Vec3d.ORIGIN, Vec3d(+0.38, -0.079, -0.03))
        ) + (MarkerTaggedSpace.arucoBoard(
            ArucoDictionary.DICT_6X6_250, 8, 5, 0.0295, 0.006
        ).movedTo(Vec3d(+0.166, -0.31, 0.0)) - list[0,4,5,6])
    }

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

    private fun setFoundCamParams() {
        cameraMatrix = getCameraMat()
        distCoeffs = getDistCoeffsMat()
        PersistentCameraParameters.saveCameraParameters(this, cameraMatrix, distCoeffs)
    }

    private fun getCameraMat(): Mat {
        return Mat(
            3, 3, CvType.CV_64FC1, doubleArrayOf(
                1032.8829095671827, 0.0, 633.4940320469335 * calibSizeRatio,
                0.0, 1031.2002634811117, 353.94940985894704 * calibSizeRatio,
                0.0, 0.0, 1.0
            ).asDoubleBuffer().copyToNewByteBuffer()
        )
    }

    private fun getDistCoeffsMat(): Mat {
        return Mat(
            1, 5, CvType.CV_64FC1, doubleArrayOf(
                0.138768877522313,
                -0.6601745137551255,
                -0.0007624348695516956,
                -0.000016450434278321715,
                0.819925063225912
            ).asDoubleBuffer().copyToNewByteBuffer()
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
            R.id.action_run_gc -> {
                guiExec {
                    Toast.makeText(this, "" + distCoeffs?.dump(), Toast.LENGTH_SHORT).show()
                    setFoundCamParams()
                }

                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    private fun setCameraParameters(pair: Pair<Mat?, Mat?>) {
        val (cm, dcs) = pair
        cameraMatrix = cm
        distCoeffs = dcs
        Log.i(TAG, "Returned from calibration. cameraMatrix = $cm")
        Log.i(TAG, "Returned from calibration. distCoeffs   = $dcs")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) =
        when (requestCode) {
            CALIBRATION_REQUEST -> {
                setCameraParameters(
                    PersistentCameraParameters.loadCameraParameters(
                        applicationContext
                    )
                )
            }
            else -> {
            }
        }

    override fun onCameraViewStarted(width: Int, height: Int) {
        if (cameraMatrix == null || distCoeffs == null) {
            startActivityForResult(Intent(this, CalibActivity::class.java), CALIBRATION_REQUEST)
        }
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

                    if (cameraMatrix != null && distCoeffs != null) {

                        val foundPoses = NativeMethods.processCameraFrame(
                            cameraMatrix!!.nativeObjAddr,
                            distCoeffs!!.nativeObjAddr,
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

                        if (foundPoses > 1) {
                            val estimatedPosition = DoubleArray(3) { 0.0 }


                            val inliersCount = NativeMethods.estimateCameraPosition(
                                cameraMatrix!!.nativeObjAddr,
                                distCoeffs!!.nativeObjAddr,
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



