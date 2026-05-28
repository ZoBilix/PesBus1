package com.example.myapplication

import com.example.myapplication.models.Bus
import com.example.myapplication.models.BustimeCityDb
import retrofit2.http.*

interface BusApiService {

    @POST("register")
    suspend fun register(@Body user: UserRegisterRequest): UserResponse

    @FormUrlEncoded
    @POST("token")
    suspend fun login(
        @Field("username") username: String,
        @Field("password") password: String
    ): LoginResponse

    @GET("users/me")
    suspend fun getMe(@Header("Authorization") token: String): UserResponse

    @GET("schedule")
    suspend fun getSchedules(
        @Header("Authorization") token: String,
        @Query("skip") skip: Int = 0,
        @Query("limit") limit: Int = 100
    ): List<ScheduleResponse>

    @POST("schedule")
    suspend fun createSchedule(
        @Header("Authorization") token: String,
        @Body schedule: ScheduleCreateRequest
    ): ScheduleResponse

    @PUT("schedule/{schedule_id}")
    suspend fun updateSchedule(
        @Header("Authorization") token: String,
        @Path("schedule_id") scheduleId: Int,
        @Body schedule: ScheduleUpdateRequest
    ): ScheduleResponse

    @DELETE("schedule/{schedule_id}")
    suspend fun deleteSchedule(
        @Header("Authorization") token: String,
        @Path("schedule_id") scheduleId: Int
    ): Map<String, String>

    @GET("routes-list")
    suspend fun getRoutes(@Header("Authorization") token: String): List<RouteResponse>

    @POST("verify-code")
    suspend fun verifyCode(@Body request: VerifyCodeRequest): VerifyCodeResponse

    @POST("resend-code")
    suspend fun resendCode(@Body request: ResendCodeRequest): ResendCodeResponse

    @GET("api/buses")
    suspend fun getBuses(): List<Bus>

    @GET
    suspend fun getCityDb(@Url url: String): BustimeCityDb
}