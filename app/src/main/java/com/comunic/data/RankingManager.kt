package com.comunic.data

import android.content.Context
import com.comunic.ItemKey
import com.comunic.data.dao.ItemUsadoBucketDao
import com.comunic.data.dao.ItemUsadoDao
import com.comunic.data.db.AppDatabase
import com.comunic.data.entity.ItemUsado
import com.comunic.data.entity.ItemUsadoBucket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar

class RankingManager private constructor(context: Context) {

    private val appContext: Context = context.applicationContext

    private val db: AppDatabase = AppDatabase.getDatabase(appContext)
    private val itemUsadoDao: ItemUsadoDao = db.itemUsadoDao()

    private val itemUsadoBucketDao: ItemUsadoBucketDao = db.itemUsadoBucketDao()

    // 3) Un solo scope para este manager (en lugar de crear uno por click)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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

    /**
     * Registra un uso:
     * - Actualiza el ranking global (items_usados)
     * - Actualiza el ranking contextual por día+franja (items_usados_bucket)
     */
//    fun registrarUso(nombreArchivo: String) {
//        val now = System.currentTimeMillis()
//
//        scope.launch {
//            // Ranking global
//            val itemGlobal = itemUsadoDao.obtenerPorNombre(nombreArchivo)
//            if (itemGlobal != null) {
//                itemGlobal.cantidadDeUsos++
//                itemGlobal.ultimaFechaUso = now
//                itemUsadoDao.insertar(itemGlobal)
//            } else {
//                itemUsadoDao.insertar(
//                    ItemUsado(
//                        nombreArchivo = nombreArchivo,
//                        cantidadDeUsos = 1,
//                        ultimaFechaUso = now
//                    )
//                )
//            }
//
//            // Ranking por momento (día+franja)
//            val bucketId = TimeBucket.bucketIdFromMillis(now)
//
//            val itemBucket = itemUsadoBucketDao.obtenerPorNombreYBucket(nombreArchivo, bucketId)
//            if (itemBucket != null) {
//                itemBucket.cantidadDeUsos++
//                itemBucket.ultimaFechaUso = now
//                itemUsadoBucketDao.insertar(itemBucket)
//            } else {
//                itemUsadoBucketDao.insertar(
//                    ItemUsadoBucket(
//                        nombreArchivo = nombreArchivo,
//                        bucketId = bucketId,
//                        cantidadDeUsos = 1,
//                        ultimaFechaUso = now
//                    )
//                )
//            }
//        }
//    }


    fun registrarUso(itemKeyOrLegacy: String) {
        val now = System.currentTimeMillis()

        scope.launch {

            val key = normalizeKey(itemKeyOrLegacy) // ✅ normalización única

            // ---------- GLOBAL ----------
            val itemGlobal = itemUsadoDao.obtenerPorNombre(key)

            if (itemGlobal != null) {
                itemGlobal.cantidadDeUsos++
                itemGlobal.ultimaFechaUso = now
                itemUsadoDao.insertar(itemGlobal)
            } else {
                itemUsadoDao.insertar(
                    ItemUsado(
                        nombreArchivo = key,
                        cantidadDeUsos = 1,
                        ultimaFechaUso = now
                    )
                )
            }

            // ---------- BUCKET ----------
            val bucketId = TimeBucket.bucketIdFromMillis(now)

            val itemBucket =
                itemUsadoBucketDao.obtenerPorNombreYBucket(key, bucketId)

            if (itemBucket != null) {
                itemBucket.cantidadDeUsos++
                itemBucket.ultimaFechaUso = now
                itemUsadoBucketDao.insertar(itemBucket)
            } else {
                itemUsadoBucketDao.insertar(
                    ItemUsadoBucket(
                        nombreArchivo = key,
                        bucketId = bucketId,
                        cantidadDeUsos = 1,
                        ultimaFechaUso = now
                    )
                )
            }
        }
    }

    private suspend fun normalizeKey(k: String): String = when {
        ItemKey.isPicto(k) || ItemKey.isMedia(k) -> k

        else -> {
            val existsPicto =
                db.pictogramDao().getPictoUiById(k) != null

            if (existsPicto)
                ItemKey.picto(k)
            else
                ItemKey.media(k)
        }
    }

    suspend fun getTop6Global(): List<ItemUsado> {
        return itemUsadoDao.getTop6ItemsUsados()
    }

    suspend fun getTopForNow(limit: Int = 6): List<ItemUsadoBucket> {
        val bucketId = TimeBucket.bucketIdFromMillis(System.currentTimeMillis())
        return itemUsadoBucketDao.getTopForBucket(bucketId, limit)
    }

    // Vaciar toda la base (global + bucket)
    fun resetear() {
        scope.launch {
            itemUsadoDao.borrarTodo()
            itemUsadoBucketDao.borrarTodo() // agregá este método en el DAO bucket
        }
    }

    /**
     * Bucket de tiempo:
     * - Día de semana (lunes..domingo) + franja de 4 horas
     * - Resultado: 0..41 (7 días * 6 franjas)
     */
    object TimeBucket {
        private const val SLOTS_PER_DAY = 6 // 6 franjas de 4h

        fun bucketIdFromMillis(timeMillis: Long): Int {
            val cal = Calendar.getInstance().apply { timeInMillis = timeMillis }

            val dayIndex = when (cal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> 0
                Calendar.TUESDAY -> 1
                Calendar.WEDNESDAY -> 2
                Calendar.THURSDAY -> 3
                Calendar.FRIDAY -> 4
                Calendar.SATURDAY -> 5
                Calendar.SUNDAY -> 6
                else -> 0
            }

            val hour = cal.get(Calendar.HOUR_OF_DAY) // 0..23
            val slot = hour / 4 // 0..5 (cada slot = 4 horas)

            return dayIndex * SLOTS_PER_DAY + slot // 0..41
        }
    }
}

