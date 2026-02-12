package com.comunic

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

//@Parcelize
//data class ItemLista(
//    val nombre: String,
//    val uri: Uri,
//    val esImagen: Boolean,
//    var timestamp: Long
//) : Parcelable
@kotlinx.android.parcel.Parcelize
data class ItemLista(
    val id: String,        // "basic_yes" o nombreArchivo user
    val nombre: String,    // texto visible: "Sí" / "No" / ...
    val uri: Uri,
    val esImagen: Boolean,
    var timestamp: Long
) : Parcelable