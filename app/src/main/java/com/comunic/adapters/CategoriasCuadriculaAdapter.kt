package com.comunic.adapters

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import java.io.File
import com.bumptech.glide.Glide
import com.comunic.CategoryPreview
import com.comunic.R

class CategoriasCuadriculaAdapter (
    private var items: List<CategoryPreview>,
    private val onClick: (CategoryPreview) -> Unit,
    private val onOptionsClick: (CategoryPreview) -> Unit,
    private val onEnableClick: (CategoryPreview) -> Unit,    // ⬇descarga
    private val onLongClick: (CategoryPreview) -> Unit
    ) : RecyclerView.Adapter<CategoriasCuadriculaAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val card: View = view.findViewById(R.id.cardCategoria)
        val nombre: TextView = view.findViewById(R.id.txtNombreCategoria)
        val img1: ImageView = view.findViewById(R.id.img1)
        val img2: ImageView = view.findViewById(R.id.img2)
        val img3: ImageView = view.findViewById(R.id.img3)
        val img4: ImageView = view.findViewById(R.id.img4)
        val btnOpciones: ImageButton = view.findViewById(R.id.btnOpcionesCategoria)
        val btnHabilitar: ImageButton = view.findViewById(R.id.btnHabilitarPack)
    }



    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.cuadricula_categoria, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.nombre.text = item.name

        val disabled = item.isSystem && !item.packEnabled

        holder.card.setOnClickListener { onClick(item) }

        val imgs = listOf(holder.img1, holder.img2, holder.img3, holder.img4)

        // Limpieza/placeholder básico
        //imgs.forEach { it.setImageDrawable(null) }
        imgs.forEach { it.setImageResource(R.drawable.ic_lista_placeholder) }

        // Cargar hasta 4
        item.previewUris.forEachIndexed { i, uri ->
            if (i < 4) loadPreviewInto(imgs[i], uri)
        }

        //  Alternar botones overlay
        holder.btnOpciones.visibility = if (disabled) View.GONE else View.VISIBLE
        holder.btnHabilitar.visibility = if (disabled) View.VISIBLE else View.GONE

        //  Acciones
        holder.btnOpciones.setOnClickListener { onOptionsClick(item) }     // ⋮
        holder.btnHabilitar.setOnClickListener { onEnableClick(item) }     // ⬇️ habilitar
        // longpress en toda la tarjeta (además del botón)
        holder.card.setOnLongClickListener {
            onLongClick(item)
            true
        }

        // Si es pack del sistema pero no está habilitado, mostrarlo atenuado (alpha 0.45)

        holder.itemView.alpha = if (disabled) 0.45f else 1f
    }

    override fun getItemCount() = items.size

    fun submitList(newItems: List<CategoryPreview>) {
        items = newItems
        notifyDataSetChanged()
    }


    private fun loadPreviewInto(imageView: ImageView, uriOrRes: String) {
        val ctx = imageView.context

        if (uriOrRes.isBlank()) {
            imageView.setImageDrawable(null)
            return
        }

        // ✅ 1) Si es path absoluto (/data/...)
        if (uriOrRes.startsWith("/")) {
            val file = File(uriOrRes)
            if (!file.exists()) {
                imageView.setImageDrawable(null)
                return
            }

            // Detectar si es video por extensión
            val ext = file.extension.lowercase()
            val esVideo = ext in listOf("mp4","mkv","avi","mov","webm")

            if (esVideo) {
                Glide.with(ctx)
                    .asBitmap()
                    .load(file)
                    .frame(1000)
                    .centerCrop()
                    .into(imageView)
            } else {
                Picasso.get()
                    .load(file)
                    .fit()
                    .centerCrop()
                    .into(imageView)
            }
            return
        }

        // ✅ 2) Si parece drawable por nombre
        val cleaned = uriOrRes
            .removePrefix("@drawable/")
            .removePrefix("drawable/")

        val isProbablyDrawableName =
            !uriOrRes.contains("://") && cleaned.matches(Regex("^[a-z0-9_]+$"))

        if (isProbablyDrawableName) {
            val resId = ctx.resources.getIdentifier(cleaned, "drawable", ctx.packageName)
            if (resId != 0) {
                Picasso.get()
                    .load(resId)
                    .fit()
                    .centerCrop()
                    .into(imageView)
                return
            }
        }

        // ✅ 3) URI normal: content:// o file://
        val uri = try { Uri.parse(uriOrRes) } catch (e: Exception) { null }
        if (uri == null) {
            imageView.setImageDrawable(null)
            return
        }

        // Detectar video por extensión del string (simple y suficiente)
        val lower = uriOrRes.lowercase()
        val esVideo = lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".avi")
                || lower.endsWith(".mov") || lower.endsWith(".webm")

        if (esVideo) {
            Glide.with(ctx)
                .asBitmap()
                .load(uri)
                .frame(1000)
                .centerCrop()
                .into(imageView)
        } else {
            Picasso.get()
                .load(uri)
                .fit()
                .centerCrop()
                .into(imageView)
        }
    }
}

