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

// importá tu interfaz MenuHandler
// import com.tu.paquete.MenuHandler

class Listas : Fragment(), MenuHandler {

    private var _binding: FragmentListasBinding? = null
    private val binding get() = _binding!!

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

        val db = AppDatabase.getDatabase(requireContext())

        viewLifecycleOwner.lifecycleScope.launch {
            // 1) instala Pack Básico si no está
            PackRepository(requireContext(), db).ensureBasicPackInstalled()

            // 2) trae categorías
            val categorias = db.categoryDao().getAll()

            // 3) setea RecyclerView
            binding.recyclerCategorias.layoutManager = LinearLayoutManager(requireContext())
            binding.recyclerCategorias.adapter = CategoriasAdapter(categorias) { categoria ->
                // navegar a detalle
                abrirCategoriaDetalle(categoria.categoryId, categoria.name)
            }
        }
    }

    private fun abrirCategoriaDetalle(categoryId: String, categoryName: String) {
        // MVP sin Navigation Component: FragmentTransaction
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