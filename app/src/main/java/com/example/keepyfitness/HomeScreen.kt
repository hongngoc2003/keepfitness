package com.example.keepyfitness

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.keepyfitness.utils.WeatherHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

class HomeScreen : AppCompatActivity() {

    private val LOCATION_PERMISSION_REQUEST = 2001
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var weatherHelper: WeatherHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_home_screen)

        // Khởi tạo Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        weatherHelper = WeatherHelper(this)

        // Hiển thị tổng calo
        loadTotalCalories()

        // Nút quét calo
        val btnScanCalo = findViewById<LinearLayout>(R.id.btnScanCalo)
        btnScanCalo.setOnClickListener {
            startActivity(Intent(this, FruitCalo::class.java))
        }


        // Nút bắt đầu bài tập
        val btnStartWorkout = findViewById<LinearLayout>(R.id.btnStartWorkout)
        btnStartWorkout.setOnClickListener {
            startActivity(Intent(this, ExerciseListActivity::class.java))
        }

        // Nút lịch tập
        val btnScheduleWorkout = findViewById<LinearLayout>(R.id.btnScheduleWorkout)
        btnScheduleWorkout.setOnClickListener {
            startActivity(Intent(this, ScheduleListActivity::class.java))
        }

        // Nút xem lịch sử tập
        val btnViewHistory = findViewById<LinearLayout>(R.id.btnViewHistory)
        btnViewHistory.setOnClickListener {
            startActivity(Intent(this, WorkoutHistoryActivity::class.java))
        }

        // Áp padding cho hệ thống bar
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Kiểm tra quyền vị trí
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST)
        } else {
            showWeatherSuggestion()
        }

        // Yêu cầu quyền thông báo cho Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }
    }

    private fun loadTotalCalories() {
        val tvCalories = findViewById<TextView>(R.id.tvTotalCalories)
        try {
            val prefs = getSharedPreferences("health_data", MODE_PRIVATE)
            val totalCalories = prefs.getInt("total_calories_today", 0)
            val lastUpdate = prefs.getLong("last_calorie_update", 0L)

            // Reset calo nếu là ngày mới
            val currentTime = System.currentTimeMillis()
            val oneDayMillis = 24 * 60 * 60 * 1000L

            if (currentTime - lastUpdate > oneDayMillis) {
                // Đã qua 1 ngày, reset về 0
                prefs.edit().apply {
                    putInt("total_calories_today", 0)
                    apply()
                }
                tvCalories.text = "0 calo"
            } else {
                // Hiển thị tổng calo với text "calo" trên cùng dòng
                tvCalories.text = if (totalCalories >= 1000) {
                    String.format(Locale.getDefault(), "%.1fK calo", totalCalories / 1000.0)
                } else {
                    "$totalCalories calo"
                }
            }
        } catch (e: Exception) {
            tvCalories.text = "0 calo"
            Log.e("HomeScreen", "Error loading calories: ${e.message}")
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showWeatherSuggestion()
            } else {
                val tvSuggestion = findViewById<TextView>(R.id.tvWeatherSuggestion)
                tvSuggestion.text = "❌ Cần quyền vị trí để lấy gợi ý thời tiết"

                AlertDialog.Builder(this)
                    .setTitle("Quyền vị trí bị từ chối")
                    .setMessage("Không thể lấy gợi ý tập luyện theo thời tiết nếu không cấp quyền vị trí.")
                    .setPositiveButton("OK", null)
                    .setCancelable(true)
                    .show()
            }
        }
    }

    private fun showWeatherSuggestion() {
        val tvSuggestion = findViewById<TextView>(R.id.tvWeatherSuggestion)
        tvSuggestion.text = "⏳ Đang lấy vị trí và thời tiết...\n(Có thể mất 10-20 giây)"

        try {
            weatherHelper.getWeatherSuggestion { suggestion ->
                runOnUiThread {
                    tvSuggestion.text = suggestion
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            runOnUiThread {
                tvSuggestion.text = "❌ Lỗi: ${e.message}\n\nVui lòng thử lại sau."
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadTotalCalories() // Thêm dòng này để reload calo khi quay lại màn hình
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cleanup WeatherHelper
        weatherHelper.cleanup()
    }
}