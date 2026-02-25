package com.comunic;


// adaptador de categorias para el fragment listas

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.recyclerview.widget.RecyclerView

import com.comunic.data.entity.CategoryEntity;
import com.comunic.databinding.ItemCategoriaBinding
//
//class CategoriasAdapter(
//    private val items: kotlin.collections.List<CategoryEntity>,
//    private val onClick: (CategoryEntity) -> Unit
//) : RecyclerView.Adapter<CategoriasAdapter.VH>() {
//
//    inner class VH(val binding: ItemCategoriaBinding) : RecyclerView.ViewHolder(binding.root)
//
//    override fun onCreateViewHolder(parent:ViewGroup, viewType: Int): VH {
//        val inflater = LayoutInflater.from(parent.context)
//        val binding = ItemCategoriaBinding.inflate(inflater, parent, false)
//        return VH(binding)
//    }
//
//    override fun onBindViewHolder(holder: VH, position: Int) {
//        val item = items[position]
//        holder.binding.txtCategoria.text = item.name
//        holder.binding.root.setOnClickListener { onClick(item) }
//    }
//
//    override fun getItemCount() = items.size
//}
