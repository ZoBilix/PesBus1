package com.example.myapplication

// ================= МОДЕЛИ ДЛЯ АВТОРИЗАЦИИ =================

data class UserRegisterRequest(
    val username: String,
    val email: String,
    val password: String,
    val phone: String? = null
)

data class LoginResponse(
    val access_token: String,
    val token_type: String,
    val role: String
)

data class UserResponse(
    val id: Int,
    val username: String,
    val email: String,
    val role: String,
    val phone: String?,
    val is_active: Boolean,
    val created_at: String
)

data class ScheduleResponse(
    val id: Int,
    val route_number: String,
    val route_name: String?,
    val departure_point_a: String,
    val departure_point_b: String,
    val time_from_a: String,
    val time_from_b: String,
    val price: String?,
    val is_active: Boolean
)

data class ScheduleCreateRequest(
    val route_number: String,
    val route_name: String?,
    val departure_point_a: String,
    val departure_point_b: String,
    val time_from_a: String,
    val time_from_b: String,
    val price: String?,
    val is_active: Boolean = true
)

data class ScheduleUpdateRequest(
    val route_number: String,
    val route_name: String?,
    val departure_point_a: String,
    val departure_point_b: String,
    val time_from_a: String,
    val time_from_b: String,
    val price: String?,
    val is_active: Boolean
)

data class RouteResponse(
    val route_number: String,
    val route_name: String?
)

data class VerifyCodeRequest(
    val email: String,
    val code: String
)

data class VerifyCodeResponse(
    val message: String,
    val username: String,
    val is_active: Boolean
)

data class ResendCodeRequest(
    val email: String
)

data class ResendCodeResponse(
    val message: String
)