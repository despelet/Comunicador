package com.comunic.fragment



import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.comunic.data.db.AppDatabase
import com.comunic.data.db.PackRepository
import com.comunic.databinding.FragmentListasBinding // <-- ajustá el paquete
import kotlinx.coroutines.launch
import androidx.recyclerview.widget.GridLayoutManager
import com.comunic.adapters.CategoriasCuadriculaAdapter
import com.comunic.CategoryPreview
import com.comunic.ItemKey
import com.comunic.ItemLista
import com.comunic.MenuHandler
import com.comunic.PickItemsDialogFragment
import com.comunic.R
import com.comunic.data.dao.CategoryDao
import com.comunic.data.entity.CategoryEntity
import com.comunic.data.entity.CategoryItemEntity
import com.comunic.data.entity.InstalledPackEntity
import com.comunic.data.entity.MediaEntity
import com.comunic.data.mappers.resolveItemKeyToItemLista
import com.comunic.interfaces.DrawerMenuConfig
import com.comunic.session.PermissionManager
import com.comunic.session.RoleAwareFragment
import com.comunic.session.SessionManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

// importá tu interfaz MenuHandler
// import com.tu.paquete.MenuHandler

class Listas : Fragment(), MenuHandler,
    DrawerMenuConfig, RoleAwareFragment {

    private var _binding: FragmentListasBinding? = null
    private val binding get() = _binding!!

    private lateinit var categoriasAdapter: CategoriasCuadriculaAdapter
    private lateinit var db: AppDatabase

    override fun onUserModeChanged() {
        if (::categoriasAdapter.isInitialized) {
            categoriasAdapter.notifyDataSetChanged()
        }
    }

    override fun configureDrawerMenu(menu: Menu) {
        menu.findItem(R.id.nav_eliminar)?.isVisible = false
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentListasBinding.inflate(inflater, container, false)
        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.fabNuevaLista.setOnClickListener {
            mostrarDialogoNuevaLista()
        }

        db = AppDatabase.getDatabase(requireContext())

        // 1) Adapter (mismo que Home) + click inteligente (habilitar pack si está deshabilitado)
        categoriasAdapter = CategoriasCuadriculaAdapter(
            emptyList(),
            onClick = { cat ->
                onCategoriaClick(cat)
            },
            onOptionsClick = { cat ->
                mostrarOpcionesCategoria(cat)
            },
            onEnableClick = { cat ->
                habilitarPackYEntrar(cat)
            },
            onLongClick = { cat ->
                mostrarOpcionesCategoria(cat)
            },
            mostrarOpciones = {
                PermissionManager(
                    SessionManager(requireContext())
                ).canDeleteCategory()
            }
        )

        // 2) Layout: grilla 2 columnas tipo Spotify
        binding.recyclerCategorias.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.recyclerCategorias.adapter = categoriasAdapter

        // 3) Cargar datos
        /*viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                PackRepository(requireContext(), db).ensureBasicPackInstalled()
            }

            observeRows().collect { allRows ->
                procesarYActualizarUI(allRows)
            }
        }*/
        viewLifecycleOwner.lifecycleScope.launch {

            withContext(Dispatchers.IO) {
                PackRepository(requireContext(), db)
                    .ensureBasicPackInstalled()
            }

            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                observeRows().collect { allRows ->
                    procesarYActualizarUI(allRows)
                }
            }
        }
    }

    private fun observeRows() : Flow<List<List<CategoryDao.CategoryPreviewKeyRowList>>> {

        val userId =
            SessionManager(requireContext())
                .getCurrentUserId()

        return combine(
            db.categoryDao()
                .getCategoryPreviewKeyRowsForListScreen(
                    0,
                    userId
                ),

            db.categoryDao()
                .getCategoryPreviewKeyRowsForListScreen(
                    1,
                    userId
                ),

            db.categoryDao()
                .getCategoryPreviewKeyRowsForListScreen(
                    2,
                    userId
                ),

            db.categoryDao()
                .getCategoryPreviewKeyRowsForListScreen(
                    3,
                    userId
                )

        ) { r0, r1, r2, r3 ->

            listOf(
                r0,
                r1,
                r2,
                r3
            )
        }
    }

    private fun procesarYActualizarUI(allRows: List<List<CategoryDao.CategoryPreviewKeyRowList>>) {

        val (rows0, rows1, rows2, rows3) = allRows

        data class CatAgg(
            val categoryId: String,
            val name: String,
            val isSystem: Boolean,
            val packId: String,
            val packEnabled: Boolean,
            val keys: MutableList<String> = mutableListOf()
        )

        val byCat = LinkedHashMap<String, CatAgg>()

        fun addRows(rows: List<CategoryDao.CategoryPreviewKeyRowList>) {
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
                r.itemKey?.takeIf { it.isNotBlank() }?.let {
                    agg.keys.add(it)
                }
            }
        }

        addRows(rows0)
        addRows(rows1)
        addRows(rows2)
        addRows(rows3)

        viewLifecycleOwner.lifecycleScope.launch {
            val previews = withContext(Dispatchers.IO) {
                byCat.values.map { agg ->
                    val uris = agg.keys.mapNotNull { key ->
                        resolvePreviewUriForListScreen(key)
                    }.take(4)

                    CategoryPreview(
                        categoryId = agg.categoryId,
                        name = agg.name,
                        previewUris = uris,
                        isSystem = agg.isSystem,
                        packId = agg.packId,
                        packEnabled = agg.packEnabled
                    )
                }
            }

            categoriasAdapter.submitList(previews)
        }
    }

    private fun habilitarPackYEntrar(cat: CategoryPreview) {
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val dao = db.installedPackDao()
                val existing = dao.get(cat.packId)
                if (existing == null) {
//                    dao.upsert(
//                        InstalledPackEntity(
//                            packId = cat.packId,
//                            version = 1,
//                            installedAt = System.currentTimeMillis(),
//                            enabled = true,
//                            isSystem = true
//                        )
//                    )
                    dao.insert(
                        InstalledPackEntity(
                            packId = cat.packId,
                            version = 3,
                            installedAt = System.currentTimeMillis(),
                            enabled = true,
                            isSystem = true
                        )
                    )
                } else {
                    dao.setEnabled(cat.packId, true)
                }
            }

            //procesarYActualizarUI()
            abrirCategoriaDetalle(cat.categoryId, cat.name)
        }
    }

//    private fun abrirCategoriaDetalle(categoryId: String, categoryName: String) {
//        parentFragmentManager.beginTransaction()
//            .replace(R.id.fragment_container, CategoriaDetalleFragment.newInstance(categoryId, categoryName))
//            .addToBackStack(null)
//            .commit()
//    }
private fun abrirCategoriaDetalle(categoryId: String, categoryName: String) {
    parentFragmentManager.beginTransaction()
        .replace(
            R.id.fragment_container,
            CategoriaDetalleFragment.newInstance(categoryId, categoryName)
        )
        .addToBackStack(null)
        .commit()
}

    private fun onCategoriaClick(cat: CategoryPreview) {
        abrirCategoriaDetalle(cat.categoryId, cat.name) // entra siempre
    }

    private fun mostrarDialogoNuevaLista() {
        val input = com.google.android.material.textfield.TextInputEditText(requireContext())
        input.hint = "Nombre de la lista"

        AlertDialog.Builder(requireContext(), R.style.ThemeOverlay_Comunic_AlertDialog)
            .setTitle("Nueva lista")
            .setView(input)
            .setPositiveButton("Crear") { _, _ ->
                val nombre = input.text?.toString()?.trim().orEmpty()
                if (nombre.isBlank()) {
                    Toast.makeText(requireContext(), "El nombre no puede estar vacío", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                //crearLista(nombre)
                crearListaYSeleccionar(nombre)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /// crear lista vacia
    private fun crearLista(nombre: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val now = System.currentTimeMillis()
            val newId = "user_" + java.util.UUID.randomUUID().toString()

            val order = withContext(Dispatchers.IO) {
                db.categoryDao().getMaxCategoryOrderIndex() + 1
            }

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

            //cargarCategorias()
        }
    }

    private fun crearListaYSeleccionar(nombre: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val userId =
                SessionManager(requireContext())
                    .getCurrentUserId()
            val now = System.currentTimeMillis()
            val newId = "user_" + java.util.UUID.randomUUID().toString()

            val order = withContext(Dispatchers.IO) {
                db.categoryDao().getMaxCategoryOrderIndex() + 1
            }

            // 1) Crear categoría
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

            // 2) Abrir selector de elementos existentes (tipo exportar)
            val disponibles = withContext(Dispatchers.IO) {
                com.comunic.data.mappers.loadAllAvailableItems(requireContext(), db)
            }

            (activity as? MainActivity)?.pendingCategoryId = newId

            PickItemsDialogFragment(disponibles) { selected ->
                viewLifecycleOwner.lifecycleScope.launch {
                    // 3) Insertar seleccionados como itemKey dentro de la categoría nueva
                    withContext(Dispatchers.IO) {
                        var next = db.categoryDao().getMaxOrderIndex(newId, userId) + 1
                        selected.forEach { item ->
                            db.categoryDao().insertCategoryItem(
                                CategoryItemEntity(
                                    placementId = UUID.randomUUID().toString(),
                                    categoryId = newId,
                                    itemKey = item.id,   // ✅ item.id = itemKey
                                    orderIndex = next++,
                                    createdAt = now,
                                    updatedAt = now,
                                    ownerUserId =
                                    SessionManager(requireContext())
                                        .getCurrentUserId()
                                )
                            )
                        }
                    }

                    // 4) Refrescar grilla y abrir detalle
                   // cargarCategorias()
                    abrirCategoriaDetalle(newId, nombre)
                }
            }.show(parentFragmentManager, "PickItemsCreate")
        }
    }

    override fun abrirSelectorDeImagen() {
        Toast.makeText(requireContext(), "Esta sección todavía no está disponible", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private suspend fun resolvePreviewUriForListScreen(key: String): String? {
        return when {
            key.startsWith("PIC:") -> {
                val pictoId = key.removePrefix("PIC:")
                db.pictogramDao().getPictoUiById(pictoId)?.imageUri
            }
            key.startsWith("MED:") -> {
                resolveItemKeyToItemLista(requireContext(), key)?.uri?.toString()
            }
            else -> null
        }
    }

    private fun mostrarOpcionesCategoria(cat: CategoryPreview) {

        Log.d("LISTAS_DEBUG", "LongClick cat=${cat.name} id=${cat.categoryId} isSystem=${cat.isSystem} packId=${cat.packId} packEnabled=${cat.packEnabled}")

        if (cat.isSystem) {
            val titulo = cat.name
            val opciones = if (cat.packEnabled) {
                arrayOf("Desactivar pack")
            } else {
                arrayOf("Habilitar pack")
            }


            AlertDialog.Builder(requireContext(), R.style.ThemeOverlay_Comunic_AlertDialog)
                .setTitle(titulo)
                .setItems(opciones) { _, which ->
                    when {
                        cat.packEnabled -> deshabilitarPack(cat.packId)
                        else -> habilitarPack(cat.packId)
                    }
                }
                .setNegativeButton("Cancelar", null)
                .show()

            return
        }

        // USER: eliminar lista
        if (!puedeEliminarListas()) return

        AlertDialog.Builder(requireContext(), R.style.ThemeOverlay_Comunic_AlertDialog)
            .setTitle(cat.name)
            .setItems(arrayOf("Eliminar lista")) { _, _ ->

                AlertDialog.Builder(requireContext(), R.style.ThemeOverlay_Comunic_AlertDialog)
                    .setTitle("Eliminar lista")
                    .setMessage("¿Seguro que querés eliminar \"${cat.name}\"?")
                    .setPositiveButton("Eliminar") { _, _ ->
                        eliminarCategoria(cat.categoryId)
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun deshabilitarPack(packId: String) {
        if (packId.isBlank()) return
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val dao = db.installedPackDao()
                val existing = dao.get(packId)
                if (existing == null) {
//                    dao.upsert(
//                        InstalledPackEntity(
//                            packId = packId,
//                            version = 1,
//                            installedAt = System.currentTimeMillis(),
//                            enabled = false,
//                            isSystem = true
//                        )
//                    )
                    dao.insert(
                        InstalledPackEntity(
                            packId = packId,
                            version = 3,
                            installedAt = System.currentTimeMillis(),
                            enabled = false,
                            isSystem = true
                        )
                    )
                } else {
                    dao.setEnabled(packId, false)
                }
                Log.d("PACK_DEBUG", "after disable: basic_core=${dao.isEnabled("basic_core")} basic_food=${dao.isEnabled("basic_food")}")
            }
            //cargarCategorias()
        }
    }

    private fun habilitarPack(packId: String) {
        if (packId.isBlank()) return
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val dao = db.installedPackDao()
                val existing = dao.get(packId)
                if (existing == null) {
//                    dao.upsert(
//                        InstalledPackEntity(
//                            packId = packId,
//                            version = 1,
//                            installedAt = System.currentTimeMillis(),
//                            enabled = true,
//                            isSystem = true
//                        )
//                    )
                    dao.insert(
                        InstalledPackEntity(
                            packId = packId,
                            version = 3,
                            installedAt = System.currentTimeMillis(),
                            enabled = false,
                            isSystem = true
                        )
                    )
                } else {
                    dao.setEnabled(packId, true)
                }
                Log.d("PACK_DEBUG", "after disable: basic_core=${dao.isEnabled("basic_core")} basic_food=${dao.isEnabled("basic_food")}")
            }
            //cargarCategorias()
        }
    }

    private fun eliminarCategoria(categoryId: String) {
        viewLifecycleOwner.lifecycleScope.launch {

            withContext(Dispatchers.IO) {
                db.categoryDao().softDeleteUserCategory(categoryId)
            }

            //cargarCategorias() // refresca UI
        }
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

    fun importarArchivos() {

        val dialogView = layoutInflater.inflate(
            R.layout.imp_dialog_importar,
            null
        )

        val dialog = androidx.appcompat.app.AlertDialog.Builder(
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

    private fun importarListaDesdeZip(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val userId =
                SessionManager(requireContext())
                    .getCurrentUserId()
            try {

                val inputStream =  requireContext().contentResolver.openInputStream(uri)  ?: return@launch

                val zipInputStream =ZipInputStream(BufferedInputStream(inputStream))

                val mediaDir = File(requireContext().filesDir, "media")
                if (!mediaDir.exists()) mediaDir.mkdirs()
                    data class CategoriaImportada(
                        val originalFolder: String,
                        var nombre: String,
                        val categoryId: String
                    )

                val categoriasMap =  mutableMapOf<String, CategoriaImportada>()

                val itemsImportados =mutableListOf<ItemLista>()

                var entry: ZipEntry?

                while (zipInputStream.nextEntry.also { entry = it } != null) {
                    val entryName = entry!!.name
                    if (entry!!.isDirectory) continue

                    // =========================
                    // METADATA
                    // =========================

                    if (entryName.endsWith("metadata.json")) {

                        val folderName =  entryName.substringBefore("/")

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

                            val dialogView = layoutInflater.inflate( R.layout.imp_dialog_input,null)

                            val dialog = androidx.appcompat.app.AlertDialog.Builder(  requireContext(), R.style.ThemeOverlay_Comunic_AlertDialog )
                                .setView(dialogView)
                                .create()

                            val txtTitulo = dialogView.findViewById<TextView>(R.id.txtTitulo)
                            val editText = dialogView.findViewById<TextInputEditText>(R.id.editText)
                            val btnAceptar =dialogView.findViewById<MaterialButton>(R.id.btnAceptar)
                            val btnCancelar =  dialogView.findViewById<MaterialButton>(R.id.btnCancelar)

                            txtTitulo.text = "IMPORTAR LISTA"
                            editText.setText(nombreOriginal)
                            btnAceptar.setOnClickListener {nombreFinal =editText.text ?.toString() ?.trim() .orEmpty()
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
                                val dialogView = layoutInflater.inflate(R.layout.imp_dialog_acciones_vertical,null)

                                val dialog = androidx.appcompat.app.AlertDialog.Builder( requireContext(),  R.style.ThemeOverlay_Comunic_AlertDialog  )
                                    .setView(dialogView)
                                    .create()

                                val txtTitulo = dialogView.findViewById<TextView>(R.id.txtTitulo)
                                val btn1 =  dialogView.findViewById<MaterialButton>(R.id.btnAccion1)
                                val btn2 = dialogView.findViewById<MaterialButton>(R.id.btnAccion2)
                                val btn3 = dialogView.findViewById<MaterialButton>(R.id.btnAccion3)
                                val btn4 = dialogView.findViewById<MaterialButton>(R.id.btnAccion4)
                                val btnCancelar = dialogView.findViewById<MaterialButton>(R.id.btnCancelar)
                                txtTitulo.text ="La lista \"$nombreFinal\" ya existe"
                                btn1.text =  "REEMPLAZAR LISTA EXISTENTE"
                                btn2.text = "CONSERVAR AMBAS"
                                btn3.text ="RENOMBRAR NUEVA LISTA"
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
                                    val renameView =
                                        layoutInflater.inflate(R.layout.imp_dialog_input, null)
                                    val renameDialog = androidx.appcompat.app.AlertDialog.Builder(
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
                                    txtTituloRename.text = "NUEVO NOMBRE"

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

                        val nuevaCategoriaId =
                            "user_" + UUID.randomUUID()

                        val order =
                            db.categoryDao()
                                .getMaxCategoryOrderIndex() + 1

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

                    /*if (archivoDestino.exists()) {
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
                    }*/
                    if (archivoDestino.exists()) {

                        val uriExistente =  Uri.fromFile(archivoDestino)

                        var mediaExistente =db.mediaDao() .getActiveByDisplayName(  nombreSinExtension )
                        if (mediaExistente == null) {

                            val now =  System.currentTimeMillis()

                            mediaExistente = MediaEntity(
                                mediaId = UUID.randomUUID().toString(),
                                displayName = nombreSinExtension,
                                localUri = uriExistente.toString(),
                                mediaType =if (esImagen) "image" else "video",
                                createdAt =archivoDestino.lastModified()  .takeIf { it > 0L } ?: now,
                                updatedAt = now,
                                isDeleted = false,
                                ownerUserId =
                                SessionManager(requireContext())
                                    .getCurrentUserId()
                            )

                            db.mediaDao().upsert( mediaExistente )
                        }

                        val itemExistente = ItemLista(
                            id = ItemKey.media(
                                mediaExistente.mediaId
                            ),
                            nombre =mediaExistente.displayName,
                            uri = uriExistente,
                            esImagen = esImagen,
                            timestamp = mediaExistente.createdAt
                        )
                        val now = System.currentTimeMillis()
                        val nextOrder =
                            db.categoryDao() .getMaxOrderIndex( categoria.categoryId , userId) + 1

                        db.categoryDao().insertCategoryItem(
                            CategoryItemEntity(
                                placementId =UUID.randomUUID().toString(),
                                categoryId =  categoria.categoryId,
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

//                    val item = ItemLista(
//                        id = ItemKey.media(nombreSinExtension),
//                        nombre = nombreSinExtension,
//                        uri = uriGuardado,
//                        esImagen = esImagen,
//                        timestamp = System.currentTimeMillis()
//                    )

                    val now = System.currentTimeMillis()

                    val media = MediaEntity(
                        mediaId = UUID.randomUUID().toString(),
                        displayName = nombreSinExtension,
                        localUri = uriGuardado.toString(),
                        mediaType =
                        if (esImagen) "image" else "video",
                        createdAt = now,
                        updatedAt = now,
                        isDeleted = false,
                        ownerUserId =
                        SessionManager(requireContext())
                            .getCurrentUserId()
                    )

                    db.mediaDao().upsert(media)

                    val item = ItemLista(
                        id = ItemKey.media(
                            media.mediaId
                        ),
                        nombre =
                        media.displayName,
                        uri = uriGuardado,
                        esImagen = esImagen,
                        timestamp =
                        media.createdAt
                    )

                    itemsImportados.add(item)

                    saveMediaData(
                        nombreSinExtension,
                        uriGuardado,
                        esImagen
                    )

                    val nextOrder =
                        db.categoryDao()
                            .getMaxOrderIndex(categoria.categoryId, userId) + 1

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

    private fun saveMediaData(nombreArchivo: String, mediaUri: Uri, isImage: Boolean) {
        val sharedPreferences = requireContext().getSharedPreferences("media_data", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString(nombreArchivo, mediaUri.toString())
        editor.putBoolean("$nombreArchivo|type", isImage) // Guardar si es imagen o video
        editor.apply()
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

    fun importarElementosDesdeZip(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {

            try {
                // Abrir el archivo ZIP
                val inputStream = requireContext().contentResolver.openInputStream(uri) ?: return@launch
                val zipInputStream = ZipInputStream(BufferedInputStream(inputStream))

                val mediaDir = File(
                    requireContext().filesDir,
                    "media"
                ) // Carpeta "media" en el almacenamiento interno
                if (!mediaDir.exists()) {
                    mediaDir.mkdirs() // Crear la carpeta si no existe
                }

                var entry: ZipEntry?
                while (zipInputStream.nextEntry.also { entry = it } != null) {
                    val extension = entry!!.name.substringAfterLast(".", "")
                        .lowercase(Locale.ROOT) // valido extension de archivo
                    val esImagen = when (extension) {
                        in listOf("jpg", "jpeg", "png", "gif", "bmp", "webp") -> true
                        in listOf("mp4", "mkv", "avi", "mov", "webm") -> false
                        else -> {
                            withContext(Dispatchers.Main) {
                                mostrarSnackbar(
                                    "Archivo no soportado: $extension",
                                    false
                                )
                            }
                            continue
                        }
                    }

                    val nombreSinExtension = entry!!.name.substringBeforeLast(".")
                    val nombreFinal = "$nombreSinExtension.${if (esImagen) "jpg" else "mp4"}" // le doy la extension final, el nombre es el q levanta sin la extension

                    val archivoDestino = File(mediaDir, nombreFinal)

                    if (archivoDestino.exists()) { // para evitar sobreescribir archivos
                        withContext(Dispatchers.Main) {
                            mostrarSnackbar(
                                "Ya existe un archivo llamado $nombreSinExtension",
                                false
                            )
                        }
                        continue
                    }
                    // extraigo zip y lo guardo en la carpeta media (archivoDestino me lleva a mediaDir)
                    val outputStream = FileOutputStream(archivoDestino)
                    zipInputStream.copyTo(outputStream)
                    zipInputStream.closeEntry()
                    outputStream.close()

                    val uriGuardado = Uri.fromFile(archivoDestino)

//                    val item = ItemLista(
//                        id = ItemKey.media(nombreSinExtension),
//                        nombre = nombreSinExtension,
//                        uri = uriGuardado,
//                        esImagen = esImagen,
//                        timestamp = System.currentTimeMillis()
//                    )
                    val now =  System.currentTimeMillis()

                    val media = MediaEntity(
                        mediaId = UUID.randomUUID().toString(),
                        displayName = nombreSinExtension,
                        localUri = uriGuardado.toString(),
                        mediaType =
                        if (esImagen) "image" else "video",
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

                    saveMediaData(
                        nombreSinExtension,
                        uriGuardado,
                        esImagen
                    ) // guardo en el almacenamiento persistente (sharedPreferences)
                }

                zipInputStream.close()
                withContext(Dispatchers.Main) {
                    mostrarSnackbar(
                        "Importación exitosa",
                        true
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    mostrarSnackbar(
                        "Error al importar ZIP",
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

    private fun puedeEliminarListas(): Boolean {

        val sessionManager = SessionManager(requireContext())
        val permissionManager =PermissionManager(sessionManager)

        if (!permissionManager.canDeleteCategory()) {
            Toast.makeText(
                requireContext(),
                "No tenés permisos para eliminar listas",
                Toast.LENGTH_SHORT
            ).show()
            return false
        }
        return true
    }

    companion object {
        private const val REQUEST_CODE_IMPORTAR_ELEMENTOS = 5001
        private const val REQUEST_CODE_IMPORTAR_LISTA = 5002
    }
}

