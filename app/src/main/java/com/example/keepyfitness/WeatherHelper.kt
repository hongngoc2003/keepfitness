package com.example.keepyfitness.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class WeatherHelper(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val client = OkHttpClient()
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "WeatherHelper"
        private const val MAX_ACCURACY = 50f
        private const val MAX_NETWORK_ACCURACY = 200f
        private const val IDEAL_ACCURACY = 30f
        private const val TIMEOUT_MS = 20000L
        private const val MIN_TIME_BETWEEN_UPDATES = 1000L
    }

    @SuppressLint("MissingPermission")
    fun getWeatherSuggestion(callback: (String) -> Unit) {
        // Kiểm tra quyền
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            callback("❌ Chưa có quyền vị trí.")
            return
        }

        // Kiểm tra GPS
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)

        Log.d(TAG, "GPS enabled: $isGpsEnabled, Network enabled: $isNetworkEnabled")

        if (!isGpsEnabled && !isNetworkEnabled) {
            callback("❌ Định vị đang TẮT!\n\nBật Location trong Settings để nhận gợi ý thời tiết.")
            return
        }

        if (!isGpsEnabled) {
            Log.w(TAG, "GPS OFF - using network location")
        }

        var isCallbackCalled = false
        var bestLocation: Location? = null
        var updateCount = 0
        var hasGpsLocation = false

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            MIN_TIME_BETWEEN_UPDATES
        ).apply {
            setMinUpdateIntervalMillis(500L)
            setMaxUpdates(10)
            setWaitForAccurateLocation(true)
            setMaxUpdateDelayMillis(2000L)
        }.build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                if (isCallbackCalled) return

                val location = result.lastLocation ?: return
                updateCount++

                val locationAge = System.currentTimeMillis() - location.time
                Log.d(TAG, "Update #$updateCount - Lat: ${location.latitude}, " +
                        "Lon: ${location.longitude}, Accuracy: ${location.accuracy}m, " +
                        "Provider: ${location.provider}, Age: ${locationAge}ms")

                // Bỏ qua location quá cũ
                if (locationAge > 30000) {
                    Log.w(TAG, "Location too old (${locationAge / 1000}s), skipping...")
                    return
                }

                if (location.provider == "gps") {
                    hasGpsLocation = true
                    Log.d(TAG, "GPS location received!")
                }

                // Lưu vị trí tốt nhất
                if (bestLocation == null) {
                    bestLocation = location
                    Log.d(TAG, "First location saved: ${location.accuracy}m from ${location.provider}")
                } else {
                    if (location.provider == "gps" && bestLocation!!.provider != "gps") {
                        bestLocation = location
                        Log.d(TAG, "Switched to GPS location: ${location.accuracy}m")
                    } else if (location.provider == bestLocation!!.provider && location.accuracy < bestLocation!!.accuracy) {
                        bestLocation = location
                        Log.d(TAG, "Better accuracy: ${location.accuracy}m")
                    }
                }

                // Điều kiện chấp nhận location
                if (location.accuracy <= IDEAL_ACCURACY && location.provider == "gps") {
                    isCallbackCalled = true
                    fusedLocationClient.removeLocationUpdates(this)
                    handler.removeCallbacksAndMessages(null)

                    Log.d(TAG, "Excellent GPS accuracy: ${location.accuracy}m")
                    fetchWeather(location.latitude, location.longitude, location.accuracy, "GPS", callback)
                } else if (location.accuracy <= MAX_ACCURACY && location.provider == "gps" && updateCount >= 2) {
                    isCallbackCalled = true
                    fusedLocationClient.removeLocationUpdates(this)
                    handler.removeCallbacksAndMessages(null)

                    Log.d(TAG, "Good GPS accuracy: ${location.accuracy}m after $updateCount updates")
                    fetchWeather(location.latitude, location.longitude, location.accuracy, "GPS", callback)
                } else if (location.accuracy <= MAX_NETWORK_ACCURACY && updateCount >= 7 && !hasGpsLocation) {
                    isCallbackCalled = true
                    fusedLocationClient.removeLocationUpdates(this)
                    handler.removeCallbacksAndMessages(null)

                    Log.d(TAG, "Using network location: ${location.accuracy}m (GPS unavailable)")
                    fetchWeather(location.latitude, location.longitude, location.accuracy, "Network", callback)
                }
            }

            override fun onLocationAvailability(availability: LocationAvailability) {
                if (!availability.isLocationAvailable && !isCallbackCalled) {
                    Log.w(TAG, "GPS not available - check if GPS is enabled")
                }
            }
        }

        Log.d(TAG, "Starting location updates...")
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )

        // Timeout handler
        handler.postDelayed({
            if (!isCallbackCalled) {
                fusedLocationClient.removeLocationUpdates(locationCallback)

                if (bestLocation != null) {
                    isCallbackCalled = true
                    val locationAge = System.currentTimeMillis() - bestLocation!!.time

                    if (bestLocation!!.provider == "gps" && bestLocation!!.accuracy <= 100f) {
                        Log.w(TAG, "Timeout - using GPS location: ${bestLocation!!.accuracy}m")
                        fetchWeather(
                            bestLocation!!.latitude,
                            bestLocation!!.longitude,
                            bestLocation!!.accuracy,
                            "GPS",
                            callback
                        )
                    } else if (bestLocation!!.accuracy <= MAX_NETWORK_ACCURACY && locationAge < 30000) {
                        Log.w(TAG, "Timeout - using network location: ${bestLocation!!.accuracy}m")
                        fetchWeather(
                            bestLocation!!.latitude,
                            bestLocation!!.longitude,
                            bestLocation!!.accuracy,
                            "Network",
                            callback
                        )
                    } else {
                        showGpsInstructions(callback, bestLocation!!.accuracy, hasGpsLocation, isGpsEnabled)
                    }
                } else {
                    isCallbackCalled = true
                    showGpsInstructions(callback, null, hasGpsLocation, isGpsEnabled)
                }
            }
        }, TIMEOUT_MS)
    }

    private fun showGpsInstructions(callback: (String) -> Unit, accuracy: Float?, hasGpsLocation: Boolean, isGpsEnabled: Boolean) {
        val msg = buildString {
            append("❌ Không lấy được vị trí chính xác!\n\n")

            if (!isGpsEnabled) {
                append("🔴 GPS đang TẮT\n\n")
                append("Cách bật:\n")
                append("Settings → Location → Bật 'Use location'\n")
                append("Chọn mode 'High accuracy'\n\n")
            } else if (!hasGpsLocation) {
                append("⚠️ GPS chưa kết nối vệ tinh\n\n")
                append("Hãy thử:\n")
                append("• Ra ngoài trời hoặc gần cửa sổ\n")
                append("• Chờ 30-60 giây\n")
                append("• Tắt/bật lại GPS\n\n")
            }

            append("💡 GPS trong nhà rất yếu\n")
            append("Cần tầm nhìn trời để bắt tín hiệu")

            if (accuracy != null) {
                append("\n\n(Độ chính xác: ±${accuracy.toInt()}m)")
            }
        }
        callback(msg)
    }

    private fun fetchWeather(lat: Double, lon: Double, accuracy: Float, provider: String, callback: (String) -> Unit) {
        // Open-Meteo API - FREE, no API key needed
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true&timezone=auto"

        Log.d(TAG, "Fetching weather from Open-Meteo - Lat: $lat, Lon: $lon")

        val request = Request.Builder().url(url).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Weather API failed", e)
                handler.post {
                    callback("❌ Không lấy được dữ liệu thời tiết: ${e.message}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val data = response.body?.string()
                handler.post {
                    if (data != null) {
                        try {
                            val json = JSONObject(data)
                            val currentWeather = json.getJSONObject("current_weather")

                            val temp = currentWeather.getDouble("temperature") // °C
                            val weatherCode = currentWeather.getInt("weathercode")
                            val windSpeed = currentWeather.getDouble("windspeed") // km/h

                            Log.d(TAG, "Weather fetched - Temp: $temp°C, Code: $weatherCode, Wind: $windSpeed km/h")

                            // Map weather code to condition
                            val weatherCondition = getWeatherCondition(weatherCode)

                            // Reverse geocoding để lấy tên thành phố (optional)
                            val cityName = getCityName(lat, lon)

                            // Thông tin độ chính xác
                            val accuracyInfo = when {
                                provider == "GPS" && accuracy <= 30f -> "📍 GPS chính xác cao"
                                provider == "GPS" && accuracy <= 50f -> "📍 GPS vị trí tốt"
                                provider == "GPS" && accuracy <= 100f -> "📍 GPS (±${accuracy.toInt()}m)"
                                else -> "📍 Vị trí từ ${provider} (±${accuracy.toInt()}m)"
                            }


                            // Tạo suggestion
                            val suggestion = when {
                                weatherCode in listOf(51, 53, 55, 61, 63, 65, 80, 81, 82, 95, 96, 99) ->
                                    "🌧️ $cityName - Trời mưa (${temp.toInt()}°C)\n" +
                                            "$accuracyInfo\n\n" +
                                            "→ Tập trong nhà: Chống đẩy, Squat, Downward Dog Yoga"

                                temp < 15 ->
                                    "🥶 $cityName - Trời lạnh (${temp.toInt()}°C)\n" +
                                            "$accuracyInfo\n\n" +
                                            "→ Khởi động kỹ, tập trong nhà: Chống đẩy, Squat, Đứng một chân"

                                temp in 15.0..25.0 && weatherCode == 0 ->
                                    "☀️ $cityName - Thời tiết đẹp (${temp.toInt()}°C)\n" +
                                            "$accuracyInfo\n\n" +
                                            "→ Ra ngoài tập: Dang tay chân cardio, Đứng một chân"

                                temp in 15.0..25.0 && weatherCode in listOf(1, 2, 3) ->
                                    "⛅ $cityName - Trời râm mát (${temp.toInt()}°C)\n" +
                                            "$accuracyInfo\n\n" +
                                            "→ Tập ngoài trời: Dang tay chân cardio, Downward Dog Yoga"

                                temp > 30 ->
                                    "🥵 $cityName - Trời nóng (${temp.toInt()}°C)\n" +
                                            "$accuracyInfo\n\n" +
                                            "→ Tập trong nhà, uống đủ nước: Chống đẩy, Squat, Downward Dog Yoga"

                                windSpeed > 30 ->
                                    "💨 $cityName - Gió mạnh (${temp.toInt()}°C, ${windSpeed.toInt()} km/h)\n" +
                                            "$accuracyInfo\n\n" +
                                            "→ Tập trong nhà an toàn: Chống đẩy, Squat, Đứng một chân"

                                else ->
                                    "⚡ $cityName - Thời tiết thất thường (${temp.toInt()}°C)\n" +
                                            "$accuracyInfo\n\n" +
                                            "→ Ưu tiên tập trong nhà: Chống đẩy, Downward Dog Yoga, Đứng một chân"
                            }


                            callback(suggestion)

                        } catch (e: Exception) {
                            Log.e(TAG, "JSON parsing error", e)
                            callback("❌ Lỗi phân tích dữ liệu: ${e.message}")
                        }
                    } else {
                        callback("❌ Không nhận được dữ liệu từ API.")
                    }
                }
            }
        })
    }

    private fun getWeatherCondition(code: Int): String {
        return when (code) {
            0 -> "Trời quang"
            in 1..3 -> "Có mây"
            in 4..10 -> "Khói hoặc bụi"
            in 11..20 -> "Gió cuốn bụi hoặc cát"
            in 21..29 -> "Hiện tượng bụi hoặc cát"
            in 30..35 -> "Sương mù nhẹ"
            in 36..39 -> "Sương mù dày"
            40 -> "Sương mù lắng đọng"
            in 41..44 -> "Sương mù hoặc mây thấp"
            45 -> "Sương mù"
            48 -> "Sương mù băng giá"
            in 51..55 -> "Mưa phùn"
            in 56..57 -> "Mưa phùn đóng băng"
            in 61..65 -> "Mưa"
            in 66..67 -> "Mưa đóng băng"
            in 71..75 -> "Tuyết"
            77 -> "Hạt tuyết"
            in 80..82 -> "Mưa rào"
            in 85..86 -> "Mưa tuyết"
            in 95..96 -> "Giông bão"
            99 -> "Giông bão kèm mưa đá"
            else -> "Thời tiết khác"
        }
    }

    private fun getCityName(lat: Double, lon: Double): String {
        // Hardcode một số tọa độ Hà Nội để hiển thị tên quận
        return when {
            lat in 20.95..21.00 && lon in 105.80..105.87 -> "Hoàng Mai"
            lat in 20.96..21.02 && lon in 105.74..105.80 -> "Hà Đông"
            lat in 21.00..21.05 && lon in 105.80..105.86 -> "Đống Đa"
            lat in 21.01..21.04 && lon in 105.82..105.86 -> "Hai Bà Trưng"
            else -> "Hà Nội"
        }
    }

    fun cleanup() {
        handler.removeCallbacksAndMessages(null)
    }
}