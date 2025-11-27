package com.example.keepyfitness

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.keepyfitness.Model.ExerciseDataModel
import com.example.keepyfitness.Model.WorkoutHistory
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

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
        migrateLocalDataToFirestore() // Migrate dữ liệu cũ (will be skipped unless FIREBASE enabled)
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
            exerciseId = exerciseDataModel.id.toShort(),
            exerciseName = exerciseDataModel.title,
            count = completedCount.toShort(),
            targetCount = targetCount.toShort(),
            date = System.currentTimeMillis(),
            duration = workoutDuration.toInt(),
            caloriesBurned = caloriesBurned.toFloat(), // Convert Double to Float
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

        // Only attempt to write to Firestore if user has enabled it in prefs
        val useFirebase = prefs.getBoolean("use_firebase", false)
        if (!useFirebase) {
            // Skip Firestore to avoid serialization issues with Short types
            return
        }

        // Lưu vào Firestore (an toàn với kiểu dữ liệu: chuyển Short -> Int/Long trước khi gửi)
        val user = auth.currentUser
        if (user != null) {
            try {
                val workoutMap: Map<String, Any> = mapOf(
                    "id" to workoutHistory.id,
                    "exerciseId" to workoutHistory.exerciseId.toInt(),
                    "exerciseName" to workoutHistory.exerciseName,
                    "count" to workoutHistory.count.toInt(),
                    "targetCount" to workoutHistory.targetCount.toInt(),
                    "date" to workoutHistory.date,
                    "duration" to workoutHistory.duration,
                    "caloriesBurned" to workoutHistory.caloriesBurned,
                    "isCompleted" to workoutHistory.isCompleted
                )

                db.collection("users").document(user.uid).collection("workouts")
                    .document(workoutHistory.id)
                    .set(workoutMap)
                    .addOnSuccessListener {
                        // Cập nhật personal record safely
                        updatePersonalRecordSafe(workoutHistory)
                    }
                    .addOnFailureListener { e ->
                        android.widget.Toast.makeText(this, "Lỗi lưu lịch sử: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                    }
            } catch (e: Exception) {
                // Catch serialization problems or other sync exceptions
                android.widget.Toast.makeText(this, "Lỗi lưu vào Firestore: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        } else {
            // Not logged in: skip Firestore write but inform user
            android.widget.Toast.makeText(this, "Không đăng nhập: lịch sử được lưu cục bộ.", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Update personal record in Firestore safely without relying on automatic
     * deserialization to Kotlin data classes (which can fail for Short fields).
     * We read primitive numeric fields from the document and write back a Map.
     */
    private fun updatePersonalRecordSafe(workout: WorkoutHistory) {
        val user = auth.currentUser ?: return
        val recordRef = db.collection("users").document(user.uid)
            .collection("personalRecords").document(workout.exerciseId.toString())

        recordRef.get().addOnSuccessListener { document ->
            try {
                // Read existing values using safe getters
                val existingMaxCount = document.getLong("maxCount")?.toInt() ?: 0
                val existingBestDate = document.getLong("bestDate") ?: 0L
                val existingTotalWorkouts = document.getLong("totalWorkouts")?.toInt() ?: 0
                val existingAverageCount = document.getDouble("averageCount")?.toFloat() ?: 0f
                val existingExerciseName = document.getString("exerciseName") ?: workout.exerciseName

                val newTotalWorkouts = existingTotalWorkouts + 1
                val newAverageCount = if (existingTotalWorkouts == 0) {
                    workout.count.toFloat()
                } else {
                    (((existingAverageCount * existingTotalWorkouts) + workout.count) / newTotalWorkouts)
                }
                val newMaxCount = maxOf(existingMaxCount, workout.count.toInt())
                val newBestDate = if (workout.count.toInt() > existingMaxCount) workout.date else existingBestDate

                // Create a map with safe numeric types (Int/Long/Float)
                val newRecordMap: Map<String, Any> = mapOf(
                    "exerciseId" to workout.exerciseId.toInt(),
                    "exerciseName" to existingExerciseName,
                    "maxCount" to newMaxCount,
                    "bestDate" to newBestDate,
                    "totalWorkouts" to newTotalWorkouts,
                    "averageCount" to newAverageCount
                )

                recordRef.set(newRecordMap).addOnFailureListener { e ->
                    android.widget.Toast.makeText(this, "Lỗi cập nhật PR: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(this, "Lỗi xử lý PR: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }.addOnFailureListener { e ->
            android.widget.Toast.makeText(this, "Lỗi tải PR: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun migrateLocalDataToFirestore() {
        val prefs = getSharedPreferences("workout_history", MODE_PRIVATE)
        val historyJson = prefs.getString("history_list", null)

        // Only attempt migration if the user enabled Firebase usage
        val useFirebase = prefs.getBoolean("use_firebase", false)
        if (!useFirebase) return

        if (historyJson != null) {
            val gson = Gson()
            val type = object : TypeToken<List<WorkoutHistory>>() {}.type
            val historyList: List<WorkoutHistory> = gson.fromJson(historyJson, type)
            val user = auth.currentUser
            if (user != null) {
                try {
                    val batch = db.batch()
                    historyList.forEach { workout ->
                        // Convert to Map to avoid Short serialization issues
                        val workoutMap = mapOf(
                            "id" to workout.id,
                            "exerciseId" to workout.exerciseId.toInt(),
                            "exerciseName" to workout.exerciseName,
                            "count" to workout.count.toInt(),
                            "targetCount" to workout.targetCount.toInt(),
                            "date" to workout.date,
                            "duration" to workout.duration,
                            "caloriesBurned" to workout.caloriesBurned,
                            "isCompleted" to workout.isCompleted
                        )
                        batch.set(
                            db.collection("users").document(user.uid).collection("workouts").document(workout.id),
                            workoutMap
                        )
                    }
                    batch.commit().addOnSuccessListener {
                        // Migrate personal records
                        historyList.groupBy { it.exerciseId }.forEach { (exerciseId, workouts) ->
                            val maxCount = workouts.maxByOrNull { it.count }
                            if (maxCount != null) {
                                val totalWorkouts = workouts.size
                                val averageCount = workouts.map { it.count.toInt() }.average().toFloat()
                                val newRecordMap = mapOf(
                                    "exerciseId" to exerciseId.toInt(),
                                    "exerciseName" to maxCount.exerciseName,
                                    "maxCount" to maxCount.count.toInt(),
                                    "bestDate" to maxCount.date,
                                    "totalWorkouts" to totalWorkouts,
                                    "averageCount" to averageCount
                                )
                                db.collection("users").document(user.uid).collection("personalRecords")
                                    .document(exerciseId.toString())
                                    .set(newRecordMap)
                            }
                        }
                        // Không xóa local data để đảm bảo tương thích
                    }.addOnFailureListener { e ->
                        android.widget.Toast.makeText(this, "Lỗi migrate dữ liệu: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    android.widget.Toast.makeText(this, "Lỗi migrate dữ liệu sync: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}