package com.example.myapplication.main

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.example.myapplication.R
import com.example.myapplication.TokenManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class ProfileBottomSheet : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val avatar: ImageView = view.findViewById(R.id.profile_avatar)
        val usernameTv: TextView = view.findViewById(R.id.profile_username)
        val statusTv: TextView = view.findViewById(R.id.profile_status)
        val btnLogin: Button = view.findViewById(R.id.btn_login)
        val btnRegister: Button = view.findViewById(R.id.btn_register)
        val btnLogout: Button = view.findViewById(R.id.btn_logout)

        val context = requireContext()
        val isLoggedIn = TokenManager.isLoggedIn(context)
        val username = TokenManager.getUsername(context) ?: "Гость"

        if (isLoggedIn) {
            usernameTv.text = username
            statusTv.text = "Вы авторизованы"
            btnLogin.visibility = View.GONE
            btnRegister.visibility = View.GONE
            btnLogout.visibility = View.VISIBLE
        } else {
            usernameTv.text = "Гость"
            statusTv.text = "Вы используете гостевой доступ"
            btnLogin.visibility = View.VISIBLE
            btnRegister.visibility = View.VISIBLE
            btnLogout.visibility = View.GONE
        }

        btnLogin.setOnClickListener {
            startActivity(Intent(context, LoginActivity::class.java))
            dismiss()
        }

        btnRegister.setOnClickListener {
            startActivity(Intent(context, RegisterActivity::class.java))
            dismiss()
        }

        btnLogout.setOnClickListener {
            TokenManager.clearToken(context)
            Toast.makeText(context, "Вы вышли", Toast.LENGTH_SHORT).show()
            val intent = Intent(context, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            dismiss()
        }
    }

    override fun getTheme(): Int = R.style.CustomBottomSheetDialog
}
