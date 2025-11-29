package com.comunic

//import android.os.Build.VERSION_CODES.R
import android.Manifest
import android.app.Activity
import com.comunic.R
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
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.comunic.data.AppDatabase
import com.comunic.data.RankingManager
import com.comunic.ui.theme.HomeFragment
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationView
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.Drive
import com.google.api.client.json.gson.GsonFactory
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale
import java.util.concurrent.Executors
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.FileHeader
import net.lingala.zip4j.model.ZipParameters
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream


class MainActivity : AppCompatActivity(),  NavigationView.OnNavigationItemSelectedListener {


    private lateinit var drawerLayout: DrawerLayout     // para el menu lateral
    private lateinit var navigationView: NavigationView
    private lateinit var bottomNav: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        lifecycleScope.launch {
            val dao = AppDatabase.getDatabase(applicationContext).itemUsadoDao()
            dao.getAllItemsUsados() // o cualquier consulta mínima
        }



        // MENU LATERAL
        drawerLayout = findViewById(R.id.drawer_layout) // inicializacion del drawer layout y navigation view
        navigationView = findViewById(R.id.navigation_view) // inicializacion del drawer layout y navigation view
        navigationView.setNavigationItemSelectedListener(this) // Configurar NavigationView y su listener
        // BARRA INFERIOR
        bottomNav = findViewById(R.id.bottom_nav)

        val addImageButton = findViewById<Button>(R.id.addImageButton)
        addImageButton?.setOnClickListener {
            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_IMAGES
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(permission), HomeFragment.PERMISSION_REQUEST_CODE
                )
            } else {
                val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
//                if (fragment is HomeFragment) {
//                    fragment.opcionesDeImagen()
//                }
                if (fragment is MenuHandler) {
                    fragment.abrirSelectorDeImagen()
                } else {
                    Toast.makeText(this, "Función no disponible en esta sección", Toast.LENGTH_SHORT).show()
                }
            }
        }


        val menuButton: ImageButton = findViewById(R.id.menu)
        menuButton.setOnClickListener {                     // Abrir el menú lateral al presionar el botón
            drawerLayout.openDrawer(GravityCompat.START)
        }

        // Listener para la barra inferior
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.menu_home -> {
                    openFragment(HomeFragment())
                    true
                }
                R.id.menu_recientes -> {
                    openFragment(Recientes())
                    true
                }
                R.id.menu_listas -> {
                    openFragment(Listas())
                    true
                }
                R.id.menu_sugeridos -> {
                    openFragment(Sugeridos())
                    true
                }
                else -> false
            }
        }

        // Cargar fragment por defecto (Home)
        if (savedInstanceState == null) {
            bottomNav.selectedItemId = R.id.menu_home
        }
    }

    private fun openFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    // Manejo de los ítems del menú lateral
    override fun onNavigationItemSelected(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {

            R.id.nav_nosotros -> {
                // Acción para "Sobre BiCom"
                Toast.makeText(this, "Sección no disponible. Próximamente", Toast.LENGTH_SHORT).show()
            }

            R.id.nav_proteger -> {
                // Acción para control parental
                Toast.makeText(this, "Sección no disponible. Próximamente", Toast.LENGTH_SHORT).show()
            }

            R.id.nav_control_parental -> {
                // Acción para control parental
                Toast.makeText(this, "Sección no disponible. Próximamente", Toast.LENGTH_SHORT).show()
            }

            R.id.nav_editar -> {
                // Acción para editar
                Toast.makeText(this, "Sección no disponible. Próximamente", Toast.LENGTH_SHORT).show()
            }

            R.id.nav_eliminar -> {
                // Acción para eliminar
                //Toast.makeText(this, "Seleccionado: ${menuItem.title}", Toast.LENGTH_SHORT).show()
                //solicitarContrasena()
                withHomeFragment { solicitarContrasena() }
                val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
                when (fragment) {

                    is Recientes -> fragment.solicitarContrasena()
                    //is FavoritosFragment -> fragment.solicitarContrasena() // AGREGAR MAS PESTAÑAS
                    else -> Toast.makeText(this, "Fragmento no compatible", Toast.LENGTH_SHORT).show()
                }

            }

            /*R.id.nav_drive -> {  // Nuevo caso para conectar con Google Drive
                signInToGoogle()  // Llama a la función para iniciar sesión en Google Drive
            }
            R.id.nav_cambiar_cuenta -> {
                // Mostrar un dialogo de confirmación
                val builder = AlertDialog.Builder(this)
                builder.setMessage("¿Está seguro que quiere cerrar sesión?")
                    .setCancelable(false)
                    .setPositiveButton("Sí") { dialog, id ->
                        // Cerrar sesión y permitir elegir cuenta nuevamente
                        signOutAndSelectAccount()
                    }
                    .setNegativeButton("No") { dialog, id ->
                        // Solo cerrar el diálogo
                        dialog.dismiss()
                    }
                val alert = builder.create()
                alert.show()
            } */

            R.id.exportar_archivos -> {
                // Acción para exportar archivos
                Toast.makeText(this, "Seleccionado: ${menuItem.title}", Toast.LENGTH_SHORT).show()
                //abrirSelectorArchivos()
                //withHomeFragment { mostrarDialogoSeleccionarElementos() }
                val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
                when (fragment) {
                    is HomeFragment -> fragment.mostrarDialogoSeleccionarElementos()
                    is Recientes -> fragment.mostrarDialogoSeleccionarElementos()
                    //is FavoritosFragment -> fragment.mostrarDialogoSeleccionarElementos() // AGREGAR MAS PESTAÑAS
                    else -> Toast.makeText(this, "Fragmento no compatible", Toast.LENGTH_SHORT).show()
                }

            }

            R.id.importar_archivos -> {
                // Acción para importar archivos
                Toast.makeText(this, "Seleccionado: ${menuItem.title}", Toast.LENGTH_SHORT).show()
                //funcion
                //withHomeFragment { importarArchivos() }
                val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
                when (fragment) {
                    is HomeFragment -> fragment.importarArchivos()
                    is Recientes -> fragment.importarArchivos()
                    //is FavoritosFragment -> fragment.importarArchivos() // AGREGAR MAS PESTAÑAS
                    else -> Toast.makeText(this, "Fragmento no compatible", Toast.LENGTH_SHORT).show()
                }

            }

        }
        drawerLayout.closeDrawers() // Cierra el menú después de seleccionar un ítem
        return true
    }

    // funcion para llamar a las funciones que estan dentro de homefragment.
    private fun withHomeFragment(action: HomeFragment.() -> Unit) {
        val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
        if (fragment is HomeFragment) {
            fragment.action()
        } else {
            Toast.makeText(this, "HomeFragment no está activo", Toast.LENGTH_SHORT).show()
        }
    }



}

        /*
//    //para subir un archivo solo
//    private fun openGallery() {
//        val intent = Intent(Intent.ACTION_PICK).apply {
//            type = "image/* video/*" // imagenes o videos
//            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
//        }
//        startActivityForResult(intent, PICK_MEDIA_REQUEST)
//    }
//
//
//    /* inicializo */
//    //private lateinit var lastCapturedUri: Uri
//    private var lastCapturedUri: Uri? = null
//
//    private fun openCamera() {
//        val options = arrayOf("Capturar Imagen", "Grabar Video")
//
//        val builder = AlertDialog.Builder(this)
//        builder.setTitle("Seleccionar Opción")
//        builder.setItems(options) { _, which ->
//            when (which) {
//                0 -> {
//                    // Capturar Imagen
//                    val photoUri: Uri = createImageUri() // Crea un URI utilizando FileProvider
//                    lastCapturedUri = photoUri
//                    val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
//                    intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
//                    startActivityForResult(intent, CAPTURE_IMAGE_REQUEST)
//                }
//                1 -> {
//                    val videoUri: Uri = createVideoUri() // Crea un URI utilizando FileProvider
//                    lastCapturedUri = videoUri
//                    // Grabar Video
//                    val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
//                    intent.putExtra(MediaStore.EXTRA_OUTPUT, videoUri)
//                    startActivityForResult(intent, CAPTURE_VIDEO_REQUEST)
//                }
//            }
//        }
//        builder.show()
//    }
//
//    private fun createImageUri(): Uri {
//        val contentValues = ContentValues().apply {
//            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
//            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Comunic")
//        }
//        return contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)!!
//    }
//
//    private fun createVideoUri(): Uri {
//        val contentValues = ContentValues().apply {
//            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
//            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Comunic")
//        }
//        return contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)!!
//    }
//
//    // EDICION ucrop
//    fun startCrop(uri: Uri) {
//        val destinationUri = Uri.fromFile(File(cacheDir, "imagen_editada_${System.currentTimeMillis()}.jpg"))
//
//        val options = UCrop.Options().apply {
//            setCompressionFormat(Bitmap.CompressFormat.JPEG)
//            setCompressionQuality(90)
//            setFreeStyleCropEnabled(true) // Permite mover y redimensionar libremente
//
//            setToolbarColor(ContextCompat.getColor(this@MainActivity, R.color.color3))      // barra superior
//            setStatusBarColor(ContextCompat.getColor(this@MainActivity, R.color.color3))     // barra de estado
//            setToolbarWidgetColor(ContextCompat.getColor(this@MainActivity, R.color.color5)) // texto/iconos
//            setActiveControlsWidgetColor(ContextCompat.getColor(this@MainActivity, R.color.color3)) // botones activos
//            setRootViewBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.color4))   // fondo general
//
//            setToolbarTitle("Editar imagen") // título personalizado
//        }
//
//        UCrop.of(uri, destinationUri)
//            .withOptions(options)
//            .withAspectRatio(1f, 1f) // Si querés forzar cuadrado. Podés cambiarlo o quitarlo.
//            .start(this@MainActivity, UCROP_REQUEST_CODE)
//    }
//
//    /* para ver si esta inicializada la variable lastcaptureduri
//    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
//        super.onActivityResult(requestCode, resultCode, data)
//
//        if (resultCode == RESULT_OK) {
//            val mediaUri = when (requestCode) {
//                PICK_MEDIA_REQUEST -> data?.data
//                CAPTURE_IMAGE_REQUEST -> lastCapturedUri
//                CAPTURE_VIDEO_REQUEST -> lastCapturedUri
//                else -> null
//            }
//            if (mediaUri != null) {
//                val mimeType = contentResolver.getType(mediaUri)
//                if (mimeType != null) {
//
////                    if (mimeType.startsWith("image/")) {
////                        Log.d("CapturedMedia", "Imagen capturada. URI: $mediaUri")
////                        ingresarNombreArchivo(mediaUri, true)
//
//                    if (mimeType.startsWith("image/")) {
//                        if (requestCode == CAPTURE_IMAGE_REQUEST) {
//                            startCrop(mediaUri) // 👉 Editamos antes de continuar
//                        } else {
//                            ingresarNombreArchivo(mediaUri, true)
//                        }
//                    } else if (mimeType.startsWith("video/")) {
//                        Log.d("CapturedMedia", "Video capturado URI: $mediaUri")
//                        ingresarNombreArchivo(mediaUri, false)
//                    }
//                }
//            } else {
//                Log.e("CaptureError", "Media URI is null")
//            }
//
//            if (requestCode == UCROP_REQUEST_CODE && resultCode == RESULT_OK) {
//                val resultUri = UCrop.getOutput(data!!)
//                if (resultUri != null) {
//                    ingresarNombreArchivo(resultUri, true) // Usamos imagen recortada
//                } else {
//                    Toast.makeText(this, "Error al recortar la imagen", Toast.LENGTH_SHORT).show()
//                }
//            }
//        }
//
//
//
//
//        /*
//        if (requestCode == REQUEST_CODE_SIGN_IN && resultCode == RESULT_OK) {
//            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
//            try {
//                val account = task.getResult(ApiException::class.java)
//                if (account != null) {
//                    val email = account.email ?: "Correo no disponible"
//                    // Inicializa el servicio de Google Drive después de la autenticación en un hilo en segundo plano
//                    val executorService = Executors.newSingleThreadExecutor()
//                    executorService.execute {
//                        driveServiceHelper = DriveServiceHelper(this, getGoogleDriveService(account))
//
//                        // Ahora que tienes el helper, puedes cargar las imágenes
//                        loadImageData()
//
//                        // Mostrar mensaje de éxito en el hilo principal
//                        runOnUiThread {
//                            Toast.makeText(this, "Conectado a Google Drive.\n Email: $email", Toast.LENGTH_SHORT).show()
//                        }
//                    }
//                } else {
//                    Log.e("GoogleDrive", "Cuenta de Google es nula después del inicio de sesión")
//                }
//            } catch (e: ApiException) {
//                Log.e("GoogleDrive", "Error en la autenticación de Google Drive: ${e.statusCode}")
//                Toast.makeText(this, "Error en la autenticación", Toast.LENGTH_SHORT).show()
//            }
//        }*/
//
//        if (requestCode == SELECT_FILES_REQUEST_CODE && resultCode == RESULT_OK) {
//            val archivosSeleccionados = mutableListOf<Uri>()
//            data?.data?.let { archivosSeleccionados.add(it) }
//            data?.clipData?.let {
//                for (i in 0 until it.itemCount) {
//                    archivosSeleccionados.add(it.getItemAt(i).uri)
//                }
//            }
//
//            val archivoComprimido = File(getExternalFilesDir(null), "exported_files.zip") // Comprimir los archivos seleccionados
//            comprimirArchivos(archivosSeleccionados, archivoComprimido.absolutePath)
//
//            compartirArchivo(archivoComprimido) // Compartir el archivo comprimido
//        }
//        if (resultCode == Activity.RESULT_OK && requestCode == REQUEST_CODE_IMPORTAR_ZIP) {
//            val uri = data?.data
//            uri?.let { importarElementosDesdeZip(it) }
//        }
//    }*/
//
//    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
//        super.onActivityResult(requestCode, resultCode, data)
//
//        if (requestCode == UCROP_REQUEST_CODE && resultCode == RESULT_OK) {
//            val resultUri = UCrop.getOutput(data!!)
//            if (resultUri != null) {
//                ingresarNombreArchivo(resultUri, true) // Usamos imagen recortada
//            } else {
//                Toast.makeText(this, "Error al recortar la imagen", Toast.LENGTH_SHORT).show()
//            }
//            return
//        }
//
//        if (resultCode == RESULT_OK) {
//            val mediaUri = when (requestCode) {
//                PICK_MEDIA_REQUEST -> data?.data
//                CAPTURE_IMAGE_REQUEST -> lastCapturedUri
//                CAPTURE_VIDEO_REQUEST -> lastCapturedUri
//                else -> null
//            }
//
//            if (mediaUri != null) {
//                val mimeType = contentResolver.getType(mediaUri)
//                if (mimeType != null) {
//                    if (mimeType.startsWith("image/")) {
//                        if (requestCode == CAPTURE_IMAGE_REQUEST) {
//                            startCrop(mediaUri) // 👉 Editamos antes de continuar
//                        } else {
//                            ingresarNombreArchivo(mediaUri, true)
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
//    }
//
//
//    private fun ingresarNombreArchivo(mediaUri: Uri?, isImage: Boolean) {
//        val dialogView = layoutInflater.inflate(R.layout.dialog_image_name, null)
//        val nameEditText = dialogView.findViewById<EditText>(R.id.nameEditText)
//        AlertDialog.Builder(this)
//            .setTitle(if (isImage) "Sonido de la imagen" else "Sonido del video")
//            .setView(dialogView)
//            .setPositiveButton("OK") { _, _ ->
//                val nombreArchivo = nameEditText.text.toString()
//                if (mediaUri != null && nombreArchivo.isNotBlank()) {
//                    guardarArchivo(mediaUri, nombreArchivo, isImage)
//
//                } else {
//                    Toast.makeText(this, "El nombre no puede estar vacío", Toast.LENGTH_SHORT)
//                        .show()
//                }
//            }
//            .setNegativeButton("Cancelar", null)
//            .show()
//    }
//
//    private fun guardarArchivo(mediaUri: Uri, nombre: String, esImagen: Boolean) {
//        /* si quiero que el nombre contecta el timestamp
//        val timestamp = System.currentTimeMillis()
//        val nombreConTimestamp = "$(nombre)_$timestamp" */
//
////        val savedUri = guardarEnAlmacenamiento(mediaUri, nombreConTimestamp, esImagen)
//        val savedUri = guardarEnAlmacenamientoInterno(this, mediaUri, nombre, esImagen)
//        if (savedUri != null) {
//            // Eliminar el elemento antiguo
//            val index = listaDeArchivos.indexOfFirst { it.nombre == nombre }
//            if (index != -1) {
//                listaDeArchivos.removeAt(index)
//                mediaAdapter.notifyItemRemoved(index)
//            }
//            // Agregar la imagen o video a la lista y guardarla
//            //listaDeArchivos.add(Triple(nombre, savedUri, esImagen))
//            listaDeArchivos.add(ItemLista(nombre, savedUri, esImagen, System.currentTimeMillis()))
//            saveMediaData(nombre, savedUri, esImagen)
//            mediaAdapter.notifyItemInserted(listaDeArchivos.size - 1)
//            Toast.makeText( this,
//                if (esImagen) "Imagen guardada como $nombre" else "Video guardado como $nombre",
//                Toast.LENGTH_SHORT
//            ).show()
//
//            /*// 📤 **Subir a Google Drive**
//            val mimeType = if (esImagen) "image/jpeg" else "video/mp4"
//            driveServiceHelper.uploadFile(savedUri, nombre, mimeType)
//                .addOnSuccessListener { fileId ->
//                    Log.d("GoogleDrive", "Archivo subido con éxito. ID: $fileId")
//                }
//                .addOnFailureListener { e ->
//                    Log.e("GoogleDrive", "Error al subir archivo: ${e.message}")
//                }
//
//        } else {
//            Toast.makeText(this, "Error al guardar", Toast.LENGTH_SHORT).show()
//        } */
//        }
//    }
//
//    /*
//    private fun guardarEnAlmacenamiento(
//    mediaUri: Uri,
//    nombreArchivo: String,
//    esImagen: Boolean
//    ): Uri? {
//    val contentResolver = contentResolver
//    val contentValues = ContentValues().apply {
//    put(MediaStore.MediaColumns.DISPLAY_NAME, nombreArchivo)
//    put(MediaStore.MediaColumns.MIME_TYPE, if (esImagen) "image/jpeg" else "video/mp4")
//    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Comunic")
//    }
//    val uri = if (esImagen) {
//    contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
//    } else contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
//    uri?.let {
//    try {
//    val inputStream: InputStream? = contentResolver.openInputStream(mediaUri)
//    val outputStream: OutputStream? = contentResolver.openOutputStream(it)
//    inputStream?.copyTo(outputStream!!)
//    inputStream?.close()
//    outputStream?.close()
//    return it
//    } catch (e: IOException) {
//    e.printStackTrace()
//    }
//    }
//    return null
//    }
//    */
//
//    private fun guardarEnAlmacenamientoInterno(
//        context: Context,
//        mediaUri: Uri,
//        nombreArchivo: String,
//        esImagen: Boolean
//    ): Uri? {
//        //val subCarpeta = if (esImagen) "imagenes" else "videos" // si quiero 2 carpetas diferentes
////        val directorio = File(context.filesDir, subCarpeta) // si quiero 2 carpetas diferentes
//        val directorio = File(context.filesDir, "media") // Carpeta "media" en el almacenamiento interno
//
//        if (!directorio.exists()) {
//            directorio.mkdirs() // Crear la carpeta si no existe
//        }
//
//        val archivo = File(directorio, "$nombreArchivo.${if (esImagen) "jpg" else "mp4"}")
//        return try {
//            context.contentResolver.openInputStream(mediaUri)?.use { inputStream ->
//                FileOutputStream(archivo).use { outputStream ->
//                    inputStream.copyTo(outputStream)
//                }
//            }
//            Uri.fromFile(archivo) // Retorna el URI del archivo guardado
//        } catch (e: IOException) {
//            e.printStackTrace()
//            null
//        }
//    }
//
//    private fun saveMediaData(nombreArchivo: String, mediaUri: Uri, isImage: Boolean) {
//        val sharedPreferences = getSharedPreferences("media_data", MODE_PRIVATE)
//        val editor = sharedPreferences.edit()
//        editor.putString(nombreArchivo, mediaUri.toString())
//        editor.putBoolean("$nombreArchivo|type", isImage) // Guardar si es imagen o video
//        editor.apply()
//    }
//
//    private fun opcionesDeImagen() {
//        val opciones = arrayOf("Abrir Galeria", "Abrir Camara")
//        val builder = AlertDialog.Builder(this)
//        builder.setTitle("Seleccione una opción")
//        builder.setItems(opciones) { _, which ->
//            when (which) {
//                0 -> openGallery()
//                1 -> openCamera()
//            }
//        }
//        builder.show()
//    }
//
//    override fun onInit(status: Int) {
//        if (status == TextToSpeech.SUCCESS) {
//            val idioma = escucharPalabra.setLanguage(Locale("es","AR"))
//            if (idioma == TextToSpeech.LANG_MISSING_DATA || idioma == TextToSpeech.LANG_NOT_SUPPORTED)
//                Log.e("TextToSpeech", "Error con el idioma")
//        } else { Log.e("TextToSpeech", "Error al inicializar") }
//    }
//
//    private fun audio(text: String) {
//        if (::escucharPalabra.isInitialized) {
//            escucharPalabra.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
//        }
//    }
//
//    override fun onDestroy() {
//        if (::escucharPalabra.isInitialized) {
//            escucharPalabra.stop()
//            escucharPalabra.shutdown()
//        }
//        super.onDestroy()
//    }
//
//    private fun eliminar(nombre: String) {
//        //val index = listaDeArchivos.indexOfFirst { it.first == nombre }
//        val index = listaDeArchivos.indexOfFirst { it.nombre == nombre }
//        if (index != -1) {
//            val (_, uri, _) = listaDeArchivos[index]
//            try {
//                when (uri.scheme) {
//                    "content" -> {
//                        // Manejo de URI en MediaStore
//                        val rowsDeleted = contentResolver.delete(uri, null, null)
//                        if (rowsDeleted > 0) {
//                            Log.d("Eliminar", "Eliminado de MediaStore: $nombre")
//                        } else {
//                            Toast.makeText(this, "No se pudo eliminar el archivo de MediaStore: $nombre", Toast.LENGTH_SHORT).show()
//                        }
//                    }
//                    "file" -> {
//                        // Manejo de URI local
//                        val file = File(uri.path ?: "")
//                        if (file.exists()) {
//                            if (file.delete()) { Log.d("Eliminar", "Archivo local eliminado: $nombre")
//                            } else { Toast.makeText(this, "No se pudo eliminar el archivo local: $nombre", Toast.LENGTH_SHORT).show()
//                            }
//                        } else { Toast.makeText(this, "El archivo local no existe: $nombre", Toast.LENGTH_SHORT).show()
//                        }
//                    }
//                    else -> { Toast.makeText(this, "URI desconocida: $nombre", Toast.LENGTH_SHORT).show()
//                    }
//                }
//                listaDeArchivos.removeAt(index) // Elimina el elemento de la lista
//                mediaAdapter.notifyItemRemoved(index) // Notifica al adaptador sobre el cambio
//                eliminarDatosDeMedia(nombre) // Actualiza el almacenamiento persistente
//
//                Toast.makeText(this, "Eliminado: $nombre", Toast.LENGTH_SHORT).show()
//            } catch (e: Exception) { Toast.makeText(this, "Error al eliminar el archivo: $nombre", Toast.LENGTH_SHORT).show()
//                e.printStackTrace()
//            }
//        } else {
//            Toast.makeText(this, "No se encontró el elemento: $nombre", Toast.LENGTH_SHORT).show()
//        }
//    }
//
//    private fun eliminarDatosDeMedia(nombreArchivo: String) {
//        val sharedPreferences = getSharedPreferences("media_data", MODE_PRIVATE)
//        val editor = sharedPreferences.edit()
//        editor.remove(nombreArchivo)
//        editor.remove("$nombreArchivo|type")
//        editor.apply()
//    }
//
//    /*
//    private fun loadImageData() {
//    if (driveServiceHelper == null) {
//    Log.e("DriveService", "driveServiceHelper no está inicializado")
//    return
//    }
//
//    val sharedPreferences = getSharedPreferences("media_data", MODE_PRIVATE)
//    sharedPreferences.all.forEach { (key, value) ->
//    if (key.contains("|type")) return@forEach // Saltar las entradas que son de tipo
//
//    val uriString = value as? String ?: return@forEach // Asegúrate de que el valor sea una cadena
//    val uri = Uri.parse(uriString)
//    val esImagen = sharedPreferences.getBoolean(
//    "$key|type",
//    true
//    ) // Recupera el tipo, valor por defecto es true que corresponde a imagen
//
//    listaDeArchivos.add(ItemLista(key, uri, esImagen, System.currentTimeMillis()))
//    //listaDeArchivos.add(Triple(key, uri, esImagen)) // Usa Triple en lugar de Pair
//    mediaAdapter.notifyItemInserted(listaDeArchivos.size - 1) // Notificar que se ha añadido un elemento
//
//    // Verificar si el archivo ya está en Google Drive
//    checkAndUploadToDrive(uri, key, esImagen)
//
//    Log.d(
//    "LoadImageData",
//    "Cargado ${if (esImagen) "image" else "video"} data: $key -> $uri"
//    )
//    }
//    }
//    SI USO DOS CARPETAS DISTINTAS
//    private fun loadImageData() {
//    listaDeArchivos.clear() // Limpia la lista antes de cargar nuevos datos
//
//    // Directorios internos para imágenes y videos
//    val imagenesDir = File(filesDir, "imagenes")
//    val videosDir = File(filesDir, "videos")
//
//    // Cargar imágenes
//    if (imagenesDir.exists()) {
//    imagenesDir.listFiles()?.forEach { file ->
//    listaDeArchivos.add(
//    ItemLista(
//    nombre = file.name,
//    uri = Uri.fromFile(file),
//    esImagen = true,
//    timestamp = file.lastModified()
//    )
//    )
//    }
//    }
//
//    // Cargar videos
//    if (videosDir.exists()) {
//    videosDir.listFiles()?.forEach { file ->
//    listaDeArchivos.add(
//    ItemLista(
//    nombre = file.name,
//    uri = Uri.fromFile(file),
//    esImagen = false,
//    timestamp = file.lastModified()
//    )
//    )
//    }
//    }
//
//    // Ordenar por timestamp (más reciente primero)
//    listaDeArchivos.sortByDescending { it.timestamp }
//
//    // Notificar al adaptador
//    mediaAdapter.notifyDataSetChanged()
//    }
//    */
//
//    // si uso una carpeta del almacenamiento interno para imagenes y videos
//    private fun loadImageData() {
//        listaDeArchivos.clear() // Limpia la lista antes de cargar nuevos datos
//
//        val mediaDir = File(filesDir, "media")         // Directorio único para imágenes y videos
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
//
//    private fun solicitarContrasena() {
//        val builder = AlertDialog.Builder(this)
//        builder.setTitle("Ingrese la contraseña")
//
//        val input = EditText(this)
//        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
//        builder.setView(input)
//
//        builder.setPositiveButton("Aceptar") { _, _ ->
//            val passwordIngresada = input.text.toString()
//            if (passwordIngresada == "1234") { // Reemplaza con la contraseña correcta
//                activarModoEliminacion()
//            } else {
//                Toast.makeText(this, "Contraseña incorrecta", Toast.LENGTH_SHORT).show()
//            }
//        }
//        builder.setNegativeButton("Cancelar") { dialog, _ -> dialog.cancel() }
//        builder.show()
//    }
//
//    private var modoEliminacionActivo = false
//    private fun activarModoEliminacion() {
//        modoEliminacionActivo = true
//        mediaAdapter.setModoEliminacion(true)
//        Toast.makeText(this, "Modo eliminación activado", Toast.LENGTH_SHORT).show()
//    }
//
//    fun eliminarSeleccionadosDesdeAdapter(nombres: List<String>) {
//        for (nombre in nombres) {
//            eliminar(nombre) // asumimos que tenés una función que elimina el archivo por nombre
//        }
//        Toast.makeText(this, "Elementos eliminados", Toast.LENGTH_SHORT).show()
//        mediaAdapter.notifyDataSetChanged()
//    }
//
//    override fun onEliminarSeleccionSolicitada(seleccionados: List<MainActivity.ItemLista>) {
//        AlertDialog.Builder(this)
//            .setTitle("¿Eliminar elementos seleccionados?")
//            .setMessage("Se eliminarán ${seleccionados.size} elementos. ¿Desea continuar?")
//            .setPositiveButton("Eliminar") { _, _ ->
//                eliminarElementosSeleccionados(seleccionados)
//            }
//            .setNegativeButton("Cancelar") { _, _ ->
//                cancelarModoEliminacion()
//            }
//            .show()
//    }
//    private fun eliminarElementosSeleccionados(lista: List<MainActivity.ItemLista>) {
//        for (item in lista) {
//            val archivo = File(filesDir, item.nombre)
//            if (archivo.exists()) {
//                archivo.delete()
//            }
//        }
////        cargarArchivosDesdeDirectorio() // O actualizá la lista del RecyclerView
//        cancelarModoEliminacion()
//    }
//
//    private fun cancelarModoEliminacion() {
//        modoEliminacionActivo = false
//        mediaAdapter.setModoEliminacion(false)
//        Toast.makeText(this, "Modo eliminación cancelado", Toast.LENGTH_SHORT).show()
//    }
//
//
//
//
//
//    /*private fun signInToGoogle() {
//        if (!::googleSignInClient.isInitialized) {
//            googleSignInClient = GoogleSignIn.getClient(
//                this, GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
//                    .requestEmail()
//                    .requestScopes(Scope(DriveScopes.DRIVE_FILE)) // Permiso para Google Drive
//                    .build()
//            )
//        }
//        val signInIntent = googleSignInClient.signInIntent
//        startActivityForResult(signInIntent, REQUEST_CODE_SIGN_IN)
//    }
//
//    private fun getGoogleDriveService(googleAccount: GoogleSignInAccount): Drive {
//        val credential = GoogleAccountCredential.usingOAuth2(
//            applicationContext, listOf(DriveScopes.DRIVE_FILE)
//        )
//        credential.selectedAccount = googleAccount.account
//
//        return Drive.Builder(
//            NetHttpTransport(),
//            GsonFactory.getDefaultInstance(),
//            credential
//        ).setApplicationName("MiApp").build()
//    }
//
//    private fun signOutAndSelectAccount() {
//        // Cerrar sesión de la cuenta actual
//        googleSignInClient.signOut()
//            .addOnCompleteListener(this) {
//                // Al cerrarse sesión, permitir elegir cuenta nuevamente
//                signInToGoogle()
//            }
//    }
//
//    private fun checkAndUploadToDrive(uri: Uri, fileName: String, esImagen: Boolean) {
//        // Ejecutar la verificación y subida en un hilo en segundo plano
//        val executorService = Executors.newSingleThreadExecutor()
//        executorService.execute {
//            try {
//                // Busca el archivo en Google Drive utilizando el nombre del archivo
//                val query = "name = '$fileName' and trashed = false"
//                val request = driveServiceHelper.driveService.files()?.list()
//                    ?.setQ(query)
//                    ?.setSpaces("drive")
//                    ?.setFields("files(id, name)")
//
//                request?.execute()?.files?.let { files ->
//                    if (files.isEmpty()) {
//                        // Si no está en Google Drive, lo subimos
//                        val mimeType = if (esImagen) "image/jpeg" else "video/mp4"
//                        driveServiceHelper.uploadFile(uri, fileName, mimeType)
//                            ?.addOnSuccessListener { fileId ->
//                                Log.d("GoogleDrive", "Archivo subido con éxito. ID: $fileId")
//                            }
//                            ?.addOnFailureListener { e ->
//                                Log.e("GoogleDrive", "Error al subir archivo: ${e.message}")
//                            }
//                    } else {
//                        // Si ya existe en Google Drive, no lo subimos
//                        Log.d("GoogleDrive", "El archivo ya existe en Google Drive: $fileName")
//                    }
//                }
//            } catch (e: Exception) {
//                Log.e("GoogleDrive", "Error al verificar o subir el archivo: ${e.message}")
//            }
//        }
//    }
//*/
//
////    fun getMediaAdapter(): MediaAdapter {
////        return mediaAdapter
////    }
//
//    fun abrirSelectorArchivos() {
//        val intent = Intent(Intent.ACTION_GET_CONTENT)
//        intent.type = "*/*" // Permitir seleccionar cualquier tipo de archivo
//        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true) // Permitir selección múltiple
//
//        startActivityForResult(intent, SELECT_FILES_REQUEST_CODE )
//    }
//
//    fun comprimirArchivos(files: List<Uri>, destinationPath: String) {
//        try {
//            val zipFile = ZipFile(destinationPath)
//
//            // Convertir URIs a archivos y añadirlos al archivo ZIP
//            for (uri in files) {
//                val file = File(getRealPathFromURI(uri)) // Obtener el archivo real desde la URI
//                zipFile.addFile(file)
//            }
//
//            // Si todo fue bien, el archivo ZIP está creado
//            Toast.makeText(this, "Archivos comprimidos exitosamente", Toast.LENGTH_SHORT).show()
//
//        } catch (e: Exception) {
//            e.printStackTrace()
//            Toast.makeText(this, "Error al comprimir los archivos", Toast.LENGTH_SHORT).show()
//        }
//    }
//
//    fun getRealPathFromURI(uri: Uri): String {
//        val cursor = contentResolver.query(uri, null, null, null, null)
//        cursor?.moveToFirst()
//        val columnIndex = cursor?.getColumnIndex(android.provider.MediaStore.Images.Media.DATA)
//        return cursor?.getString(columnIndex!!) ?: ""
//    }
//
//    fun compartirArchivo(archivo: File) {
//        val uri = FileProvider.getUriForFile(
//            this,
//            "com.comunic.fileprovider", // Asegúrate de configurar correctamente el FileProvider
//            archivo
//        )
//        val intent = Intent(Intent.ACTION_SEND)
//        intent.type = "application/zip"
//        intent.putExtra(Intent.EXTRA_STREAM, uri)
//        startActivity(Intent.createChooser(intent, "Compartir archivo"))
//    }
//
//    // EXPORTAR
////    private fun mostrarDialogoSeleccionarElementos() {
////        val nombres = listaDeArchivos.map { it.nombre }.toTypedArray()
////        val seleccionados = BooleanArray(nombres.size)
////
////        AlertDialog.Builder(this)
////            .setTitle("Selecciona elementos para exportar")
////            .setMultiChoiceItems(nombres, seleccionados) { _, which, isChecked ->
////                seleccionados[which] = isChecked
////            }
////            .setPositiveButton("Continuar") { _, _ ->
////                val elementosSeleccionados = listaDeArchivos.filterIndexed { index, _ -> seleccionados[index] }
////                if (elementosSeleccionados.isEmpty()) {
////                    Toast.makeText(this, "No seleccionaste ningún elemento", Toast.LENGTH_SHORT).show()
////                } else {
////                    mostrarResumenSeleccion(elementosSeleccionados)
////                }
////            }
////            .setNegativeButton("Cancelar", null)
////            .show()
////    }
//    private fun mostrarDialogoSeleccionarElementos() {
//        val nombres = listaDeArchivos.map { it.nombre }
//        val seleccionados = BooleanArray(nombres.size)
//
//        val dialogView = layoutInflater.inflate(R.layout.dialogo_seleccion, null)
//        val listView = dialogView.findViewById<ListView>(R.id.listaItems)
//        val btnSeleccionarTodos = dialogView.findViewById<Button>(R.id.btnSeleccionarTodos)
//        val btnDeseleccionarTodos = dialogView.findViewById<Button>(R.id.btnDeseleccionarTodos)
//
//        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_multiple_choice, nombres)
//        listView.adapter = adapter
//
//        listView.setOnItemClickListener { _, _, position, _ ->
//            seleccionados[position] = listView.isItemChecked(position)
//        }
//
//        btnSeleccionarTodos.setOnClickListener {
//            for (i in nombres.indices) {
//                listView.setItemChecked(i, true)
//                seleccionados[i] = true
//            }
//        }
//
//        btnDeseleccionarTodos.setOnClickListener {
//            for (i in nombres.indices) {
//                listView.setItemChecked(i, false)
//                seleccionados[i] = false
//            }
//        }
//
//        AlertDialog.Builder(this)
//            .setTitle("Selecciona elementos para exportar")
//            .setView(dialogView)
//            .setPositiveButton("Continuar") { _, _ ->
//                val elementosSeleccionados = listaDeArchivos.filterIndexed { index, _ -> seleccionados[index] }
//                if (elementosSeleccionados.isEmpty()) {
//                    Toast.makeText(this, "No seleccionaste ningún elemento", Toast.LENGTH_SHORT).show()
//                } else {
//                    mostrarResumenSeleccion(elementosSeleccionados)
//                }
//            }
//            .setNegativeButton("Cancelar", null)
//            .show()
//    }
//
//
//    private fun mostrarResumenSeleccion(elementosSeleccionados: List<ItemLista>) {
//        val cantidad = elementosSeleccionados.size
//        val nombres = elementosSeleccionados.joinToString("\n") { "- ${it.nombre}" }
//
//        AlertDialog.Builder(this)
//            .setTitle("Resumen de selección")
//            .setMessage("Seleccionaste $cantidad elementos:\n\n$nombres")
//            .setPositiveButton("Exportar") { _, _ ->
//                mostrarDialogoTipoExportacion(elementosSeleccionados)
//            }
//            .setNegativeButton("Cancelar", null)
//            .show()
//    }
//
//
//    private fun mostrarDialogoTipoExportacion(elementosSeleccionados: List<ItemLista>) {
//        AlertDialog.Builder(this)
//            .setTitle("¿Cómo querés exportarlos?")
//            .setItems(arrayOf("Compartir archivos sueltos", "Exportar como ZIP")) { _, which ->
//                when (which) {
//                    0 -> exportarElementos(elementosSeleccionados) // Sueltos
//                    1 -> exportarElementosComoZip(elementosSeleccionados) // Como ZIP
//                }
//            }
//            .show()
//    }
//
//    private fun exportarElementos(elementos: List<ItemLista>) {
//        if (elementos.isEmpty()) {
//            Toast.makeText(this, "No seleccionaste elementos", Toast.LENGTH_SHORT).show()
//            return
//        }
//
//        val uris = ArrayList<Uri>()
//        for (item in elementos) {
//            val uri = if (item.uri.scheme == "content") {
//                // Ya es un content://, lo usamos directamente
//                item.uri
//            } else {
//                // Es un file://, lo pasamos por FileProvider
//                val file = File(item.uri.path!!)
//                FileProvider.getUriForFile(this, "com.comunic.fileprovider", file)
//            }
//            uris.add(uri)
//        }
//
//        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
//            type = "*/*"
//            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
//            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
//        }
//        startActivity(Intent.createChooser(intent, "Compartir archivos"))
//    }
//
//    private fun exportarElementosComoZip(elementos: List<ItemLista>) {
//        if (elementos.isEmpty()) {
//            Toast.makeText(this, "No seleccionaste elementos", Toast.LENGTH_SHORT).show()
//            return
//        }
//
//        val zipFile = File(cacheDir, "archivos_exportados.zip")
//
//        try {
//            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
//                for (item in elementos) {
//                    val inputStream = contentResolver.openInputStream(item.uri) ?: continue
//
//                    // obtener mime y extension
//                    val mimeType = contentResolver.getType(item.uri)
//                    var extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
//
//                    // fallback: intenta extraerla de la URL si no se pudo obtener desde el MIME
//                    if (extension == null) {
//                        extension = MimeTypeMap.getFileExtensionFromUrl(item.uri.toString())
//                    }
//
//                    // añade extensión si no está
//                    val fileName = if (extension != null && !item.nombre.endsWith(".$extension")) {
//                        "${item.nombre}.$extension"
//                    } else {
//                        item.nombre
//                    }
//
//
//                    val entry = ZipEntry(fileName)
//                    zos.putNextEntry(entry)
//
//                    inputStream.copyTo(zos)
//
//                    zos.closeEntry()
//                    inputStream.close()
//                }
//            }
//
//            val uriZip = FileProvider.getUriForFile(this, "com.comunic.fileprovider", zipFile)
//
//            val intent = Intent(Intent.ACTION_SEND).apply {
//                type = "application/zip"
//                putExtra(Intent.EXTRA_STREAM, uriZip)
//                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
//            }
//
//            startActivity(Intent.createChooser(intent, "Compartir ZIP"))
//        } catch (e: Exception) {
//            e.printStackTrace()
//            Toast.makeText(this, "Error al crear ZIP", Toast.LENGTH_SHORT).show()
//        }
//    }
//
//    // IMPORTAR PAQUETES
//    private fun importarArchivos() {
//        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
//            type = "application/zip"
//            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
//        }
//        startActivityForResult(intent, REQUEST_CODE_IMPORTAR_ZIP)
//    }
//
//    private fun importarElementosDesdeZip(uri: Uri) {
//        try {
//            // Abrir el archivo ZIP
//            val inputStream = contentResolver.openInputStream(uri) ?: return
//            val zipInputStream = ZipInputStream(BufferedInputStream(inputStream))
//
//            val mediaDir = File(filesDir, "media") // Carpeta "media" en el almacenamiento interno
//            if (!mediaDir.exists()) {
//                mediaDir.mkdirs() // Crear la carpeta si no existe
//            }
//
//            var entry: ZipEntry?
//            while (zipInputStream.nextEntry.also { entry = it } != null) {
//                val extension = entry!!.name.substringAfterLast(".", "").lowercase(Locale.ROOT) // valido extension de archivo
//                val esImagen = when (extension) {
//                    in listOf("jpg", "jpeg", "png", "gif", "bmp", "webp") -> true
//                    in listOf("mp4", "mkv", "avi", "mov", "webm") -> false
//                    else -> {
//                        Toast.makeText(this, "Archivo no soportado: $extension", Toast.LENGTH_SHORT).show()
//                        continue
//                    }
//                }
//
//                val nombreSinExtension = entry!!.name.substringBeforeLast(".")
//                val nombreFinal = "$nombreSinExtension.${if (esImagen) "jpg" else "mp4"}" // le doy la extension final, el nombre es el q levanta sin la extension
//
//                val archivoDestino = File(mediaDir, nombreFinal)
//
//                if (archivoDestino.exists()) { // para evitar sobreescribir archivos
//                    Toast.makeText(this, "Ya existe un archivo llamado $nombreSinExtension", Toast.LENGTH_SHORT).show()
//                    continue
//                }
//                // extraigo zip y lo guardo en la carpeta media (archivoDestino me lleva a mediaDir)
//                val outputStream = FileOutputStream(archivoDestino)
//                zipInputStream.copyTo(outputStream)
//                zipInputStream.closeEntry()
//                outputStream.close()
//
//                val uriGuardado = Uri.fromFile(archivoDestino)
//                val item = ItemLista(
//                    nombre = nombreSinExtension,
//                    uri = uriGuardado,
//                    esImagen = esImagen,
//                    timestamp = System.currentTimeMillis()
//                )
//
//                listaDeArchivos.add(item) // agrego archivos a la lista actual
//                saveMediaData(nombreSinExtension, uriGuardado, esImagen) // guardo en el almacenamiento persistente (sharedPreferences)
//            }
//
//            zipInputStream.close() // cierro zip y aviso que se importo bien
//            Toast.makeText(this, "Importación exitosa", Toast.LENGTH_SHORT).show()
//            mediaAdapter.notifyDataSetChanged()
//        } catch (e: Exception) {
//            e.printStackTrace()
//            Toast.makeText(this, "Error al importar ZIP", Toast.LENGTH_SHORT).show()
//        }
//    }
//
//
//
//
//    companion object {
//        private const val PERMISSION_REQUEST_CODE = 123
//        private const val PICK_IMAGE_REQUEST = 124
//        private const val PICK_MEDIA_REQUEST = 125
//        private const val CAPTURE_IMAGE_REQUEST = 126
//        private const val CAPTURE_VIDEO_REQUEST = 127
//        private const val REQUEST_CODE_SIGN_IN = 128
//        private const val SELECT_FILES_REQUEST_CODE = 129
//        private const val REQUEST_CODE_IMPORTAR_ZIP = 130
//        private const val REQUEST_CODE_PICK_MEDIA = 131
//        private val UCROP_REQUEST_CODE = 69
//
//    }*/
}

//import com.comunic.MediaAdapterProvider



//interface MediaAdapterProvider {


         */