package com.comunic.fragment



import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.InputType
import android.util.Log
import android.view.ActionMode
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.comunic.data.db.AppDatabase
import com.comunic.databinding.FragmentCategoriaDetalleBinding
import kotlinx.coroutines.launch
import com.comunic.ItemLista
import com.comunic.MainActivity
import com.comunic.adapters.MediaAdapter
import com.comunic.PickItemsDialogFragment
import com.comunic.R
import com.comunic.SpeechTextResolver
import com.comunic.data.entity.CategoryItemEntity
import com.comunic.data.entity.InstalledPackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.comunic.data.mappers.resolveItemKeyToItemLista
import com.comunic.data.mappers.resolveItemKeyToItemListaAllowDisabled
import com.comunic.interfaces.MediaResultListener
import com.comunic.interfaces.OnNuevoItemListener
import com.comunic.interfaces.RecientesProvider
import java.util.Locale
import java.util.UUID

class CategoriaDetalleFragment :
    Fragment(),
    TextToSpeech.OnInitListener,
    MediaResultListener {

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




    companion object {
        fun newInstance(categoryId: String, categoryName: String) =
            CategoriaDetalleFragment().apply {
                arguments = Bundle().apply {
                    putString("categoryId", categoryId)
                    putString("categoryName", categoryName)
                }
            }
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
        audioManager =
            requireContext().getSystemService(Context.AUDIO_SERVICE) as AudioManager

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
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val placementId = db.categoryDao().findPlacementId(categoryId, itemKey)
                if (placementId != null) {
                    db.categoryDao().deletePlacement(placementId)
                }
            }
            loadCategory(categoryId)
        }
    }

    private fun eliminarSeleccionDeCategoria(categoryId: String, seleccionados: List<ItemLista>) {
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                // cada ItemLista.id == itemKey (porque ya ajustamos el resolver)
                seleccionados.forEach { item ->
                    val placementId = db.categoryDao().findPlacementId(categoryId, item.id)
                    if (placementId != null) {
                        db.categoryDao().deletePlacement(placementId)
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

                        var next = db.categoryDao().getMaxOrderIndex(categoryId) + 1
                        nuevos.forEach { item ->
                            db.categoryDao().insertCategoryItem(
                                CategoryItemEntity(
                                    placementId = UUID.randomUUID().toString(),
                                    categoryId = categoryId,
                                    itemKey = item.id,
                                    orderIndex = next++
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
            binding.btnEliminarCategoria.visibility = if (isSystem) View.GONE else View.VISIBLE

            binding.btnEliminarCategoria.setOnClickListener {
                mostrarMenuEliminar()
            }
        }
    }

    private fun mostrarMenuEliminar() {
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
        val builder = AlertDialog.Builder(requireContext(), R.style.ThemeOverlay_Comunic_AlertDialog)
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
        builder.show()
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

//    private fun configurarBotonSegunEstado() {
//
//        val habilitado = !(isSystemCategory && !isPackEnabled)
//
//        binding.btnAgregarElemento.isEnabled = habilitado
//        binding.btnCamera.isEnabled = habilitado
//        binding.btnVideo.isEnabled = habilitado
//        binding.btnGallery.isEnabled = habilitado
//
//        if (!habilitado) {
//            binding.btnAgregarElemento.setOnClickListener {
//                habilitarPack(packId)
//            }
//        }
//    }
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
                    dao.upsert(
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

                val next = db.categoryDao().getMaxOrderIndex(categoryId) + 1

                db.categoryDao().insertCategoryItem(
                    CategoryItemEntity(
                        placementId = UUID.randomUUID().toString(),
                        categoryId = categoryId,
                        itemKey = item.id,
                        orderIndex = next
                    )
                )
            }

            loadCategory(categoryId)
        }
    }

    override fun onMediaCreated(item: ItemLista) {
        agregarItemACategoria(item)
    }

}


