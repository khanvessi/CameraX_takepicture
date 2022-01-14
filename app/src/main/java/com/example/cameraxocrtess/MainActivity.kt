package com.example.cameraxocrtess

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.PackageManagerCompat
import com.example.cameraxocrtess.databinding.ActivityMainBinding
import java.io.File
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var imageCapture: ImageCapture? = null
    private lateinit var outPutDirectory: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        outPutDirectory = getOutPutDirectoryFile()

        if(allPermissionsGranted()){
            startCamera()
            Toast.makeText(this, "WE have Permissions",Toast.LENGTH_SHORT).show()
        }else{
            ActivityCompat.requestPermissions(this, Constants.REQUIRED_PERMISSIONS, Constants.REQUEST_CODE_PERMISSIONS)
        }

        binding.btnTakePicture.setOnClickListener(View.OnClickListener {
            takePicture()
        })
    }

    private fun takePicture(){
        val imageCapture = imageCapture?: return
        val photoFile = File(
            outPutDirectory,
            SimpleDateFormat(Constants.FILE_NAME_FORMAT,
            Locale.getDefault())
                .format(System.currentTimeMillis()) + ".jpg"
        )

        val outputOption = ImageCapture
            .OutputFileOptions
            .Builder(photoFile)
            .build()


        imageCapture.takePicture(outputOption, ContextCompat.getMainExecutor(this),
        object :ImageCapture.OnImageSavedCallback{
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {

                val savedUri = Uri.fromFile(photoFile)
                val msg = "Photo Saved"

                Toast.makeText(this@MainActivity, "$msg $savedUri", Toast.LENGTH_LONG ).show()
            }

            override fun onError(exception: ImageCaptureException) {

                Log.e(Constants.TAG, "onError: ${exception.message}",exception )
            }

        })
    }

    private fun getOutPutDirectoryFile(): File{

        val mediaDir = externalMediaDirs.firstOrNull()?.let { mFile -> File(mFile, resources.getString(R.string.app_name)).apply {
            mkdirs()
        } }

        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }

    private fun startCamera(){
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener( {

            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder()
                .build()
                .also { mPreivew -> mPreivew.setSurfaceProvider(binding.viewFinder.surfaceProvider) }

            imageCapture = ImageCapture.Builder()
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {

                cameraProvider.unbindAll()

                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
                
            }catch (e: Exception){
                Log.e(Constants.TAG, "startCamera: Failed", e )
            }
        }, ContextCompat.getMainExecutor(this) )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if(requestCode == Constants.REQUEST_CODE_PERMISSIONS){

            if(allPermissionsGranted()){
                startCamera()
            }else{
                Toast.makeText(this, "Permissions not granted by the user",Toast.LENGTH_SHORT).show()

                finish()
            }

        }


    }
    private fun allPermissionsGranted() =
        Constants.REQUIRED_PERMISSIONS.all{
            ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
        }

}