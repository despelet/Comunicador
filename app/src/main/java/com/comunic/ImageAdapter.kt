package com.comunic

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.VideoView
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso

class ImageAdapter(private val mediaList: MutableList<Triple<String, Uri, Boolean>>) : RecyclerView.Adapter<ImageAdapter.MediaViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_media, parent, false)
        return MediaViewHolder(view)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        val (name, uri) = mediaList[position]
        holder.nameTextView.text = name
        val mimeType = holder.itemView.context.contentResolver.getType(uri)

        if (mimeType != null && mimeType.startsWith("image/")) {
            holder.imageView.visibility = View.VISIBLE
            holder.videoView.visibility = View.GONE
            Picasso.get().load(uri).into(holder.imageView)
        } else if (mimeType != null && mimeType.startsWith("video/")) {
            holder.imageView.visibility = View.GONE
            holder.videoView.visibility = View.VISIBLE
            holder.videoView.setVideoURI(uri)
            holder.videoView.start()
        }

        holder.itemView.contentDescription = name
    }

    override fun getItemCount(): Int = mediaList.size

    class MediaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.mediaImageView)
        val videoView: VideoView = view.findViewById(R.id.mediaVideoView)
        val nameTextView: TextView = view.findViewById(R.id.nameTextView)
    }
}
