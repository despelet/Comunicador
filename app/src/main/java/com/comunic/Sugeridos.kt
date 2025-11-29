package com.comunic

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.comunic.databinding.FragmentListasBinding // <-- ajustá el paquete
// importá tu interfaz MenuHandler
// import com.tu.paquete.MenuHandler

class Sugeridos : Fragment(), MenuHandler {

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