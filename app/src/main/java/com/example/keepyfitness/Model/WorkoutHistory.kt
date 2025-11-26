package com.example.keepyfitness.Model

import com.google.firebase.firestore.PropertyName
import java.io.Serializable

data class WorkoutHistory(
    @PropertyName("id") val id: String = System.currentTimeMillis().toString(),
    @PropertyName("exerciseId") val exerciseId: Short = 0,             // Int → Short
    @PropertyName("exerciseName") val exerciseName: String = "",
    @PropertyName("count") val count: Short = 0,                  // Int → Short
    @PropertyName("targetCount") val targetCount: Short = 0,            // Int → Short
    @PropertyName("date") val date: Long = System.currentTimeMillis(), // Giữ Long vì timestamp
    @PropertyName("duration") val duration: Int = 0,                 // Long → Int (nếu chỉ tính giây)
    @PropertyName("caloriesBurned") val caloriesBurned: Float = 0f,        // Double → Float
    @PropertyName("isCompleted") val isCompleted: Boolean = false
) : Serializable


data class PersonalRecord(
    @PropertyName("exerciseId") val exerciseId: Short = 0,             // Int → Short
    @PropertyName("exerciseName") val exerciseName: String = "",
    @PropertyName("maxCount") val maxCount: Short = 0,               // Int → Short
    @PropertyName("bestDate") val bestDate: Long = 0L,
    @PropertyName("totalWorkouts") val totalWorkouts: Int = 0,            // Giữ Int vì số buổi tập có thể lớn
    @PropertyName("averageCount") val averageCount: Float = 0f           // Double → Float
) : Serializable