package com.comunic.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.CheckedTextView
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.comunic.R
import com.comunic.data.entity.CategoryEntity

class SimpleListCheckAdapter(
    private val items: List<CategoryEntity>,
    private val checked: BooleanArray
) : RecyclerView.Adapter<SimpleListCheckAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {

        val check = view.findViewById<CheckBox>(R.id.check)
        val name = view.findViewById<TextView>(R.id.name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_list_check, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {

        val item = items[position]

        holder.name.text = item.name
        holder.check.isChecked = checked[position]

        holder.check.setOnClickListener {
            checked[position] = holder.check.isChecked
        }


    }

    override fun getItemCount() = items.size
}