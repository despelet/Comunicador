package com.comunic.fragment

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
import com.comunic.interfaces.MediaResultListener
import com.comunic.interfaces.RecientesProvider
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.launch
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.drawable.ColorDrawable
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.RequiresApi
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
import com.bumptech.glide.Glide
import com.comunic.AddToListHost
import com.comunic.ItemKey
import com.comunic.ItemLista
import com.comunic.R
import com.comunic.Sugeridos
import com.comunic.auth.AuthSessionManager
import com.comunic.data.entity.MediaEntity
import com.comunic.dialog.SyncProgressDialog
import com.comunic.interfaces.DrawerMenuConfig
import com.comunic.interfaces.ZipImportListener
import com.comunic.migration.ImageOptimizationMigration
import com.comunic.migration.LegacyMediaKeyMigrationRepository
import com.comunic.session.PermissionManager
import com.comunic.session.RoleAwareFragment
import com.comunic.session.SessionManager
import com.comunic.sync.CloudSyncRepository
import com.comunic.sync.SyncEvents
import com.comunic.utils.ImageCompressor
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.withContext
import com.google.android.material.textfield.TextInputEditText


class MainActivity : AppCompatActivity(),  NavigationView.OnNavigationItemSelectedListener {


    private lateinit var drawerLayout: DrawerLayout     // para el menu lateral
    private lateinit var navigationView: NavigationView
    private lateinit var bottomNav: BottomNavigationView

    private var lastCapturedUri: Uri? = null

    private var mediaResultListener: MediaResultListener? = null
    private var zipImportListener: ZipImportListener? = null
    var pendingCategoryId: String? = null

    // sessions
    private lateinit var sessionManager: SessionManager
    private lateinit var permissionManager: PermissionManager
    private val tutorTimeoutHandler = Handler(Looper.getMainLooper())
    private val tutorTimeoutRunnable = Runnable {
        checkTutorTimeout()
    }

    private var lastPreviewUri: Uri? = null
    private var pendingNombreTexto: String = ""


    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        val userId =
            SessionManager(this@MainActivity)
                .getCurrentUserId()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        lifecycleScope.launch {
            val dao = AppDatabase.getDatabase(applicationContext).itemUsadoDao()
            dao.getAllItemsUsados(userId) // o cualquier consulta mínima
        }

        // MENU LATERAL
        drawerLayout = findViewById(R.id.drawer_layout) // inicializacion del drawer layout y navigation view
        navigationView = findViewById(R.id.navigation_view) // inicializacion del drawer layout y navigation view
        navigationView.setNavigationItemSelectedListener(this) // Configurar NavigationView y su listener
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.END)
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.START)

        // BARRA INFERIOR
        bottomNav = findViewById(R.id.bottom_nav)

        // SESSIONS
        sessionManager = SessionManager(this)
        permissionManager = PermissionManager(sessionManager)
        AuthSessionManager(this).restoreFirebaseSessionIfExists()
        sessionManager.deactivateTutorMode()
        runStartupMigrations()
        actualizarBloqueCuentaMenu()
        actualizarSaludoCuenta()

        lifecycleScope.launch {
            SyncEvents.dataChanged.collect {
                refrescarEstadoSesion()
            }
        }


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
        //bottomNav.menu.findItem(R.id.menu_sugeridos)?.isVisible = false
        bottomNav.labelVisibilityMode =
            com.google.android.material.navigation.NavigationBarView.LABEL_VISIBILITY_SELECTED

        // -------- MENU LATERAL --------
        val drawerMenu = navigationView.menu

//        drawerMenu.findItem(R.id.nav_proteger)?.isVisible = false
//        drawerMenu.findItem(R.id.nav_editar)?.isVisible = false
//        drawerMenu.findItem(R.id.nav_nosotros)?.isVisible = false


        drawerLayout.post {
            val current =
                supportFragmentManager.findFragmentById(
                    R.id.fragment_container
                )

            if (current != null) {
                actualizarMenuLateralParaFragment(current)

                if (current is RoleAwareFragment) {
                    current.onUserModeChanged()
                }
            }

            navigationView.menu.close()
            navigationView.invalidate()
            navigationView.requestLayout()
        }
    }

    private fun openFragment(fragment: Fragment) {
        val tag = when (fragment) {
            is Recientes -> "RECENTES_TAG"
            else -> fragment::class.java.simpleName
        }

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment, tag)
            .commit()

        actualizarMenuLateralParaFragment(fragment)

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

//            R.id.nav_proteger -> {
//                // Acción para control parental
//                Toast.makeText(this, "Sección no disponible. Próximamente", Toast.LENGTH_SHORT).show()
//            }

//            R.id.nav_control_parental -> {
//                // Acción para control parental
//                Toast.makeText(this, "Sección no disponible. Próximamente", Toast.LENGTH_SHORT).show()
//            }

//            R.id.nav_editar -> {
//                // Acción para editarok
//                Toast.makeText(this, "Sección no disponible. Próximamente", Toast.LENGTH_SHORT).show()
//            }

            R.id.nav_eliminar -> {
                // Acción para eliminar
                //Toast.makeText(this, "Seleccionado: ${menuItem.title}", Toast.LENGTH_SHORT).show()
                //solicitarContrasena()
                withHomeFragment { solicitarContrasena() }
                val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
                when (fragment) {
                    is Recientes -> fragment.solicitarContrasena()
                    is HomeFragment -> fragment.solicitarContrasena()
                    //is FavoritosFragment -> fragment.solicitarContrasena() // AGREGAR MAS PESTAÑAS
                    else -> Toast.makeText(this, "Fragmento no compatible", Toast.LENGTH_SHORT).show()
                }
            }

            R.id.nav_papelera -> {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, PapeleraFragment())
                    .addToBackStack(null)
                    .commit()
            }

            R.id.nav_administrar_perfiles -> {
                menuItem.isChecked = false

                if (sessionManager.isTutor()) {
                    sessionManager.deactivateTutorMode()
                    val current =
                        supportFragmentManager.findFragmentById(
                            R.id.fragment_container
                        )

                    if (current != null) {
                        actualizarMenuLateralParaFragment(current)
                        if (current is RoleAwareFragment) {
                            current.onUserModeChanged()
                        }
                        navigationView.menu.close()
                        navigationView.invalidate()
                        navigationView.requestLayout()
                    }

                    drawerLayout.post {
                        drawerLayout.closeDrawer(GravityCompat.START)
                    }

                    Toast.makeText(
                        this,
                        "Modo paciente activado",
                        Toast.LENGTH_SHORT
                    ).show()

                } else {
                    mostrarDialogoAdministrarPerfiles()
                    drawerLayout.post {
                        drawerLayout.closeDrawer(GravityCompat.START)
                    }
                }
                return true
            }

//            R.id.nav_usuario_a -> {
//                cambiarUsuarioDebug(SessionManager.LOCAL_USER_A)
//            }
//
//            R.id.nav_usuario_b -> {
//                cambiarUsuarioDebug(SessionManager.LOCAL_USER_B)
//            }

            R.id.exportar_archivos -> {
                // Acción para exportar archivos
                Toast.makeText(this, "Seleccionado: ${menuItem.title}", Toast.LENGTH_SHORT).show()
                //abrirSelectorArchivos()
                //withHomeFragment { mostrarDialogoSeleccionarElementos() }
                val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
                when (fragment) {
                    is HomeFragment -> fragment.mostrarDialogoSeleccionarElementos()
                    is Recientes -> fragment.mostrarDialogoSeleccionarElementos()
                    is CategoriaDetalleFragment -> fragment.mostrarDialogoSeleccionarElementos()
                    //else -> Toast.makeText(this, "Fragmento no compatible", Toast.LENGTH_SHORT).show()
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
                    is Listas -> fragment.importarArchivos()
                    //is FavoritosFragment -> fragment.importarArchivos() // AGREGAR MAS PESTAÑAS
                    else -> Toast.makeText(this, "Fragmento no compatible", Toast.LENGTH_SHORT).show()
                }

            }

            R.id.nav_cuenta_accion -> {
                mostrarDialogoCuenta()
            }
            R.id.nav_sincronizar -> {
                sincronizarCuenta()
            }
            R.id.nav_download -> {
                sincronizarDownload()
            }

        }

        menuItem.isChecked = false // desmarcar el ítem seleccionado
        //drawerLayout.closeDrawer(GravityCompat.END) // Cierra el menú después de seleccionar un ítem
        drawerLayout.closeDrawer(GravityCompat.START) // Cierra el menú después de seleccionar un ítem desde izq
        return false
    }

    fun actualizarMenuLateralParaFragment(fragment: Fragment) {

        val menu = navigationView.menu

        // =========================
        // Permisos por rol
        // =========================

        val canDelete =permissionManager.canDeleteMedia()
        val canEdit = permissionManager.canEditMedia()
        val canTrash =permissionManager.canAccessTrash()
        val canImportExport =permissionManager.canImportExport()
        val canManageProfiles = permissionManager.canManageProfiles()

        // =========================
        // CONTROL PARENTAL
        // =========================

        val canSeeControlParental = canDelete || canEdit || canTrash
        // padre
        menu.findItem(R.id.nav_control_parental)?.isVisible =canSeeControlParental
        // hijos
        menu.findItem(R.id.nav_proteger)?.isVisible =canSeeControlParental
        menu.findItem(R.id.nav_editar)?.isVisible =  canEdit
        menu.findItem(R.id.nav_eliminar)?.isVisible =canDelete
        menu.findItem(R.id.nav_papelera)?.isVisible =canTrash

//        menu.findItem(R.id.nav_usuario_a)?.isChecked =
//            sessionManager.getCurrentUserId() == SessionManager.LOCAL_USER_A
//
//        menu.findItem(R.id.nav_usuario_b)?.isChecked =
//            sessionManager.getCurrentUserId() == SessionManager.LOCAL_USER_B

        // =========================
        // IMPORT / EXPORT
        // =========================

        menu.findItem(R.id.exportar_archivos)?.isVisible = canImportExport
        menu.findItem(R.id.importar_archivos)?.isVisible = canImportExport

        // =========================
        // PERFILES
        // =========================

        menu.findItem(R.id.nav_administrar_perfiles)?.isVisible =canManageProfiles

        menu.findItem(R.id.nav_administrar_perfiles)?.title =
            if (sessionManager.isTutor()) {
                "Volver a modo paciente"
            } else {
                "Administrar perfiles"
            }

        // =========================
        // Configuración específica
        // del fragment
        // =========================

        if (fragment is DrawerMenuConfig) {
            fragment.configureDrawerMenu(menu)
        }



    }

    override fun onResume() {
        super.onResume()

        checkTutorTimeout()
    }

    private fun checkTutorTimeout() {

        if (
            sessionManager.isTutor() &&
            sessionManager.isTutorSessionExpired()
        ) {

            sessionManager.deactivateTutorMode()

            val current =
                supportFragmentManager.findFragmentById(
                    R.id.fragment_container
                )

            if (current != null) {
                actualizarMenuLateralParaFragment(current)
                if (current is RoleAwareFragment) {
                    current.onUserModeChanged()
                }
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            Toast.makeText(
                this,
                "Modo tutor finalizado por inactividad",
                Toast.LENGTH_SHORT
            ).show()

        } else {
            resetTutorTimeoutTimer()
        }
    }

    private fun resetTutorTimeoutTimer() {

        tutorTimeoutHandler.removeCallbacks(tutorTimeoutRunnable)

        if (sessionManager.isTutor()) {

            sessionManager.touchTutorAccess()

            tutorTimeoutHandler.postDelayed(
                tutorTimeoutRunnable,
                SessionManager.TUTOR_TIMEOUT_MS
            )
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {

        resetTutorTimeoutTimer()

        return super.dispatchTouchEvent(ev)
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

    // funcion para abrir el selector de archivos desde homefragment.
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
//                R.id.menu_sugeridos -> {
//                    openFragment(Sugeridos())
//                    true
//                }
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

//    fun irASugeridosDesdeHome() {
//        navigateTo(R.id.menu_sugeridos, Sugeridos())
//    }

    fun irAHomeDesdeHome() {
        navigateTo(R.id.menu_home, HomeFragment())
    }

    // funcion para pedir a los fragments que soporten "Agregar a lista" que muestren el dialog correspondiente
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

    // funcion para manejar los resultados de las actividades de captura, selección e importación
    @RequiresApi(Build.VERSION_CODES.N)
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
                UCrop.getOutput(data!!)?.let {
                    lastPreviewUri = it
                    ingresarNombreArchivo(it, true)
                }
            } else {
                lastPreviewUri?.let {
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
                    val uriNormalizada = normalizarImagenParaPreview(it)
                    ingresarNombreArchivo(uriNormalizada, true)
                } else if (mime?.startsWith("video/") == true) {
                    ingresarNombreArchivo(it, false)
                }
            }
        }
    }

    // funcion para mostrar un dialog personalizado para ingresar el nombre del archivo, con preview de la imagen/video y validación de campo vacío
    private fun ingresarNombreArchivo(mediaUri: Uri, isImage: Boolean) {

        Log.d("DIALOG_FLOW", "Mostrando dialog desde MainActivity")

        lastPreviewUri = mediaUri

        val view = layoutInflater.inflate(R.layout.dialog_ingresar_sonido, null)

        val imagePreview = view.findViewById<ImageView>(R.id.imagePreview)
        val editNombre = view.findViewById<EditText>(R.id.editNombre)
        val btnEditar = view.findViewById<FloatingActionButton>(R.id.btnEditar)
        val btnGuardar = view.findViewById<MaterialButton>(R.id.btnGuardar)
        val btnCancelar = view.findViewById<MaterialButton>(R.id.btnCancelar)
        val btnCerrar = view.findViewById<ImageButton>(R.id.btnCerrar)

        editNombre.setText(pendingNombreTexto)

        imagePreview.setImageURI(mediaUri)

        btnEditar.visibility =
            if (isImage) View.VISIBLE else View.GONE

        val dialog = AlertDialog.Builder(this, R.style.ThemeOverlay_Comunic_AlertDialog).setView(view).create()

        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val width = (resources.displayMetrics.widthPixels * 0.85).toInt()
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)



        btnEditar.setOnClickListener {
            pendingNombreTexto = editNombre.text.toString()
            lastPreviewUri = mediaUri

            dialog.dismiss()
            startCrop(mediaUri)
        }

        btnGuardar.setOnClickListener {
            val nombre = editNombre.text.toString().trim()

            if (nombre.isNotEmpty()) {

                guardarArchivo(mediaUri, nombre, isImage)

                pendingNombreTexto = ""
                lastPreviewUri = null

                dialog.dismiss()

                mostrarSnackbar(
                    if (isImage) "Tu imagen se guardó con éxito" else "Tu video se guardó con éxito",
                    TipoSnackbar.EXITO
                )

            } else {
                editNombre.error = "Ingresá un nombre"
            }
        }

        btnCancelar.setOnClickListener {
            pendingNombreTexto = ""
            lastPreviewUri = null
            dialog.dismiss()
        }

        btnCerrar.setOnClickListener {
            pendingNombreTexto = ""
            lastPreviewUri = null
            dialog.dismiss()
        }

        dialog.show()

        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        dialog.setCancelable(false)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun normalizarImagenParaPreview(uri: Uri): Uri {
        return try {
            val orientation = contentResolver.openInputStream(uri)?.use { input ->
                ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL

            val bitmapOriginal = contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input)
            } ?: return uri

            val matrix = Matrix()

            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    matrix.postRotate(90f)
                    matrix.preScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    matrix.postRotate(270f)
                    matrix.preScale(-1f, 1f)
                }
            }

            val necesitaTransformacion =
                orientation != ExifInterface.ORIENTATION_NORMAL &&
                        orientation != ExifInterface.ORIENTATION_UNDEFINED

            val bitmapCorregido =
                if (necesitaTransformacion) {
                    Bitmap.createBitmap(
                        bitmapOriginal,
                        0,
                        0,
                        bitmapOriginal.width,
                        bitmapOriginal.height,
                        matrix,
                        true
                    )
                } else {
                    bitmapOriginal
                }

            val file = File(
                cacheDir,
                "preview_normalizada_${System.currentTimeMillis()}.jpg"
            )

            FileOutputStream(file).use { output ->
                bitmapCorregido.compress(
                    Bitmap.CompressFormat.JPEG,
                    90,
                    output
                )
            }

            Uri.fromFile(file)

        } catch (e: Exception) {
            e.printStackTrace()
            uri
        }
    }
    // funcion para mostrar un snackbar con mensaje personalizado y colores distintos para éxito o error, usado después de guardar un archivo o al ocurrir un error
    private fun mostrarSnackbar(
        mensaje: String,
        tipo: TipoSnackbar
    ) {
        val rootView = findViewById<View>(android.R.id.content)

        val snackbar = Snackbar.make(rootView, mensaje, Snackbar.LENGTH_SHORT)



        val color = when (tipo) {
            TipoSnackbar.EXITO ->
                ContextCompat.getColor(this, R.color.color_success)

            TipoSnackbar.ERROR ->
                ContextCompat.getColor(
                    this,
                    com.google.android.material.R.color.design_default_color_error
                )

            TipoSnackbar.ADVERTENCIA ->
                ContextCompat.getColor(this, R.color.color_warning)
        }

        snackbar.setBackgroundTint(color)
        snackbar.setTextColor(Color.WHITE)
        snackbar.show()

        val textView = snackbar.view.findViewById<TextView>(
            com.google.android.material.R.id.snackbar_text
        )

        when (tipo) {
            TipoSnackbar.EXITO ->
                textView.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_check, 0, 0, 0)

            TipoSnackbar.ADVERTENCIA ->
                textView.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_warning, 0, 0, 0)

            TipoSnackbar.ERROR ->
                textView.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_error, 0, 0, 0)
        }
        textView.compoundDrawablePadding = 16
    }

    enum class TipoSnackbar {
        EXITO,
        ERROR,
        ADVERTENCIA
    }

    // funcion para guardar el archivo en almacenamiento interno, registrar su metadata en SharedPreferences y notificar a los fragments interesados, además de agregarlo a la categoría pendiente si corresponde. Se llama desde ingresarNombreArchivo después de que el usuario ingresa el nombre y confirma guardar.
    private fun guardarArchivo(uri: Uri, nombre: String, esImagen: Boolean) {

        val savedUri = guardarEnAlmacenamientoInterno(uri, nombre, esImagen)
            ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            val userId =
                SessionManager(this@MainActivity)
                    .getCurrentUserId()
            val db = AppDatabase.getDatabase(applicationContext)
            val now = System.currentTimeMillis()

            val media = MediaEntity(
                mediaId = UUID.randomUUID().toString(),
                displayName = nombre,
                localUri = savedUri.toString(),
                mediaType = if (esImagen) "image" else "video",
                createdAt = now,
                updatedAt = now,
                isDeleted = false,
                ownerUserId =
                SessionManager(this@MainActivity)
                    .getCurrentUserId()
            )

            db.mediaDao().upsert(media)

            val item = ItemLista(
                id = ItemKey.media(media.mediaId),
                nombre = media.displayName,
                uri = savedUri,
                esImagen = esImagen,
                timestamp = media.createdAt
            )

            saveMediaData(nombre, savedUri, esImagen)

            withContext(Dispatchers.Main) {
                val currentFragment =
                    supportFragmentManager.findFragmentById(
                        R.id.fragment_container
                    )

                if (currentFragment is MediaResultListener) {
                    Log.d(
                        "MEDIA_NOTIFY",
                        "Notificando a: ${currentFragment.javaClass.simpleName}"
                    )
                    currentFragment.onMediaCreated(item)
                } else {
                    Log.e(
                        "MEDIA_NOTIFY",
                        "Fragment actual no implementa MediaResultListener"
                    )
                }


            }

            pendingCategoryId?.let { catId ->

                if (ItemKey.isMedia(item.id) && !ItemKey.isMediaUuid(item.id)) {
                    Log.e(
                        "ITEM_KEY_VALIDATION",
                        "Intento de insertar MED legacy: ${item.id}"
                    )
                    return@let
                }

                val next = db.categoryDao().getMaxOrderIndex(catId, userId) + 1

                db.categoryDao().insertCategoryItem(
                    CategoryItemEntity(
                        placementId = UUID.randomUUID().toString(),
                        categoryId = catId,
                        itemKey = item.id,
                        orderIndex = next,
                        createdAt = now,
                        updatedAt = now,
                        ownerUserId =
                        SessionManager(this@MainActivity)
                            .getCurrentUserId()
                    )
                )
            }

            pendingCategoryId = null
        }
    }

    // funcion para copiar el archivo desde su ubicación original (galería o cámara) a una carpeta privada de la app, con un nombre basado en el input del usuario y extensión según el tipo de media. Retorna el URI del nuevo archivo o null si hubo un error. Se llama desde guardarArchivo para hacer la copia física del archivo antes de registrar su metadata y notificar a los fragments.
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
            if (esImagen) {
                ImageCompressor.saveOptimizedImage(
                    context = this,
                    sourceUri = mediaUri,
                    destination = file
                )
            } else {
                contentResolver.openInputStream(mediaUri)?.use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }
                Uri.fromFile(file)
            }

        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // funcion para guardar en SharedPreferences la relación entre el nombre del archivo, su URI y si es imagen o video, usando un esquema de claves que permite recuperar esta info fácilmente. Se llama desde guardarArchivo después de copiar el archivo a almacenamiento interno, para registrar su metadata localmente.
    fun saveMediaData(nombre: String, uri: Uri, isImage: Boolean) {
        val prefs = getSharedPreferences("media_data", MODE_PRIVATE)
        prefs.edit()
            .putString(nombre, uri.toString())
            .putBoolean("$nombre|type", isImage)
            .apply()
    }

    private fun mostrarDialogoAdministrarPerfiles() {

        val input = EditText(this).apply {
            inputType =
                InputType.TYPE_CLASS_TEXT or
                        InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "Contraseña"
        }

        AlertDialog.Builder(
            this,
            R.style.ThemeOverlay_Comunic_AlertDialog
        )
            .setTitle("ADMINISTRAR PERFILES")
            .setMessage("Ingresá la contraseña de tutor")
            .setView(input)
            .setPositiveButton("INGRESAR") { _, _ ->

                val password = input.text.toString()

                if (password == "1234") {
                    sessionManager.activateTutorMode()
                    val current =
                        supportFragmentManager.findFragmentById(
                            R.id.fragment_container
                        )

                    if (current != null) {
                        actualizarMenuLateralParaFragment(current)
                    }

                    navigationView.menu.findItem(R.id.nav_administrar_perfiles)?.isChecked = false
                    navigationView.menu.close()
                    navigationView.invalidate()
                    navigationView.requestLayout()

                    Toast.makeText(
                        this,
                        "Modo tutor activado",
                        Toast.LENGTH_SHORT
                    ).show()

                } else {
                    Toast.makeText(
                        this,
                        "Contraseña incorrecta",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton("CANCELAR", null)
            .show()
    }

    private fun cambiarUsuarioDebug(userId: String) {

        sessionManager.setCurrentUserId(userId)
        sessionManager.deactivateTutorMode()

        val current =
            supportFragmentManager.findFragmentById(
                R.id.fragment_container
            )

        if (current != null) {
            actualizarMenuLateralParaFragment(current)

            if (current is RoleAwareFragment) {
                current.onUserModeChanged()
            }
        }

        when (current) {
            is HomeFragment -> openFragment(HomeFragment())
            is Recientes -> openFragment(Recientes())
            is Listas -> openFragment(Listas())
            is CategoriaDetalleFragment -> {
                openFragment(Listas())
            }
            else -> openFragment(Recientes())
        }

        Toast.makeText(
            this,
            "Usuario activo: $userId",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun runStartupMigrations() {

        lifecycleScope.launch {
            if (!sessionManager.isLegacyMediaKeyMigrationDone()) {
                Log.d(
                    "STARTUP_MIGRATION",
                    "Ejecutando migración MED:nombre -> MED:uuid"
                )
                try {
                    LegacyMediaKeyMigrationRepository(this@MainActivity)
                        .migrateLegacyMediaKeys()
                    sessionManager.setLegacyMediaKeyMigrationDone()
                    Log.d(
                        "STARTUP_MIGRATION",
                        "Migración MED:nombre -> MED:uuid finalizada"
                    )
                } catch (e: Exception) {
                    Log.e(
                        "STARTUP_MIGRATION",
                        "Error ejecutando migración MED:nombre -> MED:uuid",
                        e
                    )
                }
            } else {
                Log.d(
                    "STARTUP_MIGRATION",
                    "Migración MED:nombre -> MED:uuid ya realizada"
                )
            }

            // para optimizar imágenes en la app, se ejecuta la migración de optimización de imágenes al inicio
            if (!sessionManager.isImageOptimizationDone()) {
                val ok = ImageOptimizationMigration(
                    this@MainActivity
                ).optimizeImages()
                Log.d("TEST", "Optimización finalizada: $ok")

                if (ok) {
                    sessionManager.setImageOptimizationDone()
                }
            }
        }
    }

    private fun mostrarDialogoCuenta() {
        val emailActual = AuthSessionManager(this).getCurrentEmail()
        if (emailActual == null) {
            mostrarDialogoLogin()
        } else {
            AuthSessionManager(this)
                .logout()
            mostrarSnackbar(
                "Tu sesión se cerró",
                TipoSnackbar.ADVERTENCIA
            )
            refrescarEstadoSesion()
            SyncEvents.notifyDataChanged()}

    }

    private fun sincronizarCuenta() {

        val syncDialog = SyncProgressDialog()
        syncDialog.show(supportFragmentManager, "sync_dialog")
        syncDialog.showIndeterminate("Preparando sincronización...")

        lifecycleScope.launch {
            try {
                val repo = CloudSyncRepository(this@MainActivity)
                repo.uploadAll(syncDialog)
                syncDialog.finishSuccess("Sincronización completada")
            } catch (e: Exception) {
                syncDialog.finishError(
                    e.message ?: "Error desconocido"
                )
            }
        }
    }

    private fun sincronizarDownload() {

        val syncDialog = SyncProgressDialog()

        syncDialog.show(
            supportFragmentManager,
            "sync_dialog"
        )

        syncDialog.showIndeterminate(
            "Preparando descarga..."
        )

        lifecycleScope.launch {

            try {

                val repo = CloudSyncRepository(this@MainActivity)
                repo.downloadAll(syncDialog)
                SyncEvents.notifyDataChanged()
                syncDialog.finishSuccess("Descarga completada")
            } catch (e: Exception) {
                syncDialog.finishError(
                    e.message ?: "Error desconocido"
                )
            }
        }
    }


    private fun mostrarDialogoCuentaActiva(
        email: String
    ) {

        AlertDialog.Builder(
            this,
            R.style.ThemeOverlay_Comunic_AlertDialog
        )
            .setTitle("Cuenta")
            .setMessage("Sesión iniciada como:\n$email")
            .setPositiveButton("Cerrar sesión") { _, _ ->

                AuthSessionManager(this)
                    .logout()

                Toast.makeText(
                    this,
                    "Sesión cerrada",
                    Toast.LENGTH_SHORT
                ).show()

                refrescarEstadoSesion()            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun mostrarDialogoLogin() {

        val dialogView =
            layoutInflater.inflate(
                R.layout.login_dialog_login,
                null
            )

        val editEmail =  dialogView.findViewById<TextInputEditText>(R.id.editEmail)
        val editPassword = dialogView.findViewById<TextInputEditText>(R.id.editPassword)
        val btnLogin = dialogView.findViewById<MaterialButton>(R.id.btnLogin)
        val btnIrARegistro = dialogView.findViewById<TextView>(R.id.btnIrARegistro)
        val btnRecuperar =  dialogView.findViewById<TextView>(R.id.btnRecuperar)
        val btnCancelar = dialogView.findViewById<MaterialButton>(R.id.btnCancelar)

        val dialog = AlertDialog.Builder(this, R.style.ThemeOverlay_Comunic_AlertDialog).setView(dialogView).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val texto = SpannableString("¿No tenés cuenta? Crear cuenta")

        val inicio = texto.indexOf("Crear cuenta")
        val fin = inicio + "Crear cuenta".length

        texto.setSpan(ForegroundColorSpan(ContextCompat.getColor(this, R.color.color12)), inicio, fin, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        texto.setSpan(UnderlineSpan(), inicio, fin, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        btnIrARegistro.text = texto

        btnLogin.setOnClickListener {
            val email = editEmail.text?.toString()?.trim().orEmpty()
            val password =editPassword.text?.toString().orEmpty()

            if (email.isBlank() || password.isBlank()) {
                Toast.makeText(this, "Ingresá email y contraseña", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                try {
                    //AuthSessionManager(this@MainActivity).loginAndAdoptLocalData(email, password)
                    AuthSessionManager(this@MainActivity)
                        .loginAndUseAccount(
                            email,
                            password
                        )
                    CloudSyncRepository(this@MainActivity)
                        .downloadAll()

                    Toast.makeText(this@MainActivity, "Sesión iniciada", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    refrescarEstadoSesion()
                    SyncEvents.notifyDataChanged()
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Error al iniciar sesión: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        btnRecuperar.setOnClickListener {

            val email = editEmail.text?.toString()?.trim().orEmpty()
            if (email.isBlank()) {
                Toast.makeText(this, "Ingresá tu email para recuperar la contraseña", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                try {
                    AuthSessionManager(this@MainActivity).sendPasswordReset(email)
                    Toast.makeText(this@MainActivity, "Te enviamos un email para recuperar la contraseña", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        btnIrARegistro.setOnClickListener {
            dialog.dismiss()
            mostrarDialogoRegistro()
        }

        btnCancelar.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun mostrarDialogoRegistro() {

        val dialogView = layoutInflater.inflate(R.layout.login_dialog_signup, null)
        val editNombre = dialogView.findViewById<TextInputEditText>(R.id.editNombre)
        val editEmail = dialogView.findViewById<TextInputEditText>(R.id.editEmail)
        val editPassword = dialogView.findViewById<TextInputEditText>(R.id.editPassword)
        val btnRegistro =dialogView.findViewById<MaterialButton>(R.id.btnRegistro)
        val btnCancelar = dialogView.findViewById<MaterialButton>(R.id.btnCancelar)
        val dialog = AlertDialog.Builder(this, R.style.ThemeOverlay_Comunic_AlertDialog).setView(dialogView).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        btnRegistro.setOnClickListener {
            val nombre = editNombre.text?.toString()?.trim().orEmpty()
            val email = editEmail.text?.toString()?.trim().orEmpty()
            val password = editPassword.text?.toString().orEmpty()

            if (nombre.isBlank() || email.isBlank() || password.isBlank()) {
                Toast.makeText(this, "Ingresá nombre, email y contraseña", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                try {
                    AuthSessionManager(this@MainActivity).registerAndAdoptLocalData(email, password, nombre)
                    CloudSyncRepository(this@MainActivity).uploadAll()
                    Toast.makeText(this@MainActivity, "Cuenta creada. Tu contenido quedó asociado a esta cuenta.", Toast.LENGTH_LONG).show()
                    dialog.dismiss()
                    recreate()
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Error al crear cuenta: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        btnCancelar.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun actualizarSaludoCuenta() {

        val saludo = findViewById<TextView>(R.id.txtSaludoCuenta)
        val nombreLocal = sessionManager.getDisplayName()

        val nombreFirebase = AuthSessionManager(this).getCurrentDisplayName()

        if (!nombreFirebase.isNullOrBlank()) {
            sessionManager.setDisplayName(nombreFirebase)
        }

        val nombreFinal =
            if (!nombreLocal.isNullOrBlank()) {
                nombreLocal
            } else {
                nombreFirebase
            }

        saludo.text =
            if (!nombreFinal.isNullOrBlank()) {
                "¡Hola, $nombreFinal!"
            } else {
                "¡Hola!"
            }
    }

    private fun actualizarBloqueCuentaMenu() {

        val menu = navigationView.menu
        val authSessionManager = AuthSessionManager(this)
        val email = authSessionManager.getCurrentEmail()
        Log.d(
            "SESSION_DEBUG",
            "email=${authSessionManager.getCurrentEmail()}"
        )
        val nombre = sessionManager.getDisplayName()

        if (!email.isNullOrBlank()) {

            menu.findItem(R.id.nav_cuenta_info)?.title =  if (!nombre.isNullOrBlank()) nombre else "Cuenta activa"
            menu.findItem(R.id.nav_cuenta_email)?.title =  email
            menu.findItem(R.id.nav_cuenta_email)?.isVisible =true
            menu.findItem(R.id.nav_sincronizar)?.isVisible = true
            menu.findItem(R.id.nav_download)?.isVisible = true
            menu.findItem(R.id.nav_cuenta_accion)?.title =  "Cerrar sesión"
        } else {
            menu.findItem(R.id.nav_cuenta_info)?.title =  "Sin iniciar sesión"
            menu.findItem(R.id.nav_cuenta_email)?.isVisible = false
            menu.findItem(R.id.nav_sincronizar)?.isVisible = false
            menu.findItem(R.id.nav_download)?.isVisible = false
            menu.findItem(R.id.nav_cuenta_accion)?.title = "Iniciar sesión / Crear cuenta"
        }
    }

    private fun refrescarEstadoSesion() {
        actualizarBloqueCuentaMenu()
        actualizarSaludoCuenta()
        val current =
            supportFragmentManager.findFragmentById(
                R.id.fragment_container
            )
        if (current != null) {
            actualizarMenuLateralParaFragment(current)
            if (current is RoleAwareFragment) {
                current.onUserModeChanged()
            }
        }
        navigationView.invalidate()
        navigationView.requestLayout()
    }

}