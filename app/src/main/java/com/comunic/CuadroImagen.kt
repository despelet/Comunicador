package com.comunic

import MediaAdapterProvider
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.FileProvider
import androidx.fragment.app.DialogFragment
import androidx.viewpager2.widget.CompositePageTransformer
import androidx.viewpager2.widget.MarginPageTransformer
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.squareup.picasso.Picasso
import com.comunic.databinding.CuadroImagenBinding
import javax.sql.DataSource


class CuadroImagen : DialogFragment() {

    private lateinit var binding: CuadroImagenBinding
    private lateinit var listaCompleta: List<ItemLista>
    private var posicionInicial: Int = 0
    //lateinit var listener: CuadroImagen.PalabraListener
    var listener: PalabraListener? = null


    interface PalabraListener {
        fun reproducirPalabra(palabra: String)
    }

    companion object {
        fun nuevaInstancia(
            listaCompleta: List<ItemLista>,
            posicionInicial: Int
        ): CuadroImagen {
            val fragment = CuadroImagen()
            val args = Bundle()
            args.putParcelableArrayList(
                "listaCompleta",
                ArrayList(listaCompleta)
            )
            args.putInt("posicionInicial", posicionInicial)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = CuadroImagenBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        listaCompleta = arguments?.getParcelableArrayList("listaCompleta") ?: emptyList()
        posicionInicial = arguments?.getInt("posicionInicial") ?: 0

        Log.d("CuadroImagenDBG", "lista size=${listaCompleta.size}, posInicial=$posicionInicial")
        listaCompleta.forEachIndexed { i, it ->
            Log.d("CuadroImagenDBG", "[$i] ${it.nombre}, esImagen=${it.esImagen}, uri=${it.uri}")
        }

// Debug visual: ver si el pager principal está en pantalla
        //binding.mainCarousel.setBackgroundColor(0x55FF0000) // rojo con alpha
       // binding.suggestionsCarousel.setBackgroundColor(0x5500FF00) // verde con alpha

        configurarCarruselPrincipal()
        configurarBotonCerrar()
        //cerrarAutomaticamente()
    }

    override fun onStart() {
        super.onStart()

        val widthInPixels = (420 * resources.displayMetrics.density).toInt()
        val heightInPixels = (550 * resources.displayMetrics.density).toInt()

        dialog?.window?.setLayout(widthInPixels, heightInPixels)
    }


    /*rivate fun configurarCarruselPrincipal() {
        val adapter = CarruselAdapter(
            listaCompleta.map { it.uri },
            listaCompleta.map { it.nombre },
            listaCompleta.map { it.esImagen }
        ) { uriClick, nombreClick ->
            listener.reproducirPalabra(nombreClick)
        }

//        binding.mainCarousel.adapter = adapter
//        binding.mainCarousel.orientation = ViewPager2.ORIENTATION_HORIZONTAL
//        binding.mainCarousel.post {
//            val pos = posicionInicial.coerceIn(0, (listaCompleta.size - 1).coerceAtLeast(0))
//            binding.mainCarousel.setCurrentItem(pos, false)
//            listaCompleta.getOrNull(pos)?.let { actualizarSugerencias(it) }
//        }
        // Cargar sugerencias del item inicial:
       // listaCompleta.getOrNull(posicionInicial)?.let { actualizarSugerencias(it) }
        binding.suggestionsCarousel.adapter = adapter
        binding.suggestionsCarousel.orientation = ViewPager2.ORIENTATION_HORIZONTAL
        binding.suggestionsCarousel.post {
            val pos = posicionInicial.coerceIn(0, (listaCompleta.size - 1).coerceAtLeast(0))
            binding.suggestionsCarousel.setCurrentItem(pos, false)
            listaCompleta.getOrNull(pos)?.let { actualizarSugerencias(it) }
        }
        // Cuando cambie de item en el carrusel, actualizar las sugerencias
        binding.suggestionsCarousel.registerOnPageChangeCallback(object :
            ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                if (position in 0 until listaCompleta.size) {
                    val item = listaCompleta[position]
                    actualizarSugerencias(item)
                } else {
                    // NO_POSITION (-1) u otro valor fuera de rango: ignorar
                    Log.w("CuadroImagen", "onPageSelected fuera de rango: $position")
                }
            }
        })
    } */

    private fun configurarCarruselPrincipal() {
        val adapter = CarruselAdapter(
            listaCompleta.map { it.uri },
            listaCompleta.map { it.nombre },
            listaCompleta.map { it.esImagen }
        ) { _, nombreClick ->
            listener?.reproducirPalabra(nombreClick)
        }

        // 👉 Carrusel principal ARRIBA
        binding.mainCarousel.adapter = adapter
        binding.mainCarousel.orientation = ViewPager2.ORIENTATION_HORIZONTAL

        // Aplicar el PageTransformer personalizado SOLO
        // binding.mainCarousel.setPageTransformer(CarouselPageTransformer())
        // Combinamos múltiples transformers
        val compositeTransformer = CompositePageTransformer()
        compositeTransformer.addTransformer(MarginPageTransformer(20)) // 20 píxeles de margen
        compositeTransformer.addTransformer(CarouselPageTransformer())
        binding.mainCarousel.setPageTransformer(compositeTransformer) // Aplicamos el combinado


        binding.mainCarousel.post {
            val pos = posicionInicial.coerceIn(0, (listaCompleta.size - 1).coerceAtLeast(0))
            Log.d("CuadroImagenDBG", "setCurrentItem pos=$pos")
            binding.mainCarousel.setCurrentItem(pos, false)
            listaCompleta.getOrNull(pos)?.let { actualizarSugerencias(it) }
        }

        binding.mainCarousel.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                Log.d("CuadroImagenDBG", "onPageSelected=$position size=${listaCompleta.size}")
                if (position in 0 until listaCompleta.size) {
                    actualizarSugerencias(listaCompleta[position])
                } else {
                    Log.w("CuadroImagenDBG", "onPageSelected fuera de rango: $position")
                }
            }
        })
    }



    private fun actualizarSugerencias(item: ItemLista) {
        val sugerencias = (requireActivity() as? MediaAdapterProvider)
            ?.getMediaAdapter()
            ?.obtenerSugerenciasSiguientes(item.uri)
            ?: emptyList()

        val sugerenciasAdapter = CarruselAdapter(
            sugerencias.map { it.uri },
            sugerencias.map { it.nombre },
            sugerencias.map { it.esImagen }
        ) { uriClick, nombreClick ->
            //listener.reproducirPalabra(nombreClick)
            listener?.reproducirPalabra(nombreClick)
            // Abrir nuevo cuadro en la posición de la sugerencia
            val pos = listaCompleta.indexOfFirst { it.uri == uriClick }
            val nuevoCuadro = nuevaInstancia(listaCompleta, if (pos != -1) pos else 0)
            nuevoCuadro.listener = listener
            nuevoCuadro.show(parentFragmentManager, "CuadroImagen")
        }

        //binding.suggestionsCarousel.adapter = sugerenciasAdapter
    }

    private fun configurarBotonCerrar() {
        binding.btnCerrar.setOnClickListener { dismiss() }
    }

    private fun cerrarAutomaticamente() {
        Handler(Looper.getMainLooper()).postDelayed({ dismiss() }, 20000)
    }


}



/*
class CuadroImagen : DialogFragment() {

    private lateinit var binding: CuadroImagenBinding
    private var mediaUri: Uri? = null
    private var esImagen: Boolean = true
    private lateinit var sugerenciasUris: List<Uri>
    private lateinit var sugerenciasNombres: List<String>

    interface PalabraListener {
        fun reproducirPalabra(palabra: String)
    }
    var listener: PalabraListener? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = CuadroImagenBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // obtener uri y tipo de medio
        mediaUri = arguments?.getParcelable("mediaUri")
        if (mediaUri != null && mediaUri?.scheme == "file") {
            mediaUri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                java.io.File(mediaUri!!.path!!)
            )
        }
        esImagen = arguments?.getBoolean("esImagen", true) ?: true

        Log.d("CuadroImagen", "esImagen: $esImagen")

        val nombre = arguments?.getString("nombre")
        Log.d("CuadroImagen", "mediaUri: $mediaUri, esImagen: $esImagen, nombre: $nombre")


        // Obtener sugerencias
        sugerenciasUris = arguments?.getParcelableArrayList("sugerenciasUris") ?: listOf()
        sugerenciasNombres = arguments?.getStringArrayList("sugerenciasNombres") ?: listOf()
/*
        // Mostrar la imagen o video principal
        if (mediaUri != null) {
            if (esImagen) {
                binding.imageView.visibility = View.VISIBLE
                Log.d("CuadroImagen", "Cargando imagen con URI: ${mediaUri.toString()}")
                Glide.with(requireContext())
                    .load(mediaUri)
                    .into(binding.imageView)
                binding.videoView.visibility = View.GONE
                binding.nameTextView.text = nombre
            } else {
                binding.imageView.visibility = View.GONE
                binding.videoView.visibility = View.VISIBLE
                binding.videoView.setVideoURI(mediaUri)
                binding.videoView.setOnPreparedListener { mediaPlayer ->
                    mediaPlayer.setVolume(0f, 0f) // Anula el volumen del video
                    binding.videoView.start()
                }
                //binding.videoView.start()
                binding.nameTextView.text = nombre
                Log.d("CuadroImagen", "Cargando video con URI: ${mediaUri.toString()}")
            }
        } else {
            binding.nameTextView.text = "Error al cargar el medio"
            binding.imageView.visibility = View.GONE
            binding.videoView.visibility = View.GONE
        }

        // Configuración del carrusel de sugerencias
        val sugerenciasAdapter = SugerenciasAdapter(sugerenciasUris, sugerenciasNombres) { uri, nombreSugerencia ->
            dismiss() // cierro cuadro actual
            val mediaAdapter = (requireActivity() as? MediaAdapterProvider)?.getMediaAdapter() // actualizar timestamp del elemento seleccionado
            mediaAdapter?.actualizarTimeStamp(uri)
            val sugerenciasParaSiguiente = obtenerSugerenciasPara(uri) // obtener nuevas sugerencias. sugerenciasParaSiguiente toma lo que retorna la función obtenerSugerenciasPara
            val nuevoCuadro = nuevaInstancia(  // crear nuevo cuadro
                uri,
                true,
                nombreSugerencia,
                sugerenciasParaSiguiente.map { it.uri },
                sugerenciasParaSiguiente.map { it.nombre })
            nuevoCuadro.show(requireActivity().supportFragmentManager, "CuadroImagen")
            // Reproducir el audio de la sugerencia seleccionada
            listener?.reproducirPalabra(nombreSugerencia)


        }
*/

        // Combinar media principal + sugerencias en listas para el carrusel
        val todosUris = mutableListOf<Uri>()
        val todosNombres = mutableListOf<String>()
        mediaUri?.let { todosUris.add(it); todosNombres.add(nombre ?: "") }
        sugerenciasUris.forEachIndexed { index, uri ->
            todosUris.add(uri)
            todosNombres.add(sugerenciasNombres.getOrNull(index) ?: "")
        }

        // Configurar carrusel
        val carruselAdapter = CarruselAdapter(todosUris, todosNombres) { uriClick, nombreClick ->
            dismiss() // cerrar cuadro actual
            listener?.reproducirPalabra(nombreClick)

            // Abrir nuevo cuadro con sugerencias actualizadas
            val nuevasSugerencias = obtenerSugerenciasPara(uriClick)
            val nuevoCuadro = nuevaInstancia(
                uriClick,
                uriClick.toString().endsWith(".jpg") || uriClick.toString().endsWith(".png"),
                nombreClick,
                nuevasSugerencias.map { it.uri },
                nuevasSugerencias.map { it.nombre }
            )
            nuevoCuadro.listener = listener
            nuevoCuadro.show(requireActivity().supportFragmentManager, "CuadroImagen")
        }

        binding.suggestionsCarousel.adapter = carruselAdapter
        binding.suggestionsCarousel.orientation = ViewPager2.ORIENTATION_HORIZONTAL

        // Cerrar automáticamente después de 20 segundos
        Handler(Looper.getMainLooper()).postDelayed({
            dismiss()
        }, 20000)

        // Botón cerrar
        binding.btnCerrar.setOnClickListener { dismiss() }

        //binding.suggestionsCarousel.adapter = sugerenciasAdapter
        // Cerrar automáticamente después de 20 segundos
//        Handler(Looper.getMainLooper()).postDelayed({
//            dismiss()
//        }, 20000)

        // Al hacer click en la imagen o video principal, repetir el audio
//        val palabra = arguments?.getString("nombre") ?: ""
//        binding.imageView.setOnClickListener {
//            listener?.reproducirPalabra(palabra)
//        }
//        binding.videoView.setOnClickListener {
//            listener?.reproducirPalabra(palabra)
//        }

//        // Listener del botón de cerrar
//        binding.btnCerrar.setOnClickListener {
//            dismiss()
//        }

    }

    // Método que genera sugerencias basadas en la selección actual
    private fun obtenerSugerenciasPara(uri: Uri): List<ItemLista> {
        val mediaAdapter = (requireActivity() as? MediaAdapterProvider)?.getMediaAdapter()
        val sugerencias = mediaAdapter?.obtenerSugerenciasSiguientes(uri) ?: emptyList()
        // Agregar log para verificar si hay sugerencias
        Log.d("CuadroImagen", "Sugerencias obtenidas: ${sugerencias.map { it.nombre }}")
        return sugerencias
    }
    companion object {
        fun nuevaInstancia(
            mediaUri: Uri, esImagen: Boolean, nombre: String, sugerenciasUris: List<Uri>, sugerenciasNombres: List<String>
        ): CuadroImagen {
            val args = Bundle()
            args.putParcelable("mediaUri", mediaUri)
            args.putBoolean("esImagen", esImagen)
            args.putString("nombre", nombre)
            args.putParcelableArrayList("sugerenciasUris", ArrayList(sugerenciasUris))
            args.putStringArrayList("sugerenciasNombres", ArrayList(sugerenciasNombres))

            val fragmento = CuadroImagen()
            fragmento.arguments = args
            return fragmento
        }
    }
}
*/