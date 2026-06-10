package com.example.pesbuscom.main

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.example.pesbuscom.BusApiService
import com.example.pesbuscom.R
import com.example.pesbuscom.TokenManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
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
    private lateinit var forgotPassword: AppCompatTextView
    private lateinit var errorText: AppCompatTextView
    private lateinit var loadingIndicator: ProgressBar

    companion object {
        private const val BASE_URL = "https://top4023177375.mwscdn.ru/"
        private const val API_KEY = "8FuexJFFJizPEnptwnn9b70y7jc88VZFiOTPVUIE8sE="
        private const val PREFS_NAME = "app_prefs"
        private const val KEY_THEME = "is_dark_theme"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        // Настройка логирования и обязательного заголовка X-API-KEY
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("X-API-KEY", API_KEY)
                    .build()
                chain.proceed(request)
            }
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        apiService = retrofit.create(BusApiService::class.java)

        if (TokenManager.isLoggedIn(this) && TokenManager.getRole(this) != "guest") {
            navigateToMain()
            finish()
            return
        }

        usernameInput.doAfterTextChanged { clearErrors() }
        passwordInput.doAfterTextChanged { clearErrors() }

        loginButton.setOnClickListener { performLogin() }
        registerButton.setOnClickListener { startActivity(Intent(this, RegisterActivity::class.java)) }

        guestButton.setOnClickListener {
            TokenManager.saveToken(this, "guest_token", "guest", "Гость")
            navigateToMain()
            finish()
        }

        passwordInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                performLogin()
                true
            } else {
                false
            }
        }
    }

    private fun applySavedTheme() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val isDarkTheme = prefs.getBoolean(KEY_THEME, false)
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkTheme) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    private fun performLogin() {
        val username = usernameInput.text.toString().trim()
        val password = passwordInput.text.toString()

        if (username.isEmpty() || password.isEmpty()) {
            if (username.isEmpty()) usernameLayout.error = "Введите имя пользователя"
            if (password.isEmpty()) passwordLayout.error = "Введите пароль"
            return
        }

        showLoading(true)
        clearErrors()

        lifecycleScope.launch {
            try {
                val response = apiService.login(username, password)

                // Сервер возвращает "token", а данные пользователя внутри объекта "user"
                if (response.token != null) {
                    val finalUsername = response.user?.username ?: username
                    
                    // ПРОВЕРКА НА АДМИНА: если имя "admin", принудительно даем роль admin
                    val finalRole = if (finalUsername.equals("admin", ignoreCase = true)) {
                        "admin"
                    } else {
                        response.user?.role ?: "user"
                    }

                    TokenManager.saveToken(
                        this@LoginActivity,
                        response.token,
                        finalRole,
                        finalUsername
                    )
                    navigateToMain()
                    finish()
                } else {
                    showError("Ошибка: сервер не вернул токен")
                }

            } catch (e: HttpException) {
                when (e.code()) {
                    401 -> showError("Неверное имя пользователя или пароль")
                    404 -> showError("Сервис авторизации недоступен (404)")
                    else -> showError("Ошибка: ${e.code()}")
                }
            } catch (e: IOException) {
                showError("Ошибка сети. Проверьте соединение")
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
    }

    private fun showLoading(isLoading: Boolean) {
        loadingIndicator.visibility = if (isLoading) View.VISIBLE else View.GONE
        loginButton.isEnabled = !isLoading
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }
}
