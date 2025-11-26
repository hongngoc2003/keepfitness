package com.example.keepyfitness

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.DocumentSnapshot

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        // Initialize FirebaseAuth and Firestore
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val emailEditText = findViewById<EditText>(R.id.emailEditText)
        val passwordEditText = findViewById<EditText>(R.id.passwordEditText)
        val confirmPasswordEditText = findViewById<EditText>(R.id.confirmPasswordEditText)
        val registerButton = findViewById<Button>(R.id.registerButton)
        val loginTextView = findViewById<TextView>(R.id.loginTextView)

        registerButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()
            val confirmPassword = confirmPasswordEditText.text.toString().trim()

            if (email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "Hãy điền đầy đủ thông tin.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                Toast.makeText(this, "Mật khẩu không khớp.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Create Firebase account and send verification email
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val user = auth.currentUser
                        user?.sendEmailVerification()?.addOnCompleteListener { verifyTask ->
                            if (verifyTask.isSuccessful) {
                                Toast.makeText(this, "Verification email sent. Please check your inbox.", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(this, "Failed to send verification email.", Toast.LENGTH_LONG).show()
                            }
                        }
                        // Tạo hoặc cập nhật dữ liệu user trong Firestore
                        if (user != null) {
                            createUserDatabase(user.uid, email)
                        }
                        val intent = Intent(this, LoginActivity::class.java)
                        startActivity(intent)
                        finish()
                    } else {
                        Toast.makeText(this, "Lỗi: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }

        // If already have an account → go back to LoginActivity
        loginTextView.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun createUserDatabase(uid: String, email: String) {
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