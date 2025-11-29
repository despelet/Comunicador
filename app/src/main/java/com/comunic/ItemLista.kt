package com.comunic

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ItemLista(
    val nombre: String,
    val uri: Uri,
    val esImagen: Boolean,
    var timestamp: Long
) : Parcelable
