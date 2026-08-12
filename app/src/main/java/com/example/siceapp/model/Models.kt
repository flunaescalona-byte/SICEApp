package com.example.siceapp.model

data class LoginRequest(
    val email: String,
    val password: String,
    val device: String = "Android"
)

data class LoginResponse(
    val ok: Boolean,
    val data: LoginData?,
    val error: String?
)

data class LoginData(
    val token: String,
    val expires_at: String,
    val temp_password: Boolean,
    val user: User
)

data class User(
    val id: Int,
    val name: String,
    val email: String,
    val role: String,
    val position: String?,
    val photo: String?,
    val status: String?,
    val bio: String?
)

data class Task(
    val id: Int,
    val title: String,
    val description: String?,
    val status: String,
    val priority: String,
    val color: String?,
    val start_date: String,
    val end_date: String,
    val start_time: String?,
    val end_time: String?,
    val assigned_name: String?,
    val cat_name: String?,
    val cat_icon: String?,
    val cat_color: String?,
    val creator_name: String?,
    val comment_count: Int = 0,
    val hidden: Boolean = false
)

data class TasksResponse(
    val ok: Boolean,
    val data: List<Task>?,
    val error: String?
)

data class TaskDetailResponse(
    val ok: Boolean,
    val data: TaskDetail?,
    val error: String?
)

data class TaskDetail(
    val id: Int,
    val title: String,
    val description: String?,
    val status: String,
    val priority: String,
    val color: String?,
    val start_date: String,
    val end_date: String,
    val start_time: String?,
    val end_time: String?,
    val assigned_name: String?,
    val assigned_position: String?,
    val cat_name: String?,
    val cat_icon: String?,
    val creator_name: String?,
    val collaborators: List<Collaborator>?,
    val hidden: Boolean = false
)

data class Collaborator(
    val id: Int,
    val name: String,
    val photo: String?,
    val position: String?
)

data class Comment(
    val id: Int,
    val comment: String,
    val user_id: Int,
    val author_name: String?,
    val author_position: String?,
    val author_photo: String?,
    val created_at: String,
    val attachments: List<Attachment>?
)

data class Attachment(
    val id: Int,
    val filename: String,
    val filepath: String,
    val url: String?,
    val filetype: String?
)

data class CommentsResponse(
    val ok: Boolean,
    val data: List<Comment>?,
    val error: String?
)

data class Notification(
    val id: Int,
    val message: String,
    val task_id: Int?,
    val task_title: String?,
    val read_at: String?,
    val created_at: String
)

data class NotificationsResponse(
    val ok: Boolean,
    val data: NotificationsData?,
    val error: String?
)

data class NotificationsData(
    val unread: Int,
    val unread_count: Int = unread,
    val items: List<Notification>
)

data class CalendarResponse(
    val ok: Boolean,
    val data: CalendarData?,
    val error: String?
)

data class CalendarData(
    val month: Int,
    val year: Int,
    val by_day: Map<String, List<Task>>
)

data class UsersResponse(
    val ok: Boolean,
    val data: List<User>?,
    val error: String?
)

data class UserResponse(
    val ok: Boolean,
    val data: User?,
    val error: String?
)

data class GenericResponse(
    val ok: Boolean,
    val data: Any?,
    val error: String?
)

data class StatusRequest(
    val status: String
)

data class CommentRequest(
    val task_id: Int,
    val comment: String
)
