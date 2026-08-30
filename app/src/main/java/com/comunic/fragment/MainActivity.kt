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
import android.view.WindowManager
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
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import com.bumptech.glide.Glide
import com.comunic.AddToListHost
import com.comunic.CategoryPreview
import com.comunic.ItemKey
import com.comunic.ItemLista
import com.comunic.R
import com.comunic.auth.AuthSessionManager
import com.comunic.data.entity.MediaEntity
import com.comunic.dialog.SyncProgressDialog
import com.comunic.interfaces.ZipImportListener
import com.comunic.migration.ImageOptimizationMigration
import com.comunic.migration.LegacyMediaKeyMigrationRepository
import com.comunic.session.PermissionManager
import com.comunic.session.RoleAwareFragment
import com.comunic.session.SessionManager
import com.comunic.sync.CloudSyncRepository
import com.comunic.sync.SyncEvents
import com.comunic.utils.FileHash
import com.comunic.utils.ImageCompressor
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.imageview.ShapeableImageView
import kotlinx.coroutines.withContext
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.squareup.picasso.Picasso


class MainActivity : AppCompatActivity(),  NavigationView.OnNavigationItemSelectedListener {


    private lateinit var drawerLayout: DrawerLayout     // para el menu lateral
    private lateinit var navigationView: NavigationView
    private lateinit var imgPerfil: ShapeableImageView

    private lateinit var btnPerfilActual: MaterialButton
    private lateinit var btnAdministrarPerfiles: MaterialButton
    private lateinit var btnExportar: MaterialButton
    private lateinit var btnImportar: MaterialButton
    private lateinit var tvControlParental: TextView
    private lateinit var dividerControlParental: View
  //  private lateinit var btnProteger: MaterialButton
    private lateinit var btnEditar: MaterialButton
    private lateinit var btnEliminar: MaterialButton
    private lateinit var btnPapelera: MaterialButton
    private lateinit var txtCuenta: TextView
    private lateinit var txtEmail: TextView
    private lateinit var btnSincronizar: MaterialButton
    private lateinit var btnDownload: MaterialButton
    private lateinit var btnLogin: MaterialButton
    private lateinit var btnSignup: MaterialButton
    private lateinit var btnCerrarSesion: MaterialButton


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

    private var mediaEnEdicion: ItemLista? = null
    private var dialogEdicionMedia: AlertDialog? = null


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
        imgPerfil = findViewById(R.id.imgPerfil)
        //inicializaciones menu
        //val drawer = navigationView.getHeaderView(0)
        btnPerfilActual = navigationView.findViewById(R.id.btnPerfilActual)
        btnAdministrarPerfiles = navigationView.findViewById(R.id.btnAdministrarPerfiles)
        btnExportar = navigationView.findViewById(R.id.btnExportar)
        btnImportar = navigationView.findViewById(R.id.btnImportar)
        tvControlParental = navigationView.findViewById(R.id.tvControlParental)
        dividerControlParental = navigationView.findViewById(R.id.dividerControlParental)
       // btnProteger = navigationView.findViewById(R.id.btnProteger)
        btnEditar = navigationView.findViewById(R.id.btnEditar)
        btnEliminar = navigationView.findViewById(R.id.btnEliminar)
        btnPapelera = navigationView.findViewById(R.id.btnPapelera)
        txtCuenta = navigationView.findViewById(R.id.txtCuenta)
        txtEmail = navigationView.findViewById(R.id.txtEmail)
        btnSincronizar = navigationView.findViewById(R.id.btnSincronizar)
        btnDownload = navigationView.findViewById(R.id.btnDownload)
        btnLogin = navigationView.findViewById(R.id.btnLogin)
        btnSignup = navigationView.findViewById(R.id.btnSignup)
        btnCerrarSesion = navigationView.findViewById(R.id.btnCerrarSesion)
        // fin inicializaciones menu

       // navigationView.setNavigationItemSelectedListener(this) // Configurar NavigationView y su listener
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
        //val drawerMenu = navigationView.menu

//        drawerMenu.findItem(R.id.nav_proteger)?.isVisible = false
//        drawerMenu.findItem(R.id.nav_editar)?.isVisible = false
//        drawerMenu.findItem(R.id.nav_nosotros)?.isVisible = false

        /////////////////////////// funciones menu lateral ///////////////////////////

        btnLogin.setOnClickListener {
            mostrarDialogoLogin()
            drawerLayout.closeDrawer(GravityCompat.START)
        }
        btnSignup.setOnClickListener {
            mostrarDialogoRegistro()
            drawerLayout.closeDrawer(GravityCompat.START)
        }
        btnCerrarSesion.setOnClickListener {
            mostrarDialogoCerrarSesion()
            drawerLayout.closeDrawer(GravityCompat.START)
        }
        btnSincronizar.setOnClickListener {
            sincronizarCuenta()
            drawerLayout.closeDrawer(GravityCompat.START)
        }
        btnDownload.setOnClickListener {
            sincronizarDownload()
            drawerLayout.closeDrawer(GravityCompat.START)
        }
        btnAdministrarPerfiles.setOnClickListener {
            if (sessionManager.isTutor()) {
                sessionManager.deactivateTutorMode()
                val current = supportFragmentManager.findFragmentById(R.id.fragment_container)
                if (current != null) {
                    actualizarMenuLateralParaFragment(current)
                    if (current is RoleAwareFragment) {
                        current.onUserModeChanged()
                    }
                }
                drawerLayout.closeDrawer(GravityCompat.START)
                mostrarSnackbar(
                    "Modo usuario activado",
                    TipoSnackbar.ADVERTENCIA
                )
            } else {
                mostrarDialogoAdministrarPerfiles()
                drawerLayout.closeDrawer(GravityCompat.START)

            }
        }
        btnExportar.setOnClickListener {

            val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
            when (fragment) {
                is HomeFragment -> fragment.mostrarDialogoSeleccionarElementos()
                is Recientes -> fragment.mostrarDialogoSeleccionarElementos()
                is Listas -> fragment.mostrarDialogoSeleccionarElementos()
                is CategoriaDetalleFragment -> fragment.mostrarDialogoSeleccionarElementos()
            }

            drawerLayout.closeDrawer(GravityCompat.START)
        }
        btnImportar.setOnClickListener {
            val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
            when (fragment) {
                is HomeFragment -> fragment.importarArchivos()
                is Recientes -> fragment.importarArchivos()
                is Listas -> fragment.importarArchivos()
            }
            drawerLayout.closeDrawer(GravityCompat.START)
        }
        btnPapelera.setOnClickListener {

            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, PapeleraFragment())
                .addToBackStack(null)
                .commit()

            drawerLayout.closeDrawer(GravityCompat.START)
        }
        btnEditar.setOnClickListener {

            if (!permissionManager.canEditMedia()) {
                return@setOnClickListener
            }

            val fragment =
                supportFragmentManager.findFragmentById(
                    R.id.fragment_container
                )

            when (fragment) {
                is HomeFragment -> {
                    navigateTo(
                        R.id.menu_recientes,
                        Recientes()
                    )
                }

                is Recientes -> {
                    fragment.activarModoEdicion()
                }

                is Listas -> {
                    fragment.activarModoEdicion()
                }

                else -> {
                    Toast.makeText(
                        this,
                        "Esta pantalla no permite editar elementos",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            drawerLayout.closeDrawer(GravityCompat.START)
        }
        btnEliminar.setOnClickListener {
            withHomeFragment { solicitarContrasena() }
            val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
            when (fragment) {
                is Recientes -> fragment.solicitarContrasena()
                is HomeFragment -> fragment.solicitarContrasena()
                else ->
                    Toast.makeText(this, "Fragmento no compatible", Toast.LENGTH_SHORT).show()
            }
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        /////////////////////////// funciones menu lateral ///////////////////////////


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

            //navigationView.menu.close()
            navigationView.invalidate()
            navigationView.requestLayout()
        }
        lifecycleScope.launch {

            val dao =
                AppDatabase
                    .getDatabase(applicationContext)
                    .categoryDao()

            val placements =
                withContext(Dispatchers.IO) {
                    dao.getActiveMediaPlacements()
                }

            Log.d(
                "MED_MIGRATION_CHECK",
                "Cantidad de referencias MED activas: ${placements.size}"
            )

            placements.forEach { placement ->

                Log.d(
                    "MED_MIGRATION_CHECK",
                    "placementId=${placement.placementId} " +
                            "categoryId=${placement.categoryId} " +
                            "itemKey=${placement.itemKey} " +
                            "isUuid=${ItemKey.isMediaUuid(placement.itemKey)}"
                )
            }
            supportFragmentManager.addOnBackStackChangedListener {
                val fragment = supportFragmentManager
                    .findFragmentById(R.id.fragment_container)

                if (fragment != null) {
                    actualizarMenuLateralParaFragment(fragment)
                }
            }
        }
        procesarEnlaceFirebase(intent)
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
                        "Modo usuario activado",
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
                    is Listas -> fragment.mostrarDialogoSeleccionarElementos()
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

       // val menu = navigationView.menu

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
//        menu.findItem(R.id.nav_control_parental)?.isVisible =canSeeControlParental
//        // hijos
//        menu.findItem(R.id.nav_proteger)?.isVisible =canSeeControlParental
//        menu.findItem(R.id.nav_editar)?.isVisible =  canEdit
//        menu.findItem(R.id.nav_eliminar)?.isVisible =canDelete
//        menu.findItem(R.id.nav_papelera)?.isVisible =canTrash

      //  btnProteger.isVisible = canSeeControlParental
        btnEditar.isVisible = canEdit
        btnEliminar.isVisible = canDelete
        btnPapelera.isVisible = canTrash
        tvControlParental.isVisible = canSeeControlParental
        dividerControlParental.isVisible = canSeeControlParental

//        menu.findItem(R.id.nav_usuario_a)?.isChecked =
//            sessionManager.getCurrentUserId() == SessionManager.LOCAL_USER_A
//
//        menu.findItem(R.id.nav_usuario_b)?.isChecked =
//            sessionManager.getCurrentUserId() == SessionManager.LOCAL_USER_B

        // =========================
        // IMPORT / EXPORT
        // =========================

//        menu.findItem(R.id.exportar_archivos)?.isVisible = canImportExport
//        menu.findItem(R.id.importar_archivos)?.isVisible = canImportExport
        btnExportar.isVisible = canImportExport
        Log.d(
            "MENU_DEBUG",
            "Fragment: ${fragment::class.simpleName}, ocultarImportar=${fragment is CategoriaDetalleFragment}"
        )
        btnImportar.isVisible = canImportExport && fragment !is CategoriaDetalleFragment

        // =========================
        // PERFILES
        // =========================

        //menu.findItem(R.id.nav_administrar_perfiles)?.isVisible =canManageProfiles
        btnAdministrarPerfiles.isVisible = canManageProfiles

//        menu.findItem(R.id.nav_administrar_perfiles)?.title =
//            if (sessionManager.isTutor()) {
//                "Volver a modo paciente"
//            } else {
//                "Administrar perfiles"
//            }
        btnAdministrarPerfiles.text =
            if (sessionManager.isTutor()) {
                "Volver a modo usuario"
            } else {
                "Administrar perfiles"
            }

        // =========================
        // Configuración específica
        // del fragment
        // =========================

//        if (fragment is DrawerMenuConfig) {
//            fragment.configureDrawerMenu(menu)
//        }



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
//            mediaUri?.let {
//                val mime = contentResolver.getType(it)
//                if (mime?.startsWith("image/") == true) {
//                    val uriNormalizada = normalizarImagenParaPreview(it)
//                    ingresarNombreArchivo(uriNormalizada, true)
//                } else if (mime?.startsWith("video/") == true) {
//                    ingresarNombreArchivo(it, false)
//                }
//            }
            mediaUri?.let {
                procesarMediaSeleccionado(it)
            }
        }
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

        val view = layoutInflater.inflate(R.layout.dialog_administrar_perfiles, null)

        val editPassword = view.findViewById<TextInputEditText>(R.id.editPassword)
        val passwordLayout = view.findViewById<TextInputLayout>(R.id.passwordLayout)
        val btnIngresar = view.findViewById<MaterialButton>(R.id.btnIngresar)
        val btnCancelar = view.findViewById<MaterialButton>(R.id.btnCancelar)
        val btnCerrar = view.findViewById<ImageButton>(R.id.btnCerrar)

        val dialog = AlertDialog.Builder(
            this,
            R.style.ThemeOverlay_Comunic_AlertDialog
        )
            .setView(view)
            .create()

        dialog.show()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val width = (resources.displayMetrics.widthPixels * 0.85).toInt()
        dialog.window?.setLayout(
            width,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        dialog.setCancelable(false)

        btnIngresar.setOnClickListener {

            val password = editPassword.text.toString()
            if (password == "1234") {

                dialog.dismiss()
                sessionManager.activateTutorMode()
                val current = supportFragmentManager.findFragmentById(R.id.fragment_container)
                current?.let {
                    actualizarMenuLateralParaFragment(it)
                }

                navigationView.menu.findItem(R.id.nav_administrar_perfiles)?.isChecked = false
                navigationView.menu.close()
                navigationView.invalidate()
                navigationView.requestLayout()

                mostrarSnackbar(
                    "Modo tutor activado",
                    TipoSnackbar.EXITO
                )

            } else {

                passwordLayout.error = "Contraseña incorrecta"
                editPassword.requestFocus()
                dialog.window?.setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                )
                editPassword.doOnTextChanged { _, _, _, _ ->
                    passwordLayout.error = null
                }
            }
        }

        btnCancelar.setOnClickListener {
            dialog.dismiss()
        }

        btnCerrar.setOnClickListener {
            dialog.dismiss()
        }
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

        val syncJob = lifecycleScope.launch {
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
        syncDialog.onCancelSync = {
            syncJob.cancel()
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

        val syncJob = lifecycleScope.launch {

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
        syncDialog.onCancelSync = {
            syncJob.cancel()
        }
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

            val loading = mostrarDialogoCarga()

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

                    loading.dismiss()

                    mostrarSnackbar(
                        "Sesión iniciada correctamente",
                        TipoSnackbar.EXITO
                    )
                    dialog.dismiss()
                    refrescarEstadoSesion()
                    SyncEvents.notifyDataChanged()
                } catch (e: Exception) {
                    mostrarSnackbar(
                        "Error al iniciar sesión: ${e.message}",
                        TipoSnackbar.ERROR
                    )
                    loading.dismiss()
                }
            }
        }

        btnRecuperar.setOnClickListener {

            dialog.dismiss()

            mostrarDialogoRecuperarContrasena()
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

    private fun mostrarDialogoRecuperarContrasena() {

        val dialogView = layoutInflater.inflate(R.layout.login_dialog_password_reset, null)
        val editEmail = dialogView.findViewById<TextInputEditText>(R.id.editEmailReset)
        val btnEnviar = dialogView.findViewById<MaterialButton>(R.id.btnEnviarReset)
        val btnCancelar = dialogView.findViewById<MaterialButton>(R.id.btnCancelarReset)
        val dialog = AlertDialog.Builder(this, R.style.ThemeOverlay_Comunic_AlertDialog)
                .setView(dialogView)
                .create()

        dialog.window?.setBackgroundDrawable(
            ColorDrawable(Color.TRANSPARENT)
        )

        btnEnviar.setOnClickListener {
            val email =
                editEmail.text
                    ?.toString()
                    ?.trim()
                    .orEmpty()

            if (email.isBlank()) {
                Toast.makeText(
                    this,
                    "Ingresá tu correo electrónico",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            lifecycleScope.launch {

                try {

                    btnEnviar.isEnabled = false

                    AuthSessionManager(this@MainActivity)
                        .sendPasswordReset(email)

                    dialog.dismiss()

                    mostrarSnackbar(
                        "Te enviamos un correo para recuperar la contraseña",
                        TipoSnackbar.EXITO
                    )

                } catch (e: Exception) {

                    btnEnviar.isEnabled = true

                    mostrarSnackbar(
                        e.message ?: "No se pudo enviar el correo",
                        TipoSnackbar.ERROR
                    )
                }
            }
        }

        btnCancelar.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun procesarEnlaceFirebase(intent: Intent) {

        val data = intent.data ?: return

        Log.d(
            "PASSWORD_RESET",
            "Enlace recibido: $data"
        )

        // Firebase coloca el enlace real dentro del parámetro "link"
        val firebaseLink =
            data.getQueryParameter("link")

        if (firebaseLink.isNullOrBlank()) {
            Log.d(
                "PASSWORD_RESET",
                "No se encontró parámetro link"
            )
            return
        }

        Log.d(
            "PASSWORD_RESET",
            "Firebase link: $firebaseLink"
        )

        val firebaseUri =
            Uri.parse(firebaseLink)

        val mode =
            firebaseUri.getQueryParameter("mode")

        val oobCode =
            firebaseUri.getQueryParameter("oobCode")

        Log.d(
            "PASSWORD_RESET",
            "mode=$mode"
        )

        Log.d(
            "PASSWORD_RESET",
            "oobCode presente=${!oobCode.isNullOrBlank()}"
        )

        if (
            mode == "resetPassword" &&
            !oobCode.isNullOrBlank()
        ) {

            lifecycleScope.launch {

                try {

                    val email =
                        AuthSessionManager(this@MainActivity)
                            .verifyPasswordResetCode(oobCode)

                    Log.d(
                        "PASSWORD_RESET",
                        "Código válido para: $email"
                    )

                    mostrarDialogoNuevaContrasena(
                        oobCode
                    )

                } catch (e: Exception) {

                    Log.e(
                        "PASSWORD_RESET",
                        "Código inválido o expirado",
                        e
                    )

                    mostrarSnackbar(
                        "El enlace de recuperación no es válido o ya expiró",
                        TipoSnackbar.ERROR
                    )
                }
            }
        }
    }
    private fun mostrarDialogoNuevaContrasena(
        code: String
    ) {

        val dialogView =
            layoutInflater.inflate(
                R.layout.login_dialog_new_password,
                null
            )

        val editNewPassword =
            dialogView.findViewById<TextInputEditText>(
                R.id.editNewPassword
            )

        val editRepeatPassword =
            dialogView.findViewById<TextInputEditText>(
                R.id.editRepeatPassword
            )

        val btnConfirmar =
            dialogView.findViewById<MaterialButton>(
                R.id.btnConfirmarReset
            )

        val dialog =
            AlertDialog.Builder(
                this,
                R.style.ThemeOverlay_Comunic_AlertDialog
            )
                .setView(dialogView)
                .create()

        dialog.window?.setBackgroundDrawable(
            ColorDrawable(Color.TRANSPARENT)
        )

        btnConfirmar.setOnClickListener {

            val newPassword =
                editNewPassword.text
                    ?.toString()
                    .orEmpty()

            val repeatPassword =
                editRepeatPassword.text
                    ?.toString()
                    .orEmpty()

            if (newPassword.isBlank() || repeatPassword.isBlank()) {

                Toast.makeText(
                    this,
                    "Completá ambos campos",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            if (newPassword != repeatPassword) {

                Toast.makeText(
                    this,
                    "Las contraseñas no coinciden",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            if (newPassword.length < 6) {

                Toast.makeText(
                    this,
                    "La contraseña debe tener al menos 6 caracteres",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            lifecycleScope.launch {

                try {

                    btnConfirmar.isEnabled = false

                    AuthSessionManager(this@MainActivity)
                        .confirmPasswordReset(
                            code,
                            newPassword
                        )

                    dialog.dismiss()

                    mostrarDialogoPasswordResetExitoso()

                } catch (e: Exception) {

                    btnConfirmar.isEnabled = true

                    mostrarSnackbar(
                        e.message
                            ?: "No se pudo cambiar la contraseña",
                        TipoSnackbar.ERROR
                    )
                }
            }
        }

        dialog.show()
    }

    private fun mostrarDialogoPasswordResetExitoso() {

        val dialogView =
            layoutInflater.inflate(
                R.layout.login_dialog_password_reset_success,
                null
            )

        val btnIngresar =
            dialogView.findViewById<MaterialButton>(
                R.id.btnIrAIngresar
            )

        val dialog =
            AlertDialog.Builder(
                this,
                R.style.ThemeOverlay_Comunic_AlertDialog
            )
                .setView(dialogView)
                .create()

        dialog.window?.setBackgroundDrawable(
            ColorDrawable(Color.TRANSPARENT)
        )

        btnIngresar.setOnClickListener {

            dialog.dismiss()

            mostrarDialogoLogin()
        }

        dialog.show()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        setIntent(intent)

        procesarEnlaceFirebase(intent)
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

    private fun mostrarDialogoCerrarSesion() {

        val dialogView = layoutInflater.inflate(R.layout.login_dialog_logout, null)
        val btnCerrarSesion = dialogView.findViewById<MaterialButton>(R.id.btnCerrarSesion)
        val btnCancelar = dialogView.findViewById<MaterialButton>(R.id.btnCancelar)

        val dialog =
            AlertDialog.Builder(this, R.style.ThemeOverlay_Comunic_AlertDialog)
                .setView(dialogView)
                .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        btnCerrarSesion.setOnClickListener {

            AuthSessionManager(this).logout()

            refrescarEstadoSesion()
            SyncEvents.notifyDataChanged()

            mostrarSnackbar(
                "Sesión cerrada correctamente",
                TipoSnackbar.EXITO
            )

            dialog.dismiss()
        }

        btnCancelar.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun mostrarDialogoCarga(): AlertDialog {

        val view = layoutInflater.inflate(
            R.layout.login_dialog_loading,
            null
        )

        val dialog =
            AlertDialog.Builder(
                this,
                R.style.ThemeOverlay_Comunic_AlertDialog
            )
                .setView(view)
                .create()

        dialog.setCancelable(false)
        dialog.window?.setBackgroundDrawable(
            ColorDrawable(Color.TRANSPARENT)
        )

        dialog.show()

        return dialog
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

        //val menu = navigationView.menu
        val authSessionManager = AuthSessionManager(this)
        val email = authSessionManager.getCurrentEmail()
        Log.d(
            "SESSION_DEBUG",
            "email=${authSessionManager.getCurrentEmail()}"
        )
        val nombre = sessionManager.getDisplayName()

//        if (!email.isNullOrBlank()) {
//
//            menu.findItem(R.id.nav_cuenta_info)?.title =  if (!nombre.isNullOrBlank()) nombre else "Cuenta activa"
//            menu.findItem(R.id.nav_cuenta_email)?.title =  email
//            menu.findItem(R.id.nav_cuenta_email)?.isVisible =true
//            menu.findItem(R.id.nav_sincronizar)?.isVisible = true
//            menu.findItem(R.id.nav_download)?.isVisible = true
//            menu.findItem(R.id.nav_cuenta_accion)?.title =  "Cerrar sesión"
//        } else {
//            menu.findItem(R.id.nav_cuenta_info)?.title =  "Sin iniciar sesión"
//            menu.findItem(R.id.nav_cuenta_email)?.isVisible = false
//            menu.findItem(R.id.nav_sincronizar)?.isVisible = false
//            menu.findItem(R.id.nav_download)?.isVisible = false
//            menu.findItem(R.id.nav_cuenta_accion)?.title = "Iniciar sesión / Crear cuenta"
//        }
        if (!email.isNullOrBlank()) {

            txtCuenta.text =
                if (!nombre.isNullOrBlank())
                    nombre
                else
                    "Cuenta activa"

            txtEmail.text = email
            txtEmail.isVisible = true

            btnLogin.isVisible = false
            btnSignup.isVisible = false

            btnSincronizar.isVisible = true
            btnDownload.isVisible = true
            btnCerrarSesion.isVisible = true
            imgPerfil.setImageResource(R.drawable.ic_person)



        } else {

            txtCuenta.text = "Sin iniciar sesión"

            txtEmail.isVisible = false

            btnLogin.isVisible = true
            btnSignup.isVisible = true

            btnSincronizar.isVisible = false
            btnDownload.isVisible = false
            btnCerrarSesion.isVisible = false
            imgPerfil.setImageResource(R.drawable.bicom_logo2)

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

    fun iniciarEdicionMedia(item: ItemLista) {

        if (!permissionManager.canEditMedia()) {
            return
        }

        mostrarDialogoEditarMedia(item)
    }

    private fun mostrarDialogoEditarMedia(item: ItemLista) {

        val view = layoutInflater.inflate(
            R.layout.dialog_editar_sonido,
            null
        )

        val txtTitulo = view.findViewById<TextView>(
            R.id.txtTitulo
        )

        val imagePreview = view.findViewById<ImageView>(
            R.id.imagePreview
        )

        val btnCameraEditar = view.findViewById<MaterialButton>(
            R.id.btnCameraEditar
        )

        val btnVideoEditar = view.findViewById<MaterialButton>(
            R.id.btnVideoEditar
        )

        val btnGalleryEditar = view.findViewById<MaterialButton>(
            R.id.btnGalleryEditar
        )

        val editNombre = view.findViewById<EditText>(
            R.id.editNombre
        )

        val btnGuardar = view.findViewById<MaterialButton>(
            R.id.btnGuardar
        )

        val btnCancelar = view.findViewById<MaterialButton>(
            R.id.btnCancelar
        )

        val btnCerrar = view.findViewById<ImageButton>(
            R.id.btnCerrar
        )

        txtTitulo.text = "EDITAR ELEMENTO"

        editNombre.setText(item.nombre)

        if (item.esImagen) {

            Picasso.get()
                .load(item.uri)
                .fit()
                .centerCrop()
                .into(imagePreview)

        } else {

            Glide.with(this)
                .asBitmap()
                .load(item.uri)
                .frame(1000)
                .centerCrop()
                .into(imagePreview)
        }

        val dialog = AlertDialog.Builder(
            this,
            R.style.ThemeOverlay_Comunic_AlertDialog
        )
            .setView(view)
            .create()

        dialog.window?.setBackgroundDrawable(
            ColorDrawable(Color.TRANSPARENT)
        )

        imagePreview.setOnClickListener {

            mediaEnEdicion = item
            dialogEdicionMedia = dialog


        }

        btnGuardar.setOnClickListener {

            val nuevoNombre = editNombre.text
                ?.toString()
                ?.trim()
                .orEmpty()

            if (nuevoNombre.isBlank()) {
                editNombre.error = "Ingresá un nombre"
                return@setOnClickListener
            }

            guardarEdicionMedia(
                item = item,
                nuevoNombre = nuevoNombre,
                dialog = dialog
            )
        }

        btnCancelar.setOnClickListener {
            dialog.dismiss()
        }

        btnCerrar.setOnClickListener {
            dialog.dismiss()
        }

        btnCameraEditar.setOnClickListener {

            mediaEnEdicion = item
            dialogEdicionMedia = dialog

            launchImageCapture()
        }

        btnVideoEditar.setOnClickListener {

            mediaEnEdicion = item
            dialogEdicionMedia = dialog

            launchVideoCapture()
        }

        btnGalleryEditar.setOnClickListener {

            mediaEnEdicion = item
            dialogEdicionMedia = dialog

            openGallery()
        }

        dialog.show()

        val width =
            (resources.displayMetrics.widthPixels * 0.85).toInt()

        dialog.window?.setLayout(
            width,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun guardarEdicionMedia(
        item: ItemLista,
        nuevoNombre: String,
        dialog: AlertDialog
    ) {

        lifecycleScope.launch {

            val userId =
                sessionManager.getCurrentUserId()

            val mediaId = when {

                ItemKey.isMedia(item.id) ->
                    ItemKey.mediaBase(item.id)

                else -> {
                    Toast.makeText(
                        this@MainActivity,
                        "No se pudo identificar el elemento",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@launch
                }
            }

            val now = System.currentTimeMillis()

            withContext(Dispatchers.IO) {

                AppDatabase
                    .getDatabase(applicationContext)
                    .mediaDao()
                    .updateDisplayName(
                        mediaId = mediaId,
                        newName = nuevoNombre,
                        updatedAt = now,
                        userId = userId
                    )
            }

            dialog.dismiss()

            Toast.makeText(
                this@MainActivity,
                "Elemento actualizado",
                Toast.LENGTH_SHORT
            ).show()

            SyncEvents.notifyDataChanged()
//            val fragment =
//            supportFragmentManager.findFragmentById(
//                R.id.fragment_container
//            )
//
//            if (fragment is Recientes) {
//                fragment.recargarMedia()
//            }

        }
    }

    private fun copiarMediaParaEdicion(
        sourceUri: Uri,
        mediaId: String,
        mediaType: String
    ): File? {
        //esta funcion copia el archivo de media desde su ubicación original (galería o cámara) a una
        // carpeta privada de la app, usando el mediaId como
        // nombre del archivo y la extensión según el tipo de media.
        // Retorna el archivo copiado o null si hubo un error.

        return try {

            val mediaDir = File(filesDir, "media")
            if (!mediaDir.exists()) {
                mediaDir.mkdirs()
            }

            val extension = when (mediaType) {
                "image" -> "jpg"
                "video" -> "mp4"
                else -> return null
            }

            // Por ahora usamos el mismo mediaId como nombre físico.
            // Esto evita depender del nombre visible del elemento.
//            val destinationFile = File(
//                mediaDir,
//                "$mediaId.$extension"
//            )

            val version = System.currentTimeMillis()

            val destinationFile = File(
                mediaDir,
                "${mediaId}_$version.$extension"
            )
            contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destinationFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return null
            destinationFile
        } catch (e: Exception) {
            Log.e(
                "EDIT_MEDIA",
                "Error copiando media para edición",
                e
            )
            null
        }
    }

    private fun reemplazarMediaExistente(
        item: ItemLista,
        nuevaUri: Uri,
        mediaType: String,
        onComplete: (Boolean) -> Unit
    ) {

        lifecycleScope.launch {
            try {
                val userId = sessionManager.getCurrentUserId()
                val mediaId = when {
                    ItemKey.isMedia(item.id) ->
                        ItemKey.mediaBase(item.id)
                    else -> {
                        Toast.makeText(this@MainActivity, "No se pudo identificar el elemento", Toast.LENGTH_SHORT).show()
                        onComplete(false)
                        return@launch
                    }
                }
                val dao = AppDatabase.getDatabase(applicationContext).mediaDao()
                // Buscar el MediaEntity ORIGINAL
                val mediaAnterior =
                    withContext(Dispatchers.IO) {
                        dao.getById(mediaId)
                    }
                if (mediaAnterior == null) {
                    Toast.makeText(this@MainActivity, "No se encontró el elemento", Toast.LENGTH_SHORT).show()
                    onComplete(false)
                    return@launch
                }

                // -------------------------------------------------
                // 1. COPIAR EL NUEVO ARCHIVO
                // -------------------------------------------------
                val nuevoArchivo = withContext(Dispatchers.IO) {
                        copiarMediaParaEdicion(
                            sourceUri = nuevaUri,
                            mediaId = mediaId,
                            mediaType = mediaType
                        )
                    }
                if (nuevoArchivo == null) {
                    Toast.makeText(this@MainActivity, "No se pudo guardar el nuevo archivo", Toast.LENGTH_SHORT).show()
                    onComplete(false)
                    return@launch
                }

                // -------------------------------------------------
                // 2. CALCULAR HASH
                // -------------------------------------------------
                val nuevoHash =
                    withContext(Dispatchers.IO) {
                        FileHash.sha256(nuevoArchivo)
                    }
                val nuevaLocalUri = Uri.fromFile(nuevoArchivo).toString()
                val ahora = System.currentTimeMillis()

                // -------------------------------------------------
                // 3. ACTUALIZAR ROOM
                // -------------------------------------------------

                withContext(Dispatchers.IO) {
                    dao.updateMediaContent(
                        mediaId = mediaId,
                        localUri = nuevaLocalUri,
                        mediaType = mediaType,
                        contentHash = nuevoHash,
                        updatedAt = ahora,
                        userId = userId
                    )
                }

                // -------------------------------------------------
                // 4. ELIMINAR ARCHIVO ANTERIOR
                // -------------------------------------------------

                val uriAnterior = Uri.parse(mediaAnterior.localUri)
                if (uriAnterior.scheme == "file") {
                    val archivoAnterior =
                        uriAnterior.path?.let {
                            File(it)
                        }

                    if (
                        archivoAnterior != null &&
                        archivoAnterior.exists() &&
                        archivoAnterior.absolutePath !=
                        nuevoArchivo.absolutePath
                    ) {
                        archivoAnterior.delete()
                    }
                }

                Log.d(
                    "EDIT_MEDIA",
                    """
                Media reemplazado correctamente
                mediaId=$mediaId
                oldUri=${mediaAnterior.localUri}
                newUri=$nuevaLocalUri
                mediaType=$mediaType
                contentHash=$nuevoHash
                """.trimIndent()
                )

                onComplete(true)

            } catch (e: Exception) {
                Log.e("EDIT_MEDIA", "Error reemplazando media", e)
                Toast.makeText(this@MainActivity, "No se pudo reemplazar el archivo", Toast.LENGTH_SHORT).show()
                onComplete(false)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun procesarMediaSeleccionado(uri: Uri) {

        val itemEnEdicion = mediaEnEdicion

        // =========================================================
        // MODO EDICIÓN
        // =========================================================

        if (itemEnEdicion != null) {
            val mime = contentResolver.getType(uri)
            val mediaType = when {
                mime?.startsWith("image/") == true -> "image"
                mime?.startsWith("video/") == true -> "video"
                else -> {
                    Toast.makeText(this, "Formato de archivo no compatible", Toast.LENGTH_SHORT).show()
                    return
                }
            }

            reemplazarMediaExistente(
                item = itemEnEdicion,
                nuevaUri = uri,
                mediaType = mediaType
            ) { correcto ->

                if (!correcto) {
                    return@reemplazarMediaExistente
                }

                // La imagen/video nuevo se guardó correctamente.
                Toast.makeText(
                    this,
                    "Contenido actualizado",
                    Toast.LENGTH_SHORT
                ).show()

                dialogEdicionMedia?.dismiss()

                mediaEnEdicion = null
                dialogEdicionMedia = null

//                val fragment =
//                    supportFragmentManager
//                        .findFragmentById(
//                            R.id.fragment_container
//                        )
//
//                if (fragment is Recientes) {
//                    fragment.recargarMedia()
//                }

                SyncEvents.notifyDataChanged()
            }

            return
        }

        // =========================================================
        // MODO CREACIÓN
        // =========================================================

        val mime =
            contentResolver.getType(uri)

        if (mime?.startsWith("image/") == true) {

            val uriNormalizada =
                normalizarImagenParaPreview(uri)

            ingresarNombreArchivo(
                uriNormalizada,
                true
            )

        } else if (mime?.startsWith("video/") == true) {

            ingresarNombreArchivo(
                uri,
                false
            )

        } else {

            Toast.makeText(
                this,
                "Formato de archivo no compatible",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun editarCategoriaDesdeListas(cat: CategoryPreview) {
// funcion para editar una categoría desde el fragment Listas, se llama
        // desde el adaptador de la lista de categorías cuando el usuario
        // selecciona "Editar" en una categoría.
        // Se encarga de verificar permisos y mostrar el diálogo de edición.

        // Seguridad adicional
        if (!permissionManager.canEditMedia()) {
            return
        }

        if (cat.isSystem) {
            Toast.makeText(this,  "Esta lista no se puede editar", Toast.LENGTH_SHORT ).show()
            return
        }
        mostrarDialogoEditarCategoria(cat)
    }

    private fun mostrarDialogoEditarCategoria(
        cat: CategoryPreview
    ) {
        val view = layoutInflater.inflate(R.layout.dialog_editar_lista, null)

        val txtTitulo = view.findViewById<TextView>(R.id.txtTitulo)
        val btnCerrar = view.findViewById<ImageButton>( R.id.btnCerrar)
        val img1 = view.findViewById<ImageView>(R.id.img1)
        val img2 = view.findViewById<ImageView>(R.id.img2)
        val img3 = view.findViewById<ImageView>(R.id.img3)
        val img4 = view.findViewById<ImageView>(R.id.img4)
        val editNombre = view.findViewById<EditText>(R.id.editNombreLista)
        val btnGuardar = view.findViewById<MaterialButton>(R.id.btnGuardar)
        val btnCancelar = view.findViewById<MaterialButton>(R.id.btnCancelar)

        txtTitulo.text = "EDITAR LISTA"
        editNombre.setText(cat.name)

        // =========================================================
        // PREVIEW
        // =========================================================

        val imageViews = listOf(
            img1,
            img2,
            img3,
            img4
        )

        imageViews.forEach {
            it.setImageDrawable(null)
        }

        cat.previewUris
            .take(4)
            .forEachIndexed { index, uri ->

                loadCategoryPreviewInto(
                    imageViews[index],
                    uri
                )
            }

        // =========================================================
        // DIALOG
        // =========================================================

        val dialog = AlertDialog.Builder(this, R.style.ThemeOverlay_Comunic_AlertDialog)
                .setView(view)
                .create()

        dialog.window?.setBackgroundDrawable(
            ColorDrawable(Color.TRANSPARENT)
        )

        // =========================================================
        // GUARDAR
        // =========================================================

        btnGuardar.setOnClickListener {

            val nuevoNombre =
                editNombre.text
                    ?.toString()
                    ?.trim()
                    .orEmpty()

            if (nuevoNombre.isBlank()) {

                editNombre.error =
                    "Ingresá un nombre"

                return@setOnClickListener
            }

            guardarEdicionCategoria(
                categoria = cat,
                nuevoNombre = nuevoNombre,
                dialog = dialog
            )
        }

        // =========================================================
        // CANCELAR
        // =========================================================

        btnCancelar.setOnClickListener {
            dialog.dismiss()
        }

        btnCerrar.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setOnShowListener {
            val width =
                (
                        resources.displayMetrics.widthPixels * 0.85
                        ).toInt()

            dialog.window?.setLayout(
                width,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        dialog.show()
    }

    private fun loadCategoryPreviewInto(
        imageView: ImageView,
        uriOrRes: String
    ) {

        if (uriOrRes.isBlank()) {
            imageView.setImageDrawable(null)
            return
        }

        // =========================================================
        // PATH ABSOLUTO
        // =========================================================

        if (uriOrRes.startsWith("/")) {

            val file = File(uriOrRes)

            if (!file.exists()) {
                imageView.setImageDrawable(null)
                return
            }

            val ext =
                file.extension.lowercase()

            val esVideo =
                ext in listOf(
                    "mp4",
                    "mkv",
                    "avi",
                    "mov",
                    "webm"
                )

            if (esVideo) {

                Glide.with(this)
                    .asBitmap()
                    .load(file)
                    .frame(1000)
                    .centerCrop()
                    .into(imageView)

            } else {

                Picasso.get()
                    .load(file)
                    .fit()
                    .centerCrop()
                    .into(imageView)
            }

            return
        }

        // =========================================================
        // DRAWABLE
        // =========================================================

        val cleaned =
            uriOrRes
                .removePrefix("@drawable/")
                .removePrefix("drawable/")

        val isProbablyDrawableName =
            !uriOrRes.contains("://") &&
                    cleaned.matches(
                        Regex("^[a-z0-9_]+$")
                    )

        if (isProbablyDrawableName) {

            val resId =
                resources.getIdentifier(
                    cleaned,
                    "drawable",
                    packageName
                )

            if (resId != 0) {

                Picasso.get()
                    .load(resId)
                    .fit()
                    .centerCrop()
                    .into(imageView)

                return
            }
        }

        // =========================================================
        // URI
        // =========================================================

        val uri =
            try {
                Uri.parse(uriOrRes)
            } catch (
                e: Exception
            ) {
                null
            }

        if (uri == null) {
            imageView.setImageDrawable(null)
            return
        }

        if (uri.scheme == "file") {

            val path = uri.path

            if (path.isNullOrBlank()) {
                imageView.setImageDrawable(null)
                return
            }

            val file = File(path)

            if (!file.exists()) {
                imageView.setImageDrawable(null)
                return
            }

            val ext =
                file.extension.lowercase()

            val esVideo =
                ext in listOf(
                    "mp4",
                    "mkv",
                    "avi",
                    "mov",
                    "webm"
                )

            if (esVideo) {

                Glide.with(this)
                    .asBitmap()
                    .load(file)
                    .frame(1000)
                    .centerCrop()
                    .into(imageView)

            } else {

                Picasso.get()
                    .load(file)
                    .fit()
                    .centerCrop()
                    .into(imageView)
            }

            return
        }

        // =========================================================
        // CONTENT URI
        // =========================================================

        val lower =
            uriOrRes.lowercase()

        val esVideo =
            lower.endsWith(".mp4") ||
                    lower.endsWith(".mkv") ||
                    lower.endsWith(".avi") ||
                    lower.endsWith(".mov") ||
                    lower.endsWith(".webm")

        if (esVideo) {

            Glide.with(this)
                .asBitmap()
                .load(uri)
                .frame(1000)
                .centerCrop()
                .into(imageView)

        } else {

            Picasso.get()
                .load(uri)
                .fit()
                .centerCrop()
                .into(imageView)
        }
    }

    private fun guardarEdicionCategoria(
        categoria: CategoryPreview,
        nuevoNombre: String,
        dialog: AlertDialog
    ) {

        lifecycleScope.launch {

            try {

                val userId =
                    sessionManager.getCurrentUserId()

                val ahora =
                    System.currentTimeMillis()

                val dao =
                    AppDatabase
                        .getDatabase(applicationContext)
                        .categoryDao()

                val filasActualizadas =
                    withContext(Dispatchers.IO) {

                        dao.updateCategoryName(
                            categoryId = categoria.categoryId,
                            newName = nuevoNombre,
                            updatedAt = ahora,
                            userId = userId
                        )
                    }

                // ==========================================
                // Verificar que realmente se haya actualizado
                // ==========================================

                if (filasActualizadas == 0) {

                    Toast.makeText(
                        this@MainActivity,
                        "No se pudo actualizar la lista",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@launch
                }

                // ==========================================
                // ÉXITO
                // ==========================================

                dialog.dismiss()

                Toast.makeText(
                    this@MainActivity,
                    "Lista actualizada",
                    Toast.LENGTH_SHORT
                ).show()

                val fragment =
                    supportFragmentManager.findFragmentById(
                        R.id.fragment_container
                    )

                if (fragment is Listas) {
                    fragment.recargarCategorias()
                }

                // Avisar al resto de la aplicación
                SyncEvents.notifyDataChanged()

            } catch (e: Exception) {

                Log.e(
                    "EDIT_CATEGORY",
                    "Error actualizando categoría",
                    e
                )

                Toast.makeText(
                    this@MainActivity,
                    "No se pudo actualizar la lista",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }



}