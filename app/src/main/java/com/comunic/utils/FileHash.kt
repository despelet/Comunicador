package com.comunic.utils

import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/*
*
* Usa SHA-256.
Lee el archivo por bloques (8 KB), por lo que funciona bien tanto para imágenes como para videos grandes.
No carga el archivo completo en memoria.
Devuelve un String hexadecimal estable, fácil de comparar y almacenar.
*
* */

object FileHash {

    fun sha256(file: File): String {

        val digest = MessageDigest.getInstance("SHA-256")

        FileInputStream(file).use { input ->
                                                  
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

            while (true) {

                val bytesRead = input.read(buffer)

                if (bytesRead <= 0) {
                    break
                }

                digest.update(buffer, 0, bytesRead)
            }
        }

        return digest.digest()
            .joinToString("") { "%02x".format(it) }
    }

    fun sha256(uri: Uri): String {
        return sha256(
            File(
                requireNotNull(uri.path) {
                    "URI sin ruta local: $uri"
                }
            )
        )
    }
}