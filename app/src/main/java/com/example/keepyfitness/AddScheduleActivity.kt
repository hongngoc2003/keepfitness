package com.example.keepyfitness

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.keepyfitness.Model.Schedule
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.gson.Gson
import java.util.Calendar
import java.util.BitSet

class AddScheduleActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    // Memory optimization: Use BitSet for day selection (1 bit per day vs 1 byte per boolean)
    // Saves 87.5% memory: 7 bits (1 byte) vs 7 bytes for boolean array
    private val selectedDaysBitSet = BitSet(7)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_schedule)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val btnPickTime = findViewById<Button>(R.id.btnPickTime)
        val btnSave = findViewById<Button>(R.id.btnSaveSchedule)
        val btnDelete = findViewById<Button>(R.id.btnDeleteSchedule)
        val edtTime = findViewById<EditText>(R.id.edtTime)
        val checkAll = findViewById<CheckBox>(R.id.checkAllDays)
        val checkMon = findViewById<CheckBox>(R.id.checkMon)
        val checkTue = findViewById<CheckBox>(R.id.checkTue)
        val checkWed = findViewById<CheckBox>(R.id.checkWed)
        val checkThu = findViewById<CheckBox>(R.id.checkThu)
        val checkFri = findViewById<CheckBox>(R.id.checkFri)
        val checkSat = findViewById<CheckBox>(R.id.checkSat)
        val checkSun = findViewById<CheckBox>(R.id.checkSun)
        val spinnerExercise = findViewById<Spinner>(R.id.spinnerExercise)
        val edtQuantity = findViewById<EditText>(R.id.edtQuantity)

        // Thiết lập danh sách bài tập cho Spinner
        val exerciseList = listOf("Tập Chống Đẩy", "Squat", "Dang Tay Chân Cardio", "Downward Dog Yoga", "Tree Pose")
        val exerciseAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, exerciseList)
        exerciseAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerExercise.adapter = exerciseAdapter

        // Setup checkbox listeners with BitSet optimization
        setupDayCheckboxes(checkMon, checkTue, checkWed, checkThu, checkFri, checkSat, checkSun, checkAll)

        // Nếu có dữ liệu chỉnh sửa, hiển thị lên form
        val editScheduleId = intent.getStringExtra("edit_schedule_id")
        val editDataJson = intent.getStringExtra("edit_schedule_data")
        if (editScheduleId != null && editDataJson != null) {
            val gson = Gson()
            val editSchedule = gson.fromJson(editDataJson, Schedule::class.java)
            edtTime.setText(editSchedule.time)
            edtQuantity.setText(editSchedule.quantity.toString())
            spinnerExercise.setSelection(exerciseList.indexOf(editSchedule.exercise))

            // Load days into BitSet
            loadDaysIntoBitSet(editSchedule.days, checkMon, checkTue, checkWed, checkThu, checkFri, checkSat, checkSun, checkAll)
            btnDelete.visibility = Button.VISIBLE
        }

        btnPickTime.setOnClickListener {
            val calendar = Calendar.getInstance()
            val timePicker = TimePickerDialog(this, { _: TimePicker, hour: Int, minute: Int ->
                edtTime.setText(String.format("%02d:%02d", hour, minute))
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true)
            timePicker.show()
        }

        checkAll.setOnCheckedChangeListener { _, isChecked ->
            // Use BitSet to efficiently set all days
            if (isChecked) {
                selectedDaysBitSet.set(0, 7) // Set all 7 days
            } else {
                selectedDaysBitSet.clear() // Clear all
            }
            updateCheckboxesFromBitSet(checkMon, checkTue, checkWed, checkThu, checkFri, checkSat, checkSun)
        }

        btnSave.setOnClickListener {
            val selectedExercise = spinnerExercise.selectedItem.toString()
            val time = edtTime.text.toString()
            val quantityText = edtQuantity.text.toString()
            val quantity = quantityText.toIntOrNull() ?: 0

            // Convert BitSet to day list
            val days = getDaysFromBitSet()

            if (time.isEmpty() || days.isEmpty() || quantity <= 0) {
                Toast.makeText(this, "Hãy chọn thời gian sau ít nhất 1 ngày kể từ bây giờ", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val schedule = Schedule(selectedExercise, time, days, quantity)
            val user = auth.currentUser
            if (user != null) {
                val scheduleId = if (editScheduleId != null) editScheduleId else "${selectedExercise}_${System.currentTimeMillis()}"
                db.collection("users").document(user.uid).collection("schedules")
                    .document(scheduleId)
                    .set(schedule, SetOptions.merge())
                    .addOnSuccessListener {
                        // Đặt lịch thông báo
                        scheduleNotification(time, days, scheduleId)
                        Toast.makeText(this, "Lịch tập đã được lưu!", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Lỗi lưu lịch tập: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            } else {
                Toast.makeText(this, "Vui lòng đăng nhập.", Toast.LENGTH_SHORT).show()
            }
        }

        btnDelete.setOnClickListener {
            if (editScheduleId != null) {
                val user = auth.currentUser
                if (user != null) {
                    // Hủy thông báo trước khi xóa
                    cancelNotification(editScheduleId)
                    db.collection("users").document(user.uid).collection("schedules")
                        .document(editScheduleId)
                        .delete()
                        .addOnSuccessListener {
                            Toast.makeText(this, "Lịch tập đã được xóa!", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Lỗi xóa lịch tập: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                }
            }
        }
    }

<<<<<<< HEAD
    /**
     * Setup day checkboxes with BitSet optimization
     * Memory saving: 7 bits (1 byte) vs 7 bytes = 87.5% reduction
     */
    private fun setupDayCheckboxes(
        checkMon: CheckBox, checkTue: CheckBox, checkWed: CheckBox,
        checkThu: CheckBox, checkFri: CheckBox, checkSat: CheckBox, checkSun: CheckBox,
        checkAll: CheckBox
    ) {
        val checkboxes = listOf(checkMon, checkTue, checkWed, checkThu, checkFri, checkSat, checkSun)

        checkboxes.forEachIndexed { index, checkbox ->
            checkbox.setOnCheckedChangeListener { _, isChecked ->
                selectedDaysBitSet.set(index, isChecked)
                // Update "All" checkbox
                checkAll.isChecked = selectedDaysBitSet.cardinality() == 7
            }
        }
    }

    /**
     * Load days into BitSet from schedule data
     */
    private fun loadDaysIntoBitSet(
        days: List<String>,
        checkMon: CheckBox, checkTue: CheckBox, checkWed: CheckBox,
        checkThu: CheckBox, checkFri: CheckBox, checkSat: CheckBox, checkSun: CheckBox,
        checkAll: CheckBox
    ) {
        selectedDaysBitSet.clear()

        checkMon.isChecked = days.contains("Thứ Hai").also { if (it) selectedDaysBitSet.set(0) }
        checkTue.isChecked = days.contains("Thứ Ba").also { if (it) selectedDaysBitSet.set(1) }
        checkWed.isChecked = days.contains("Thứ Tư").also { if (it) selectedDaysBitSet.set(2) }
        checkThu.isChecked = days.contains("Thứ Năm").also { if (it) selectedDaysBitSet.set(3) }
        checkFri.isChecked = days.contains("Thứ Sáu").also { if (it) selectedDaysBitSet.set(4) }
        checkSat.isChecked = days.contains("Thứ Bảy").also { if (it) selectedDaysBitSet.set(5) }
        checkSun.isChecked = days.contains("Chủ Nhật").also { if (it) selectedDaysBitSet.set(6) }

        checkAll.isChecked = selectedDaysBitSet.cardinality() == 7
    }

    /**
     * Update UI checkboxes from BitSet
     */
    private fun updateCheckboxesFromBitSet(
        checkMon: CheckBox, checkTue: CheckBox, checkWed: CheckBox,
        checkThu: CheckBox, checkFri: CheckBox, checkSat: CheckBox, checkSun: CheckBox
    ) {
        checkMon.isChecked = selectedDaysBitSet.get(0)
        checkTue.isChecked = selectedDaysBitSet.get(1)
        checkWed.isChecked = selectedDaysBitSet.get(2)
        checkThu.isChecked = selectedDaysBitSet.get(3)
        checkFri.isChecked = selectedDaysBitSet.get(4)
        checkSat.isChecked = selectedDaysBitSet.get(5)
        checkSun.isChecked = selectedDaysBitSet.get(6)
    }

    /**
     * Convert BitSet to day name list
     */
    private fun getDaysFromBitSet(): List<String> {
        val dayNames = listOf("Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy", "Chủ Nhật")
        val selectedDays = mutableListOf<String>()

        for (i in 0 until 7) {
            if (selectedDaysBitSet.get(i)) {
                selectedDays.add(dayNames[i])
            }
        }

        return selectedDays
=======
    private fun scheduleNotification(time: String, days: List<String>, scheduleId: String) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val timeParts = time.split(":")
        if (timeParts.size != 2) return

        val hour = timeParts[0].toIntOrNull() ?: return
        val minute = timeParts[1].toIntOrNull() ?: return

        // Map ngày tiếng Việt sang Calendar day
        val dayMap = mapOf(
            "Chủ Nhật" to Calendar.SUNDAY,
            "Thứ Hai" to Calendar.MONDAY,
            "Thứ Ba" to Calendar.TUESDAY,
            "Thứ Tư" to Calendar.WEDNESDAY,
            "Thứ Năm" to Calendar.THURSDAY,
            "Thứ Sáu" to Calendar.FRIDAY,
            "Thứ Bảy" to Calendar.SATURDAY
        )

        days.forEachIndexed { index, day ->
            val calendarDay = dayMap[day] ?: return@forEachIndexed
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.DAY_OF_WEEK, calendarDay)

                // Nếu thời gian đã qua trong tuần này, chuyển sang tuần sau
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.WEEK_OF_YEAR, 1)
                }
            }

            val intent = Intent(this, WorkoutNotificationReceiver::class.java)
            val requestCode = (scheduleId.hashCode() + index)
            val pendingIntent = PendingIntent.getBroadcast(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Đặt lịch lặp lại hàng tuần
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                AlarmManager.INTERVAL_DAY * 7, // Lặp lại mỗi tuần
                pendingIntent
            )
        }
    }

    private fun cancelNotification(scheduleId: String) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // Hủy tất cả các thông báo liên quan đến schedule này (tối đa 7 ngày)
        for (i in 0..6) {
            val intent = Intent(this, WorkoutNotificationReceiver::class.java)
            val requestCode = (scheduleId.hashCode() + i)
            val pendingIntent = PendingIntent.getBroadcast(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
>>>>>>> d880ad9d64a4259b3ab8218cfd5f80ee93ccb0ec
    }
}