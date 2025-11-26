package com.example.keepyfitness

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.keepyfitness.Model.ExerciseDataModel
import com.example.keepyfitness.Model.WorkoutHistory
import com.example.keepyfitness.Model.PersonalRecord
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

class WorkoutResultsActivity : AppCompatActivity() {

    private lateinit var exerciseDataModel: ExerciseDataModel
    private var completedCount: Int = 0
    private var targetCount: Int = 0
    private var workoutDuration: Long = 0 // in seconds
    private var caloriesBurned: Float = 0f // Changed from Double to Float
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_workout_results)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Get data from intent
        exerciseDataModel = intent.getSerializableExtra("exercise_data") as ExerciseDataModel
        completedCount = intent.getIntExtra("completed_count", 0)
        targetCount = intent.getIntExtra("target_count", 0)
        workoutDuration = intent.getLongExtra("workout_duration", 0)

        // Calculate calories burned (rough estimate)
        caloriesBurned = calculateCalories(exerciseDataModel.id, completedCount, workoutDuration)

        setupUI()
        migrateLocalDataToFirestore() // Migrate dữ liệu cũ
        saveWorkoutHistory()
    }

    private fun setupUI() {
        val exerciseImage = findViewById<ImageView>(R.id.exerciseImage)
        val exerciseName = findViewById<TextView>(R.id.exerciseName)
        val completedCountText = findViewById<TextView>(R.id.completedCount)
        val targetCountText = findViewById<TextView>(R.id.targetCount)
        val workoutDurationText = findViewById<TextView>(R.id.workoutDuration)
        val caloriesBurnedText = findViewById<TextView>(R.id.caloriesBurned)
        val completionPercentage = findViewById<TextView>(R.id.completionPercentage)
        val achievementMessage = findViewById<TextView>(R.id.achievementMessage)
        val btnWorkoutAgain = findViewById<MaterialButton>(R.id.btnWorkoutAgain)
        val btnFinish = findViewById<MaterialButton>(R.id.btnFinish)

        // Set exercise image and name
        Glide.with(this).asGif().load(exerciseDataModel.image).into(exerciseImage)
        exerciseName.text = exerciseDataModel.title

        // Set results
        completedCountText.text = "$completedCount reps"
        targetCountText.text = "$targetCount reps"

        // Format duration
        val minutes = workoutDuration / 60
        val seconds = workoutDuration % 60
        workoutDurationText.text = if (minutes > 0) {
            "${minutes} min ${seconds} sec"
        } else {
            "${seconds} sec"
        }

        caloriesBurnedText.text = "Bạn đã đốt cháy ${caloriesBurned.toInt()} calo"

        // Calculate completion percentage
        val percentage = if (targetCount > 0) {
            (completedCount.toFloat() / targetCount * 100).toInt()
        } else {
            0
        }
        completionPercentage.text = "$percentage%"

        // Set achievement message based on performance
        achievementMessage.text = when {
            percentage >= 100 -> "Excellent! You've achieved your goal! 🎉"
            percentage >= 75 -> "Great job! You're almost there! 💪"
            percentage >= 50 -> "Good work! Keep pushing yourself! 👍"
            percentage >= 25 -> "Nice start! You can do better next time! 🔥"
            else -> "Every start counts! Keep going! 💪"
        }

        // Set button listeners
        btnWorkoutAgain.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("data", exerciseDataModel)
            intent.putExtra("target_count", targetCount)
            startActivity(intent)
            finish()
        }

        btnFinish.setOnClickListener {
            val intent = Intent(this, HomeScreen::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun calculateCalories(exerciseId: Int, reps: Int, duration: Long): Float {
        // Rough calorie calculations based on exercise type
        val baseCaloriesPerRep = when (exerciseId) {
            1 -> 0.35f // Push ups
            2 -> 0.4f  // Squats
            3 -> 0.5f  // Jumping jacks
            4 -> 0.3f  // Plank to downward dog
            else -> 0.3f
        }

        // Factor in duration (higher intensity = more calories)
        val intensityFactor = if (duration > 0) {
            minOf(2.0f, reps.toFloat() / (duration / 60.0f)) // reps per minute
        } else {
            1.0f
        }

        return reps * baseCaloriesPerRep * intensityFactor
    }

    private fun saveWorkoutHistory() {
        val workoutHistory = WorkoutHistory(
            id = System.currentTimeMillis().toString(),
            exerciseId = exerciseDataModel.id.toShort(), // Convert to Short
            exerciseName = exerciseDataModel.title,
            count = completedCount.toShort(), // Convert to Short
            targetCount = targetCount.toShort(), // Convert to Short
            date = System.currentTimeMillis(),
            duration = workoutDuration.toInt(), // Convert to Int
            caloriesBurned = caloriesBurned, // Already Float
            isCompleted = completedCount >= targetCount
        )

        // Lưu vào SharedPreferences (giữ nguyên để tương thích)
        val prefs = getSharedPreferences("workout_history", MODE_PRIVATE)
        val gson = Gson()
        val type = object : TypeToken<MutableList<WorkoutHistory>>() {}.type
        val historyJson = prefs.getString("history_list", null)
        val historyList: MutableList<WorkoutHistory> = if (historyJson != null) {
            gson.fromJson(historyJson, type)
        } else {
            mutableListOf()
        }
        historyList.add(workoutHistory)
        prefs.edit().putString("history_list", gson.toJson(historyList)).apply()

        // Lưu vào Firestore
        val user = auth.currentUser
        if (user != null) {
            db.collection("users").document(user.uid).collection("workouts")
                .document(workoutHistory.id)
                .set(workoutHistory)
                .addOnSuccessListener {
                    // Cập nhật personal record
                    updatePersonalRecord(workoutHistory)
                }
                .addOnFailureListener { e ->
                    android.widget.Toast.makeText(this, "Lỗi lưu lịch sử: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
        } else {
            android.widget.Toast.makeText(this, "Vui lòng đăng nhập.", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun updatePersonalRecord(workout: WorkoutHistory) {
        val user = auth.currentUser ?: return
        val recordRef = db.collection("users").document(user.uid)
            .collection("personalRecords").document(workout.exerciseId.toString())

        // Lấy record hiện tại
        recordRef.get().addOnSuccessListener { document ->
            val existingRecord = document.toObject(PersonalRecord::class.java)
            val newRecord = if (existingRecord == null) {
                PersonalRecord(
                    exerciseId = workout.exerciseId,
                    exerciseName = workout.exerciseName,
                    maxCount = workout.count,
                    bestDate = workout.date,
                    totalWorkouts = 1,
                    averageCount = workout.count.toFloat() // Convert to Float
                )
            } else {
                val newTotalWorkouts = existingRecord.totalWorkouts + 1
                val newAverageCount = ((existingRecord.averageCount * existingRecord.totalWorkouts) + workout.count) / newTotalWorkouts
                PersonalRecord(
                    exerciseId = workout.exerciseId,
                    exerciseName = workout.exerciseName,
                    maxCount = maxOf(existingRecord.maxCount, workout.count),
                    bestDate = if (workout.count > existingRecord.maxCount) workout.date else existingRecord.bestDate,
                    totalWorkouts = newTotalWorkouts,
                    averageCount = newAverageCount
                )
            }

            // Lưu hoặc update record
            recordRef.set(newRecord)
                .addOnFailureListener { e ->
                    android.widget.Toast.makeText(this, "Lỗi cập nhật PR: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
        }.addOnFailureListener { e ->
            android.widget.Toast.makeText(this, "Lỗi tải PR: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun migrateLocalDataToFirestore() {
        val prefs = getSharedPreferences("workout_history", MODE_PRIVATE)
        val historyJson = prefs.getString("history_list", null)
        if (historyJson != null) {
            val gson = Gson()
            val type = object : TypeToken<List<WorkoutHistory>>() {}.type
            val historyList: List<WorkoutHistory> = gson.fromJson(historyJson, type)
            val user = auth.currentUser
            if (user != null) {
                val batch = db.batch()
                historyList.forEach { workout ->
                    batch.set(
                        db.collection("users").document(user.uid).collection("workouts").document(workout.id),
                        workout
                    )
                }
                batch.commit().addOnSuccessListener {
                    // Migrate personal records
                    historyList.groupBy { it.exerciseId }.forEach { (exerciseId, workouts) ->
                        val maxCount = workouts.maxByOrNull { it.count }
                        if (maxCount != null) {
                            val totalWorkouts = workouts.size
                            val averageCount = workouts.map { it.count.toInt() }.average().toFloat()
                            val newRecord = PersonalRecord(
                                exerciseId = exerciseId,
                                exerciseName = maxCount.exerciseName,
                                maxCount = maxCount.count,
                                bestDate = maxCount.date,
                                totalWorkouts = totalWorkouts,
                                averageCount = averageCount
                            )
                            db.collection("users").document(user.uid).collection("personalRecords")
                                .document(exerciseId.toString())
                                .set(newRecord)
                        }
                    }
                    // Không xóa local data để đảm bảo tương thích
                    // prefs.edit().remove("history_list").apply()
                }.addOnFailureListener { e ->
                    android.widget.Toast.makeText(this, "Lỗi migrate dữ liệu: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}