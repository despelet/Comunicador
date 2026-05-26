package com.comunic.fragment

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.comunic.ItemKey
import com.comunic.ItemLista
import com.comunic.R
import com.comunic.adapters.MediaAdapter
import com.comunic.data.db.AppDatabase
import com.comunic.databinding.FragmentPapeleraBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PapeleraFragment : Fragment() {

    private var _binding: FragmentPapeleraBinding? = null
    private val binding get() = _binding!!

    private lateinit var db: AppDatabase
    private lateinit var adapter: MediaAdapter

    private val items = mutableListOf<ItemLista>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPapeleraBinding.inflate(inflater, container, false)

        db = AppDatabase.getDatabase(requireContext())

        adapter = MediaAdapter(
            mediaList = items,
            eliminar = { },
            palabraAudio = { },
            mostrarMenuEnDialog = false,
            onItemClickOverride = { item ->
                restaurarElemento(item.id)
            }
        )

        binding.recyclerPapelera.layoutManager =
            GridLayoutManager(requireContext(), 3)

        binding.recyclerPapelera.adapter = adapter

        binding.btnVolver.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, HomeFragment())
                .commit()
        }

        cargarPapelera()

        return binding.root
    }

    private fun cargarPapelera() {
        viewLifecycleOwner.lifecycleScope.launch {

            val eliminados = withContext(Dispatchers.IO) {
                db.mediaDao().getDeletedMedia()
            }

            items.clear()

            items.addAll(
                eliminados.map { media ->
                    ItemLista(
                        id = ItemKey.media(media.mediaId),
                        nombre = media.displayName,
                        uri = Uri.parse(media.localUri),
                        esImagen = media.mediaType == "image",
                        timestamp = media.updatedAt
                    )
                }
            )

            adapter.notifyDataSetChanged()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun restaurarElemento(itemKey: String) {

        if (!ItemKey.isMedia(itemKey)) return

        androidx.appcompat.app.AlertDialog.Builder(
            requireContext(),
            R.style.ThemeOverlay_Comunic_AlertDialog
        )
            .setTitle("RESTAURAR")
            .setMessage("¿Restaurar este elemento?")
            .setPositiveButton("SI") { _, _ ->

                viewLifecycleOwner.lifecycleScope.launch {

                    val mediaId = ItemKey.mediaId(itemKey)

                    withContext(Dispatchers.IO) {
                        db.mediaDao().restore(
                            mediaId = mediaId,
                            updatedAt = System.currentTimeMillis()
                        )
                    }

                    cargarPapelera()

                    Toast.makeText(
                        requireContext(),
                        "Elemento restaurado",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton("NO", null)
            .show()
    }
}