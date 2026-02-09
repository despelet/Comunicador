package com.comunic.data.db


import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration
import com.comunic.data.dao.CategoryDao
import com.comunic.data.dao.InstalledPackDao
import com.comunic.data.dao.ItemUsadoBucketDao
import com.comunic.data.dao.ItemUsadoDao
import com.comunic.data.dao.PictogramDao
import com.comunic.data.entity.CategoryEntity
import com.comunic.data.entity.CategoryItemEntity
import com.comunic.data.entity.InstalledPackEntity
import com.comunic.data.entity.ItemUsado
import com.comunic.data.entity.ItemUsadoBucket
import com.comunic.data.entity.PictogramEntity
import com.comunic.data.entity.PictogramOverrideEntity

@Database(
    entities = [
        ItemUsado::class,
        ItemUsadoBucket::class,
        InstalledPackEntity::class,
        CategoryEntity::class,
        PictogramEntity::class,
        CategoryItemEntity::class,
        PictogramOverrideEntity::class
    ],
    version = 3
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun itemUsadoDao(): ItemUsadoDao
    abstract fun itemUsadoBucketDao(): ItemUsadoBucketDao

    abstract fun installedPackDao(): InstalledPackDao
    abstract fun categoryDao(): CategoryDao
    abstract fun pictogramDao(): PictogramDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "items_usados_db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS installed_packs (
                        packId TEXT NOT NULL PRIMARY KEY,
                        version INTEGER NOT NULL,
                        installedAt INTEGER NOT NULL
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS categories (
                        categoryId TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        orderIndex INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS pictograms (
                        pictogramId TEXT NOT NULL PRIMARY KEY,
                        packId TEXT NOT NULL,
                        baseLabel TEXT NOT NULL,
                        baseImageUri TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS category_items (
                        placementId TEXT NOT NULL PRIMARY KEY,
                        categoryId TEXT NOT NULL,
                        pictogramId TEXT NOT NULL,
                        orderIndex INTEGER NOT NULL
                    )
                """.trimIndent())

                db.execSQL("""
                  CREATE INDEX IF NOT EXISTS index_category_items_categoryId_orderIndex
                  ON category_items(categoryId, orderIndex)
                """.trimIndent())

                                db.execSQL("""
                  CREATE INDEX IF NOT EXISTS index_category_items_pictogramId
                  ON category_items(pictogramId)
                """.trimIndent())


                db.execSQL("""
                      CREATE INDEX IF NOT EXISTS index_pictograms_packId
                      ON pictograms(packId)
                    """.trimIndent())


                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS pictogram_overrides (
                        pictogramId TEXT NOT NULL PRIMARY KEY,
                        customLabel TEXT,
                        customImageUri TEXT,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }
    }
}


//
//import android.content.Context
//import androidx.room.Database
//import androidx.room.Room
//import androidx.room.RoomDatabase
//import com.comunic.data.dao.ItemUsadoBucketDao
//import com.comunic.data.dao.ItemUsadoDao
//import com.comunic.data.entity.ItemUsado
//import com.comunic.data.entity.ItemUsadoBucket
//
//@Database(entities = [ItemUsado::class, ItemUsadoBucket::class], version = 2)
//abstract class AppDatabase : RoomDatabase() {
//
//    abstract fun itemUsadoDao(): ItemUsadoDao
//    abstract fun itemUsadoBucketDao(): ItemUsadoBucketDao
//
//    companion object {
//        @Volatile private var INSTANCE: AppDatabase? = null
//
//        fun getDatabase(context: Context): AppDatabase {
//            return INSTANCE ?: synchronized(this) {
//                val instance = Room.databaseBuilder(
//                    context.applicationContext,
//                    AppDatabase::class.java,
//                    "items_usados_db"
//                )
//                    .addMigrations(MIGRATION_1_2) // migra de la base de datos anterior a la nueva
//                    .build()
//                INSTANCE = instance
//                instance
//            }
//        }
//
//        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
//            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
//                db.execSQL("""
//                    CREATE TABLE IF NOT EXISTS items_usados_bucket (
//                        nombreArchivo TEXT NOT NULL,
//                        bucketId INTEGER NOT NULL,
//                        cantidadDeUsos INTEGER NOT NULL,
//                        ultimaFechaUso INTEGER NOT NULL,
//                        PRIMARY KEY(nombreArchivo, bucketId)
//                    )
//                """.trimIndent())
//            }
//        }
//    }
//}
