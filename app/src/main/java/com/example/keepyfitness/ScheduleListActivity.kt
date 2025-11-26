package com.example.keepyfitness

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.example.keepyfitness.Model.Schedule
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import java.util.Calendar

class ScheduleListActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_schedule_list)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val btnAddSchedule = findViewById<ImageView>(R.id.btnAddSchedule)
        btnAddSchedule.setOnClickListener {
            val intent = Intent(this, AddScheduleActivity::class.java)
            startActivity(intent)
        }

        val btnCreateFirstSchedule = findViewById<MaterialButton>(R.id.btnCreateFirstSchedule)
        btnCreateFirstSchedule.setOnClickListener {
            val intent = Intent(this, AddScheduleActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        loadScheduleData()
    }

    private fun loadScheduleData() {
        val scheduleListContainer = findViewById<LinearLayout>(R.id.scheduleListContainer)
        val emptyStateLayout = findViewById<View>(R.id.emptyStateLayout)
        val totalSchedulesCount = findViewById<TextView>(R.id.totalSchedulesCount)
        val todaySchedulesCount = findViewById<TextView>(R.id.todaySchedulesCount)

        scheduleListContainer.removeAllViews()

        val user = auth.currentUser
        if (user != null) {
            db.collection("users").document(user.uid).collection("schedules")
                .get()
                .addOnSuccessListener { documents ->
                    val scheduleList = mutableListOf<Schedule>()
                    for (document in documents) {
                        val schedule = document.toObject(Schedule::class.java)
                        scheduleList.add(schedule)
                    }

                    totalSchedulesCount.text = scheduleList.size.toString()

                    val calendar = Calendar.getInstance()
                    val dayOfWeek = when (calendar.get(Calendar.DAY_OF_WEEK)) {
                        Calendar.MONDAY -> "Thứ Hai"
                        Calendar.TUESDAY -> "Thứ Ba"
                        Calendar.WEDNESDAY -> "Thứ Tư"
                        Calendar.THURSDAY -> "Thứ Năm"
                        Calendar.FRIDAY -> "Thứ Sáu"
                        Calendar.SATURDAY -> "Thứ Bảy"
                        Calendar.SUNDAY -> "Chủ Nhật"
                        else -> ""
                    }
                    todaySchedulesCount.text = scheduleList.count { it.days.contains(dayOfWeek) }.toString()

                    if (scheduleList.isEmpty()) {
                        scheduleListContainer.visibility = View.GONE
                        emptyStateLayout.visibility = View.VISIBLE
                    } else {
                        scheduleListContainer.visibility = View.VISIBLE
                        emptyStateLayout.visibility = View.GONE

                        for ((index, schedule) in scheduleList.withIndex()) {
                            val scheduleCard = createScheduleCard(schedule, documents.toList()[index].id, index)
                            scheduleListContainer.addView(scheduleCard)
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("ScheduleList", "Error loading schedules: ${e.message}")
                    Toast.makeText(this, "Lỗi tải lịch tập: ${e.message}", Toast.LENGTH_LONG).show()
                }
        } else {
            Toast.makeText(this, "Vui lòng đăng nhập.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createScheduleCard(schedule: Schedule, scheduleId: String, index: Int): CardView {
        val cardView = CardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 16)
            }
            radius = 16f
            cardElevation = 2f
            setCardBackgroundColor(Color.WHITE)
        }

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
        }

        // Header với icon và tên bài tập
        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val iconCard = CardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(48, 48)
            radius = 24f
            cardElevation = 0f
            setCardBackgroundColor(getColorForIndex(index))
        }

        val icon = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(24, 24).apply {
                gravity = Gravity.CENTER
            }
            setImageResource(android.R.drawable.ic_menu_compass)
            setColorFilter(Color.WHITE)
        }
        val iconLayout = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            gravity = Gravity.CENTER
            addView(icon)
        }
        iconCard.addView(iconLayout)

        val exerciseText = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                setMargins(16, 0, 0, 0)
            }
            text = schedule.exercise
            textSize = 18f
            setTextColor(Color.parseColor("#2E7D32"))
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        headerLayout.addView(iconCard)
        headerLayout.addView(exerciseText)
        mainLayout.addView(headerLayout)

        // Divider
        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                2
            ).apply {
                setMargins(0, 16, 0, 16)
            }
            setBackgroundColor(Color.parseColor("#F0F0F0"))
        }
        mainLayout.addView(divider)

        // Details Grid
        val detailsGrid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Time Info
        val timeRow = createTimeRow(schedule.time)
        detailsGrid.addView(timeRow)

        // Days Info
        val daysRow = createDaysRow(schedule.days)
        detailsGrid.addView(daysRow)

        // Quantity Info
        val quantityRow = createInfoRow(
            "💪 Số lượng",
            "${schedule.quantity} lần",
            Color.parseColor("#4CAF50")
        )
        detailsGrid.addView(quantityRow)

        mainLayout.addView(detailsGrid)

        cardView.addView(mainLayout)

        // Click listeners
        cardView.setOnClickListener {
            val gson = Gson()
            val scheduleJson = gson.toJson(schedule)
            val intent = Intent(this, AddScheduleActivity::class.java)
            intent.putExtra("edit_schedule_id", scheduleId)
            intent.putExtra("edit_schedule_data", scheduleJson)
            startActivity(intent)
        }

        cardView.setOnLongClickListener {
            AlertDialog.Builder(this)
                .setTitle("Xóa lịch trình")
                .setMessage("Bạn có chắc muốn xóa lịch trình này?")
                .setPositiveButton("Xóa") { _, _ ->
                    val user = auth.currentUser
                    if (user != null) {
                        db.collection("users").document(user.uid).collection("schedules")
                            .document(scheduleId)
                            .delete()
                            .addOnSuccessListener {
                                Toast.makeText(this, "Lịch trình đã được xóa!", Toast.LENGTH_SHORT).show()
                                loadScheduleData()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Lỗi xóa lịch trình: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                    }
                }
                .setNegativeButton("Hủy", null)
                .show()
            true
        }

        return cardView
    }

    private fun createTimeRow(time: String): LinearLayout {
        return LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 12)
            }
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL

            val labelText = TextView(this@ScheduleListActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                text = "🕒 "
                textSize = 16f
            }

            val timeCard = CardView(this@ScheduleListActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(8, 0, 0, 0)
                }
                radius = 20f
                cardElevation = 0f
                setCardBackgroundColor(Color.parseColor("#FFF3E0"))
            }

            val timeText = TextView(this@ScheduleListActivity).apply {
                setPadding(16, 8, 16, 8)
                text = time
                textSize = 15f
                setTextColor(Color.parseColor("#FF9800"))
                setTypeface(null, android.graphics.Typeface.BOLD)
            }

            timeCard.addView(timeText)
            addView(labelText)
            addView(timeCard)
        }
    }

    private fun createDaysRow(days: List<String>): LinearLayout {
        return LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 12)
            }
            orientation = LinearLayout.VERTICAL

            // Label
            val labelText = TextView(this@ScheduleListActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, 8)
                }
                text = "📅 Ngày tập"
                textSize = 14f
                setTextColor(Color.parseColor("#666666"))
            }
            addView(labelText)

            // Container for day chips
            val chipsContainer = LinearLayout(this@ScheduleListActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.HORIZONTAL
            }

            // Create chips for each day
            val dayAbbreviations = mapOf(
                "Thứ Hai" to "T2",
                "Thứ Ba" to "T3",
                "Thứ Tư" to "T4",
                "Thứ Năm" to "T5",
                "Thứ Sáu" to "T6",
                "Thứ Bảy" to "T7",
                "Chủ Nhật" to "CN"
            )

            val chipColors = listOf(
                "#E3F2FD" to "#2196F3",
                "#F3E5F5" to "#9C27B0",
                "#E8F5E9" to "#4CAF50",
                "#FFF3E0" to "#FF9800",
                "#FCE4EC" to "#E91E63",
                "#E0F7FA" to "#00BCD4",
                "#FFF9C4" to "#FBC02D"
            )

            days.forEachIndexed { index, day ->
                val colorPair = chipColors[index % chipColors.size]
                val dayChip = createDayChip(
                    dayAbbreviations[day] ?: day,
                    colorPair.first,
                    colorPair.second
                )
                chipsContainer.addView(dayChip)
            }

            addView(chipsContainer)
        }
    }

    private fun createDayChip(dayText: String, bgColor: String, textColor: String): CardView {
        return CardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 8, 0)
            }
            radius = 16f
            cardElevation = 0f
            setCardBackgroundColor(Color.parseColor(bgColor))

            val chipText = TextView(this@ScheduleListActivity).apply {
                setPadding(12, 6, 12, 6)
                text = dayText
                textSize = 13f
                setTextColor(Color.parseColor(textColor))
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            addView(chipText)
        }
    }

    private fun createInfoRow(label: String, value: String, accentColor: Int): LinearLayout {
        return LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 12)
            }
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL

            val labelText = TextView(this@ScheduleListActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
                text = label
                textSize = 14f
                setTextColor(Color.parseColor("#666666"))
            }

            val valueCard = CardView(this@ScheduleListActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                radius = 12f
                cardElevation = 0f
                setCardBackgroundColor(adjustAlpha(accentColor, 0.1f))
            }

            val valueText = TextView(this@ScheduleListActivity).apply {
                setPadding(12, 6, 12, 6)
                text = value
                textSize = 14f
                setTextColor(accentColor)
                setTypeface(null, android.graphics.Typeface.BOLD)
            }

            valueCard.addView(valueText)
            addView(labelText)
            addView(valueCard)
        }
    }

    private fun getColorForIndex(index: Int): Int {
        val colors = listOf(
            "#4CAF50", "#2196F3", "#FF9800", "#E91E63",
            "#9C27B0", "#00BCD4", "#FFC107", "#795548"
        )
        return Color.parseColor(colors[index % colors.size])
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = (Color.alpha(color) * factor).toInt()
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        return Color.argb(alpha, red, green, blue)
    }
}