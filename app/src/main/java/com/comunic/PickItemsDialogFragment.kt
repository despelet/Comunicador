package com.comunic

import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.comunic.adapters.PickItemsAdapter
import com.comunic.fragment.MainActivity
import com.google.android.material.button.MaterialButton

class PickItemsDialogFragment(
    private val items: List<ItemLista>,
    private val onConfirm: (List<ItemLista>) -> Unit
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_pick_items, null)
        val recycler = view.findViewById<RecyclerView>(R.id.recyclerItems)
        val checked = BooleanArray(items.size)

        val adapter = PickItemsAdapter(items = items, checked = checked
        ) { index, isChecked ->
            checked[index] = isChecked
        }

        recycler.layoutManager = GridLayoutManager(requireContext(), 2)
        recycler.adapter = adapter

        val activity = activity as? MainActivity

        // =========================
        // BOTONES SUPERIORES
        // =========================
        view.findViewById<View>(R.id.btnCamera).setOnClickListener {
            activity?.launchImageCapture()
            dismiss()
        }

        view.findViewById<View>(R.id.btnVideo).setOnClickListener {
            activity?.launchVideoCapture()
            dismiss()
        }

        view.findViewById<View>(R.id.btnGallery).setOnClickListener {
            activity?.openGallery()
            dismiss()
        }

        view.findViewById<View>(R.id.btnConfirmar).setOnClickListener {
            val selected = items.filterIndexed { idx, _ ->
                checked[idx]
            }
            onConfirm(selected)
            dismiss()
        }

        val dialog = AlertDialog.Builder(requireContext(), R.style.ThemeOverlay_Comunic_AlertDialog).setView(view).create()
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        view.findViewById<MaterialButton>(R.id.btnCancelar).setOnClickListener { dialog.dismiss() }

        return dialog
    }
}