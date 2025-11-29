package com.comunic.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RankingManager private constructor(context: Context) {

    private val itemUsadoDao = AppDatabase.getDatabase(context).itemUsadoDao()

    companion object {
        @Volatile
        private var INSTANCE: RankingManager? = null

        fun getInstance(context: Context): RankingManager {
            return INSTANCE ?: synchronized(this) {
                val instance = RankingManager(context)
                INSTANCE = instance
                instance
            }
        }
    }

    // Aumenta el contador de uso y actualiza la fecha
    fun registrarUso(nombreArchivo: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val item = itemUsadoDao.obtenerPorNombre(nombreArchivo)
            if (item != null) {
                item.cantidadDeUsos++
                item.ultimaFechaUso = System.currentTimeMillis()
                itemUsadoDao.insertar(item)
            } else {
                itemUsadoDao.insertar(
                    ItemUsado(
                        nombreArchivo = nombreArchivo,
                        cantidadDeUsos = 1,
                        ultimaFechaUso = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    // Obtener los 6 más usados
    suspend fun getTop6(): List<ItemUsado> {
        return itemUsadoDao.getTop6ItemsUsados()
    }


    // Vaciar toda la base
    fun resetear() {
        CoroutineScope(Dispatchers.IO).launch {
            itemUsadoDao.borrarTodo()
        }
    }
}
