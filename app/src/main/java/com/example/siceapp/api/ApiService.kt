package com.example.siceapp.api

import com.example.siceapp.model.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // AUTH
    @POST(".")
    suspend fun login(
        @Query("path") path: String = "auth/login",
        @Body body: LoginRequest
    ): Response<LoginResponse>

    @GET(".")
    suspend fun getMe(
        @Query("path") path: String = "auth/me"
    ): Response<UserResponse>

    @POST(".")
    suspend fun logout(
        @Query("path") path: String = "auth/logout"
    ): Response<GenericResponse>

    // TASKS
    @GET(".")
    suspend fun getTasks(
        @Query("path") path: String = "tasks",
        @Query("status") status: String? = null,
        @Query("month") month: Int? = null,
        @Query("year") year: Int? = null,
        @Query("show_hidden") showHidden: String? = null
    ): Response<TasksResponse>

    @GET(".")
    suspend fun getTaskDetail(
        @Query("path") path: String
    ): Response<TaskDetailResponse>

    @POST(".")
    suspend fun updateTaskStatus(
        @Query("path") path: String,
        @Body body: StatusRequest
    ): Response<GenericResponse>

    @POST(".")
    suspend fun toggleTaskHidden(
        @Query("path") path: String
    ): Response<GenericResponse>

    @POST(".")
    suspend fun createTask(
        @Query("path") path: String = "tasks",
        @Body body: Map<String, String>
    ): Response<GenericResponse>

    @PUT(".")
    suspend fun editTask(
        @Query("path") path: String,
        @Body body: Map<String, String>
    ): Response<GenericResponse>

    @DELETE(".")
    suspend fun deleteTask(
        @Query("path") path: String
    ): Response<GenericResponse>

    // COMMENTS
    @GET(".")
    suspend fun getComments(
        @Query("path") path: String = "comments",
        @Query("task_id") taskId: Int
    ): Response<CommentsResponse>

    @POST(".")
    suspend fun addComment(
        @Query("path") path: String = "comments",
        @Body body: CommentRequest
    ): Response<GenericResponse>

    @PUT(".")
    suspend fun editComment(
        @Query("path") path: String,
        @Body body: Map<String, String>
    ): Response<GenericResponse>

    @DELETE(".")
    suspend fun deleteComment(
        @Query("path") path: String
    ): Response<GenericResponse>

    // NOTIFICATIONS
    @GET(".")
    suspend fun getNotifications(
        @Query("path") path: String = "notifications"
    ): Response<NotificationsResponse>

    @POST(".")
    suspend fun markNotificationsRead(
        @Query("path") path: String = "notifications/read"
    ): Response<GenericResponse>

    // CALENDAR
    @GET(".")
    suspend fun getCalendar(
        @Query("path") path: String = "calendar",
        @Query("month") month: Int,
        @Query("year") year: Int,
        @Query("show_hidden") showHidden: String? = null
    ): Response<CalendarResponse>

    // USERS
    @GET(".")
    suspend fun getUsers(
        @Query("path") path: String = "users"
    ): Response<UsersResponse>

    @PUT(".")
    suspend fun updateProfile(
        @Query("path") path: String = "users/me",
        @Body body: Map<String, String>
    ): Response<GenericResponse>

    @GET(".")
    suspend fun getUserById(
        @Query("path") path: String
    ): Response<UserResponse>

    @POST(".")
    suspend fun updateStatus(
        @Query("path") path: String = "users/status",
        @Body body: Map<String, String>
    ): Response<GenericResponse>

    @POST(".")
    suspend fun updateFcmToken(
        @Query("path") path: String = "users/fcm_token",
        @Body body: Map<String, String>
    ): Response<GenericResponse>

    // CATEGORIES
    @GET(".")
    suspend fun getCategories(
        @Query("path") path: String = "categories"
    ): Response<GenericResponse>
}
