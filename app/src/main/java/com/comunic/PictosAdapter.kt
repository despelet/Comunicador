package com.comunic

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import java.io.File

data class PictoUi(
    val pictogramId: String,
    val label: String,
    val imagePath: String // guardamos absolutePath en DB
)

class PictosAdapter(
    private val pictos: List<PictoUi>,
    private val onClick: (PictoUi) -> Unit,
    private val onLongClick: ((PictoUi) -> Unit)? = null
) : RecyclerView.Adapter<PictosAdapter.PictoViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PictoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_picto, parent, false)
        return PictoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PictoViewHolder, position: Int) {
        val item = pictos[position]

        holder.txt.text = item.label

        // Cargar imagen desde ruta local (absolutePath)
        // Picasso acepta File, así evitamos líos con "file://"
        Picasso.get()
            .load(File(item.imagePath))
            .into(holder.img, object : com.squareup.picasso.Callback {
                override fun onSuccess() {}
                override fun onError(e: Exception?) {
                    Log.e("PictosAdapter", "Error loading picto: ${e?.message}")
                }
            })

        holder.itemView.setOnClickListener { onClick(item) }
        holder.itemView.setOnLongClickListener {
            onLongClick?.invoke(item)
            true
        }
    }

    override fun getItemCount(): Int = pictos.size

    class PictoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val img: ImageView = view.findViewById(R.id.imgPicto)
        val txt: TextView = view.findViewById(R.id.txtPicto)
    }
}
