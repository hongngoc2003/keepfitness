package com.example.keepyfitness

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark

class PoseOverlay(context:Context?, attr: AttributeSet?): View(context, attr) {
    var imageWidth:Int = 640
    var imageHeight:Int = 480

    private var scaleX: Float = 0f
    private var scaleY: Float = 0f

    var videoWidth: Int = width
    var videoHeight: Int = height

    var sensorOrientation: Int = 0

    private var pose: Pose? = null

    // Paint cho các điểm pose
    var paint = Paint().apply {
        color = Color.RED
        strokeWidth = 6f
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // Paint cho bên trái (màu xanh dương)
    var paintLeft = Paint().apply {
        color = Color.BLUE
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    // Paint cho bên phải (màu vàng)
    var paintRight = Paint().apply {
        color = Color.YELLOW
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    fun setPose(pose: Pose) {
        this.pose = pose
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if(sensorOrientation == 90 || sensorOrientation == 270) {
            scaleX = videoWidth/imageHeight.toFloat()
            scaleY = videoHeight/imageWidth.toFloat()
        } else {
            scaleX = videoHeight/imageHeight.toFloat()
            scaleY = videoWidth/imageWidth.toFloat()
        }

        scaleX = videoWidth/imageHeight.toFloat()
        scaleY = videoHeight/imageWidth.toFloat()

        // VẼ TẤT CẢ CÁC ĐIỂM POSE QUAN TRỌNG để có độ chính xác cao nhất
        pose?.let { currentPose ->
            // Vẽ tất cả các điểm pose landmarks quan trọng
            val allKeyPoints = listOf(
                PoseLandmark.NOSE,
                PoseLandmark.LEFT_EYE, PoseLandmark.RIGHT_EYE,
                PoseLandmark.LEFT_EAR, PoseLandmark.RIGHT_EAR,
                PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER,
                PoseLandmark.LEFT_ELBOW, PoseLandmark.RIGHT_ELBOW,
                PoseLandmark.LEFT_WRIST, PoseLandmark.RIGHT_WRIST,
                PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP,
                PoseLandmark.LEFT_KNEE, PoseLandmark.RIGHT_KNEE,
                PoseLandmark.LEFT_ANKLE, PoseLandmark.RIGHT_ANKLE
            )

            allKeyPoints.forEach { landmarkType ->
                currentPose.getPoseLandmark(landmarkType)?.let { landmark ->
                    canvas.drawCircle(
                        landmark.position.x * scaleX,
                        landmark.position.y * scaleY,
                        8f, // Kích thước điểm vừa phải để dễ nhìn
                        paint
                    )
                }
            }
        }

        // VẼ SKELETON ĐẦY ĐỦ - tất cả các đường nối quan trọng

        // FACE CONNECTIONS
        drawPoseLines(canvas, PoseLandmark.LEFT_EYE, PoseLandmark.RIGHT_EYE, paint)
        drawPoseLines(canvas, PoseLandmark.LEFT_EAR, PoseLandmark.LEFT_EYE, paintLeft)
        drawPoseLines(canvas, PoseLandmark.RIGHT_EAR, PoseLandmark.RIGHT_EYE, paintRight)

        // TORSO CONNECTIONS
        drawPoseLines(canvas, PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER, paint)
        drawPoseLines(canvas, PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_HIP, paintLeft)
        drawPoseLines(canvas, PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_HIP, paintRight)
        drawPoseLines(canvas, PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP, paint)

        // LEFT ARM CONNECTIONS
        drawPoseLines(canvas, PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW, paintLeft)
        drawPoseLines(canvas, PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST, paintLeft)

        // RIGHT ARM CONNECTIONS
        drawPoseLines(canvas, PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW, paintRight)
        drawPoseLines(canvas, PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST, paintRight)

        // LEFT LEG CONNECTIONS
        drawPoseLines(canvas, PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE, paintLeft)
        drawPoseLines(canvas, PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE, paintLeft)

        // RIGHT LEG CONNECTIONS
        drawPoseLines(canvas, PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE, paintRight)
        drawPoseLines(canvas, PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE, paintRight)
    }

    fun drawPoseLines(canvas: Canvas, startPoint: Int, endPoint: Int, paint: Paint) {
        var pointStart = pose?.getPoseLandmark(startPoint)
        var pointEnd = pose?.getPoseLandmark(endPoint)

        if (pointStart != null && pointEnd != null) {
            canvas.drawLine(
                pointStart.position.x * scaleX,
                pointStart.position.y * scaleY,
                pointEnd.position.x * scaleX,
                pointEnd.position.y * scaleY,
                paint
            )
        }
    }

}