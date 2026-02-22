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
import android.widget.LinearLayout
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

    private val MAX_DOTS = 5
    private var dotsStartIndex = 0 // índice real del primer dot visible

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
            //Log.d("CuadroImagenDBG", "[$i] ${it.nombre}, esImagen=${it.esImagen}, uri=${it.uri}")
            Log.d("CuadroImagenDBG", "[$i] id=${it.id}, nombre=${it.nombre}, esImagen=${it.esImagen}, uri=${it.uri}")

        }

// Debug visual: ver si el pager principal está en pantalla
        //binding.mainCarousel.setBackgroundColor(0x55FF0000) // rojo con alpha
       // binding.suggestionsCarousel.setBackgroundColor(0x5500FF00) // verde con alpha

        configurarCarruselPrincipal()
       // configurarFlechasCarrusel()
        configurarBotonCerrar()
        //cerrarAutomaticamente()

    }

    override fun onStart() {
        super.onStart()

        val widthInPixels = (420 * resources.displayMetrics.density).toInt()
        val heightInPixels = (550 * resources.displayMetrics.density).toInt()

        dialog?.window?.setLayout(widthInPixels, heightInPixels)
    }


    private fun configurarCarruselPrincipal() {
//        val adapter = CarruselAdapter(
//            listaCompleta.map { it.uri },
//            listaCompleta.map { it.nombre },
//            listaCompleta.map { it.esImagen }
//        ) { _, nombreClick ->
//            listener?.reproducirPalabra(nombreClick)
//        }
        val adapter = CarruselAdapter(
            uris = listaCompleta.map { it.uri },
            labels = listaCompleta.map { it.nombre },  // visible
            ids = listaCompleta.map { it.id },         // id estable
            esImagenLista = listaCompleta.map { it.esImagen }
        ) { _, idClick ->
            listener?.reproducirPalabra(idClick)       // ahora devuelve ID
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

        crearDots(listaCompleta.size)

        binding.mainCarousel.post {
            val pos = posicionInicial.coerceIn(0, (listaCompleta.size - 1).coerceAtLeast(0))
            Log.d("CuadroImagenDBG", "setCurrentItem pos=$pos")
            binding.mainCarousel.setCurrentItem(pos, false)
           // actualizarVisibilidadFlechas(pos)
            //actualizarDots(pos)
            actualizarDots(currentIndex = pos, totalItems = listaCompleta.size)
            listaCompleta.getOrNull(pos)?.let { actualizarSugerencias(it) }
        }

        binding.mainCarousel.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                super.onPageScrolled(position, positionOffset, positionOffsetPixels)

                // position = página base, offset = progreso hacia la siguiente
                actualizarDotsScroll(
                    baseIndex = position,
                    offset = positionOffset,
                    totalItems = listaCompleta.size
                )
            }

            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                // Dejamos esto para asegurar estado final exacto
                actualizarDots(currentIndex = position, totalItems = listaCompleta.size)

                if (position in 0 until listaCompleta.size) {
                    actualizarSugerencias(listaCompleta[position])
                }
            }
        })
    }



    private fun actualizarSugerencias(item: ItemLista) {
        val sugerencias = (requireActivity() as? MediaAdapterProvider)
            ?.getMediaAdapter()
            ?.obtenerSugerenciasSiguientes(item.uri)
            ?: emptyList()

//        val sugerenciasAdapter = CarruselAdapter(
//            sugerencias.map { it.uri },
//            sugerencias.map { it.nombre },
//            sugerencias.map { it.esImagen }
//        ) { uriClick, nombreClick ->
//            //listener.reproducirPalabra(nombreClick)
//            listener?.reproducirPalabra(nombreClick)
//            // Abrir nuevo cuadro en la posición de la sugerencia
//            val pos = listaCompleta.indexOfFirst { it.uri == uriClick }
//            val nuevoCuadro = nuevaInstancia(listaCompleta, if (pos != -1) pos else 0)
//            nuevoCuadro.listener = listener
//            nuevoCuadro.show(parentFragmentManager, "CuadroImagen")
//        }
        val sugerenciasAdapter = CarruselAdapter(
            uris = sugerencias.map { it.uri },
            labels = sugerencias.map { it.nombre },
            ids = sugerencias.map { it.id },
            esImagenLista = sugerencias.map { it.esImagen }
        ) { uriClick, idClick ->
            listener?.reproducirPalabra(idClick)

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



    private fun crearDots(totalItems: Int) {
        binding.dotsContainer.removeAllViews()

        // Si no hay paginación, escondemos el indicador
        binding.dotsContainer.visibility = if (totalItems > 1) View.VISIBLE else View.GONE
        if (totalItems <= 1) return

        val visibleDots = minOf(MAX_DOTS, totalItems)

        repeat(visibleDots) {
            val dot = View(requireContext())
            val params = LinearLayout.LayoutParams(22, 22)
            params.setMargins(10, 0, 10, 0)
            dot.layoutParams = params
            dot.setBackgroundResource(R.drawable.dot_indicator)
            binding.dotsContainer.addView(dot)
        }

        // Estado inicial
        actualizarDots(currentIndex = posicionInicial.coerceIn(0, totalItems - 1), totalItems = totalItems)
    }

    private fun actualizarDots(currentIndex: Int, totalItems: Int) {
        val visibleDots = binding.dotsContainer.childCount
        if (visibleDots == 0) return

        // Ventana centrada (siempre que se pueda)
        val half = visibleDots / 2
        val maxStart = (totalItems - visibleDots).coerceAtLeast(0)

        dotsStartIndex = (currentIndex - half).coerceIn(0, maxStart)

        val activeLocal = currentIndex - dotsStartIndex

        val hasMoreLeft = dotsStartIndex > 0
        val hasMoreRight = (dotsStartIndex + visibleDots) < totalItems

        for (i in 0 until visibleDots) {
            val dot = binding.dotsContainer.getChildAt(i)

            val distance = kotlin.math.abs(i - activeLocal)

            val scale = when (distance) {
                0 -> 1.25f   // activo
                1 -> 1.05f   // vecinos (intermedio)
                2 -> 0.85f
                else -> 0.65f
            }

            val alpha = when (distance) {
                0 -> 1f
                1 -> 0.7f
                2 -> 0.4f
                else -> 0.2f
            }

            dot.scaleX = scale
            dot.scaleY = scale
            dot.alpha = alpha

            // Pista de continuidad: si hay más items fuera de la ventana,
            // achicamos MUCHO el primer/último dot visible.
            if (i == 0 && hasMoreLeft) {
                dot.scaleX *= 0.75f
                dot.scaleY *= 0.75f
                dot.alpha *= 0.6f
            }
            if (i == visibleDots - 1 && hasMoreRight) {
                dot.scaleX *= 0.75f
                dot.scaleY *= 0.75f
                dot.alpha *= 0.6f
            }
        }
    }

    private fun actualizarDotsScroll(baseIndex: Int, offset: Float, totalItems: Int) {
        val visibleDots = binding.dotsContainer.childCount
        if (visibleDots == 0 || totalItems <= 1) return

        // Clamp por seguridad
        val base = baseIndex.coerceIn(0, totalItems - 1)
        val next = (base + 1).coerceIn(0, totalItems - 1)
        val t = offset.coerceIn(0f, 1f)

        // Elegimos un índice "virtual" (float) para centrar ventana suavemente.
        // Podés hacerlo con base + t, pero la ventana mejor que "salte" solo cuando cruza 0.5.
        val currentForWindow = if (t < 0.5f) base else next

        // --- misma lógica de ventana que tu actualizarDots() ---
        val half = visibleDots / 2
        val maxStart = (totalItems - visibleDots).coerceAtLeast(0)
        dotsStartIndex = (currentForWindow - half).coerceIn(0, maxStart)

        val activeLocalBase = base - dotsStartIndex
        val activeLocalNext = next - dotsStartIndex

        val hasMoreLeft = dotsStartIndex > 0
        val hasMoreRight = (dotsStartIndex + visibleDots) < totalItems

        for (i in 0 until visibleDots) {
            val dot = binding.dotsContainer.getChildAt(i)

            // “Fuerza” del dot respecto a base y next, mezcladas por t
            val sBase = dotScaleForDistance(kotlin.math.abs(i - activeLocalBase))
            val aBase = dotAlphaForDistance(kotlin.math.abs(i - activeLocalBase))

            val sNext = dotScaleForDistance(kotlin.math.abs(i - activeLocalNext))
            val aNext = dotAlphaForDistance(kotlin.math.abs(i - activeLocalNext))

            // Interpolación lineal (movimiento suave)
            var scale = lerp(sBase, sNext, t)
            var alpha = lerp(aBase, aNext, t)

            // Pista de continuidad en extremos (como ya tenías)
            if (i == 0 && hasMoreLeft) {
                scale *= 0.75f
                alpha *= 0.6f
            }
            if (i == visibleDots - 1 && hasMoreRight) {
                scale *= 0.75f
                alpha *= 0.6f
            }

            dot.scaleX = scale
            dot.scaleY = scale
            dot.alpha = alpha
        }
    }


    private fun dotScaleForDistance(distance: Int): Float = when (distance) {
        0 -> 1.25f
        1 -> 1.05f
        2 -> 0.85f
        else -> 0.65f
    }

    private fun dotAlphaForDistance(distance: Int): Float = when (distance) {
        0 -> 1f
        1 -> 0.7f
        2 -> 0.4f
        else -> 0.2f
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t


}

//    private fun configurarFlechasCarrusel() {
//        binding.btnPrev.setOnClickListener {
//            val prev = (binding.mainCarousel.currentItem - 1).coerceAtLeast(0)
//            binding.mainCarousel.setCurrentItem(prev, true)
//        }
//
//        binding.btnNext.setOnClickListener {
//            val last = (listaCompleta.size - 1).coerceAtLeast(0)
//            val next = (binding.mainCarousel.currentItem + 1).coerceAtMost(last)
//            binding.mainCarousel.setCurrentItem(next, true)
//        }
//
//        // Estado inicial
//        actualizarVisibilidadFlechas(binding.mainCarousel.currentItem)
//    }
//
//    private fun actualizarVisibilidadFlechas(position: Int) {
//        val last = listaCompleta.size - 1
//        val tieneMasDeUno = listaCompleta.size > 1
//
//        binding.btnPrev.visibility =
//            if (tieneMasDeUno && position > 0) View.VISIBLE else View.INVISIBLE
//
//        binding.btnNext.visibility =
//            if (tieneMasDeUno && position < last) View.VISIBLE else View.INVISIBLE
//    }

