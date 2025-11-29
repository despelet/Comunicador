package com.comunic

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

import com.squareup.picasso.Picasso
class SugerenciasAdapter(
    private val sugerenciasUris: List<Uri>,
    private val sugerenciasNombres: List<String>,
    private val onClick: (Uri, String) -> Unit
) : RecyclerView.Adapter<SugerenciasAdapter.SugerenciaViewHolder>() {

    inner class SugerenciaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val imageView1: ImageView = view.findViewById(R.id.imageView1)
        private val imageView2: ImageView = view.findViewById(R.id.imageView2)
        private val imageView3: ImageView = view.findViewById(R.id.imageView3)

        fun bind(position: Int) {
            val index1 = position * 3
            val index2 = index1 + 1
            val index3 = index1 + 2

            bindImage(imageView1, index1)
            bindImage(imageView2, index2)
            bindImage(imageView3, index3)
        }

        private fun bindImage(imageView: ImageView, index: Int) {
            if (index < sugerenciasUris.size) {
                imageView.visibility = View.VISIBLE
                Picasso.get().load(sugerenciasUris[index]).into(imageView)
                imageView.setOnClickListener {
                    onClick(sugerenciasUris[index], sugerenciasNombres[index])
                }
            } else {
                imageView.visibility = View.GONE // Ocultar si no hay más imágenes
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SugerenciaViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sugerencia, parent, false)
        return SugerenciaViewHolder(view)
    }

    override fun onBindViewHolder(holder: SugerenciaViewHolder, position: Int) {
        holder.bind(position)
    }

    override fun getItemCount(): Int = (sugerenciasUris.size + 2) / 3 // Calcular el número de páginas
}
