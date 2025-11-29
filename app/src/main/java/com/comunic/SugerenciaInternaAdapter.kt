//NO SE USA
//
// package com.comunic
//
//import android.net.Uri
//import android.view.LayoutInflater
//import android.view.View
//import android.view.ViewGroup
//import android.widget.ImageView
//import androidx.recyclerview.widget.RecyclerView
//import com.squareup.picasso.Picasso
//
//class SugerenciaInternaAdapter(
//    private val sugerenciasUris: List<Uri>,
//    private val onClick: (Uri) -> Unit
//) : RecyclerView.Adapter<SugerenciaInternaAdapter.ViewHolder>() {
//
//    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
//        private val imageView: ImageView = view.findViewById(R.id.imageView)
//
//        fun bind(uri: Uri) {
//            Picasso.get().load(uri).into(imageView)
//            imageView.setOnClickListener { onClick(uri) }
//        }
//    }
//
//    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
//        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_image, parent, false)
//        return ViewHolder(view)
//    }
//
//    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
//        holder.bind(sugerenciasUris[position])
//    }
//
//    override fun getItemCount() = sugerenciasUris.size
//}
