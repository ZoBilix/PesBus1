package com.example.myapplication

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import kotlinx.coroutines.runBlocking

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        runBlocking {
            try {
                val theme = SettingsManager.getTheme(this@MyApplication)
                // ✅ ИСПРАВЛЕНО: только theme, без this@MyApplication!
                SettingsManager.applyTheme(theme)
            } catch (e: Exception) {
                AppCompatDelegate.setDefaultNightMode(
                    AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                )
            }
        }
    }
}