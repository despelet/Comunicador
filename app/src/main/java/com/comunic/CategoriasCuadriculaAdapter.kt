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
        imgs.forEach { it.setImageDrawable(null) }

        // Cargar hasta 4
//        item.previewUris.forEachIndexed { i, uri ->
//            if (i < 4) {
//                //Picasso.get().load(uri).fit().centerCrop().into(imgs[i])
//                val u = Uri.parse(uri)
//                Picasso.get()
//                    .load(u)
//                    .fit()
//                    .centerCrop()
//                    .noFade()
//                    .into(imgs[i])
//            }
//            Log.d("CAT_PREVIEW", "uri=$uri")
//        }
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

        Log.d("PREVIEW_DEBUG", "---- NUEVA CARGA ----")
        Log.d("PREVIEW_DEBUG", "uriOrRes recibido: $uriOrRes")

        if (uriOrRes.isBlank()) {
            Log.d("PREVIEW_DEBUG", "URI vacía → no se carga nada")
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
            val uri = Uri.parse(uriOrRes)
            Log.d("PREVIEW_DEBUG", "Intentando cargar como URI normal: $uri")

            Picasso.get()
                .load(uri)
                .fit()
                .centerCrop()
                .into(imageView)

            Log.d("PREVIEW_DEBUG", "Cargando como Uri.parse()")
        } catch (e: Exception) {
            Log.e("PREVIEW_DEBUG", "Error al cargar imagen: ${e.message}")
            e.printStackTrace()
        }
    }
}