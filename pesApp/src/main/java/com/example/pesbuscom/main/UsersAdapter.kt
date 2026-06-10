package com.example.pesbuscom.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.pesbuscom.R
import com.example.pesbuscom.models.UserAdminResponse

class UsersAdapter(
    private var users: List<UserAdminResponse>,
    private val onEditClick: (UserAdminResponse) -> Unit,
    private val onDeleteClick: (UserAdminResponse) -> Unit
) : RecyclerView.Adapter<UsersAdapter.UserViewHolder>() {

    class UserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val username: TextView = view.findViewById(R.id.tv_username)
        val email: TextView = view.findViewById(R.id.tv_email)
        val role: TextView = view.findViewById(R.id.tv_role)
        val btnEdit: ImageButton = view.findViewById(R.id.btn_edit)
        val btnDelete: ImageButton = view.findViewById(R.id.btn_delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_user, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = users[position]
        holder.username.text = user.username
        holder.email.text = user.email
        holder.role.text = user.role
        
        // Цвет тега роли
        holder.role.setBackgroundResource(if (user.role == "admin") R.drawable.bg_role_admin else R.drawable.bg_role_user)

        holder.btnEdit.setOnClickListener { onEditClick(user) }
        holder.btnDelete.setOnClickListener { onDeleteClick(user) }
    }

    override fun getItemCount() = users.size

    fun updateUsers(newUsers: List<UserAdminResponse>) {
        users = newUsers
        notifyDataSetChanged()
    }
}
