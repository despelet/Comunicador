package com.comunic.fragment

import MediaAdapterProvider
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.CompositePageTransformer
import androidx.viewpager2.widget.MarginPageTransformer
import androidx.viewpager2.widget.ViewPager2
import com.comunic.CarouselPageTransformer
import com.comunic.CarruselAdapter
import com.comunic.ItemKey
import com.comunic.ItemLista
import com.comunic.R
import com.comunic.data.dao.CategoryDao
import com.comunic.data.db.AppDatabase
import com.comunic.databinding.CuadroImagenBinding
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class CuadroImagen : DialogFragment() {

    private lateinit var binding: CuadroImagenBinding
    private lateinit var listaCompleta: List<ItemLista>
    private var posicionInicial: Int = 0
    //lateinit var listener: CuadroImagen.PalabraListener
    var listener: PalabraListener? = null
    private lateinit var db: AppDatabase

    private val MAX_DOTS = 5
    private var dotsStartIndex = 0 // índice real del primer dot visible

    // para reproducrti palabra al delizar
    private var pagerCallback: ViewPager2.OnPageChangeCallback? = null
    private var pendingSpeakPos: Int? = null
    private var lastSpokenPos: Int = -1

    private var mostrarMenu: Boolean = true

    interface PalabraListener {
        fun reproducirPalabra(palabra: String)
    }

    companion object {
        fun nuevaInstancia(
            listaCompleta: List<ItemLista>,
            posicionInicial: Int,
            mostrarMenu: Boolean = true
        ): CuadroImagen {
            val fragment = CuadroImagen()
            val args = Bundle()

            args.putParcelableArrayList(
                "listaCompleta",
                ArrayList(listaCompleta)
            )
            args.putInt("posicionInicial", posicionInicial)
            args.putBoolean("mostrarMenu", mostrarMenu)

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

    private var currentPos: Int = 0
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db = AppDatabase.getDatabase(requireContext())

        listaCompleta = arguments?.getParcelableArrayList("listaCompleta") ?: emptyList()
        posicionInicial = arguments?.getInt("posicionInicial") ?: 0
        mostrarMenu = arguments?.getBoolean("mostrarMenu", true) ?: true

        binding.btnMore.visibility = if (mostrarMenu) View.VISIBLE else View.GONE

        Log.d("CuadroImagenDBG", "lista size=${listaCompleta.size}, posInicial=$posicionInicial")
        listaCompleta.forEachIndexed { i, it ->
            //Log.d("CuadroImagenDBG", "[$i] ${it.nombre}, esImagen=${it.esImagen}, uri=${it.uri}")
            Log.d("CuadroImagenDBG", "[$i] id=${it.id}, nombre=${it.nombre}, esImagen=${it.esImagen}, uri=${it.uri}")

        }



        configurarCarruselPrincipal()
       // configurarFlechasCarrusel()
        configurarBotonCerrar()
        //cerrarAutomaticamente()

        currentPos = posicionInicial

//        binding.btnMore.setOnClickListener { anchor ->
//            val popup = PopupMenu(requireContext(), anchor)
//            popup.menuInflater.inflate(R.menu.menu_cuadroimagen, popup.menu)
//
//            popup.setOnMenuItemClickListener { menuItem ->
//                when (menuItem.itemId) {
//                    R.id.action_add_to_list -> {
//                        val item = listaCompleta.getOrNull(currentPos) ?: return@setOnMenuItemClickListener true
//                        (activity as? MainActivity)?.pedirAgregarAListaDesdeCuadro(item)
//                        true
//                    }
//                    else -> false
//                }
//            }
//
//            popup.show()
//        }
        binding.btnMore.setOnClickListener {
            val item = listaCompleta.getOrNull(currentPos) ?: return@setOnClickListener
            (activity as? MainActivity)?.pedirAgregarAListaDesdeCuadro(item)
        }

    }

    override fun onStart() {
        super.onStart()

        val dm = resources.displayMetrics
        val maxW = (dm.widthPixels * 0.90f).toInt()

        // ancho máximo; alto wrap según contenido
        dialog?.window?.setLayout(maxW, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)

        binding.mainCarousel.post {
            val pos = posicionInicial.coerceIn(0, (listaCompleta.size - 1).coerceAtLeast(0))
            ajustarDialogParaItem(pos)
            //cargarYRenderCategorias(pos)
            observeCategorias(pos)
        }
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

            actualizarDots(currentIndex = pos, totalItems = listaCompleta.size)
            listaCompleta.getOrNull(pos)?.let { actualizarSugerencias(it) }

            //  hablar al abrir
            lastSpokenPos = pos
            pendingSpeakPos = pos
            Log.d("TTS_DBG", "CuadroImagen speak open id=${listaCompleta[pos].id}")
            listener?.reproducirPalabra(listaCompleta[pos].id)
        }

        // Si ya había uno registrado, lo removemos para no duplicar eventos
        pagerCallback?.let { binding.mainCarousel.unregisterOnPageChangeCallback(it) }

        pagerCallback = object : ViewPager2.OnPageChangeCallback() {

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                super.onPageScrolled(position, positionOffset, positionOffsetPixels)

                actualizarDotsScroll(
                    baseIndex = position,
                    offset = positionOffset,
                    totalItems = listaCompleta.size
                )
            }

            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)

                currentPos = position
                pendingSpeakPos = position

                actualizarDots(currentIndex = position, totalItems = listaCompleta.size)

                if (position in listaCompleta.indices) {
                    ajustarDialogParaItem(position)
                    actualizarSugerencias(listaCompleta[position])
                    observeCategorias(position)
                }
            }

            override fun onPageScrollStateChanged(state: Int) {
                super.onPageScrollStateChanged(state)

                // ✅ hablar cuando el swipe termina (queda quieto)
                if (state == ViewPager2.SCROLL_STATE_IDLE) {
                    val pos = pendingSpeakPos ?: binding.mainCarousel.currentItem
                    if (pos != lastSpokenPos && pos in listaCompleta.indices) {
                        lastSpokenPos = pos
                        Log.d("TTS_DBG", "CuadroImagen speak open id=${listaCompleta[pos].id}")
                        listener?.reproducirPalabra(listaCompleta[pos].id) // id = itemKey (PIC:/MED:)
                    }
                }
            }
        }

        binding.mainCarousel.registerOnPageChangeCallback(pagerCallback!!)
    }



    private fun actualizarSugerencias(item: ItemLista) {
        val sugerencias = (requireActivity() as? MediaAdapterProvider)
            ?.getMediaAdapter()
            ?.obtenerSugerenciasSiguientes(item.uri)
            ?: emptyList()


        val sugerenciasAdapter = CarruselAdapter(
            uris = sugerencias.map { it.uri },
            labels = sugerencias.map { it.nombre },
            ids = sugerencias.map { it.id },
            esImagenLista = sugerencias.map { it.esImagen }
        ) { uriClick, idClick ->
            listener?.reproducirPalabra(idClick)

            val pos = listaCompleta.indexOfFirst { it.uri == uriClick }
            val nuevoCuadro = nuevaInstancia(
                listaCompleta,
                if (pos != -1) pos else 0,
                mostrarMenu = mostrarMenu   // 👈 mantener el estado
            )
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

    interface CategoriaClickListener {
        fun irACategoria(categoryId: String, categoryName: String)
    }
    var categoriaListener: CategoriaClickListener? = null

    private fun ajustarDialogParaItem(pos: Int) {
        val item = listaCompleta.getOrNull(pos) ?: return

        val dm = resources.displayMetrics
        val maxW = (dm.widthPixels * 0.90f).toInt()
        val maxH = (dm.heightPixels * 0.85f).toInt()

        // Reservamos espacio aprox para dots + categorías + márgenes
        val reserved = (140 * dm.density).toInt()
        val maxMediaH = (maxH - reserved).coerceAtLeast((180 * dm.density).toInt())

        val ratio = obtenerAspectRatio(item) ?: 1f // fallback cuadrado

        val targetW = maxW
        val targetH = (targetW / ratio).toInt().coerceAtMost(maxMediaH)

        binding.mainCarousel.layoutParams = binding.mainCarousel.layoutParams.apply {
            width = targetW
            height = targetH
        }

        dialog?.window?.setLayout(targetW, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun obtenerAspectRatio(item: ItemLista): Float? {
        return if (item.esImagen) {
            obtenerAspectRatioImagen(item.uri)
        } else {
            obtenerAspectRatioVideo(item.uri)
        }
    }

    private fun obtenerAspectRatioImagen(uri: Uri): Float? {
        return try {
            requireContext().contentResolver.openInputStream(uri)?.use { input ->
                val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeStream(input, null, opts)
                if (opts.outWidth > 0 && opts.outHeight > 0) opts.outWidth.toFloat() / opts.outHeight.toFloat() else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun obtenerAspectRatioVideo(uri: Uri): Float? {
        return try {
            val r = android.media.MediaMetadataRetriever()
            r.setDataSource(requireContext(), uri)
            val w = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            val h = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            r.release()
            if (w != null && h != null && w > 0 && h > 0) w.toFloat() / h.toFloat() else null
        } catch (_: Exception) {
            null
        }
    }

//    private fun cargarYRenderCategorias(pos: Int) {
//        val item = listaCompleta.getOrNull(pos) ?: return
//
//        viewLifecycleOwner.lifecycleScope.launch {
//            val cats: List<CategoryDao.CategoryMiniRow> = withContext(Dispatchers.IO) {
//                val itemKey = normalizarItemKey(item.id) // normalizarItemKey es suspend
//                db.categoryDao().getCategoriesForItemKey(itemKey)
//            }
//            renderCategorias(cats)
//        }
//    }
    private suspend fun normalizarItemKey(raw: String): String {
        // Si ya está normalizado, listo
        if (raw.startsWith("PIC:") || raw.startsWith("MED:")) return raw

        // Si existe como picto en Room, es PIC; si no, asumimos MED
        val existsPicto = db.pictogramDao().getPictoUiById(raw) != null
        return if (existsPicto) ItemKey.picto(raw) else ItemKey.media(raw)
    }

    private fun renderCategorias(categorias: List<CategoryDao.CategoryMiniRow>) {

        binding.categoriesContainer.removeAllViews()

        binding.categoriesScroll.visibility =
            if (categorias.isEmpty()) View.GONE else View.VISIBLE

        if (categorias.isEmpty()) return

        categorias.forEach { cat ->

            val btn = MaterialButton(
                requireContext(),
                null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle
            ).apply {

                text = cat.name
                isAllCaps = true

                // ✅ COLOR TEXTO (FIX PRINCIPAL)
                setTextColor(
                    ContextCompat.getColor(context, R.color.color1)
                )

                // ✅ BORDE visible
                strokeWidth = 2
                strokeColor = ColorStateList.valueOf(
                    ContextCompat.getColor(context, R.color.color1)
                )

                // ✅ fondo transparente estilo chip
                backgroundTintList =
                    ColorStateList.valueOf(Color.TRANSPARENT)

                // bordes redondeados tipo pill
                cornerRadius =
                    (18 * resources.displayMetrics.density).toInt()

                setOnClickListener {
                    dismissAllowingStateLoss()
                    categoriaListener?.irACategoria(
                        cat.categoryId,
                        cat.name
                    )
                }
            }

            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(12, 0, 12, 0)
            }

            binding.categoriesContainer.addView(btn, lp)
        }
    }

    private var catsJob: Job? = null

    private fun observeCategorias(pos: Int) {
        val item = listaCompleta.getOrNull(pos) ?: return

        catsJob?.cancel()
        catsJob = viewLifecycleOwner.lifecycleScope.launch {
            val itemKey = withContext(Dispatchers.IO) { normalizarItemKey(item.id) }

            db.categoryDao()
                .observeCategoriesForItemKey(itemKey)
                .collect { cats ->
                    Log.d(
                        "CUADRO_CATS_FLOW",
                        "emit -> itemKey=$itemKey  cats=${cats.map { it.name }}"
                    )
                    renderCategorias(cats)
                }
        }
    }

    override fun onDestroyView() {
        catsJob?.cancel()
        pagerCallback?.let { binding.mainCarousel.unregisterOnPageChangeCallback(it) }
        pagerCallback = null
        super.onDestroyView()
    }

}


