package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.HttpException
import java.io.IOException


class LoginActivity : AppCompatActivity() {

    private lateinit var apiService: BusApiService

    // Views
    private lateinit var toolbar: MaterialToolbar
    private lateinit var usernameInput: TextInputEditText
    private lateinit var passwordInput: TextInputEditText
    private lateinit var usernameLayout: TextInputLayout
    private lateinit var passwordLayout: TextInputLayout
    private lateinit var loginButton: MaterialButton
    private lateinit var registerButton: MaterialButton
    private lateinit var guestButton: MaterialButton
    private lateinit var forgotPassword: androidx.appcompat.widget.AppCompatTextView
    private lateinit var errorText: androidx.appcompat.widget.AppCompatTextView
    private lateinit var loadingIndicator: android.widget.ProgressBar

    companion object {
        private const val BASE_URL = "https://bus.api.pespes.online:8443/"
        private const val PREFS_NAME = "app_prefs"
        private const val KEY_THEME = "is_dark_theme"
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        // Применяем сохранённую тему
        applySavedTheme()

        setContentView(R.layout.activity_login)

        // Инициализация Views
        toolbar = findViewById(R.id.toolbar)
        usernameInput = findViewById(R.id.username_input)
        passwordInput = findViewById(R.id.password_input)
        usernameLayout = findViewById(R.id.username_layout)
        passwordLayout = findViewById(R.id.password_layout)
        loginButton = findViewById(R.id.login_button)
        registerButton = findViewById(R.id.register_button)
        guestButton = findViewById(R.id.guest_button)
        forgotPassword = findViewById(R.id.forgot_password)
        errorText = findViewById(R.id.error_text)
        loadingIndicator = findViewById(R.id.loading_indicator)

        // Инициализация API
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        apiService = retrofit.create(BusApiService::class.java)

        // Проверяем, авторизован ли пользователь
        if (TokenManager.isLoggedIn(this) && TokenManager.getRole(this) != "guest") {
            navigateToMain()
            finish()
            return
        }

        // Валидация в реальном времени
        usernameInput.doAfterTextChanged { clearErrors() }
        passwordInput.doAfterTextChanged { clearErrors() }

        // Кнопка: Войти
        loginButton.setOnClickListener {
            performLogin()
        }

        // Кнопка: Регистрация
        registerButton.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        // Кнопка: Гость
        guestButton.setOnClickListener {
            TokenManager.saveToken(
                context = this,
                accessToken = "guest_token",
                role = "guest",
                username = "Гость"
            )
            Toast.makeText(this, "Вход как гость", Toast.LENGTH_SHORT).show()
            navigateToMain()
            finish()
        }

        // Ссылка: Забыли пароль?
        forgotPassword.setOnClickListener {
            Toast.makeText(this, "Восстановление пароля скоро будет доступно", Toast.LENGTH_SHORT).show()
        }

        // Обработка Enter в поле пароля
        passwordInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                performLogin()
                true
            } else {
                false
            }
        }
    }

    // ✅ Применение сохранённой темы
    private fun applySavedTheme() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val isDarkTheme = prefs.getBoolean(KEY_THEME, false)

        val mode = if (isDarkTheme) {
            AppCompatDelegate.MODE_NIGHT_YES
        } else {
            AppCompatDelegate.MODE_NIGHT_NO
        }

        AppCompatDelegate.setDefaultNightMode(mode)
    }


    private fun performLogin() {
        val username = usernameInput.text.toString().trim()
        val password = passwordInput.text.toString()

        when {
            username.isEmpty() -> {
                usernameLayout.error = "Введите имя пользователя"
                usernameInput.requestFocus()
                return
            }
            password.isEmpty() -> {
                passwordLayout.error = "Введите пароль"
                passwordInput.requestFocus()
                return
            }
        }

        showLoading(true)
        clearErrors()

        lifecycleScope.launch {
            try {
                val response = apiService.login(
                    android.net.Uri.encode(username),
                    password
                )

                TokenManager.saveToken(
                    this@LoginActivity,
                    response.access_token,
                    response.role,
                    username
                )

                Toast.makeText(
                    this@LoginActivity,
                    "Добро пожаловать, $username! 👋",
                    Toast.LENGTH_SHORT
                ).show()

                navigateToMain()
                finish()

            } catch (e: HttpException) {
                when (e.code()) {
                    401 -> showError("Неверное имя пользователя или пароль")
                    400 -> {
                        val body = e.response()?.errorBody()?.string()
                        if (body?.contains("деактивирован", ignoreCase = true) == true) {
                            showError("Аккаунт деактивирован")
                        } else {
                            showError("Ошибка входа")
                        }
                    }
                    500 -> showError("Ошибка сервера. Попробуйте позже")
                    else -> showError("Ошибка: ${e.code()}")
                }
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
        passwordLayout.error = null
        errorText.visibility = View.GONE
    }

    private fun showError(message: String) {
        errorText.text = message
        errorText.visibility = View.VISIBLE
        errorText.animate()
            .alpha(1f)
            .setDuration(200)
            .start()
    }

    private fun showLoading(isLoading: Boolean) {
        loadingIndicator.visibility = if (isLoading) View.VISIBLE else View.GONE
        loginButton.isEnabled = !isLoading
        usernameInput.isEnabled = !isLoading
        passwordInput.isEnabled = !isLoading
        registerButton.isEnabled = !isLoading
        guestButton.isEnabled = !isLoading

        if (isLoading) {
            loginButton.alpha = 0.7f
        } else {
            loginButton.alpha = 1f
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }
}