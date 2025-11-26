package com.example.keepyfitness

import com.example.keepyfitness.Model.FormFeedback
import com.example.keepyfitness.Model.FeedbackType
import com.example.keepyfitness.Model.FeedbackSeverity
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.*

class FormCorrector {

    companion object {
        private const val TAG = "FormCorrector"
    }

    fun analyzeForm(exerciseId: Int, pose: Pose): List<FormFeedback> {
        val feedbacks = mutableListOf<FormFeedback>()

        when (exerciseId) {
            1 -> feedbacks.addAll(analyzePushUpForm(pose))
            2 -> feedbacks.addAll(analyzeSquatForm(pose))
            3 -> feedbacks.addAll(analyzeJumpingJackForm(pose))
            4 -> feedbacks.addAll(analyzePlankForm(pose))
            5 -> feedbacks.addAll(analyzeTreePose(pose)) // ✅ thêm case Tree Pose
        }

        return feedbacks
    }

    fun calculateFormQuality(exerciseId: Int, pose: Pose): Int {
        val feedbacks = analyzeForm(exerciseId, pose)

        var baseScore = 100

        feedbacks.forEach { feedback ->
            when (feedback.severity) {
                FeedbackSeverity.CRITICAL -> baseScore -= 20
                FeedbackSeverity.WARNING -> baseScore -= 10
                FeedbackSeverity.INFO -> baseScore -= 5
            }
        }

        return maxOf(0, baseScore)
    }

    private fun analyzeTreePose(pose: Pose): List<FormFeedback> {
        val feedbacks = mutableListOf<FormFeedback>()

        val leftAnkle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
        val rightAnkle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)
        val leftKnee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
        val rightKnee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)
        val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
        val rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)

        if (leftAnkle == null || rightAnkle == null ||
            leftKnee == null || rightKnee == null ||
            leftHip == null || rightHip == null ||
            leftShoulder == null || rightShoulder == null) {
            return feedbacks
        }

        // 1. Kiểm tra có đứng bằng 1 chân không
        val ankleDiffY = abs(leftAnkle.position.y - rightAnkle.position.y)
        if (ankleDiffY < 50f) {
            feedbacks.add(
                FormFeedback(
                    exerciseId = 5,
                    feedbackType = FeedbackType.ALIGNMENT,
                    message = "Hãy nhấc một chân lên để vào tư thế cây",
                    severity = FeedbackSeverity.INFO
                )
            )
        }

        // 2. Kiểm tra chân trụ có thẳng không
        val leftHigher = leftAnkle.position.y < rightAnkle.position.y
        val rightHigher = rightAnkle.position.y < leftAnkle.position.y
        if (leftHigher) {
            val angle = calculateAngleSafe(rightHip, rightKnee, rightAnkle)
            if (angle < 170) {
                feedbacks.add(
                    FormFeedback(
                        exerciseId = 5,
                        feedbackType = FeedbackType.POSTURE_CORRECTION,
                        message = "Chân trụ chưa thẳng, hãy đứng thẳng hơn",
                        severity = FeedbackSeverity.WARNING
                    )
                )
            }
        }
        if (rightHigher) {
            val angle = calculateAngleSafe(leftHip, leftKnee, leftAnkle)
            if (angle < 170) {
                feedbacks.add(
                    FormFeedback(
                        exerciseId = 5,
                        feedbackType = FeedbackType.POSTURE_CORRECTION,
                        message = "Chân trụ chưa thẳng, hãy đứng thẳng hơn",
                        severity = FeedbackSeverity.WARNING
                    )
                )
            }
        }

        // 3. Kiểm tra vai có cân bằng không
        val shoulderDiffY = abs(leftShoulder.position.y - rightShoulder.position.y)
        if (shoulderDiffY > 40f) {
            feedbacks.add(
                FormFeedback(
                    exerciseId = 5,
                    feedbackType = FeedbackType.ALIGNMENT,
                    message = "Giữ vai cân bằng, lưng thẳng",
                    severity = FeedbackSeverity.WARNING
                )
            )
        }

        // 4. Kiểm tra tay chắp lại hoặc đưa lên cao
        if (leftWrist != null && rightWrist != null) {
            val wristDiffY = abs(leftWrist.position.y - rightWrist.position.y)
            if (!(leftWrist.position.y < leftShoulder.position.y &&
                        rightWrist.position.y < rightShoulder.position.y &&
                        wristDiffY < 60f)) {
                feedbacks.add(
                    FormFeedback(
                        exerciseId = 5,
                        feedbackType = FeedbackType.POSTURE_CORRECTION,
                        message = "Giữ hai tay chắp lại hoặc đưa lên cao",
                        severity = FeedbackSeverity.INFO
                    )
                )
            }
        }

        // 5. Kiểm tra chân nhấc đặt đúng vị trí (gần đùi trong)
        val liftedKnee = if (leftHigher) leftKnee else if (rightHigher) rightKnee else null
        val supportHip = if (leftHigher) rightHip else if (rightHigher) leftHip else null
        if (liftedKnee != null && supportHip != null) {
            val kneeToHipDist = abs(liftedKnee.position.y - supportHip.position.y)
            if (kneeToHipDist > 250f) {
                feedbacks.add(
                    FormFeedback(
                        exerciseId = 5,
                        feedbackType = FeedbackType.ALIGNMENT,
                        message = "Đặt chân nhấc lên gần đùi trong hoặc bắp chân",
                        severity = FeedbackSeverity.WARNING
                    )
                )
            }
        }

        return feedbacks
    }
    fun analyzePushUpForm(pose: Pose): List<FormFeedback> {
        val feedbacks = mutableListOf<FormFeedback>()

        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val leftElbow = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW)
        val rightElbow = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW)
        val leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
        val rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)
        val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val leftKnee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
        val rightKnee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)

        if (leftShoulder != null && rightShoulder != null &&
            leftElbow != null && rightElbow != null &&
            leftWrist != null && rightWrist != null &&
            leftHip != null && rightHip != null) {

            // Kiểm tra góc cùi chỏ
            val leftElbowAngle = calculateAngle(leftShoulder, leftElbow, leftWrist)
            val rightElbowAngle = calculateAngle(rightShoulder, rightElbow, rightWrist)

            // Kiểm tra cùi chỏ quá ra ngoài
            if (leftElbowAngle > 90 || rightElbowAngle > 90) {
                val wristShoulderDistance = distance(leftWrist, leftShoulder)
                val elbowShoulderDistance = distance(leftElbow, leftShoulder)

                if (wristShoulderDistance > elbowShoulderDistance * 1.5) {
                    feedbacks.add(FormFeedback(
                        exerciseId = 1,
                        feedbackType = FeedbackType.POSTURE_CORRECTION,
                        message = "Khuỷu tay đang dang quá rộng, hãy giữ khuỷu tay vào gần cơ thể bạn",
                        severity = FeedbackSeverity.WARNING
                    ))
                }
            }

            // Kiểm tra thẳng lưng
            val knee = leftKnee ?: rightKnee
            if (knee != null) {
                val torsoAngle = calculateAngle(leftShoulder, leftHip, knee)
                if (torsoAngle < 160) {
                    feedbacks.add(FormFeedback(
                        exerciseId = 1,
                        feedbackType = FeedbackType.POSTURE_CORRECTION,
                        message = "Hãy giữ lưng thẳng, không cong hay cúi xuống.",
                        severity = FeedbackSeverity.CRITICAL
                    ))
                }
            }

            // Kiểm tra tay không thẳng hàng
            val handAlignment = abs(leftWrist.position.y - rightWrist.position.y)
            if (handAlignment > 50) {
                feedbacks.add(FormFeedback(
                    exerciseId = 1,
                    feedbackType = FeedbackType.ALIGNMENT,
                    message = "Hai tay không thẳng hàng, hãy đặt hai tay đều nhau.",
                    severity = FeedbackSeverity.WARNING
                ))
            }

            // Feedback tích cực
            if (leftElbowAngle in 45.0..90.0 && rightElbowAngle in 45.0..90.0) {
                feedbacks.add(FormFeedback(
                    exerciseId = 1,
                    feedbackType = FeedbackType.POSTURE_CORRECTION,
                    message = "Góc khuỷu tay tuyệt vời!",
                    severity = FeedbackSeverity.INFO
                ))
            }
        }

        return feedbacks
    }

    private fun analyzeSquatForm(pose: Pose): List<FormFeedback> {
        val feedbacks = mutableListOf<FormFeedback>()

        val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val leftKnee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
        val rightKnee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)
        val leftAnkle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
        val rightAnkle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)

        if (leftHip != null && rightHip != null &&
            leftKnee != null && rightKnee != null &&
            leftAnkle != null && rightAnkle != null) {

            // Kiểm tra góc đầu gối
            val leftKneeAngle = calculateAngle(leftHip, leftKnee, leftAnkle)
            val rightKneeAngle = calculateAngle(rightHip, rightKnee, rightAnkle)

            // Kiểm tra đầu gối không vượt quá ngón chân
            if (leftKnee.position.x > leftAnkle.position.x + 30 ||
                rightKnee.position.x > rightAnkle.position.x + 30) {
                feedbacks.add(FormFeedback(
                    exerciseId = 2,
                    feedbackType = FeedbackType.SAFETY_WARNING,
                    message = "Đầu gối đang qua ngón chân, hãy đẩy hông về phía sau.",
                    severity = FeedbackSeverity.CRITICAL
                ))
            }

            // Kiểm tra độ sâu squat
            val avgHipY = (leftHip.position.y + rightHip.position.y) / 2
            val avgKneeY = (leftKnee.position.y + rightKnee.position.y) / 2

            if (avgHipY < avgKneeY - 20) {
                feedbacks.add(FormFeedback(
                    exerciseId = 2,
                    feedbackType = FeedbackType.RANGE_OF_MOTION,
                    message = "Squat chưa đủ sâu, hãy hạ thấp hơn.",
                    severity = FeedbackSeverity.WARNING
                ))
            }

            // Kiểm tra chân không đều
            val footAlignment = abs(leftAnkle.position.x - rightAnkle.position.x)
            val shoulderWidth = distance(leftHip, rightHip)

            if (footAlignment > shoulderWidth * 1.5) {
                feedbacks.add(FormFeedback(
                    exerciseId = 2,
                    feedbackType = FeedbackType.ALIGNMENT,
                    message = "Chân đang để quá rộng, hãy chỉnh lại tư thế để chân gần nhau hơn.",
                    severity = FeedbackSeverity.WARNING
                ))
            }

            // Feedback tích cực
            if (leftKneeAngle in 80.0..100.0 && rightKneeAngle in 80.0..100.0) {
                feedbacks.add(FormFeedback(
                    exerciseId = 2,
                    feedbackType = FeedbackType.POSTURE_CORRECTION,
                    message = "Góc squat hoàn hảo.",
                    severity = FeedbackSeverity.INFO
                ))
            }
        }

        return feedbacks
    }

    private fun analyzeJumpingJackForm(pose: Pose): List<FormFeedback> {
        val feedbacks = mutableListOf<FormFeedback>()

        val leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
        val rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)
        val leftAnkle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
        val rightAnkle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)

        if (leftWrist != null && rightWrist != null &&
            leftAnkle != null && rightAnkle != null &&
            leftShoulder != null && rightShoulder != null) {

            // Kiểm tra tay có giơ cao đủ không
            val avgShoulderY = (leftShoulder.position.y + rightShoulder.position.y) / 2
            val avgWristY = (leftWrist.position.y + rightWrist.position.y) / 2

            if (avgWristY > avgShoulderY - 20) {
                feedbacks.add(FormFeedback(
                    exerciseId = 3,
                    feedbackType = FeedbackType.RANGE_OF_MOTION,
                    message = "Cần giơ tay cao hơn nữa, giơ trên đầu bạn.",
                    severity = FeedbackSeverity.WARNING
                ))
            }

            // Kiểm tra hai tay không đồng bộ
            val handSync = abs(leftWrist.position.y - rightWrist.position.y)
            if (handSync > 40) {
                feedbacks.add(FormFeedback(
                    exerciseId = 3,
                    feedbackType = FeedbackType.TIMING,
                    message = "Nâng cả hai tay lên cùng nhau.",
                    severity = FeedbackSeverity.WARNING
                ))
            }

            // Kiểm tra chân không tách đủ xa
            val footDistance = distance(leftAnkle, rightAnkle)
            val shoulderWidth = distance(leftShoulder, rightShoulder)

            if (footDistance < shoulderWidth * 1.2) {
                feedbacks.add(FormFeedback(
                    exerciseId = 3,
                    feedbackType = FeedbackType.RANGE_OF_MOTION,
                    message = "Mở rộng chân ra, vượt qua khoảng cách vai.",
                    severity = FeedbackSeverity.WARNING
                ))
            }

            // Feedback tích cực
            if (avgWristY < avgShoulderY - 50 && footDistance > shoulderWidth * 1.5) {
                feedbacks.add(FormFeedback(
                    exerciseId = 3,
                    feedbackType = FeedbackType.POSTURE_CORRECTION,
                    message = "Tư thế hoàn hảo.",
                    severity = FeedbackSeverity.INFO
                ))
            }
        }

        return feedbacks
    }

    private fun analyzePlankForm(pose: Pose): List<FormFeedback> {
        val feedbacks = mutableListOf<FormFeedback>()

        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val leftAnkle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
        val rightAnkle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)

        if (leftShoulder != null && rightShoulder != null &&
            leftHip != null && rightHip != null &&
            leftAnkle != null && rightAnkle != null) {

            // Kiểm tra thẳng lưng
            val shoulderY = (leftShoulder.position.y + rightShoulder.position.y) / 2
            val hipY = (leftHip.position.y + rightHip.position.y) / 2
            val ankleY = (leftAnkle.position.y + rightAnkle.position.y) / 2

            val hipSag = abs(hipY - shoulderY)
            val ankleSag = abs(ankleY - shoulderY)

            if (hipSag > 40) {
                if (hipY > shoulderY) {
                    feedbacks.add(FormFeedback(
                        exerciseId = 4,
                        feedbackType = FeedbackType.POSTURE_CORRECTION,
                        message = "Hông đang quá thấp, nâng hông của bạn lên",
                        severity = FeedbackSeverity.CRITICAL
                    ))
                } else {
                    feedbacks.add(FormFeedback(
                        exerciseId = 4,
                        feedbackType = FeedbackType.POSTURE_CORRECTION,
                        message = "Hông đang quá cao, hãy hạ hông thấp hơn.",
                        severity = FeedbackSeverity.CRITICAL
                    ))
                }
            }

            // Feedback tích cực
            if (hipSag < 30 && ankleSag < 30) {
                feedbacks.add(FormFeedback(
                    exerciseId = 4,
                    feedbackType = FeedbackType.POSTURE_CORRECTION,
                    message = "Tuyệt vời, hãy giữ cơ thể bạn thẳng.",
                    severity = FeedbackSeverity.INFO
                ))
            }
        }

        return feedbacks
    }

    private fun calculateAngleSafe(first: PoseLandmark, mid: PoseLandmark, last: PoseLandmark): Double {
        val a = distance(mid, last)
        val b = distance(first, mid)
        val c = distance(first, last)

        if (a == 0.0 || b == 0.0) return 180.0 // tránh chia 0

        val cosValue = ((b * b + a * a - c * c) / (2 * b * a)).coerceIn(-1.0, 1.0)
        return acos(cosValue) * (180 / PI)
    }

    private fun calculateAngle(first: PoseLandmark, mid: PoseLandmark, last: PoseLandmark): Double {
        val a = distance(mid, last)
        val b = distance(first, mid)
        val c = distance(first, last)
        return acos((b * b + a * a - c * c) / (2 * b * a)) * (180 / PI)
    }

    private fun distance(p1: PoseLandmark, p2: PoseLandmark): Double {
        val dx = p1.position.x - p2.position.x
        val dy = p1.position.y - p2.position.y
        return sqrt((dx * dx + dy * dy).toDouble())
    }
}

