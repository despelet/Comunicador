package com.comunic.data.mappers

import android.net.Uri
import com.comunic.ItemLista
import com.comunic.data.dao.PictogramDao
import com.comunic.data.model.PictogramUiRow
import java.io.File


// mapper para pictograma de db a ItemLista
fun PictogramDao.PictogramUiRow.toItemLista(
timestamp: Long = System.currentTimeMillis()
): ItemLista {
    return ItemLista(
        id = pictogramId,
        nombre = label,
        uri = Uri.fromFile(File(imageUri)),  // imageUri = absolutePath
        esImagen = true,
        timestamp = timestamp
    )
}


fun PictogramUiRow.toItemLista(
    timestamp: Long = System.currentTimeMillis()
): ItemLista {
    return ItemLista(
        id = pictogramId,
        nombre = label,
        uri = Uri.fromFile(File(imageUri)),
        esImagen = true,
        timestamp = timestamp
    )
}