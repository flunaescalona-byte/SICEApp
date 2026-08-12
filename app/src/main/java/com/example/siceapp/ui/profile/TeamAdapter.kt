package com.example.siceapp.ui.profile

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.example.siceapp.api.ApiConfig
import com.example.siceapp.databinding.ItemTeamMemberBinding
import com.example.siceapp.model.User
import kotlinx.coroutines.launch

class TeamAdapter(
    private var members: List<User>,
    private val isAdmin: Boolean,
    private val lifecycleOwner: LifecycleOwner,
    private val onStatusChanged: () -> Unit,
    private val onEditProfile: ((User) -> Unit)? = null
) : RecyclerView.Adapter<TeamAdapter.ViewHolder>() {

    private val statusLabels = mapOf(
        "available"  to Pair("🟢 Disponible",          "#10b981"),
        "deployed"   to Pair("🔴 Desplegado",           "#ef4444"),
        "off_duty"   to Pair("🟡 Saliente de Servicio", "#f59e0b"),
        "commission" to Pair("🟣 En Comisión",          "#8b5cf6")
    )

    inner class ViewHolder(val binding: ItemTeamMemberBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTeamMemberBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val member = members[position]
        val ctx    = holder.binding.root.context

        with(holder.binding) {
            // Grado, Nombre, Cargo, Bio - separados
            val parts = member.name.split(". ", limit = 2)
            if (parts.size == 2) {
                tvGrade.text = parts[0] + "."
                tvName.text  = parts[1]
            } else {
                tvGrade.text = ""
                tvName.text  = member.name
            }
            tvPosition.text = member.position ?: ""
            if (!member.bio.isNullOrEmpty()) {
                tvBio.visibility = View.VISIBLE
                tvBio.text = member.bio
            } else {
                tvBio.visibility = View.GONE
            }

            val status = member.status ?: "available"
            val (label, color) = statusLabels[status] ?: Pair("🟢 Disponible", "#10b981")
            tvStatus.text = label
            tvStatus.setTextColor(Color.parseColor(color))

            // Load photo with lightbox
            if (!member.photo.isNullOrEmpty()) {
                Glide.with(ctx)
                    .load(member.photo)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .circleCrop()
                    .into(imgPhoto)
            } else {
                imgPhoto.setImageResource(com.example.siceapp.R.mipmap.ic_launcher)
            }
            imgPhoto.setOnClickListener {
                showPhotoLightbox(ctx, member.photo, member.name)
            }

            // Show edit button for admin
            if (isAdmin) {
                btnChangeStatus.visibility = View.VISIBLE
                btnChangeStatus.setOnClickListener {
                    showStatusDialog(ctx, member)
                }
                // Edit profile button
                btnEditMember.visibility = View.VISIBLE
                btnEditMember.setOnClickListener {
                    onEditProfile?.invoke(member)
                }
            } else {
                btnChangeStatus.visibility = View.GONE
                btnEditMember.visibility = View.GONE
            }
        }
    }

    override fun getItemCount() = members.size

    private fun showPhotoLightbox(ctx: android.content.Context, photoUrl: String?, name: String) {
        if (photoUrl.isNullOrEmpty()) return
        val dialog = android.app.Dialog(ctx, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setContentView(android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(android.graphics.Color.parseColor("#E0000000"))
            val img = android.widget.ImageView(ctx).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    0, 1f)
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                com.bumptech.glide.Glide.with(ctx).load(photoUrl).into(this)
            }
            val btn = android.widget.Button(ctx).apply {
                text = "✕ Cerrar"
                setTextColor(android.graphics.Color.WHITE)
                setBackgroundColor(android.graphics.Color.parseColor("#880a1628"))
                setOnClickListener { dialog.dismiss() }
            }
            addView(img)
            addView(btn)
        })
        dialog.show()
    }

    fun updateMembers(newMembers: List<User>) {
        members = newMembers
        notifyDataSetChanged()
    }

    private fun showStatusDialog(ctx: android.content.Context, member: User) {
        val options = arrayOf(
            "🟢 Disponible",
            "🔴 Desplegado",
            "🟡 Saliente de Servicio",
            "🟣 En Comisión"
        )
        val statusValues = arrayOf("available", "deployed", "off_duty", "commission")

        AlertDialog.Builder(ctx)
            .setTitle("Estado de ${member.name}")
            .setItems(options) { _, which ->
                val newStatus = statusValues[which]
                lifecycleOwner.lifecycleScope.launch {
                    try {
                        val response = ApiConfig.service.updateStatus(
                            body = mapOf(
                                "status"  to newStatus,
                                "user_id" to member.id.toString()
                            )
                        )
                        if (response.isSuccessful && response.body()?.ok == true) {
                            Toast.makeText(ctx, "✅ Estado actualizado", Toast.LENGTH_SHORT).show()
                            onStatusChanged()
                        } else {
                            Toast.makeText(ctx, "Error al actualizar", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(ctx, "Error de conexión", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
