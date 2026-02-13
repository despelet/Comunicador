package com.comunic

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.comunic.data.entity.CategoryEntity



class CategoriasQuickAdapter(
    private var items: List<CategoryEntity>,
    private val onClick: (CategoryEntity) -> Unit
) : RecyclerView.Adapter<CategoriasQuickAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val txt: TextView = view.findViewById(R.id.txtCategoria)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_categoria_quick, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val cat = items[position]
        holder.txt.text = cat.name
        holder.itemView.setOnClickListener { onClick(cat) }
    }

    override fun getItemCount() = items.size

    fun submitList(newItems: List<CategoryEntity>) {
        items = newItems
        notifyDataSetChanged()
    }
}