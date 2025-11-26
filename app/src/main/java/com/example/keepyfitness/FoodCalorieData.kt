package com.example.keepyfitness

object FoodCalorieData {
    // Dữ liệu calo cho 15 loại thực phẩm từ Food.AI model (calo/100g hoặc 1 serving)
    private val calorieMap = mapOf(
        "Bread" to 265,
        "Pancake" to 227,
        "Waffle" to 291,
        "Bagel" to 257,
        "Muffin" to 377,
        "Doughnut" to 452,
        "Hamburger" to 295,
        "Pizza" to 266,
        "Sandwich" to 250,
        "Hot dog" to 290,
        "French fries" to 312,
        "Apple" to 52,
        "Orange" to 47,
        "Banana" to 89,
        "Grape" to 69
    )

    fun getCalories(foodName: String): Int {
        return calorieMap[foodName] ?: 200 // mặc định 200 nếu không tìm thấy
    }

    fun getNutritionalInfo(foodName: String): String {
        val calories = getCalories(foodName)

        return """
            🍽️ Món ăn: $foodName
            🔥 Calo: ~$calories kcal/phần
            
            💡 Gợi ý: ${getAdvice(calories)}
        """.trimIndent()
    }

    private fun getAdvice(calories: Int): String {
        return when {
            calories < 100 -> "Món ăn rất nhẹ, giàu vitamin và chất xơ. Tốt cho sức khỏe!"
            calories < 250 -> "Lượng calo vừa phải, tốt cho bữa ăn cân đối."
            calories < 400 -> "Lượng calo cao, nên kết hợp với rau xanh và vận động."
            else -> "Món ăn nhiều calo, nên ăn vừa phải và tăng cường tập luyện."
        }
    }
}
