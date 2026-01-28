package com.comunic.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ItemUsado::class, ItemUsadoBucket::class], version = 2)
abstract class AppDatabase : RoomDatabase() {

    abstract fun itemUsadoDao(): ItemUsadoDao
    abstract fun itemUsadoBucketDao(): ItemUsadoBucketDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "items_usados_db"
                )
                    .addMigrations(MIGRATION_1_2) // migra de la base de datos anterior a la nueva
                    .build()
                INSTANCE = instance
                instance
            }
        }

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS items_usados_bucket (
                        nombreArchivo TEXT NOT NULL,
                        bucketId INTEGER NOT NULL,
                        cantidadDeUsos INTEGER NOT NULL,
                        ultimaFechaUso INTEGER NOT NULL,
                        PRIMARY KEY(nombreArchivo, bucketId)
                    )
                """.trimIndent())
            }
        }
    }
}
