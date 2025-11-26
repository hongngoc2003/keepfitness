package com.example.keepyfitness

import android.content.res.AssetFileDescriptor
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

// Data class để lưu kết quả detection
data class Recognition(
    val id: String,
    val title: String,
    val confidence: Float,
    val location: RectF
)

class Classifier(
    assetManager: AssetManager,
    modelPath: String,
    labelPath: String,
    private val inputSize: Int = 224
) {
    private var interpreter: Interpreter
    private var labels: List<String>
    private var isQuantized: Boolean = false
    private var isObjectDetectionModel: Boolean = false

    companion object {
        private const val NUM_DETECTIONS = 10 // Số detection tối đa
    }

    init {
        val options = Interpreter.Options()
        options.setNumThreads(4)
        interpreter = Interpreter(loadModelFile(assetManager, modelPath), options)

        // Kiểm tra xem model có quantized không (UINT8)
        val inputTensor = interpreter.getInputTensor(0)
        val outputTensor = interpreter.getOutputTensor(0)

        isQuantized = outputTensor.dataType() == org.tensorflow.lite.DataType.UINT8

        // Thử load labels từ file
        val loadedLabels = try {
            loadLabels(assetManager, labelPath)
        } catch (e: Exception) {
            Log.w("Classifier", "Could not load labels from $labelPath: ${e.message}")
            emptyList()
        }

        // Kiểm tra xem có phải object detection model không
        val outputCount = interpreter.outputTensorCount
        isObjectDetectionModel = outputCount >= 4 // Object detection thường có 4 outputs: locations, classes, scores, numDetections

        Log.d("Classifier", "Model loaded: ${modelPath}")
        Log.d("Classifier", "Input shape: ${inputTensor.shape().contentToString()}")
        Log.d("Classifier", "Input type: ${inputTensor.dataType()}")
        Log.d("Classifier", "Output count: $outputCount")

        if (isObjectDetectionModel) {
            Log.d("Classifier", "✅ Object Detection Model detected!")
            for (i in 0 until outputCount) {
                val tensor = interpreter.getOutputTensor(i)
                Log.d("Classifier", "Output $i shape: ${tensor.shape().contentToString()}, type: ${tensor.dataType()}")
            }
        } else {
            Log.d("Classifier", "Output shape: ${outputTensor.shape().contentToString()}")
            Log.d("Classifier", "Output type: ${outputTensor.dataType()}")
        }

        Log.d("Classifier", "Is Quantized: ${isQuantized}")
        Log.d("Classifier", "Is Object Detection: ${isObjectDetectionModel}")
        Log.d("Classifier", "Labels count: ${loadedLabels.size}")

        labels = loadedLabels
    }

    private fun loadModelFile(assetManager: AssetManager, modelPath: String): ByteBuffer {
        val fileDescriptor: AssetFileDescriptor = assetManager.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel: FileChannel = inputStream.channel
        val startOffset: Long = fileDescriptor.startOffset
        val declaredLength: Long = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun loadLabels(assetManager: AssetManager, labelPath: String): List<String> {
        return assetManager.open(labelPath).bufferedReader().useLines { it.toList() }
    }

    // Hàm mới cho Object Detection - trả về nhiều kết quả
    fun recognizeImageMultiple(bitmap: Bitmap): List<Recognition> {
        if (!isObjectDetectionModel) {
            // Nếu không phải object detection model, dùng single recognition
            val (label, prob) = recognizeImage(bitmap)
            return listOf(
                Recognition(
                    id = "0",
                    title = label,
                    confidence = prob,
                    location = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
                )
            )
        }

        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val byteBuffer = convertBitmapToByteBuffer(resizedBitmap)

        // Object Detection outputs
        val outputLocations = Array(1) { Array(NUM_DETECTIONS) { FloatArray(4) } }
        val outputClasses = Array(1) { FloatArray(NUM_DETECTIONS) }
        val outputScores = Array(1) { FloatArray(NUM_DETECTIONS) }
        val numDetections = FloatArray(1)

        val outputMap = mapOf(
            0 to outputLocations,
            1 to outputClasses,
            2 to outputScores,
            3 to numDetections
        )

        interpreter.runForMultipleInputsOutputs(arrayOf(byteBuffer), outputMap)

        val recognitions = mutableListOf<Recognition>()
        val numDetectionsInt = numDetections[0].toInt().coerceAtMost(NUM_DETECTIONS)

        Log.d("Classifier", "✅ Detected $numDetectionsInt objects")

        // Giảm threshold xuống 30% để detect nhiều món hơn
        for (i in 0 until numDetectionsInt) {
            val score = outputScores[0][i]
            val classIndex = outputClasses[0][i].toInt()
            val label = if (classIndex < labels.size) labels[classIndex] else "Unknown"

            Log.d("Classifier", "Object $i: $label (class:$classIndex) - ${(score * 100).toInt()}%")

            if (score > 0.3f) { // Giảm threshold từ 0.5 xuống 0.3 (30%)
                val detection = RectF(
                    outputLocations[0][i][1] * inputSize,
                    outputLocations[0][i][0] * inputSize,
                    outputLocations[0][i][3] * inputSize,
                    outputLocations[0][i][2] * inputSize
                )

                recognitions.add(
                    Recognition(
                        id = i.toString(),
                        title = label,
                        confidence = score,
                        location = detection
                    )
                )

                Log.d("Classifier", "✅ Added: $label - ${(score * 100).toInt()}%")
            } else {
                Log.d("Classifier", "❌ Skipped: $label - ${(score * 100).toInt()}% (too low)")
            }
        }

        return recognitions.sortedByDescending { it.confidence }
    }

    // Hàm cũ cho Classification - trả về 1 kết quả
    fun recognizeImage(bitmap: Bitmap): Pair<String, Float> {
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val byteBuffer = convertBitmapToByteBuffer(resizedBitmap)

        // Lấy output shape từ model
        val outputTensor = interpreter.getOutputTensor(0)
        val outputShape = outputTensor.shape()
        val numClasses = outputShape[1] // [1, numClasses]

        return if (isQuantized) {
            val outputArray = Array(1) { ByteArray(numClasses) }
            interpreter.run(byteBuffer, outputArray)

            val output = outputArray[0]
            val maxIndex = output.indices.maxByOrNull { output[it] } ?: 0
            val maxValue = (output[maxIndex].toInt() and 0xFF) / 255.0f

            val labelName = if (maxIndex < labels.size) labels[maxIndex] else "unknown_$maxIndex"
            Log.d("Classifier", "Classification result: $labelName - ${(maxValue * 100).toInt()}%")

            labelName to maxValue
        } else {
            val outputArray = Array(1) { FloatArray(numClasses) }
            interpreter.run(byteBuffer, outputArray)

            val probabilities = outputArray[0]
            val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: 0

            val labelName = if (maxIndex < labels.size) labels[maxIndex] else "unknown_$maxIndex"
            Log.d("Classifier", "Classification result: $labelName - ${(probabilities[maxIndex] * 100).toInt()}%")

            labelName to probabilities[maxIndex]
        }
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val inputTensor = interpreter.getInputTensor(0)
        val isInputQuantized = inputTensor.dataType() == org.tensorflow.lite.DataType.UINT8

        val inputShape = inputTensor.shape()
        val expectedBytes = inputShape.reduce { acc, i -> acc * i } * if (isInputQuantized) 1 else 4
        val actuallyNeedsFloat = expectedBytes > 50000 || !isInputQuantized

        val byteBuffer = if (actuallyNeedsFloat) {
            ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
        } else {
            ByteBuffer.allocateDirect(inputSize * inputSize * 3)
        }
        byteBuffer.order(ByteOrder.nativeOrder())

        val intValues = IntArray(inputSize * inputSize)
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        var pixel = 0
        for (i in 0 until inputSize) {
            for (j in 0 until inputSize) {
                val value = intValues[pixel++]

                val r = (value shr 16 and 0xFF)
                val g = (value shr 8 and 0xFF)
                val b = (value and 0xFF)

                if (actuallyNeedsFloat) {
                    byteBuffer.putFloat(r / 255.0f)
                    byteBuffer.putFloat(g / 255.0f)
                    byteBuffer.putFloat(b / 255.0f)
                } else {
                    byteBuffer.put(r.toByte())
                    byteBuffer.put(g.toByte())
                    byteBuffer.put(b.toByte())
                }
            }
        }

        byteBuffer.rewind()
        return byteBuffer
    }

    fun close() {
        interpreter.close()
    }
}
