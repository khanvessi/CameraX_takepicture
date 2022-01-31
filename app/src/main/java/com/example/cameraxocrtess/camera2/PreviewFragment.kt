package com.example.cameraxocrtess.camera2

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.params.MeteringRectangle
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Range
import android.view.*
import androidx.fragment.app.Fragment
import com.example.cameraxocrtess.R
import com.example.cameraxocrtess.databinding.FragmentPreviewBinding
import kotlinx.android.synthetic.main.fragment_preview.*
import pub.devrel.easypermissions.AfterPermissionGranted
import pub.devrel.easypermissions.EasyPermissions
import java.util.*


class PreviewFragment : Fragment() {

    //HARDCODING THE VALUE FOR ASPECT RATIO
    private val MAX_PREVIEW_WIDTH = 1280
    private val MAX_PREVIEW_HEIGHT = 720
    private lateinit var captureSession: CameraCaptureSession
    private lateinit var captureRequestBuilder: CaptureRequest.Builder

    //BACKGROUND THREAD
    private lateinit var backgroundThread: HandlerThread
    private lateinit var backgroundHandler: Handler
    private var isFlashSupported = false
    private var isTorchOn = false
    private lateinit var binding: FragmentPreviewBinding
    val CAMERA_FRONT = "1"
    val CAMERA_BACK = "0"
    var cameraCharacteristics: CameraCharacteristics? = null

    private val cameraId = CAMERA_BACK
    private var mManualFocusEngaged: Boolean = false


    //VAR FOR CAMERA DEVICE
    private lateinit var cameraDevice: CameraDevice
    private val deviceStateCallback = object: CameraDevice.StateCallback(){
        override fun onOpened(camera: CameraDevice) {
            Log.e(TAG, "onOpened: camera device opened")
            cameraDevice = camera
            previewSession()

        }

        override fun onDisconnected(camera: CameraDevice) {
            Log.e(TAG, "onDisconnected: camera device disconnected")
            camera.close()
        }

        override fun onError(camera: CameraDevice, error: Int) {
            Log.e(TAG, "onError: camera device error")
            this@PreviewFragment.activity?.finish()
        }

    }

    //ACCESSING THE HARDWARE CAMERA OF THE DEVICE BY CAMERAMANAGER
    private val cameraManager by lazy {
        activity?.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }


    private fun getRange(): Range<Int>? {
        var chars: CameraCharacteristics? = null
        return try {
            chars = cameraManager.getCameraCharacteristics(CAMERA_BACK)
            val ranges: Array<Range<Int>> =
                chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)!!
            var result: Range<Int>? = null
            for (range in ranges) {
                val upper: Int = range.getUpper()
                // 10 - min range upper for my needs
                if (upper >= 10) {
                    if (result == null || upper < result.upper) {
                        result = range
                    }
                }
            }
            if (result == null) {
                result = ranges[0]
            }
            result
        } catch (e: CameraAccessException) {
            e.printStackTrace()
            null
        }
    }


    @SuppressLint("Recycle")
    private fun previewSession(){
        val surfaceTexture = preview_texture_view.surfaceTexture
        surfaceTexture?.setDefaultBufferSize(MAX_PREVIEW_WIDTH, MAX_PREVIEW_HEIGHT)
        val surface = Surface(surfaceTexture)

        captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        captureRequestBuilder.addTarget(surface)


        cameraDevice.createCaptureSession(Arrays.asList(surface),
            object: CameraCaptureSession.StateCallback(){
                override fun onConfigured(session: CameraCaptureSession) {
                   if(session != null){
                       captureSession = session
                       captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                       captureSession.setRepeatingRequest(captureRequestBuilder.build(), null,null)
                       captureRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, getRange())
                   }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e(TAG, "onConfigureFailed: ceating caputre session failed")
                }

            }, null)
    }



    private fun closeCamera(){
        //THIS FOR LATEINIT
        if(this::captureSession.isInitialized){
            if(this::cameraDevice.isInitialized){
                cameraDevice.close()
            }
        }
    }

    private fun startBackgroundThread(){
        backgroundThread = HandlerThread("Camera2 Kotlin").also { it.start() }
        backgroundHandler = Handler(backgroundThread.looper)
    }

    private fun stopBackgroundThread(){
        backgroundThread.quitSafely()
        try{
            backgroundThread.join()
        }catch (e: InterruptedException){
            Log.e(TAG, "stopBackgroundThread: ${e.message}")
        }
    }

    //HELPER FUNCTION TO SUPPORT THE CHARACTERISTICS


    //RETURNS A SPECIFIC CAMERA ID FOR FRONT FACING OR REAR FACING LENS
    private fun cameraId(lens: Int): String {
        var deviceId = listOf<String>()
        try {
            val cameraIdList = cameraManager.cameraIdList
            //MATCH THE LENS (FRONT OR REAR)
            deviceId = cameraIdList.filter { lens == cameraCharacteristics(it, CameraCharacteristics.LENS_FACING) }
        }catch (e: CameraAccessException){
            Log.e(TAG, "cameraId: ${e.message}")
        }

        //CAMERA OR DEVICE ID is the same
        return deviceId[0]
    }

    //CONNECT TO THAT PARTICULAR CAMERA DEVICE
    @SuppressLint("MissingPermission")
    private fun connectCamera(){
        //RETURNS THE DEVICE FOR THE REAR LENS, IMPORTANT FOR THE REQUEST CONNECTION
        val deviceId = cameraId(CameraCharacteristics.LENS_FACING_BACK)
        Log.e(TAG, "connectCamera: $deviceId")
        try {
            cameraManager.openCamera(deviceId, deviceStateCallback, backgroundHandler)
        }catch (e: CameraAccessException){
            Log.e(TAG, "connectCamera: ${e.reason}")
        }catch (e: InterruptedException){
            Log.e(TAG, "connectCamera: Interrupted  ${e.message}")

        }
    }
    private fun <T> cameraCharacteristics(
        cameraId: String,
        charKey: CameraCharacteristics.Key<T>
    ): T? {
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)

        return when (charKey) {
            //CAMERA ID FOR A SPECIFIC LENS
            CameraCharacteristics.LENS_FACING -> characteristics.get(charKey)
            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP -> characteristics.get(charKey)
            else -> throw IllegalArgumentException("Key not recognized")
        }

    }


    companion object {
        const val REQUEST_CAMERA_PERMISSION = 100
        private val TAG = PreviewFragment::class.qualifiedName
        @JvmStatic
        fun newInstance() = PreviewFragment()
    }

    //Before we can use textureview we need to wait for it to get initialized and for that we need to create a listener
    private val surfaceListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            Log.d(TAG, "onSurfaceTextureSizeChanged: textureSurface width $width height: $height")
            openCamera()
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {


        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    }

    private fun openCamera() {
        checkCameraPermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }


    //CHARACTERISTICS
    fun switchFlash() {
        try {
            if (cameraId.equals(CAMERA_BACK)) {
                if (isFlashSupported) {
                    if (isTorchOn) {
                        captureRequestBuilder.set(
                            CaptureRequest.FLASH_MODE,
                            CaptureRequest.FLASH_MODE_OFF
                        )
                        captureSession.setRepeatingRequest(captureRequestBuilder.build(), null, null)
                        binding.imageView.setImageResource(R.drawable.ic_torch_off)
                        isTorchOn = false
                    } else {
                        captureRequestBuilder.set(
                            CaptureRequest.FLASH_MODE,
                            CaptureRequest.FLASH_MODE_TORCH
                        )
                        captureSession.setRepeatingRequest(captureRequestBuilder.build(), null, null)
                        binding.imageView.setImageResource(R.drawable.ic_torch_on)
                        isTorchOn = true
                    }
                }
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }




    @Throws(CameraAccessException::class)
    private fun setFocusArea(focus_point_x: Int, focus_point_y: Int) {
        if (cameraId == null || mManualFocusEngaged) return
//        if (cameraManager == null) {
//            cameraManager = context?.getSystemService(Context.CAMERA_SERVICE) as CameraManager
//        }
        var focusArea: MeteringRectangle? = null
        if (cameraManager != null) {
            if (cameraCharacteristics == null) {
                cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId)
            }
            val sensorArraySize: Rect? =
                cameraCharacteristics!!.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
            var y = focus_point_x
            var x = focus_point_y
            if (sensorArraySize != null) {
                y = ((focus_point_x.toFloat() / MAX_PREVIEW_WIDTH * sensorArraySize.height().toFloat()).toInt())
                x = ((focus_point_y.toFloat() / MAX_PREVIEW_HEIGHT * sensorArraySize.width().toFloat()).toInt())
            }
            val halfTouchLength = 150
            focusArea = MeteringRectangle(
                Math.max(x - halfTouchLength, 0),
                Math.max(y - halfTouchLength, 0),
                halfTouchLength * 2,
                halfTouchLength * 2,
                MeteringRectangle.METERING_WEIGHT_MAX - 1
            )
        }
        val mCaptureCallback: CameraCaptureSession.CaptureCallback =
            object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    super.onCaptureCompleted(session, request, result)
                    mManualFocusEngaged = false
                    if (request.tag == "FOCUS_TAG") { // previously getTag == "Focus_tag"
                        //the focus trigger is complete -
                        //resume repeating (preview surface will get frames), clear AF trigger
                        captureRequestBuilder.set(
                            CaptureRequest.CONTROL_AF_TRIGGER,
                            CameraMetadata.CONTROL_AF_TRIGGER_IDLE
                        )
                        captureRequestBuilder.set(
                            CaptureRequest.CONTROL_AF_TRIGGER,
                            CameraMetadata.CONTROL_AF_TRIGGER_CANCEL
                        )
                        captureRequestBuilder.set(
                            CaptureRequest.CONTROL_AF_TRIGGER,
                            null
                        ) // As documentation says AF_trigger can be null in some device
                        try {
                            captureSession.setRepeatingRequest(
                                captureRequestBuilder.build(),
                                null,
                                backgroundHandler
                            )
                        } catch (e: CameraAccessException) {
                            // error handling
                        }
                    }
                }

                override fun onCaptureFailed(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    failure: CaptureFailure
                ) {
                    super.onCaptureFailed(session, request, failure)
                    mManualFocusEngaged = false
                }
            }
        captureSession.stopRepeating() // Destroy current session
        captureRequestBuilder.set(
            CaptureRequest.CONTROL_AF_TRIGGER,
            CameraMetadata.CONTROL_AF_TRIGGER_IDLE
        )
        captureRequestBuilder.set(
            CaptureRequest.CONTROL_AF_TRIGGER,
            CameraMetadata.CONTROL_AF_TRIGGER_START
        )
        captureSession.capture(
            captureRequestBuilder.build(),
            mCaptureCallback,
            backgroundHandler
        ) //Set all settings for once
        if (isMeteringAreaAESupported()) {
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(focusArea))
        }
        if (isMeteringAreaAFSupported()) {
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(focusArea))
            captureRequestBuilder.set(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_AUTO
            )
        }
        captureRequestBuilder.setTag("FOCUS_TAG") //it will be checked inside mCaptureCallback
        captureSession.capture(
            captureRequestBuilder.build(),
            mCaptureCallback,
            backgroundHandler
        )
        mManualFocusEngaged = true
    }


    private fun isMeteringAreaAFSupported(): Boolean { // AF stands for AutoFocus
        val afRegion: Int? = cameraCharacteristics?.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF)
        return afRegion != null && afRegion >= 1
    }


    private fun isMeteringAreaAESupported(): Boolean { //AE stands for AutoExposure
        val aeState: Int? = cameraCharacteristics?.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE)
        return aeState != null && aeState >= 1
    }




    fun setupFlashButton() {
        if (cameraId.equals(CAMERA_BACK) && isFlashSupported) {
            binding.imageView.visibility = View.VISIBLE
            if (isTorchOn) {
                binding.imageView.setImageResource(R.drawable.ic_torch_off)
            } else {
                binding.imageView.setImageResource(R.drawable.ic_torch_on)
            }
        } else {
            binding.imageView.visibility = View.GONE
        }


    }


    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.imageView.setOnClickListener(View.OnClickListener {

            cameraCharacteristics =
                cameraManager.getCameraCharacteristics(0.toString())
            val available: Boolean = cameraCharacteristics!!.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            isFlashSupported = available ?: false

            setupFlashButton()
            switchFlash()
        })


        binding.previewTextureView.setOnTouchListener(View.OnTouchListener { v, event ->
            setFocusArea(event.x.toInt(),event.y.toInt())
            return@OnTouchListener false
        })
    }

    @AfterPermissionGranted(REQUEST_CAMERA_PERMISSION)
    private fun checkCameraPermission() {
        if (EasyPermissions.hasPermissions(requireActivity(), Manifest.permission.CAMERA)) {

            Log.e(TAG, "checkCameraPermission: Has Permssions")
            connectCamera()

        } else {
            EasyPermissions.requestPermissions(
                requireActivity(),
                getString(R.string.camera_request_rationale),
                REQUEST_CAMERA_PERMISSION,
                Manifest.permission.CAMERA
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        //return inflater.inflate(R.layout.fragment_preview, container, false)
        binding = FragmentPreviewBinding.inflate(inflater,container,false);
        return binding.root;
    }

    override fun onResume() {
        super.onResume()

        startBackgroundThread()
        if (preview_texture_view.isAvailable)
            openCamera()
        else
        //this will do the callback when the textureview is available
            preview_texture_view.surfaceTextureListener = surfaceListener
    }

    override fun onPause() {
        closeCamera()
        stopBackgroundThread()
        super.onPause()
    }



}