package com.amb.camtensorflow

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import io.reactivex.Single
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.activity_main.*
import org.tensorflow.lite.Interpreter
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.*
import kotlin.Comparator
import kotlin.collections.ArrayList


class MainActivity : AppCompatActivity() {

    private val cameraView by lazy { findViewById<CameraView>(R.id.cameraView) }

    private lateinit var fotoapparat: Fotoapparat

    private lateinit var tfLite: Interpreter
    private lateinit var labelProb: Array<ByteArray>
    private lateinit var imgData: ByteBuffer
    private val labels = Vector<String>()
    private val intValues by lazy { IntArray(INPUT_SIZE * INPUT_SIZE) }
    private val processor: FrameProcessor = { frame ->
        print(frame)
        if (frame.image.isNotEmpty()) {

            recognizeImage(frame)

        }
    }

    private fun recognizeImage(frame: Frame) {
        fotoapparat.takePicture()
            .toBitmap()
            .whenAvailable { photo ->
                try {
                    photo?.let {

                        val photoImage = Bitmap.createScaledBitmap(it.bitmap, INPUT_SIZE, INPUT_SIZE, false)
                        imageResult.setImageBitmap(photoImage)

                        classifier.recognizeImage(photoImage).subscribeBy(
                            onSuccess = {
                                //                    txtResult.text = it.toString()
                                print(it)
                            },
                            onError = {
                                print(it)
                            }
                        )
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
//            tensorFlow()
            classifier = ImageClassifier(getAssets())
            attachFrameProcessor()
        } else {
            requestPermission()
        }
    }

//    private fun tensorFlow() {
//        try {
//            val br = BufferedReader(InputStreamReader(assets.open(LABEL_PATH)))
//            while (true) {
//                val line = br.readLine() ?: break
//                labels.add(line)
//            }
//            br.close()
//        } catch (e: IOException) {
//            throw RuntimeException("Problem reading label file!", e)
//        }
//        labelProb = Array(1) { ByteArray(labels.size) }
//        imgData =
//            ByteBuffer.allocateDirect(DIM_BATCH_SIZE * DIM_IMG_SIZE_X * DIM_IMG_SIZE_Y * DIM_PIXEL_SIZE)
//        imgData.order(ByteOrder.nativeOrder())
//        try {
//            tfLite = Interpreter(loadModelFile(this.applicationContext))
//        } catch (e: Exception) {
//            throw RuntimeException(e)
//        }
//    }

    override fun onStart() {
        super.onStart()
        fotoapparat.start()
    }

    override fun onStop() {
        super.onStop()
        fotoapparat.stop()
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

//    private fun loadModelFile(context: Context): MappedByteBuffer {
//        val fileDescriptor = context.assets.openFd(MODEL_PATH)
//        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
//        val fileChannel = inputStream.channel
//        return fileChannel.map(
//            FileChannel.MapMode.READ_ONLY,
//            fileDescriptor.startOffset,
//            fileDescriptor.declaredLength
//        )
//    }

//    private fun frameInterpreter() {
//        tfLite = Interpreter(loadModelFile(this.applicationContext))
//
//        val labelProbArray = arrayListOf<Float>()
//        tfLite.run(imgData, labelProbArray)
//    }

//    private fun convertBitmapToByteBuffer(bitmap: Bitmap) {
//        imgData.rewind()
//        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
//        var pixel = 0
//        for (i in 0 until DIM_IMG_SIZE_X) {
//            for (j in 0 until DIM_IMG_SIZE_Y) {
//                val value = intValues[pixel++]
//                imgData.put((value shr 16 and 0xFF).toByte())
//                imgData.put((value shr 8 and 0xFF).toByte())
//                imgData.put((value and 0xFF).toByte())
//            }
//        }
//    }
//
//    fun recognizeImageFromBitmap(bitmap: Bitmap): Single<List<Result>> {
//        return Single.just(bitmap).flatMap {
//            convertBitmapToByteBuffer(it)
//            tfLite.run(imgData, labelProb)
//            val pq = PriorityQueue<Result>(3,
//                Comparator<Result> { lhs, rhs ->
//                    // Intentionally reversed to put high confidence at the head of the queue.
//                    java.lang.Float.compare(rhs.confidence!!, lhs.confidence!!)
//                })
//            for (i in labels.indices) {
//                pq.add(
//                    Result(
//                        "" + i,
//                        if (labels.size > i) labels[i] else "unknown",
//                        labelProb[0][i].toFloat(),
//                        null
//                    )
//                )
//            }
//            val recognitions = ArrayList<Result>()
//            val recognitionsSize = Math.min(pq.size, MAX_RESULTS)
//            for (i in 0 until recognitionsSize) recognitions.add(pq.poll())
//            return@flatMap Single.just(recognitions)
//        }
//    }
//
//    fun recognizeFromFrame(frame: Frame): Single<List<Result>> {
//        tfLite.run(frame.image, labelProb)
//        val pq = PriorityQueue<Result>(3,
//            Comparator<Result> { lhs, rhs ->
//                // Intentionally reversed to put high confidence at the head of the queue.
//                java.lang.Float.compare(rhs.confidence!!, lhs.confidence!!)
//            })
//        for (i in labels.indices) {
//            pq.add(
//                Result(
//                    "" + i,
//                    if (labels.size > i) labels[i] else "unknown",
//                    labelProb[0][i].toFloat(),
//                    null
//                )
//            )
//        }
//        val recognitions = ArrayList<Result>()
//        val recognitionsSize = Math.min(pq.size, MAX_RESULTS)
//        for (i in 0 until recognitionsSize) recognitions.add(pq.poll())
//        return Single.just(recognitions)
//    }

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
