// Adaptador para manejar la creacion y enlace de elementos de la lista

package com.comunic.adapters


import android.content.Context
import android.net.Uri
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import android.util.Log
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.bumptech.glide.Glide
import android.graphics.Color
import com.comunic.data.RankingManager
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import com.comunic.fragment.CuadroImagen
import com.comunic.ItemLista
import com.comunic.R
import com.comunic.fragment.CategoriaDetalleFragment
import jp.wasabeef.picasso.transformations.RoundedCornersTransformation


open class MediaAdapter(
    private val mediaList: MutableList<ItemLista>,
    private val eliminar: (String) -> Unit,
    private val palabraAudio: (String) -> Unit,
    private val onLongClick: ((ItemLista) -> Unit)? = null,
    private val grayscaleMode: (ItemLista) -> Boolean = { false },
    private val mostrarMenuEnDialog: Boolean = true,
    private val bloquearClicks: () -> Boolean = { false },
    private val onItemClickOverride: ((ItemLista) -> Unit)? = null
) : RecyclerView.Adapter<MediaAdapter.MediaViewHolder>() {




    private var edicion = false // estado del modo edicion
    private var modoEliminacion = false // estado del modo eliminacion
    private val seleccionados = mutableSetOf<String>() // lista de elementos para eliminar


    fun setModoEliminacion(activar: Boolean) {
        modoEliminacion = activar
        if (!activar) {
            seleccionados.clear() // Limpia la selección al salir del modo eliminación
        }
        notifyDataSetChanged()
    }

    fun setModoEdicion(activar: Boolean) {
        edicion = activar

        if (activar) {
            // No podemos estar editando y eliminando al mismo tiempo
            modoEliminacion = false
            seleccionados.clear()
        }

        notifyDataSetChanged()
    }

    // Interfaz para avisar al MainActivity
    interface OnEliminarSeleccionListener {
        fun onEliminarSeleccionSolicitada(seleccionados: List<ItemLista>)
    }
    var eliminarSeleccionListener: OnEliminarSeleccionListener? = null

    var onSeleccionCambio: ((Int) -> Unit)? = null


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_media, parent, false)
        return MediaViewHolder(view)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        val mediaItem = mediaList[position]

        //val gray = grayscaleMode // para hcer griaceo al estar dehabilitadp un pack

        holder.itemView.setOnLongClickListener {
            // Si estás en modo eliminación o edición, no abrimos “Agregar a lista”
            if (modoEliminacion || edicion) return@setOnLongClickListener true

            holder.itemView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)


            onLongClick?.invoke(mediaItem)
            true
        }

        holder.nameTextView.text = mediaItem.nombre
        // SEGUN IMAGEN/VIDEO, ABRIR EL CUADRO IMAGEN
        if (mediaItem.esImagen) {
            holder.imageView.visibility = View.VISIBLE
            holder.videoThumbnail.visibility = View.GONE
            Picasso.get().load(mediaItem.uri).fit()
                .centerCrop()
                .transform(
                    RoundedCornersTransformation(16, 0))
                .into(holder.imageView, object : com.squareup.picasso.Callback {
                    override fun onSuccess() {}
                    override fun onError(e: Exception?) {
                        Log.e("MediaAdapter", "Error loading image: ${e?.message}")
                    }
                })
            applyGray(holder.imageView, grayscaleMode(mediaItem))
            holder.imageView.contentDescription = mediaItem.nombre

            holder.itemView.setOnClickListener {

                // PACK DESHABILITADO
                if (bloquearClicks()) {
                    return@setOnClickListener
                }
                // modo edicion
                if (edicion) {
                    onItemClickOverride?.invoke(mediaItem)
                    return@setOnClickListener
                }

               // modo eliminacion

                if (modoEliminacion) {
                        toggleSeleccion(holder, mediaItem)
                        return@setOnClickListener

                } else {

                    // Registramos el uso primero
                    RankingManager.getInstance(holder.itemView.context).registrarUso(mediaItem.id)
                    //palabraAudio(mediaItem.nombre)
                    //Log.d("TTS_DBG", "MediaAdapter speak id=${mediaItem.id}")
                  //  palabraAudio(mediaItem.id)

                    // Posición segura del item clickeado
                    val pos = holder.bindingAdapterPosition
                    if (pos == RecyclerView.NO_POSITION) return@setOnClickListener

                    // ABRIR el CuadroImagen nuevo: carrusel con la LISTA COMPLETA y empezando en 'pos'
                        val fragmento = CuadroImagen.nuevaInstancia(
                            listaCompleta = mediaList.toList(),
                            posicionInicial = pos,
                            mostrarMenu = mostrarMenuEnDialog
                        )

                    fragmento.listener = object : CuadroImagen.PalabraListener {
                        override fun reproducirPalabra(palabra: String) {

                            if (bloquearClicks()) return

                            palabraAudio(palabra)
                        }
                    }

                    // navegación a detalle de categoría desde el dialog
                    fragmento.categoriaListener = object : CuadroImagen.CategoriaClickListener {
                        override fun irACategoria(categoryId: String, categoryName: String) {
                            val fm = (holder.itemView.context as AppCompatActivity).supportFragmentManager
                            fm.beginTransaction()
                                .replace(R.id.fragment_container, CategoriaDetalleFragment.newInstance(categoryId, categoryName))
                                .addToBackStack(null)
                                .commit()
                        }
                    }

                    fragmento.show(
                        (holder.itemView.context as AppCompatActivity).supportFragmentManager,
                        "CuadroImagen"
                    )
                }
            }
        } else {
            holder.imageView.visibility = View.GONE
            holder.videoThumbnail.visibility = View.VISIBLE

            // Usa Glide o cualquier librería para cargar un frame del video en el ImageView
            Glide.with(holder.itemView.context)
                .asBitmap()
                .load(mediaItem.uri) // URI del video
                .frame(1000) // Muestra un frame específico (en milisegundos)
                .into(holder.videoThumbnail)

            applyGray(holder.videoThumbnail, grayscaleMode(mediaItem))

            holder.itemView.setOnClickListener {

                // PACK DESHABILITADO
                if (bloquearClicks()) {
                    return@setOnClickListener
                }

                if (edicion) {
                    onItemClickOverride?.invoke(mediaItem)
                    return@setOnClickListener
                }

                if (modoEliminacion) {
                    toggleSeleccion(holder, mediaItem)
                    return@setOnClickListener
                } else {
                    // Registramos el uso
                    RankingManager.getInstance(holder.itemView.context).registrarUso(mediaItem.id)
                    //palabraAudio(mediaItem.nombre)
                    //Log.d("TTS_DBG", "MediaAdapter speak id=${mediaItem.id}")
                   // palabraAudio(mediaItem.id)

                    // Posición segura del item clickeado
                    val pos = holder.bindingAdapterPosition
                    if (pos == RecyclerView.NO_POSITION) return@setOnClickListener

                    // ABRIR el CuadroImagen nuevo: carrusel con la LISTA COMPLETA y empezando en 'pos'
                    val fragmento = CuadroImagen.nuevaInstancia(
                        listaCompleta = mediaList.toList(),
                        posicionInicial = pos,
                        mostrarMenu = mostrarMenuEnDialog
                    )

                    fragmento.listener = object : CuadroImagen.PalabraListener {
                        override fun reproducirPalabra(palabra: String) {

                            if (bloquearClicks()) return

                            palabraAudio(palabra)
                        }
                    }

                    // navegación a detalle de categoría desde el dialog
                    fragmento.categoriaListener = object : CuadroImagen.CategoriaClickListener {
                        override fun irACategoria(categoryId: String, categoryName: String) {
                            val fm = (holder.itemView.context as AppCompatActivity).supportFragmentManager
                            fm.beginTransaction()
                                .replace(R.id.fragment_container, CategoriaDetalleFragment.newInstance(categoryId, categoryName))
                                .addToBackStack(null)
                                .commit()
                        }
                    }

                    fragmento.show(
                        (holder.itemView.context as AppCompatActivity).supportFragmentManager,
                        "CuadroImagen"
                    )
                }
            }

        }

        val seleccionado = modoEliminacion && seleccionados.contains(mediaItem.id)
        holder.itemView.setBackgroundColor(if (seleccionado) Color.LTGRAY else Color.TRANSPARENT)
    }


    // pack deshabilitado de color gris
    private fun applyGray(view: ImageView, gray: Boolean) {
        if (gray) {
            val matrix = ColorMatrix().apply { setSaturation(0f) }
            view.colorFilter = ColorMatrixColorFilter(matrix)
            view.alpha = 0.65f
        } else {
            view.colorFilter = null
            view.alpha = 1f
        }
    }


    //fun obtenerSeleccionados(): Set<String> = seleccionados.toSet()
    fun obtenerSeleccionados(): List<ItemLista> {
        return mediaList.filter { seleccionados.contains(it.id) }
    }

    private fun limpiarSeleccion() {
        seleccionados.clear()
        notifyDataSetChanged()
    }

    fun eliminarSeleccionados() {
        mediaList.removeAll { seleccionados.contains(it.id) }
        seleccionados.clear()
        notifyDataSetChanged()
    }

    fun cancelarModoEliminacion() {
        modoEliminacion = false
        seleccionados.clear()
        notifyDataSetChanged()
    }

    fun eliminarItem(item: ItemLista) {
        val index = mediaList.indexOfFirst { it.id == item.id }
        if (index != -1) {
            mediaList.removeAt(index)
            notifyItemRemoved(index)
        }
    }

    fun eliminarItems(items: List<ItemLista>) {
        //mediaList.removeAll { item -> items.any { it.nombre == item.nombre } }
        mediaList.removeAll { x -> items.any { it.id == x.id } }
        notifyDataSetChanged()
    }




    fun obtenerSugerenciasSiguientes(mediaItemUri: Uri): List<ItemLista> {
        // Ordena la lista por timestamp en orden descendente para obtener el uso más reciente primero
        val listaOrdenada = mediaList.sortedByDescending { it.timestamp }
        // Encontrar el índice del elemento actual en la lista ordenada
        val indiceActual = listaOrdenada.indexOfFirst { it.uri == mediaItemUri }
        // Tomar los siguientes elementos como sugerencias, si están disponibles
        if (indiceActual != -1) {
            // Obtener hasta 3 elementos siguientes o los que estén disponibles
            return listaOrdenada.subList(indiceActual + 1, (indiceActual + 4).coerceAtMost(listaOrdenada.size))
        }
        // Si no se encuentra el índice o la lista está vacía
        return emptyList()
    }

    override fun getItemCount(): Int = mediaList.size

    fun actualizarTimeStamp(uri: Uri) {
        val item = mediaList.find { it.uri == uri }
        item?.let {
            it.timestamp = System.currentTimeMillis()
            //ordenarListaPorUsoReciente()
        }
    }


//    esta funcion pide contraseña, si es correcta habilita el modo edicion y el boton borrar se vuelve visible
    fun ingresarContrasena(context: Context, holder: MediaViewHolder) {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Ingresar Contraseña")

        val input = EditText(context)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        builder.setView(input)

        builder.setPositiveButton("Aceptar") { dialog, _ ->
            val enteredPassword = input.text.toString()
            if (enteredPassword == "1234") {
               // modoEdicion(true, holder)  // Habilita el modo edición si la contraseña es correcta
            } else {
                Toast.makeText(context, "Contraseña incorrecta", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }
        builder.setNegativeButton("Cancelar") { dialog, _ -> dialog.cancel() }
        builder.show()
    }

    fun getSeleccionadosCount(): Int = seleccionados.size

    fun getSeleccionadosItems(): List<ItemLista> = obtenerSeleccionados()

    private fun toggleSeleccion(holder: MediaViewHolder, mediaItem: ItemLista) {
        if (seleccionados.contains(mediaItem.id)) {
            seleccionados.remove(mediaItem.id)
        } else {
            seleccionados.add(mediaItem.id)
        }

        onSeleccionCambio?.invoke(seleccionados.size)
        notifyItemChanged(holder.bindingAdapterPosition)
    }



    class MediaViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        val imageView: ImageView = view.findViewById(R.id.mediaImageView)
        val videoThumbnail: ImageView = view.findViewById(R.id.videoThumbnail)
        val nameTextView: TextView = view.findViewById(R.id.nameTextView)
    }
}
