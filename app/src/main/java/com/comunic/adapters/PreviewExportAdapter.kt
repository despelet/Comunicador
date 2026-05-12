package com.comunic.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.comunic.ItemLista
import com.comunic.R

class PreviewExportAdapter(
    private val items: List<ItemLista>
) : RecyclerView.Adapter<PreviewExportAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) :
        RecyclerView.ViewHolder(view) {

        val imgItem: ImageView =
            view.findViewById(R.id.imgItem)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {

        val view = LayoutInflater.from(parent.context)
            .inflate(
                R.layout.item_preview_export,
                parent,
                false
            )

        return ViewHolder(view)
    }

    override fun getItemCount(): Int =
        items.size

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {

        Glide.with(holder.itemView.context)
            .load(items[position].uri)
            .into(holder.imgItem)
    }
}