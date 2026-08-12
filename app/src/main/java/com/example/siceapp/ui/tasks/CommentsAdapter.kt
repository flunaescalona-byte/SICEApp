package com.example.siceapp.ui.tasks

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.util.TypedValue // Importado para corregir las unidades de tamaño de texto
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat // Asegurar el ContextCompat para los colores
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.example.siceapp.R
import com.example.siceapp.api.ApiConfig
import com.example.siceapp.databinding.ItemCommentBinding
import com.example.siceapp.model.Attachment
import com.example.siceapp.model.Comment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

class CommentsAdapter(
    private var comments: MutableList<Comment>,
    private val currentUserId: Int,
    private val isAdmin: Boolean,
    private val lifecycleOwner: LifecycleOwner,
    private val onRefresh: () -> Unit
) : RecyclerView.Adapter<CommentsAdapter.ViewHolder>() {

    private val imageExtensions = listOf("jpg","jpeg","png","gif","webp")
    private val docExtensions   = listOf("doc","docx")
    private val xlsExtensions   = listOf("xls","xlsx")
    private val pptExtensions   = listOf("ppt","pptx")

    inner class ViewHolder(val binding: ItemCommentBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCommentBinding.inflate(
            LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val comment = comments[position]
        val ctx     = holder.binding.root.context

        with(holder.binding) {
            tvAuthor.text   = comment.author_name ?: "Usuario"
            tvPosition.text = comment.author_position ?: ""
            tvComment.text  = comment.comment
            tvDate.text     = formatDate(comment.created_at)

            val canModify = (comment.user_id == currentUserId) || isAdmin
            btnEditComment.visibility   = if (canModify) View.VISIBLE else View.GONE
            btnDeleteComment.visibility = if (canModify) View.VISIBLE else View.GONE

            btnEditComment.setOnClickListener   { showEditDialog(ctx, comment) }
            btnDeleteComment.setOnClickListener { showDeleteDialog(ctx, comment) }

            val attachments = comment.attachments ?: emptyList()
            val images = attachments.filter { getExt(it.url ?: it.filepath) in imageExtensions }
            val files  = attachments.filter { getExt(it.url ?: it.filepath) !in imageExtensions }

            // IMAGES inline
            photosContainer.removeAllViews()
            if (images.isNotEmpty()) {
                scrollPhotos.visibility = View.VISIBLE
                images.forEach { att ->
                    val url = att.url ?: att.filepath
                    val imgView = ImageView(ctx).apply {
                        layoutParams = LinearLayout.LayoutParams(200, 200).apply {
                            setMargins(0, 0, 12, 0)
                        }
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        clipToOutline = true
                        background = ctx.getDrawable(R.drawable.card_bg)
                    }
                    Glide.with(ctx).load(url)
                        .transition(DrawableTransitionOptions.withCrossFade())
                        .placeholder(R.drawable.card_bg).centerCrop().into(imgView)
                    imgView.setOnClickListener { showLightbox(url, ctx) }
                    photosContainer.addView(imgView)
                }
            } else {
                scrollPhotos.visibility = View.GONE
            }

            // FILES as tappable chips
            if (files.isNotEmpty()) {
                tvAttachments.visibility = View.VISIBLE
                tvAttachments.text = ""
                showFileAttachments(ctx, files, holder.binding)
            } else {
                tvAttachments.visibility = View.GONE
                tvAttachments.text = ""
            }
        }
    }

    private fun showFileAttachments(ctx: Context, files: List<Attachment>,
                                    binding: ItemCommentBinding) {
        binding.tvAttachments.text = "📎 Archivos adjuntos:"
        binding.tvAttachments.visibility = View.VISIBLE

        val parent = binding.root as? LinearLayout ?: return

        val toRemove = mutableListOf<View>()
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child.tag == "file_chip") toRemove.add(child)
        }
        toRemove.forEach { parent.removeView(it) }

        files.forEach { att ->
            val ext   = getExt(att.url ?: att.filepath)
            val emoji = getFileEmoji(ext)
            val name  = att.filename.let {
                if (it.length > 30) it.substring(0, 27) + "..." else it
            }

            val chipView = android.widget.TextView(ctx).apply {
                text = "$emoji $name"
                setTextColor(Color.parseColor("#0ea5e9"))
                textSize = 12f
                setPadding(0, 8, 0, 8)
                tag = "file_chip"
                setOnClickListener { handleFileOpen(ctx, att) }
            }
            parent.addView(chipView)
        }
    }

    private fun handleFileOpen(ctx: Context, att: Attachment) {
        val url = att.url ?: att.filepath
        val ext = getExt(url)

        when {
            ext == "pdf" -> openInBrowser(ctx, url)
            ext in docExtensions || ext in xlsExtensions || ext in pptExtensions -> {
                showFileOptions(ctx, att, url)
            }
            else -> openInBrowser(ctx, url)
        }
    }

    private fun showFileOptions(ctx: Context, att: Attachment, url: String) {
        val ext   = getExt(url)
        val emoji = getFileEmoji(ext)

        AlertDialog.Builder(ctx)
            .setTitle("$emoji ${att.filename}")
            .setItems(arrayOf(
                "🌐 Abrir en Google Docs (online)",
                "📥 Descargar archivo"
            )) { _, which ->
                when (which) {
                    0 -> openInGoogleDocs(ctx, url)
                    1 -> downloadFile(ctx, att, url)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun openInGoogleDocs(ctx: Context, fileUrl: String) {
        val viewerUrl = "https://docs.google.com/viewer?url=${Uri.encode(fileUrl)}"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(viewerUrl))
        try {
            ctx.startActivity(intent)
        } catch (e: Exception) {
            openInBrowser(ctx, fileUrl)
        }
    }

    private fun openInBrowser(ctx: Context, url: String) {
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(ctx, "No se pudo abrir el archivo", Toast.LENGTH_SHORT).show()
        }
    }

    private fun downloadFile(ctx: Context, att: Attachment, url: String) {
        Toast.makeText(ctx, "📥 Descargando ${att.filename}...", Toast.LENGTH_SHORT).show()
        lifecycleOwner.lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val client = OkHttpClient.Builder()
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(60, TimeUnit.SECONDS)
                        .build()
                    val request = Request.Builder()
                        .url(url)
                        .addHeader("X-Token", ApiConfig.getToken())
                        .build()
                    val response = client.newCall(request).execute()
                    if (!response.isSuccessful) return@withContext null
                    val bytes = response.body?.bytes() ?: return@withContext null

                    val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_DOWNLOADS)
                    val siceDir = File(downloadsDir, "SICE")
                    if (!siceDir.exists()) siceDir.mkdirs()
                    val file = File(siceDir, att.filename)
                    file.writeBytes(bytes)
                    file
                }

                if (result != null) {
                    Toast.makeText(ctx, "✅ Guardado en Descargas/SICE: ${att.filename}",
                        Toast.LENGTH_LONG).show()

                    val ext  = getExt(att.filename)
                    val mime = getMimeType(ext)
                    val uri  = androidx.core.content.FileProvider.getUriForFile(
                        ctx, "${ctx.packageName}.provider", result)
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, mime)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    try {
                        ctx.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(ctx, "Archivo guardado en Descargas", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(ctx, "Error al descargar", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(ctx, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showLightbox(imageUrl: String, ctx: Context) {
        val dialog = Dialog(ctx, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_image)
        dialog.setCancelable(true)
        val imgFull  = dialog.findViewById<ImageView>(R.id.imgFull)
        val btnClose = dialog.findViewById<ImageButton>(R.id.btnClose)
        Glide.with(ctx).load(imageUrl)
            .transition(DrawableTransitionOptions.withCrossFade()).into(imgFull)
        var isZoomed = false
        imgFull.setOnClickListener {
            isZoomed = !isZoomed
            imgFull.animate().scaleX(if (isZoomed) 1.8f else 1f)
                .scaleY(if (isZoomed) 1.8f else 1f).setDuration(200).start()
        }
        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showEditDialog(ctx: Context, comment: Comment) {
        // Calcular los píxeles basados en la densidad de pantalla para el padding
        val density = ctx.resources.displayMetrics.density
        val paddingHorizontal = (32 * density).toInt()
        val paddingVertical = (24 * density).toInt()

        val editText = android.widget.EditText(ctx).apply {
            setText(comment.comment)
            setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))

            // CORRECCIÓN 1: Forzar explícitamente a que 14f sea interpretado en unidades SP
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)

            // CORRECCIÓN 2: Asignar los valores calculados dinámicamente en px basados en DP
            setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical)

            setBackgroundColor(ContextCompat.getColor(ctx, R.color.bg_card))
            minLines = 3
        }

        AlertDialog.Builder(ctx)
            .setTitle("✏️ Editar comentario")
            .setView(editText)
            .setPositiveButton("Guardar") { _, _ ->
                val newText = editText.text.toString().trim()
                if (newText.isEmpty()) {
                    Toast.makeText(ctx, "No puede estar vacío", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                lifecycleOwner.lifecycleScope.launch {
                    try {
                        val response = ApiConfig.service.editComment(
                            path = "comments/${comment.id}",
                            body = mapOf("comment" to newText))
                        if (response.isSuccessful && response.body()?.ok == true) {
                            Toast.makeText(ctx, "✅ Comentario actualizado", Toast.LENGTH_SHORT).show()
                            onRefresh()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(ctx, "Error de conexión", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun showDeleteDialog(ctx: Context, comment: Comment) {
        AlertDialog.Builder(ctx)
            .setTitle("🗑️ Eliminar comentario")
            .setMessage("¿Estás seguro? No se puede deshacer.")
            .setPositiveButton("Eliminar") { _, _ ->
                lifecycleOwner.lifecycleScope.launch {
                    try {
                        val response = ApiConfig.service.deleteComment(
                            path = "comments/${comment.id}")
                        if (response.isSuccessful && response.body()?.ok == true) {
                            Toast.makeText(ctx, "✅ Comentario eliminado", Toast.LENGTH_SHORT).show()
                            onRefresh()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(ctx, "Error de conexión", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null).show()
    }

    override fun getItemCount() = comments.size

    private fun getExt(url: String): String = try {
        url.substringAfterLast(".").lowercase().split("?")[0]
    } catch (e: Exception) { "" }

    private fun getFileEmoji(ext: String): String = when(ext) {
        "pdf"            -> "📄"
        "doc","docx"     -> "📝"
        "xls","xlsx"     -> "📊"
        "ppt","pptx"     -> "📑"
        "txt"            -> "📃"
        else             -> "📎"
    }

    private fun getMimeType(ext: String): String = when(ext) {
        "pdf"  -> "application/pdf"
        "doc"  -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xls"  -> "application/vnd.ms-excel"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "ppt"  -> "application/vnd.ms-powerpoint"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "txt"  -> "text/plain"
        else   -> "*/*"
    }

    private fun formatDate(dateStr: String): String = try {
        val parts = dateStr.split(" ")
        val d = parts[0].split("-")
        "${d[2]}-${d[1]}-${d[0]} ${parts[1].substring(0,5)}"
    } catch (e: Exception) { dateStr }
}