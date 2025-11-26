package com.example.keepyfitness.Model

data class FormFeedback(
    val exerciseId: Int,
    val feedbackType: FeedbackType,
    val message: String,
    val severity: FeedbackSeverity,
    val timestamp: Long = System.currentTimeMillis()
)

enum class FeedbackType {
    POSTURE_CORRECTION,
    RANGE_OF_MOTION,
    TIMING,
    ALIGNMENT,
    SAFETY_WARNING
}

enum class FeedbackSeverity {
    INFO,      // Thông tin chung
    WARNING,   // Cảnh báo nhẹ
    CRITICAL   // Cần sửa ngay
}
