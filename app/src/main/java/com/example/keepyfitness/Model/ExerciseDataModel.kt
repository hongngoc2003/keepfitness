package com.example.keepyfitness.Model

import java.io.Serial
import java.io.Serializable

class ExerciseDataModel(
    var title: String,
    var image: Int,
    var id: Int,
    var color: Int
): Serializable