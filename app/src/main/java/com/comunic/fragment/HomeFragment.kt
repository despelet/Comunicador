package com.comunic.fragment

import android.Manifest
import android.app.Activity.RESULT_OK
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.comunic.adapters.CategoriasCuadriculaAdapter
import com.comunic.CategoryPreview
import com.comunic.CategoryPreviewKeyRow
import com.comunic.databinding.FragmentHomeBinding
import com.yalantis.ucrop.UCrop
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.io.BufferedInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import com.comunic.data.db.AppDatabase
import com.comunic.data.RankingManager
import com.comunic.data.db.PackRepository
import com.comunic.data.mappers.toItemLista
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import com.comunic.data.entity.CategoryEntity
import com.comunic.data.entity.CategoryItemEntity
import com.comunic.data.mappers.resolveItemKeyToItemLista
import com.comunic.fragment.Recientes.Companion
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.squareup.picasso.Picasso
import com.comunic.interfaces.ZipImportListener
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CompletableDeferred
import org.json.JSONObject
import java.util.UUID


class HomeFragment : Fragment(), TextToSpeech.OnInitListener,
    MediaAdapter.OnEliminarSeleccionListener,
    MenuHandler, ZipImportListener {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // Esta función se llama desde MainActivity cuando se presiona el botón de agregar imagen
    override fun abrirSelectorDeImagen() {
        opcionesDeImagen() // Tu función existente que muestra el diálogo de imagen/cámara
    }

    private lateinit var mediaAdapter: MediaAdapter
    private val listaDeArchivos: MutableList<ItemLista> = mutableListOf()
    lateinit var escucharPalabra: TextToSpeech
    private lateinit var audioManager: AudioManager
    private var volumenOriginal = -1
    private var reproduccionesActivas = 0
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
        categoriasAdapter = CategoriasCuadriculaAdapter(
            items = emptyList(),
            onClick = { cat ->
                parentFragmentManager.beginTransaction()
                    .replace(
                        R.id.fragment_container,
                        CategoriaDetalleFragment.newInstance(cat.categoryId, cat.name)
                    )
                    .addToBackStack(null)
                    .commit()
            },
            onOptionsClick = { cat ->
                Toast.makeText(requireContext(), "Mantener presionado: sin acción en Home", Toast.LENGTH_SHORT).show()            },
            onEnableClick = { cat ->
                Toast.makeText(requireContext(), "Mantener presionado: sin acción en Home", Toast.LENGTH_SHORT).show()            },
            onLongClick = { cat ->
                // En Home NO querés borrar listas, así que lo dejamos sin acción
                // (o podés mostrar un toast)
                Toast.makeText(requireContext(), "Mantener presionado: sin acción en Home", Toast.LENGTH_SHORT).show()
            }

        )

        binding.recyclerCategorias.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.recyclerCategorias.adapter = categoriasAdapter
        binding.recyclerCategorias.visibility = View.VISIBLE

        //binding.recyclerCategorias.adapter = categoriasQuickAdapter


        //cargarPreviewCategorias()
        ///// FIN INICIALIZAR CUADRICULA CATEGROIA

        escucharPalabra = TextToSpeech(requireContext(), this)
        audioManager =
            requireContext().getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val recyclerView = binding.recyclerView
        //recyclerView.layoutManager = LinearLayoutManager(this)  // 1 columna
        recyclerView.layoutManager = GridLayoutManager(requireContext(), 4)  // 4 columnas
        // DESCOMENTAR SI QUIERO QUE SE VEA EL RECYCLER
//        mediaAdapter = MediaAdapter(listaDeArchivos, ::eliminar) { id ->
//            audio(id)
//        }
        mediaAdapter = MediaAdapter(
            mediaList = listaDeArchivos,
            eliminar = { itemKey: String -> eliminar(itemKey) },
            palabraAudio = { itemKey: String -> audio(itemKey) }
        )
        recyclerView.adapter = mediaAdapter
        //binding.recyclerView.visibility = View.GONE

        // TOP 6
        lifecycleScope.launch {
            val now = System.currentTimeMillis()
            val bucketId = RankingManager.TimeBucket.bucketIdFromMillis(now)

            val (topBucket, topGlobal) = withContext(Dispatchers.IO) {
                val bucket = db.itemUsadoBucketDao().getTopForBucket(bucketId, 30)
                val global = db.itemUsadoDao().obtenerMasUsados()
                bucket to global
            }

            // Precomputar normalización en IO para no llamar suspend fuera de IO
            val rawToNorm = mutableMapOf<String, String>()
            suspend fun norm(k: String): String {
                return rawToNorm.getOrPut(k) {
                    when {
                        ItemKey.isPicto(k) || ItemKey.isMedia(k) -> k
                        else -> {
                            val existsPicto = db.pictogramDao().getPictoUiById(k) != null
                            if (existsPicto) ItemKey.picto(k) else ItemKey.media(k)
                        }
                    }
                }
            }

            val bucketCount = mutableMapOf<String, Int>()
            val bucketLastUsed = mutableMapOf<String, Long>()
            val globalCount = mutableMapOf<String, Int>()
            val globalLastUsed = mutableMapOf<String, Long>()

            val candidatos = withContext(Dispatchers.IO) {
                // llenar maps bucket/global con norm correcto
                topBucket.forEach {
                    val k = norm(it.nombreArchivo)
                    bucketCount[k] = maxOf(bucketCount[k] ?: 0, it.cantidadDeUsos)
                    bucketLastUsed[k] = maxOf(bucketLastUsed[k] ?: 0L, it.ultimaFechaUso)
                }
                topGlobal.forEach {
                    val k = norm(it.nombreArchivo)
                    globalCount[k] = maxOf(globalCount[k] ?: 0, it.cantidadDeUsos)
                    globalLastUsed[k] = maxOf(globalLastUsed[k] ?: 0L, it.ultimaFechaUso)
                }

                // candidatos normalizados (bucket primero, después global)
                LinkedHashSet<String>().apply {
                    topBucket.forEach { add(norm(it.nombreArchivo)) }
                    topGlobal.forEach { add(norm(it.nombreArchivo)) }
                }.toList()
            }

            // LOGS (ya sin suspend)
            Log.d("TOP6_DEBUG", "===== BUCKET RAW =====")
            topBucket.forEach {
                val n = rawToNorm[it.nombreArchivo] ?: it.nombreArchivo
                Log.d("TOP6_DEBUG", "bucket -> raw=${it.nombreArchivo} norm=$n usos=${it.cantidadDeUsos} last=${it.ultimaFechaUso}")
            }

            Log.d("TOP6_DEBUG", "===== GLOBAL RAW =====")
            topGlobal.take(10).forEach {
                val n = rawToNorm[it.nombreArchivo] ?: it.nombreArchivo
                Log.d("TOP6_DEBUG", "global -> raw=${it.nombreArchivo} norm=$n usos=${it.cantidadDeUsos} last=${it.ultimaFechaUso}")
            }

            Log.d("TOP6_DEBUG", "===== CANDIDATOS NORMALIZADOS =====")
            candidatos.forEach { Log.d("TOP6_DEBUG", "candidate -> $it") }

            val topFinalKeys = candidatos
                .sortedWith(
                    compareByDescending<String> { bucketCount[it] ?: 0 }
                        .thenByDescending { bucketLastUsed[it] ?: 0L }
                        .thenByDescending { globalCount[it] ?: 0 }
                        .thenByDescending { globalLastUsed[it] ?: 0L }
                )
                .take(6)

            Log.d("TOP6_DEBUG", "===== TOP FINAL KEYS =====")
            topFinalKeys.forEachIndexed { index, key ->
                Log.d("TOP6_DEBUG", "#${index + 1} -> $key | bucket=${bucketCount[key]} global=${globalCount[key]}")
            }

            val mediaItemsTop = withContext(Dispatchers.IO) {
                topFinalKeys.mapNotNull { key ->
                    val lastUsed = bucketLastUsed[key] ?: globalLastUsed[key] ?: now

                    val item = resolveItemKeyToItemLista(requireContext(), key)
                        ?: run {
                            // fallback: si vino como MED:basic_x pero existe como PIC:basic_x
                            if (ItemKey.isMedia(key)) {
                                val base = ItemKey.mediaBase(key)
                                val picKey = ItemKey.picto(base)
                                resolveItemKeyToItemLista(requireContext(), picKey)
                            } else null
                        }

                    item?.copy(timestamp = lastUsed)
                }
            }

            Log.d("TOP6_DEBUG", "===== ITEMS MOSTRADOS EN HOME =====")
            mediaItemsTop.forEachIndexed { i, item ->
                Log.d("TOP6_DEBUG", "#${i + 1} UI -> id=${item.id} nombre=${item.nombre} esImagen=${item.esImagen}")
            }

            val adapter = MediaAdapter(
                mediaList = mediaItemsTop.toMutableList(),
                eliminar = { itemKey -> eliminar(itemKey) },
                palabraAudio = { itemKey -> audio(itemKey) }
            )
            binding.recyclerTop6.layoutManager = GridLayoutManager(requireContext(), 3)
            binding.recyclerTop6.adapter = adapter
            binding.recyclerTop6.visibility = View.VISIBLE
        }

        // Orden: (1) bucketCount desc, (2) bucketLastUsed desc, (3) globalCount desc, (4) globalLastUsed desc
        /* val topFinalNombres = candidatos
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

                 ItemLista(
                     id = nombre,
                     nombre = nombre,
                     uri = uri,
                     esImagen = esImagen,
                     timestamp = lastUsed
                 )
             }
         }*/

        loadImageData() // Cargar los datos (imágenes/videos)

        mediaAdapter.notifyDataSetChanged()

        //setupAddButton() // boton agregar imagen
        setupSelectionPanel() // seleccion y eliminacion
        checkReadPermissionIfNeeded() // permisos

//        val addImageButton = findViewById<Button>(R.id.addImageButton)

        // ACCESO RAPIDO A RESUMEN DESDE HOME
        setupResumenClicks()

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = AppDatabase.getDatabase(requireContext())

//        categoriasAdapter = CategoriasCuadriculaAdapter(emptyList()) { cat ->
//            parentFragmentManager.beginTransaction()
//                .replace(R.id.fragment_container, CategoriaDetalleFragment.newInstance(cat.categoryId, cat.name))
//                .addToBackStack(null)
//                .commit()
//        }

        binding.recyclerCategorias.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.recyclerCategorias.adapter = categoriasAdapter

        cargarPreviewCategorias()
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
                    R.style.ThemeOverlay_Comunic_AlertDialog
                )
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
            R.style.ThemeOverlay_Comunic_AlertDialog
        )
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
            type = "*/*" // imagenes o videos
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
            R.style.ThemeOverlay_Comunic_AlertDialog
        )
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



    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d("FLOW", "requestCode=$requestCode resultCode=$resultCode data=$data")

        // devolucion para el ucrop
        if (requestCode == UCROP_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                Log.d("FLOW", "Entró en RESULT_OK ucrop")
               // val resultUri = UCrop.getOutput(data!!)
//                if (resultUri != null) {
//                    ingresarNombreArchivo(resultUri, true)
//                }
                val resultUri = data?.let { UCrop.getOutput(it) }
                if (resultUri != null) {
                    view?.post {
                        if (!isAdded || activity == null || requireActivity().isFinishing) {
                            Log.d("DIALOG", "Fragment no listo, no se muestra dialog")
                            return@post
                        }
                        ingresarNombreArchivo(resultUri, true)
                    }
                }else {
                    Toast.makeText(requireContext(), "Error al recortar la imagen. Usando imagen original.", Toast.LENGTH_SHORT).show()
                    //lastCapturedUri?.let { ingresarNombreArchivo(it, true) }
                    lastCapturedUri?.let {
                        view?.post {
                            if (isAdded) {
                                ingresarNombreArchivo(it, true)
                            }
                        }
                    }
                }
            } else {
                // Si el usuario canceló el crop, usamos la imagen original
//                lastCapturedUri?.let {
//                    ingresarNombreArchivo(it, true)
//                }
                lastCapturedUri?.let {
                    view?.post {
                        if (isAdded) {
                            ingresarNombreArchivo(it, true)
                        }
                    }
                }
            }
            return
        }

        // devolicon para la captura de imagen o video
        if (resultCode == RESULT_OK) {
            Log.d("FLOW", "Entró en RESULT_OK")
            val mediaUri = when (requestCode) {
                PICK_MEDIA_REQUEST -> data?.data
                CAPTURE_IMAGE_REQUEST -> lastCapturedUri
                CAPTURE_VIDEO_REQUEST -> lastCapturedUri
                else -> null
            }
            Log.d("FLOW", "mediaUri=$mediaUri")

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
                        view?.post {
                            if (!isAdded || activity == null || requireActivity().isFinishing) {
                                Log.d("DIALOG", "Fragment no listo (video)")
                                return@post
                            }
                            ingresarNombreArchivo(mediaUri, false)
                        }
                    }
                }
            } else {
                Log.e("CaptureError", "Media URI is null")
            }
        }

        // devolucion para el importar archivos
        val uri = data?.data ?: return
        when (requestCode) {
            REQUEST_CODE_IMPORTAR_ELEMENTOS -> {
                importarElementosDesdeZip(uri)
            }
            REQUEST_CODE_IMPORTAR_LISTA -> {
                viewLifecycleOwner.lifecycleScope.launch {
                    val esLista = esZipConListas(uri)
                    if (!esLista) {
                        Toast.makeText(
                            requireContext(),
                            "El ZIP no contiene listas válidas",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@launch
                    }
                    importarListaDesdeZip(uri)
                }
            }
        }
    }




    private fun ingresarNombreArchivo(mediaUri: Uri?, isImage: Boolean) {
    Log.d("DIALOG_FLOW", "Intentando mostrar dialog desde: ${this::class.java.simpleName}")
        if (!isAdded || activity == null || requireActivity().isFinishing) {
            Log.e("DIALOG", "Fragment no está activo, no se puede mostrar dialog")
            return
        }
        Log.d("TEST", "Se llamó ingresarNombreArchivo")

        val view = layoutInflater.inflate(R.layout.dialog_ingresar_sonido, null)

        val imagePreview = view.findViewById<ImageView>(R.id.imagePreview)
        val editNombre = view.findViewById<EditText>(R.id.editNombre)
        val btnGuardar = view.findViewById<MaterialButton>(R.id.btnGuardar)
        val btnCancelar = view.findViewById<MaterialButton>(R.id.btnCancelar)

        // Preview
        if (mediaUri != null) {
            Picasso.get().load(mediaUri).into(imagePreview)
        }
    Log.d("DIALOG", "Mostrando dialog ingresarNombreArchivo")
        val dialog = AlertDialog.Builder(
            requireContext(),
            R.style.ThemeOverlay_Comunic_AlertDialog
        )
            .setView(view)
            .create()

        /*btnGuardar.setOnClickListener {
            val nombre = editNombre.text.toString().trim()

            if (mediaUri != null && nombre.isNotEmpty()) {
                guardarArchivo(mediaUri, nombre, isImage)
                dialog.dismiss()
            } else {
                editNombre.error = "Ingresá un nombre"
            }
        }*/
        btnGuardar.setOnClickListener {
            val nombre = editNombre.text.toString().trim()

            if (mediaUri != null && nombre.isNotEmpty()) {

                val exito = guardarArchivo(mediaUri, nombre, isImage)

                dialog.dismiss()

                if (exito) {
                    mostrarSnackbar(
                        if (isImage) "Tu imagen se guardó con éxito" else "Tu video se guardó con éxito",
                        true
                    )
                } else {
                    mostrarSnackbar("Error al guardar", false)
                }

            } else {
                editNombre.error = "Ingresá un nombre"
            }
        }

        btnCancelar.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
//        dialog.window?.setBackgroundDrawable(
//            ColorDrawable(android.graphics.Color.TRANSPARENT)
//        )
    dialog.setCancelable(false)
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


    private fun guardarArchivo(mediaUri: Uri, nombre: String, esImagen: Boolean): Boolean {

        val savedUri = guardarEnAlmacenamientoInterno(requireContext(), mediaUri, nombre, esImagen)

        if (savedUri != null) {

            val index = listaDeArchivos.indexOfFirst { it.nombre == nombre }
            if (index != -1) {
                listaDeArchivos.removeAt(index)
                mediaAdapter.notifyItemRemoved(index)
            }

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

            return true
        }

        return false
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
                                reproduccionesActivas--
                                if (reproduccionesActivas <= 0) {
                                    reproduccionesActivas = 0
                                    if (volumenOriginal >= 0) {
                                        audioManager.setStreamVolume(
                                            AudioManager.STREAM_MUSIC,
                                            volumenOriginal,
                                            0
                                        )
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                    override fun onError(utteranceId: String?) {

                        Handler(Looper.getMainLooper()).post {

                            reproduccionesActivas--

                            if (reproduccionesActivas < 0) {
                                reproduccionesActivas = 0
                            }
                        }
                    }
                }
            )

        } else {

            Log.e("TextToSpeech", "Error al inicializar")
        }
    }

 /*   private fun audio(itemKeyOrId: String) {
        if (!::escucharPalabra.isInitialized) return

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val texto = SpeechTextResolver.resolve(db, itemKeyOrId)

            withContext(Dispatchers.Main) {
                escucharPalabra.speak(
                    texto,
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    null
                )
            }
        }
    }*/
    private fun audio(itemKeyOrId: String) {

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

                     reproduccionesActivas++

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

                        //ItemLista(
                            //id = base,
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

            // 3) Cargar pictos de packs habilitados (según installed_packs.enabled)
            val pictos = db.pictogramDao().getEnabledPictosUi()

            val pictosAsItems = pictos.map { row ->
                row.toItemLista(timestamp = 0L)
                    .copy(id = ItemKey.picto(row.pictogramId)) // ✅ id = "PIC:xxx"
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
                        this@HomeFragment,
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

    /*private fun exportarElementos(elementos: List<ItemLista>) {
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
            type = "**"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Compartir archivos"))
    }

    private fun exportarElementosComoZip(elementos: List<ItemLista>, nombreZip: String  ) {
        if (elementos.isEmpty()) {
            Toast.makeText(requireContext(), "No seleccionaste elementos", Toast.LENGTH_SHORT).show()
            return
        }

        val zipFile = File(requireContext().cacheDir, nombreZip)

        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
                for (item in elementos) {
                    val inputStream = requireContext().contentResolver.openInputStream(item.uri) ?: continue

                    // obtener mime y extension
                    var extension = File(item.uri.path ?: "")
                        .extension
                        .lowercase(Locale.ROOT)

                    if (extension.isBlank()) {

                        val mimeType = requireContext().contentResolver.getType(item.uri)

                        extension = MimeTypeMap
                            .getSingleton()
                            .getExtensionFromMimeType(mimeType)
                            ?.lowercase(Locale.ROOT)
                            ?: ""
                    }

                    // añade extensión si no está
                    val fileName = if (
                        extension.isNotBlank() &&
                        !item.nombre.endsWith(".$extension")
                    ) {
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

    private fun pedirNombreZip(
        onNombreListo: (String) -> Unit
    ) {

        val inputLayout = TextInputLayout(requireContext())
        val editText = TextInputEditText(requireContext())

        val fecha = SimpleDateFormat("yyyy_MM_dd",Locale.getDefault()).format(Date())

        editText.setText("bicom_$fecha")

        inputLayout.hint = "Nombre del ZIP"
        inputLayout.addView(editText)

        AlertDialog.Builder(
            requireContext(),
            R.style.ThemeOverlay_Comunic_AlertDialog
        )
            .setTitle("Nombre del archivo ZIP")
            .setView(inputLayout)
            .setPositiveButton("Continuar") { _, _ ->

                val texto = editText.text
                    ?.toString()
                    ?.trim()
                    .orEmpty()

                val nombreBase =
                    if (texto.isBlank()) {
                        "bicom_$fecha"
                    } else {
                        texto.replace(
                            Regex("[^a-zA-Z0-9._-]"),
                            "_"
                        )
                    }

                val nombreFinal =  if (nombreBase.endsWith(".zip")) {  nombreBase
                    } else {
                        "$nombreBase.zip"
                    }

                onNombreListo(nombreFinal)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }*/

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
                REQUEST_CODE_IMPORTAR_LISTA
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
                REQUEST_CODE_IMPORTAR_ELEMENTOS
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

                val item = ItemLista(
                    id = ItemKey.media(nombreSinExtension),
                    nombre = nombreSinExtension,
                    uri = uriGuardado,
                    esImagen = esImagen,
                    timestamp = System.currentTimeMillis()
                )

                listaDeArchivos.add(item) // agrego archivos a la lista actual
                saveMediaData(nombreSinExtension, uriGuardado, esImagen) // guardo en el almacenamiento persistente (sharedPreferences)
                mediaAdapter.notifyDataSetChanged()
            }

            zipInputStream.close()
            mediaAdapter.notifyDataSetChanged()
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
                        val existing =db.categoryDao().getCategoryByName(nombreFinal)
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
                                                .getCategoryByName(nuevoNombre) != null
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

                        val nuevaCategoriaId = "user_" + UUID.randomUUID()

                        val order = db.categoryDao().getMaxCategoryOrderIndex() + 1

                        db.categoryDao().upsert(
                            CategoryEntity(
                                categoryId = nuevaCategoriaId,
                                name = nombreFinal,
                                orderIndex = order,
                                createdAt = System.currentTimeMillis()
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

                        val itemExistente = ItemLista(
                            id = ItemKey.media(nombreSinExtension),
                            nombre = nombreSinExtension,
                            uri = Uri.fromFile(archivoDestino),
                            esImagen = esImagen,
                            timestamp = archivoDestino.lastModified()
                        )

                        val nextOrder =
                            db.categoryDao()
                                .getMaxOrderIndex(categoria.categoryId) + 1

                        db.categoryDao().insertCategoryItem(
                            CategoryItemEntity(
                                placementId = UUID.randomUUID().toString(),
                                categoryId = categoria.categoryId,
                                itemKey = itemExistente.id,
                                orderIndex = nextOrder
                            )
                        )

                        continue
                    }

                    val outputStream = FileOutputStream(archivoDestino)

                    zipInputStream.copyTo(outputStream)

                    outputStream.close()
                    zipInputStream.closeEntry()

                    val uriGuardado = Uri.fromFile(archivoDestino)

                    val item = ItemLista(
                        id = ItemKey.media(nombreSinExtension),
                        nombre = nombreSinExtension,
                        uri = uriGuardado,
                        esImagen = esImagen,
                        timestamp = System.currentTimeMillis()
                    )

                    itemsImportados.add(item)

                    saveMediaData(
                        nombreSinExtension,
                        uriGuardado,
                        esImagen
                    )

                    val nextOrder =
                        db.categoryDao()
                            .getMaxOrderIndex(categoria.categoryId) + 1

                    db.categoryDao().insertCategoryItem(
                        CategoryItemEntity(
                            placementId = UUID.randomUUID().toString(),
                            categoryId = categoria.categoryId,
                            itemKey = item.id,
                            orderIndex = nextOrder
                        )
                    )
                }

                zipInputStream.close()

                withContext(Dispatchers.Main) {
                    listaDeArchivos.addAll(itemsImportados)
                    mediaAdapter.notifyDataSetChanged()
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

    private fun cargarPreviewCategorias() {
        viewLifecycleOwner.lifecycleScope.launch {

            withContext(Dispatchers.IO) {
                PackRepository(requireContext(), db).ensureBasicPackInstalled()
            }

            val rows0 = withContext(Dispatchers.IO) { db.categoryDao().getHomeActiveCategoryPreviewKeyRows(0) }
            val rows1 = withContext(Dispatchers.IO) { db.categoryDao().getHomeActiveCategoryPreviewKeyRows(1) }
            val rows2 = withContext(Dispatchers.IO) { db.categoryDao().getHomeActiveCategoryPreviewKeyRows(2) }
            val rows3 = withContext(Dispatchers.IO) { db.categoryDao().getHomeActiveCategoryPreviewKeyRows(3) }

            data class CatAgg(
                val categoryId: String,
                val name: String,
                val isSystem: Boolean,
                val packId: String,          // ✅ no-null
                val packEnabled: Boolean,    // ✅ no-null
                val keys: MutableList<String> = mutableListOf()
            )

            val byCat = LinkedHashMap<String, CatAgg>()

            fun addRows(rows: List<CategoryPreviewKeyRow>) {
                rows.forEach { r ->
                    val agg = byCat.getOrPut(r.categoryId) {
                        CatAgg(
                            categoryId = r.categoryId,
                            name = r.name,
                            isSystem = r.isSystem,
                            packId = r.packId,
                            packEnabled = r.packEnabled
                        )
                    }
                    r.itemKey?.takeIf { it.isNotBlank() }?.let { agg.keys.add(it) }
                }
            }

            addRows(rows0); addRows(rows1); addRows(rows2); addRows(rows3)

            val TAG = "PREVIEW_2X2"

            val previews = withContext(Dispatchers.IO) {
                byCat.values.map { agg ->

                    Log.d(TAG, "CAT ${agg.categoryId} '${agg.name}' keys=${agg.keys}")

                    val uris = agg.keys.mapNotNull { key ->
                        val item = resolveItemKeyToItemLista(requireContext(), key)
                        Log.d(TAG, "  key=$key -> item=${item != null} uri=${item?.uri} scheme=${item?.uri?.scheme}")
                        item?.uri?.toString()
                    }.take(4)

                    Log.d(TAG, "CAT ${agg.categoryId} urisFinal=$uris")

                    CategoryPreview(
                        categoryId = agg.categoryId,
                        name = agg.name,
                        previewUris = uris,
                        isSystem = agg.isSystem,
                        packEnabled = agg.packEnabled ?: true,
                        packId = agg.packId
                    )
                }
            }

            categoriasAdapter.submitList(previews.take(10))
            binding.textoDesarrolloCategorias.visibility =
                if (previews.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        cargarPreviewCategorias()
        (activity as? MainActivity)
            ?.setZipImportListener(this)
    }

    override fun onPause() {
        super.onPause()

        (activity as? MainActivity)
            ?.setZipImportListener(null)
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
        private const val REQUEST_CODE_IMPORTAR_LISTA = 1001
        private const val REQUEST_CODE_IMPORTAR_ELEMENTOS = 1002
        private const val REQUEST_CODE_PICK_MEDIA = 131
        const val UCROP_REQUEST_CODE = 69

    }

}
