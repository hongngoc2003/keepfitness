package com.example.keepyfitness

import android.content.Context
import android.content.Intent
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
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.example.keepyfitness.Model.ExerciseDataModel
import com.example.keepyfitness.Model.Schedule
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*
import kotlin.system.measureTimeMillis

class ExerciseListActivity : AppCompatActivity() {

    private lateinit var exerciseListView: ListView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_exercise_list)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        exerciseListView = findViewById(R.id.exerciseList)

        val list = listOf(
            ExerciseDataModel("Tập Chống Đẩy", R.drawable.pushup, 1, Color.parseColor("#0041a8")),
            ExerciseDataModel("Squat", R.drawable.squat, 2, Color.parseColor("#f20226")),
            ExerciseDataModel("Dang Tay Chân Cardio", R.drawable.jumping, 3, Color.parseColor("#f7680f")),
            ExerciseDataModel("Downward Dog Yoga", R.drawable.plank, 4, Color.parseColor("#008a40")),
            ExerciseDataModel("Đứng Một Chân", R.drawable.treepose, 5, Color.parseColor("#7b1fa2")),
        )

        val adapter = ExerciseAdapter(this, list)
    }


    class ExerciseAdapter(val context: Context, val exerciseList: List<ExerciseDataModel>) : BaseAdapter() {
        private val auth = FirebaseAuth.getInstance()
        private val db = FirebaseFirestore.getInstance()

        override fun getCount(): Int {
            return exerciseList.size
        }

        override fun getItem(position: Int): Any {
            return exerciseList[position]
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = LayoutInflater.from(context).inflate(R.layout.exercise_item, parent, false)
            val titleTV = view.findViewById<TextView>(R.id.textView2)
            val exerciseImg = view.findViewById<ImageView>(R.id.imageView)
            val card = view.findViewById<CardView>(R.id.cardView)

            card.setOnClickListener {
                // Mark exercise as started (update BitSet)
                markExerciseAsStarted(exerciseList[position].id)

                // Lấy target từ schedule của ngày hôm nay từ Firestore
                getTodayTargetForExercise(exerciseList[position].title) { targetReps ->
                    val intent = Intent(context, MainActivity::class.java)
                    intent.putExtra("data", exerciseList[position])
                    intent.putExtra("target_count", targetReps)
                    context.startActivity(intent)
                }
            }

            card.setCardBackgroundColor(exerciseList[position].color)
            Glide.with(context).asGif().load(exerciseList[position].image).into(exerciseImg)
            titleTV.text = exerciseList[position].title
            return view
        }

        private fun markExerciseAsStarted(exerciseId: Int) {
            val prefs = context.getSharedPreferences("exercise_completion", Context.MODE_PRIVATE)
            val calendar = Calendar.getInstance()
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)

            prefs.edit()
                .putBoolean("exercise_${exerciseId - 1}_started_$today", true)
                .apply()
        }

        private fun getTodayTargetForExercise(exerciseName: String, callback: (Int) -> Unit) {
            val user = auth.currentUser
            if (user == null) {
                // Nếu không đăng nhập, thử đọc từ SharedPreferences (fallback)
                val target = getTodayTargetFromSharedPrefs(exerciseName)
                callback(target)
                return
            }

            // Lấy tên ngày hôm nay theo tiếng Việt
            val calendar = Calendar.getInstance()
            val today = when (calendar.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> "Thứ Hai"
                Calendar.TUESDAY -> "Thứ Ba"
                Calendar.WEDNESDAY -> "Thứ Tư"
                Calendar.THURSDAY -> "Thứ Năm"
                Calendar.FRIDAY -> "Thứ Sáu"
                Calendar.SATURDAY -> "Thứ Bảy"
                Calendar.SUNDAY -> "Chủ Nhật"
                else -> ""
            }

            // Đọc từ Firestore
            db.collection("users").document(user.uid).collection("schedules")
                .get()
                .addOnSuccessListener { documents ->
                    var targetReps = 0
                    for (document in documents) {
                        val schedule = document.toObject(Schedule::class.java)
                        if (schedule.exercise == exerciseName && schedule.days.contains(today)) {
                            targetReps = schedule.quantity
                            break
                        }
                    }

                    // Nếu không tìm thấy target từ Firestore, thử SharedPreferences
                    if (targetReps == 0) {
                        targetReps = getTodayTargetFromSharedPrefs(exerciseName)
                    }

                    callback(targetReps)
                }
                .addOnFailureListener { e ->
                    Log.e("ExerciseListActivity", "Error loading schedules: ${e.message}")
                    // Fallback to SharedPreferences
                    val target = getTodayTargetFromSharedPrefs(exerciseName)
                    callback(target)
                }
        }

        private fun getTodayTargetFromSharedPrefs(exerciseName: String): Int {
            val prefs = context.getSharedPreferences("schedules", Context.MODE_PRIVATE)
            val gson = Gson()
            val type = object : TypeToken<List<Schedule>>() {}.type
            val listJson = prefs.getString("schedule_list", null)
            val scheduleList: List<Schedule> = if (listJson != null) {
                gson.fromJson(listJson, type)
            } else {
                emptyList()
            }

            // Lấy tên ngày hôm nay theo tiếng Việt
            val calendar = Calendar.getInstance()
            val today = when (calendar.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> "Thứ Hai"
                Calendar.TUESDAY -> "Thứ Ba"
                Calendar.WEDNESDAY -> "Thứ Tư"
                Calendar.THURSDAY -> "Thứ Năm"
                Calendar.FRIDAY -> "Thứ Sáu"
                Calendar.SATURDAY -> "Thứ Bảy"
                Calendar.SUNDAY -> "Chủ Nhật"
                else -> ""
            }

            // Tìm schedule cho bài tập này trong ngày hôm nay
            val todaySchedule = scheduleList.find { schedule ->
                schedule.exercise == exerciseName && schedule.days.contains(today)
            }

            return todaySchedule?.quantity ?: 50 // Trả về 50 mặc định nếu không có target
        }
    }
}