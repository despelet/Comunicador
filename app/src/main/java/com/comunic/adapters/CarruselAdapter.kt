package com.comunic.adapters

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
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.comunic.CropVideoView
import com.comunic.R

class CarruselAdapter(
    private val uris: List<Uri>,
    private val labels: List<String>,     // lo que se muestra
    private val ids: List<String>,        // lo que se devuelve al click
    private val esImagenLista: List<Boolean>,
    private val onClick: (Uri, String) -> Unit // (uri, id)
    ) : RecyclerView.Adapter<CarruselAdapter.CarruselViewHolder>() {


    inner class CarruselViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.carruselImageView)
        val videoView: CropVideoView = itemView.findViewById(R.id.carruselVideoView)
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
     //   holder.nameText.text = label
        // =========================================================
        // LIMPIAR ESTADO ANTERIOR DEL VIEW HOLDER
        // =========================================================

        holder.videoView.stopPlayback()
        holder.videoView.setOnPreparedListener(null)
        holder.videoView.setOnErrorListener(null)

        holder.imageView.visibility = View.GONE
        holder.videoView.visibility = View.GONE

        // IMAGEN
        if (esImagen) {
            holder.videoView.visibility = View.GONE
            holder.imageView.visibility = View.VISIBLE
            Glide.with(holder.itemView.context)
                .load(uri)
                .centerCrop()
                .into(holder.imageView)
        } else { // IVIDEO
            holder.videoView.visibility = View.VISIBLE

            holder.videoView.setZOrderMediaOverlay(true)


            Log.d(
                "CarruselVideo",
                "Intentando reproducir video -> uri=$uri"
            )


            holder.videoView.setZOrderMediaOverlay(true)

            holder.videoView.setOnPreparedListener { mp ->

                Log.d(
                    "CarruselVideo",
                    "Video preparado correctamente -> uri=$uri"
                )

                val videoWidth = mp.videoWidth
                val videoHeight = mp.videoHeight

                if (videoWidth > 0 && videoHeight > 0) {

                    val ratio =
                        videoWidth.toFloat() / videoHeight.toFloat()

                    holder.videoView.setVideoAspectRatio(ratio)

                    Log.d(
                        "CarruselVideo",
                        "Video ratio=$ratio ($videoWidth x $videoHeight)"
                    )
                }

                mp.isLooping = true
                mp.setVolume(0f, 0f)

                mp.setVideoScalingMode(
                    android.media.MediaPlayer
                        .VIDEO_SCALING_MODE_SCALE_TO_FIT
                )

                holder.videoView.start()
            }

            holder.videoView.setOnErrorListener { _, what, extra ->

                Log.e(
                    "CarruselVideo",
                    "ERROR reproduciendo video -> uri=$uri, what=$what, extra=$extra"
                )

                true
            }

            holder.videoView.setVideoURI(uri)

            holder.videoView.setOnErrorListener { _, what, extra ->

                Log.e(
                    "CarruselVideo",
                    "ERROR reproduciendo video -> uri=$uri, what=$what, extra=$extra"
                )

                true
            }
            holder.videoView.setVideoURI(uri)
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

    override fun onViewRecycled(holder: CarruselViewHolder) {
        super.onViewRecycled(holder)

        holder.videoView.stopPlayback()
        holder.videoView.setOnPreparedListener(null)
        holder.videoView.setOnErrorListener(null)
    }

}

