package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
  tableName = "level_progress",
  primaryKeys = ["userId", "levelNumber"],
)
data class LocalLevelProgressEntity(
  val userId: String,
  val levelNumber: Int,
  val worldIndex: Int,
  val bestScore: Int,
  val stars: Int,
  val completed: Boolean,
  val bestToolsUsed: Int,
  val syncedToCloud: Boolean,
  val updatedAtMillis: Long,
)

@Entity(tableName = "player_profile")
data class LocalPlayerProfileEntity(
  @PrimaryKey val userId: String,
  val displayName: String,
  val coins: Int,
  val hintsRemaining: Int,
  val streakDays: Int,
  val lastClaimEpochDay: Int,
  val selectedSkin: String,
  val unlockedSkinsCsv: String,
  val totalScore: Int,
  val totalStars: Int,
  val levelsCompleted: Int,
  val syncedToCloud: Boolean,
  val updatedAtMillis: Long,
)

@Entity(tableName = "custom_levels")
data class LocalCustomLevelEntity(
  @PrimaryKey val id: String,
  val authorId: String,
  val authorName: String,
  val title: String,
  val description: String,
  val gravityMode: String,
  val levelJson: String,
  val published: Boolean,
  val verified: Boolean,
  val playsCount: Int,
  val likesCount: Int,
  val syncedToCloud: Boolean,
  val updatedAtMillis: Long,
)

@Entity(tableName = "theme_preferences")
data class LocalThemePreferenceEntity(
  @PrimaryKey val userId: String,
  val selectedThemeId: String,
  val unlockedThemesCsv: String,
  val preferredRegion: String,
  val updatedAtMillis: Long,
)

@Entity(tableName = "theme_purchases")
data class LocalThemePurchaseEntity(
  @PrimaryKey val transactionId: String,
  val userId: String,
  val themeId: String,
  val paymentMethod: String,
  val currency: String,
  val amountDisplay: String,
  val maskedAccount: String,
  val status: String,
  val purchasedAtMillis: Long,
)
