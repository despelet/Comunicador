package com.comunic.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.comunic.ItemLista
import com.comunic.R

class PickItemsAdapter(
    private val items: List<ItemLista>,
    private val checked: BooleanArray,
    private val onCheck: (Int, Boolean) -> Unit
) : RecyclerView.Adapter<PickItemsAdapter.VH>() {


    inner class VH(val view: View) : RecyclerView.ViewHolder(view) {
        val image = view.findViewById<ImageView>(R.id.imgItem)
        val checkBox = view.findViewById<CheckBox>(R.id.check)
        val name = view.findViewById<TextView>(R.id.name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pick, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {

        val item = items[position]

        holder.name.text = item.nombre

        holder.checkBox.setOnCheckedChangeListener(null)
        holder.checkBox.isChecked = checked[position]

        holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
            checked[position] = isChecked
            onCheck(position, isChecked)
        }

        // 👇 IMAGEN
        when {
            item.esImagen -> {
                holder.image.setImageURI(item.uri)
            }
            else -> {
                //holder.image.setImageResource(R.drawable.ic_video_placeholder)
            }
        }
    }
}