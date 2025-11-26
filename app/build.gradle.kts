plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.keepyfitness"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.keepyfitness"
        minSdk = 26   // 👈 nên nâng lên 26 vì CameraX yêu cầu API 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64", "x86")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    aaptOptions{
        noCompress += "tflite"
    }
}
dependencies {
    val camerax_version = "1.2.3"

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")

    implementation ("com.google.firebase:firebase-auth-ktx")
    implementation ("com.google.firebase:firebase-firestore-ktx")

    // ML Kit
    implementation("com.google.mlkit:pose-detection:18.0.0-beta4")
    implementation("com.google.mlkit:pose-detection-accurate:18.0.0-beta4")

    // Image loading
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // JSON
    implementation("com.google.code.gson:gson:2.10.1")

    // Charts
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // CardView
    implementation("androidx.cardview:cardview:1.0.0")

    // Material Design
    implementation("com.google.android.material:material:1.12.0")

    // CameraX
    implementation("androidx.camera:camera-core:$camerax_version")
    implementation("androidx.camera:camera-camera2:$camerax_version")
    implementation("androidx.camera:camera-lifecycle:$camerax_version")
    implementation("androidx.camera:camera-view:$camerax_version")
    implementation("androidx.camera:camera-extensions:$camerax_version")

    // Test dependencies
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.tensorflow:tensorflow-lite:2.12.0")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.12.0")


    implementation("org.tensorflow:tensorflow-lite-support:0.4.3")
// TensorImage, TensorBuffer
    implementation("org.tensorflow:tensorflow-lite-task-vision:0.4.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")


}
