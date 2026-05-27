package com.comunic.fragment



import android.app.AlertDialog
import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.comunic.data.db.AppDatabase
import com.comunic.databinding.FragmentCategoriaDetalleBinding
import kotlinx.coroutines.launch
import com.comunic.ItemLista
import com.comunic.adapters.MediaAdapter
import com.comunic.PickItemsDialogFragment
import com.comunic.R
import com.comunic.SpeechTextResolver
import com.comunic.adapters.ExportItemsAdapter
import com.comunic.adapters.ExportListasAdapter
import com.comunic.export.exportitems.ExportManager
import com.comunic.adapters.ResumenExportacionAdapter
import com.comunic.data.entity.CategoryItemEntity
import com.comunic.data.entity.InstalledPackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.comunic.data.mappers.resolveItemKeyToItemLista
import com.comunic.data.mappers.resolveItemKeyToItemListaAllowDisabled
import com.comunic.interfaces.DrawerMenuConfig
import com.comunic.interfaces.MediaResultListener
import com.comunic.session.PermissionManager
import com.comunic.session.RoleAwareFragment
import com.comunic.session.SessionManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import java.util.Locale
import java.util.UUID

class CategoriaDetalleFragment :
    Fragment(),
    TextToSpeech.OnInitListener,
    MediaResultListener ,
    DrawerMenuConfig ,
    RoleAwareFragment {

    private var _binding: FragmentCategoriaDetalleBinding? = null
    private val binding get() = _binding!!


    private lateinit var tts: TextToSpeech
    private var ttsReady = false
    private lateinit var audioManager: AudioManager
    private var volumenOriginal = -1

    private lateinit var db: AppDatabase
    private lateinit var mediaAdapter: MediaAdapter
    private val listaDeArchivos: MutableList<ItemLista> = mutableListOf()

    private lateinit var categoryId: String
    private lateinit var categoryName: String

    private var isPackEnabled: Boolean = true
    private var isSystemCategory: Boolean = false
    private var packId: String = "user"

    private val previewMode: Boolean
        get() = (isSystemCategory && !isPackEnabled)

    private var previewEnabled: Boolean = false

    //private var deleteActionMode: ActionMode? = null


    override fun onUserModeChanged() {
        setupDeleteCategoryButton()
    }

    companion object {
        fun newInstance(categoryId: String, categoryName: String) =
            CategoriaDetalleFragment().apply {
                arguments = Bundle().apply {
                    putString("categoryId", categoryId)
                    putString("categoryName", categoryName)
                }
            }
    }

    override fun configureDrawerMenu(menu: Menu) {
        menu.findItem(R.id.nav_eliminar)?.isVisible = false
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCategoriaDetalleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        (activity as? MainActivity)?.setMediaResultListener(this)
    }

    override fun onPause() {
        super.onPause()
        (activity as? MainActivity)?.setMediaResultListener(null)
    }

    /*override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val categoryId = requireArguments().getString("categoryId")!!
        val categoryName = requireArguments().getString("categoryName")!!
        binding.txtTitulo.text = categoryName

        val db = AppDatabase.getDatabase(requireContext())

        // IMPORTANTE: en onViewCreated ya existe viewLifecycleOwner
        viewLifecycleOwner.lifecycleScope.launch {
            val pictosDb = db.pictogramDao().getPictosForCategory(categoryId)

            val pictos = pictosDb.map {
                PictoUi(
                    pictogramId = it.pictogramId,
                    label = it.label,
                    imagePath = it.imageUri
                )
            }

            binding.recyclerPictos.layoutManager = GridLayoutManager(requireContext(), 3)
            binding.recyclerPictos.adapter = PictosAdapter(
                pictos = pictos,
                onClick = { picto ->
                    RankingManager.getInstance(requireContext())
                        .registrarUso(picto.pictogramId)

                    // TTS o lo que quieras
                    // palabraAudio(picto.label)
                }
            )

        }
    } */


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tts = TextToSpeech(requireContext(), this)
        audioManager =  requireContext().getSystemService(Context.AUDIO_SERVICE) as AudioManager

        db = AppDatabase.getDatabase(requireContext())

        categoryId = requireArguments().getString("categoryId")!!
        categoryName = requireArguments().getString("categoryName")!!
        binding.txtTitulo.text = categoryName
        val activity = activity as? MainActivity

        binding.btnAgregarElemento.setOnClickListener {
            abrirSelectorParaAgregar(categoryId)
        }

        binding.btnCamera.setOnClickListener {
            activity?.launchImageCapture()
        }

        binding.btnVideo.setOnClickListener {
            activity?.launchVideoCapture()
        }

        binding.btnGallery.setOnClickListener {
            activity?.openGallery()
        }


        viewLifecycleOwner.lifecycleScope.launch {
            val status = withContext(Dispatchers.IO) {
                db.categoryDao().getCategoryStatus(categoryId)
            }

            isSystemCategory = status?.isSystem == true
            packId = status?.packId ?: "user"
            isPackEnabled = status?.packEnabled ?: true
            previewEnabled = isSystemCategory && !isPackEnabled

            configurarBotonSegunEstado()
            loadCategory(categoryId)
        }


        // ✅ Config Recycler + adapter (tu código)
        binding.recyclerPictos.layoutManager = GridLayoutManager(requireContext(), 3)


        mediaAdapter = MediaAdapter(
            mediaList = listaDeArchivos,
            eliminar = { itemKey -> eliminarDeCategoria(categoryId, itemKey) },
            palabraAudio = { itemKey -> reproducirAudioPorItemKey(itemKey) },
            grayscaleMode = { previewEnabled },
            mostrarMenuEnDialog = false ,  // desactiva botone de 3 puntos y acciones asociadas (porque no queremos eliminar ni editar desde ahí en este caso
            bloquearClicks = { previewEnabled }
        )

        mediaAdapter.eliminarSeleccionListener = object : MediaAdapter.OnEliminarSeleccionListener {
            override fun onEliminarSeleccionSolicitada(seleccionados: List<ItemLista>) {
                eliminarSeleccionDeCategoria(categoryId, seleccionados)
            }
        }



        binding.recyclerPictos.adapter = mediaAdapter

        // ✅ Nuevo: configurar botón eliminar lista (solo user)
        setupDeleteCategoryButton()
        // salir del modo eliminacion
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            if (binding.selectionPanel.visibility == View.VISIBLE) {
                cerrarModoEliminacionPanel()
            } else {
                isEnabled = false
                requireActivity().onBackPressed()
            }
        }

        //loadCategory(categoryId)
    }

    override fun onInit(status: Int) {

        if (status == TextToSpeech.SUCCESS) {

            val idioma = tts.setLanguage(Locale("es", "AR"))

            if (
                idioma == TextToSpeech.LANG_MISSING_DATA ||
                idioma == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                Log.e("TextToSpeech", "Error con el idioma")
            } else {
                ttsReady = true
            }

            tts.setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {

                    override fun onStart(utteranceId: String?) {
                    }

                    override fun onDone(utteranceId: String?) {

                        Handler(Looper.getMainLooper()).post {

                            try {

                                if (volumenOriginal >= 0) {

                                    audioManager.setStreamVolume(
                                        AudioManager.STREAM_MUSIC,
                                        volumenOriginal,
                                        0
                                    )
                                }

                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }

                    override fun onError(utteranceId: String?) {
                    }
                }
            )

        } else {

            Log.e("TextToSpeech", "Error al inicializar")
        }
    }

    override fun onDestroyView() {
        // por si queda activo
        if (::mediaAdapter.isInitialized) {
            mediaAdapter.onSeleccionCambio = null
            mediaAdapter.setModoEliminacion(false)
        }

        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroyView()
        _binding = null
    }

    private fun loadCategory(categoryId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val keys = withContext(Dispatchers.IO) {
                db.categoryDao().getItemKeysForCategory(categoryId)
            }
            Log.d("CAT_DEBUG", "keys=${keys.joinToString()}")

//            val items = withContext(Dispatchers.IO) {
//                keys.mapNotNull { key ->
//                    resolveItemKeyToItemLista(requireContext(), key)
//                }
//            }
            val items = withContext(Dispatchers.IO) {
                keys.mapNotNull { key ->
                    if (isSystemCategory && !isPackEnabled) {
                        resolveItemKeyToItemListaAllowDisabled(requireContext(), key)
                    } else {
                        resolveItemKeyToItemLista(requireContext(), key)
                    }
                }
            }

            listaDeArchivos.clear()
            listaDeArchivos.addAll(items)
            mediaAdapter.notifyDataSetChanged()
        }
    }

    private fun eliminarDeCategoria(categoryId: String, itemKey: String) {
        if (!puedeModificarLista()) return

        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val placementId = db.categoryDao().findPlacementId(categoryId, itemKey)
                if (placementId != null) {
                    db.categoryDao().softDeletePlacement(
                        placementId = placementId,
                        updatedAt = System.currentTimeMillis()
                    )
                }
            }
            loadCategory(categoryId)
        }
    }

    private fun eliminarSeleccionDeCategoria(categoryId: String, seleccionados: List<ItemLista>) {
        if (!puedeModificarLista()) return

        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                // cada ItemLista.id == itemKey (porque ya ajustamos el resolver)
                seleccionados.forEach { item ->
                    val placementId = db.categoryDao().findPlacementId(categoryId, item.id)
                    if (placementId != null) {
                        db.categoryDao().softDeletePlacement(
                            placementId = placementId,
                            updatedAt = System.currentTimeMillis()
                        )
                    }
                }
            }
            loadCategory(categoryId)
        }
    }

    private fun reproducirAudioPorItemKey(itemKeyOrId: String) {

        if (!ttsReady) return

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {

            val texto = SpeechTextResolver.resolve(db, itemKeyOrId)

            withContext(Dispatchers.Main) {

                try {

                    // Guardar volumen actual solo si no estaba hablando
                    if (!tts.isSpeaking) {

                        volumenOriginal = audioManager.getStreamVolume(
                            AudioManager.STREAM_MUSIC
                        )
                    }

                    // Desmutear multimedia
                    audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_UNMUTE,
                        0
                    )

                    // Volumen máximo
                    val maxVolumen = audioManager.getStreamMaxVolume(
                        AudioManager.STREAM_MUSIC
                    )

                    audioManager.setStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        maxVolumen,
                        0
                    )

                    // pequeño delay
                    Handler(Looper.getMainLooper()).postDelayed({

                        tts.speak(
                            texto,
                            TextToSpeech.QUEUE_FLUSH,
                            null,
                            "BICOM_TTS"
                        )

                    }, 80)

                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun abrirSelectorParaAgregar(categoryId: String) {
        viewLifecycleOwner.lifecycleScope.launch {

            // 1) cargar items disponibles (pictos + media)
            val disponibles = withContext(Dispatchers.IO) {
                com.comunic.data.mappers.loadAllAvailableItems(requireContext(), db)
            }

            // 2) abrir dialog multi-select
            PickItemsDialogFragment(disponibles) { selected ->
                viewLifecycleOwner.lifecycleScope.launch {

                    // 3) insertar seleccionados en category_items
//                    withContext(Dispatchers.IO) {
//                        var next = db.categoryDao().getMaxOrderIndex(categoryId) + 1
//
//                        selected.forEach { item ->
//                            db.categoryDao().insertCategoryItem(
//                                CategoryItemEntity(
//                                    placementId = UUID.randomUUID().toString(),
//                                    categoryId = categoryId,
//                                    itemKey = item.id,    // ✅ id == itemKey
//                                    orderIndex = next++
//                                )
//                            )
//                        }
//                    }
                    withContext(Dispatchers.IO) {
                        val existentes = db.categoryDao().getItemKeysForCategory(categoryId).toSet()
                        val nuevos = selected.filter { it.id !in existentes }
                        val now = System.currentTimeMillis()
                        var next = db.categoryDao().getMaxOrderIndex(categoryId) + 1
                        nuevos.forEach { item ->
                            db.categoryDao().insertCategoryItem(
                                CategoryItemEntity(
                                    placementId = UUID.randomUUID().toString(),
                                    categoryId = categoryId,
                                    itemKey = item.id,
                                    orderIndex = next++,
                                    createdAt = now,
                                    updatedAt = now
                                )
                            )
                        }
                    }

                    // 4) refrescar UI
                    loadCategory(categoryId)
                }
            }.show(parentFragmentManager, "PickItemsAdd")
        }
    }

    private fun setupDeleteCategoryButton() {
        viewLifecycleOwner.lifecycleScope.launch {

            val cat = withContext(Dispatchers.IO) {
                db.categoryDao().getCategoryById(categoryId)
            }

            val isSystem = cat?.isSystem == true

            // Solo user => visible
            val sessionManager = SessionManager(requireContext())
            val permissionManager = PermissionManager(sessionManager)

            val puedeEliminar =
                permissionManager.canDeleteCategory()

            binding.btnEliminarCategoria.visibility =
                if (!isSystem && puedeEliminar) View.VISIBLE else View.GONE

            binding.btnEliminarCategoria.setOnClickListener {
                mostrarMenuEliminar()
            }
        }
    }

    private fun mostrarMenuEliminar() {
        if (!puedeModificarLista()) return

        val opciones = arrayOf("Eliminar lista", "Eliminar elementos de la lista")

        AlertDialog.Builder(requireContext(), R.style.ThemeOverlay_Comunic_AlertDialog)
            .setTitle("Eliminar")
            .setItems(opciones) { _, which ->
                when (which) {
                    0 -> confirmarEliminarCategoria()                 // como ahora
                    1 -> solicitarContrasenaEliminarElementos()       // nuevo
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun solicitarContrasenaEliminarElementos() {
        if (!puedeModificarLista()) return

        activarModoEliminacionEnCategoria()

        //ya no tengo que pedir la contraseña
        /*val builder = AlertDialog.Builder(requireContext(), R.style.ThemeOverlay_Comunic_AlertDialog)
        builder.setTitle("Ingrese la contraseña")

        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        builder.setView(input)

        builder.setPositiveButton("Aceptar") { _, _ ->
            val passwordIngresada = input.text.toString()
            if (passwordIngresada == "1234") {
                activarModoEliminacionEnCategoria()
            } else {
                Toast.makeText(requireContext(), "Contraseña incorrecta", Toast.LENGTH_SHORT).show()
            }
        }

        builder.setNegativeButton("Cancelar") { dialog, _ -> dialog.cancel() }
        builder.show()*/
    }

    private fun activarModoEliminacionEnCategoria() {
        mediaAdapter.setModoEliminacion(true)

        // mostrar panel
        binding.selectionPanel.visibility = View.VISIBLE
        binding.selectionCountText.text = "0 elementos seleccionados"

        // contador en vivo
        mediaAdapter.onSeleccionCambio = { count ->
            binding.selectionCountText.text = "$count elementos seleccionados"
        }

        // cancelar
        binding.cancelSelectionButton.setOnClickListener {
            cerrarModoEliminacionPanel()
        }

        // quitar seleccionados (solo de la categoría)
        binding.deleteSelectedButton.setOnClickListener {
            val seleccionados = mediaAdapter.getSeleccionadosItems()
            if (seleccionados.isEmpty()) {
                Toast.makeText(requireContext(), "No hay elementos seleccionados", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            AlertDialog.Builder(requireContext(), R.style.ThemeOverlay_Comunic_AlertDialog)
                .setTitle("Quitar de la lista")
                .setMessage("¿Quitar ${seleccionados.size} elemento(s) de \"$categoryName\"?")
                .setPositiveButton("Quitar") { _, _ ->
                    eliminarSeleccionDeCategoria(categoryId, seleccionados)
                    cerrarModoEliminacionPanel()
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        Toast.makeText(requireContext(), "Seleccioná elementos para quitar de la lista", Toast.LENGTH_SHORT).show()
    }

    private fun cerrarModoEliminacionPanel() {
        binding.selectionPanel.visibility = View.GONE
        mediaAdapter.onSeleccionCambio = null
        mediaAdapter.setModoEliminacion(false)
    }

    private fun confirmarEliminarCategoria() {
        AlertDialog.Builder(requireContext(), R.style.ThemeOverlay_Comunic_AlertDialog)
            .setTitle("Eliminar lista")
            .setMessage("¿Deseás eliminar la lista \"$categoryName\"?")
            .setPositiveButton("Eliminar") { _, _ ->
                eliminarCategoria()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun eliminarCategoria() {
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                // ✅ Solo afecta a user por tu WHERE en la query del DAO
                db.categoryDao().softDeleteUserCategory(categoryId)
            }

            Toast.makeText(requireContext(), "Lista eliminada", Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack() // vuelve a Listas
        }
    }

    private fun configurarBotonSegunEstado() {

        val habilitado = !(isSystemCategory && !isPackEnabled)

        if (habilitado) {

            // mostrar botones normales
            binding.btnAgregarElemento.visibility = View.VISIBLE
            binding.btnCamera.visibility = View.VISIBLE
            binding.btnVideo.visibility = View.VISIBLE
            binding.btnGallery.visibility = View.VISIBLE

            // ocultar botón habilitar
            binding.btnHabilitarPack.visibility = View.GONE

        } else {

            // ocultar botones normales
            binding.btnAgregarElemento.visibility = View.GONE
            binding.btnCamera.visibility = View.GONE
            binding.btnVideo.visibility = View.GONE
            binding.btnGallery.visibility = View.GONE

            // mostrar habilitar
            binding.btnHabilitarPack.visibility = View.VISIBLE

            binding.btnHabilitarPack.setOnClickListener {
                habilitarPack(packId)
            }
        }
    }

    private fun habilitarPack(packId: String) {
        if (packId.isBlank()) return

        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val dao = db.installedPackDao()
                val existing = dao.get(packId)
                if (existing == null) {
                    dao.insert(
                        InstalledPackEntity(
                            packId = packId,
                            version = 1,
                            installedAt = System.currentTimeMillis(),
                            enabled = true,
                            isSystem = true
                        )
                    )
                } else {
                    dao.setEnabled(packId, true)
                }
            }

            // refrescar estado + UI
            val status = withContext(Dispatchers.IO) { db.categoryDao().getCategoryStatus(categoryId) }
            isPackEnabled = status?.packEnabled ?: true
            previewEnabled = isSystemCategory && !isPackEnabled
            configurarBotonSegunEstado()
            loadCategory(categoryId)
            mediaAdapter.notifyDataSetChanged()
        }
    }

    private fun agregarItemACategoria(item: ItemLista) {
        viewLifecycleOwner.lifecycleScope.launch {

            withContext(Dispatchers.IO) {

                val exists = db.categoryDao().existsItemInCategory(categoryId, item.id)
                if (exists) return@withContext

                val now = System.currentTimeMillis()
                val next = db.categoryDao().getMaxOrderIndex(categoryId) + 1

                db.categoryDao().insertCategoryItem(
                    CategoryItemEntity(
                        placementId = UUID.randomUUID().toString(),
                        categoryId = categoryId,
                        itemKey = item.id,
                        orderIndex = next,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            }

            loadCategory(categoryId)
        }
    }

    override fun onMediaCreated(item: ItemLista) {
        agregarItemACategoria(item)
    }

    // EXPORTAR ELEMENTOS
    fun mostrarDialogoSeleccionarElementos(
        checkedInicial: BooleanArray? = null
    ) {

        val checked = checkedInicial
            ?: BooleanArray(listaDeArchivos.size)
        val dialogView = layoutInflater.inflate(
            R.layout.exp_dialogo_seleccion,
            null
        )
        // TABS
        val tabs = dialogView.findViewById<TabLayout>(R.id.tabExportacion)
        val layoutElementos = dialogView.findViewById<LinearLayout>(R.id.layoutExportarElementos)
        val layoutListas = dialogView.findViewById<LinearLayout>(R.id.layoutExportarListas)

        tabs.addOnTabSelectedListener(
            object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab?) {
                    when (tab?.position) {
                        0 -> {
                            layoutElementos.visibility = View.VISIBLE
                            layoutListas.visibility = View.GONE
                        }
                        1 -> {
                            layoutElementos.visibility = View.GONE
                            layoutListas.visibility = View.VISIBLE
                        }
                    }
                }

                override fun onTabUnselected(tab: TabLayout.Tab?) {}

                override fun onTabReselected(tab: TabLayout.Tab?) {}
            }
        )

        val recycler = dialogView.findViewById<RecyclerView>(R.id.recyclerItems)
        val btnSeleccionarTodos = dialogView.findViewById<MaterialButton>(R.id.btnSeleccionarTodos)
        val btnDeseleccionarTodos = dialogView.findViewById<MaterialButton>(R.id.btnDeseleccionarTodos)
        val btnExportar = dialogView.findViewById<MaterialButton>(R.id.btnExportar)
        val btnCancelar = dialogView.findViewById<MaterialButton>(R.id.btnCancelar)

        val adapter = ExportItemsAdapter(
            items = listaDeArchivos,
            checked = checked
        ) { index, isChecked ->

            checked[index] = isChecked
        }

        recycler.layoutManager =         GridLayoutManager(requireContext(), 2)
        recycler.adapter = adapter

        btnSeleccionarTodos.setOnClickListener {
            for (i in checked.indices) {
                checked[i] = true
            }
            adapter.notifyDataSetChanged()
        }

        btnDeseleccionarTodos.setOnClickListener {
            for (i in checked.indices) {
                checked[i] = false
            }
            adapter.notifyDataSetChanged()
        }
        val dialog = androidx.appcompat.app.AlertDialog.Builder(
            requireContext(),
            R.style.ThemeOverlay_Comunic_AlertDialog
        )
            .setView(dialogView)
            .create()

        btnExportar.setOnClickListener {
            val elementosSeleccionados =
                listaDeArchivos.filterIndexed { index, _ ->
                    checked[index]
                }
            if (elementosSeleccionados.isEmpty()) {
                Toast.makeText(
                    requireContext(),
                    "No seleccionaste ningún elemento",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            dialog.dismiss()
            mostrarResumenSeleccion(
                elementosSeleccionados,
                checked
            )
        }

        btnCancelar.setOnClickListener {
            dialog.dismiss()
        }


        // =========================
        // EXPORTAR LISTAS
        // =========================

        val recyclerListas =  dialogView.findViewById<RecyclerView>(R.id.recyclerListas)
        val btnExportarListas =      dialogView.findViewById<MaterialButton>(R.id.btnExportarListas)
        val btnSeleccionarTodas = dialogView.findViewById<MaterialButton>(R.id.btnSeleccionarTodas)
        val btnDeseleccionarTodas = dialogView.findViewById<MaterialButton>(R.id.btnDeseleccionarTodas)

        viewLifecycleOwner.lifecycleScope.launch { val categorias = db.categoryDao().getUserActive()
            val cantidades = categorias.associate { categoria ->
                categoria.categoryId to
                        db.categoryDao()
                            .getItemKeysForCategory(categoria.categoryId)
                            .size
            }

            val checkedListas = BooleanArray(categorias.size)
            val previews =
                categorias.associate { categoria ->
                    val itemKeys =
                        db.categoryDao()
                            .getItemKeysForCategory(categoria.categoryId)
                    val previewItems =
                        itemKeys.mapNotNull { key ->
                            resolveItemKeyToItemLista(
                                requireContext(),
                                key
                            )
                        }

                    categoria.categoryId to previewItems
                }
            val adapterListas = ExportListasAdapter(
                context = requireContext(),
                items = categorias,
                cantidades = cantidades,
                previews = previews,
                checked = checkedListas
            ) { index, isChecked ->

                checkedListas[index] = isChecked
            }

            recyclerListas.layoutManager = LinearLayoutManager(requireContext())
            recyclerListas.adapter = adapterListas

            btnExportarListas.setOnClickListener {
                val seleccionadas =
                    categorias.filterIndexed { index, _ ->
                        checkedListas[index]
                    }

                if (seleccionadas.isEmpty()) {
                    Toast.makeText(
                        requireContext(),
                        "No seleccionaste listas",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

                viewLifecycleOwner.lifecycleScope.launch {
                    ExportManager.exportarListasComoZip(
                        this@CategoriaDetalleFragment,
                        seleccionadas
                    )
                }
            }
            btnSeleccionarTodas.setOnClickListener {
                for (i in checkedListas.indices) {
                    checkedListas[i] = true
                }
                adapterListas.notifyDataSetChanged()
            }

            btnDeseleccionarTodas.setOnClickListener {
                for (i in checkedListas.indices) {
                    checkedListas[i] = false
                }
                adapterListas.notifyDataSetChanged()
            }
        }
        dialog.show()
    }


    private fun mostrarResumenSeleccion(
        elementosSeleccionados: List<ItemLista>,
        checked: BooleanArray
    ) {

        val dialogView = layoutInflater.inflate(
            R.layout.exp_dialogo_resumen_exportacion,
            null
        )

        val txtCantidad =
            dialogView.findViewById<TextView>(R.id.txtCantidad)

        val btnAgregarMas =
            dialogView.findViewById<MaterialButton>(R.id.btnAgregarMas)

        val btnContinuar =
            dialogView.findViewById<MaterialButton>(R.id.btnContinuar)

        val btnCancelar =
            dialogView.findViewById<MaterialButton>(R.id.btnCancelar)

        txtCantidad.text =
            "ELEMENTOS SELECCIONADOS: ${elementosSeleccionados.size}"

        val recyclerResumen =
            dialogView.findViewById<RecyclerView>(R.id.recyclerResumen)

        recyclerResumen.layoutManager =
            GridLayoutManager(requireContext(), 3)

        recyclerResumen.adapter =
            ResumenExportacionAdapter(elementosSeleccionados)

        val dialog = androidx.appcompat.app.AlertDialog.Builder(
            requireContext(),
            R.style.ThemeOverlay_Comunic_AlertDialog
        )
            .setView(dialogView)
            .create()

        btnAgregarMas.setOnClickListener {

            dialog.dismiss()

            mostrarDialogoSeleccionarElementos(checked)
        }

        btnContinuar.setOnClickListener {

            dialog.dismiss()

            mostrarDialogoTipoExportacion(
                elementosSeleccionados
            )
        }

        btnCancelar.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun mostrarDialogoTipoExportacion(
        elementosSeleccionados: List<ItemLista>
    ) {

        val dialogView = layoutInflater.inflate(
            R.layout.exp_dialogo_tipo_exportacion,
            null
        )

        val btnArchivos =
            dialogView.findViewById<MaterialButton>(R.id.btnArchivos)

        val btnZip =   dialogView.findViewById<MaterialButton>(R.id.btnZip)

        val btnCancelar = dialogView.findViewById<MaterialButton>(R.id.btnCancelar)

        val dialog = androidx.appcompat.app.AlertDialog.Builder( requireContext(),R.style.ThemeOverlay_Comunic_AlertDialog )
            .setView(dialogView)
            .create()
        btnArchivos.setOnClickListener {
            dialog.dismiss()
//            exportarElementos(elementosSeleccionados)
            ExportManager.exportarElementos(
                this,
                elementosSeleccionados
            )
        }
//        btnZip.setOnClickListener {
//            dialog.dismiss()
//            exportarElementosComoZip(elementosSeleccionados)
//        }
        btnZip.setOnClickListener {
            dialog.dismiss()
//            pedirNombreZip { nombreZip ->
            ExportManager.pedirNombreZip(this) { nombreZip ->
                //exportarElementosComoZip(
                ExportManager.exportarElementosComoZip(   this, elementosSeleccionados,nombreZip )
            }
        }
        btnCancelar.setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }



    // EXPORTAR ELEMENTOS - hasta aca

    private fun puedeModificarLista(): Boolean {
        val sessionManager = SessionManager(requireContext())
        val permissionManager = PermissionManager(sessionManager)

        if (!permissionManager.canRemoveItemFromCategory()) {
            Toast.makeText(
                requireContext(),
                "No tenés permisos para modificar esta lista",
                Toast.LENGTH_SHORT
            ).show()
            return false
        }

        return true
    }

}


