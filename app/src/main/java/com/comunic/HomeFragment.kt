package com.comunic

import android.Manifest
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
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.comunic.databinding.FragmentHomeBinding
import com.yalantis.ucrop.UCrop
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import com.comunic.data.db.AppDatabase
import com.comunic.data.RankingManager
import com.comunic.data.db.PackRepository
import com.comunic.data.mappers.toItemLista
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class HomeFragment : Fragment(), TextToSpeech.OnInitListener, MediaAdapter.OnEliminarSeleccionListener,
    MenuHandler {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // Esta función se llama desde MainActivity cuando se presiona el botón de agregar imagen
    override fun abrirSelectorDeImagen() {
        opcionesDeImagen() // Tu función existente que muestra el diálogo de imagen/cámara
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

//    private lateinit var adapterTop6: MediaAdapter
    //private lateinit var adapterCategorias: CategoriaAdapter

    // para integrar pack
    private lateinit var db: AppDatabase

    // resumen de listas + adaptador cuadricula
    //private lateinit var categoriasQuickAdapter: CategoriasQuickAdapter
    private lateinit var categoriasAdapter: CategoriasCuadriculaAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        db = AppDatabase.getDatabase(requireContext())

        ///// INICIALIZAR CUADRICULA CATEGROIA
        categoriasAdapter = CategoriasCuadriculaAdapter(emptyList()) { cat ->
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, CategoriaDetalleFragment.newInstance(cat.categoryId, cat.name))
                .addToBackStack(null)
                .commit()
        }

        binding.recyclerCategorias.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.recyclerCategorias.adapter = categoriasAdapter

        binding.recyclerCategorias.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

        //binding.recyclerCategorias.adapter = categoriasQuickAdapter
        binding.recyclerCategorias.visibility = View.VISIBLE

        cargarPreviewCategorias()
        ///// FIN INICIALIZAR CUADRICULA CATEGROIA

        escucharPalabra = TextToSpeech(requireContext(), this)

        val recyclerView = binding.recyclerView
        //recyclerView.layoutManager = LinearLayoutManager(this)  // 1 columna
        recyclerView.layoutManager = GridLayoutManager(requireContext(), 4)  // 4 columnas
        // DESCOMENTAR SI QUIERO QUE SE VEA EL RECYCLER
        mediaAdapter = MediaAdapter(listaDeArchivos, ::eliminar) { id ->
            audio(id)
        }
        recyclerView.adapter = mediaAdapter
        //binding.recyclerView.visibility = View.GONE

        // TOP 6
        lifecycleScope.launch {
            val now = System.currentTimeMillis()
            val bucketId = RankingManager.TimeBucket.bucketIdFromMillis(now)

            val (topBucket, topGlobal) = withContext(Dispatchers.IO) {
                val db = AppDatabase.getDatabase(requireContext())

                // Traemos más de 6 para poder ordenar bien y tener margen
                val bucket = db.itemUsadoBucketDao().getTopForBucket(bucketId, 30)

                // Global para completar si el bucket no alcanza
                val global = db.itemUsadoDao().obtenerMasUsados() // ya lo tenés en tu DAO

                bucket to global
            }

            // Maps del BUCKET (frecuencia + recencia)
            val bucketCount = topBucket.associate { it.nombreArchivo to it.cantidadDeUsos }
            val bucketLastUsed = topBucket.associate { it.nombreArchivo to it.ultimaFechaUso }

            // Maps GLOBAL (por si necesitamos completar)
            val globalCount = topGlobal.associate { it.nombreArchivo to it.cantidadDeUsos }
            val globalLastUsed = topGlobal.associate { it.nombreArchivo to it.ultimaFechaUso }

            // Unión de candidatos: primero bucket, luego global (sin duplicar)
            val candidatos = LinkedHashSet<String>().apply {
                topBucket.forEach { add(it.nombreArchivo) }
                topGlobal.forEach { add(it.nombreArchivo) }
            }.toList()

            // Orden: (1) bucketCount desc, (2) bucketLastUsed desc, (3) globalCount desc, (4) globalLastUsed desc
            val topFinalNombres = candidatos
                .sortedWith(
                    compareByDescending<String> { bucketCount[it] ?: 0 }
                        .thenByDescending { bucketLastUsed[it] ?: 0L }
                        // Desempates extra para los que vienen de global o para estabilidad
                        .thenByDescending { globalCount[it] ?: 0 }
                        .thenByDescending { globalLastUsed[it] ?: 0L }
                )
                .take(6)

            // Convertimos a ItemLista (timestamp = ultima fecha real: bucket si existe, si no global)
            val carpeta = File(requireContext().filesDir, "media")
            val mediaItemsTop = topFinalNombres.mapNotNull { nombre ->
                val archivoJpg = File(carpeta, "$nombre.jpg")
                val archivoMp4 = File(carpeta, "$nombre.mp4")

                val archivoExistente = when {
                    archivoJpg.exists() -> archivoJpg
                    archivoMp4.exists() -> archivoMp4
                    else -> null
                }

                archivoExistente?.let { archivo ->
                    val uri = Uri.fromFile(archivo)
                    val esImagen = archivo.name.endsWith(".jpg", ignoreCase = true)

                    val lastUsed = bucketLastUsed[nombre] ?: globalLastUsed[nombre] ?: now

//                    ItemLista(
//                        nombre = nombre,
//                        uri = uri,
//                        esImagen = esImagen,
//                        timestamp = lastUsed
//                    )
                    ItemLista(
                        id = nombre,
                        nombre = nombre,
                        uri = uri,
                        esImagen = esImagen,
                        timestamp = lastUsed
                    )
                }
            }

            val adapter = MediaAdapter(mediaItemsTop.toMutableList(), ::eliminar) { id -> audio(id) }
            binding.recyclerTop6.layoutManager = GridLayoutManager(requireContext(), 3)
            binding.recyclerTop6.adapter = adapter
            binding.recyclerTop6.visibility = View.VISIBLE
        }

        loadImageData() // Cargar los datos (imágenes/videos)

        mediaAdapter.notifyDataSetChanged()

        //setupAddButton() // boton agregar imagen
        setupSelectionPanel() // seleccion y eliminacion
        checkReadPermissionIfNeeded() // permisos

//        val addImageButton = findViewById<Button>(R.id.addImageButton)

        // ACCESO RAPIDO A RESUMEN DESDE HOME
        setupResumenClicks()

        // --- PREVIEW CATEGORIAS EN HOME ---
//        categoriasQuickAdapter = CategoriasQuickAdapter(emptyList()) { categoria ->
//            // Opción A (mejor UX): ir DIRECTO al detalle de la categoría
//            parentFragmentManager.beginTransaction()
//                .replace(R.id.fragment_container, CategoriaDetalleFragment.newInstance(categoria.categoryId, categoria.name))
//                .addToBackStack(null)
//                .commit()
//
//        }



        return binding.root
    }

//    private fun setupAddButton() {
//        binding.addImageButton.setOnClickListener {
//            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//                Manifest.permission.READ_MEDIA_IMAGES
//            } else {
//                Manifest.permission.READ_EXTERNAL_STORAGE
//            }
//
//            if (ContextCompat.checkSelfPermission(requireContext(), permission) != PackageManager.PERMISSION_GRANTED) {
//                requestPermissions(arrayOf(permission), PERMISSION_REQUEST_CODE)
//            } else {
//                opcionesDeImagen()
//            }
//        }
//    }

    private fun setupSelectionPanel() {
        binding.deleteSelectedButton.setOnClickListener {
            val seleccionados = mediaAdapter.obtenerSeleccionados()
            if (seleccionados.isNotEmpty()) {
                AlertDialog.Builder(requireContext(),
                    R.style.ThemeOverlay_Comunic_AlertDialog)
                    .setTitle("Confirmar eliminación")
                    .setMessage("¿Deseás eliminar los ${seleccionados.size} elementos seleccionados?")
                    .setPositiveButton("Eliminar") { dialog, _ ->
                        mediaAdapter.eliminarSeleccionados()
                        actualizarPanelSeleccion(0)
                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        }

        binding.cancelSelectionButton.setOnClickListener {
            mediaAdapter.cancelarModoEliminacion()
            actualizarPanelSeleccion(0)
        }
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

    fun actualizarPanelSeleccion(cantidad: Int) {
        if (cantidad > 0) {
            selectionPanel.visibility = View.VISIBLE
            selectionCountText.text = "$cantidad elemento${if (cantidad > 1) "s" else ""} seleccionad${if (cantidad > 1) "os" else "o"}"
        } else {
            selectionPanel.visibility = View.GONE
        }
    }

    // Maneja resultado de la solicitud de permisos
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permiso concedido, recarga los urls de las imagenes
                Log.d("Permissions", "Permission OK") // Log para cuando conceden el permiso
                openGallery()
            } else {
                Log.d("Permissions", "Permission denied") // Log para cuando deniegan el permiso
            }
        }
    }

    fun opcionesDeImagen() {
        val opciones = arrayOf("Abrir Galeria", "Abrir Camara")
        val builder = AlertDialog.Builder(requireContext(),
            R.style.ThemeOverlay_Comunic_AlertDialog)
        builder.setTitle("Seleccione una opción")
        builder.setItems(opciones) { _, which ->
            when (which) {
                0 -> openGallery()
                1 -> openCamera()
            }
        }
        builder.show()
    }

    //para subir un archivo solo
    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK).apply {
            type = "image/* video/*" // imagenes o videos
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
        }
        startActivityForResult(intent, PICK_MEDIA_REQUEST)
    }


    /* inicializo */
    //private lateinit var lastCapturedUri: Uri
    private var lastCapturedUri: Uri? = null

    private fun openCamera() {
        val options = arrayOf("Capturar Imagen", "Grabar Video")

        val builder = AlertDialog.Builder(requireContext(),
            R.style.ThemeOverlay_Comunic_AlertDialog)
        builder.setTitle("Seleccionar Opción")
        builder.setItems(options) { _, which ->
            when (which) {
                0 -> {
                    // Capturar Imagen
                    val photoUri: Uri = createImageUri() // Crea un URI utilizando FileProvider
                    lastCapturedUri = photoUri
                    val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                    startActivityForResult(intent, CAPTURE_IMAGE_REQUEST)
                }
                1 -> {
                    val videoUri: Uri = createVideoUri() // Crea un URI utilizando FileProvider
                    lastCapturedUri = videoUri
                    // Grabar Video
                    val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, videoUri)
                    startActivityForResult(intent, CAPTURE_VIDEO_REQUEST)
                }
            }
        }
        builder.show()
    }

    private fun createImageUri(): Uri {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Comunic")
        }
        return requireContext().contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)!!
    }

    private fun createVideoUri(): Uri {
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Comunic")
        }
        return requireContext().contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)!!
    }

    // EDICION ucrop
    fun startCrop(uri: Uri) {
        val destinationUri = Uri.fromFile(File(requireContext().cacheDir, "imagen_editada_${System.currentTimeMillis()}.jpg"))

        val options = UCrop.Options().apply {
            setCompressionFormat(Bitmap.CompressFormat.JPEG)
            setCompressionQuality(90)
            setFreeStyleCropEnabled(true) // Permite mover y redimensionar libremente

            setToolbarColor(ContextCompat.getColor(requireContext(), R.color.color5))      // barra superior
            setStatusBarColor(ContextCompat.getColor(requireContext(), R.color.color5))     // barra de estado
            setToolbarWidgetColor(ContextCompat.getColor(requireContext(), R.color.color1)) // texto/iconos
            setActiveControlsWidgetColor(ContextCompat.getColor(requireContext(), R.color.color1)) // botones activos
            setRootViewBackgroundColor(ContextCompat.getColor(requireContext(), R.color.color5))   // fondo general

            setToolbarTitle("Editar imagen") // título personalizado
        }

//        UCrop.of(uri, destinationUri)|
//            .withOptions(options)
//            .withAspectRatio(1f, 1f)
//            .start(requireActivity(), UCROP_REQUEST_CODE)

        UCrop.of(uri, destinationUri)
            .withOptions(options)
            .withAspectRatio(1f, 1f)
            .start(requireContext(), this@HomeFragment) // 👈 IMPORTANTE: que el Fragment reciba el resultado

    }

    /* para ver si esta inicializada la variable lastcaptureduri
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == RESULT_OK) {
            val mediaUri = when (requestCode) {
                PICK_MEDIA_REQUEST -> data?.data
                CAPTURE_IMAGE_REQUEST -> lastCapturedUri
                CAPTURE_VIDEO_REQUEST -> lastCapturedUri
                else -> null
            }
            if (mediaUri != null) {
                val mimeType = contentResolver.getType(mediaUri)
                if (mimeType != null) {

//                    if (mimeType.startsWith("image/")) {
//                        Log.d("CapturedMedia", "Imagen capturada. URI: $mediaUri")
//                        ingresarNombreArchivo(mediaUri, true)

                    if (mimeType.startsWith("image/")) {
                        if (requestCode == CAPTURE_IMAGE_REQUEST) {
                            startCrop(mediaUri) // 👉 Editamos antes de continuar
                        } else {
                            ingresarNombreArchivo(mediaUri, true)
                        }
                    } else if (mimeType.startsWith("video/")) {
                        Log.d("CapturedMedia", "Video capturado URI: $mediaUri")
                        ingresarNombreArchivo(mediaUri, false)
                    }
                }
            } else {
                Log.e("CaptureError", "Media URI is null")
            }

            if (requestCode == UCROP_REQUEST_CODE && resultCode == RESULT_OK) {
                val resultUri = UCrop.getOutput(data!!)
                if (resultUri != null) {
                    ingresarNombreArchivo(resultUri, true) // Usamos imagen recortada
                } else {
                    Toast.makeText(this, "Error al recortar la imagen", Toast.LENGTH_SHORT).show()
                }
            }
        }




        /*
        if (requestCode == REQUEST_CODE_SIGN_IN && resultCode == RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                if (account != null) {
                    val email = account.email ?: "Correo no disponible"
                    // Inicializa el servicio de Google Drive después de la autenticación en un hilo en segundo plano
                    val executorService = Executors.newSingleThreadExecutor()
                    executorService.execute {
                        driveServiceHelper = DriveServiceHelper(this, getGoogleDriveService(account))

                        // Ahora que tienes el helper, puedes cargar las imágenes
                        loadImageData()

                        // Mostrar mensaje de éxito en el hilo principal
                        runOnUiThread {
                            Toast.makeText(this, "Conectado a Google Drive.\n Email: $email", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Log.e("GoogleDrive", "Cuenta de Google es nula después del inicio de sesión")
                }
            } catch (e: ApiException) {
                Log.e("GoogleDrive", "Error en la autenticación de Google Drive: ${e.statusCode}")
                Toast.makeText(this, "Error en la autenticación", Toast.LENGTH_SHORT).show()
            }
        }*/

        if (requestCode == SELECT_FILES_REQUEST_CODE && resultCode == RESULT_OK) {
            val archivosSeleccionados = mutableListOf<Uri>()
            data?.data?.let { archivosSeleccionados.add(it) }
            data?.clipData?.let {
                for (i in 0 until it.itemCount) {
                    archivosSeleccionados.add(it.getItemAt(i).uri)
                }
            }

            val archivoComprimido = File(getExternalFilesDir(null), "exported_files.zip") // Comprimir los archivos seleccionados
            comprimirArchivos(archivosSeleccionados, archivoComprimido.absolutePath)

            compartirArchivo(archivoComprimido) // Compartir el archivo comprimido
        }
        if (resultCode == Activity.RESULT_OK && requestCode == REQUEST_CODE_IMPORTAR_ZIP) {
            val uri = data?.data
            uri?.let { importarElementosDesdeZip(it) }
        }
    }*/

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // devolucion para el ucrop
        if (requestCode == UCROP_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                val resultUri = UCrop.getOutput(data!!)
                if (resultUri != null) {
                    ingresarNombreArchivo(resultUri, true)
                } else {
                    Toast.makeText(requireContext(), "Error al recortar la imagen. Usando imagen original.", Toast.LENGTH_SHORT).show()
                    lastCapturedUri?.let { ingresarNombreArchivo(it, true) }
                }
            } else {
                // Si el usuario canceló el crop, usamos la imagen original
                lastCapturedUri?.let {
                    ingresarNombreArchivo(it, true)
                }
            }
            return
        }

        // devolicon para la captura de imagen o video
        if (resultCode == RESULT_OK) {
            val mediaUri = when (requestCode) {
                PICK_MEDIA_REQUEST -> data?.data
                CAPTURE_IMAGE_REQUEST -> lastCapturedUri
                CAPTURE_VIDEO_REQUEST -> lastCapturedUri
                else -> null
            }

            if (mediaUri != null) {
                val mimeType = requireContext().contentResolver.getType(mediaUri)
                if (mimeType != null) {
                    if (mimeType.startsWith("image/")) {
                        if (requestCode == CAPTURE_IMAGE_REQUEST) {
                            startCrop(mediaUri) // 👉 Editamos antes de continuar
                        } else {
                           // ingresarNombreArchivo(mediaUri, true)
                            startCrop(mediaUri) // 👉 Editamos antes de continuar
                        }
                    } else if (mimeType.startsWith("video/")) {
                        Log.d("CapturedMedia", "Video capturado URI: $mediaUri")
                        ingresarNombreArchivo(mediaUri, false)
                    }
                }
            } else {
                Log.e("CaptureError", "Media URI is null")
            }
        }

        // devolucion para el importar archivos
        if (requestCode == REQUEST_CODE_IMPORTAR_ZIP && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            importarElementosDesdeZip(uri)
        }
    }


    private fun ingresarNombreArchivo(mediaUri: Uri?, isImage: Boolean) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_image_name, null)
        val nameEditText = dialogView.findViewById<EditText>(R.id.nameEditText)
        AlertDialog.Builder(requireContext(),
            R.style.ThemeOverlay_Comunic_AlertDialog)
            .setTitle(if (isImage) "Sonido de la imagen" else "Sonido del video")
            .setView(dialogView)
            .setPositiveButton("OK") { _, _ ->
                val nombreArchivo = nameEditText.text.toString()
                if (mediaUri != null && nombreArchivo.isNotBlank()) {
                    guardarArchivo(mediaUri, nombreArchivo, isImage)

                } else {
                    Toast.makeText(requireContext(), "El nombre no puede estar vacío", Toast.LENGTH_SHORT)
                        .show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

//    private fun ingresarNombreArchivo(mediaUri: Uri?, isImage: Boolean) {
//        val dialogView = layoutInflater.inflate(R.layout.dialog_image_name, null)
//        val nameEditText = dialogView.findViewById<EditText>(R.id.nameEditText)
//
//        val dialog = AlertDialog.Builder(requireContext())
//            .setTitle(if (isImage) "Sonido de la imagen" else "Sonido del video")
//            .setView(dialogView)
//            .setPositiveButton("OK", null)        // listener se setea luego
//            .setNegativeButton("Cancelar", null)
//            .create()
//
//        dialog.show()
//
//        // 🎨 COLORES (excepción por código)
//        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
//            .setTextColor(ContextCompat.getColor(requireContext(), R.color.color1))
//
//        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
//            .setTextColor(ContextCompat.getColor(requireContext(), R.color.color1))
//
//        // ✅ Mantenemos funcionalidad (no cerrar si está vacío)
//        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
//            val nombreArchivo = nameEditText.text.toString()
//            if (mediaUri != null && nombreArchivo.isNotBlank()) {
//                guardarArchivo(mediaUri, nombreArchivo, isImage)
//                dialog.dismiss()
//            } else {
//                Toast.makeText(
//                    requireContext(),
//                    "El nombre no puede estar vacío",
//                    Toast.LENGTH_SHORT
//                ).show()
//            }
//        }
//    }


    private fun guardarArchivo(mediaUri: Uri, nombre: String, esImagen: Boolean) {
        /* si quiero que el nombre contecta el timestamp
        val timestamp = System.currentTimeMillis()
        val nombreConTimestamp = "$(nombre)_$timestamp" */

//        val savedUri = guardarEnAlmacenamiento(mediaUri, nombreConTimestamp, esImagen)
        val savedUri = guardarEnAlmacenamientoInterno(requireContext(), mediaUri, nombre, esImagen)
        if (savedUri != null) {
            // Eliminar el elemento antiguo
            val index = listaDeArchivos.indexOfFirst { it.nombre == nombre }
            if (index != -1) {
                listaDeArchivos.removeAt(index)
                mediaAdapter.notifyItemRemoved(index)
            }
            // Agregar la imagen o video a la lista y guardarla
            //listaDeArchivos.add(Triple(nombre, savedUri, esImagen))
          //  listaDeArchivos.add(ItemLista(nombre, savedUri, esImagen, System.currentTimeMillis()))
            listaDeArchivos.add(
                ItemLista(
                    id = nombre,
                    nombre = nombre,
                    uri = savedUri,
                    esImagen = esImagen,
                    timestamp = System.currentTimeMillis()
                )
            )

            saveMediaData(nombre, savedUri, esImagen)
            mediaAdapter.notifyItemInserted(listaDeArchivos.size - 1)
            Toast.makeText( requireContext(),
                if (esImagen) "Imagen guardada como $nombre" else "Video guardado como $nombre",
                Toast.LENGTH_SHORT
            ).show()

            /*// 📤 **Subir a Google Drive**
            val mimeType = if (esImagen) "image/jpeg" else "video/mp4"
            driveServiceHelper.uploadFile(savedUri, nombre, mimeType)
                .addOnSuccessListener { fileId ->
                    Log.d("GoogleDrive", "Archivo subido con éxito. ID: $fileId")
                }
                .addOnFailureListener { e ->
                    Log.e("GoogleDrive", "Error al subir archivo: ${e.message}")
                }

        } else {
            Toast.makeText(this, "Error al guardar", Toast.LENGTH_SHORT).show()
        } */
        }
    }

    private fun guardarEnAlmacenamientoInterno(
        context: Context,
        mediaUri: Uri,
        nombreArchivo: String,
        esImagen: Boolean
    ): Uri? {
        //val subCarpeta = if (esImagen) "imagenes" else "videos" // si quiero 2 carpetas diferentes
//        val directorio = File(context.filesDir, subCarpeta) // si quiero 2 carpetas diferentes
        val directorio = File(context.filesDir, "media") // Carpeta "media" en el almacenamiento interno

        if (!directorio.exists()) {
            directorio.mkdirs() // Crear la carpeta si no existe
        }

        val archivo = File(directorio, "$nombreArchivo.${if (esImagen) "jpg" else "mp4"}")
        return try {
            context.contentResolver.openInputStream(mediaUri)?.use { inputStream ->
                FileOutputStream(archivo).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            Uri.fromFile(archivo) // Retorna el URI del archivo guardado
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    private fun saveMediaData(nombreArchivo: String, mediaUri: Uri, isImage: Boolean) {
        val sharedPreferences = requireContext().getSharedPreferences("media_data", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString(nombreArchivo, mediaUri.toString())
        editor.putBoolean("$nombreArchivo|type", isImage) // Guardar si es imagen o video
        editor.apply()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val idioma = escucharPalabra.setLanguage(Locale("es","AR"))
            if (idioma == TextToSpeech.LANG_MISSING_DATA || idioma == TextToSpeech.LANG_NOT_SUPPORTED)
                Log.e("TextToSpeech", "Error con el idioma")
        } else { Log.e("TextToSpeech", "Error al inicializar") }
    }

//    private fun audio(text: String) {
//        if (::escucharPalabra.isInitialized) {
//            escucharPalabra.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
//        }
//    }
private fun audio(nombre: String) {
    if (!::escucharPalabra.isInitialized) return

    viewLifecycleOwner.lifecycleScope.launch {
        // si coincide con un pictograma, hablar el label
        val picto = db.pictogramDao().getPictoById(nombre)
        val texto = picto?.label ?: nombre

        escucharPalabra.speak(texto, TextToSpeech.QUEUE_FLUSH, null, null)
    }
}

    override fun onDestroy() {
        if (::escucharPalabra.isInitialized) {
            escucharPalabra.stop()
            escucharPalabra.shutdown()
        }
        super.onDestroy()
    }

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

    // si uso una carpeta del almacenamiento interno para imagenes y videos
//    private fun loadImageData() {
//        listaDeArchivos.clear() // Limpia la lista antes de cargar nuevos datos
//
//        val mediaDir = File(requireContext().filesDir, "media")         // Directorio único para imágenes y videos
//
//        if (mediaDir.exists()) { // Cargar archivos desde la carpeta "media"
//            mediaDir.listFiles()?.forEach { file ->
//                val esImagen = file.extension.equals("jpg", ignoreCase = true) // Verifica si es imagen
//                listaDeArchivos.add(
//                    ItemLista(
//                        nombre = file.nameWithoutExtension,
//                        uri = Uri.fromFile(file),
//                        esImagen = esImagen,
//                        timestamp = file.lastModified()
//                    )
//                )
//            }
//        }
//
//        //listaDeArchivos.sortByDescending { it.timestamp } // Ordenar por timestamp (más reciente primero)
//
//        mediaAdapter.notifyDataSetChanged() // Notificar al adaptador
//    }
    private fun loadImageData() {
        viewLifecycleOwner.lifecycleScope.launch {

            // 0) Asegurar que el pack básico exista (idempotente)
            val db = AppDatabase.getDatabase(requireContext())
            PackRepository(requireContext(), db).ensureBasicPackInstalled()

            // 1) Limpiar lista
            listaDeArchivos.clear()

            // 2) Cargar USER media desde /files/media (tu lógica original)
            val mediaDir = File(requireContext().filesDir, "media")

            if (mediaDir.exists()) {
                mediaDir.listFiles()?.forEach { file ->
                    val esImagen = file.extension.equals("jpg", ignoreCase = true)
                    val esVideo  = file.extension.equals("mp4", ignoreCase = true)

                    if (!esImagen && !esVideo) return@forEach
                    val base = file.nameWithoutExtension
                    listaDeArchivos.add(
//                        ItemLista(
//                            nombre = file.nameWithoutExtension,
//                            uri = Uri.fromFile(file),
//                            esImagen = esImagen, // si esVideo => false
//                            timestamp = file.lastModified()
//                        )

                        ItemLista(
                            id = base,
                            nombre = base,
                            uri = Uri.fromFile(file),
                            esImagen = esImagen,
                            timestamp = file.lastModified()
                        )

                    )
                }
            }

            // 3) Cargar pictos del pack básico (categoría "basic_core") desde Room
            val pictosBasic = db.pictogramDao().getPictosForCategory("basic_core")

            // Convertir a ItemLista para que funcionen con MediaAdapter + CuadroImagen
            val pictosAsItems = pictosBasic.map { row ->
                row.toItemLista(timestamp = 0L) // packs: timestamp fijo (luego lo mejoramos)
            }

            listaDeArchivos.addAll(pictosAsItems)

            // 4) Notificar
            mediaAdapter.notifyDataSetChanged()
        }
    }

    fun solicitarContrasena() {
        val builder = AlertDialog.Builder(requireContext(),
            R.style.ThemeOverlay_Comunic_AlertDialog)
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
        Toast.makeText(requireContext(), "Modo eliminación activado", Toast.LENGTH_SHORT).show()
    }

//    fun eliminarSeleccionadosDesdeAdapter(nombres: List<String>) {
//        for (nombre in nombres) {
//            eliminar(nombre) // asumimos que tenés una función que elimina el archivo por nombre
//        }
//        Toast.makeText(requireContext(), "Elementos eliminados", Toast.LENGTH_SHORT).show()
//        mediaAdapter.notifyDataSetChanged()
//    }

    override fun onEliminarSeleccionSolicitada(seleccionados: List<ItemLista>) {
        AlertDialog.Builder(requireContext(),
            R.style.ThemeOverlay_Comunic_AlertDialog)
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
        for (item in lista) {
            val archivo = File(requireContext().filesDir, item.nombre)
            if (archivo.exists()) {
                archivo.delete()
            }
        }
//        cargarArchivosDesdeDirectorio() // O actualizá la lista del RecyclerView
        cancelarModoEliminacion()
    }

    private fun cancelarModoEliminacion() {
        modoEliminacionActivo = false
        mediaAdapter.setModoEliminacion(false)
        Toast.makeText(requireContext(), "Modo eliminación cancelado", Toast.LENGTH_SHORT).show()
    }

/*
//    fun abrirSelectorArchivos() {
//        val intent = Intent(Intent.ACTION_GET_CONTENT)
//        intent.type = "*" // Permitir seleccionar cualquier tipo de archivo
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true) // Permitir selección múltiple

        startActivityForResult(intent, SELECT_FILES_REQUEST_CODE )
    }

    fun comprimirArchivos(files: List<Uri>, destinationPath: String) {
        try {
            val zipFile = ZipFile(destinationPath)

            // Convertir URIs a archivos y añadirlos al archivo ZIP
            for (uri in files) {
                val file = File(getRealPathFromURI(uri)) // Obtener el archivo real desde la URI
                zipFile.addFile(file)
            }

            // Si todo fue bien, el archivo ZIP está creado
            Toast.makeText(requireContext(), "Archivos comprimidos exitosamente", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Error al comprimir los archivos", Toast.LENGTH_SHORT).show()
        }
    }
*/
    fun getRealPathFromURI(uri: Uri): String {
        val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
        cursor?.moveToFirst()
        val columnIndex = cursor?.getColumnIndex(android.provider.MediaStore.Images.Media.DATA)
        return cursor?.getString(columnIndex!!) ?: ""
    }

    fun compartirArchivo(archivo: File) {
        val uri = FileProvider.getUriForFile(
            requireContext(),
            "com.comunic.fileprovider", // Asegúrate de configurar correctamente el FileProvider
            archivo
        )
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "application/zip"
        intent.putExtra(Intent.EXTRA_STREAM, uri)
        startActivity(Intent.createChooser(intent, "Compartir archivo"))
    }

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
            R.style.ThemeOverlay_Comunic_AlertDialog)
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
//        val dialog = AlertDialog.Builder(requireContext())
//            .setTitle("Selecciona elementos para exportar")
//            .setView(dialogView)
//            .setPositiveButton("Continuar", null)   // listener después
//            .setNegativeButton("Cancelar", null)
//            .create()
//
//        dialog.show()
//
//        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
//            .setTextColor(ContextCompat.getColor(requireContext(), R.color.color1))
//
//        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
//            .setTextColor(ContextCompat.getColor(requireContext(), R.color.color1))
//
//        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
//            val elementosSeleccionados =
//                listaDeArchivos.filterIndexed { index, _ -> seleccionados[index] }
//
//            if (elementosSeleccionados.isEmpty()) {
//                Toast.makeText(
//                    requireContext(),
//                    "No seleccionaste ningún elemento",
//                    Toast.LENGTH_SHORT
//                ).show()
//            } else {
//                mostrarResumenSeleccion(elementosSeleccionados)
//                dialog.dismiss()
//            }
//        }

    }


    private fun mostrarResumenSeleccion(elementosSeleccionados: List<ItemLista>) {
        val cantidad = elementosSeleccionados.size
        val nombres = elementosSeleccionados.joinToString("\n") { "- ${it.nombre}" }

        AlertDialog.Builder(requireContext(),
            R.style.ThemeOverlay_Comunic_AlertDialog)
            .setTitle("Resumen de selección")
            .setMessage("Seleccionaste $cantidad elementos:\n\n$nombres")
            .setPositiveButton("Exportar") { _, _ ->
                mostrarDialogoTipoExportacion(elementosSeleccionados)
            }
            .setNegativeButton("Cancelar", null)
            .show()
//        val dialog = AlertDialog.Builder(requireContext())
//            .setTitle("Resumen de selección")
//            .setMessage("Seleccionaste $cantidad elementos:\n\n$nombres")
//            .setPositiveButton("Exportar", null)   // listener después
//            .setNegativeButton("Cancelar", null)
//            .create()
//
//        dialog.show()
//
//        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
//            .setTextColor(ContextCompat.getColor(requireContext(), R.color.color1))
//
//        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
//            .setTextColor(ContextCompat.getColor(requireContext(), R.color.color1))
//
//        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
//            mostrarDialogoTipoExportacion(elementosSeleccionados)
//            dialog.dismiss()
//        }

    }


    private fun mostrarDialogoTipoExportacion(elementosSeleccionados: List<ItemLista>) {
        AlertDialog.Builder(requireContext(),
            R.style.ThemeOverlay_Comunic_AlertDialog)
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

    // IMPORTAR PAQUETES
    fun importarArchivos() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "application/zip"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_CODE_IMPORTAR_ZIP)
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
                val extension = entry!!.name.substringAfterLast(".", "").lowercase(Locale.ROOT) // valido extension de archivo
                val esImagen = when (extension) {
                    in listOf("jpg", "jpeg", "png", "gif", "bmp", "webp") -> true
                    in listOf("mp4", "mkv", "avi", "mov", "webm") -> false
                    else -> {
                        Toast.makeText(requireContext(), "Archivo no soportado: $extension", Toast.LENGTH_SHORT).show()
                        continue
                    }
                }

                val nombreSinExtension = entry!!.name.substringBeforeLast(".")
                val nombreFinal = "$nombreSinExtension.${if (esImagen) "jpg" else "mp4"}" // le doy la extension final, el nombre es el q levanta sin la extension

                val archivoDestino = File(mediaDir, nombreFinal)

                if (archivoDestino.exists()) { // para evitar sobreescribir archivos
                    Toast.makeText(requireContext(), "Ya existe un archivo llamado $nombreSinExtension", Toast.LENGTH_SHORT).show()
                    continue
                }
                // extraigo zip y lo guardo en la carpeta media (archivoDestino me lleva a mediaDir)
                val outputStream = FileOutputStream(archivoDestino)
                zipInputStream.copyTo(outputStream)
                zipInputStream.closeEntry()
                outputStream.close()

                val uriGuardado = Uri.fromFile(archivoDestino)
//                val item = ItemLista(
//                    nombre = nombreSinExtension,
//                    uri = uriGuardado,
//                    esImagen = esImagen,
//                    timestamp = System.currentTimeMillis()
//                )
                val item = ItemLista(
                    id = nombreSinExtension,
                    nombre = nombreSinExtension,
                    uri = uriGuardado,
                    esImagen = esImagen,
                    timestamp = System.currentTimeMillis()
                )

                listaDeArchivos.add(item) // agrego archivos a la lista actual
                saveMediaData(nombreSinExtension, uriGuardado, esImagen) // guardo en el almacenamiento persistente (sharedPreferences)
            }

            zipInputStream.close() // cierro zip y aviso que se importo bien
            Toast.makeText(requireContext(), "Importación exitosa", Toast.LENGTH_SHORT).show()
            mediaAdapter.notifyDataSetChanged()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Error al importar ZIP", Toast.LENGTH_SHORT).show()
        }
    }

    // ACCESOS DIRECTOS DESDE HOME
    private fun setupResumenClicks() {

        // 1) Recientes (Top 6)
        binding.contenedorTop6.setOnClickListener {
            (activity as? MainActivity)?.irARecientesDesdeHome()
        }

        // 2) Listas / Categorías
        binding.contenedorCategorias.setOnClickListener {
            (activity as? MainActivity)?.irAListasDesdeHome()
        }

        // 3) Modos (si ya tenés un fragment real; si no, dejalo en toast)
        binding.contenedor3.setOnClickListener {
            // Si tenés fragment "Sugeridos()" o "Modos()", llamalo acá:
            (activity as? MainActivity)?.irASugeridosDesdeHome()

            //Toast.makeText(requireContext(), "Sección no disponible. Próximamente", Toast.LENGTH_SHORT).show()
        }
    }

    // RESUMEN DE CATEGORÍAS EN HOME
//    private fun cargarPreviewCategorias() {
//        viewLifecycleOwner.lifecycleScope.launch {
//            withContext(Dispatchers.IO) {
//                PackRepository(requireContext(), db).ensureBasicPackInstalled() // cargo pack basico
//            }
//
//            val categorias = withContext(Dispatchers.IO) {
//                db.categoryDao().getAll()
//            }
//
//            val preview = categorias.take(5) // cant a mostrar
//            categoriasQuickAdapter.submitList(preview)
//
//            binding.textoDesarrolloCategorias.visibility =
//                if (preview.isEmpty()) View.VISIBLE else View.GONE
//        }
//    }
    private fun cargarPreviewCategorias() {
        viewLifecycleOwner.lifecycleScope.launch {

            // Asegurar pack básico (si inserta categorías)
            withContext(Dispatchers.IO) {
                PackRepository(requireContext(), db).ensureBasicPackInstalled()
            }

            // 🔵 1) Traer filas crudas desde Room
            val rows = withContext(Dispatchers.IO) {
                db.categoryDao().getAllCategoryPreviewRows()
            }

            // 🔵 2) Transformarlas a CategoryPreview
            val previews = CategoryPreviewMapper.build(rows)

            // 🔵 3) Mostrar máximo 10 en Home
            categoriasAdapter.submitList(previews.take(10))

            // 🔵 4) Ocultar texto "en desarrollo"
            binding.textoDesarrolloCategorias.visibility =
                if (previews.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    companion object {
        const val PERMISSION_REQUEST_CODE = 123
        private const val PICK_IMAGE_REQUEST = 124
        const val PICK_MEDIA_REQUEST = 125
        const val CAPTURE_IMAGE_REQUEST = 126
        const val CAPTURE_VIDEO_REQUEST = 127
        private const val REQUEST_CODE_SIGN_IN = 128
        private const val SELECT_FILES_REQUEST_CODE = 129
        const val REQUEST_CODE_IMPORTAR_ZIP = 130
        private const val REQUEST_CODE_PICK_MEDIA = 131
        const val UCROP_REQUEST_CODE = 69

    }

}
