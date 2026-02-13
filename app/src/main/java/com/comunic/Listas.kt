package com.comunic



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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

    private fun cargarCategorias() {
        viewLifecycleOwner.lifecycleScope.launch {

            withContext(Dispatchers.IO) {
                PackRepository(requireContext(), db).ensureBasicPackInstalled()
            }

            val rows = withContext(Dispatchers.IO) {
                db.categoryDao().getAllCategoryPreviewRows()
            }

            val previews = CategoryPreviewMapper.build(rows)

            categoriasAdapter.submitList(previews)
        }
    }

    private fun abrirCategoriaDetalle(categoryId: String, categoryName: String) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, CategoriaDetalleFragment.newInstance(categoryId, categoryName))
            .addToBackStack(null)
            .commit()
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