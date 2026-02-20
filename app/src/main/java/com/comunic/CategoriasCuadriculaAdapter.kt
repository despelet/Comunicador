package com.comunic

import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import java.io.File
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions

class CategoriasCuadriculaAdapter (
    private var items: List<CategoryPreview>,
    private val onClick: (CategoryPreview) -> Unit
    ) : RecyclerView.Adapter<CategoriasCuadriculaAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val card: View = view.findViewById(R.id.cardCategoria)
        val nombre: TextView = view.findViewById(R.id.txtNombreCategoria)
        val img1: ImageView = view.findViewById(R.id.img1)
        val img2: ImageView = view.findViewById(R.id.img2)
        val img3: ImageView = view.findViewById(R.id.img3)
        val img4: ImageView = view.findViewById(R.id.img4)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.cuadricula_categoria, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.nombre.text = item.name
        holder.card.setOnClickListener { onClick(item) }

        val imgs = listOf(holder.img1, holder.img2, holder.img3, holder.img4)

        // Limpieza/placeholder básico
        //imgs.forEach { it.setImageDrawable(null) }
        imgs.forEach { it.setImageResource(R.drawable.ic_lista_placeholder) }

        // Cargar hasta 4
        item.previewUris.forEachIndexed { i, uri ->
            if (i < 4) loadPreviewInto(imgs[i], uri)
        }
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

/*private fun loadPreviewInto(imageView: ImageView, uriOrRes: String) {
    val ctx = imageView.context

    Log.d("PREVIEW_DEBUG", "---- NUEVA CARGA ----")
    Log.d("PREVIEW_DEBUG", "uriOrRes recibido: $uriOrRes")

//        if (uriOrRes.isBlank()) {
//            Log.d("PREVIEW_DEBUG", "URI vacía → no se carga nada")
//            return
//        }
    if (uriOrRes.isBlank()) {
        Log.d("PREVIEW_DEBUG", "URI vacía → placeholder")
        imageView.setImageResource(R.drawable.ic_lista_placeholder) // ✅ tu placeholder
        return
    }


    // ✅ Caso 1: path absoluto (ej /data/user/0/...)
    if (uriOrRes.startsWith("/")) {
        Log.d("PREVIEW_DEBUG", "Detectado path absoluto")
        val file = File(uriOrRes)

        Log.d("PREVIEW_DEBUG", "Existe archivo: ${file.exists()}")
        Log.d("PREVIEW_DEBUG", "Path real: ${file.absolutePath}")

        Picasso.get()
            .load(file)
            .fit()
            .centerCrop()
            .into(imageView)

        Log.d("PREVIEW_DEBUG", "Cargando como File")
        return
    }

    // Caso 2: drawable por nombre
    val cleaned = uriOrRes
        .removePrefix("@drawable/")
        .removePrefix("drawable/")

    val isProbablyDrawableName =
        !uriOrRes.contains("://") && cleaned.matches(Regex("^[a-z0-9_]+$"))

    Log.d("PREVIEW_DEBUG", "cleaned: $cleaned")
    Log.d("PREVIEW_DEBUG", "isDrawableName: $isProbablyDrawableName")

    if (isProbablyDrawableName) {
        val resId = ctx.resources.getIdentifier(cleaned, "drawable", ctx.packageName)

        Log.d("PREVIEW_DEBUG", "resId encontrado: $resId")

        if (resId != 0) {
            Picasso.get()
                .load(resId)
                .fit()
                .centerCrop()
                .into(imageView)

            Log.d("PREVIEW_DEBUG", "Cargando como drawable")
            return
        } else {
            Log.d("PREVIEW_DEBUG", "Drawable no encontrado")
        }
    }

    // Caso 3: URI normal
    try {
//            val uri = Uri.parse(uriOrRes)
//            Log.d("PREVIEW_DEBUG", "Intentando cargar como URI normal: $uri")
//
//            Picasso.get()
//                .load(uri)
//                .fit()
//                .centerCrop()
//                .into(imageView)
//
//            Log.d("PREVIEW_DEBUG", "Cargando como Uri.parse()")
        if (uriOrRes.startsWith("file://")) {
            Picasso.get()
                .load(Uri.parse(uriOrRes))
                .fit()
                .centerCrop()
                .into(imageView)
            return
        }
    } catch (e: Exception) {
        Log.e("PREVIEW_DEBUG", "Error al cargar imagen: ${e.message}")
        e.printStackTrace()
    }
}*/