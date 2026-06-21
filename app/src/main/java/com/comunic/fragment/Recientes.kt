package com.comunic.fragment

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.InputType
import android.transition.TransitionManager
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.comunic.AddToListHost
import com.comunic.ItemKey
import com.comunic.ItemLista
import com.comunic.adapters.MediaAdapter
import com.comunic.MenuHandler
import com.comunic.R
import com.comunic.SpeechTextResolver
import com.comunic.adapters.ExportItemsAdapter
import com.comunic.adapters.ExportListasAdapter
import com.comunic.export.exportitems.ExportManager
import com.comunic.adapters.ResumenExportacionAdapter
import com.comunic.adapters.SimpleListCheckAdapter
import com.comunic.databinding.FragmentRecientesBinding
import com.comunic.fragment.HomeFragment.Companion.PERMISSION_REQUEST_CODE
import com.comunic.data.db.AppDatabase
import com.comunic.data.db.MediaRepository
import com.comunic.data.db.PackRepository
import com.comunic.data.entity.CategoryEntity
import com.comunic.data.entity.CategoryItemEntity
import com.comunic.data.entity.MediaEntity
import com.comunic.data.mappers.resolveItemKeyToItemLista
import com.comunic.data.mappers.toItemLista
import com.comunic.fragment.HomeFragment.Companion
import com.comunic.fragment.HomeFragment.Companion.REQUEST_CODE_IMPORTAR_ZIP
import com.comunic.interfaces.MediaResultListener
import com.comunic.interfaces.OnNuevoItemListener
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
//import io.opencensus.stats.View
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import com.comunic.interfaces.ZipImportListener
import com.comunic.session.PermissionManager
import com.comunic.session.SessionManager
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CompletableDeferred
import org.json.JSONObject
import androidx.lifecycle.lifecycleScope
import com.comunic.sync.SyncEvents
import kotlinx.coroutines.launch


class Recientes : Fragment(),
    TextToSpeech.OnInitListener,
    MediaAdapter.OnEliminarSeleccionListener,
    MenuHandler,
    AddToListHost,
    ZipImportListener, MediaResultListener {

    private var _binding: FragmentRecientesBinding? = null
    private val binding get() = _binding!!

    // Esta función se llama desde MainActivity cuando se presiona el botón de agregar imagen
    override fun abrirSelectorDeImagen() {
//        opcionesDeImagen() // Tu función existente que muestra el diálogo de imagen/cámara
    }

    private lateinit var mediaAdapter: MediaAdapter
    private val listaDeArchivos: MutableList<ItemLista> = mutableListOf()
    lateinit var escucharPalabra: TextToSpeech
    private lateinit var audioManager: AudioManager
    private var volumenOriginal = -1
    //private lateinit var googleSignInClient: GoogleSignInClient
    //private lateinit var driveServiceHelper: DriveServiceHelper
    private lateinit var selectionPanel: LinearLayout
    private lateinit var selectionCountText: TextView
    private lateinit var deleteSelectedButton: Button
    private lateinit var cancelSelectionButton: Button

    // para integrar pack
    private lateinit var db: AppDatabase


    var nuevoItemListener: OnNuevoItemListener? = null


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): android.view.View? {
        _binding = FragmentRecientesBinding.inflate(inflater, container, false)


        db = AppDatabase.getDatabase(requireContext())

//        mediaAdapter = MediaAdapter(listaDeArchivos, ::eliminar) { nombre ->
//            audio(nombre)
//        }
        escucharPalabra = TextToSpeech(requireContext(), this)
        audioManager = requireContext().getSystemService(Context.AUDIO_SERVICE) as AudioManager
        escucharPalabra = TextToSpeech(requireContext(), this)


        val recyclerView = binding.recyclerView
        val orderButton = binding.orderButton // Botón para ordenar

        //recyclerView.layoutManager = LinearLayoutManager(this)  // 1 columna
        recyclerView.layoutManager = GridLayoutManager(requireContext(), 3)  // 4 columnas
//        mediaAdapter = MediaAdapter(listaDeArchivos, ::eliminar) { id ->
//            audio(id)
//        }
//        recyclerView.adapter = mediaAdapter
        mediaAdapter = MediaAdapter(
            listaDeArchivos,
            ::eliminar,
            { id -> audio(id) },
            onLongClick = { item ->
                mostrarDialogoAgregarAListas(item)
            },
            mostrarMenuEnDialog = true
        )
        binding.recyclerView.adapter = mediaAdapter

        loadImageData() // Cargar los datos (imágenes/videos)
//        val ordenGuardado = getOrdenSeleccionado()
//        aplicarOrden(ordenGuardado, orderButton)


        checkReadPermissionIfNeeded() // permisos

        orderButton.setOnClickListener { view ->
            val popup = PopupMenu(requireContext(), view)
            popup.menuInflater.inflate(R.menu.menu_filtro_recientes, popup.menu)

            popup.setOnMenuItemClickListener { item ->
                saveOrdenSeleccionado(item.itemId)
                aplicarOrden(item.itemId, orderButton)
                true
            }

            popup.show()
        }


        // Eliminacion de elementos
        //setupSelectionPanel() // seleccion y eliminacion
        selectionPanel = binding.selectionPanel
        selectionCountText = binding.selectionCountText
        deleteSelectedButton = binding.deleteSelectedButton
        cancelSelectionButton = binding.cancelSelectionButton
        deleteSelectedButton.setOnClickListener {
            val seleccionados = mediaAdapter.obtenerSeleccionados()
            onEliminarSeleccionSolicitada(seleccionados)
        }
        mediaAdapter.onSeleccionCambio = { count ->
            actualizarPanelSeleccion(count)
        }
        binding.cancelSelectionButton.setOnClickListener {
            cancelarModoEliminacion()
        }

        /// fin eliminacion
//        cancelSelectionButton = binding.cancelSelectionButton
//        binding.cancelSelectionButton.setOnClickListener {
//            mediaAdapter.cancelarModoEliminacion()
//            actualizarPanelSeleccion(0)
//            //selectionPanel.visibility = View.GONE
//        }

        binding.btnCamera.setOnClickListener {
            (activity as? MainActivity)?.launchImageCapture()
        }

        binding.btnVideo.setOnClickListener {
            (activity as? MainActivity)?.launchVideoCapture()
        }

        binding.btnGallery.setOnClickListener {
            (activity as? MainActivity)?.openGallery()
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            if (modoEliminacionActivo) {
                cancelarModoEliminacion()
            } else {
                isEnabled = false
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            SyncEvents.dataChanged.collect {
                Log.d("SYNC", "Recargando Recientes")
                loadImageData()
            }
        }

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        (activity as? MainActivity)?.setMediaResultListener(this)
        (activity as? MainActivity)
            ?.setZipImportListener(this)
    }

    override fun onPause() {
        super.onPause()
        (activity as? MainActivity)?.setMediaResultListener(null)
        (activity as? MainActivity)
            ?.setZipImportListener(null)
    }

    private fun checkReadPermissionIfNeeded() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(requireContext(), permission) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(permission), PERMISSION_REQUEST_CODE)
        }
    }




    override fun onInit(status: Int) {

        if (status == TextToSpeech.SUCCESS) {

            val idioma = escucharPalabra.setLanguage(Locale("es","AR"))

            if (idioma == TextToSpeech.LANG_MISSING_DATA ||
                idioma == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                Log.e("TextToSpeech", "Error con el idioma")
            }

            escucharPalabra.setOnUtteranceProgressListener(
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

    private fun audio(itemKeyOrId: String) {
        Log.d(
            "AUDIO_DEBUG",
            "audio() recibido -> $itemKeyOrId"
        )

        if (!::escucharPalabra.isInitialized) return

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {

            val texto = SpeechTextResolver.resolve(db, itemKeyOrId)


            withContext(Dispatchers.Main) {

                try {

                    // Guardar volumen actual SOLO si no estaba hablando
                    if (!escucharPalabra.isSpeaking) {

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

                    // Subir volumen
                    audioManager.setStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        maxVolumen,
                        0
                    )

                    // Pequeño delay para asegurar aplicación
                    Handler(Looper.getMainLooper()).postDelayed({

                        escucharPalabra.speak(
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


    override fun onDestroy() {
        if (::escucharPalabra.isInitialized) {
            try {
                escucharPalabra.shutdown()
            } catch (_: Exception) {
            }
        }
        super.onDestroy()
    }

    // ELIMINACION INDIVIDUAL DESDE ITEM_MEDIA.KT
    private fun eliminar(nombre: String) {
        //val index = listaDeArchivos.indexOfFirst { it.first == nombre }
        val index = listaDeArchivos.indexOfFirst { it.nombre == nombre }
        if (index != -1) {
            //val (_, uri, _) = listaDeArchivos[index]
            val item = listaDeArchivos[index]
            val uri = item.uri
            try {
                when (uri.scheme) {
                    "content" -> {
                        // Manejo de URI en MediaStore
                        val rowsDeleted = requireContext().contentResolver.delete(uri, null, null)
                        if (rowsDeleted > 0) {
                            Log.d("Eliminar", "Eliminado de MediaStore: $nombre")
                        } else {
                            Toast.makeText(requireContext(), "No se pudo eliminar el archivo de MediaStore: $nombre", Toast.LENGTH_SHORT).show()
                        }
                    }
                    "file" -> {
                        // Manejo de URI local
                        val file = File(uri.path ?: "")
                        if (file.exists()) {
                            if (file.delete()) { Log.d("Eliminar", "Archivo local eliminado: $nombre")
                            } else { Toast.makeText(requireContext(), "No se pudo eliminar el archivo local: $nombre", Toast.LENGTH_SHORT).show()
                            }
                        } else { Toast.makeText(requireContext(), "El archivo local no existe: $nombre", Toast.LENGTH_SHORT).show()
                        }
                    }
                    else -> { Toast.makeText(requireContext(), "URI desconocida: $nombre", Toast.LENGTH_SHORT).show()
                    }
                }
                listaDeArchivos.removeAt(index) // Elimina el elemento de la lista
                mediaAdapter.notifyItemRemoved(index) // Notifica al adaptador sobre el cambio
                eliminarDatosDeMedia(nombre) // Actualiza el almacenamiento persistente

                Toast.makeText(requireContext(), "Eliminado: $nombre", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) { Toast.makeText(requireContext(), "Error al eliminar el archivo: $nombre", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        } else {
            Toast.makeText(requireContext(), "No se encontró el elemento: $nombre", Toast.LENGTH_SHORT).show()
        }
    }

    private fun eliminarDatosDeMedia(nombreArchivo: String) {
        val sharedPreferences = requireContext().getSharedPreferences("media_data", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.remove(nombreArchivo)
        editor.remove("$nombreArchivo|type")
        editor.apply()
    }
    // ELIMINACION INDIVIDUAL DESDE ITEM_MEDIA.KT

    private fun aplicarOrdenActual() {
        val orden = getOrdenSeleccionado()
        aplicarOrden(orden, binding.orderButton)
    }

    private fun loadImageData() {
        viewLifecycleOwner.lifecycleScope.launch {
            //val ordenGuardado = getOrdenSeleccionado()


            // 0) Asegurar que el pack básico exista (idempotente)
            val db = AppDatabase.getDatabase(requireContext())
            PackRepository(requireContext(), db).ensureBasicPackInstalled() // idempotente, no hace nada si ya está
            MediaRepository(requireContext(), db).ensureLocalMediaIndexed() // sincronizar carpeta interna
            withContext(Dispatchers.IO) {
                val dao = db.pictogramDao()
                val ipDao = db.installedPackDao()

                // 1) Cuántos pictos hay por packId (para ver si existe "basic")
                val counts = dao.debugCountPictosByPack()
                Log.d("PACK_DEBUG", "Pictos por packId: $counts")

                // 2) Estado enabled de los packs nuevos (y si existe "basic")
                Log.d("PACK_DEBUG", "enabled basic_core=${ipDao.isEnabled("basic_core")}")
                Log.d("PACK_DEBUG", "enabled basic_food=${ipDao.isEnabled("basic_food")}")
                Log.d("PACK_DEBUG", "enabled basic=${ipDao.isEnabled("basic")}") // debería ser null si lo borraste
            }

            // 1) Limpiar lista
            listaDeArchivos.clear()

            // 2) Cargar USER media desde /files/media (tu lógica original)
            // Tu carpeta interna "media" ya está sincronizada con la base de datos gracias a MediaRepository.ensureLocalMediaIndexed()
            val userId =
                SessionManager(requireContext())
                    .getCurrentUserId()

            val mediaItems =
                db.mediaDao()
                    .getActiveMediaForUser(userId)
            val userMediaAsItems = mediaItems.map { media ->
//                ItemLista(
//                    id = ItemKey.media(media.displayName), // temporal: compatibilidad con listas existentes
//                    nombre = media.displayName,
//                    uri = Uri.parse(media.localUri),
//                    esImagen = media.mediaType == "image",
//                    timestamp = media.createdAt
//                )
                ItemLista(
                    id = ItemKey.media(media.mediaId), // id consistente con la base de datos: MEDIA:UUID
                    nombre = media.displayName,
                    uri = Uri.parse(media.localUri),
                    esImagen = media.mediaType == "image",
                    timestamp = media.createdAt
                )
            }

            listaDeArchivos.addAll(userMediaAsItems)

            // 3) Cargar pictos de packs habilitados
            val pictos = db.pictogramDao().getEnabledPictosUi()

            val pictosAsItems = pictos.map { row ->
                row.toItemLista(timestamp = 0L)
                    .copy(id = ItemKey.picto(row.pictogramId)) // id consistente PIC:xxx
            }

            listaDeArchivos.addAll(pictosAsItems)

            // 4) Notificar
            mediaAdapter.notifyDataSetChanged()

            withContext(Dispatchers.Main) {
                aplicarOrdenActual()
            }
        }
    }

    /*fun solicitarContrasena() {

        val builder = AlertDialog.Builder(requireContext(),
            R.style.ThemeOverlay_Comunic_AlertDialog
        )
        builder.setTitle("Ingrese la contraseña")

        val input = EditText(requireContext())
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        builder.setView(input)

        builder.setPositiveButton("Aceptar") { _, _ ->
            val passwordIngresada = input.text.toString()
            if (passwordIngresada == "1234") { // Reemplaza con la contraseña correcta
                activarModoEliminacion()
            } else {
                Toast.makeText(requireContext(), "Contraseña incorrecta", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Cancelar") { dialog, _ -> dialog.cancel() }
        builder.show()
    }*/

    fun solicitarContrasena() {

        // si ya tengo los permisos, no necesito solicitar la contraseña
        val sessionManager = SessionManager(requireContext())
        val permissionManager = PermissionManager(sessionManager)

        if (!permissionManager.canDeleteMedia()) {

            Toast.makeText(
                requireContext(),
                "No tenés permisos para eliminar",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        activarModoEliminacion()
    }

    private var modoEliminacionActivo = false
    private fun activarModoEliminacion() {
        modoEliminacionActivo = true
        mediaAdapter.setModoEliminacion(true)
        actualizarPanelSeleccion(mediaAdapter.obtenerSeleccionados().size)
        Toast.makeText(requireContext(), "Modo eliminación activado", Toast.LENGTH_SHORT).show()
    }

    override fun onEliminarSeleccionSolicitada(seleccionados: List<ItemLista>) {
        AlertDialog.Builder(requireContext(),
            R.style.ThemeOverlay_Comunic_AlertDialog
        )
            .setTitle("¿Eliminar elementos seleccionados?")
            .setMessage("Se eliminarán ${seleccionados.size} elementos. ¿Desea continuar?")
            .setPositiveButton("Eliminar") { _, _ ->
                eliminarElementosSeleccionados(seleccionados)
            }
            .setNegativeButton("Cancelar") { _, _ ->
                cancelarModoEliminacion()
            }
            .show()
    }

  /*  private fun eliminarElementosSeleccionados(lista: List<ItemLista>) {
        val cr = requireContext().contentResolver
        val itemsEliminados = mutableListOf<ItemLista>()

        lista.forEach { item ->
            val uri = item.uri
            var ok = false
            try {
                ok = when (uri.scheme) {
                    "content" -> {
                        // MediaStore / SAF
                        cr.delete(uri, null, null) > 0
                    }
                    "file" -> {
                        // Archivo directo
                        val path = uri.path
                        if (!path.isNullOrEmpty()) File(path).delete() else false
                    }
                    else -> {
                        // Fallback a tu carpeta “media” interna si fuese el caso
                        val carpeta = File(requireContext().filesDir, "media")
                        File(carpeta, item.nombre).delete()
                    }
                }
            } catch (se: SecurityException) {
                // Android 10+: si no fue creado por tu app podrías necesitar pedir permiso:
                // val req = MediaStore.createDeleteRequest(cr, listOf(uri))
                // startIntentSenderForResult(req.intentSender, REQ_DELETE, null, 0, 0, 0)
                ok = false
            }

            if (ok) {
                itemsEliminados.add(item)
                Log.d("Eliminar", "Eliminado: ${item.nombre}")
            } else {
                Log.e("Eliminar", "No se pudo eliminar: ${item.nombre}")
            }
        }

        // Sacarlos del adapter y refrescar
        mediaAdapter.eliminarItems(itemsEliminados)

        // Cerrar UI de selección
        //selectionPanel.visibility = View.GONE
       cancelarModoEliminacion()
        Toast.makeText(requireContext(), "${itemsEliminados.size} elementos eliminados", Toast.LENGTH_SHORT).show()
    }*/

    private fun eliminarElementosSeleccionados(lista: List<ItemLista>) {
        viewLifecycleOwner.lifecycleScope.launch {

            val itemsEliminados = withContext(Dispatchers.IO) {

                val eliminados = mutableListOf<ItemLista>()
                val now = System.currentTimeMillis()

                lista.forEach { item ->

                    if (!ItemKey.isMedia(item.id)) {
                        return@forEach
                    }

                    val mediaId = ItemKey.mediaId(item.id)

                    db.mediaDao().softDelete(
                        mediaId = mediaId,
                        updatedAt = now
                    )

                    eliminados.add(item)

                    Log.d(
                        "SOFT_DELETE",
                        "Media enviado a papelera: ${item.nombre} id=${item.id}"
                    )
                }

                eliminados
            }

            mediaAdapter.eliminarItems(itemsEliminados)

            cancelarModoEliminacion()

            Toast.makeText(
                requireContext(),
                "${itemsEliminados.size} elementos enviados a papelera",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun cancelarModoEliminacion() {
        modoEliminacionActivo = false
        mediaAdapter.setModoEliminacion(false)
        actualizarPanelSeleccion(0) // reinicio contador
        Toast.makeText(requireContext(), "Modo eliminación cancelado", Toast.LENGTH_SHORT).show()
    }

    fun actualizarPanelSeleccion(cantidad: Int) {
        TransitionManager.beginDelayedTransition(binding.root)
        val enModoSeleccion = cantidad > 0

        // Panel superior de selección
        selectionPanel.visibility =
            if (enModoSeleccion) View.VISIBLE else View.GONE

        // Texto contador
        selectionCountText.text =
            "$cantidad elemento${if (cantidad > 1) "s" else ""} seleccionad${if (cantidad > 1) "os" else "o"}"

        // ✅ Ocultar acciones de agregar mientras se selecciona
        binding.btnCamera.visibility =
            if (enModoSeleccion) View.GONE else View.VISIBLE

        binding.btnVideo.visibility =
            if (enModoSeleccion) View.GONE else View.VISIBLE

        binding.btnGallery.visibility =
            if (enModoSeleccion) View.GONE else View.VISIBLE

        // ✅ Desactivar ordenar para evitar estados inconsistentes
        binding.orderButton.isEnabled = !enModoSeleccion
        binding.orderButton.alpha = if (enModoSeleccion) 0.4f else 1f
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
        val dialog = AlertDialog.Builder(
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
        val userId =
            SessionManager(requireContext())
                .getCurrentUserId()
        viewLifecycleOwner.lifecycleScope.launch { val categorias = db.categoryDao().getUserActive()
            val cantidades = categorias.associate { categoria ->
                    categoria.categoryId to
                            db.categoryDao()
                                .getItemKeysForCategory(categoria.categoryId, userId)
                                .size
                }

            val checkedListas = BooleanArray(categorias.size)
            val previews =
                categorias.associate { categoria ->
                    val itemKeys =
                        db.categoryDao()
                            .getItemKeysForCategory(categoria.categoryId, userId)
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
                        this@Recientes,
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

    fun mostrarResumenSeleccion(
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

        val dialog = AlertDialog.Builder(
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


//    private fun mostrarDialogoTipoExportacion(elementosSeleccionados: List<ItemLista>) {
//        AlertDialog.Builder(requireContext(),
//            R.style.ThemeOverlay_Comunic_AlertDialog
//        )
//            .setTitle("¿Cómo querés exportarlos?")
//            .setItems(arrayOf("Compartir archivos sueltos", "Exportar como ZIP")) { _, which ->
//                when (which) {
//                    0 -> exportarElementos(elementosSeleccionados) // Sueltos
//                    1 -> exportarElementosComoZip(elementosSeleccionados) // Como ZIP
//                }
//            }
//            .show()
//    }
     fun mostrarDialogoTipoExportacion(
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

        val dialog = AlertDialog.Builder( requireContext(),R.style.ThemeOverlay_Comunic_AlertDialog )
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


    // IMPORTAR PAQUETES

    fun importarArchivos() {

        val dialogView = layoutInflater.inflate(
            R.layout.imp_dialog_importar,
            null
        )

        val dialog = AlertDialog.Builder(
            requireContext(),
            R.style.ThemeOverlay_Comunic_AlertDialog
        )
            .setView(dialogView)
            .create()

        val btnImportarLista =
            dialogView.findViewById<MaterialButton>(R.id.btnImportarLista)

        val btnImportarElementos =
            dialogView.findViewById<MaterialButton>(R.id.btnImportarElementos)

        val btnCancelar =
            dialogView.findViewById<MaterialButton>(R.id.btnCancelar)

        btnImportarLista.setOnClickListener {

            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "application/zip"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivityForResult(
                intent,
                Recientes.REQUEST_CODE_IMPORTAR_LISTA
            )

            dialog.dismiss()
        }

        btnImportarElementos.setOnClickListener {

            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "application/zip"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivityForResult(
                intent,
                Recientes.REQUEST_CODE_IMPORTAR_ELEMENTOS
            )

            dialog.dismiss()
        }

        btnCancelar.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    override fun importarElementosDesdeZip(uri: Uri) {
        try {
            // Abrir el archivo ZIP
            val inputStream = requireContext().contentResolver.openInputStream(uri) ?: return
            val zipInputStream = ZipInputStream(BufferedInputStream(inputStream))

            val mediaDir = File(requireContext().filesDir, "media") // Carpeta "media" en el almacenamiento interno
            if (!mediaDir.exists()) {
                mediaDir.mkdirs() // Crear la carpeta si no existe
            }

            var entry: ZipEntry?
            while (zipInputStream.nextEntry.also { entry = it } != null) {
                val extension = entry!!.name.substringAfterLast(".", "").lowercase(Locale.ROOT) // valido extension de archivo
                val esImagen = when (extension) {
                    in listOf("jpg", "jpeg", "png", "gif", "bmp", "webp") -> true
                    in listOf("mp4", "mkv", "avi", "mov", "webm") -> false
                    else -> {
                        mostrarSnackbar(
                            "Archivo no soportado: $extension",
                            false
                        )
                        continue
                    }
                }

                val nombreSinExtension = entry!!.name.substringBeforeLast(".")
                val nombreFinal = "$nombreSinExtension.${if (esImagen) "jpg" else "mp4"}" // le doy la extension final, el nombre es el q levanta sin la extension

                val archivoDestino = File(mediaDir, nombreFinal)

                if (archivoDestino.exists()) { // para evitar sobreescribir archivos
                    mostrarSnackbar(
                        "Ya existe un archivo llamado $nombreSinExtension",
                        false
                    )
                    continue
                }
                // extraigo zip y lo guardo en la carpeta media (archivoDestino me lleva a mediaDir)
                val outputStream = FileOutputStream(archivoDestino)
                zipInputStream.copyTo(outputStream)
                zipInputStream.closeEntry()
                outputStream.close()

                val uriGuardado = Uri.fromFile(archivoDestino)

               /* val item = ItemLista(
                    id = ItemKey.media(nombreSinExtension),
                    nombre = nombreSinExtension,
                    uri = uriGuardado,
                    esImagen = esImagen,
                    timestamp = System.currentTimeMillis()
                )

                listaDeArchivos.add(item) // agrego archivos a la lista actual
                saveMediaData(nombreSinExtension, uriGuardado, esImagen) // guardo en el almacenamiento persistente (sharedPreferences)
                mediaAdapter.notifyDataSetChanged()*/
                val now = System.currentTimeMillis()

                val media = MediaEntity(
                    mediaId = UUID.randomUUID().toString(),
                    displayName = nombreSinExtension,
                    localUri = uriGuardado.toString(),
                    mediaType = if (esImagen) "image" else "video",
                    createdAt = now,
                    updatedAt = now,
                    isDeleted = false,
                    ownerUserId =
                    SessionManager(requireContext())
                        .getCurrentUserId()
                )

                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    db.mediaDao().upsert(media)
                }

                val item = ItemLista(
                    id = ItemKey.media(media.mediaId),
                    nombre = media.displayName,
                    uri = uriGuardado,
                    esImagen = esImagen,
                    timestamp = media.createdAt
                )

                listaDeArchivos.add(item)
                saveMediaData(nombreSinExtension, uriGuardado, esImagen)
                mediaAdapter.notifyDataSetChanged()
            }

            zipInputStream.close()
            mediaAdapter.notifyDataSetChanged()
            aplicarOrdenActual()
            mostrarSnackbar(
                "Importación exitosa",
                true
            )
        } catch (e: Exception) {
            e.printStackTrace()
            mostrarSnackbar(
                "Error al importar ZIP",
                false
            )        }
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(
            requestCode,
            resultCode,
            data
        )
        if (resultCode != AppCompatActivity.RESULT_OK) {
            return
        }
        val uri = data?.data ?: return
        when (requestCode) {
            REQUEST_CODE_IMPORTAR_ELEMENTOS -> {
                importarElementosDesdeZip(uri)
            }

            REQUEST_CODE_IMPORTAR_LISTA -> {
                viewLifecycleOwner.lifecycleScope.launch {

                    val esLista = esZipConListas(uri)
                    if (!esLista) {
                        mostrarSnackbar(
                            "El ZIP no contiene listas válidas",
                            false
                        )
                        return@launch
                    }
                    importarListaDesdeZip(uri)
                }
            }
        }
    }

    private suspend fun esZipConListas(uri: Uri): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val input =
                    requireContext()
                        .contentResolver
                        .openInputStream(uri)
                        ?: return@withContext false
                val zip =
                    ZipInputStream(
                        BufferedInputStream(input)
                    )
                var entry: ZipEntry?
                while (
                    zip.nextEntry.also { entry = it } != null
                ) {
                    val name =entry!!.name.lowercase()
                    if (
                        name.endsWith("metadata.json")
                    ) {
                        zip.close()
                        return@withContext true
                    }
                }
                zip.close()
                false

            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    private fun importarListaDesdeZip(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {

            try {

                val inputStream =
                    requireContext().contentResolver.openInputStream(uri)
                        ?: return@launch

                val zipInputStream =
                    ZipInputStream(BufferedInputStream(inputStream))

                val mediaDir = File(requireContext().filesDir, "media")
                if (!mediaDir.exists()) mediaDir.mkdirs()

                data class CategoriaImportada(
                    val originalFolder: String,
                    var nombre: String,
                    val categoryId: String
                )

                val categoriasMap =
                    mutableMapOf<String, CategoriaImportada>()

                val itemsImportados =
                    mutableListOf<ItemLista>()

                var entry: ZipEntry?

                while (zipInputStream.nextEntry.also { entry = it } != null) {

                    val entryName = entry!!.name

                    if (entry!!.isDirectory) continue

                    // =========================
                    // METADATA
                    // =========================

                    if (entryName.endsWith("metadata.json")) {

                        val folderName =
                            entryName.substringBefore("/")

                        val json = buildString {
                            val buffer = ByteArray(1024)
                            var len: Int

                            while (zipInputStream.read(buffer).also { len = it } > 0) {
                                append(String(buffer, 0, len))
                            }
                        }

                        val obj = JSONObject(json)

                        val nombreOriginal = obj.optString("name", "Lista importada")

                        var nombreFinal = nombreOriginal
                        var reemplazar = false

                        /*withContext(Dispatchers.Main) {
                            val deferred = CompletableDeferred<Unit>()

                            val input =TextInputEditText(requireContext())
                            input.setText(nombreOriginal)
                            AlertDialog.Builder(
                                requireContext(),
                                R.style.ThemeOverlay_Comunic_AlertDialog
                            )
                                .setTitle("Importar lista")
                                .setMessage("Nombre de la lista")
                                .setView(input)
                                .setPositiveButton("Continuar") { _, _ ->
                                    nombreFinal =
                                        input.text
                                            ?.toString()
                                            ?.trim()
                                            .orEmpty()
                                    deferred.complete(Unit)  }

                                .setNegativeButton("Cancelar") { _, _ -> deferred.complete(Unit)  }

                                .setOnCancelListener { deferred.complete(Unit)}

                                .show()

                            deferred.await()
                        }

                        if (nombreFinal.isBlank()) {
                            continue
                        }*/

                        var cancelado = false

                        withContext(Dispatchers.Main) {

                            val deferred = CompletableDeferred<Unit>()

                            val dialogView = layoutInflater.inflate(
                                R.layout.imp_dialog_input,
                                null
                            )

                            val dialog = AlertDialog.Builder(
                                requireContext(),
                                R.style.ThemeOverlay_Comunic_AlertDialog
                            )
                                .setView(dialogView)
                                .create()

                            val txtTitulo = dialogView.findViewById<TextView>(R.id.txtTitulo)
                            val editText = dialogView.findViewById<TextInputEditText>(R.id.editText)
                            val btnAceptar =dialogView.findViewById<MaterialButton>(R.id.btnAceptar)
                            val btnCancelar =  dialogView.findViewById<MaterialButton>(R.id.btnCancelar)

                            txtTitulo.text = "IMPORTAR LISTA"
                            editText.setText(nombreOriginal)
                            btnAceptar.setOnClickListener {
                                nombreFinal =
                                    editText.text
                                        ?.toString()
                                        ?.trim()
                                        .orEmpty()
                                deferred.complete(Unit)
                                dialog.dismiss()
                            }

                            btnCancelar.setOnClickListener {
                                cancelado = true
                                deferred.complete(Unit)
                                dialog.dismiss()
                            }

                            dialog.setOnCancelListener {
                                cancelado = true
                                deferred.complete(Unit)
                            }

                            dialog.show()
                            deferred.await()
                        }

                        if (cancelado) {
                            zipInputStream.close()
                            return@launch
                        }
                        if (nombreFinal.isBlank()) {
                            continue
                        }
                        val userId =
                            SessionManager(requireContext())
                                .getCurrentUserId()

                        val existing =
                            db.categoryDao()
                                .getCategoryByName(
                                    nombreFinal,
                                    userId
                                )
                        if (existing != null) {
                            withContext(Dispatchers.Main) {
                                val deferred =CompletableDeferred<Pair<String?, Boolean>?>()
                                val opciones = arrayOf(
                                    "Reemplazar lista existente",
                                    "Conservar ambas",
                                    "Renombrar nueva lista",
                                    "Cancelar importación"
                                )
                                val dialogView = layoutInflater.inflate(
                                    R.layout.imp_dialog_acciones_vertical,
                                    null
                                )

                                val dialog = AlertDialog.Builder(
                                    requireContext(),
                                    R.style.ThemeOverlay_Comunic_AlertDialog
                                )
                                    .setView(dialogView)
                                    .create()

                                val txtTitulo =
                                    dialogView.findViewById<TextView>(R.id.txtTitulo)

                                val btn1 =
                                    dialogView.findViewById<MaterialButton>(R.id.btnAccion1)

                                val btn2 =
                                    dialogView.findViewById<MaterialButton>(R.id.btnAccion2)

                                val btn3 =
                                    dialogView.findViewById<MaterialButton>(R.id.btnAccion3)

                                val btn4 =
                                    dialogView.findViewById<MaterialButton>(R.id.btnAccion4)

                                val btnCancelar =
                                    dialogView.findViewById<MaterialButton>(R.id.btnCancelar)

                                txtTitulo.text =
                                    "La lista \"$nombreFinal\" ya existe"

                                btn1.text =
                                    "REEMPLAZAR LISTA EXISTENTE"

                                btn2.text =
                                    "CONSERVAR AMBAS"

                                btn3.text =
                                    "RENOMBRAR NUEVA LISTA"

                                btn4.visibility = View.GONE

                                btn1.setOnClickListener {

                                    deferred.complete(
                                        Pair(
                                            nombreFinal,
                                            true
                                        )
                                    )

                                    dialog.dismiss()
                                }

                                btn2.setOnClickListener {

                                    viewLifecycleOwner.lifecycleScope.launch {

                                        var contador = 1

                                        var nuevoNombre =
                                            "$nombreFinal ($contador)"

                                        while (
                                            db.categoryDao()
                                                .getCategoryByName(
                                                    nuevoNombre,
                                                    userId
                                                ) != null
                                        ) {

                                            contador++

                                            nuevoNombre =
                                                "$nombreFinal ($contador)"
                                        }

                                        deferred.complete(
                                            Pair(
                                                nuevoNombre,
                                                false
                                            )
                                        )

                                        dialog.dismiss()
                                    }
                                }

                                btn3.setOnClickListener {

                                    dialog.dismiss()

                                    val renameView = layoutInflater.inflate(
                                        R.layout.imp_dialog_input,
                                        null
                                    )

                                    val renameDialog = AlertDialog.Builder(
                                        requireContext(),
                                        R.style.ThemeOverlay_Comunic_AlertDialog
                                    )
                                        .setView(renameView)
                                        .create()

                                    val txtTituloRename =
                                        renameView.findViewById<TextView>(R.id.txtTitulo)

                                    val editText =
                                        renameView.findViewById<TextInputEditText>(R.id.editText)

                                    val btnAceptarRename =
                                        renameView.findViewById<MaterialButton>(R.id.btnAceptar)

                                    val btnCancelarRename =
                                        renameView.findViewById<MaterialButton>(R.id.btnCancelar)

                                    txtTituloRename.text =
                                        "NUEVO NOMBRE"

                                    editText.setText("$nombreFinal copia")

                                    btnAceptarRename.setOnClickListener {

                                        deferred.complete(
                                            Pair(
                                                editText.text
                                                    ?.toString()
                                                    ?.trim(),
                                                false
                                            )
                                        )

                                        renameDialog.dismiss()
                                    }

                                    btnCancelarRename.setOnClickListener {

                                        deferred.complete(null)

                                        renameDialog.dismiss()
                                    }

                                    renameDialog.show()
                                }

                                btnCancelar.setOnClickListener {

                                    deferred.complete(null)

                                    dialog.dismiss()
                                }

                                dialog.setOnCancelListener {
                                    deferred.complete(null)
                                }

                                dialog.show()
                                val resultado = deferred.await()

                                if (resultado == null) {
                                    return@withContext
                                }

                                nombreFinal = resultado.first ?: nombreFinal
                                reemplazar =resultado.second
                            }

                            if (reemplazar) {
                                db.categoryDao()
                                    .softDeleteCategory(
                                        existing.categoryId
                                    )
                            }
                        }
                        val now = System.currentTimeMillis()
                        val nuevaCategoriaId = "user_" + UUID.randomUUID()
                        val order = db.categoryDao().getMaxCategoryOrderIndex() + 1
                        db.categoryDao().upsert(
                            CategoryEntity(
                                categoryId = nuevaCategoriaId,
                                name = nombreFinal,
                                orderIndex = order,
                                createdAt = now,
                                updatedAt = now,
                                ownerUserId =
                                SessionManager(requireContext())
                                    .getCurrentUserId()
                            )
                        )

                        categoriasMap[folderName] =
                            CategoriaImportada(
                                originalFolder = folderName,
                                nombre = nombreFinal,
                                categoryId = nuevaCategoriaId
                            )

                        continue
                    }

                    // =========================
                    // ARCHIVOS
                    // =========================

                    val folderName = entryName.substringBefore("/")
                    val categoria = categoriasMap[folderName] ?: continue
                    val extension = entryName.substringAfterLast(".", "").lowercase(Locale.ROOT)
                    val esImagen = when (extension) {
                        in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp") -> true
                        in listOf("mp4", "mkv", "avi", "mov", "webm") -> false
                        else -> continue
                    }
                    val fileName = entryName.substringAfterLast("/")
                    var nombreSinExtension =  fileName.substringBeforeLast(".")

                    nombreSinExtension =
                        nombreSinExtension
                            .removePrefix("MED_")
                            .removePrefix("MED:")
                    val extensionFinal = if (esImagen) "jpg" else "mp4"

                    val nombreFinal = "$nombreSinExtension.$extensionFinal"

                    val archivoDestino = File(mediaDir, nombreFinal)

                    if (archivoDestino.exists()) {

                        /*val itemExistente = ItemLista(
                            id = ItemKey.media(nombreSinExtension),
                            nombre = nombreSinExtension,
                            uri = Uri.fromFile(archivoDestino),
                            esImagen = esImagen,
                            timestamp = archivoDestino.lastModified()*/

                        val uriExistente = Uri.fromFile(archivoDestino)

                        var mediaExistente =
                            db.mediaDao().getActiveByDisplayName(nombreSinExtension)

                        if (mediaExistente == null) {
                            val now = System.currentTimeMillis()

                            mediaExistente = MediaEntity(
                                mediaId = UUID.randomUUID().toString(),
                                displayName = nombreSinExtension,
                                localUri = uriExistente.toString(),
                                mediaType = if (esImagen) "image" else "video",
                                createdAt = archivoDestino.lastModified().takeIf { it > 0L } ?: now,
                                updatedAt = now,
                                isDeleted = false,
                                ownerUserId =
                                SessionManager(requireContext())
                                    .getCurrentUserId()
                            )

                            db.mediaDao().upsert(mediaExistente)
                        }

                        val itemExistente = ItemLista(
                            id = ItemKey.media(mediaExistente.mediaId),
                            nombre = mediaExistente.displayName,
                            uri = uriExistente,
                            esImagen = esImagen,
                            timestamp = mediaExistente.createdAt
                        )
                        val userId =
                            SessionManager(requireContext())
                                .getCurrentUserId()
                        val now = System.currentTimeMillis()
                        val nextOrder =
                            db.categoryDao()
                                .getMaxOrderIndex(categoria.categoryId, userId) + 1
                        if (ItemKey.isMedia(itemExistente.id) && !ItemKey.isMediaUuid(itemExistente.id)) {
                            Log.e(
                                "ITEM_KEY_VALIDATION",
                                "Intento de importar MED legacy existente en Listas: ${itemExistente.id}"
                            )
                            continue
                        }
                        db.categoryDao().insertCategoryItem(
                            CategoryItemEntity(
                                placementId = UUID.randomUUID().toString(),
                                categoryId = categoria.categoryId,
                                itemKey = itemExistente.id,
                                orderIndex = nextOrder,
                                createdAt = now,
                                updatedAt = now,
                                ownerUserId =
                                SessionManager(requireContext())
                                    .getCurrentUserId()
                            )
                        )

                        continue
                    }

                    val outputStream = FileOutputStream(archivoDestino)

                    zipInputStream.copyTo(outputStream)

                    outputStream.close()
                    zipInputStream.closeEntry()

                    val uriGuardado = Uri.fromFile(archivoDestino)

                    /*val item = ItemLista(
                        id = ItemKey.media(nombreSinExtension),
                        nombre = nombreSinExtension,
                        uri = uriGuardado,
                        esImagen = esImagen,
                        timestamp = System.currentTimeMillis()
                    )*/

                    val now = System.currentTimeMillis()

                    val media = MediaEntity(
                        mediaId = UUID.randomUUID().toString(),
                        displayName = nombreSinExtension,
                        localUri = uriGuardado.toString(),
                        mediaType = if (esImagen) "image" else "video",
                        createdAt = now,
                        updatedAt = now,
                        isDeleted = false,
                        ownerUserId =
                        SessionManager(requireContext())
                            .getCurrentUserId()
                    )

                    db.mediaDao().upsert(media)

                    val item = ItemLista(
                        id = ItemKey.media(media.mediaId),
                        nombre = media.displayName,
                        uri = uriGuardado,
                        esImagen = esImagen,
                        timestamp = media.createdAt
                    )

                    itemsImportados.add(item)

                    saveMediaData(
                        nombreSinExtension,
                        uriGuardado,
                        esImagen
                    )
                    val userId =
                        SessionManager(requireContext())
                            .getCurrentUserId()
                    val nextOrder =
                        db.categoryDao()
                            .getMaxOrderIndex(categoria.categoryId, userId ) + 1
                    if (ItemKey.isMedia(item.id) && !ItemKey.isMediaUuid(item.id)) {
                        Log.e(
                            "ITEM_KEY_VALIDATION",
                            "Intento de importar MED legacy nuevo en Listas: ${item.id}"
                        )
                        continue
                    }
                    db.categoryDao().insertCategoryItem(
                        CategoryItemEntity(
                            placementId = UUID.randomUUID().toString(),
                            categoryId = categoria.categoryId,
                            itemKey = item.id,
                            orderIndex = nextOrder,
                            createdAt = now,
                            updatedAt = now,
                            ownerUserId =
                            SessionManager(requireContext())
                                .getCurrentUserId()
                        )
                    )
                }

                zipInputStream.close()

                withContext(Dispatchers.Main) {

                    listaDeArchivos.addAll(itemsImportados)

                    mediaAdapter.notifyDataSetChanged()

                    aplicarOrdenActual()

                    mostrarSnackbar(
                        "Importación exitosa",
                        true
                    )
                }

            } catch (e: Exception) {

                e.printStackTrace()

                withContext(Dispatchers.Main) {

                    mostrarSnackbar(
                        "Error al importar Listas",
                        false
                    )
                }
            }
        }
    }

    private fun mostrarSnackbar(mensaje: String, esExito: Boolean) {

//        val snackbar = Snackbar
//            .make(requireActivity().findViewById(android.R.id.content), mensaje, Snackbar.LENGTH_SHORT)
//            .setAnchorView(R.id.bottom_nav)

        val rootView = requireActivity().findViewById<View>(android.R.id.content)

        val snackbar = Snackbar
            .make(rootView, mensaje, Snackbar.LENGTH_SHORT)
//            .setAnchorView(R.id.bottom_nav)

        val color = if (esExito) {
            ContextCompat.getColor(requireContext(), R.color.color3)
        } else {
            ContextCompat.getColor(requireContext(), com.google.android.material.R.color.design_default_color_error)
        }

        snackbar.setBackgroundTint(color)
        snackbar.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
        snackbar.show()
    }

    private fun saveMediaData(nombreArchivo: String, mediaUri: Uri, isImage: Boolean) {
        val sharedPreferences = requireContext().getSharedPreferences("media_data", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString(nombreArchivo, mediaUri.toString())
        editor.putBoolean("$nombreArchivo|type", isImage) // Guardar si es imagen o video
        editor.apply()
    }

    private fun saveOrdenSeleccionado(itemId: Int) {
        requireContext()
            .getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_ORDEN_RECENTES, itemId)
            .apply()
    }

    private fun getOrdenSeleccionado(): Int {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        return prefs.getInt(KEY_ORDEN_RECENTES, R.id.orden_reciente) // default: reciente
    }

    private fun aplicarOrden(itemId: Int, orderButton: Button) {
        when (itemId) {
            R.id.orden_reciente -> {
                orderButton.text = "Ordenar por: Más recientes"
                listaDeArchivos.sortByDescending { it.timestamp }
            }
            R.id.orden_viejo -> {
                orderButton.text = "Ordenar por: Más viejos"
                listaDeArchivos.sortBy { it.timestamp }
            }
            R.id.az -> {
                orderButton.text = "Ordenar por: A-Z"
                listaDeArchivos.sortBy { it.nombre.lowercase() }
            }
            R.id.za -> {
                orderButton.text = "Ordenar por: Z-A"
                listaDeArchivos.sortByDescending { it.nombre.lowercase() }
            }
        }
        mediaAdapter.notifyDataSetChanged()
    }


    override fun mostrarDialogoAgregarAListas(item: ItemLista) {

        viewLifecycleOwner.lifecycleScope.launch {

            val categorias = withContext(Dispatchers.IO) {
                db.categoryDao().getUserActive()
            }

            if (categorias.isEmpty()) {
                Toast.makeText(requireContext(), "No hay listas. Creá una primero.", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val view = layoutInflater.inflate(R.layout.dialog_add_to_lists, null)

            val recycler = view.findViewById<RecyclerView>(R.id.recyclerLists)
            val btnAgregar = view.findViewById<MaterialButton>(R.id.btnConfirmar)
            val btnNueva = view.findViewById<MaterialButton>(R.id.btnNuevaLista)

            val checked = BooleanArray(categorias.size)

            val headerAgregar = view.findViewById<LinearLayout>(R.id.headerAgregar)
            val contenidoAgregar = view.findViewById<LinearLayout>(R.id.contenidoAgregar)
            val iconAgregar = view.findViewById<ImageView>(R.id.iconExpand)

            val headerExtra = view.findViewById<LinearLayout>(R.id.headerExtra)
            val contenidoExtra = view.findViewById<LinearLayout>(R.id.contenidoExtra)
            val iconExtra = view.findViewById<ImageView>(R.id.iconExtra)

            val contenidos = listOf(contenidoAgregar, contenidoExtra)
            val iconos = listOf(iconAgregar, iconExtra)

            // Estado inicial: abrir "Agregar"
            contenidoAgregar.visibility = View.VISIBLE
            iconAgregar.rotation = 180f

            contenidoExtra.visibility = View.GONE
            iconExtra.rotation = 0f

            fun toggle(target: LinearLayout, icon: ImageView) {
                val isOpen = target.visibility == View.VISIBLE

                TransitionManager.beginDelayedTransition(view as ViewGroup)

                target.visibility = if (isOpen) View.GONE else View.VISIBLE
                icon.animate().rotation(if (isOpen) 0f else 180f).setDuration(200).start()
            }

            headerAgregar.setOnClickListener {
                toggle(contenidoAgregar, iconAgregar)
            }

            headerExtra.setOnClickListener {
                toggle(contenidoExtra, iconExtra)
            }

            recycler.layoutManager = LinearLayoutManager(requireContext())
            recycler.adapter = SimpleListCheckAdapter(
                categorias,
                checked
            )

            val dialog = AlertDialog.Builder(requireContext())
                .setView(view)
                .create()

            btnNueva.setOnClickListener {
                dialog.dismiss()
                mostrarDialogoCrearListaYAgregar(item)
            }

            btnAgregar.setOnClickListener {
                val seleccionadas = categorias.filterIndexed { i, _ -> checked[i] }

                if (seleccionadas.isEmpty()) {
                    Toast.makeText(requireContext(), "Seleccioná al menos una lista", Toast.LENGTH_SHORT).show()
                } else {
                    agregarItemAListas(item, seleccionadas.map { it.categoryId })
                    dialog.dismiss()
                }
            }

            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            dialog.show()
        }
    }

    private fun mostrarDialogoCrearListaYAgregar(item: ItemLista) {
        val input = com.google.android.material.textfield.TextInputEditText(requireContext()).apply {
            hint = "Nombre de la lista"
        }

        AlertDialog.Builder(requireContext(), R.style.ThemeOverlay_Comunic_AlertDialog)
            .setTitle("Nueva lista")
            .setView(input)
            .setPositiveButton("Crear") { _, _ ->
                val nombre = input.text?.toString()?.trim().orEmpty()
                if (nombre.isBlank()) {
                    Toast.makeText(requireContext(), "El nombre no puede estar vacío", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                crearListaYAgregarItem(nombre, item)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun crearListaYAgregarItem(nombre: String, item: ItemLista) {
        viewLifecycleOwner.lifecycleScope.launch {
            val now = System.currentTimeMillis()
            val newId = "user_" + UUID.randomUUID().toString()

            val order = withContext(Dispatchers.IO) { db.categoryDao().getMaxCategoryOrderIndex() + 1 }

            withContext(Dispatchers.IO) {
                db.categoryDao().upsert(
                    CategoryEntity(
                        categoryId = newId,
                        name = nombre,
                        orderIndex = order,
                        createdAt = now,
                        updatedAt = now,
                        ownerUserId =
                        SessionManager(requireContext())
                            .getCurrentUserId()
                    )
                )
            }

            // Agregar a esa lista recién creada
            agregarItemAListas(item, listOf(newId))

            // Opcional: ir directo al detalle
            // abrirCategoriaDetalle(newId, nombre)
        }
    }

    private fun agregarItemAListas(item: ItemLista, categoryIds: List<String>) {
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val userId =
                    SessionManager(requireContext())
                        .getCurrentUserId()
                for (catId in categoryIds) {

                    // 1) evitar duplicado
                    val exists = db.categoryDao().existsItemInCategory(catId, item.id, userId)
                    if (exists) continue

                    // 2) siguiente orden
                    val next = db.categoryDao().getMaxOrderIndex(catId, userId) + 1

                    // 3) insertar placement
//                    db.categoryDao().insertCategoryItem(
//                        CategoryItemEntity(
//                            placementId = UUID.randomUUID().toString(),
//                            categoryId = catId,
//                            itemKey = item.id,   // ✅ item.id = itemKey (PIC:... o MED:...)
//                            orderIndex = next
//                        )
//                    )

                    val now = System.currentTimeMillis()
                    val itemKey = item.id.trim()     // ✅ id = MED:... o PIC:...
                    Log.d("ADD_DEBUG", "guardando itemKey='${item.id}' nombre='${item.nombre}'")
                    if (ItemKey.isMedia(item.id) && !ItemKey.isMediaUuid(item.id)) {
                        Log.e(
                            "ITEM_KEY_VALIDATION",
                            "Intento de importar MED legacy nuevo en Listas: ${item.id}"
                        )
                        continue
                    }
                    db.categoryDao().insertCategoryItem(
                        CategoryItemEntity(
                            placementId = UUID.randomUUID().toString(),
                            categoryId = catId,
                            itemKey = itemKey,
                            orderIndex = next,
                            createdAt = now,
                            updatedAt = now,
                            ownerUserId =
                            SessionManager(requireContext())
                                .getCurrentUserId()
                        )
                    )
                }
            }

            Toast.makeText(requireContext(), "Agregado a listas", Toast.LENGTH_SHORT).show()
        }
    }


    override fun onMediaCreated(item: ItemLista) {
        listaDeArchivos.add(item)
        aplicarOrdenActual()
    }

    fun agregarItemAListaExterna(categoryId: String, item: ItemLista) {
        viewLifecycleOwner.lifecycleScope.launch {
            val userId =
                SessionManager(requireContext())
                    .getCurrentUserId()
            withContext(Dispatchers.IO) {

                val now = System.currentTimeMillis()
                val next = db.categoryDao().getMaxOrderIndex(categoryId, userId) + 1
                if (ItemKey.isMedia(item.id) && !ItemKey.isMediaUuid(item.id)) {
                    Log.e(
                        "ITEM_KEY_VALIDATION",
                        "Intento de importar MED legacy nuevo en Listas: ${item.id}"
                    )
                }
                db.categoryDao().insertCategoryItem(
                    CategoryItemEntity(
                        placementId = UUID.randomUUID().toString(),
                        categoryId = categoryId,
                        itemKey = item.id,
                        orderIndex = next,
                        createdAt = now,
                        updatedAt = now,
                        ownerUserId =
                        SessionManager(requireContext())
                            .getCurrentUserId()
                    )
                )
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "recientes_prefs"
        private const val KEY_ORDEN_RECENTES = "orden_recientes"
        const val REQUEST_CODE_IMPORTAR_ZIP = 1001
        private const val REQUEST_CODE_IMPORTAR_LISTA = 1001
        private const val REQUEST_CODE_IMPORTAR_ELEMENTOS = 1002
    }


}




