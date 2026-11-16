package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
  entities = [
    LocalLevelProgressEntity::class,
    LocalPlayerProfileEntity::class,
    LocalCustomLevelEntity::class,
    LocalThemePreferenceEntity::class,
    LocalThemePurchaseEntity::class,
  ],
  version = 2,
  exportSchema = false,
)
abstract class KineticDatabase : RoomDatabase() {
  abstract fun kineticDao(): KineticDao

  companion object {
    @Volatile private var instance: KineticDatabase? = null

    val MIGRATION_1_2 = object : Migration(1, 2) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS `theme_preferences` (
            `userId` TEXT NOT NULL,
            `selectedThemeId` TEXT NOT NULL,
            `unlockedThemesCsv` TEXT NOT NULL,
            `preferredRegion` TEXT NOT NULL,
            `updatedAtMillis` INTEGER NOT NULL,
            PRIMARY KEY(`userId`)
          )
          """.trimIndent()
        )
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS `theme_purchases` (
            `transactionId` TEXT NOT NULL,
            `userId` TEXT NOT NULL,
            `themeId` TEXT NOT NULL,
            `paymentMethod` TEXT NOT NULL,
            `currency` TEXT NOT NULL,
            `amountDisplay` TEXT NOT NULL,
            `maskedAccount` TEXT NOT NULL,
            `status` TEXT NOT NULL,
            `purchasedAtMillis` INTEGER NOT NULL,
            PRIMARY KEY(`transactionId`)
          )
          """.trimIndent()
        )
      }
    }

    fun getInstance(context: Context): KineticDatabase {
      return instance ?: synchronized(this) {
        instance ?: Room.databaseBuilder(
          context.applicationContext,
          KineticDatabase::class.java,
          "kinetic_lab_offline.db",
        )
          .addMigrations(MIGRATION_1_2)
          .build()
          .also { instance = it }
      }
    }
  }
}
