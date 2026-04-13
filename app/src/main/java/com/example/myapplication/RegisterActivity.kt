package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.HttpException
import java.io.IOException

class RegisterActivity : AppCompatActivity() {

    private lateinit var apiService: BusApiService

    // Views
    private lateinit var toolbar: MaterialToolbar
    private lateinit var usernameInput: TextInputEditText
    private lateinit var emailInput: TextInputEditText
    private lateinit var phoneInput: TextInputEditText
    private lateinit var passwordInput: TextInputEditText
    private lateinit var confirmPasswordInput: TextInputEditText
    private lateinit var usernameLayout: TextInputLayout
    private lateinit var emailLayout: TextInputLayout
    private lateinit var passwordLayout: TextInputLayout
    private lateinit var confirmLayout: TextInputLayout
    private lateinit var registerButton: MaterialButton
    private lateinit var loginLink: androidx.appcompat.widget.AppCompatTextView
    private lateinit var errorText: androidx.appcompat.widget.AppCompatTextView
    private lateinit var loadingIndicator: android.widget.ProgressBar

    companion object {
        private const val BASE_URL = "https://bus.api.pespes.online:8443/"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        // Инициализация Views
        toolbar = findViewById(R.id.toolbar)
        usernameInput = findViewById(R.id.username_input)
        emailInput = findViewById(R.id.email_input)
        phoneInput = findViewById(R.id.phone_input)
        passwordInput = findViewById(R.id.password_input)
        confirmPasswordInput = findViewById(R.id.confirm_password_input)
        usernameLayout = findViewById(R.id.username_layout)
        emailLayout = findViewById(R.id.email_layout)
        passwordLayout = findViewById(R.id.password_layout)
        confirmLayout = findViewById(R.id.confirm_password_layout)
        registerButton = findViewById(R.id.register_button)
        loginLink = findViewById(R.id.login_link)
        errorText = findViewById(R.id.error_text)
        loadingIndicator = findViewById(R.id.loading_indicator)

        // Toolbar: только кнопка назад
        toolbar.setNavigationOnClickListener { finish() }

        // Инициализация API
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        apiService = retrofit.create(BusApiService::class.java)

        // Валидация в реальном времени
        usernameInput.doAfterTextChanged { clearErrors() }
        emailInput.doAfterTextChanged { clearErrors() }
        passwordInput.doAfterTextChanged { clearErrors() }
        confirmPasswordInput.doAfterTextChanged {
            clearErrors()
            // Проверка совпадения паролей при вводе
            if (confirmPasswordInput.text?.isNotEmpty() == true &&
                passwordInput.text.toString() != confirmPasswordInput.text.toString()) {
                confirmLayout.error = "Пароли не совпадают"
            }
        }

        // Кнопка: Зарегистрироваться
        registerButton.setOnClickListener {
            performRegistration()
        }

        // Ссылка: Уже есть аккаунт?
        loginLink.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        // Обработка Enter в последнем поле
        confirmPasswordInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                performRegistration()
                true
            } else {
                false
            }
        }
    }

    private fun performRegistration() {
        val username = usernameInput.text.toString().trim()
        val email = emailInput.text.toString().trim()
        val phone = phoneInput.text.toString().trim().ifEmpty { null }
        val password = passwordInput.text.toString()
        val confirmPassword = confirmPasswordInput.text.toString()

        // Валидация
        when {
            username.isEmpty() -> {
                usernameLayout.error = "Введите имя пользователя"
                usernameInput.requestFocus()
                return
            }
            username.length < 3 -> {
                usernameLayout.error = "Минимум 3 символа"
                usernameInput.requestFocus()
                return
            }
            email.isEmpty() -> {
                emailLayout.error = "Введите email"
                emailInput.requestFocus()
                return
            }
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                emailLayout.error = "Неверный формат email"
                emailInput.requestFocus()
                return
            }
            password.isEmpty() -> {
                passwordLayout.error = "Введите пароль"
                passwordInput.requestFocus()
                return
            }
            password.length < 8 -> {
                passwordLayout.error = "Минимум 8 символов"
                passwordInput.requestFocus()
                return
            }
            !password.matches(Regex(".*[A-Za-z].*")) || !password.matches(Regex(".*[0-9].*")) -> {
                passwordLayout.error = "Нужны буквы и цифры"
                passwordInput.requestFocus()
                return
            }
            password != confirmPassword -> {
                confirmLayout.error = "Пароли не совпадают"
                confirmPasswordInput.requestFocus()
                return
            }
        }

        showLoading(true)
        clearErrors()

        lifecycleScope.launch {
            try {
                // ✅ Сетевой запрос выполняем в фоновом потоке
                val result = withContext(Dispatchers.IO) {
                    val request = UserRegisterRequest(
                        username = username,
                        email = email,
                        password = password,
                        phone = phone
                    )
                    apiService.register(request)
                }

                // ✅ UI обновляем на главном потоке (автоматически после withContext)
                Toast.makeText(
                    this@RegisterActivity,
                    "✅ Проверьте почту для подтверждения!",
                    Toast.LENGTH_LONG
                ).show()

                val intent = Intent(this@RegisterActivity, VerifyEmailActivity::class.java)
                intent.putExtra(VerifyEmailActivity.EXTRA_EMAIL, email)
                startActivity(intent)
                finish()


            } catch (e: HttpException) {
                val errorBody = e.response()?.errorBody()?.string()
                val errorMsg = when (e.code()) {
                    400 -> {
                        when {
                            errorBody?.contains("именем", ignoreCase = true) == true ->
                                "Имя пользователя уже занято"
                            errorBody?.contains("Email", ignoreCase = true) == true ->
                                "Этот email уже зарегистрирован"
                            else -> "Пользователь с такими данными уже существует"
                        }
                    }
                    422 -> "Неверные данные. Проверьте поля формы"
                    500 -> "Ошибка сервера. Попробуйте позже"
                    else -> "Ошибка: ${e.code()}"
                }
                showError(errorMsg)
            } catch (e: IOException) {
                showError("Ошибка сети. Проверьте интернет")
            } catch (e: Exception) {
                showError("Ошибка: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun clearErrors() {
        usernameLayout.error = null
        emailLayout.error = null
        passwordLayout.error = null
        confirmLayout.error = null
        errorText.visibility = android.view.View.GONE
    }

    private fun showError(message: String) {
        errorText.text = message
        errorText.visibility = android.view.View.VISIBLE
        // Плавное появление
        errorText.animate().alpha(1f).setDuration(200).start()
    }

    private fun showLoading(isLoading: Boolean) {
        loadingIndicator.visibility = if (isLoading) android.view.View.VISIBLE else android.view.View.GONE
        registerButton.isEnabled = !isLoading
        usernameInput.isEnabled = !isLoading
        emailInput.isEnabled = !isLoading
        phoneInput.isEnabled = !isLoading
        passwordInput.isEnabled = !isLoading
        confirmPasswordInput.isEnabled = !isLoading
        loginLink.isEnabled = !isLoading

        // Анимация кнопки
        registerButton.alpha = if (isLoading) 0.7f else 1f
    }
}