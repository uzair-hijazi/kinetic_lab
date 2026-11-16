package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.billing.BillingRegion
import com.example.billing.PaymentMethod
import com.example.billing.PurchaseTransactionResult
import com.example.data.local.LocalCustomLevelEntity
import com.example.data.local.LocalLevelProgressEntity
import com.example.data.local.LocalPlayerProfileEntity
import com.example.data.local.LocalThemePreferenceEntity
import com.example.data.local.LocalThemePurchaseEntity
import com.example.data.remote.CloudCustomLevel
import com.example.data.remote.CloudLeaderboardEntry
import com.example.data.repository.DailyRewardResult
import com.example.data.repository.KineticRepository
import com.example.physics.PuzzleLevel
import com.example.ui.theme.AppThemeCatalog
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>
  data class Success<T>(val data: T) : UiState<T>
  data class Error(val message: String) : UiState<Nothing>
}

sealed interface SyncBannerState {
  data object Synced : SyncBannerState
  data object Syncing : SyncBannerState
  data object OfflineLocal : SyncBannerState
  data class Warning(val message: String) : SyncBannerState
}

class KineticViewModel(
  private val repository: KineticRepository,
  private val currentUserId: String,
  private val defaultDisplayName: String,
  isOnlineFlow: kotlinx.coroutines.flow.Flow<Boolean>,
) : ViewModel() {

  val isGuestPreview: Boolean = currentUserId == KineticRepository.PREVIEW_USER_ID

  val isOnline: StateFlow<Boolean> = isOnlineFlow.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
    initialValue = true,
  )

  val profile: StateFlow<LocalPlayerProfileEntity?> = repository.observeLocalProfile(currentUserId)
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
      initialValue = null,
    )

  val progressMap: StateFlow<Map<Int, LocalLevelProgressEntity>> =
    repository.observeLocalProgress(currentUserId)
      .map { list -> list.associateBy { it.levelNumber } }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = emptyMap(),
      )

  val myCustomLevels: StateFlow<List<LocalCustomLevelEntity>> =
    repository.observeMyCustomLevels(currentUserId)
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = emptyList(),
      )

  val themePreference: StateFlow<LocalThemePreferenceEntity?> =
    repository.observeThemePreference(currentUserId)
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = null,
      )

  val themePurchases: StateFlow<List<LocalThemePurchaseEntity>> =
    repository.observeThemePurchases(currentUserId)
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = emptyList(),
      )

  val leaderboardState: StateFlow<UiState<List<CloudLeaderboardEntry>>> = if (isGuestPreview) {
    MutableStateFlow<UiState<List<CloudLeaderboardEntry>>>(
      UiState.Error("Preview Mode active — sign in with Google to access live global rankings.")
    ).asStateFlow()
  } else {
    repository.observeLeaderboard()
      .map<List<CloudLeaderboardEntry>, UiState<List<CloudLeaderboardEntry>>> { UiState.Success(it) }
      .catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
          .filterIsInstance<FirebaseFirestoreException>()
          .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing leaderboard", rootError)
        emit(UiState.Error(rootError.localizedMessage ?: "Leaderboard unavailable offline."))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
      )
  }

  val communityLevelsState: StateFlow<UiState<List<CloudCustomLevel>>> = if (isGuestPreview) {
    MutableStateFlow<UiState<List<CloudCustomLevel>>>(
      UiState.Error("Preview Mode active — sign in with Google to browse & publish community levels.")
    ).asStateFlow()
  } else {
    repository.observeCommunityLevels()
      .map<List<CloudCustomLevel>, UiState<List<CloudCustomLevel>>> { UiState.Success(it) }
      .catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
          .filterIsInstance<FirebaseFirestoreException>()
          .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing community levels", rootError)
        emit(UiState.Error(rootError.localizedMessage ?: "Community levels unavailable offline."))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
      )
  }

  private val _syncState = MutableStateFlow<SyncBannerState>(
    if (isGuestPreview) SyncBannerState.OfflineLocal else SyncBannerState.Synced
  )
  val syncState: StateFlow<SyncBannerState> = _syncState.asStateFlow()

  private val _toastMessage = MutableStateFlow<String?>(null)
  val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

  private val _dailyRewardBanner = MutableStateFlow<DailyRewardResult?>(null)
  val dailyRewardBanner: StateFlow<DailyRewardResult?> = _dailyRewardBanner.asStateFlow()

  init {
    viewModelScope.launch {
      repository.ensureLocalProfile(currentUserId, defaultDisplayName)
      repository.ensureThemePreference(currentUserId)
      if (!isGuestPreview) {
        syncNow(silent = true)
      } else {
        _syncState.value = SyncBannerState.OfflineLocal
      }
    }
    viewModelScope.launch {
      isOnline.collect { online ->
        if (online && !isGuestPreview) {
          syncNow(silent = true)
        } else {
          _syncState.value = SyncBannerState.OfflineLocal
        }
      }
    }
  }

  fun currentEpochDay(): Int = (System.currentTimeMillis() / 86_400_000L).toInt()

  fun isDailyRewardClaimable(prof: LocalPlayerProfileEntity?): Boolean {
    return prof != null && prof.lastClaimEpochDay < currentEpochDay()
  }

  fun syncNow(silent: Boolean = false) {
    if (isGuestPreview) {
      _syncState.value = SyncBannerState.OfflineLocal
      if (!silent) {
        _toastMessage.value = "Preview Mode — progress saved locally. Sign in from Lab for cloud sync."
      }
      return
    }
    if (!isOnline.value) {
      _syncState.value = SyncBannerState.OfflineLocal
      if (!silent) _toastMessage.value = "Offline — progress saved locally and queued for cloud sync."
      return
    }
    viewModelScope.launch {
      _syncState.value = SyncBannerState.Syncing
      val res = repository.syncWithCloud(defaultDisplayName)
      if (res.isSuccess) {
        _syncState.value = SyncBannerState.Synced
        if (!silent) {
          val summary = res.getOrNull()
          _toastMessage.value = "Cloud synced (${summary?.uploadedCount ?: 0} up, ${summary?.downloadedCount ?: 0} down)"
        }
      } else {
        _syncState.value = if (isOnline.value) {
          SyncBannerState.Warning("Cloud sync pending")
        } else {
          SyncBannerState.OfflineLocal
        }
        if (!silent) {
          _toastMessage.value = "Saved locally; cloud sync will retry automatically."
        }
      }
    }
  }

  fun recordCampaignWin(
    levelNumber: Int,
    worldIndex: Int,
    score: Int,
    stars: Int,
    toolsUsed: Int,
  ) {
    viewModelScope.launch {
      val result = repository.saveLevelWin(
        levelNumber = levelNumber,
        worldIndex = worldIndex,
        score = score,
        stars = stars,
        toolsUsed = toolsUsed,
        userId = currentUserId,
      )
      if (result.isSuccess) {
        val synced = result.getOrDefault(false)
        _syncState.value = if (synced) SyncBannerState.Synced else SyncBannerState.OfflineLocal
      }
    }
  }

  fun claimDailyReward() {
    viewModelScope.launch {
      val res = repository.claimDailyReward(currentEpochDay(), userId = currentUserId)
      if (res.isSuccess) {
        _dailyRewardBanner.value = res.getOrNull()
      } else {
        _toastMessage.value = res.exceptionOrNull()?.message ?: "Reward already claimed today."
      }
    }
  }

  fun dismissDailyRewardBanner() {
    _dailyRewardBanner.value = null
  }

  fun selectOrUnlockSkin(skinId: String, cost: Int) {
    viewModelScope.launch {
      val res = repository.selectOrUnlockSkin(skinId, cost, userId = currentUserId)
      if (res.isFailure) {
        _toastMessage.value = res.exceptionOrNull()?.message ?: "Cannot unlock skin."
      }
    }
  }

  fun selectOrUnlockTheme(themeId: String) {
    viewModelScope.launch {
      val res = repository.selectOrUnlockCoinTheme(themeId, userId = currentUserId)
      if (res.isSuccess) {
        val theme = res.getOrThrow()
        _toastMessage.value = "${theme.name} theme active!"
      } else {
        _toastMessage.value = res.exceptionOrNull()?.message ?: "Cannot apply theme."
      }
    }
  }

  suspend fun purchasePremiumTheme(
    themeId: String,
    region: BillingRegion,
    paymentMethod: PaymentMethod,
    mobileNumber: String,
    cnicLast6: String,
  ): Result<PurchaseTransactionResult> {
    val accountLabel = profile.value?.displayName?.let { "$it (G Pay)" } ?: "Google Pay Wallet"
    val res = repository.purchasePremiumTheme(
      themeId = themeId,
      region = region,
      paymentMethod = paymentMethod,
      mobileNumber = mobileNumber,
      cnicLast6 = cnicLast6,
      accountLabel = accountLabel,
      userId = currentUserId,
    )
    if (res.isSuccess) {
      val theme = AppThemeCatalog.getTheme(themeId)
      val receipt = res.getOrThrow()
      _toastMessage.value = "${theme.name} unlocked via ${receipt.paymentMethod.displayName}!"
    }
    return res
  }

  fun setPreferredRegion(region: BillingRegion) {
    viewModelScope.launch {
      repository.updatePreferredBillingRegion(region, userId = currentUserId)
    }
  }

  suspend fun tryConsumeHint(): Boolean {
    val res = repository.consumeHint(userId = currentUserId)
    if (res.isFailure) {
      _toastMessage.value = res.exceptionOrNull()?.message ?: "No hints available."
      return false
    }
    return true
  }

  fun buyHintWithCoins() {
    viewModelScope.launch {
      val res = repository.buyHintWithCoins(userId = currentUserId)
      _toastMessage.value = if (res.isSuccess) {
        "Synthesized +1 Hint token!"
      } else {
        res.exceptionOrNull()?.message ?: "Not enough coins."
      }
    }
  }

  fun updateDisplayName(name: String) {
    viewModelScope.launch {
      val res = repository.updateDisplayName(name, userId = currentUserId)
      _toastMessage.value = if (res.isSuccess) "Pilot handle updated!" else "Failed to update handle."
    }
  }

  fun saveCustomLevel(
    level: PuzzleLevel,
    title: String,
    description: String,
    published: Boolean,
    verified: Boolean,
    existingId: String?,
    onSaved: (String) -> Unit = {},
  ) {
    viewModelScope.launch {
      val author = profile.value?.displayName ?: defaultDisplayName
      val res = repository.saveCustomLevel(
        authorName = author,
        level = level,
        title = title,
        description = description,
        published = published,
        verified = verified,
        existingId = existingId,
        userId = currentUserId,
      )
      if (res.isSuccess) {
        val id = res.getOrThrow()
        _toastMessage.value = when {
          isGuestPreview && published -> "Saved to My Lab (sign in to publish to Community)"
          published -> "Level published & synced!"
          else -> "Level saved to My Lab!"
        }
        onSaved(id)
      } else {
        _toastMessage.value = res.exceptionOrNull()?.message ?: "Save failed."
      }
    }
  }

  fun deleteCustomLevel(levelId: String) {
    viewModelScope.launch {
      repository.deleteCustomLevel(levelId, userId = currentUserId)
      _toastMessage.value = "Level deleted."
    }
  }

  fun recordCommunityPlay(levelId: String) {
    viewModelScope.launch {
      repository.incrementCommunityPlay(levelId)
    }
  }

  fun likeCommunityLevel(levelId: String) {
    viewModelScope.launch {
      val res = repository.incrementCommunityLike(levelId)
      _toastMessage.value = if (res.isSuccess) "Appreciated level!" else "Sign in & go online to like levels."
    }
  }

  fun clearToast() {
    _toastMessage.value = null
  }

  private companion object {
    const val TAG = "KineticVM"
    const val STOP_TIMEOUT_MILLIS = 5000L
  }
}
