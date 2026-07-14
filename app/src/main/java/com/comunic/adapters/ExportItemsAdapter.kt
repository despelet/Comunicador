package com.comunic.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.comunic.ItemLista
import com.comunic.R

class ExportItemsAdapter(
    private val items: List<ItemLista>,
    private val checked: BooleanArray,
    private val onChecked: (Int, Boolean) -> Unit
) : RecyclerView.Adapter<ExportItemsAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        val imgItem: ImageView = view.findViewById(R.id.imgItem)
        val txtNombre: TextView = view.findViewById(R.id.txtNombre)
        val checkSeleccion: CheckBox = view.findViewById(R.id.checkSeleccion)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {

        val view = LayoutInflater.from(parent.context).inflate(R.layout.exp_item_export, parent, false)

        return ViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {

        val item = items[position]

        holder.txtNombre.text = item.nombre

        holder.checkSeleccion.setOnCheckedChangeListener(null)

        holder.checkSeleccion.isChecked =
            checked[position]

        holder.checkSeleccion.setOnCheckedChangeListener { _, isChecked ->

            onChecked(position, isChecked)
        }

        Glide.with(holder.itemView.context)
            .load(item.uri)
            .into(holder.imgItem)

        holder.itemView.setOnClickListener {

            val nuevoEstado = !checked[position]

            checked[position] = nuevoEstado

            holder.checkSeleccion.isChecked =
                nuevoEstado
        }
    }
}