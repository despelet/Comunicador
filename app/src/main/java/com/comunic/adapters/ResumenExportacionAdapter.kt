package com.comunic.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.comunic.ItemLista
import com.comunic.R

class ResumenExportacionAdapter(
    private val items: List<ItemLista>
) : RecyclerView.Adapter<ResumenExportacionAdapter.ViewHolder>() {

    inner class ViewHolder(view: View)
        : RecyclerView.ViewHolder(view) {

        val imgItem: ImageView =
            view.findViewById(R.id.imgItem)

        val txtNombre: TextView =
            view.findViewById(R.id.txtNombre)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {

        val view = LayoutInflater.from(parent.context)
            .inflate(
                R.layout.exp_item_resumen_exportacion,
                parent,
                false
            )

        return ViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {

        val item = items[position]

        holder.txtNombre.text =
            item.nombre.uppercase()

        Glide.with(holder.itemView.context)
            .load(item.uri)
            .into(holder.imgItem)
    }
}