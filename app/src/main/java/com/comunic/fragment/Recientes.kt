package com.comunic.fragment

import android.Manifest
import android.app.Activity
import android.app.Activity.RESULT_OK
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.transition.TransitionManager
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.comunic.AddToListHost
import com.comunic.ItemKey
import com.comunic.ItemLista
import com.comunic.MainActivity
import com.comunic.adapters.MediaAdapter
import com.comunic.MenuHandler
import com.comunic.R
import com.comunic.SpeechTextResolver
import com.comunic.adapters.SimpleListCheckAdapter
import com.comunic.databinding.FragmentRecientesBinding
import com.comunic.fragment.HomeFragment.Companion.CAPTURE_IMAGE_REQUEST
import com.comunic.fragment.HomeFragment.Companion.CAPTURE_VIDEO_REQUEST
import com.comunic.fragment.HomeFragment.Companion.PERMISSION_REQUEST_CODE
import com.comunic.fragment.HomeFragment.Companion.PICK_MEDIA_REQUEST
import com.comunic.fragment.HomeFragment.Companion.UCROP_REQUEST_CODE
import com.comunic.data.db.AppDatabase
import com.comunic.data.db.PackRepository
import com.comunic.data.entity.CategoryEntity
import com.comunic.data.entity.CategoryItemEntity
import com.comunic.data.mappers.toItemLista
import com.comunic.interfaces.MediaResultListener
import com.comunic.interfaces.OnNuevoItemListener
import com.comunic.interfaces.RecientesProvider
import com.google.android.material.button.MaterialButton
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
//import io.opencensus.stats.View
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class Recientes : Fragment(),
    TextToSpeech.OnInitListener,
    MediaAdapter.OnEliminarSeleccionListener,
    MenuHandler,
    AddToListHost,
    MediaResultListener {

    private var _binding: FragmentRecientesBinding? = null
    private val binding get() = _binding!!

    // Esta función se llama desde MainActivity cuando se presiona el botón de agregar imagen
    override fun abrirSelectorDeImagen() {
//        opcionesDeImagen() // Tu función existente que muestra el diálogo de imagen/cámara
    }

    private lateinit var mediaAdapter: MediaAdapter
    private val listaDeArchivos: MutableList<ItemLista> = mutableListOf()
    lateinit var escucharPalabra: TextToSpeech
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
                mostrarDialogoAgregarAListas(item)   // ✅ tu función (la que armamos)
            }
        )
        binding.recyclerView.adapter = mediaAdapter

        loadImageData() // Cargar los datos (imágenes/videos)
        val ordenGuardado = getOrdenSeleccionado()
        aplicarOrden(ordenGuardado, orderButton)


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
            if (idioma == TextToSpeech.LANG_MISSING_DATA || idioma == TextToSpeech.LANG_NOT_SUPPORTED)
                Log.e("TextToSpeech", "Error con el idioma")
        } else { Log.e("TextToSpeech", "Error al inicializar") }
    }

    private fun audio(itemKeyOrId: String) {
        if (!::escucharPalabra.isInitialized) return

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val texto = SpeechTextResolver.resolve(db, itemKeyOrId)

            withContext(Dispatchers.Main) {
                escucharPalabra.speak(texto, TextToSpeech.QUEUE_FLUSH, null, null)
            }
        }
    }


    override fun onDestroy() {
        if (::escucharPalabra.isInitialized) {
            escucharPalabra.stop()
            escucharPalabra.shutdown()
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


    private fun loadImageData() {
        viewLifecycleOwner.lifecycleScope.launch {

            // 0) Asegurar que el pack básico exista (idempotente)
            val db = AppDatabase.getDatabase(requireContext())
            PackRepository(requireContext(), db).ensureBasicPackInstalled()
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
            val mediaDir = File(requireContext().filesDir, "media")

            if (mediaDir.exists()) {
                mediaDir.listFiles()?.forEach { file ->
                    val esImagen = file.extension.equals("jpg", ignoreCase = true)
                    val esVideo  = file.extension.equals("mp4", ignoreCase = true)

                    if (!esImagen && !esVideo) return@forEach
                    val base = file.nameWithoutExtension.trim()
                    listaDeArchivos.add(

                            ItemLista(
                                id = ItemKey.media(base),
                                nombre = base,
                                uri = Uri.fromFile(file),
                                esImagen = esImagen,
                                timestamp = file.lastModified()
                            )

                    )
                }
            }

            // 3) Cargar pictos de packs habilitados
            val pictos = db.pictogramDao().getEnabledPictosUi()

            val pictosAsItems = pictos.map { row ->
                row.toItemLista(timestamp = 0L)
                    .copy(id = ItemKey.picto(row.pictogramId)) // id consistente PIC:xxx
            }

            listaDeArchivos.addAll(pictosAsItems)

            // 4) Notificar
            mediaAdapter.notifyDataSetChanged()
        }
    }

    fun solicitarContrasena() {
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

    private fun eliminarElementosSeleccionados(lista: List<ItemLista>) {
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
    fun mostrarDialogoSeleccionarElementos() {
        val nombres = listaDeArchivos.map { it.nombre }
        val seleccionados = BooleanArray(nombres.size)

        val dialogView = layoutInflater.inflate(R.layout.dialogo_seleccion, null)
        val listView = dialogView.findViewById<ListView>(R.id.listaItems)
        val btnSeleccionarTodos = dialogView.findViewById<Button>(R.id.btnSeleccionarTodos)
        val btnDeseleccionarTodos = dialogView.findViewById<Button>(R.id.btnDeseleccionarTodos)

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_multiple_choice, nombres)
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            seleccionados[position] = listView.isItemChecked(position)
        }

        btnSeleccionarTodos.setOnClickListener {
            for (i in nombres.indices) {
                listView.setItemChecked(i, true)
                seleccionados[i] = true
            }
        }

        btnDeseleccionarTodos.setOnClickListener {
            for (i in nombres.indices) {
                listView.setItemChecked(i, false)
                seleccionados[i] = false
            }
        }

        AlertDialog.Builder(requireContext(),
            R.style.ThemeOverlay_Comunic_AlertDialog
        )
            .setTitle("Selecciona elementos para exportar")
            .setView(dialogView)
            .setPositiveButton("Continuar") { _, _ ->
                val elementosSeleccionados = listaDeArchivos.filterIndexed { index, _ -> seleccionados[index] }
                if (elementosSeleccionados.isEmpty()) {
                    Toast.makeText(requireContext(), "No seleccionaste ningún elemento", Toast.LENGTH_SHORT).show()
                } else {
                    mostrarResumenSeleccion(elementosSeleccionados)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun mostrarResumenSeleccion(elementosSeleccionados: List<ItemLista>) {
        val cantidad = elementosSeleccionados.size
        val nombres = elementosSeleccionados.joinToString("\n") { "- ${it.nombre}" }

        AlertDialog.Builder(requireContext(),
            R.style.ThemeOverlay_Comunic_AlertDialog
        )
            .setTitle("Resumen de selección")
            .setMessage("Seleccionaste $cantidad elementos:\n\n$nombres")
            .setPositiveButton("Exportar") { _, _ ->
                mostrarDialogoTipoExportacion(elementosSeleccionados)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }


    private fun mostrarDialogoTipoExportacion(elementosSeleccionados: List<ItemLista>) {
        AlertDialog.Builder(requireContext(),
            R.style.ThemeOverlay_Comunic_AlertDialog
        )
            .setTitle("¿Cómo querés exportarlos?")
            .setItems(arrayOf("Compartir archivos sueltos", "Exportar como ZIP")) { _, which ->
                when (which) {
                    0 -> exportarElementos(elementosSeleccionados) // Sueltos
                    1 -> exportarElementosComoZip(elementosSeleccionados) // Como ZIP
                }
            }
            .show()
    }

    private fun exportarElementos(elementos: List<ItemLista>) {
        if (elementos.isEmpty()) {
            Toast.makeText(requireContext(), "No seleccionaste elementos", Toast.LENGTH_SHORT).show()
            return
        }

        val uris = ArrayList<Uri>()
        for (item in elementos) {
            val uri = if (item.uri.scheme == "content") {
                // Ya es un content://, lo usamos directamente
                item.uri
            } else {
                // Es un file://, lo pasamos por FileProvider
                val file = File(item.uri.path!!)
                FileProvider.getUriForFile(requireContext(), "com.comunic.fileprovider", file)
            }
            uris.add(uri)
        }

        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Compartir archivos"))
    }

    private fun exportarElementosComoZip(elementos: List<ItemLista>) {
        if (elementos.isEmpty()) {
            Toast.makeText(requireContext(), "No seleccionaste elementos", Toast.LENGTH_SHORT).show()
            return
        }

        val zipFile = File(requireContext().cacheDir, "archivos_exportados.zip")

        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
                for (item in elementos) {
                    val inputStream = requireContext().contentResolver.openInputStream(item.uri) ?: continue

                    // obtener mime y extension
                    val mimeType = requireContext().contentResolver.getType(item.uri)
                    var extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)

                    // fallback: intenta extraerla de la URL si no se pudo obtener desde el MIME
                    if (extension == null) {
                        extension = MimeTypeMap.getFileExtensionFromUrl(item.uri.toString())
                    }

                    // añade extensión si no está
                    val fileName = if (extension != null && !item.nombre.endsWith(".$extension")) {
                        "${item.nombre}.$extension"
                    } else {
                        item.nombre
                    }


                    val entry = ZipEntry(fileName)
                    zos.putNextEntry(entry)

                    inputStream.copyTo(zos)

                    zos.closeEntry()
                    inputStream.close()
                }
            }

            val uriZip = FileProvider.getUriForFile(requireContext(), "com.comunic.fileprovider", zipFile)

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uriZip)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(intent, "Compartir ZIP"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Error al crear ZIP", Toast.LENGTH_SHORT).show()
        }
    }

    // EXPORTAR ELEMENTOS - hasta aca


    // IMPORTAR PAQUETES
    fun importarArchivos() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "application/zip"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivityForResult(intent, HomeFragment.REQUEST_CODE_IMPORTAR_ZIP)
    }

    fun importarElementosDesdeZip(uri: Uri) {
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

                val entryName = entry!!.name

                // Si el ZIP trae carpetas, entryName puede incluir "carpeta/archivo.png" Nos quedamos solo con el nombre del archivo.
                val baseName = entryName.substringAfterLast("/").substringAfterLast("\\")

                val nombreSinExtension = baseName.substringBeforeLast(".")
                val extension = baseName.substringAfterLast(".", "").lowercase(Locale.ROOT)

                val esImagen = extension in listOf("jpg", "jpeg", "png", "gif", "bmp", "webp")
                val esVideo  = extension in listOf("mp4", "mkv", "avi", "mov", "webm")

                if (!esImagen && !esVideo) {
                    Toast.makeText(requireContext(), "Archivo no soportado: .$extension", Toast.LENGTH_SHORT).show()
                    zipInputStream.closeEntry()
                    continue
                }

                val nombreFinal = "$nombreSinExtension.$extension"
                val archivoDestino = File(mediaDir, nombreFinal)

                if (archivoDestino.exists()) {
                    Toast.makeText(requireContext(), "Ya existe un archivo llamado $nombreSinExtension", Toast.LENGTH_SHORT).show()
                    zipInputStream.closeEntry()
                    continue
                }

                // Extraigo ZIP y guardo en /files/media/
                FileOutputStream(archivoDestino).use { outputStream ->
                    zipInputStream.copyTo(outputStream)
                }
                zipInputStream.closeEntry()

                val uriGuardado = Uri.fromFile(archivoDestino)
                val base = nombreSinExtension.trim()
                val item = ItemLista(
                    id = ItemKey.media(base),
                    nombre = base,
                    uri = uriGuardado,
                    esImagen = esImagen,
                    timestamp = System.currentTimeMillis()
                )


                listaDeArchivos.add(item)
                (activity as? MainActivity)?.saveMediaData(nombreSinExtension, uriGuardado, esImagen)
            }


            zipInputStream.close() // cierro zip y aviso que se importo bien
            Toast.makeText(requireContext(), "Importación exitosa", Toast.LENGTH_SHORT).show()
            mediaAdapter.notifyDataSetChanged()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Error al importar ZIP", Toast.LENGTH_SHORT).show()
        }
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

//    override fun mostrarDialogoAgregarAListas(item: ItemLista) {
//        viewLifecycleOwner.lifecycleScope.launch {
//            val categorias = withContext(Dispatchers.IO) { db.categoryDao().getUserActive() }
//
//            if (categorias.isEmpty()) {
//                Toast.makeText(requireContext(), "No hay listas. Creá una primero.", Toast.LENGTH_SHORT).show()
//                return@launch
//            }
//
//            val nombres = categorias.map { it.name }.toTypedArray()
//            val checked = BooleanArray(categorias.size)
//
//            AlertDialog.Builder(requireContext(), R.style.ThemeOverlay_Comunic_AlertDialog)
//                .setTitle("Agregar a listas")
//                .setMultiChoiceItems(nombres, checked) { _, which, isChecked ->
//                    checked[which] = isChecked
//                }
//                .setNeutralButton("Nueva lista") { _, _ ->
//                    mostrarDialogoCrearListaYAgregar(item)
//                }
//                .setPositiveButton("Agregar") { _, _ ->
//                    val seleccionadas = categorias.filterIndexed { index, _ -> checked[index] }
//                    if (seleccionadas.isEmpty()) {
//                        Toast.makeText(requireContext(), "No seleccionaste ninguna lista", Toast.LENGTH_SHORT).show()
//                    } else {
//                        agregarItemAListas(item, seleccionadas.map { it.categoryId })
//                    }
//                }
//                .setNegativeButton("Cancelar", null)
//                .show()
//        }
//    }
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
                        createdAt = now
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
                for (catId in categoryIds) {

                    // 1) evitar duplicado
                    val exists = db.categoryDao().existsItemInCategory(catId, item.id)
                    if (exists) continue

                    // 2) siguiente orden
                    val next = db.categoryDao().getMaxOrderIndex(catId) + 1

                    // 3) insertar placement
//                    db.categoryDao().insertCategoryItem(
//                        CategoryItemEntity(
//                            placementId = UUID.randomUUID().toString(),
//                            categoryId = catId,
//                            itemKey = item.id,   // ✅ item.id = itemKey (PIC:... o MED:...)
//                            orderIndex = next
//                        )
//                    )
                    val itemKey = item.id.trim()     // ✅ id = MED:... o PIC:...
                    Log.d("ADD_DEBUG", "guardando itemKey='${item.id}' nombre='${item.nombre}'")
                    db.categoryDao().insertCategoryItem(
                        CategoryItemEntity(
                            placementId = UUID.randomUUID().toString(),
                            categoryId = catId,
                            itemKey = itemKey,
                            orderIndex = next
                        )
                    )
                }
            }

            Toast.makeText(requireContext(), "Agregado a listas", Toast.LENGTH_SHORT).show()
        }
    }


    override fun onMediaCreated(item: ItemLista) {
        listaDeArchivos.add(item)
        mediaAdapter.notifyItemInserted(listaDeArchivos.size - 1)
    }

    fun agregarItemAListaExterna(categoryId: String, item: ItemLista) {
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {

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
        }
    }

    companion object {
        private const val PREFS_NAME = "recientes_prefs"
        private const val KEY_ORDEN_RECENTES = "orden_recientes"
    }


}


//    //para subir un archivo solo
//    override fun openGallery() {
//        val intent = Intent(Intent.ACTION_PICK).apply {
//            type = "image/* video/*" // imagenes o videos
//            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
//        }
//        startActivityForResult(intent, PICK_MEDIA_REQUEST)
//    }


//    /* inicializo */
//    //private lateinit var lastCapturedUri: Uri
//    private var lastCapturedUri: Uri? = null
//
//
//     override fun launchImageCapture() {
//        val photoUri: Uri = createImageUri()
//        lastCapturedUri = photoUri
//
//        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
//        intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
//
//        startActivityForResult(intent, CAPTURE_IMAGE_REQUEST)
//    }
//
//     override fun launchVideoCapture() {
//        val videoUri: Uri = createVideoUri()
//        lastCapturedUri = videoUri
//
//        val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
//        intent.putExtra(MediaStore.EXTRA_OUTPUT, videoUri)
//
//        startActivityForResult(intent, CAPTURE_VIDEO_REQUEST)
//    }
//
//     fun createImageUri(): Uri {
//        val contentValues = ContentValues().apply {
//            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
//            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Comunic")
//        }
//        return requireContext().contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)!!
//    }
//
//     fun createVideoUri(): Uri {
//        val contentValues = ContentValues().apply {
//            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
//            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Comunic")
//        }
//        return requireContext().contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)!!
//    }
//
//    // EDICION ucrop
//    fun startCrop(uri: Uri) {
//        val destinationUri = Uri.fromFile(File(requireContext().cacheDir, "imagen_editada_${System.currentTimeMillis()}.jpg"))
//
//        val options = UCrop.Options().apply {
//            setCompressionFormat(Bitmap.CompressFormat.JPEG)
//            setCompressionQuality(90)
//            setFreeStyleCropEnabled(true) // Permite mover y redimensionar libremente
//
//            setToolbarColor(ContextCompat.getColor(requireContext(), R.color.color5))      // barra superior
//            setStatusBarColor(ContextCompat.getColor(requireContext(), R.color.color5))     // barra de estado
//            setToolbarWidgetColor(ContextCompat.getColor(requireContext(), R.color.color1)) // texto/iconos
//            setActiveControlsWidgetColor(ContextCompat.getColor(requireContext(), R.color.color1)) // botones activos
//            setRootViewBackgroundColor(ContextCompat.getColor(requireContext(), R.color.color5))   // fondo general
//
//            setToolbarTitle("Editar imagen") // título personalizado
//        }
//
//        UCrop.of(uri, destinationUri)
//            .withOptions(options)
//            .withAspectRatio(1f, 1f)
//            .start(requireContext(), this@Recientes) // 👈 IMPORTANTE: que el Fragment reciba el resultado
//
//    }
//
//
//    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
//        super.onActivityResult(requestCode, resultCode, data)
//
//        // devolucion para el ucrop
//        if (requestCode == UCROP_REQUEST_CODE) {
//            if (resultCode == RESULT_OK) {
//                val resultUri = UCrop.getOutput(data!!)
//                if (resultUri != null) {
//                    ingresarNombreArchivo(resultUri, true)
//                } else {
//                    Toast.makeText(requireContext(), "Error al recortar la imagen. Usando imagen original.", Toast.LENGTH_SHORT).show()
//                    lastCapturedUri?.let { ingresarNombreArchivo(it, true) }
//                }
//            } else {
//                // Si el usuario canceló el crop, usamos la imagen original
//                lastCapturedUri?.let {
//                    ingresarNombreArchivo(it, true)
//                }
//            }
//            return
//        }

//        // devolucion para la captura de imagen o video
//        if (resultCode == RESULT_OK) {
//            val mediaUri = when (requestCode) {
//                PICK_MEDIA_REQUEST -> data?.data
//                CAPTURE_IMAGE_REQUEST -> lastCapturedUri
//                CAPTURE_VIDEO_REQUEST -> lastCapturedUri
//                else -> null
//            }
//
//            if (mediaUri != null) {
//                val mimeType = requireContext().contentResolver.getType(mediaUri)
//                if (mimeType != null) {
//                    if (mimeType.startsWith("image/")) {
//                        if (requestCode == CAPTURE_IMAGE_REQUEST) {
//                            startCrop(mediaUri) // 👉 Editamos antes de continuar
//                        } else {
//                            // ingresarNombreArchivo(mediaUri, true)
//                            startCrop(mediaUri) // 👉 Editamos antes de continuar
//                        }
//                    } else if (mimeType.startsWith("video/")) {
//                        Log.d("CapturedMedia", "Video capturado URI: $mediaUri")
//                        ingresarNombreArchivo(mediaUri, false)
//                    }
//                }
//            } else {
//                Log.e("CaptureError", "Media URI is null")
//            }
//        }
//
//        // devolucion para el importar archivos
//        if (requestCode == HomeFragment.REQUEST_CODE_IMPORTAR_ZIP && resultCode == Activity.RESULT_OK) {
//            val uri = data?.data ?: return
//            importarElementosDesdeZip(uri)
//        }
//    }
//
//
//    private fun ingresarNombreArchivo(mediaUri: Uri?, isImage: Boolean) {
//        val dialogView = layoutInflater.inflate(R.layout.dialog_image_name, null)
//        val nameEditText = dialogView.findViewById<EditText>(R.id.nameEditText)
//
//        AlertDialog.Builder(requireContext(), R.style.ThemeOverlay_Comunic_AlertDialog)
//            .setTitle(if (isImage) "Sonido de la imagen" else "Sonido del video")
//            .setView(dialogView)
//            .setPositiveButton("OK") { _, _ ->
//                val nombreRaw = nameEditText.text?.toString().orEmpty()
//                val nombreLimpio = normalizarNombre(nombreRaw)
//
//                if (mediaUri != null && nombreLimpio.isNotEmpty()) {
//                    guardarArchivo(mediaUri, nombreLimpio, isImage)
//                } else {
//                    Toast.makeText(requireContext(), "El nombre no puede estar vacío", Toast.LENGTH_SHORT).show()
//                }
//            }
//            .setNegativeButton("Cancelar", null)
//            .show()
//    }
//
//    /**
//     * Normaliza para que:
//     * - no haya espacios adelante/atrás
//     * - no haya dobles espacios
//     * - (opcional) evita caracteres problemáticos para nombres de archivo
//     */
//    private fun normalizarNombre(input: String): String {
//        // 1) trim + colapsar espacios internos
//        var s = input.trim().replace(Regex("\\s+"), " ")
//
//        // 2) opcional: eliminar caracteres inválidos en nombres de archivo (recomendado)
//        // Windows/Android suelen romper con / \ : * ? " < > |
//        s = s.replace(Regex("""[\\/:*?"<>|]"""), "")
//
//        return s
//    }
//
//    private fun guardarArchivo(mediaUri: Uri, nombreLimpio: String, esImagen: Boolean) {
//        // Guardar SIEMPRE usando el nombre limpio (archivo y todo)
//        val savedUri = guardarEnAlmacenamientoInterno(requireContext(), mediaUri, nombreLimpio, esImagen)
//        if (savedUri == null) return
//
//        // Si ya existía un item con ese nombre, lo reemplazamos
//        val index = listaDeArchivos.indexOfFirst { it.nombre == nombreLimpio }
//        if (index != -1) {
//            listaDeArchivos.removeAt(index)
//            mediaAdapter.notifyItemRemoved(index)
//        }
//
//        // Importante: id y nombre coherentes (sin trims extra, ya está limpio)
//        val item = ItemLista(
//            id = ItemKey.media(nombreLimpio),
//            nombre = nombreLimpio,
//            uri = savedUri,
//            esImagen = esImagen,
//            timestamp = System.currentTimeMillis()
//        )
//
//        listaDeArchivos.add(item)
//        saveMediaData(nombreLimpio, savedUri, esImagen)
//
//        mediaAdapter.notifyItemInserted(listaDeArchivos.size - 1)
//
//        Toast.makeText(
//            requireContext(),
//            if (esImagen) "Imagen guardada como $nombreLimpio" else "Video guardado como $nombreLimpio",
//            Toast.LENGTH_SHORT
//        ).show()
//    }
//
//    private fun guardarEnAlmacenamientoInterno(
//        context: Context,
//        mediaUri: Uri,
//        nombreArchivoLimpio: String,
//        esImagen: Boolean
//    ): Uri? {
//        val directorio = File(context.filesDir, "media")
//        if (!directorio.exists()) directorio.mkdirs()
//
//        val extension = if (esImagen) "jpg" else "mp4"
//        val archivo = File(directorio, "$nombreArchivoLimpio.$extension")
//
//        return try {
//            context.contentResolver.openInputStream(mediaUri)?.use { inputStream ->
//                FileOutputStream(archivo).use { outputStream ->
//                    inputStream.copyTo(outputStream)
//                }
//            }
//            Uri.fromFile(archivo)
//        } catch (e: IOException) {
//            e.printStackTrace()
//            null
//        }
//    }
//
//    private fun saveMediaData(nombreArchivoLimpio: String, mediaUri: Uri, isImage: Boolean) {
//        val sharedPreferences = requireContext().getSharedPreferences("media_data", Context.MODE_PRIVATE)
//        sharedPreferences.edit()
//            .putString(nombreArchivoLimpio, mediaUri.toString())
//            .putBoolean("$nombreArchivoLimpio|type", isImage)
//            .apply()
//    }


