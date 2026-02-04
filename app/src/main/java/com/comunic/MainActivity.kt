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

        // Cargar fragment por defecto
        if (savedInstanceState == null) {
            bottomNav.selectedItemId = R.id.menu_recientes
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