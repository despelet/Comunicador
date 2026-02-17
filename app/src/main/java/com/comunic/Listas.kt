package com.comunic



import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.comunic.data.db.AppDatabase
import com.comunic.data.db.PackRepository
import com.comunic.databinding.FragmentListasBinding // <-- ajustá el paquete
import kotlinx.coroutines.launch
import androidx.recyclerview.widget.GridLayoutManager
import com.comunic.data.entity.CategoryEntity
import com.comunic.data.entity.CategoryItemEntity
import com.comunic.data.mappers.resolveItemKeyToItemLista
import kotlinx.coroutines.Dispatchers
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

        // 1) Adapter (mismo que Home)
        categoriasAdapter = CategoriasCuadriculaAdapter(emptyList()) { cat ->
            abrirCategoriaDetalle(cat.categoryId, cat.name)
        }

        // 2) Layout: grilla 2 columnas tipo Spotify
        binding.recyclerCategorias.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.recyclerCategorias.adapter = categoriasAdapter

        // 3) Cargar datos
        cargarCategorias()
    }

//    private fun cargarCategorias() {
//        viewLifecycleOwner.lifecycleScope.launch {
//
//            withContext(Dispatchers.IO) {
//                PackRepository(requireContext(), db).ensureBasicPackInstalled()
//            }
//
////            val rows = withContext(Dispatchers.IO) {
////                db.categoryDao().getAllCategoryPreviewRows()
////            }
//            val rows = withContext(Dispatchers.IO) {
//                db.categoryDao().getAllCategoryPreviewRowsIncludingEmpty()
//            }
//
//            val previews = CategoryPreviewMapper.build(rows)
//
//            categoriasAdapter.submitList(previews)
//        }
//    }
private fun cargarCategorias() {
    viewLifecycleOwner.lifecycleScope.launch {

        withContext(Dispatchers.IO) {
            PackRepository(requireContext(), db).ensureBasicPackInstalled()
        }

        // 1) Traer para cada categoría: itemKey 0..3
        val rows0 = withContext(Dispatchers.IO) { db.categoryDao().getCategoryPreviewKeyRows(0) }
        val rows1 = withContext(Dispatchers.IO) { db.categoryDao().getCategoryPreviewKeyRows(1) }
        val rows2 = withContext(Dispatchers.IO) { db.categoryDao().getCategoryPreviewKeyRows(2) }
        val rows3 = withContext(Dispatchers.IO) { db.categoryDao().getCategoryPreviewKeyRows(3) }

        // 2) Todas las categorías (mismo orden en todos los rows)
        val byCat = LinkedHashMap<String, Pair<String, MutableList<String>>>()
        fun addRows(rows: List<CategoryPreviewKeyRow>) {
            rows.forEach { r ->
                val entry = byCat.getOrPut(r.categoryId) { r.name to mutableListOf() }
                val key = r.itemKey
                if (!key.isNullOrBlank()) entry.second.add(key)
            }
        }

        addRows(rows0)
        addRows(rows1)
        addRows(rows2)
        addRows(rows3)

        // 3) Resolver itemKeys -> ItemLista -> uri string para el adapter
        val previews = withContext(Dispatchers.IO) {
            byCat.map { (categoryId, pair) ->
                val (name, keys) = pair

                val uris = keys.mapNotNull { key ->
                    val item = resolveItemKeyToItemLista(requireContext(), key) // ✅ tu resolver
                    // usamos string para que tu adapter lo soporte (file path / uri)
                    item?.uri?.toString()
                }.take(4)

                CategoryPreview(
                    categoryId = categoryId,
                    name = name,
                    previewUris = uris
                )
            }
        }

        categoriasAdapter.submitList(previews)
    }
}

    private fun abrirCategoriaDetalle(categoryId: String, categoryName: String) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, CategoriaDetalleFragment.newInstance(categoryId, categoryName))
            .addToBackStack(null)
            .commit()
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

            cargarCategorias()
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
                    cargarCategorias()
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
}


/*class Listas : Fragment(), MenuHandler {

    private var _binding: FragmentListasBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentListasBinding.inflate(inflater, container, false)

        // Texto fijo por ahora
        binding.textoSeccionNoDisponible.text = "Sección en desarrollo"

        return binding.root
    }

    // Para que el botón "agregar" no rompa en esta sección
    override fun abrirSelectorDeImagen() {
        Toast.makeText(
            requireContext(),
            "Esta sección todavía no está disponible",
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

 */