package com.example.pesbuscom

import com.example.pesbuscom.models.*
import retrofit2.http.*

interface BusApiService {

    // --- Authentication ---
    @POST("api/register")
    suspend fun register(@Body user: UserRegisterRequest): UserResponse

    @FormUrlEncoded
    @POST("api/login")
    suspend fun login(
        @Field("username") username: String,
        @Field("password") password: String
    ): LoginResponse

    @GET("users/me")
    suspend fun getMe(@Header("Authorization") token: String): UserResponse

    // --- Routes & Favorites ---
    @GET("api/routes")
    suspend fun getAllRoutes(@Header("Authorization") token: String): List<RouteResponse>

    @POST("api/favorites/{routeId}")
    suspend fun addToFavorites(
        @Header("Authorization") token: String,
        @Path("routeId") routeId: String
    ): Map<String, String>

    @GET("api/favorites")
    suspend fun getFavorites(@Header("Authorization") token: String): List<RouteResponse>

    @DELETE("api/favorites/{routeId}")
    suspend fun removeFromFavorites(
        @Header("Authorization") token: String,
        @Path("routeId") routeId: String
    ): Map<String, String>

    // --- Admin: User Management ---
    @GET("api/admin/users")
    suspend fun adminGetUsers(@Header("Authorization") token: String): List<UserAdminResponse>

    @GET("api/admin/users/{id}")
    suspend fun adminGetUser(
        @Header("Authorization") token: String,
        @Path("id") id: String
    ): UserAdminResponse

    @POST("api/admin/users")
    suspend fun adminCreateUser(
        @Header("Authorization") token: String,
        @Body user: UserAdminCreateRequest
    ): UserAdminResponse

    @PUT("api/admin/users/{id}")
    suspend fun adminUpdateUser(
        @Header("Authorization") token: String,
        @Path("id") id: String,
        @Body user: UserAdminUpdateRequest
    ): UserAdminResponse

    @DELETE("api/admin/users/{id}")
    suspend fun adminDeleteUser(
        @Header("Authorization") token: String,
        @Path("id") id: String
    ): Map<String, String>

    // --- Admin: Stats ---
    @GET("api/admin/stats")
    suspend fun adminGetStats(@Header("Authorization") token: String): AdminStatsResponse

    // --- Legacy / Other ---
    @GET("api/buses")
    suspend fun getBuses(): List<Bus>

    @GET
    suspend fun getCityDb(@Url url: String): BustimeCityDb

    @POST("verify-code")
    suspend fun verifyCode(@Body request: VerifyCodeRequest): VerifyCodeResponse

    @POST("resend-code")
    suspend fun resendCode(@Body request: ResendCodeRequest): ResendCodeResponse
}
