package com.comunic.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ItemUsado::class], version = 1)
abstract class AppDatabase : RoomDatabase() {

    abstract fun itemUsadoDao(): ItemUsadoDao // da acceso a las operaciones DAO

    companion object {      // implementa un singleton: garantiza que haya una sola instancia de la base de datos por aplicacion
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "items_usados_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}