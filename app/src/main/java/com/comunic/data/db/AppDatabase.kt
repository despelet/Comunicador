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
    version = 7
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)                    .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
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

        private val MIGRATION_2_3 = object : Migration(2, 3) {
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

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1) Crear tabla nueva
                db.execSQL("""
            CREATE TABLE IF NOT EXISTS category_items_new (
                placementId TEXT NOT NULL,
                categoryId TEXT NOT NULL,
                itemKey TEXT NOT NULL,
                orderIndex INTEGER NOT NULL,
                PRIMARY KEY(placementId)
            )
        """.trimIndent())

                // 2) Copiar datos viejos: pictogramId -> itemKey
                db.execSQL("""
            INSERT INTO category_items_new (placementId, categoryId, itemKey, orderIndex)
            SELECT placementId, categoryId, 'PIC:' || pictogramId, orderIndex
            FROM category_items
        """.trimIndent())

                // 3) Borrar vieja y renombrar
                db.execSQL("DROP TABLE category_items")
                db.execSQL("ALTER TABLE category_items_new RENAME TO category_items")

                // 4) Re-crear índices
                db.execSQL("CREATE INDEX IF NOT EXISTS index_category_items_categoryId_orderIndex ON category_items(categoryId, orderIndex)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_category_items_itemKey ON category_items(itemKey)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {

                // installed_packs: enabled + isSystem
                db.execSQL("ALTER TABLE installed_packs ADD COLUMN enabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE installed_packs ADD COLUMN isSystem INTEGER NOT NULL DEFAULT 0")

                // categories: packId + isSystem + isDeleted
                db.execSQL("ALTER TABLE categories ADD COLUMN packId TEXT NOT NULL DEFAULT 'user'")
                db.execSQL("ALTER TABLE categories ADD COLUMN isSystem INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE categories ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")

                // índices útiles
                db.execSQL("CREATE INDEX IF NOT EXISTS index_installed_packs_enabled ON installed_packs(enabled)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_categories_packId ON categories(packId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_categories_isDeleted ON categories(isDeleted)")
            }
        }
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // borra el pack agrupador viejo que ya no usamos
                db.execSQL("DELETE FROM installed_packs WHERE packId = 'basic'")
            }
        }
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {

                // 1) Pasar pictos del pack viejo "basic" al nuevo pack correspondiente
                db.execSQL("""
            UPDATE pictograms
            SET packId = 'basic_food'
            WHERE packId = 'basic'
              AND pictogramId LIKE 'food_%'
        """.trimIndent())

                db.execSQL("""
            UPDATE pictograms
            SET packId = 'basic_core'
            WHERE packId = 'basic'
              AND pictogramId LIKE 'basic_%'
        """.trimIndent())

                // 2) (opcional) si quedó alguno raro, mandalo a core por defecto
                db.execSQL("""
            UPDATE pictograms
            SET packId = 'basic_core'
            WHERE packId = 'basic'
        """.trimIndent())

                // 3) Asegurar que existan filas en installed_packs para los nuevos
                db.execSQL("""
            INSERT OR IGNORE INTO installed_packs(packId, version, installedAt, enabled, isSystem)
            VALUES ('basic_core', 1, strftime('%s','now')*1000, 1, 1)
        """.trimIndent())

                db.execSQL("""
            INSERT OR IGNORE INTO installed_packs(packId, version, installedAt, enabled, isSystem)
            VALUES ('basic_food', 1, strftime('%s','now')*1000, 1, 1)
        """.trimIndent())

                // 4) Ahora sí: borrar el pack viejo (ya no lo necesitás)
                db.execSQL("DELETE FROM installed_packs WHERE packId='basic'")
            }
        }
    }
}



