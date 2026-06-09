package com.example.pesbuscom.main

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.pesbuscom.R
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.launch
import java.util.Calendar

class SettingsActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var btnAbout: MaterialButton
    private lateinit var switchDeveloperMode: MaterialSwitch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Инициализация Views
        toolbar = findViewById(R.id.toolbar)
        btnAbout = findViewById(R.id.btn_about)
        switchDeveloperMode = findViewById(R.id.switch_developer_mode)

        // Toolbar: кнопка назад
        toolbar.setNavigationOnClickListener {
            finish()
        }

        // Загрузка состояния режима разработчика
        lifecycleScope.launch {
            switchDeveloperMode.isChecked = SettingsManager.isDeveloperModeEnabled(this@SettingsActivity)
        }

        // Слушатель переключателя
        switchDeveloperMode.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                SettingsManager.saveDeveloperMode(this@SettingsActivity, isChecked)
            }
        }

        // Кнопка "О приложении"
        btnAbout.setOnClickListener {
            showAboutDialog()
        }
    }

    private fun showAboutDialog() {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)

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