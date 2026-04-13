package com.example.myapplication

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import android.widget.Toast
import android.content.Intent
import android.net.Uri

class SettingsActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var btnAbout: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Инициализация Views
        toolbar = findViewById(R.id.toolbar)
        btnAbout = findViewById(R.id.btn_about)

        // Toolbar: кнопка назад
        toolbar.setNavigationOnClickListener {
            finish()
        }

        // Кнопка "О приложении"
        btnAbout.setOnClickListener {
            showAboutDialog()
        }
    }

    private fun showAboutDialog() {
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)

        AlertDialog.Builder(this)
            .setTitle("О приложении")
            .setMessage("""
                BusMap — Приложение для отслеживания автобусных маршрутов
                
                Создано: PesCode
                Совместно с: PesApi
                Версия: 1.0.0
                
                © $currentYear Все права защищены
            """.trimIndent())
            .setIcon(R.drawable.ic_info)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .setNeutralButton("Сайт") { dialog, _ ->
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://pespes.online"))
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Не удалось открыть сайт", Toast.LENGTH_SHORT).show()
                }
            }
            .setCancelable(true)
            .show()
    }
}