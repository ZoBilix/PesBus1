package com.example.pesbuscom.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.lifecycle.lifecycleScope
import com.example.pesbuscom.BusApiService
import com.example.pesbuscom.R
import com.example.pesbuscom.TokenManager
import com.example.pesbuscom.models.UserAdminCreateRequest
import com.example.pesbuscom.models.UserAdminUpdateRequest
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class AdminBottomSheet : BottomSheetDialogFragment() {

    private lateinit var apiService: BusApiService
    private lateinit var tableUsers: TableLayout
    private lateinit var tvTotalUsers: TextView
    private lateinit var tvTotalRoutes: TextView
    private lateinit var tvTotalFavorites: TextView
    private lateinit var progressBar: ProgressBar

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_admin, container, false)
        
        tableUsers = view.findViewById(R.id.table_users)
        tvTotalUsers = view.findViewById(R.id.tv_total_users)
        tvTotalRoutes = view.findViewById(R.id.tv_total_routes)
        tvTotalFavorites = view.findViewById(R.id.tv_total_favorites)
        progressBar = view.findViewById(R.id.progress_admin)
        
        val btnAddUser: Button = view.findViewById(R.id.btn_add_user)
        btnAddUser.setOnClickListener { showAddUserDialog() }

        setupRetrofit()
        loadData()

        return view
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog
        dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let {
            val behavior = BottomSheetBehavior.from(it)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    private fun setupRetrofit() {
        val retrofit = Retrofit.Builder()
            .baseUrl("http://144.31.253.20/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        apiService = retrofit.create(BusApiService::class.java)
    }

    private fun loadData() {
        val token = "Bearer ${TokenManager.getToken(requireContext())}"
        
        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE
            try {
                val stats = apiService.adminGetStats(token)
                tvTotalUsers.text = stats.total_users.toString()
                tvTotalRoutes.text = stats.total_routes.toString()
                tvTotalFavorites.text = stats.total_favorites.toString()

                val users = apiService.adminGetUsers(token)
                updateUserTable(users)
            } catch (e: Exception) {
                if (isAdded) {
                    Toast.makeText(context, "Ошибка загрузки: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                if (isAdded) {
                    progressBar.visibility = View.GONE
                }
            }
        }
    }

    private fun updateUserTable(users: List<com.example.pesbuscom.models.UserAdminResponse>) {
        val childCount = tableUsers.childCount
        if (childCount > 1) {
            tableUsers.removeViews(1, childCount - 1)
        }

        users.forEach { user ->
            val row = TableRow(requireContext()).apply { setPadding(8, 8, 8, 8) }
            
            row.addView(TextView(requireContext()).apply { text = user.id.toString(); setPadding(8, 16, 8, 16) })
            row.addView(TextView(requireContext()).apply { text = user.username; setPadding(8, 16, 8, 16) })
            row.addView(TextView(requireContext()).apply { text = user.email; setPadding(8, 16, 8, 16) })
            row.addView(TextView(requireContext()).apply { text = user.created_at?.take(10) ?: "-"; setPadding(8, 16, 8, 16) })

            val actionsLayout = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
            
            val btnEdit = ImageButton(requireContext()).apply {
                setImageResource(android.R.drawable.ic_menu_edit)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setOnClickListener { showEditUserDialog(user) }
            }
            
            val btnDelete = ImageButton(requireContext()).apply {
                setImageResource(android.R.drawable.ic_menu_delete)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setOnClickListener { showDeleteConfirmDialog(user.id) }
            }
            
            actionsLayout.addView(btnEdit)
            actionsLayout.addView(btnDelete)
            row.addView(actionsLayout)

            tableUsers.addView(row)
        }
    }

    private fun showAddUserDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_admin_user, null)
        val etUser = dialogView.findViewById<TextInputEditText>(R.id.et_admin_username)
        val etEmail = dialogView.findViewById<TextInputEditText>(R.id.et_admin_email)
        val etPass = dialogView.findViewById<TextInputEditText>(R.id.et_admin_password)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Добавить пользователя")
            .setView(dialogView)
            .setPositiveButton("Создать") { _, _ ->
                val request = UserAdminCreateRequest(
                    etUser.text.toString(),
                    etEmail.text.toString(),
                    etPass.text.toString()
                )
                adminCreateUser(request)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showEditUserDialog(user: com.example.pesbuscom.models.UserAdminResponse) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_admin_user, null)
        val etUser = dialogView.findViewById<TextInputEditText>(R.id.et_admin_username)
        val etEmail = dialogView.findViewById<TextInputEditText>(R.id.et_admin_email)
        val etPass = dialogView.findViewById<TextInputEditText>(R.id.et_admin_password)

        etUser.setText(user.username)
        etEmail.setText(user.email)
        etPass.hint = "Оставьте пустым, чтобы не менять"

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Редактировать пользователя")
            .setView(dialogView)
            .setPositiveButton("Сохранить") { _, _ ->
                val request = UserAdminUpdateRequest(
                    username = etUser.text.toString(),
                    email = etEmail.text.toString(),
                    password = if (etPass.text.isNullOrEmpty()) null else etPass.text.toString()
                )
                adminUpdateUser(user.id.toString(), request)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showDeleteConfirmDialog(userId: Int) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Удаление")
            .setMessage("Вы уверены, что хотите удалить этого пользователя?")
            .setPositiveButton("Удалить") { _, _ -> adminDeleteUser(userId.toString()) }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun adminCreateUser(request: UserAdminCreateRequest) {
        lifecycleScope.launch {
            try {
                apiService.adminCreateUser("Bearer ${TokenManager.getToken(requireContext())}", request)
                loadData()
                Toast.makeText(context, "Создан", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                if (isAdded) Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun adminUpdateUser(id: String, request: UserAdminUpdateRequest) {
        lifecycleScope.launch {
            try {
                apiService.adminUpdateUser("Bearer ${TokenManager.getToken(requireContext())}", id, request)
                loadData()
                Toast.makeText(context, "Обновлен", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                if (isAdded) Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun adminDeleteUser(id: String) {
        lifecycleScope.launch {
            try {
                apiService.adminDeleteUser("Bearer ${TokenManager.getToken(requireContext())}", id)
                loadData()
                Toast.makeText(context, "Удален", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                if (isAdded) Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun getTheme(): Int = R.style.CustomBottomSheetDialog
}
