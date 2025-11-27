package com.example.keepyfitness.Model

import java.io.Serializable

/**
 * WorkoutHistory data model with memory optimization
 * Uses Short (2 bytes) instead of Int (4 bytes) for exercise counts
 *
 * Memory savings per record:
 * - exerciseId: Short (2 bytes) vs Int (4 bytes) = 2 bytes saved
 * - count: Short (2 bytes) vs Int (4 bytes) = 2 bytes saved
 * - targetCount: Short (2 bytes) vs Int (4 bytes) = 2 bytes saved
 * Total: 6 bytes saved per workout record (50% reduction for these fields)
 *
 * For 100 workout records: saves 600 bytes
 * For 1000 workout records: saves 6 KB
 */


/**
 * PersonalRecord data model with memory optimization
 * Uses Short for exercise counts to save memory
 *
 * Memory savings per record:
 * - exerciseId: Short (2 bytes) vs Int (4 bytes) = 2 bytes saved
 * - maxCount: Short (2 bytes) vs Int (4 bytes) = 2 bytes saved
 * Total: 4 bytes saved per personal record
 */
data class PersonalRecord(
    val exerciseId: Short = 0,
    val exerciseName: String = "",
    val maxCount: Short = 0,
    val bestDate: Long = 0L,
    val totalWorkouts: Int = 0,
    val averageCount: Float = 0f
) : Serializable

data class WorkoutHistory(
    val id: String = System.currentTimeMillis().toString(),
    val exerciseId: Short = 0,
    val exerciseName: String = "",
    val count: Short = 0,
    val targetCount: Short = 0,
    val date: Long = System.currentTimeMillis(),
    val duration: Int = 0,
    val caloriesBurned: Float = 0f,
    val isCompleted: Boolean = false
) : Serializable

