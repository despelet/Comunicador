package com.comunic.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.comunic.ItemLista
import com.comunic.R
import com.comunic.data.entity.CategoryEntity


class ExportListasAdapter(
    private val context: Context,
    private val items: List<CategoryEntity>,
    private val cantidades: Map<String, Int>,
    private val previews: Map<String, List<ItemLista>>,
    private val checked: BooleanArray,
    private val onChecked: (Int, Boolean) -> Unit
) : RecyclerView.Adapter<ExportListasAdapter.ViewHolder>() {

    private val expandedStates = mutableSetOf<String>()

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtNombre: TextView = view.findViewById(R.id.txtNombre)
        val txtCantidad: TextView = view.findViewById(R.id.txtCantidad)
        val checkSeleccion: CheckBox = view.findViewById(R.id.checkSeleccion)
        val imgExpandir: ImageView = view.findViewById(R.id.imgExpandir)
        val recyclerPreview: RecyclerView = view.findViewById(R.id.recyclerPreview)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.exp_item_export_lista, parent, false)

        return ViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        val item = items[position]

        holder.txtNombre.text = item.name

        val cantidad = cantidades[item.categoryId] ?: 0
        holder.txtCantidad.text = "$cantidad elementos"

        holder.checkSeleccion.setOnCheckedChangeListener(null)
        holder.checkSeleccion.isChecked = checked[position]

        holder.checkSeleccion.setOnCheckedChangeListener { _, isChecked ->
            onChecked(position, isChecked)
        }

        holder.itemView.setOnClickListener {
            val nuevoEstado = !checked[position]
            checked[position] = nuevoEstado
            holder.checkSeleccion.isChecked = nuevoEstado
        }

        val expanded = expandedStates.contains(item.categoryId)

        holder.recyclerPreview.visibility =
            if (expanded) View.VISIBLE else View.GONE

        holder.imgExpandir.animate()
            .rotation(if (expanded) 180f else 0f)
            .setDuration(180)
            .start()

        holder.imgExpandir.isClickable = true
        holder.imgExpandir.isFocusable = true

        holder.imgExpandir.setOnClickListener {

            if (expanded) {
                expandedStates.remove(item.categoryId)
            } else {
                expandedStates.add(item.categoryId)
            }

            notifyItemChanged(position)
        }

        val previewItems =
            previews[item.categoryId] ?: emptyList()

        if (holder.recyclerPreview.layoutManager == null) {
            holder.recyclerPreview.layoutManager =
                GridLayoutManager(context, 3)
        }

        holder.recyclerPreview.adapter =
            ResumenExportacionAdapter(previewItems)
    }
}