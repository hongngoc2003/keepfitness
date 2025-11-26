package com.example.keepyfitness

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.*

class VoiceCoach(private val context: Context) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "VoiceCoach"
    }

    private var textToSpeech: TextToSpeech? = null
    private var _isInitialized = false
    val isInitialized: Boolean
        get() = _isInitialized
    private var speechQueue = mutableListOf<String>()

    // Throttling để tránh spam voice
    private var lastSpeechTime = 0L
    private val speechCooldown = 200L // Giảm cooldown xuống 200ms để giảm trễ
    private var lastFormFeedbackTime = 0L
    private val formFeedbackCooldown = 5000L // 5 giây

    init {
        initTextToSpeech()
    }

    private fun initTextToSpeech() {
        textToSpeech = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.let { tts ->
                val vietnameseLocale = Locale("vi", "VN")
                val result = tts.setLanguage(vietnameseLocale)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "Tiếng Việt chưa được cài đặt trên thiết bị")
                    return
                }

                tts.setSpeechRate(1.0f)
                tts.setPitch(1.0f)
                _isInitialized = true
                Log.d(TAG, "TextToSpeech khởi tạo với tiếng Việt")

                // Xử lý các message đang queue
                processSpeechQueue()
            }
        } else {
            Log.e(TAG, "Khởi tạo TextToSpeech thất bại")
        }
    }

    private fun processSpeechQueue() {
        // Không phát lại các message cũ khi TTS vừa khởi tạo
        speechQueue.clear()
    }

    private fun speakNow(message: String, force: Boolean = false) {
        // Nếu đang nói thì bỏ qua message mới để không bị dồn
        if (isSpeaking()) return
        val currentTime = System.currentTimeMillis()
        if (!force && currentTime - lastSpeechTime < speechCooldown) return
        lastSpeechTime = currentTime
        textToSpeech?.setLanguage(Locale("vi", "VN"))
        textToSpeech?.speak(message, TextToSpeech.QUEUE_ADD, null, null)
    }

    fun speak(message: String, force: Boolean = false) {
        if (_isInitialized) {
            speakNow(message, force)
        } else {
            // Không queue lại message, chỉ bỏ qua nếu chưa sẵn sàng
        }
    }

    // Thông báo bắt đầu bài tập
    fun announceExerciseStart(exerciseName: String) {
        speak("Bắt đầu $exerciseName. Chuẩn bị nhé!")
    }

    fun announceWorkoutStart(exerciseName: String, targetCount: Int) {
        speak("Bắt đầu $exerciseName. Mục tiêu $targetCount lần. Bắt đầu nào!", force = true)
    }

    // Thông báo lỗi động tác
    fun announceFormError(errorMessage: String) {
        speak("Sửa tư thế: $errorMessage")
    }

    fun giveFormFeedback(feedback: String, isPositive: Boolean) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastFormFeedbackTime < formFeedbackCooldown) return
        lastFormFeedbackTime = currentTime

        if (isPositive) speak("Tư thế tốt")
        else speak("Chỉnh lại tư thế")
    }

    // Lỗi chi tiết cho từng bài tập
    fun announcePushUpErrors(errorType: String) {
        when (errorType) {
            "ELBOW_FLARE" -> speak("Khuỷu tay rộng quá. Giữ khuỷu tay sát cơ thể")
            "BACK_SAG" -> speak("Giữ lưng thẳng. Siết cơ bụng")
            "INCOMPLETE_RANGE" -> speak("Hạ thấp hơn. Ngực gần sàn hơn")
            "HEAD_POSITION" -> speak("Nhìn xuống sàn, giữ cổ thẳng")
            else -> speak("Chú ý tư thế hít đất")
        }
    }

    fun announceSquatErrors(errorType: String) {
        when (errorType) {
            "KNEE_CAVE" -> speak("Đầu gối chạm vào nhau. Mở rộng đầu gối ra")
            "FORWARD_LEAN" -> speak("Giữ ngực thẳng. Không nghiêng về trước")
            "HEEL_LIFT" -> speak("Giữ gót chân chạm sàn")
            "SHALLOW_SQUAT" -> speak("Ngồi xổm sâu hơn")
            "KNEE_FORWARD" -> speak("Đầu gối vượt quá mũi chân. Đẩy hông ra sau")
            else -> speak("Chú ý tư thế squat")
        }
    }

    fun announceJumpingJackErrors(errorType: String) {
        when (errorType) {
            "ARMS_LOW" -> speak("Giơ tay cao hơn, qua đầu")
            "FEET_NARROW" -> speak("Nhảy rộng hơn, mở chân ra")
            "TIMING_OFF" -> speak("Động tác chưa đồng bộ, nhảy cùng tay")
            "INCOMPLETE_RETURN" -> speak("Trở về vị trí ban đầu hoàn toàn")
            else -> speak("Chú ý tư thế jumping jack")
        }
    }

    fun announcePlankErrors(errorType: String) {
        when (errorType) {
            "HIP_SAG" -> speak("Hông thấp quá, nâng lên")
            "HIP_HIGH" -> speak("Hông cao quá, hạ xuống")
            "ARM_POSITION" -> speak("Đặt tay dưới vai")
            "HEAD_POSITION" -> speak("Nhìn xuống, giữ cổ thẳng")
            else -> speak("Chú ý tư thế plank")
        }
    }

    // Thông báo động viên
    fun giveMotivation() {
        val messages = listOf(
            "Làm tốt lắm!",
            "Cố lên!",
            "Bạn làm được!",
            "Tuyệt vời!",
            "Hoàn hảo!"
        )
        speak(messages.random())
    }

    fun announceSafetyWarning(warning: String) {
        speak("Cảnh báo an toàn: $warning")
    }

    fun announceFormQuality(quality: Int) {
        when {
            quality >= 90 -> speak("Tư thế xuất sắc!")
            quality >= 80 -> speak("Tư thế tốt")
            quality >= 70 -> speak("Tư thế ổn")
            quality >= 60 -> speak("Cần cải thiện tư thế")
            else -> speak("Chú ý tư thế")
        }
    }

    fun announceCount(count: Int, exerciseName: String) {
        speak("$count")
    }

    fun announceProgress(current: Int, target: Int) {
        val percentage = (current.toFloat() / target * 100).toInt()
        when {
            current == target -> speak("Hoàn thành!")
            current == target / 2 -> speak("Đã làm được một nửa!")
            percentage % 25 == 0 && percentage > 0 -> speak("$percentage phần trăm")
        }
    }

    fun announceRep(currentRep: Int, totalReps: Int) {
        speak("$currentRep")
        when (currentRep) {
            totalReps -> speak("Hoàn thành bài tập! Tuyệt vời!")
            totalReps / 2 -> speak("Đã làm được nửa chặng đường! Cố lên!")
        }
    }

    fun announceCountdown(seconds: Int) {
        when (seconds) {
            in 1..5 -> speak("$seconds")
            10 -> speak("Còn 10 giây")
            else -> if (seconds % 10 == 0) speak("$seconds giây")
        }
    }

    fun announceMotivation() {
        val messages = listOf(
            "Bạn đang làm rất tốt!",
            "Cố lên nào!",
            "Giữ phong độ!",
            "Tư thế hoàn hảo!",
            "Bạn làm được!",
            "Tuyệt vời!",
            "Tiếp tục nhé!"
        )
        speak(messages.random())
    }

    fun announceFormCorrection(correction: String) {
        speak("Sửa tư thế: $correction")
    }

    fun announceWorkoutComplete() {
        speak("Hoàn thành bài tập! Làm tốt lắm!")
    }

    fun announceRest(seconds: Int) {
        speak("Nghỉ $seconds giây")
    }

    fun stop() {
        textToSpeech?.stop()
    }

    fun shutdown() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        _isInitialized = false
        speechQueue.clear()
    }

    fun isSpeaking(): Boolean {
        return textToSpeech?.isSpeaking ?: false
    }

    fun setLanguageVietnamese() {
        textToSpeech?.setLanguage(Locale("vi", "VN"))
    }

    fun setSpeechRate(rate: Float) {
        textToSpeech?.setSpeechRate(rate)
    }

    fun setPitch(pitch: Float) {
        textToSpeech?.setPitch(pitch)
    }
    fun announceTreePoseErrors(errorType: String) {
        when (errorType) {
            "NO_LEG_LIFTED" -> speak("Hãy nhấc một chân lên để vào tư thế cây")
            "BENT_SUPPORT_LEG" -> speak("Chân trụ chưa thẳng, hãy đứng thẳng hơn")
            else -> speak("Chú ý tư thế đứng một chân")
        }
    }

}
