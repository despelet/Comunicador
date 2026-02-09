package com.comunic



import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.comunic.data.RankingManager
import com.comunic.data.db.AppDatabase
import com.comunic.databinding.FragmentCategoriaDetalleBinding
import kotlinx.coroutines.launch

class CategoriaDetalleFragment : Fragment() {

    private var _binding: FragmentCategoriaDetalleBinding? = null
    private val binding get() = _binding!!

    companion object {
        fun newInstance(categoryId: String, categoryName: String) =
            CategoriaDetalleFragment().apply {
                arguments = Bundle().apply {
                    putString("categoryId", categoryId)
                    putString("categoryName", categoryName)
                }
            }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCategoriaDetalleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val categoryId = requireArguments().getString("categoryId")!!
        val categoryName = requireArguments().getString("categoryName")!!
        binding.txtTitulo.text = categoryName

        val db = AppDatabase.getDatabase(requireContext())

        // IMPORTANTE: en onViewCreated ya existe viewLifecycleOwner
        viewLifecycleOwner.lifecycleScope.launch {
            val pictosDb = db.pictogramDao().getPictosForCategory(categoryId)

            val pictos = pictosDb.map {
                PictoUi(
                    pictogramId = it.pictogramId,
                    label = it.label,
                    imagePath = it.imageUri
                )
            }

            binding.recyclerPictos.layoutManager = GridLayoutManager(requireContext(), 3)
            binding.recyclerPictos.adapter = PictosAdapter(
                pictos = pictos,
                onClick = { picto ->
                    RankingManager.getInstance(requireContext())
                        .registrarUso(picto.pictogramId)

                    // TTS o lo que quieras
                    // palabraAudio(picto.label)
                }
            )

        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}


