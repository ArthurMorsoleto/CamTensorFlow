package com.amb.camtensorflow

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import io.fotoapparat.Fotoapparat
import io.fotoapparat.configuration.UpdateConfiguration
import io.fotoapparat.log.logcat
import io.fotoapparat.log.loggers
import io.fotoapparat.parameter.ScaleType
import io.fotoapparat.preview.Frame
import io.fotoapparat.selector.back
import io.fotoapparat.util.FrameProcessor
import io.fotoapparat.view.CameraView
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity() {

    private val cameraView by lazy { findViewById<CameraView>(R.id.cameraView) }

    private lateinit var fotoapparat: Fotoapparat

    private val processor: FrameProcessor = { frame ->
        if (frame.image.isNotEmpty()) {
            recognizeImage(frame)
        }
    }

    private fun recognizeImage(frame: Frame) {
        fotoapparat.takePicture()
            .toBitmap()
            .whenAvailable { photo ->
                try {
                    photo?.let { bitmapPhoto ->
                        val photoImage =
                            Bitmap.createScaledBitmap(
                                bitmapPhoto.bitmap,
                                INPUT_SIZE,
                                INPUT_SIZE,
                                false
                            )
                        imageResult.setImageBitmap(photoImage)

                        classifier.recognizerTest(photoImage).subscribeBy(
                            onSuccess = {
                                print(it)
                            },
                            onError = {
                                print(it)
                                Log.e(MainActivity::class.java.simpleName, it.toString())
                            }
                        )


                        /*classifier.recognizeImage(photoImage).subscribeBy(
                            onSuccess = {
                                print(it)
                            },
                            onError = {
                                print(it)
                                Log.e(MainActivity::class.java.simpleName, it.toString())
                            }
                        )*/
                    }
                } catch (e: java.lang.Exception) {

                }
            }
    }

    private lateinit var classifier: ImageClassifier

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (checkPermission()) {
            setupCameraView()
            classifier = ImageClassifier(assets)
            attachFrameProcessor()
        } else {
            requestPermission()
        }
    }

    override fun onStart() {
        super.onStart()
        fotoapparat.start()
    }

    override fun onStop() {
        super.onStop()
        fotoapparat.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        classifier.close()
    }

    private fun setupCameraView() {
        fotoapparat = Fotoapparat(
            context = this,
            view = cameraView,
            scaleType = ScaleType.CenterCrop,
            lensPosition = back(),
            logger = loggers(logcat())
        )
    }

    private fun attachFrameProcessor() {
        Handler().postDelayed({
            fotoapparat.updateConfiguration(UpdateConfiguration(frameProcessor = processor))
        }, 2000)
    }

    private fun checkPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this@MainActivity,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermission() {
        ActivityCompat.requestPermissions(
            this, arrayOf(Manifest.permission.CAMERA),
            PERMISSION_REQUEST_CODE
        )
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 200
        private const val MODEL_PATH = "detect.tflite"
        const val INPUT_SIZE = 224

        const val LABEL_PATH = "labelmap.txt"
        const val MAX_RESULTS = 3
        const val DIM_BATCH_SIZE = 1
        const val DIM_PIXEL_SIZE = 3
        const val DIM_IMG_SIZE_X = 224
        const val DIM_IMG_SIZE_Y = 224
    }
}
