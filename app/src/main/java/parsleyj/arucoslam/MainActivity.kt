package parsleyj.arucoslam

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
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


const val ArucoSLAMCameraActivityTag = "ArucoSLAMCameraActivity"

class MainActivity : AppCompatActivity(), FixedCameraBridgeViewBase.CvCameraViewListener2 {




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
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_4_0, this, mLoaderCallback);
    }

    private val mLoaderCallback: BaseLoaderCallback = object : BaseLoaderCallback(this) {
        // Una volta che OpenCV manager è connesso viene chiamato questo metodo di
        override fun onManagerConnected(status: Int) = when (status) {
            LoaderCallbackInterface.SUCCESS -> {
                Log.i(ArucoSLAMCameraActivityTag, "OpenCV loaded successfully")
                System.loadLibrary("gnustl_shared")
                System.loadLibrary("native-lib")
                System.loadLibrary("nonfree")
                Toast.makeText(super.mAppContext, getHelloString(), Toast.LENGTH_SHORT).show()
                Log.i(ArucoSLAMCameraActivityTag, "NDK Lib loaded successfully")
                opencvCamera.enableView()
            }
            else -> super.onManagerConnected(status)
        }
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    private external fun getHelloString(): String

    private external fun processCameraFrame(
        inputMatAddr: LongBox?,
        resultMatAddr: LongBox?
    )


    private external fun calibrate(
        inputMatsAddr: LongBox?,
        outCameraMatrix: LongBox?,
        outCameraDistortions : LongBox?
    ): Double

    override fun onCameraViewStarted(width: Int, height: Int) {

    }

    override fun onCameraViewStopped() {

    }

    var cachedResultMat: Mat? = null


    override fun onCameraFrame(inputFrame: FixedCameraBridgeViewBase.CvCameraViewFrame?): Mat? {
//        return if(inputFrame != null){
//            val rgba = inputFrame.rgba()
//            val destSize = Size(rgba.size().height, rgba.size().width)
//            val result = Mat.zeros(destSize, rgba.type())
//            processCameraFrame(LongBox(rgba.nativeObjAddr), LongBox(result.nativeObjAddr))
//            result
//        }else null

//        return if (inputFrame != null) {
//            val mRgba = inputFrame.rgba()
//            val mRgbaT = Mat.zeros(mRgba.size(), mRgba.type())
//            val mRgbaF = Mat.zeros(mRgba.size(), mRgba.type())
//            Core.transpose(mRgba, mRgbaT)
//            Imgproc.resize(mRgbaT, mRgbaF, mRgba.size(), 0.0, 0.0, 0)
//            Core.flip(mRgbaF, mRgba, 1)
//            mRgba
//        } else null

        return if (inputFrame != null) {
            val inputMat = inputFrame.rgba()

            val cameraCharacteristics = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val manager = this.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                Log.i(ArucoSLAMCameraActivityTag,
                    "Camera ids: "+(manager.cameraIdList.reduce { s1, s2 -> "$s1; $s2" }))

            } else {
                null
            }



            if (cachedResultMat == null) {
                cachedResultMat = Mat.zeros(inputMat.size(), inputMat.type())
                Log.d(ArucoSLAMCameraActivityTag, "Created new result mat")
            }
            val result : Mat = cachedResultMat!!


            processCameraFrame(
                LongBox(inputMat.nativeObjAddr),
                LongBox(result.nativeObjAddr)
            )
            result
        } else null

    }
}
