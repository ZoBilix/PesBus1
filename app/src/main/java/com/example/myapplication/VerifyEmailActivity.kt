package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.main.LoginActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.HttpException
import java.io.IOException

class VerifyEmailActivity : AppCompatActivity() {

    private lateinit var apiService: BusApiService

    // Views
    private lateinit var toolbar: MaterialToolbar
    private lateinit var emailText: androidx.appcompat.widget.AppCompatTextView
    private lateinit var verifyButton: MaterialButton
    private lateinit var resendButton: MaterialButton
    private lateinit var timerText: androidx.appcompat.widget.AppCompatTextView
    private lateinit var errorText: androidx.appcompat.widget.AppCompatTextView
    private lateinit var loadingIndicator: android.widget.ProgressBar
    private lateinit var codeInputHidden: android.widget.EditText

    // Поля для цифр кода
    private val digitInputs = mutableListOf<TextInputEditText>()

    private var userEmail: String = ""
    private var timer: CountDownTimer? = null
    private var resendEnabled: Boolean = false

    companion object {
        private const val BASE_URL = "https://bus.api.pespes.online:8443/"
        const val EXTRA_EMAIL = "extra_email"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verify_email)

        userEmail = intent.getStringExtra(EXTRA_EMAIL) ?: ""

        // Инициализация Views
        toolbar = findViewById(R.id.toolbar)
        emailText = findViewById(R.id.email_text)
        verifyButton = findViewById(R.id.verify_button)
        resendButton = findViewById(R.id.resend_button)
        timerText = findViewById(R.id.timer_text)
        errorText = findViewById(R.id.error_text)
        loadingIndicator = findViewById(R.id.loading_indicator)
        codeInputHidden = findViewById(R.id.code_input_hidden)

        // Собираем все поля для цифр
        digitInputs.add(findViewById(R.id.digit1))
        digitInputs.add(findViewById(R.id.digit2))
        digitInputs.add(findViewById(R.id.digit3))
        digitInputs.add(findViewById(R.id.digit4))
        digitInputs.add(findViewById(R.id.digit5))
        digitInputs.add(findViewById(R.id.digit6))

        // Отображаем email
        emailText.text = userEmail

        // Кнопка назад
        toolbar.setNavigationOnClickListener { finish() }

        // Инициализация API
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        apiService = retrofit.create(BusApiService::class.java)

        // Настраиваем авто-переход между полями и авто-верификацию
        setupCodeInputs()

        // Кнопка: Подтвердить
        verifyButton.setOnClickListener {
            verifyCode()
        }

        // Кнопка: Отправить код ещё раз
        resendButton.setOnClickListener {
            if (resendEnabled) {
                resendCode()
            }
        }

        // Запускаем таймер
        startResendTimer()

        // Фокус на первое поле
        digitInputs.firstOrNull()?.requestFocus()
    }

    private fun setupCodeInputs() {
        digitInputs.forEachIndexed { index, editText ->
            editText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    // Скрываем ошибку при вводе
                    if (s?.isNotEmpty() == true) {
                        errorText.visibility = View.GONE
                    }

                    // Авто-переход к следующему полю
                    if (s?.length == 1 && index < 5) {
                        digitInputs[index + 1].requestFocus()
                    }

                    // Авто-верификация когда все 6 цифр введены
                    if (index == 5 && s?.length == 1) {
                        verifyCode()
                    }

                    // Обновляем скрытое поле с полным кодом
                    updateHiddenCode()
                }

                override fun afterTextChanged(s: Editable?) {}
            })

            // Обработка Backspace для перехода назад
            editText.setOnKeyListener { _, keyCode, event ->
                if (event.action == android.view.KeyEvent.ACTION_DOWN &&
                    keyCode == android.view.KeyEvent.KEYCODE_DEL &&
                    editText.text.isNullOrEmpty() &&
                    index > 0) {
                    digitInputs[index - 1].requestFocus()
                    true
                } else {
                    false
                }
            }
        }
    }

    private fun updateHiddenCode() {
        val code = digitInputs.joinToString("") { it.text?.toString().orEmpty() }
        codeInputHidden.setText(code)
    }

    private fun getCode(): String {
        return digitInputs.joinToString("") { it.text?.toString().orEmpty() }
    }

    private fun clearCode() {
        digitInputs.forEach { it.setText("") }
        digitInputs.firstOrNull()?.requestFocus()
    }

    private fun verifyCode() {
        val code = getCode()

        if (code.length != 6) {
            showError("Введите 6 цифр")
            return
        }

        showLoading(true)
        errorText.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val response = apiService.verifyCode(VerifyCodeRequest(userEmail, code))

                Toast.makeText(
                    this@VerifyEmailActivity,
                    "✅ ${response.message}",
                    Toast.LENGTH_LONG
                ).show()

                // Переход на экран входа
                val intent = Intent(this@VerifyEmailActivity, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                finish()

            } catch (e: HttpException) {
                val errorMsg = when (e.code()) {
                    400 -> {
                        val body = e.response()?.errorBody()?.string()
                        when {
                            body?.contains("истёк", ignoreCase = true) == true -> "Код истёк. Запросите новый."
                            body?.contains("неверный", ignoreCase = true) == true -> "Неверный код"
                            else -> body ?: "Неверный код"
                        }
                    }
                    404 -> "Пользователь не найден"
                    500 -> "Ошибка сервера"
                    else -> "Ошибка: ${e.code()}"
                }
                showError(errorMsg)
            } catch (e: IOException) {
                showError("Ошибка сети")
            } catch (e: Exception) {
                showError("Ошибка: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun showError(message: String) {
        errorText.text = message
        errorText.visibility = View.VISIBLE
        // Анимация тряски полей (опционально)
        digitInputs.forEach { it.setTextColor(getColor(android.R.color.holo_red_dark)) }
    }

    private fun clearError() {
        errorText.visibility = View.GONE
        digitInputs.forEach {
            it.setTextColor(getColor(android.R.color.primary_text_light))
        }
    }

    private fun resendCode() {
        showLoading(true)
        resendEnabled = false

        lifecycleScope.launch {
            try {
                val response = apiService.resendCode(ResendCodeRequest(userEmail))

                Toast.makeText(
                    this@VerifyEmailActivity,
                    "📧 ${response.message}",
                    Toast.LENGTH_SHORT
                ).show()

                clearCode()
                clearError()
                startResendTimer()

            } catch (e: HttpException) {
                Toast.makeText(this@VerifyEmailActivity, "Не удалось отправить код", Toast.LENGTH_LONG).show()
                resendEnabled = true
            } catch (e: IOException) {
                Toast.makeText(this@VerifyEmailActivity, "Ошибка сети", Toast.LENGTH_LONG).show()
                resendEnabled = true
            } finally {
                showLoading(false)
            }
        }
    }

    private fun startResendTimer() {
        timer?.cancel()

        resendButton.isEnabled = false
        resendButton.alpha = 0.5f
        timerText.visibility = View.VISIBLE
        resendEnabled = false

        timer = object : CountDownTimer(60_000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                timerText.text = "00:${String.format("%02d", seconds)}"
            }

            override fun onFinish() {
                timerText.visibility = View.GONE
                resendButton.isEnabled = true
                resendButton.alpha = 1f
                resendEnabled = true
                resendButton.text = "Отправить код ещё раз"
            }
        }.start()
    }

    private fun showLoading(isLoading: Boolean) {
        loadingIndicator.visibility = if (isLoading) View.VISIBLE else View.GONE
        verifyButton.isEnabled = !isLoading
        digitInputs.forEach { it.isEnabled = !isLoading }
        resendButton.isEnabled = !isLoading && resendEnabled
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
    }
}