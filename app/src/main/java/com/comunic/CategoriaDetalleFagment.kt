package com.comunic



import android.app.AlertDialog
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.comunic.data.RankingManager
import com.comunic.data.db.AppDatabase
import com.comunic.databinding.FragmentCategoriaDetalleBinding
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import com.comunic.data.entity.CategoryItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.comunic.data.mappers.resolveItemKeyToItemLista
import java.util.Locale
import java.util.UUID

class CategoriaDetalleFragment :
    Fragment(),
    TextToSpeech.OnInitListener {

    private var _binding: FragmentCategoriaDetalleBinding? = null
    private val binding get() = _binding!!

    private lateinit var tts: TextToSpeech
    private var ttsReady = false

    private lateinit var db: AppDatabase
    private lateinit var mediaAdapter: MediaAdapter
    private val listaDeArchivos: MutableList<ItemLista> = mutableListOf()

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

    /*override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
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
    } */


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tts = TextToSpeech(requireContext(), this)

        val categoryId = requireArguments().getString("categoryId")!!
        val categoryName = requireArguments().getString("categoryName")!!
        binding.txtTitulo.text = categoryName

//        binding.btnAgregarElemento.setOnClickListener {
//            mostrarDialogoAgregarItem(categoryId)
//        }
        binding.btnAgregarElemento.setOnClickListener {
            abrirSelectorParaAgregar(categoryId)
        }

        db = AppDatabase.getDatabase(requireContext())

        // 1) Recycler en grilla (como querías)
        binding.recyclerPictos.layoutManager = GridLayoutManager(requireContext(), 3)

        // 2) Adapter reutilizado (como Recientes/Home)
        mediaAdapter = MediaAdapter(
            mediaList = listaDeArchivos,
            eliminar = { itemKey ->
                eliminarDeCategoria(categoryId, itemKey)
            },
            palabraAudio = { itemKey ->
                reproducirAudioPorItemKey(itemKey)
            }
        )

        // 3) Si querés soportar eliminación múltiple desde el adapter
        mediaAdapter.eliminarSeleccionListener = object : MediaAdapter.OnEliminarSeleccionListener {
            override fun onEliminarSeleccionSolicitada(seleccionados: List<ItemLista>) {
                eliminarSeleccionDeCategoria(categoryId, seleccionados)
            }
        }

        binding.recyclerPictos.adapter = mediaAdapter

        // 4) Cargar items reales de la categoría (pictos + media)
        loadCategory(categoryId)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val res = tts.setLanguage(Locale("es","AR"))
            ttsReady = (res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED)
        }
    }

    override fun onDestroyView() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroyView()
        _binding = null
    }

    private fun loadCategory(categoryId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val keys = withContext(Dispatchers.IO) {
                db.categoryDao().getItemKeysForCategory(categoryId)
            }
            Log.d("CAT_DEBUG", "keys=${keys.joinToString()}")

            val items = withContext(Dispatchers.IO) {
                keys.mapNotNull { key ->
                    resolveItemKeyToItemLista(requireContext(), key)
                }
            }

            listaDeArchivos.clear()
            listaDeArchivos.addAll(items)
            mediaAdapter.notifyDataSetChanged()
        }
    }

    private fun eliminarDeCategoria(categoryId: String, itemKey: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val placementId = db.categoryDao().findPlacementId(categoryId, itemKey)
                if (placementId != null) {
                    db.categoryDao().deletePlacement(placementId)
                }
            }
            loadCategory(categoryId)
        }
    }

    private fun eliminarSeleccionDeCategoria(categoryId: String, seleccionados: List<ItemLista>) {
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                // cada ItemLista.id == itemKey (porque ya ajustamos el resolver)
                seleccionados.forEach { item ->
                    val placementId = db.categoryDao().findPlacementId(categoryId, item.id)
                    if (placementId != null) {
                        db.categoryDao().deletePlacement(placementId)
                    }
                }
            }
            loadCategory(categoryId)
        }
    }

    private fun reproducirAudioPorItemKey(itemKeyOrId: String) {
        if (!ttsReady) return

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val texto = SpeechTextResolver.resolve(db, itemKeyOrId)

            withContext(Dispatchers.Main) {
                tts.speak(texto, TextToSpeech.QUEUE_FLUSH, null, null)
            }
        }
    }



    private fun abrirSelectorParaAgregar(categoryId: String) {
        viewLifecycleOwner.lifecycleScope.launch {

            // 1) cargar items disponibles (pictos + media)
            val disponibles = withContext(Dispatchers.IO) {
                com.comunic.data.mappers.loadAllAvailableItems(requireContext(), db)
            }

            // 2) abrir dialog multi-select
            PickItemsDialogFragment(disponibles) { selected ->
                viewLifecycleOwner.lifecycleScope.launch {

                    // 3) insertar seleccionados en category_items
//                    withContext(Dispatchers.IO) {
//                        var next = db.categoryDao().getMaxOrderIndex(categoryId) + 1
//
//                        selected.forEach { item ->
//                            db.categoryDao().insertCategoryItem(
//                                CategoryItemEntity(
//                                    placementId = UUID.randomUUID().toString(),
//                                    categoryId = categoryId,
//                                    itemKey = item.id,    // ✅ id == itemKey
//                                    orderIndex = next++
//                                )
//                            )
//                        }
//                    }
                    withContext(Dispatchers.IO) {
                        val existentes = db.categoryDao().getItemKeysForCategory(categoryId).toSet()
                        val nuevos = selected.filter { it.id !in existentes }

                        var next = db.categoryDao().getMaxOrderIndex(categoryId) + 1
                        nuevos.forEach { item ->
                            db.categoryDao().insertCategoryItem(
                                CategoryItemEntity(
                                    placementId = UUID.randomUUID().toString(),
                                    categoryId = categoryId,
                                    itemKey = item.id,
                                    orderIndex = next++
                                )
                            )
                        }
                    }

                    // 4) refrescar UI
                    loadCategory(categoryId)
                }
            }.show(parentFragmentManager, "PickItemsAdd")
        }
    }


}


