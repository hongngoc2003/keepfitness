package com.example.keepyfitness

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.keepyfitness.Model.WorkoutHistory
import com.google.android.material.button.MaterialButton
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

/**
 * WorkoutHistoryActivity - Hiển thị lịch sử tập luyện
 * Sử dụng SharedPreferences để lưu trữ local
 */
class WorkoutHistoryActivity : AppCompatActivity() {

    private lateinit var historyListView: ListView
    private lateinit var btnClearHistory: MaterialButton
    private lateinit var emptyHistoryView: View

    private lateinit var prefs: SharedPreferences
    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            setContentView(R.layout.activity_workout_history)

            // Initialize SharedPreferences
            prefs = getSharedPreferences("workout_history", MODE_PRIVATE)

            initViews()
            setupClearHistoryButton()
            loadHistoryData()
        } catch (e: Exception) {
            Log.e("WorkoutHistory", "Error in onCreate: ${e.message}", e)
            android.widget.Toast.makeText(this, "Lỗi khởi tạo: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun initViews() {
        try {
            historyListView = findViewById(R.id.historyListView)
            btnClearHistory = findViewById(R.id.btnClearHistory)
            emptyHistoryView = findViewById(R.id.emptyHistoryView)
        } catch (e: Exception) {
            Log.e("WorkoutHistory", "Error finding views: ${e.message}", e)
            throw e
        }
    }

    private fun setupClearHistoryButton() {
        btnClearHistory.setOnClickListener {
            showClearHistoryConfirmationDialog()
        }
    }

    private fun showClearHistoryConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Xóa Lịch Sử Tập Luyện")
            .setMessage("Bạn có chắc muốn xóa toàn bộ lịch sử tập luyện? Hành động này không thể hoàn tác.")
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton("Xóa Tất Cả") { _, _ ->
                clearAllHistory()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun clearAllHistory() {
        try {
            // Xóa workout history
            prefs.edit().remove("history_list").apply()

            // Refresh UI
            loadHistoryData()

            AlertDialog.Builder(this)
                .setTitle("Đã Xóa")
                .setMessage("Toàn bộ lịch sử tập luyện đã được xóa thành công.")
                .setIcon(android.R.drawable.ic_dialog_info)
                .setPositiveButton("OK", null)
                .show()

            Log.i("WorkoutHistory", "All workout history cleared")
        } catch (e: Exception) {
            Log.e("WorkoutHistory", "Error clearing history: ${e.message}", e)
            AlertDialog.Builder(this)
                .setTitle("Lỗi")
                .setMessage("Không thể xóa lịch sử: ${e.message}")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    /**
     * Load workout history from SharedPreferences
     * Use robust parsing: parse JSON as list of maps and convert numeric values safely.
     */
    private fun loadHistoryData() {
        Log.d("WorkoutHistory", "loadHistoryData() called from SharedPreferences")

        try {
            val historyJson = prefs.getString("history_list", null)

            if (historyJson == null) {
                Log.i("WorkoutHistory", "No workout history found in SharedPreferences")
                showEmptyState()
                return
            }

            // Parse into List<Map<String, Any>> to avoid Gson numeric type mismatches with Short
            val type = object : TypeToken<List<Map<String, Any>>>() {}.type
            val rawList: List<Map<String, Any>> = gson.fromJson(historyJson, type) ?: emptyList()

            val historyList = rawList.mapNotNull { map ->
                try {
                    val id = map["id"] as? String ?: return@mapNotNull null
                    val exerciseId = (map["exerciseId"] as? Number)?.toInt()?.toShort() ?: ((map["exerciseId"] as? String)?.toIntOrNull()?.toShort() ?: 0)
                    val exerciseName = map["exerciseName"] as? String ?: ""
                    val count = (map["count"] as? Number)?.toInt()?.toShort() ?: ((map["count"] as? String)?.toIntOrNull()?.toShort() ?: 0)
                    val targetCount = (map["targetCount"] as? Number)?.toInt()?.toShort() ?: ((map["targetCount"] as? String)?.toIntOrNull()?.toShort() ?: 0)
                    val date = (map["date"] as? Number)?.toLong() ?: ((map["date"] as? String)?.toLongOrNull() ?: System.currentTimeMillis())
                    val duration = (map["duration"] as? Number)?.toInt() ?: ((map["duration"] as? String)?.toIntOrNull() ?: 0)
                    val caloriesBurned = (map["caloriesBurned"] as? Number)?.toFloat() ?: ((map["caloriesBurned"] as? String)?.toFloatOrNull() ?: 0f)
                    val isCompleted = (map["isCompleted"] as? Boolean) ?: ((map["isCompleted"] as? String)?.toBoolean() ?: false)

                    WorkoutHistory(
                        id = id,
                        exerciseId = exerciseId,
                        exerciseName = exerciseName,
                        count = count,
                        targetCount = targetCount,
                        date = date,
                        duration = duration,
                        caloriesBurned = caloriesBurned,
                        isCompleted = isCompleted
                    )
                } catch (e: Exception) {
                    Log.w("WorkoutHistory", "Skipping malformed record: ${e.message}")
                    null
                }
            }

            // Sort by date descending (newest first)
            val sortedList = historyList.sortedByDescending { it.date }

            Log.d("WorkoutHistory", "Loaded ${sortedList.size} workout records from SharedPreferences")

            if (sortedList.isEmpty()) {
                Log.i("WorkoutHistory", "Workout history list is empty")
                showEmptyState()
                return
            }

            // Show list, hide empty view
            emptyHistoryView.visibility = View.GONE
            historyListView.visibility = View.VISIBLE

            val adapter = WorkoutHistoryAdapter(this, sortedList)
            historyListView.adapter = adapter
            Log.d("WorkoutHistory", "Adapter set successfully with ${sortedList.size} items")

        } catch (e: Exception) {
            Log.e("WorkoutHistory", "Error loading history: ${e.message}", e)
            android.widget.Toast.makeText(this, "Lỗi tải lịch sử: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            showEmptyState()
        }
    }

    private fun showEmptyState() {
        historyListView.adapter = WorkoutHistoryAdapter(this, emptyList())
        emptyHistoryView.visibility = View.VISIBLE
        historyListView.visibility = View.GONE
    }

    // Adapter for workout history
    class WorkoutHistoryAdapter(private val context: Context, private val historyList: List<WorkoutHistory>) : BaseAdapter() {
        override fun getCount(): Int = historyList.size
        override fun getItem(position: Int): Any = historyList[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            try {
                val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.workout_history_item, parent, false)

                if (position >= historyList.size) {
                    Log.e("WorkoutHistoryAdapter", "Position $position out of bounds, list size: ${historyList.size}")
                    return view
                }

                val workout = historyList[position]

                val exerciseIcon = view.findViewById<ImageView>(R.id.exerciseIcon) ?: return view
                val exerciseName = view.findViewById<TextView>(R.id.exerciseName) ?: return view
                val workoutDetails = view.findViewById<TextView>(R.id.workoutDetails) ?: return view
                val workoutDate = view.findViewById<TextView>(R.id.workoutDate) ?: return view
                val completionStatus = view.findViewById<TextView>(R.id.completionStatus) ?: return view
                val caloriesBurned = view.findViewById<TextView>(R.id.caloriesBurned) ?: return view

                exerciseName.text = workout.exerciseName
                workoutDetails.text = "${workout.count}/${workout.targetCount} reps"

                val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                workoutDate.text = dateFormat.format(Date(workout.date))

                val completionPercentageDouble = if (workout.targetCount > 0) {
                    val p = (workout.count.toDouble() / workout.targetCount.toDouble()) * 100.0
                    p.coerceIn(0.0, 100.0)
                } else {
                    0.0
                }
                completionStatus.text = String.format(Locale.getDefault(), "%.0f%%", completionPercentageDouble)
                val completionPercentageInt = completionPercentageDouble.toInt()
                when {
                    completionPercentageInt >= 100 -> completionStatus.setTextColor(Color.parseColor("#4CAF50"))
                    completionPercentageInt >= 75 -> completionStatus.setTextColor(Color.parseColor("#FF9800"))
                    else -> completionStatus.setTextColor(Color.parseColor("#F44336"))
                }
                caloriesBurned.text = "Đã đốt ${workout.caloriesBurned.toInt()} calo"

                when (workout.exerciseId.toInt()) {
                    1 -> exerciseIcon.setImageResource(R.drawable.pushup)
                    2 -> exerciseIcon.setImageResource(R.drawable.squat)
                    3 -> exerciseIcon.setImageResource(R.drawable.jumping)
                    4 -> exerciseIcon.setImageResource(R.drawable.plank)
                    else -> exerciseIcon.setImageResource(R.drawable.ic_launcher_foreground)
                }

                return view
            } catch (e: Exception) {
                Log.e("WorkoutHistoryAdapter", "Error in getView at position $position: ${e.message}", e)
                return convertView ?: View(context)
            }
        }
    }
}
