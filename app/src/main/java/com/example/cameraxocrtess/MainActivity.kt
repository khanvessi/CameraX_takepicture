package com.example.cameraxocrtess

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.cameraxocrtess.databinding.ActivityMainBinding
import com.example.cameraxocrtess.ocr.OcrManager
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var imageCapture: ImageCapture? = null
    private lateinit var outPutDirectory: File
    val manager = OcrManager()


    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        outPutDirectory = getOutPutDirectoryFile()


        manager.initAPI()

        if(allPermissionsGranted()){
            startCamera()
            Toast.makeText(this, "WE have Permissions",Toast.LENGTH_SHORT).show()
        }else{
            ActivityCompat.requestPermissions(this, Constants.REQUIRED_PERMISSIONS, Constants.REQUEST_CODE_PERMISSIONS)
        }

        binding.btnTakePicture.setOnClickListener(View.OnClickListener {
            takePicture()
        })

        binding.camera2.setOnClickListener(View.OnClickListener {

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

                //Toast.makeText(this@MainActivity, "$msg $savedUri", Toast.LENGTH_LONG ).show()


                val bitmap = getThumbnail(savedUri)

               // val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, savedUri)

                val ocrText = manager.startRecognize(bitmap)

                Log.e(Constants.TAG, "onImageSaved: $ocrText", )
                Toast.makeText(this@MainActivity, "$ocrText", Toast.LENGTH_LONG ).show()
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
                .also { mPreview -> mPreview.setSurfaceProvider(binding.viewFinder.surfaceProvider) }

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

    @Throws(FileNotFoundException::class, IOException::class)
    fun getThumbnail(uri: Uri?): Bitmap? {
        var input = this.contentResolver.openInputStream(uri!!)
        val onlyBoundsOptions = BitmapFactory.Options()
        onlyBoundsOptions.inJustDecodeBounds = true
        //onlyBoundsOptions.inDither = true //optional
        onlyBoundsOptions.inPreferredConfig = Bitmap.Config.ARGB_8888 //optional
        BitmapFactory.decodeStream(input, null, onlyBoundsOptions)
        input!!.close()
        if (onlyBoundsOptions.outWidth == -1 || onlyBoundsOptions.outHeight == -1) {
            return null
        }
        val originalSize =
            if (onlyBoundsOptions.outHeight > onlyBoundsOptions.outWidth) onlyBoundsOptions.outHeight else onlyBoundsOptions.outWidth

        // val ratio = if (originalSize > THUMBNAIL_SIZE) originalSize / THUMBNAIL_SIZE else 1.0
        val bitmapOptions = BitmapFactory.Options()
        // bitmapOptions.inSampleSize = getPowerOfTwoForSampleRatio(ratio)
        bitmapOptions.inDither = true //optional
        bitmapOptions.inPreferredConfig = Bitmap.Config.ARGB_8888 //
        input = this.contentResolver.openInputStream(uri)
        val bitmap = BitmapFactory.decodeStream(input, null, bitmapOptions)
        input!!.close()
        return bitmap
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