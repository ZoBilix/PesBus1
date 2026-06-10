package com.example.pesbuscom.main

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.example.pesbuscom.BusApiService
import com.example.pesbuscom.R
import com.example.pesbuscom.TokenManager
import com.example.pesbuscom.UserRegisterRequest
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
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
    private lateinit var loginLink: AppCompatTextView
    private lateinit var errorText: AppCompatTextView
    private lateinit var loadingIndicator: ProgressBar

    companion object {
        private const val BASE_URL = "https://top4023177375.mwscdn.ru/"
        private const val API_KEY = "8FuexJFFJizPEnptwnn9b70y7jc88VZFiOTPVUIE8sE="
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

        // Валидация в реальном времени
        usernameInput.doAfterTextChanged { clearErrors() }
        emailInput.doAfterTextChanged { clearErrors() }
        passwordInput.doAfterTextChanged { clearErrors() }
        confirmPasswordInput.doAfterTextChanged {
            clearErrors()
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
            finish()
        }

        // Обработка Enter в последнем поле
        confirmPasswordInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
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
                return
            }
            email.isEmpty() -> {
                emailLayout.error = "Введите email"
                return
            }
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                emailLayout.error = "Неверный формат email"
                return
            }
            password.isEmpty() -> {
                passwordLayout.error = "Введите пароль"
                return
            }
            password != confirmPassword -> {
                confirmLayout.error = "Пароли не совпадают"
                return
            }
        }

        showLoading(true)
        clearErrors()

        lifecycleScope.launch {
            try {
                // 1. Регистрация
                withContext(Dispatchers.IO) {
                    val request = UserRegisterRequest(
                        username = username,
                        email = email,
                        password = password,
                        phone = phone
                    )
                    apiService.register(request)
                }

                // 2. Автоматический вход сразу после успешной регистрации
                val loginResponse = withContext(Dispatchers.IO) {
                    apiService.login(username, password)
                }

                if (loginResponse.token != null) {
                    val finalUsername = loginResponse.user?.username ?: username
                    // ПРОВЕРКА НА АДМИНА: если имя admin, принудительно даем роль admin
                    val finalRole = if (finalUsername.equals("admin", ignoreCase = true)) {
                        "admin"
                    } else {
                        loginResponse.user?.role ?: "user"
                    }

                    TokenManager.saveToken(
                        this@RegisterActivity,
                        loginResponse.token,
                        finalRole,
                        finalUsername
                    )
                    
                    Toast.makeText(this@RegisterActivity, "Регистрация успешна!", Toast.LENGTH_SHORT).show()
                    
                    val intent = Intent(this@RegisterActivity, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                } else {
                    showError("Ошибка входа после регистрации")
                }

            } catch (e: HttpException) {
                val errorMsg = when (e.code()) {
                    400 -> "Пользователь с такими данными уже существует"
                    422 -> "Неверные данные"
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

    private fun clearErrors() {
        usernameLayout.error = null
        emailLayout.error = null
        passwordLayout.error = null
        confirmLayout.error = null
        errorText.visibility = View.GONE
    }

    private fun showError(message: String) {
        errorText.text = message
        errorText.visibility = View.VISIBLE
    }

    private fun showLoading(isLoading: Boolean) {
        loadingIndicator.visibility = if (isLoading) View.VISIBLE else View.GONE
        registerButton.isEnabled = !isLoading
    }
}
