package com.bioasistencia.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.bioasistencia.databinding.ActivitySplashBinding
import com.bioasistencia.ui.login.LoginActivity
import com.bioasistencia.utils.SessionManager

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val session = SessionManager(this)

        Handler(Looper.getMainLooper()).postDelayed({
            val dest = if (session.isLoggedIn()) MainActivity::class.java
                       else LoginActivity::class.java
            startActivity(Intent(this, dest))
            finish()
        }, 1800)
    }
}
