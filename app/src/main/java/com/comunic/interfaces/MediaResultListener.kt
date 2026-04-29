package com.comunic.interfaces

import android.net.Uri
import com.comunic.ItemLista


interface MediaResultListener {
    fun onMediaCreated(item: ItemLista)
}