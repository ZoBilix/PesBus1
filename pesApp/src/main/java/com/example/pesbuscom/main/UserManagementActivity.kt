package com.example.pesbuscom.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.pesbuscom.BusApiService
import com.example.pesbuscom.R
import com.example.pesbuscom.TokenManager
import com.example.pesbuscom.models.UserAdminCreateRequest
import com.example.pesbuscom.models.UserAdminResponse
import com.example.pesbuscom.models.UserAdminUpdateRequest
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class UserManagementActivity : AppCompatActivity() {

    private lateinit var apiService: BusApiService
    private lateinit var adapter: UsersAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var fabAddUser: FloatingActionButton

    companion object {
        private const val BASE_URL = "http://144.31.253.20/"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_management)

        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        recyclerView = findViewById(R.id.users_recycler_view)
        loadingIndicator = findViewById(R.id.loading_indicator)
        fabAddUser = findViewById(R.id.fab_add_user)

        setupApiService()
        setupRecyclerView()

        fabAddUser.setOnClickListener { showAddUserDialog() }

        loadUsers()
    }

    private fun setupApiService() {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        // X-API-KEY удален для админ-панели по запросу
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        apiService = retrofit.create(BusApiService::class.java)
    }

    private fun setupRecyclerView() {
        adapter = UsersAdapter(emptyList(), 
            onEditClick = { user -> showEditUserDialog(user) },
            onDeleteClick = { user -> showDeleteConfirmation(user) }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun loadUsers() {
        val token = TokenManager.getToken(this)
        if (token == null) {
            Toast.makeText(this, "Ошибка: вы не авторизованы", Toast.LENGTH_SHORT).show()
            return
        }

        loadingIndicator.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val users = withContext(Dispatchers.IO) {
                    apiService.adminGetUsers("Bearer $token")
                }
                adapter.updateUsers(users)
            } catch (e: HttpException) {
                val code = e.code()
                val errorBody = e.response()?.errorBody()?.string()
                Toast.makeText(this@UserManagementActivity, "Ошибка $code: $errorBody", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this@UserManagementActivity, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                loadingIndicator.visibility = View.GONE
            }
        }
    }

    private fun showAddUserDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_admin_user, null)
        val etUsername = dialogView.findViewById<EditText>(R.id.et_admin_username)
        val etEmail = dialogView.findViewById<EditText>(R.id.et_admin_email)
        val etPassword = dialogView.findViewById<EditText>(R.id.et_admin_password)

        AlertDialog.Builder(this)
            .setTitle("Добавить пользователя")
            .setView(dialogView)
            .setPositiveButton("Создать") { _, _ ->
                val username = etUsername.text.toString().trim()
                val email = etEmail.text.toString().trim()
                val password = etPassword.text.toString()
                if (username.isNotEmpty() && email.isNotEmpty() && password.isNotEmpty()) {
                    createUser(username, email, password)
                } else {
                    Toast.makeText(this, "Заполните все поля", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun createUser(username: String, email: String, password: String) {
        val token = TokenManager.getToken(this) ?: return
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    apiService.adminCreateUser("Bearer $token", UserAdminCreateRequest(username, email, password))
                }
                loadUsers()
                Toast.makeText(this@UserManagementActivity, "Пользователь создан", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@UserManagementActivity, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showEditUserDialog(user: UserAdminResponse) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_admin_user, null)
        val etUsername = dialogView.findViewById<EditText>(R.id.et_admin_username)
        val etEmail = dialogView.findViewById<EditText>(R.id.et_admin_email)
        val etPassword = dialogView.findViewById<EditText>(R.id.et_admin_password)

        etUsername.setText(user.username)
        etEmail.setText(user.email)
        etPassword.hint = "Оставьте пустым, чтобы не менять"

        AlertDialog.Builder(this)
            .setTitle("Редактировать пользователя")
            .setView(dialogView)
            .setPositiveButton("Сохранить") { _, _ ->
                val username = etUsername.text.toString().trim()
                val email = etEmail.text.toString().trim()
                val password = etPassword.text.toString().ifEmpty { null }
                updateUser(user.id.toString(), username, email, password)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun updateUser(id: String, username: String, email: String, password: String?) {
        val token = TokenManager.getToken(this) ?: return
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    apiService.adminUpdateUser("Bearer $token", id, UserAdminUpdateRequest(username, email, password))
                }
                loadUsers()
            } catch (e: Exception) {
                Toast.makeText(this@UserManagementActivity, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showDeleteConfirmation(user: UserAdminResponse) {
        AlertDialog.Builder(this)
            .setTitle("Удаление")
            .setMessage("Вы уверены, что хотите удалить пользователя ${user.username}?")
            .setPositiveButton("Удалить") { _, _ -> deleteUser(user.id.toString()) }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun deleteUser(id: String) {
        val token = TokenManager.getToken(this) ?: return
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    apiService.adminDeleteUser("Bearer $token", id)
                }
                loadUsers()
            } catch (e: Exception) {
                Toast.makeText(this@UserManagementActivity, "Ошибка удаления", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
