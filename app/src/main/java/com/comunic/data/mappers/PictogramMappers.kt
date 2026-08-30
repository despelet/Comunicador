package com.comunic.data.mappers

import android.net.Uri
import com.comunic.ItemKey
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


//fun PictogramUiRow.toItemLista(
//    timestamp: Long = System.currentTimeMillis()
//): ItemLista {
//    return ItemLista(
//        id = pictogramId,
//        nombre = label,
//        uri = Uri.fromFile(File(imageUri)),
//        esImagen = true,
//        timestamp = timestamp
//    )
//}
//
//fun PictogramDao.PictogramUiRowNoPack.toItemLista(timestamp: Long = 0L): ItemLista {
//    val uri = if (imageUri.startsWith("/")) Uri.fromFile(File(imageUri)) else Uri.parse(imageUri)
//    return ItemLista(
//        id = ItemKey.picto(pictogramId), // o pictogramId si después lo sobreescribís
//        nombre = label,
//        uri = uri,
//        esImagen = true,
//        timestamp = timestamp
//    )
//}
fun PictogramDao.PictoUiMiniRow.toItemLista(timestamp: Long = 0L): ItemLista {
    return ItemLista(
        id = pictogramId, // después el resolver lo convierte a PIC:...
        nombre = label,
        uri = Uri.fromFile(File(imageUri)), // ✅ FIX
        esImagen = true,
        timestamp = timestamp
    )
}
