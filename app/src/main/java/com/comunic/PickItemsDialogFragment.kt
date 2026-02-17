package com.comunic

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment

class PickItemsDialogFragment(
    private val items: List<ItemLista>,
    private val onConfirm: (List<ItemLista>) -> Unit
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val nombres = items.map { it.nombre }.toTypedArray()
        val checked = BooleanArray(items.size)

        return AlertDialog.Builder(requireContext(), R.style.ThemeOverlay_Comunic_AlertDialog)
            .setTitle("Elegí elementos")
            .setMultiChoiceItems(nombres, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("Aceptar") { _, _ ->
                val selected = items.filterIndexed { idx, _ -> checked[idx] }
                onConfirm(selected)
            }
            .setNegativeButton("Cancelar", null)
            .create()
    }
}