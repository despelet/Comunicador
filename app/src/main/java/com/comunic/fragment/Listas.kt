package com.comunic.fragment



import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.comunic.adapters.CategoriasCuadriculaAdapter
import com.comunic.CategoryPreview
import com.comunic.ItemKey
import com.comunic.ItemLista
import com.comunic.MenuHandler
import com.comunic.PickItemsDialogFragment
import com.comunic.R
import com.comunic.adapters.ExportItemsAdapter
import com.comunic.adapters.ExportListasAdapter
import com.comunic.adapters.ResumenExportacionAdapter
import com.comunic.data.dao.CategoryDao
import com.comunic.data.entity.CategoryEntity
import com.comunic.data.entity.CategoryItemEntity
import com.comunic.data.entity.InstalledPackEntity
import com.comunic.data.entity.MediaEntity
import com.comunic.data.mappers.resolveItemKeyToItemLista
import com.comunic.export.exportitems.ExportManager
import com.comunic.export.exportlista.ExportCategoryItem
import com.comunic.export.exportlista.ExportMediaMetadata
import com.comunic.interfaces.DrawerMenuConfig
import com.comunic.session.PermissionManager
import com.comunic.session.RoleAwareFragment
import com.comunic.session.SessionManager
import com.comunic.utils.FileHash
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import com.google.gson.Gson
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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

    private var modoEdicionActivo = false
    fun activarModoEdicion() {
        modoEdicionActivo = true
        Toast.makeText(requireContext(), "Seleccione la lista que desea editar", Toast.LENGTH_SHORT).show()
    }
    fun cancelarModoEdicion() {
        modoEdicionActivo = false
    }

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
    val fragment = CategoriaDetalleFragment.newInstance(categoryId, categoryName)
    parentFragmentManager.beginTransaction()
        .replace(
            R.id.fragment_container,
            fragment
        )
        .addToBackStack(null)
        .commit()

    (requireActivity() as? MainActivity)
        ?.actualizarMenuLateralParaFragment(fragment)
    }

    private fun onCategoriaClick(cat: CategoryPreview) {

        // =========================
        // MODO EDICIÓN
        // =========================

        if (modoEdicionActivo) {
            // Las listas del sistema no se pueden editar
            if (cat.isSystem) { Toast.makeText(requireContext(), "Esta lista no se puede editar", Toast.LENGTH_SHORT).show()
                cancelarModoEdicion()
                return
            }

            // Le informamos a MainActivity qué lista seleccionó
            (activity as? MainActivity)?.editarCategoriaDesdeListas(cat)

            cancelarModoEdicion()
            return
        }

        // =========================
        // MODO NORMAL
        // =========================

        abrirCategoriaDetalle(
            cat.categoryId,
            cat.name
        )
    }

    private fun mostrarDialogoNuevaLista() {
        val view = layoutInflater.inflate(R.layout.dialog_nueva_lista, null)
        val editNombre = view.findViewById<TextInputEditText>(R.id.editNombreLista)
        val btnCrear = view.findViewById<MaterialButton>(R.id.btnCrear)
        val btnCancelar = view.findViewById<MaterialButton>(R.id.btnCancelar)
        val btnCerrar = view.findViewById<ImageButton>(R.id.btnCerrar)

        val dialog = AlertDialog.Builder(
            requireContext(),
            R.style.ThemeOverlay_Comunic_AlertDialog
        )
            .setView(view)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        btnCrear.setOnClickListener {
            val nombre = editNombre.text?.toString()?.trim().orEmpty()
            if (nombre.isBlank()) {
                editNombre.error = "Ingresá un nombre"
                return@setOnClickListener
            }
            crearListaYSeleccionar(nombre)
            dialog.dismiss()
        }

        btnCancelar.setOnClickListener {
            dialog.dismiss()
        }

        btnCerrar.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
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
            val userId = SessionManager(requireContext()).getCurrentUserId()
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
                            if (ItemKey.isMedia(item.id) && !ItemKey.isMediaUuid(item.id)) {
                                Log.e(
                                    "ITEM_KEY_VALIDATION",
                                    "Intento de importar MED legacy nuevo en Listas: ${item.id}"
                                )
                            }
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

        Log.d(
            "LISTAS_DEBUG",
            "LongClick cat=${cat.name} id=${cat.categoryId} isSystem=${cat.isSystem} packId=${cat.packId} packEnabled=${cat.packEnabled}"
        )

        if (!cat.isSystem && !puedeEliminarListas()) return

        // =========================
        // DIÁLOGO DE OPCIONES
        // =========================

        val opcionesView = layoutInflater.inflate(
            R.layout.dialog_opciones_categoria,
            null
        )

        val txtTitulo = opcionesView.findViewById<TextView>(R.id.txtTitulo)
        val txtAccion = opcionesView.findViewById<TextView>(R.id.txtAccion)
        val imgAccion = opcionesView.findViewById<ImageView>(R.id.imgAccion)
        val opcionPrincipal = opcionesView.findViewById<View>(R.id.opcionPrincipal)
        val btnCancelar = opcionesView.findViewById<MaterialButton>(R.id.btnCancelar)
        val btnCerrar = opcionesView.findViewById<ImageButton>(R.id.btnCerrar)

        txtTitulo.text = cat.name

        if (cat.isSystem) {
            if (cat.packEnabled) {
                txtAccion.text = "Deshabilitar pack"
                imgAccion.setImageResource(R.drawable.ic_visibility_off)
            } else {
                txtAccion.text = "Habilitar pack"
                imgAccion.setImageResource(R.drawable.ic_check_circle)
            }
        } else {
            txtAccion.text = "Eliminar lista"
            imgAccion.setImageResource(R.drawable.ic_delete)
        }

        val dialogOpciones = AlertDialog.Builder(
            requireContext(),
            R.style.ThemeOverlay_Comunic_AlertDialog
        )
            .setView(opcionesView)
            .create()

        dialogOpciones.show()

        dialogOpciones.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialogOpciones.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.85).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        opcionPrincipal.setOnClickListener {

            dialogOpciones.dismiss()

            // =========================
            // PACK
            // =========================

            if (cat.isSystem) {

                if (cat.packEnabled) {
                    deshabilitarPack(cat.packId)
                } else {
                    habilitarPack(cat.packId)
                }

                return@setOnClickListener
            }

            // =========================
            // CONFIRMAR ELIMINAR LISTA
            // =========================

            val confirmarView = layoutInflater.inflate(
                R.layout.dialog_confirmar_eliminar_lista,
                null
            )

            val txtMensaje =
                confirmarView.findViewById<TextView>(R.id.txtMensaje)

            val btnEliminar =
                confirmarView.findViewById<MaterialButton>(R.id.btnEliminar)

            val btnCancelar2 =
                confirmarView.findViewById<MaterialButton>(R.id.btnCancelar)

            val btnCerrar2 =
                confirmarView.findViewById<ImageButton>(R.id.btnCerrar)

            txtMensaje.text =
                "¿Seguro que querés eliminar la lista\n\n\"${cat.name}\"?\n\nEsta acción no se puede deshacer."

            val dialogEliminar = AlertDialog.Builder(
                requireContext(),
                R.style.ThemeOverlay_Comunic_AlertDialog
            )
                .setView(confirmarView)
                .create()

            dialogEliminar.show()

            dialogEliminar.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            dialogEliminar.window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.85).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            btnEliminar.setOnClickListener {
                dialogEliminar.dismiss()
                eliminarCategoria(cat.categoryId)
            }

            btnCancelar2.setOnClickListener {
                dialogEliminar.dismiss()
            }

            btnCerrar2.setOnClickListener {
                dialogEliminar.dismiss()
            }
        }

        btnCancelar.setOnClickListener {
            dialogOpciones.dismiss()
        }

        btnCerrar.setOnClickListener {
            dialogOpciones.dismiss()
        }
    }

    private fun deshabilitarPack(packId: String) {
        if (packId.isBlank()) return
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val dao = db.installedPackDao()
                val existing = dao.get(packId)
                if (existing == null) {
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
            val userId=SessionManager(requireContext()) .getCurrentUserId()

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

                val categoriasMap = mutableMapOf<String, CategoriaImportada>()
                val itemsImportados = mutableListOf<ItemLista>()
                val metadataItems = mutableMapOf<String, ExportCategoryItem>()

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

                        val version =
                            obj.optInt("version", 1)

                        if (version >= 2) {
                            val itemsJson = obj.optJSONArray("items")
                            if (itemsJson != null) {
                                for (i in 0 until itemsJson.length()) {
                                    val itemObj = itemsJson.getJSONObject(i)
                                    val itemKey = itemObj.optString("itemKey")

                                    if (
                                        itemKey.isNotBlank()
                                    ) {
                                        val exportItem =
                                            ExportCategoryItem(
                                                itemKey = itemKey,
                                                orderIndex =
                                                itemObj.optInt(
                                                    "orderIndex",
                                                    i
                                                ),
                                                displayName =
                                                itemObj.optString(
                                                    "displayName",
                                                    null
                                                ),
                                                mediaType =
                                                itemObj.optString(
                                                    "mediaType",
                                                    null
                                                )
                                            )

                                        metadataItems[itemKey] =
                                            exportItem
                                    }
                                }
                            }
                        }

                        val nombreOriginal = obj.optString("name", "Lista importada")

                        var nombreFinal = nombreOriginal
                        var reemplazar = false

                        var cancelado = false

                        withContext(Dispatchers.Main) {

                            val deferred = CompletableDeferred<Unit>()

                            val dialogView = layoutInflater.inflate(
                                R.layout.imp_dialog_input,
                                null
                            )

                            val dialog = androidx.appcompat.app.AlertDialog.Builder(
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
//                        val userId =
//                            SessionManager(requireContext())
//                                .getCurrentUserId()

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

                                val dialog = androidx.appcompat.app.AlertDialog.Builder(
                                    requireContext(),
                                    R.style.ThemeOverlay_Comunic_AlertDialog
                                )
                                    .setView(dialogView)
                                    .create()
                                val txtTitulo = dialogView.findViewById<TextView>(R.id.txtTitulo)
                                val btn1 = dialogView.findViewById<MaterialButton>(R.id.btnAccion1)
                                val btn2 = dialogView.findViewById<MaterialButton>(R.id.btnAccion2)
                                val btn3 = dialogView.findViewById<MaterialButton>(R.id.btnAccion3)
                                val btn4 = dialogView.findViewById<MaterialButton>(R.id.btnAccion4)
                                val btnCancelar = dialogView.findViewById<MaterialButton>(R.id.btnCancelar)

                                txtTitulo.text = "La lista \"$nombreFinal\" ya existe"
                                btn1.text = "REEMPLAZAR LISTA EXISTENTE"
                                btn2.text = "CONSERVAR AMBAS"
                                btn3.text = "RENOMBRAR NUEVA LISTA"
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


                    if (!entryName.contains("/media/")) continue

                    val fileName = entryName.substringAfterLast("/")

                    if (!fileName.startsWith("MED_")) continue

                    val fileNameWithoutExtension = fileName.substringBeforeLast(".")
                    val mediaId = fileNameWithoutExtension.removePrefix("MED_")
                    val itemKey = ItemKey.media(mediaId)

                    if (!ItemKey.isMediaUuid(itemKey)) {
                        Log.e("IMPORT_LIST_MEDIA", "UUID inválido en ZIP: $mediaId")
                        zipInputStream.closeEntry()
                        continue
                    }

                    val metadataItem = metadataItems[itemKey]

                    if (metadataItem == null) {
                        Log.e("IMPORT_LIST_MEDIA", "No existe metadata para itemKey=$itemKey")
                        zipInputStream.closeEntry()
                        continue
                    }

                    val displayName =
                        metadataItem.displayName
                            ?: fileNameWithoutExtension.removePrefix("MED_")

                    val extension = fileName.substringAfterLast(".", "").lowercase(Locale.ROOT)

                    val mediaType = metadataItem.mediaType ?: when (extension) {
                        "jpg", "jpeg", "png", "webp", "gif", "bmp" -> "image"
                        "mp4", "mkv", "avi", "mov", "webm" -> "video"
                        else -> {
                            Log.e("IMPORT_LIST_MEDIA", "Tipo de archivo desconocido: $fileName")
                            zipInputStream.closeEntry()
                            continue
                        }
                    }

// Buscar el elemento por UUID + usuario actual
                    val mediaExistente = db.mediaDao().getByIdForUser(
                        mediaId = mediaId,
                        userId = userId
                    )

// Extraer temporalmente para calcular el hash
                    val tempFile = File(
                        mediaDir,
                        ".import_list_${mediaId}_temp.$extension"
                    )

                    FileOutputStream(tempFile).use { output ->
                        zipInputStream.copyTo(output)
                    }

                    zipInputStream.closeEntry()

                    val importedHash = FileHash.sha256(tempFile)

// ============================================================
// EL ELEMENTO YA EXISTE PARA ESTE USUARIO
// ============================================================

                    if (mediaExistente != null) {

                        if (
                            mediaExistente.contentHash.isNotBlank() &&
                            mediaExistente.contentHash == importedHash
                        ) {
                            tempFile.delete()

                            val item = ItemLista(
                                id = itemKey,
                                nombre = mediaExistente.displayName,
                                uri = Uri.parse(mediaExistente.localUri),
                                esImagen = mediaExistente.mediaType == "image",
                                timestamp = mediaExistente.createdAt
                            )

                            if (itemsImportados.none { it.id == item.id }) {
                                itemsImportados.add(item)
                            }

                            val exportItem = metadataItem
                            val orderIndex = exportItem.orderIndex

                            db.categoryDao().insertCategoryItem(
                                CategoryItemEntity(
                                    placementId = UUID.randomUUID().toString(),
                                    categoryId = categoria.categoryId,
                                    itemKey = itemKey,
                                    orderIndex = orderIndex,
                                    createdAt = System.currentTimeMillis(),
                                    updatedAt = System.currentTimeMillis(),
                                    ownerUserId = userId
                                )
                            )

                            continue
                        }

                        tempFile.delete()

                        Log.e(
                            "IMPORT_LIST_MEDIA",
                            "CONFLICTO: mismo UUID pero contenido diferente: $mediaId"
                        )

                        withContext(Dispatchers.Main) {
                            mostrarSnackbar(
                                "El elemento \"$displayName\" ya existe con contenido diferente",
                                false
                            )
                        }

                        continue
                    }

// ============================================================
// CREAR NUEVO MEDIAENTITY
// ============================================================

                    var archivoDestino = File(
                        mediaDir,
                        "$displayName.$extension"
                    )

                    if (archivoDestino.exists()) {
                        var contador = 1

                        while (archivoDestino.exists()) {
                            archivoDestino = File(
                                mediaDir,
                                "$displayName ($contador).$extension"
                            )
                            contador++
                        }
                    }

                    tempFile.renameTo(archivoDestino)

                    val uriGuardado = Uri.fromFile(archivoDestino)
                    val now = System.currentTimeMillis()

                    val media = MediaEntity(
                        mediaId = mediaId,
                        displayName = displayName,
                        localUri = uriGuardado.toString(),
                        mediaType = mediaType,
                        createdAt = now,
                        updatedAt = now,
                        isDeleted = false,
                        ownerUserId = userId,
                        contentHash = importedHash
                    )

                    db.mediaDao().upsert(media)

                    val item = ItemLista(
                        id = ItemKey.media(media.mediaId),
                        nombre = media.displayName,
                        uri = uriGuardado,
                        esImagen = media.mediaType == "image",
                        timestamp = media.createdAt
                    )

                    if (itemsImportados.none { it.id == item.id }) {
                        itemsImportados.add(item)
                    }

                    saveMediaData(
                        displayName,
                        uriGuardado,
                        mediaType == "image"
                    )

                    db.categoryDao().insertCategoryItem(
                        CategoryItemEntity(
                            placementId = UUID.randomUUID().toString(),
                            categoryId = categoria.categoryId,
                            itemKey = itemKey,
                            orderIndex = metadataItem.orderIndex,
                            createdAt = now,
                            updatedAt = now,
                            ownerUserId = userId
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

                val context = requireContext()
                val db = AppDatabase.getDatabase(context)

                val userId =
                    SessionManager(context)
                        .getCurrentUserId()

                val mediaDir =
                    File(context.filesDir, "media")

                if (!mediaDir.exists()) {
                    mediaDir.mkdirs()
                }

                /*
                 * ============================================================
                 * FASE 1 — LEER METADATA
                 * ============================================================
                 */

                data class MetadataItem(
                    val mediaId: String,
                    val displayName: String,
                    val mediaType: String
                )

                val metadataItems =
                    mutableMapOf<String, MetadataItem>()

                val inputStream =
                    context.contentResolver
                        .openInputStream(uri)
                        ?: throw Exception("No se pudo abrir el ZIP")

                ZipInputStream(
                    BufferedInputStream(inputStream)
                ).use { zip ->

                    var entry: ZipEntry?

                    while (
                        zip.nextEntry.also { entry = it } != null
                    ) {

                        if (entry!!.isDirectory) {
                            continue
                        }

                        if (
                            entry!!.name.equals(
                                "metadata.json",
                                ignoreCase = true
                            )
                        ) {

                            val json =
                                buildString {

                                    val buffer =
                                        ByteArray(1024)

                                    var len: Int

                                    while (
                                        zip.read(buffer)
                                            .also { len = it } > 0
                                    ) {
                                        append(
                                            String(
                                                buffer,
                                                0,
                                                len
                                            )
                                        )
                                    }
                                }

                            val metadata =
                                Gson().fromJson(
                                    json,
                                    ExportMediaMetadata::class.java
                                )

                            if (metadata.version != 2) {
                                throw Exception(
                                    "Versión de ZIP no compatible: ${metadata.version}"
                                )
                            }

                            metadata.items.forEach { item ->

                                metadataItems[item.mediaId] =
                                    MetadataItem(
                                        mediaId = item.mediaId,
                                        displayName = item.displayName,
                                        mediaType = item.mediaType
                                    )
                            }

                            break
                        }

                        zip.closeEntry()
                    }
                }

                /*
                 * ============================================================
                 * FASE 2 — PROCESAR ARCHIVOS
                 * ============================================================
                 */

                val itemsImportados =
                    mutableListOf<ItemLista>()

                val inputStreamArchivos =
                    context.contentResolver
                        .openInputStream(uri)
                        ?: throw Exception("No se pudo abrir el ZIP")

                ZipInputStream(
                    BufferedInputStream(inputStreamArchivos)
                ).use { zip ->

                    var entry: ZipEntry?

                    while (
                        zip.nextEntry.also { entry = it } != null
                    ) {

                        if (entry!!.isDirectory) {
                            continue
                        }

                        val entryName =
                            entry!!.name

                        /*
                         * No procesar metadata.json como archivo multimedia.
                         */
                        if (
                            entryName.equals(
                                "metadata.json",
                                ignoreCase = true
                            )
                        ) {
                            zip.closeEntry()
                            continue
                        }

                        /*
                         * Esperamos archivos dentro de:
                         *
                         * media/MED_UUID.ext
                         */
                        val fileName =
                            entryName.substringAfterLast("/")

                        if (
                            !fileName.startsWith("MED_")
                        ) {
                            zip.closeEntry()
                            continue
                        }

                        val fileNameWithoutExtension =
                            fileName.substringBeforeLast(".")

                        val mediaId =
                            fileNameWithoutExtension
                                .removePrefix("MED_")

                        /*
                         * Validar que el UUID sea realmente un UUID.
                         */
                        val itemKey =
                            ItemKey.media(mediaId)

                        if (
                            !ItemKey.isMediaUuid(itemKey)
                        ) {

                            Log.e(
                                "IMPORT_MEDIA",
                                "UUID inválido en ZIP: $mediaId"
                            )

                            zip.closeEntry()
                            continue
                        }

                        /*
                         * Buscar información del elemento
                         * en metadata.json.
                         */
                        val metadataItem =
                            metadataItems[mediaId]

                        if (metadataItem == null) {

                            Log.e(
                                "IMPORT_MEDIA",
                                "No existe metadata para mediaId=$mediaId"
                            )

                            zip.closeEntry()
                            continue
                        }

                        val extension =
                            fileName
                                .substringAfterLast(
                                    ".",
                                    ""
                                )
                                .lowercase(Locale.ROOT)

                        val extensionFinal =
                            when (metadataItem.mediaType) {

                                "image" -> "jpg"

                                "video" -> "mp4"

                                else -> {
                                    Log.e(
                                        "IMPORT_MEDIA",
                                        "Tipo multimedia desconocido: ${metadataItem.mediaType}"
                                    )

                                    zip.closeEntry()
                                    continue
                                }
                            }

                        /*
                         * Nombre físico local.
                         *
                         * El nombre del archivo no es la identidad.
                         * La identidad es mediaId.
                         */
                        val nombreArchivo =
                            "${metadataItem.displayName}.$extensionFinal"

                        var archivoDestino =
                            File(
                                mediaDir,
                                nombreArchivo
                            )

                        /*
                         * Evitar conflicto de nombre físico.
                         *
                         * Esto NO modifica el UUID.
                         */
                        if (archivoDestino.exists()) {

                            var contador = 1

                            while (archivoDestino.exists()) {

                                archivoDestino =
                                    File(
                                        mediaDir,
                                        "${metadataItem.displayName} ($contador).$extensionFinal"
                                    )

                                contador++
                            }
                        }

                        /*
                         * Extraer temporalmente el archivo.
                         */
                        val tempFile =
                            File(
                                mediaDir,
                                ".import_${mediaId}_temp.$extension"
                            )

                        FileOutputStream(tempFile).use { output ->

                            zip.copyTo(output)
                        }

                        zip.closeEntry()

                        val tempUri =
                            Uri.fromFile(tempFile)

                        val importedHash =
                            FileHash.sha256(tempUri)

                        /*
                         * ====================================================
                         * BUSCAR MEDIA EXISTENTE POR UUID
                         * ====================================================
                         */

                        val mediaExistente =
                            db.mediaDao()
                                .getByIdForUser( // es segun user
                                    mediaId = mediaId,
                                    userId = userId
                                )

                        if (mediaExistente != null) {

                            /*
                             * El UUID ya existe.
                             *
                             * Verificamos si el contenido es el mismo.
                             */

                            if (
                                mediaExistente.contentHash.isNotBlank() &&
                                mediaExistente.contentHash == importedHash
                            ) {

                                /*
                                 * Es exactamente el mismo elemento.
                                 *
                                 * No creamos otro MediaEntity.
                                 */

                                tempFile.delete()

                                Log.d(
                                    "IMPORT_MEDIA",
                                    "Media existente reutilizado: $mediaId"
                                )

                                val item = ItemLista(
                                    id = ItemKey.media(mediaId),
                                    nombre = mediaExistente.displayName,
                                    uri = Uri.parse(mediaExistente.localUri),
                                    esImagen = mediaExistente.mediaType == "image",
                                    timestamp = mediaExistente.createdAt
                                )

                                if (itemsImportados.none { it.id == item.id }) {
                                    itemsImportados.add(item)
                                }

                                continue

                            } else {

                                /*
                                 * Mismo UUID pero contenido diferente.
                                 *
                                 * Por ahora NO sobrescribimos.
                                 */
                                tempFile.delete()

                                Log.e(
                                    "IMPORT_MEDIA",
                                    "CONFLICTO: mismo UUID pero contenido diferente: $mediaId"
                                )

                                withContext(Dispatchers.Main) {

                                    mostrarSnackbar(
                                        "El elemento \"${metadataItem.displayName}\" ya existe con contenido diferente",
                                        false
                                    )
                                }

                                continue
                            }
                        }

                        /*
                         * ====================================================
                         * CREAR NUEVO MEDIAENTITY
                         * ====================================================
                         *
                         * IMPORTANTE:
                         *
                         * NO usamos UUID.randomUUID().
                         *
                         * Conservamos el UUID original.
                         */

                        if (
                            archivoDestino.exists()
                        ) {
                            archivoDestino.delete()
                        }

                        tempFile.renameTo(
                            archivoDestino
                        )

                        val uriGuardado =
                            Uri.fromFile(
                                archivoDestino
                            )

                        val now =
                            System.currentTimeMillis()

                        val media =
                            MediaEntity(
                                mediaId = mediaId,
                                displayName =
                                metadataItem.displayName,
                                localUri =
                                uriGuardado.toString(),
                                mediaType =
                                metadataItem.mediaType,
                                createdAt = now,
                                updatedAt = now,
                                isDeleted = false,
                                ownerUserId = userId,
                                contentHash = importedHash
                            )

                        db.mediaDao()
                            .upsert(media)

                        /*
                         * Crear ItemLista usando el UUID original.
                         */
                        val item = ItemLista(
                            id = ItemKey.media(media.mediaId),
                            nombre = media.displayName,
                            uri = uriGuardado,
                            esImagen = media.mediaType == "image",
                            timestamp = media.createdAt
                        )

                        if (itemsImportados.none { it.id == item.id }) {
                            itemsImportados.add(item)
                        }

                        Log.d(
                            "IMPORT_MEDIA",
                            "Media importado correctamente: UUID=${media.mediaId}, displayName=${media.displayName}"
                        )
                    }
                }

                /*
                 * ============================================================
                 * ACTUALIZAR UI
                 * ============================================================
                 */

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

    fun recargarCategorias() {
        viewLifecycleOwner.lifecycleScope.launch {
            val allRows = observeRows().first()
            procesarYActualizarUI(allRows)
        }
    }

    private suspend fun obtenerElementosExportables(): List<ItemLista> {
        return withContext(Dispatchers.IO) {
            com.comunic.data.mappers.loadAllAvailableItems(
                requireContext(),
                db
            )
        }
    }

    fun mostrarDialogoSeleccionarElementos(
        elementosIniciales: List<ItemLista>? = null,
        checkedInicial: BooleanArray? = null
    ) {

        Log.d("export_LISTAS", "mostrarDialogoSeleccionarElementos")
        viewLifecycleOwner.lifecycleScope.launch {

            val elementosDisponibles =
                elementosIniciales ?: obtenerElementosExportables()

            val checked = checkedInicial
                ?: BooleanArray(elementosDisponibles.size)

            // resto exactamente igual

            val dialogView = layoutInflater.inflate(
                R.layout.exp_dialogo_seleccion,
                null
            )

            // =========================
            // TABS
            // =========================

            val tabs =
                dialogView.findViewById<TabLayout>(
                    R.id.tabExportacion
                )

            val layoutElementos =
                dialogView.findViewById<LinearLayout>(
                    R.id.layoutExportarElementos
                )

            val layoutListas =
                dialogView.findViewById<LinearLayout>(
                    R.id.layoutExportarListas
                )

            tabs.addOnTabSelectedListener(
                object : TabLayout.OnTabSelectedListener {

                    override fun onTabSelected(
                        tab: TabLayout.Tab?
                    ) {
                        when (tab?.position) {

                            0 -> {
                                layoutElementos.visibility =
                                    View.VISIBLE

                                layoutListas.visibility =
                                    View.GONE
                            }

                            1 -> {
                                layoutElementos.visibility =
                                    View.GONE

                                layoutListas.visibility =
                                    View.VISIBLE
                            }
                        }
                    }

                    override fun onTabUnselected(
                        tab: TabLayout.Tab?
                    ) {}

                    override fun onTabReselected(
                        tab: TabLayout.Tab?
                    ) {}
                }
            )

            // =========================
            // EXPORTAR ELEMENTOS
            // =========================

            val recycler =
                dialogView.findViewById<RecyclerView>(
                    R.id.recyclerItems
                )

            val btnSeleccionarTodos =
                dialogView.findViewById<MaterialButton>(
                    R.id.btnSeleccionarTodos
                )

            val btnDeseleccionarTodos =
                dialogView.findViewById<MaterialButton>(
                    R.id.btnDeseleccionarTodos
                )

            val btnExportar =
                dialogView.findViewById<MaterialButton>(
                    R.id.btnExportar
                )

            val btnCancelar =
                dialogView.findViewById<MaterialButton>(
                    R.id.btnCancelar
                )

            val adapter = ExportItemsAdapter(
                items = elementosDisponibles,
                checked = checked
            ) { index, isChecked ->

                checked[index] = isChecked
            }

            recycler.layoutManager =
                GridLayoutManager(requireContext(), 2)

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

            // =========================
            // DIÁLOGO PRINCIPAL
            // =========================

            val dialog =
                AlertDialog.Builder(
                    requireContext(),
                    R.style.ThemeOverlay_Comunic_AlertDialog
                )
                    .setView(dialogView)
                    .create()

            btnExportar.setOnClickListener {

                val elementosSeleccionados =
                    elementosDisponibles.filterIndexed { index, _ ->
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
                    elementosDisponibles = elementosDisponibles,
                    elementosSeleccionados = elementosSeleccionados,
                    checked = checked
                )
            }

            btnCancelar.setOnClickListener {
                dialog.dismiss()
            }

            // =========================
            // EXPORTAR LISTAS
            // =========================

            configurarExportacionListas(
                dialog = dialog,
                dialogView = dialogView
            )

            dialog.show()
        }
    }

    private fun configurarExportacionListas(
        dialog: AlertDialog,
        dialogView: View
    ) {

        val recyclerListas = dialogView.findViewById<RecyclerView>(R.id.recyclerListas)
        val btnExportarListas = dialogView.findViewById<MaterialButton>(R.id.btnExportarListas)
        val btnSeleccionarTodas = dialogView.findViewById<MaterialButton>(R.id.btnSeleccionarTodas)
        val btnDeseleccionarTodas = dialogView.findViewById<MaterialButton>(R.id.btnDeseleccionarTodas)
        val btnCancelarListas = dialogView.findViewById<MaterialButton>(R.id.btnCancelarListas)


        viewLifecycleOwner.lifecycleScope.launch {

            val userId =
                SessionManager(requireContext())
                    .getCurrentUserId()

            val categorias =
                withContext(Dispatchers.IO) {
                    db.categoryDao()
                        .getUserActiveForUser(userId)
                }

            val cantidades =
                withContext(Dispatchers.IO) {
                    categorias.associate { categoria ->

                        categoria.categoryId to
                                db.categoryDao()
                                    .getItemKeysForCategory(
                                        categoria.categoryId,
                                        userId
                                    )
                                    .size
                    }
                }

            val previews = withContext(Dispatchers.IO) {
                    categorias.associate { categoria ->
                        val itemKeys = db.categoryDao().getItemKeysForCategory(categoria.categoryId, userId)
                        val previewItems = itemKeys.mapNotNull { key -> resolveItemKeyToItemLista(requireContext(), key)
                        }
                        categoria.categoryId to previewItems
                    }
                }

            val checkedListas =
                BooleanArray(categorias.size)

            val adapterListas =
                ExportListasAdapter(
                    context = requireContext(),
                    items = categorias,
                    cantidades = cantidades,
                    previews = previews,
                    checked = checkedListas
                ) { index, isChecked ->

                    checkedListas[index] = isChecked
                }

            recyclerListas.layoutManager =
                LinearLayoutManager(requireContext())

            recyclerListas.adapter =
                adapterListas

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
                        this@Listas,
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
        btnCancelarListas.setOnClickListener {
            dialog.dismiss()
        }

    }

    private fun mostrarResumenSeleccion(
        elementosDisponibles: List<ItemLista>,
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

        val dialog = androidx.appcompat.app.AlertDialog.Builder(
            requireContext(),
            R.style.ThemeOverlay_Comunic_AlertDialog
        )
            .setView(dialogView)
            .create()

        btnAgregarMas.setOnClickListener {

            dialog.dismiss()

            mostrarDialogoSeleccionarElementos(
                elementosIniciales = elementosDisponibles,
                checkedInicial = checked
            )
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

        val dialog = androidx.appcompat.app.AlertDialog.Builder( requireContext(),R.style.ThemeOverlay_Comunic_AlertDialog )
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

    companion object {
        private const val REQUEST_CODE_IMPORTAR_ELEMENTOS = 5001
        private const val REQUEST_CODE_IMPORTAR_LISTA = 5002
    }
}

