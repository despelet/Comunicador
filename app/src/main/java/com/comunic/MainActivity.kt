package com.comunic

//import android.os.Build.VERSION_CODES.R
import android.os.Bundle
import android.view.MenuItem
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.comunic.data.db.AppDatabase
import com.comunic.fragment.HomeFragment
import com.comunic.fragment.Listas
import com.comunic.fragment.Recientes
import com.comunic.interfaces.MediaResultListener
import com.comunic.interfaces.RecientesProvider
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.launch
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.comunic.data.entity.CategoryItemEntity
import com.comunic.fragment.HomeFragment.Companion.CAPTURE_IMAGE_REQUEST
import com.comunic.fragment.HomeFragment.Companion.CAPTURE_VIDEO_REQUEST
import com.comunic.fragment.HomeFragment.Companion.PICK_MEDIA_REQUEST
import com.comunic.fragment.HomeFragment.Companion.UCROP_REQUEST_CODE
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.squareup.picasso.Picasso
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.comunic.interfaces.ZipImportListener


class MainActivity : AppCompatActivity(),  NavigationView.OnNavigationItemSelectedListener {


    private lateinit var drawerLayout: DrawerLayout     // para el menu lateral
    private lateinit var navigationView: NavigationView
    private lateinit var bottomNav: BottomNavigationView

    private var lastCapturedUri: Uri? = null

    private var mediaResultListener: MediaResultListener? = null
    private var zipImportListener: ZipImportListener? = null
    var pendingCategoryId: String? = null



    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

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
//        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.START)
//        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.END)
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.END)
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.START)

        // BARRA INFERIOR
        bottomNav = findViewById(R.id.bottom_nav)


        val menuButton: ImageButton = findViewById(R.id.menu)
        menuButton.setOnClickListener {                     // Abrir el menú lateral al presionar el botón
            drawerLayout.openDrawer(GravityCompat.START)        }

        // Listener para la barra inferior
        setupBottomNav()

        // Cargar fragment por defecto
        if (savedInstanceState == null) {
            bottomNav.selectedItemId = R.id.menu_recientes
        }

        // -------- BOTTOM BAR --------
        bottomNav.menu.findItem(R.id.menu_sugeridos)?.isVisible = false
        bottomNav.labelVisibilityMode =
            com.google.android.material.navigation.NavigationBarView.LABEL_VISIBILITY_SELECTED

        // -------- MENU LATERAL --------
        val drawerMenu = navigationView.menu

        drawerMenu.findItem(R.id.nav_proteger)?.isVisible = false
        drawerMenu.findItem(R.id.nav_editar)?.isVisible = false
        drawerMenu.findItem(R.id.nav_nosotros)?.isVisible = false

    }

    private fun openFragment(fragment: Fragment) {
        val tag = when (fragment) {
            is Recientes -> "RECENTES_TAG"
            else -> fragment::class.java.simpleName
        }

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment, tag)
            .commit()
    }

    private fun navigateTo(menuId: Int, fragment: Fragment) {
        openFragment(fragment)

        bottomNav.setOnItemSelectedListener(null)
        bottomNav.selectedItemId = menuId
        setupBottomNav()
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

        menuItem.isChecked = false // desmarcar el ítem seleccionado
        //drawerLayout.closeDrawer(GravityCompat.END) // Cierra el menú después de seleccionar un ítem
        drawerLayout.closeDrawer(GravityCompat.START) // Cierra el menú después de seleccionar un ítem desde izq
        return false
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

    private fun setupBottomNav() {
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
    }

    fun irARecientesDesdeHome() {
        navigateTo(R.id.menu_recientes, Recientes())
    }

    fun irAListasDesdeHome() {
        navigateTo(R.id.menu_listas, Listas())
    }

    fun irASugeridosDesdeHome() {
        navigateTo(R.id.menu_sugeridos, Sugeridos())
    }

    fun irAHomeDesdeHome() {
        navigateTo(R.id.menu_home, HomeFragment())
    }

    fun pedirAgregarAListaDesdeCuadro(item: ItemLista) {
        val current = supportFragmentManager.findFragmentById(R.id.fragment_container)

        val host = when {
            current is AddToListHost -> current
            // si usás NavHostFragment, descomentá esto:
            // current is NavHostFragment -> current.childFragmentManager.fragments.firstOrNull { it is AddToListHost } as? AddToListHost
            else -> null
        }

        if (host != null) {
            host.mostrarDialogoAgregarAListas(item)
        } else {
            Toast.makeText(this, "Esta pantalla no soporta 'Agregar a lista'", Toast.LENGTH_SHORT).show()
        }
    }

    fun getRecientesFragment(): RecientesProvider? {
        val fragment = supportFragmentManager.findFragmentByTag("RECENTES_TAG")
        return fragment as? RecientesProvider
    }

    fun setMediaResultListener(listener: MediaResultListener?) {
        mediaResultListener = listener
    }
    fun setZipImportListener(listener: ZipImportListener?) {
        zipImportListener = listener
    }

    fun launchImageCapture() {
        val photoUri = createImageUri()
        lastCapturedUri = photoUri

        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)

        startActivityForResult(intent, CAPTURE_IMAGE_REQUEST)
    }
    fun launchVideoCapture() {
        val videoUri = createVideoUri()
        lastCapturedUri = videoUri

        val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
        intent.putExtra(MediaStore.EXTRA_OUTPUT, videoUri)

        startActivityForResult(intent, CAPTURE_VIDEO_REQUEST)
    }
    fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK).apply {
            type = "image/* video/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
        }
        startActivityForResult(intent, PICK_MEDIA_REQUEST)
    }
    private fun createImageUri(): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Comunic")
        }
        return contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
    }

    private fun createVideoUri(): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Comunic")
        }
        return contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)!!
    }
    private fun startCrop(uri: Uri) {
        val destinationUri = Uri.fromFile(
            File(cacheDir, "imagen_editada_${System.currentTimeMillis()}.jpg")
        )

        val options = UCrop.Options().apply {
            setCompressionFormat(Bitmap.CompressFormat.JPEG)
            setCompressionQuality(90)
            setFreeStyleCropEnabled(true)

            setToolbarColor(ContextCompat.getColor(this@MainActivity, R.color.color5))
            setStatusBarColor(ContextCompat.getColor(this@MainActivity, R.color.color5))
            setToolbarWidgetColor(ContextCompat.getColor(this@MainActivity, R.color.color1))
            setActiveControlsWidgetColor(ContextCompat.getColor(this@MainActivity, R.color.color1))
            setRootViewBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.color5))
        }

        UCrop.of(uri, destinationUri)
            .withOptions(options)
            .withAspectRatio(1f, 1f)
            .start(this)
    }
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {

        super.onActivityResult(requestCode, resultCode, data)

        // =========================
        // IMPORTAR ZIP
        // =========================
        if (
            requestCode == HomeFragment.REQUEST_CODE_IMPORTAR_ZIP &&
            resultCode == RESULT_OK
        ) {

            val zipUri = data?.data

            Log.d("ZIP_IMPORT", "ZIP seleccionado: $zipUri")

            zipUri?.let {
                zipImportListener?.importarElementosDesdeZip(it)
            }

            return
        }

        // =========================
        // UCROP
        // =========================
        if (requestCode == UCROP_REQUEST_CODE) {

            if (resultCode == RESULT_OK) {

                val resultUri = UCrop.getOutput(data!!)

                resultUri?.let {
                    ingresarNombreArchivo(it, true)
                }

            } else {

                lastCapturedUri?.let {
                    ingresarNombreArchivo(it, true)
                }
            }

            return
        }

        // =========================
        // GALERIA / CAMARA
        // =========================
        if (resultCode == RESULT_OK) {

            val mediaUri = when (requestCode) {

                PICK_MEDIA_REQUEST -> data?.data

                CAPTURE_IMAGE_REQUEST -> lastCapturedUri

                CAPTURE_VIDEO_REQUEST -> lastCapturedUri

                else -> null
            }

            mediaUri?.let {

                val mime = contentResolver.getType(it)

                if (mime?.startsWith("image/") == true) {

                    startCrop(it)

                } else if (mime?.startsWith("video/") == true) {

                    ingresarNombreArchivo(it, false)
                }
            }
        }
    }
//    private fun ingresarNombreArchivo(uri: Uri, esImagen: Boolean) {
//
//        val dialogView = layoutInflater.inflate(R.layout.dialog_image_name, null)
//        val input = dialogView.findViewById<EditText>(R.id.nameEditText)
//        Log.d("TEST", "Se llamó ingresarNombreArchivo")
//
//        AlertDialog.Builder(this)
//            .setTitle(if (esImagen) "Sonido de la imagen" else "Sonido del video")
//            .setView(dialogView)
//            .setPositiveButton("OK") { _, _ ->
//
//                val nombre = input.text.toString().trim()
//                if (nombre.isEmpty()) {
//                    Toast.makeText(this, "Nombre vacío", Toast.LENGTH_SHORT).show()
//                    return@setPositiveButton
//                }
//
//                guardarArchivo(uri, nombre, esImagen)
//            }
//            .setNegativeButton("Cancelar", null)
//            .show()
//    }
    private fun ingresarNombreArchivo(mediaUri: Uri, isImage: Boolean) {

        Log.d("DIALOG_FLOW", "Mostrando dialog desde MainActivity")

        val view = layoutInflater.inflate(R.layout.dialog_ingresar_sonido, null)

        val imagePreview = view.findViewById<ImageView>(R.id.imagePreview)
        val editNombre = view.findViewById<EditText>(R.id.editNombre)
        val btnGuardar = view.findViewById<MaterialButton>(R.id.btnGuardar)
        val btnCancelar = view.findViewById<MaterialButton>(R.id.btnCancelar)

        // Preview
        Picasso.get().load(mediaUri).into(imagePreview)

        val dialog = AlertDialog.Builder(
            this,
            R.style.ThemeOverlay_Comunic_AlertDialog
        )
            .setView(view)
            .create()

        btnGuardar.setOnClickListener {
            val nombre = editNombre.text.toString().trim()

            if (nombre.isNotEmpty()) {

                guardarArchivo(mediaUri, nombre, isImage)
                dialog.dismiss()

                mostrarSnackbar(
                    if (isImage) "Tu imagen se guardó con éxito" else "Tu video se guardó con éxito",
                    true
                )

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

        dialog.setCancelable(false)
    }
    private fun mostrarSnackbar(mensaje: String, esExito: Boolean) {

        val rootView = findViewById<View>(android.R.id.content)

        val snackbar = Snackbar
            .make(rootView, mensaje, Snackbar.LENGTH_SHORT)

        val color = if (esExito) {
            ContextCompat.getColor(this, R.color.color_success)
        } else {
            ContextCompat.getColor(this, com.google.android.material.R.color.design_default_color_error)
        }

        snackbar.setBackgroundTint(color)
        snackbar.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        snackbar.show()
    }


    private fun guardarArchivo(uri: Uri, nombre: String, esImagen: Boolean) {

        val savedUri = guardarEnAlmacenamientoInterno(uri, nombre, esImagen)
            ?: return

        val item = ItemLista(
            id = ItemKey.media(nombre),
            nombre = nombre,
            uri = savedUri,
            esImagen = esImagen,
            timestamp = System.currentTimeMillis()
        )

        saveMediaData(nombre, savedUri, esImagen)

        //mediaResultListener?.onMediaCreated(item)
        val currentFragment =
            supportFragmentManager.findFragmentById(
                R.id.fragment_container
            )
        if (currentFragment is MediaResultListener) {
            Log.d( "MEDIA_NOTIFY","Notificando a: ${currentFragment.javaClass.simpleName}"
            )
            currentFragment.onMediaCreated(item)
        } else {
            Log.e("MEDIA_NOTIFY", "Fragment actual no implementa MediaResultListener"
            )
        }

        pendingCategoryId?.let { catId ->
            lifecycleScope.launch(Dispatchers.IO) {
                val dao = AppDatabase.getDatabase(applicationContext).categoryDao()

                val next = dao.getMaxOrderIndex(catId) + 1

                dao.insertCategoryItem(
                    CategoryItemEntity(
                        placementId = UUID.randomUUID().toString(),
                        categoryId = catId,
                        itemKey = item.id,
                        orderIndex = next
                    )
                )
            }
        }
        pendingCategoryId = null

        Toast.makeText(this, "Guardado: $nombre", Toast.LENGTH_SHORT).show()
    }


    fun guardarEnAlmacenamientoInterno(
        mediaUri: Uri,
        nombre: String,
        esImagen: Boolean
    ): Uri? {

        val dir = File(filesDir, "media")
        if (!dir.exists()) dir.mkdirs()

        val ext = if (esImagen) "jpg" else "mp4"
        val file = File(dir, "$nombre.$ext")

        return try {
            contentResolver.openInputStream(mediaUri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            Uri.fromFile(file)
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }
    fun saveMediaData(nombre: String, uri: Uri, isImage: Boolean) {
        val prefs = getSharedPreferences("media_data", MODE_PRIVATE)
        prefs.edit()
            .putString(nombre, uri.toString())
            .putBoolean("$nombre|type", isImage)
            .apply()
    }


}