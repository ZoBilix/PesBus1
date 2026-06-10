package com.example.pesbuscom.models

import com.google.gson.annotations.SerializedName

// --- Auth ---
data class LoginRequest(
    val username: String,
    val password: String
)

data class LoginResponse(
    val message: String?,
    val token: String?,
    val user: UserDataResponse?
)

data class UserDataResponse(
    val id: Int?,
    val username: String?,
    val email: String?,
    val role: String? = "user"
)

data class UserRegisterRequest(
    val username: String,
    val email: String,
    val password: String
)

data class UserResponse(
    val id: Int,
    val username: String,
    val email: String,
    val role: String? = "user"
)

// --- Routes & Favorites ---
data class RouteResponse(
    val id: String,
    val route_number: String,
    val route_name: String,
    val description: String? = null
)

// --- Admin ---
data class UserAdminResponse(
    val id: Int,
    val username: String,
    val email: String,
    val role: String,
    val created_at: String? = null
)

data class UserAdminCreateRequest(
    val username: String,
    val email: String,
    val password: String,
    val role: String = "user"
)

data class UserAdminUpdateRequest(
    val username: String? = null,
    val email: String? = null,
    val password: String? = null,
    val role: String? = null
)

data class AdminStatsResponse(
    val total_users: Int,
    val total_routes: Int,
    val total_favorites: Int
)

// --- Legacy / Others ---
data class VerifyCodeRequest(val email: String, val code: String)
data class VerifyCodeResponse(val success: Boolean, val message: String?)
data class ResendCodeRequest(val email: String)
data class ResendCodeResponse(val success: Boolean, val message: String?)
data class ScheduleResponse(val id: Int, val route_id: String, val time: String)
data class ScheduleCreateRequest(val route_id: String, val time: String)
data class ScheduleUpdateRequest(val time: String)
