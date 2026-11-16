package com.example.data.remote

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class CloudPlayerProfile(
  @DocumentId val id: String = "",
  val userId: String = "",
  val displayName: String = "Kinetic Player",
  val coins: Long = 100L,
  val hintsRemaining: Long = 3L,
  val streakDays: Long = 0L,
  val lastClaimEpochDay: Long = 0L,
  val selectedSkin: String = "cyan",
  val unlockedSkins: List<String> = listOf("cyan"),
  val totalScore: Long = 0L,
  val totalStars: Long = 0L,
  val levelsCompleted: Long = 0L,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class CloudLevelProgress(
  @DocumentId val id: String = "",
  val userId: String = "",
  val levelNumber: Long = 1L,
  val worldIndex: Long = 0L,
  val bestScore: Long = 0L,
  val stars: Long = 0L,
  val completed: Boolean = false,
  val bestToolsUsed: Long = 0L,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class CloudLeaderboardEntry(
  @DocumentId val id: String = "",
  val userId: String = "",
  val displayName: String = "Kinetic Player",
  val avatarBadge: String = "cyan",
  val totalScore: Long = 0L,
  val totalStars: Long = 0L,
  val levelsCompleted: Long = 0L,
  val isPublic: Boolean = true,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class CloudCustomLevel(
  @DocumentId val id: String = "",
  val authorId: String = "",
  val authorName: String = "Architect",
  val title: String = "Untitled Lab",
  val description: String = "",
  val gravityMode: String = "EARTH",
  val emitter: Map<String, Any?> = emptyMap(),
  val goal: Map<String, Any?> = emptyMap(),
  val toolBudget: Map<String, Any?> = emptyMap(),
  val elements: List<Map<String, Any?>> = emptyList(),
  val stars: List<Map<String, Any?>> = emptyList(),
  val published: Boolean = false,
  val verified: Boolean = false,
  val playsCount: Long = 0L,
  val likesCount: Long = 0L,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)
