package com.example.keepyfitness

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import android.widget.EditText
import com.google.android.material.button.MaterialButton
import android.widget.TextView
import com.google.firebase.firestore.DocumentSnapshot

class LoginActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Initialize FirebaseAuth and Firestore
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val emailEditText = findViewById<EditText>(R.id.emailEditText)
        val passwordEditText = findViewById<EditText>(R.id.passwordEditText)
        val loginButton = findViewById<MaterialButton>(R.id.loginButton)
        val registerTextView = findViewById<TextView>(R.id.registerTextView)
        val forgotPasswordTextView = findViewById<TextView>(R.id.forgotPasswordTextView)
        forgotPasswordTextView.paint.isUnderlineText = true

        loginButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Hãy điền đầy đủ thông tin yêu cầu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Disable button during authentication
            loginButton.isEnabled = false

            // Authenticate user
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    loginButton.isEnabled = true // Re-enable button

                    if (task.isSuccessful) {
                        val user = auth.currentUser
                        if (user != null && user.isEmailVerified) {
                            // Tạo hoặc cập nhật dữ liệu user trong Firestore
                            checkAndUpdateUserDatabase(user.uid, email)
                            Toast.makeText(this, "Đăng nhập thành công.", Toast.LENGTH_SHORT).show()

                            // Clear intent flags to avoid navigation issues
                            val intent = Intent(this, HomeScreen::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            finish()
                        } else {
                            Toast.makeText(this, "Hãy xác minh email trước khi đăng nhập.", Toast.LENGTH_LONG).show()
                            auth.signOut()
                        }
                    } else {
                        Toast.makeText(this, "Lỗi: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }

        registerTextView.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        forgotPasswordTextView.setOnClickListener {
            startActivity(Intent(this, ResetPasswordActivity::class.java))
        }
    }

    private fun checkAndUpdateUserDatabase(uid: String, email: String) {
        val userRef = db.collection("users").document(uid)
        userRef.get().addOnSuccessListener { document: DocumentSnapshot ->
            if (!document.exists()) {
                val userData = hashMapOf(
                    "email" to email,
                    "role" to "user"
                )
                userRef.set(userData, SetOptions.merge()).addOnSuccessListener {
                    Toast.makeText(this, "Đã tạo CSDL user", Toast.LENGTH_SHORT).show()
                }.addOnFailureListener { e ->
                    Toast.makeText(this, "Lỗi tạo CSDL: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Lỗi kiểm tra CSDL: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}