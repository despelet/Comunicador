package com.comunic.interfaces

import android.net.Uri

interface ZipImportListener {
    fun importarElementosDesdeZip(uri: Uri)
}