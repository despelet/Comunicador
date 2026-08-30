package com.comunic.dialog

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.comunic.R
import com.google.android.material.button.MaterialButton

class SyncProgressDialog : DialogFragment() {

    private lateinit var txtTitulo: TextView
    private lateinit var txtEstado: TextView
    private lateinit var txtDetalle: TextView
    private lateinit var txtContador: TextView
    private lateinit var txtPorcentaje: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnAceptar: MaterialButton
    private lateinit var btnCerrar: ImageButton
    var onCancelSync: (() -> Unit)? = null

    override fun onCreateDialog(
        savedInstanceState: Bundle?
    ): Dialog {

        isCancelable = false

        return Dialog(requireContext()).apply {
            setCanceledOnTouchOutside(false)
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawable(
                ColorDrawable(Color.TRANSPARENT)
            )
            val margin = (20 * resources.displayMetrics.density).toInt()
            setLayout(
                resources.displayMetrics.widthPixels - (margin * 2),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val view =
            inflater.inflate(
                R.layout.sync_dialog_process,
                container,
                false
            )

        txtTitulo = view.findViewById(R.id.txtTitulo)
        txtEstado = view.findViewById(R.id.txtEstado)
        txtDetalle = view.findViewById(R.id.txtDetalle)
       txtContador = view.findViewById(R.id.txtContador)
        txtPorcentaje = view.findViewById(R.id.txtPorcentaje)
        progressBar = view.findViewById(R.id.progressBar)
        btnAceptar = view.findViewById(R.id.btnAceptar)
        btnAceptar.setOnClickListener {
            dismiss()
        }
        btnCerrar = view.findViewById(R.id.btnCerrar)
        btnCerrar.setOnClickListener {
            onCancelSync?.invoke()
            dismiss()
        }

        return view
    }

    fun updateProgress(
        current: Int,
        total: Int,
        estado: String,
        detalle: String
    ) {

        activity?.runOnUiThread {

            val porcentaje =
                if (total > 0) {
                    (current * 100) / total
                } else {
                    0
                }

            txtEstado.text = estado
            txtDetalle.text = detalle
            txtContador.text = "$current / $total"
            txtPorcentaje.text = "$porcentaje%"
            progressBar.isIndeterminate = false
            progressBar.progress = porcentaje
        }
    }

    fun showIndeterminate(
        estado: String
    ) {

        activity?.runOnUiThread {

            txtEstado.text = estado
            txtDetalle.text = ""
            txtPorcentaje.text = ""
            progressBar.isIndeterminate = true
        }
    }

    fun finishSuccess(
        mensaje: String
    ) {

        activity?.runOnUiThread {

            txtTitulo.text = "Sincronización completada"
            txtEstado.text = mensaje
            txtPorcentaje.text = "100%"
            progressBar.isIndeterminate = false
            progressBar.progress = 100

            txtDetalle.visibility = View.GONE
            btnAceptar.visibility = View.VISIBLE
        }
    }

    fun finishError(
        mensaje: String
    ) {

        activity?.runOnUiThread {
            txtTitulo.text = "Error de sincronización"
            txtEstado.text = mensaje
            progressBar.isIndeterminate = false
            btnAceptar.visibility = View.VISIBLE
        }
    }
}