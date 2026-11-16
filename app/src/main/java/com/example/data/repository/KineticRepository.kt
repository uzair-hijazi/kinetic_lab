package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.billing.BillingRegion
import com.example.billing.PaymentMethod
import com.example.billing.PaymentProcessor
import com.example.billing.PurchaseTransactionResult
import com.example.data.local.KineticDao
import com.example.data.local.KineticDatabase
import com.example.data.local.LocalCustomLevelEntity
import com.example.data.local.LocalLevelProgressEntity
import com.example.data.local.LocalPlayerProfileEntity
import com.example.data.local.LocalThemePreferenceEntity
import com.example.data.local.LocalThemePurchaseEntity
import com.example.data.local.LevelSerializer
import com.example.data.remote.CloudCustomLevel
import com.example.data.remote.CloudLeaderboardEntry
import com.example.data.remote.CloudLevelProgress
import com.example.data.remote.CloudPlayerProfile
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.example.physics.PuzzleLevel
import com.example.ui.theme.AppThemeCatalog
import com.example.ui.theme.AppThemeDefinition
import com.example.ui.theme.ThemeUnlockType
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

data class DailyRewardTier(
  val day: Int,
  val coins: Int,
  val hints: Int,
  val bonusSkin: String? = null,
)

data class DailyRewardResult(
  val tier: DailyRewardTier,
  val newStreak: Int,
)

data class SyncSummary(
  val uploadedCount: Int,
  val downloadedCount: Int,
)

class KineticRepository(
  private val db: FirebaseFirestore,
  private val dao: KineticDao,
  private val auth: FirebaseAuth = Firebase.auth,
) {
  constructor(context: Context) : this(
    db = FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    ),
    dao = KineticDatabase.getInstance(context.applicationContext).kineticDao(),
  )

  private fun requireUserId(): String {
    return auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
  }

  private fun isCloudSession(userId: String): Boolean {
    return userId != PREVIEW_USER_ID && auth.currentUser?.uid == userId
  }

  fun observeLocalProfile(userId: String): Flow<LocalPlayerProfileEntity?> = dao.observeProfile(userId)

  fun observeLocalProgress(userId: String): Flow<List<LocalLevelProgressEntity>> =
    dao.observeAllProgress(userId)

  fun observeMyCustomLevels(userId: String): Flow<List<LocalCustomLevelEntity>> =
    dao.observeMyCustomLevels(userId)

  fun observeCachedCommunityLevels(): Flow<List<LocalCustomLevelEntity>> =
    dao.observeCachedCommunityLevels()

  fun observeThemePreference(userId: String): Flow<LocalThemePreferenceEntity?> =
    dao.observeThemePreference(userId)

  fun observeThemePurchases(userId: String): Flow<List<LocalThemePurchaseEntity>> =
    dao.observeThemePurchases(userId)

  fun observeLeaderboard(): Flow<List<CloudLeaderboardEntry>> = flow {
    val uid = requireUserId()
    val path = "leaderboard"
    emitAll(
      db.collection(path)
        .whereEqualTo("isPublic", true)
        .limit(100)
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(CloudLeaderboardEntry::class.java)
            .sortedByDescending { it.totalScore }
        }
        .catch { error ->
          val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<com.google.firebase.firestore.FirebaseFirestoreException>()
            .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val exceptionToLog = firestoreError ?: (error as? Exception)
          if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, OperationType.LIST, path)
          }
          throw error
        }
    )
  }

  fun observeCommunityLevels(): Flow<List<CloudCustomLevel>> = flow {
    val uid = requireUserId()
    val path = "custom_levels"
    emitAll(
      db.collection(path)
        .whereEqualTo("published", true)
        .limit(60)
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(CloudCustomLevel::class.java)
            .sortedByDescending { it.likesCount * 3 + it.playsCount }
        }
        .catch { error ->
          val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<com.google.firebase.firestore.FirebaseFirestoreException>()
            .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val exceptionToLog = firestoreError ?: (error as? Exception)
          if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, OperationType.LIST, path)
          }
          throw error
        }
    )
  }

  suspend fun ensureLocalProfile(userId: String, defaultDisplayName: String): LocalPlayerProfileEntity {
    val existing = dao.getProfile(userId)
    if (existing != null) return existing
    val sanitizedName = sanitizeHandle(defaultDisplayName)
    val initial = LocalPlayerProfileEntity(
      userId = userId,
      displayName = sanitizedName,
      coins = 120,
      hintsRemaining = 3,
      streakDays = 0,
      lastClaimEpochDay = 0,
      selectedSkin = "cyan",
      unlockedSkinsCsv = "cyan",
      totalScore = 0,
      totalStars = 0,
      levelsCompleted = 0,
      syncedToCloud = false,
      updatedAtMillis = System.currentTimeMillis(),
    )
    dao.upsertProfile(initial)
    return initial
  }

  suspend fun ensureThemePreference(userId: String): LocalThemePreferenceEntity {
    val existing = dao.getThemePreference(userId)
    if (existing != null) return existing
    val initial = LocalThemePreferenceEntity(
      userId = userId,
      selectedThemeId = AppThemeCatalog.DEFAULT_THEME_ID,
      unlockedThemesCsv = AppThemeCatalog.DEFAULT_THEME_ID,
      preferredRegion = BillingRegion.WORLDWIDE.name,
      updatedAtMillis = System.currentTimeMillis(),
    )
    dao.upsertThemePreference(initial)
    return initial
  }

  suspend fun selectOrUnlockCoinTheme(
    themeId: String,
    userId: String = requireUserId(),
  ): Result<AppThemeDefinition> {
    return runCatching {
      val theme = AppThemeCatalog.getTheme(themeId)
      val pref = ensureThemePreference(userId)
      val unlocked = pref.unlockedThemesCsv
        .split(",")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toMutableSet()
      unlocked.add(AppThemeCatalog.DEFAULT_THEME_ID)

      if (theme.id !in unlocked) {
        when (theme.unlockType) {
          ThemeUnlockType.FREE_DEFAULT -> unlocked.add(theme.id)
          ThemeUnlockType.COINS -> {
            val fallbackName = if (userId == PREVIEW_USER_ID) "Preview Pilot" else (auth.currentUser?.displayName ?: "Kinetic Ace")
            val profile = ensureLocalProfile(userId, fallbackName)
            check(profile.coins >= theme.coinCost) {
              "Need ${theme.coinCost} Kinetic Coins to unlock ${theme.name}."
            }
            val updatedProfile = profile.copy(
              coins = profile.coins - theme.coinCost,
              syncedToCloud = false,
              updatedAtMillis = System.currentTimeMillis(),
            )
            dao.upsertProfile(updatedProfile)
            if (isCloudSession(userId)) {
              pushProfileToCloud(userId, updatedProfile)
            }
            unlocked.add(theme.id)
          }
          ThemeUnlockType.REAL_PURCHASE -> {
            throw IllegalStateException("${theme.name} can only be unlocked via real purchase.")
          }
        }
      }

      dao.upsertThemePreference(
        pref.copy(
          selectedThemeId = theme.id,
          unlockedThemesCsv = unlocked.joinToString(","),
          updatedAtMillis = System.currentTimeMillis(),
        )
      )
      theme
    }
  }

  suspend fun purchasePremiumTheme(
    themeId: String,
    region: BillingRegion,
    paymentMethod: PaymentMethod,
    mobileNumber: String,
    cnicLast6: String,
    accountLabel: String = "Google Pay Wallet",
    userId: String = requireUserId(),
  ): Result<PurchaseTransactionResult> {
    return runCatching {
      val theme = AppThemeCatalog.getTheme(themeId)
      val result = PaymentProcessor.processCheckout(
        theme = theme,
        region = region,
        method = paymentMethod,
        mobileNumber = mobileNumber,
        cnicLast6 = cnicLast6,
        accountLabel = accountLabel,
      ).getOrThrow()

      val purchaseEntity = LocalThemePurchaseEntity(
        transactionId = result.transactionId,
        userId = userId,
        themeId = theme.id,
        paymentMethod = paymentMethod.id,
        currency = result.currency,
        amountDisplay = result.amountDisplay,
        maskedAccount = result.maskedAccount,
        status = "COMPLETED",
        purchasedAtMillis = result.purchasedAtMillis,
      )
      dao.upsertThemePurchase(purchaseEntity)

      val pref = ensureThemePreference(userId)
      val unlocked = pref.unlockedThemesCsv
        .split(",")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toMutableSet()
      unlocked.add(AppThemeCatalog.DEFAULT_THEME_ID)
      unlocked.add(theme.id)

      dao.upsertThemePreference(
        pref.copy(
          selectedThemeId = theme.id,
          unlockedThemesCsv = unlocked.joinToString(","),
          preferredRegion = region.name,
          updatedAtMillis = System.currentTimeMillis(),
        )
      )
      result
    }
  }

  suspend fun updatePreferredBillingRegion(
    region: BillingRegion,
    userId: String = requireUserId(),
  ) {
    val pref = ensureThemePreference(userId)
    dao.upsertThemePreference(
      pref.copy(
        preferredRegion = region.name,
        updatedAtMillis = System.currentTimeMillis(),
      )
    )
  }

  suspend fun saveLevelWin(
    levelNumber: Int,
    worldIndex: Int,
    score: Int,
    stars: Int,
    toolsUsed: Int,
    userId: String = requireUserId(),
  ): Result<Boolean> {
    return runCatching {
      val uid = userId
      val existing = dao.getProgress(uid, levelNumber)
      val bestScore = max(existing?.bestScore ?: 0, score)
      val bestStars = max(existing?.stars ?: 0, stars.coerceIn(1, 3))
      val bestTools = if (existing == null || existing.bestToolsUsed <= 0) {
        toolsUsed
      } else {
        min(existing.bestToolsUsed, toolsUsed)
      }
      val now = System.currentTimeMillis()
      val updatedProgress = LocalLevelProgressEntity(
        userId = uid,
        levelNumber = levelNumber,
        worldIndex = worldIndex,
        bestScore = bestScore,
        stars = bestStars,
        completed = true,
        bestToolsUsed = bestTools,
        syncedToCloud = false,
        updatedAtMillis = now,
      )
      dao.upsertProgress(updatedProgress)

      // Recalculate totals from Room
      val allProgress = dao.getAllProgress(uid)
      val totalScore = allProgress.sumOf { it.bestScore }
      val totalStars = allProgress.sumOf { it.stars }
      val levelsCompleted = allProgress.count { it.completed }
      val coinGain = if (existing?.completed == true) 10 else 25 + bestStars * 10

      val fallbackName = if (uid == PREVIEW_USER_ID) "Preview Pilot" else (auth.currentUser?.displayName ?: "Kinetic Ace")
      val profile = ensureLocalProfile(uid, fallbackName)
      val updatedProfile = profile.copy(
        coins = profile.coins + coinGain,
        totalScore = totalScore,
        totalStars = totalStars,
        levelsCompleted = levelsCompleted,
        syncedToCloud = false,
        updatedAtMillis = now,
      )
      dao.upsertProfile(updatedProfile)

      if (!isCloudSession(uid)) {
        return@runCatching false
      }

      // Attempt immediate cloud sync; return true if synced, false if saved offline
      val cloudOk = pushProgressToCloud(uid, updatedProgress) &&
        pushProfileToCloud(uid, updatedProfile) &&
        pushLeaderboardToCloud(uid, updatedProfile)
      if (cloudOk) {
        dao.upsertProgress(updatedProgress.copy(syncedToCloud = true))
        dao.upsertProfile(updatedProfile.copy(syncedToCloud = true))
      }
      cloudOk
    }
  }

  suspend fun claimDailyReward(
    currentEpochDay: Int,
    userId: String = requireUserId(),
  ): Result<DailyRewardResult> {
    return runCatching {
      val uid = userId
      val fallbackName = if (uid == PREVIEW_USER_ID) "Preview Pilot" else (auth.currentUser?.displayName ?: "Kinetic Ace")
      val profile = ensureLocalProfile(uid, fallbackName)
      check(profile.lastClaimEpochDay < currentEpochDay) { "Today's reward was already claimed." }

      val nextStreak = if (profile.lastClaimEpochDay == currentEpochDay - 1) {
        profile.streakDays + 1
      } else {
        1
      }
      val tierIndex = ((nextStreak - 1) % dailyRewardTiers.size)
      val tier = dailyRewardTiers[tierIndex]
      val unlocked = profile.unlockedSkinsCsv.split(",").filter { it.isNotBlank() }.toMutableSet()
      if (tier.bonusSkin != null) {
        unlocked.add(tier.bonusSkin)
      }

      val updated = profile.copy(
        coins = profile.coins + tier.coins,
        hintsRemaining = profile.hintsRemaining + tier.hints,
        streakDays = nextStreak,
        lastClaimEpochDay = currentEpochDay,
        unlockedSkinsCsv = unlocked.joinToString(","),
        syncedToCloud = false,
        updatedAtMillis = System.currentTimeMillis(),
      )
      dao.upsertProfile(updated)
      if (isCloudSession(uid) && pushProfileToCloud(uid, updated)) {
        dao.upsertProfile(updated.copy(syncedToCloud = true))
      }
      DailyRewardResult(tier = tier, newStreak = nextStreak)
    }
  }

  suspend fun selectOrUnlockSkin(
    skinId: String,
    cost: Int,
    userId: String = requireUserId(),
  ): Result<Unit> {
    return runCatching {
      val uid = userId
      val fallbackName = if (uid == PREVIEW_USER_ID) "Preview Pilot" else (auth.currentUser?.displayName ?: "Kinetic Ace")
      val profile = ensureLocalProfile(uid, fallbackName)
      val unlocked = profile.unlockedSkinsCsv.split(",").filter { it.isNotBlank() }.toMutableSet()
      val updatedCoins = if (skinId in unlocked) {
        profile.coins
      } else {
        check(profile.coins >= cost) { "Not enough Kinetic Coins." }
        unlocked.add(skinId)
        profile.coins - cost
      }
      val updated = profile.copy(
        coins = updatedCoins,
        selectedSkin = skinId,
        unlockedSkinsCsv = unlocked.joinToString(","),
        syncedToCloud = false,
        updatedAtMillis = System.currentTimeMillis(),
      )
      dao.upsertProfile(updated)
      if (isCloudSession(uid) && pushProfileToCloud(uid, updated) && pushLeaderboardToCloud(uid, updated)) {
        dao.upsertProfile(updated.copy(syncedToCloud = true))
      }
    }
  }

  suspend fun consumeHint(userId: String = requireUserId()): Result<Int> {
    return runCatching {
      val uid = userId
      val fallbackName = if (uid == PREVIEW_USER_ID) "Preview Pilot" else (auth.currentUser?.displayName ?: "Kinetic Ace")
      val profile = ensureLocalProfile(uid, fallbackName)
      check(profile.hintsRemaining > 0) { "No hints remaining. Claim daily rewards or buy with coins!" }
      val updated = profile.copy(
        hintsRemaining = profile.hintsRemaining - 1,
        syncedToCloud = false,
        updatedAtMillis = System.currentTimeMillis(),
      )
      dao.upsertProfile(updated)
      if (isCloudSession(uid) && pushProfileToCloud(uid, updated)) {
        dao.upsertProfile(updated.copy(syncedToCloud = true))
      }
      updated.hintsRemaining
    }
  }

  suspend fun buyHintWithCoins(
    cost: Int = 50,
    userId: String = requireUserId(),
  ): Result<Int> {
    return runCatching {
      val uid = userId
      val fallbackName = if (uid == PREVIEW_USER_ID) "Preview Pilot" else (auth.currentUser?.displayName ?: "Kinetic Ace")
      val profile = ensureLocalProfile(uid, fallbackName)
      check(profile.coins >= cost) { "Need $cost coins to synthesize a hint." }
      val updated = profile.copy(
        coins = profile.coins - cost,
        hintsRemaining = profile.hintsRemaining + 1,
        syncedToCloud = false,
        updatedAtMillis = System.currentTimeMillis(),
      )
      dao.upsertProfile(updated)
      if (isCloudSession(uid) && pushProfileToCloud(uid, updated)) {
        dao.upsertProfile(updated.copy(syncedToCloud = true))
      }
      updated.hintsRemaining
    }
  }

  suspend fun updateDisplayName(
    newName: String,
    userId: String = requireUserId(),
  ): Result<Unit> {
    return runCatching {
      val uid = userId
      val clean = sanitizeHandle(newName)
      val profile = ensureLocalProfile(uid, clean)
      val updated = profile.copy(
        displayName = clean,
        syncedToCloud = false,
        updatedAtMillis = System.currentTimeMillis(),
      )
      dao.upsertProfile(updated)
      if (isCloudSession(uid) && pushProfileToCloud(uid, updated) && pushLeaderboardToCloud(uid, updated)) {
        dao.upsertProfile(updated.copy(syncedToCloud = true))
      }
    }
  }

  suspend fun saveCustomLevel(
    authorName: String,
    level: PuzzleLevel,
    title: String,
    description: String,
    published: Boolean,
    verified: Boolean,
    existingId: String? = null,
    userId: String = requireUserId(),
  ): Result<String> {
    return runCatching {
      val uid = userId
      val levelId = existingId?.takeIf { it.isNotBlank() } ?: "lvl_${UUID.randomUUID().toString().replace("-", "").take(16)}"
      val cleanTitle = title.trim().ifBlank { "Custom Chamber" }.take(50)
      val cleanDesc = description.trim().take(160)
      val cleanAuthor = sanitizeHandle(authorName)
      val existingLocal = dao.getCustomLevel(levelId)
      val now = System.currentTimeMillis()
      val entity = LocalCustomLevelEntity(
        id = levelId,
        authorId = uid,
        authorName = cleanAuthor,
        title = cleanTitle,
        description = cleanDesc,
        gravityMode = level.gravityMode.name,
        levelJson = LevelSerializer.toJsonString(level),
        published = published && isCloudSession(uid),
        verified = verified,
        playsCount = existingLocal?.playsCount ?: 0,
        likesCount = existingLocal?.likesCount ?: 0,
        syncedToCloud = false,
        updatedAtMillis = now,
      )
      dao.upsertCustomLevel(entity)
      if (isCloudSession(uid) && pushCustomLevelToCloud(uid, entity, level)) {
        dao.upsertCustomLevel(entity.copy(syncedToCloud = true))
      }
      levelId
    }
  }

  suspend fun deleteCustomLevel(
    levelId: String,
    userId: String = requireUserId(),
  ): Result<Unit> {
    return runCatching {
      val uid = userId
      dao.deleteCustomLevel(levelId)
      if (!isCloudSession(uid)) return@runCatching
      val docRef = db.collection("custom_levels").document(levelId)
      try {
        val snap = docRef.get().await()
        if (snap.exists() && snap.getString("authorId") == uid) {
          docRef.delete().await()
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.DELETE, docRef.path)
      }
    }
  }

  suspend fun incrementCommunityPlay(levelId: String) {
    if (auth.currentUser == null) return
    val docRef = db.collection("custom_levels").document(levelId)
    try {
      docRef.update(
        mapOf(
          "playsCount" to FieldValue.increment(1),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      ).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
    }
  }

  suspend fun incrementCommunityLike(levelId: String): Result<Unit> {
    return runCatching {
      requireUserId()
      val docRef = db.collection("custom_levels").document(levelId)
      try {
        docRef.update(
          mapOf(
            "likesCount" to FieldValue.increment(1),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        ).await()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, docRef.path)
        throw e
      }
    }
  }

  suspend fun syncWithCloud(defaultDisplayName: String): Result<SyncSummary> {
    return runCatching {
      val uid = requireUserId()
      mergePreviewProgressIntoUser(uid, defaultDisplayName)
      var uploaded = 0
      var downloaded = 0

      // 1. Pull remote level progress & merge with Room
      val progressPath = "users/$uid/level_progress"
      val remoteProgressSnap = try {
        db.collection("users").document(uid).collection("level_progress").get().await()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.LIST, progressPath)
        throw e
      }

      val remoteByLevel = remoteProgressSnap.documents.mapNotNull { doc ->
        doc.toObject(CloudLevelProgress::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      }.associateBy { it.levelNumber.toInt() }

      val localByLevel = dao.getAllProgress(uid).associateBy { it.levelNumber }
      val allNumbers = (remoteByLevel.keys + localByLevel.keys).distinct()
      val toUpsertLocal = mutableListOf<LocalLevelProgressEntity>()

      for (num in allNumbers) {
        val local = localByLevel[num]
        val remote = remoteByLevel[num]
        if (remote != null && (local == null || remote.bestScore > local.bestScore || remote.stars > local.stars)) {
          val merged = LocalLevelProgressEntity(
            userId = uid,
            levelNumber = num,
            worldIndex = remote.worldIndex.toInt(),
            bestScore = max(local?.bestScore ?: 0, remote.bestScore.toInt()),
            stars = max(local?.stars ?: 0, remote.stars.toInt()),
            completed = (local?.completed == true) || remote.completed,
            bestToolsUsed = listOfNotNull(
              local?.bestToolsUsed?.takeIf { it > 0 },
              remote.bestToolsUsed.toInt().takeIf { it > 0 },
            ).minOrNull() ?: 1,
            syncedToCloud = local == null || local.bestScore <= remote.bestScore.toInt(),
            updatedAtMillis = System.currentTimeMillis(),
          )
          toUpsertLocal.add(merged)
          downloaded++
        }
      }
      if (toUpsertLocal.isNotEmpty()) {
        dao.upsertAllProgress(toUpsertLocal)
      }

      // Push any unsynced local level progress
      val unsyncedProgress = dao.getUnsyncedProgress(uid)
      for (item in unsyncedProgress) {
        if (pushProgressToCloud(uid, item)) {
          dao.upsertProgress(item.copy(syncedToCloud = true))
          uploaded++
        }
      }

      // 2. Pull & merge profile
      val localProfile = ensureLocalProfile(uid, defaultDisplayName)
      val userDocRef = db.collection("users").document(uid)
      val remoteProfileSnap = try {
        userDocRef.get().await()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.GET, userDocRef.path)
        throw e
      }

      val remoteProfile = if (remoteProfileSnap.exists()) {
        remoteProfileSnap.toObject(CloudPlayerProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      } else null

      val allMergedProgress = dao.getAllProgress(uid)
      val computedScore = allMergedProgress.sumOf { it.bestScore }
      val computedStars = allMergedProgress.sumOf { it.stars }
      val computedCompleted = allMergedProgress.count { it.completed }

      val mergedSkins = (
        localProfile.unlockedSkinsCsv.split(",") +
          (remoteProfile?.unlockedSkins ?: emptyList()) +
          listOf("cyan")
        ).map { it.trim() }.filter { it.isNotBlank() }.distinct()

      val mergedProfile = if (remoteProfile != null) {
        localProfile.copy(
          displayName = if (localProfile.updatedAtMillis >= (remoteProfile.updatedAt?.toDate()?.time ?: 0L)) {
            localProfile.displayName
          } else {
            remoteProfile.displayName
          },
          coins = max(localProfile.coins, remoteProfile.coins.toInt()),
          hintsRemaining = max(localProfile.hintsRemaining, remoteProfile.hintsRemaining.toInt()),
          streakDays = max(localProfile.streakDays, remoteProfile.streakDays.toInt()),
          lastClaimEpochDay = max(localProfile.lastClaimEpochDay, remoteProfile.lastClaimEpochDay.toInt()),
          unlockedSkinsCsv = mergedSkins.joinToString(","),
          totalScore = max(computedScore, remoteProfile.totalScore.toInt()),
          totalStars = max(computedStars, remoteProfile.totalStars.toInt()),
          levelsCompleted = max(computedCompleted, remoteProfile.levelsCompleted.toInt()),
          syncedToCloud = false,
          updatedAtMillis = System.currentTimeMillis(),
        )
      } else {
        localProfile.copy(
          totalScore = computedScore,
          totalStars = computedStars,
          levelsCompleted = computedCompleted,
          syncedToCloud = false,
        )
      }
      dao.upsertProfile(mergedProfile)

      if (pushProfileToCloud(uid, mergedProfile) && pushLeaderboardToCloud(uid, mergedProfile)) {
        dao.upsertProfile(mergedProfile.copy(syncedToCloud = true))
        uploaded++
      }

      // 3. Push unsynced custom levels & pull author's cloud levels
      val unsyncedCustom = dao.getUnsyncedCustomLevels(uid)
      for (custom in unsyncedCustom) {
        val puzzle = LevelSerializer.fromEntity(custom)
        if (pushCustomLevelToCloud(uid, custom, puzzle)) {
          dao.upsertCustomLevel(custom.copy(syncedToCloud = true))
          uploaded++
        }
      }

      val myCloudLevels = try {
        db.collection("custom_levels").whereEqualTo("authorId", uid).get().await()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.LIST, "custom_levels")
        null
      }
      myCloudLevels?.documents?.forEach { doc ->
        val cloud = doc.toObject(CloudCustomLevel::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        if (cloud != null && dao.getCustomLevel(cloud.id) == null) {
          val puzzle = LevelSerializer.fromFirestoreMaps(
            id = cloud.id,
            authorName = cloud.authorName,
            title = cloud.title,
            description = cloud.description,
            gravityModeStr = cloud.gravityMode,
            emitter = cloud.emitter,
            goal = cloud.goal,
            toolBudget = cloud.toolBudget,
            elements = cloud.elements,
            stars = cloud.stars,
          )
          dao.upsertCustomLevel(
            LocalCustomLevelEntity(
              id = cloud.id,
              authorId = cloud.authorId,
              authorName = cloud.authorName,
              title = cloud.title,
              description = cloud.description,
              gravityMode = cloud.gravityMode,
              levelJson = LevelSerializer.toJsonString(puzzle),
              published = cloud.published,
              verified = cloud.verified,
              playsCount = cloud.playsCount.toInt(),
              likesCount = cloud.likesCount.toInt(),
              syncedToCloud = true,
              updatedAtMillis = cloud.updatedAt?.toDate()?.time ?: System.currentTimeMillis(),
            )
          )
          downloaded++
        }
      }

      SyncSummary(uploadedCount = uploaded, downloadedCount = downloaded)
    }
  }

  private suspend fun pushProfileToCloud(uid: String, profile: LocalPlayerProfileEntity): Boolean {
    val docRef = db.collection("users").document(uid)
    return try {
      val exists = docRef.get().await().exists()
      val unlocked = profile.unlockedSkinsCsv.split(",").map { it.trim() }.filter { it.isNotBlank() }
        .ifEmpty { listOf("cyan") }.take(10)
      if (!exists) {
        val payload = mapOf(
          "userId" to uid,
          "displayName" to sanitizeHandle(profile.displayName),
          "coins" to profile.coins.toLong(),
          "hintsRemaining" to profile.hintsRemaining.toLong(),
          "streakDays" to profile.streakDays.toLong(),
          "lastClaimEpochDay" to profile.lastClaimEpochDay.toLong(),
          "selectedSkin" to profile.selectedSkin,
          "unlockedSkins" to unlocked,
          "totalScore" to profile.totalScore.toLong(),
          "totalStars" to profile.totalStars.toLong(),
          "levelsCompleted" to profile.levelsCompleted.toLong(),
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
        docRef.set(payload).await()
      } else {
        val payload = mapOf(
          "displayName" to sanitizeHandle(profile.displayName),
          "coins" to profile.coins.toLong(),
          "hintsRemaining" to profile.hintsRemaining.toLong(),
          "streakDays" to profile.streakDays.toLong(),
          "lastClaimEpochDay" to profile.lastClaimEpochDay.toLong(),
          "selectedSkin" to profile.selectedSkin,
          "unlockedSkins" to unlocked,
          "totalScore" to profile.totalScore.toLong(),
          "totalStars" to profile.totalStars.toLong(),
          "levelsCompleted" to profile.levelsCompleted.toLong(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
        docRef.update(payload).await()
      }
      true
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path)
      false
    }
  }

  private suspend fun pushProgressToCloud(uid: String, item: LocalLevelProgressEntity): Boolean {
    val docId = "level_${item.levelNumber}"
    val docRef = db.collection("users").document(uid).collection("level_progress").document(docId)
    return try {
      val exists = docRef.get().await().exists()
      if (!exists) {
        val payload = mapOf(
          "userId" to uid,
          "levelNumber" to item.levelNumber.toLong(),
          "worldIndex" to item.worldIndex.toLong(),
          "bestScore" to item.bestScore.toLong(),
          "stars" to item.stars.toLong(),
          "completed" to item.completed,
          "bestToolsUsed" to item.bestToolsUsed.toLong(),
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
        docRef.set(payload).await()
      } else {
        val payload = mapOf(
          "worldIndex" to item.worldIndex.toLong(),
          "bestScore" to item.bestScore.toLong(),
          "stars" to item.stars.toLong(),
          "completed" to item.completed,
          "bestToolsUsed" to item.bestToolsUsed.toLong(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
        docRef.update(payload).await()
      }
      true
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path)
      false
    }
  }

  private suspend fun pushLeaderboardToCloud(uid: String, profile: LocalPlayerProfileEntity): Boolean {
    val docRef = db.collection("leaderboard").document(uid)
    return try {
      val exists = docRef.get().await().exists()
      val cleanName = sanitizeHandle(profile.displayName).take(30)
      if (!exists) {
        val payload = mapOf(
          "userId" to uid,
          "displayName" to cleanName,
          "avatarBadge" to profile.selectedSkin,
          "totalScore" to profile.totalScore.toLong(),
          "totalStars" to profile.totalStars.toLong(),
          "levelsCompleted" to profile.levelsCompleted.toLong(),
          "isPublic" to true,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
        docRef.set(payload).await()
      } else {
        val payload = mapOf(
          "displayName" to cleanName,
          "avatarBadge" to profile.selectedSkin,
          "totalScore" to profile.totalScore.toLong(),
          "totalStars" to profile.totalStars.toLong(),
          "levelsCompleted" to profile.levelsCompleted.toLong(),
          "isPublic" to true,
          "updatedAt" to FieldValue.serverTimestamp(),
        )
        docRef.update(payload).await()
      }
      true
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path)
      false
    }
  }

  private suspend fun pushCustomLevelToCloud(
    uid: String,
    entity: LocalCustomLevelEntity,
    level: PuzzleLevel,
  ): Boolean {
    val docRef = db.collection("custom_levels").document(entity.id)
    val maps = LevelSerializer.toFirestoreMaps(level)
    return try {
      val exists = docRef.get().await().exists()
      if (!exists) {
        val payload = mapOf(
          "authorId" to uid,
          "authorName" to sanitizeHandle(entity.authorName).take(30),
          "title" to entity.title.take(50),
          "description" to entity.description.take(160),
          "gravityMode" to entity.gravityMode,
          "emitter" to maps.emitter,
          "goal" to maps.goal,
          "toolBudget" to maps.toolBudget,
          "elements" to maps.elements,
          "stars" to maps.stars,
          "published" to entity.published,
          "verified" to entity.verified,
          "playsCount" to 0L,
          "likesCount" to 0L,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
        docRef.set(payload).await()
      } else {
        val payload = mapOf(
          "authorName" to sanitizeHandle(entity.authorName).take(30),
          "title" to entity.title.take(50),
          "description" to entity.description.take(160),
          "gravityMode" to entity.gravityMode,
          "emitter" to maps.emitter,
          "goal" to maps.goal,
          "toolBudget" to maps.toolBudget,
          "elements" to maps.elements,
          "stars" to maps.stars,
          "published" to entity.published,
          "verified" to entity.verified,
          "updatedAt" to FieldValue.serverTimestamp(),
        )
        docRef.update(payload).await()
      }
      true
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path)
      false
    }
  }

  private suspend fun mergePreviewProgressIntoUser(uid: String, defaultDisplayName: String) {
    if (uid == PREVIEW_USER_ID) return
    val previewProgress = dao.getAllProgress(PREVIEW_USER_ID)
    if (previewProgress.isNotEmpty()) {
      val existingByLevel = dao.getAllProgress(uid).associateBy { it.levelNumber }
      val migrated = previewProgress.mapNotNull { item ->
        val current = existingByLevel[item.levelNumber]
        if (current == null || item.bestScore > current.bestScore || item.stars > current.stars) {
          item.copy(
            userId = uid,
            bestScore = max(current?.bestScore ?: 0, item.bestScore),
            stars = max(current?.stars ?: 0, item.stars),
            completed = (current?.completed == true) || item.completed,
            syncedToCloud = false,
          )
        } else null
      }
      if (migrated.isNotEmpty()) {
        dao.upsertAllProgress(migrated)
      }
      // Clear preview progress after migrating so it isn't merged twice
      dao.upsertAllProgress(previewProgress.map { it.copy(syncedToCloud = true) })
    }

    val previewLevels = dao.getUnsyncedCustomLevels(PREVIEW_USER_ID)
    if (previewLevels.isNotEmpty()) {
      val cleanName = sanitizeHandle(defaultDisplayName)
      dao.upsertCustomLevels(
        previewLevels.map {
          it.copy(authorId = uid, authorName = cleanName, syncedToCloud = false)
        }
      )
    }

    val previewTheme = dao.getThemePreference(PREVIEW_USER_ID)
    if (previewTheme != null) {
      val existingTheme = ensureThemePreference(uid)
      val mergedUnlocked = (
        existingTheme.unlockedThemesCsv.split(",") +
          previewTheme.unlockedThemesCsv.split(",") +
          listOf(AppThemeCatalog.DEFAULT_THEME_ID)
        ).map { it.trim() }.filter { it.isNotBlank() }.distinct()
      dao.upsertThemePreference(
        existingTheme.copy(
          selectedThemeId = if (existingTheme.selectedThemeId == AppThemeCatalog.DEFAULT_THEME_ID) {
            previewTheme.selectedThemeId
          } else {
            existingTheme.selectedThemeId
          },
          unlockedThemesCsv = mergedUnlocked.joinToString(","),
          preferredRegion = previewTheme.preferredRegion,
          updatedAtMillis = System.currentTimeMillis(),
        )
      )
    }

    val previewPurchases = dao.getThemePurchases(PREVIEW_USER_ID)
    previewPurchases.forEach { purchase ->
      dao.upsertThemePurchase(purchase.copy(userId = uid))
    }
  }

  private fun sanitizeHandle(raw: String): String {
    val cleaned = raw.trim().replace(Regex("[\\r\\n]"), " ")
    return if (cleaned.isBlank()) "Kinetic Ace" else cleaned.take(30)
  }

  companion object {
    const val PREVIEW_USER_ID = "preview_pilot"

    val dailyRewardTiers = listOf(
      DailyRewardTier(day = 1, coins = 75, hints = 0),
      DailyRewardTier(day = 2, coins = 120, hints = 1),
      DailyRewardTier(day = 3, coins = 180, hints = 0),
      DailyRewardTier(day = 4, coins = 250, hints = 1, bonusSkin = "amber"),
      DailyRewardTier(day = 5, coins = 350, hints = 1, bonusSkin = "emerald"),
      DailyRewardTier(day = 6, coins = 500, hints = 2, bonusSkin = "magenta"),
      DailyRewardTier(day = 7, coins = 1000, hints = 3, bonusSkin = "prism"),
    )
  }
}
