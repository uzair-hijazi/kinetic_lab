package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface KineticDao {
  @Query("SELECT * FROM player_profile WHERE userId = :userId LIMIT 1")
  fun observeProfile(userId: String): Flow<LocalPlayerProfileEntity?>

  @Query("SELECT * FROM player_profile WHERE userId = :userId LIMIT 1")
  suspend fun getProfile(userId: String): LocalPlayerProfileEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertProfile(profile: LocalPlayerProfileEntity)

  @Query("SELECT * FROM level_progress WHERE userId = :userId ORDER BY levelNumber ASC")
  fun observeAllProgress(userId: String): Flow<List<LocalLevelProgressEntity>>

  @Query("SELECT * FROM level_progress WHERE userId = :userId")
  suspend fun getAllProgress(userId: String): List<LocalLevelProgressEntity>

  @Query("SELECT * FROM level_progress WHERE userId = :userId AND levelNumber = :levelNumber LIMIT 1")
  suspend fun getProgress(userId: String, levelNumber: Int): LocalLevelProgressEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertProgress(progress: LocalLevelProgressEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertAllProgress(items: List<LocalLevelProgressEntity>)

  @Query("SELECT * FROM level_progress WHERE userId = :userId AND syncedToCloud = 0")
  suspend fun getUnsyncedProgress(userId: String): List<LocalLevelProgressEntity>

  @Query("SELECT * FROM custom_levels WHERE authorId = :userId ORDER BY updatedAtMillis DESC")
  fun observeMyCustomLevels(userId: String): Flow<List<LocalCustomLevelEntity>>

  @Query("SELECT * FROM custom_levels WHERE published = 1 ORDER BY updatedAtMillis DESC")
  fun observeCachedCommunityLevels(): Flow<List<LocalCustomLevelEntity>>

  @Query("SELECT * FROM custom_levels WHERE id = :id LIMIT 1")
  suspend fun getCustomLevel(id: String): LocalCustomLevelEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertCustomLevel(level: LocalCustomLevelEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertCustomLevels(levels: List<LocalCustomLevelEntity>)

  @Query("SELECT * FROM custom_levels WHERE authorId = :userId AND syncedToCloud = 0")
  suspend fun getUnsyncedCustomLevels(userId: String): List<LocalCustomLevelEntity>

  @Query("DELETE FROM custom_levels WHERE id = :id")
  suspend fun deleteCustomLevel(id: String)

  @Query("SELECT * FROM theme_preferences WHERE userId = :userId LIMIT 1")
  fun observeThemePreference(userId: String): Flow<LocalThemePreferenceEntity?>

  @Query("SELECT * FROM theme_preferences WHERE userId = :userId LIMIT 1")
  suspend fun getThemePreference(userId: String): LocalThemePreferenceEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertThemePreference(pref: LocalThemePreferenceEntity)

  @Query("SELECT * FROM theme_purchases WHERE userId = :userId ORDER BY purchasedAtMillis DESC")
  fun observeThemePurchases(userId: String): Flow<List<LocalThemePurchaseEntity>>

  @Query("SELECT * FROM theme_purchases WHERE userId = :userId ORDER BY purchasedAtMillis DESC")
  suspend fun getThemePurchases(userId: String): List<LocalThemePurchaseEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertThemePurchase(purchase: LocalThemePurchaseEntity)
}
