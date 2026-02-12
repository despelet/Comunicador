package com.comunic

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.VideoView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.Transition
import com.comunic.R

class CarruselAdapter(
//    private val uris: List<Uri>,
//    private val nombres: List<String>,
//    private val esImagenLista: List<Boolean>,
//    private val onClick: (Uri, String) -> Unit
//) : RecyclerView.Adapter<CarruselAdapter.CarruselViewHolder>() {

    private val uris: List<Uri>,
    private val labels: List<String>,     // lo que se muestra
    private val ids: List<String>,        // lo que se devuelve al click
    private val esImagenLista: List<Boolean>,
    private val onClick: (Uri, String) -> Unit // (uri, id)
    ) : RecyclerView.Adapter<CarruselAdapter.CarruselViewHolder>() {


    inner class CarruselViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.carruselImageView)
        val videoView: VideoView = itemView.findViewById(R.id.carruselVideoView)
        val nameText: TextView = itemView.findViewById(R.id.carruselNameText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CarruselViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_carrusel, parent, false)
        view.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        return CarruselViewHolder(view)
    }

    override fun onBindViewHolder(holder: CarruselViewHolder, _position: Int) {
        val realPos = holder.bindingAdapterPosition
        if (realPos == RecyclerView.NO_POSITION ||
            realPos !in uris.indices ||
           // realPos !in nombres.indices ||
            realPos !in ids.indices ||
            realPos !in esImagenLista.indices
        ) return

        val uri = uris[realPos]
        val label = labels[realPos]
        holder.nameText.text = label
        //val nombre = nombres[realPos]
        val esImagen = esImagenLista[realPos]

      //  holder.nameText.text = nombre
        holder.nameText.text = label


        if (esImagen) {
            holder.videoView.visibility = View.GONE
            holder.imageView.visibility = View.VISIBLE
            Glide.with(holder.itemView.context)
                .load(uri)
                .into(holder.imageView)
        } else {
            holder.imageView.visibility = View.GONE
            holder.videoView.visibility = View.VISIBLE
            holder.videoView.setVideoURI(uri)

            holder.videoView.setOnPreparedListener { mp ->
                mp.isLooping = true
                mp.setVolume(0f, 0f)
                mp.setVideoScalingMode(
                    android.media.MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                )
                holder.videoView.start()
            }
        }

        holder.itemView.setOnClickListener {
            val clickPos = holder.bindingAdapterPosition
            if (clickPos != RecyclerView.NO_POSITION &&
                clickPos in uris.indices &&
               // clickPos in nombres.indices
                clickPos in ids.indices
            ) {
                //onClick(uris[clickPos], nombres[clickPos])
                onClick(uris[clickPos], ids[clickPos])
            }
        }
    }

    override fun getItemCount(): Int =
       // minOf(uris.size, nombres.size, esImagenLista.size)
    minOf(uris.size, labels.size, esImagenLista.size)

}

