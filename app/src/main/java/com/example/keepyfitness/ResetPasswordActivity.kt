package com.example.keepyfitness

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import android.widget.EditText
import com.google.android.material.button.MaterialButton
import android.widget.TextView

class ResetPasswordActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reset_password)

        auth = FirebaseAuth.getInstance()
        val emailEditText = findViewById<EditText>(R.id.emailEditText)
        val resetPasswordButton = findViewById<MaterialButton>(R.id.resetPasswordButton)
        val backToLoginTextView = findViewById<TextView>(R.id.backToLoginTextView)

        resetPasswordButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            if (email.isEmpty()) {
                Toast.makeText(this, "Please enter your email", Toast.LENGTH_SHORT).show()
            } else {
                auth.sendPasswordResetEmail(email)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Toast.makeText(this, "Password reset email sent. Please check your inbox.", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this, "Error: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
            }
        }

        backToLoginTextView.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}
