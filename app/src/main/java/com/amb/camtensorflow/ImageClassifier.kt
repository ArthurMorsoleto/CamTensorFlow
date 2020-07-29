package com.amb.camtensorflow

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.RectF
import io.reactivex.Single
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
import kotlin.collections.HashMap


class ImageClassifier constructor(assetManager: AssetManager) {

    private var interpreter: Interpreter? = null
    private var labelProb: Array<ByteArray>
    private val labels = Vector<String>()
    private val intValues by lazy { IntArray(INPUT_SIZE * INPUT_SIZE) }
    private var imgData: ByteBuffer

    init {
        try {
            val br = BufferedReader(InputStreamReader(assetManager.open(LABEL_PATH)))
            while (true) {
                val line = br.readLine() ?: break
                labels.add(line)
            }
            br.close()
        } catch (e: IOException) {
            throw RuntimeException("Problem reading label file!", e)
        }
        labelProb = Array(1) { ByteArray(labels.size) }
        imgData =
            ByteBuffer.allocateDirect(DIM_BATCH_SIZE * DIM_IMG_SIZE_X * DIM_IMG_SIZE_Y * DIM_PIXEL_SIZE)
        imgData.order(ByteOrder.nativeOrder())
        try {
            interpreter = Interpreter(loadModelFile(assetManager, MODEL_PATH))
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap) {
        imgData.rewind()
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var pixel = 0
        for (i in 0 until DIM_IMG_SIZE_X) {
            for (j in 0 until DIM_IMG_SIZE_Y) {
                val value = intValues[pixel++]
                imgData.put((value shr 16 and 0xFF).toByte())
                imgData.put((value shr 8 and 0xFF).toByte())
                imgData.put((value and 0xFF).toByte())
            }
        }
    }

    private fun loadModelFile(assets: AssetManager, modelFilename: String): MappedByteBuffer {
        val fileDescriptor = assets.openFd(modelFilename)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun recognizeImage(bitmap: Bitmap): Single<List<Result>> {
        return Single.just(bitmap).flatMap {
            convertBitmapToByteBuffer(it)
            interpreter!!.run(imgData, labelProb)

            val pq = PriorityQueue<Result>(3,
                Comparator<Result> { lhs, rhs ->
                    java.lang.Float.compare(rhs.confidence!!, lhs.confidence!!)
                }
            )
            for (i in labels.indices) {
                pq.add(
                    Result(
                        "" + i,
                        if (labels.size > i) labels[i] else "unknown",
                        labelProb[0][i].toFloat(),
                        null
                    )
                )
            }
            val recognitions = ArrayList<Result>()
            val recognitionsSize = Math.min(pq.size, MAX_RESULTS)
            for (i in 0 until recognitionsSize) recognitions.add(pq.poll())
            return@flatMap Single.just(recognitions)
        }
    }

    fun close() {
        interpreter?.close()
    }

    fun recognizerTest(bitmap: Bitmap): Single<ArrayList<Result>> {
        return Single.just(bitmap).flatMap {

            convertBitmapToByteBuffer(it)

            val outputLocations = Array(1) { Array(10) { FloatArray(4) } }
            val outputClasses = Array(1) { FloatArray(10) }
            val outputScores = Array(1) { FloatArray(10) }
            val numDetections = FloatArray(1)

            val outputMap: HashMap<Int, Any> = HashMap() //dando erro
            outputMap[0] = outputLocations
            outputMap[1] = outputClasses
            outputMap[2] = outputScores
            outputMap[3] = numDetections

            interpreter!!.runForMultipleInputsOutputs(arrayOf(imgData), outputMap) //dando erro no output

            val numDetectionsOutput = Math.min(10, numDetections[0].toInt()) // cast from float to integer, use min for safety
            val recognitions: ArrayList<Result> = ArrayList(numDetectionsOutput)

            for (i in 0 until numDetectionsOutput) {
                val detection = RectF(
                    outputLocations[0][i][1] * INPUT_SIZE,
                    outputLocations[0][i][0] * INPUT_SIZE,
                    outputLocations[0][i][3] * INPUT_SIZE,
                    outputLocations[0][i][2] * INPUT_SIZE
                )
                val labelOffset = 1
                recognitions.add(
                    Result(
                        "" + i,
                        labels[outputClasses[0][i].toInt() + labelOffset],
                        outputScores[0][i],
                        detection
                    )
                )
            }
            return@flatMap Single.just(recognitions)
        }
    }


    companion object Keys {
        //    const val MODEL_PATH = "tensorflow_inception_graph.pb"
        const val MODEL_PATH = "mobilenet_quant_v1_224.tflite"
        //        private const val MODEL_PATH = "detect.tflite"
        const val LABEL_PATH = "labels.txt"
        //        const val LABEL_PATH = "labelmap.txt"
        const val INPUT_NAME = "input"
        const val OUTPUT_NAME = "output"
        //        const val IMAGE_MEAN: Int = 0
//        const val IMAGE_STD: Float = 0.toFloat()
        const val INPUT_SIZE = 224
        const val MAX_RESULTS = 3
        const val DIM_BATCH_SIZE = 1
        const val DIM_PIXEL_SIZE = 3
        const val DIM_IMG_SIZE_X = 224
        const val DIM_IMG_SIZE_Y = 224


        private const val IMAGE_MEAN = 127.5f
        private const val IMAGE_STD = 127.5f

    }
}


