package com.comunic.fragment



import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.comunic.data.db.AppDatabase
import com.comunic.data.db.PackRepository
import com.comunic.databinding.FragmentListasBinding // <-- ajustá el paquete
import kotlinx.coroutines.launch
import androidx.recyclerview.widget.GridLayoutManager
import com.comunic.adapters.CategoriasCuadriculaAdapter
import com.comunic.CategoryPreview
import com.comunic.MenuHandler
import com.comunic.PickItemsDialogFragment
import com.comunic.R
import com.comunic.data.dao.CategoryDao
import com.comunic.data.entity.CategoryEntity
import com.comunic.data.entity.CategoryItemEntity
import com.comunic.data.entity.InstalledPackEntity
import com.comunic.data.mappers.resolveItemKeyToItemLista
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.util.UUID

// importá tu interfaz MenuHandler
// import com.tu.paquete.MenuHandler

class Listas : Fragment(), MenuHandler {

    private var _binding: FragmentListasBinding? = null
    private val binding get() = _binding!!

    private lateinit var categoriasAdapter: CategoriasCuadriculaAdapter
    private lateinit var db: AppDatabase

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
            }
        )

        // 2) Layout: grilla 2 columnas tipo Spotify
        binding.recyclerCategorias.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.recyclerCategorias.adapter = categoriasAdapter

        // 3) Cargar datos
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                PackRepository(requireContext(), db).ensureBasicPackInstalled()
            }

            observeRows().collect { allRows ->
                procesarYActualizarUI(allRows)
            }
        }
    }

    private fun observeRows() = combine(
        db.categoryDao().getCategoryPreviewKeyRowsForListScreen(0),
        db.categoryDao().getCategoryPreviewKeyRowsForListScreen(1),
        db.categoryDao().getCategoryPreviewKeyRowsForListScreen(2),
        db.categoryDao().getCategoryPreviewKeyRowsForListScreen(3)
    ) { r0, r1, r2, r3 ->
        listOf(r0, r1, r2, r3)
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
                        createdAt = now
                    )
                )
            }

            //cargarCategorias()
        }
    }

    private fun crearListaYSeleccionar(nombre: String) {
        viewLifecycleOwner.lifecycleScope.launch {
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
                        createdAt = now
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
                        var next = db.categoryDao().getMaxOrderIndex(newId) + 1
                        selected.forEach { item ->
                            db.categoryDao().insertCategoryItem(
                                CategoryItemEntity(
                                    placementId = UUID.randomUUID().toString(),
                                    categoryId = newId,
                                    itemKey = item.id,   // ✅ item.id = itemKey
                                    orderIndex = next++
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



}

