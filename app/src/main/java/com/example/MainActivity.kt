package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Architecture
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.Architecture
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.authStateFlow
import com.example.billing.BillingRegion
import com.example.data.NetworkMonitor
import com.example.data.local.KineticDatabase
import com.example.data.local.LocalCustomLevelEntity
import com.example.data.repository.KineticRepository
import com.example.physics.LevelCatalog
import com.example.physics.PuzzleLevel
import com.example.ui.KineticViewModel
import com.example.ui.components.ThemeCheckoutDialog
import com.example.ui.components.ThemeStoreModalSheet
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CampaignScreen
import com.example.ui.screens.EditorHubScreen
import com.example.ui.screens.LeaderboardScreen
import com.example.ui.screens.LevelEditorScreen
import com.example.ui.screens.PlayLevelScreen
import com.example.ui.screens.ProfileAndRewardsScreen
import com.example.ui.theme.AppThemeCatalog
import com.example.ui.theme.AppThemeDefinition
import com.example.ui.theme.KineticLabTheme
import com.example.ui.theme.LocalKineticColors
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      KineticLabTheme {
        AppNavigation()
      }
    }
  }
}

private enum class MainTab(
  val label: String,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
  val tag: String,
) {
  CAMPAIGN("Campaign", Icons.Rounded.Explore, Icons.Outlined.Explore, "nav_campaign"),
  STUDIO("Studio", Icons.Rounded.Architecture, Icons.Outlined.Architecture, "nav_studio"),
  RANKS("Ranks", Icons.Rounded.EmojiEvents, Icons.Outlined.EmojiEvents, "nav_ranks"),
  PROFILE("Lab", Icons.Rounded.Person, Icons.Outlined.Person, "nav_profile"),
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
  val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
  var bypassLoginForPreview by rememberSaveable { mutableStateOf(true) }
  var showSignInScreen by rememberSaveable { mutableStateOf(false) }
  val user = currentUser

  LaunchedEffect(user?.uid) {
    if (user != null) {
      showSignInScreen = false
    }
  }

  when {
    user != null -> {
      key(user.uid) {
        MainFeatureHost(
          currentUserId = user.uid,
          defaultDisplayName = user.displayName ?: "Kinetic Ace",
          isGuestPreview = false,
          onRequestSignIn = { showSignInScreen = true },
        )
      }
    }
    showSignInScreen || !bypassLoginForPreview -> {
      AuthScreen(
        onAuthSuccess = { showSignInScreen = false },
        onContinueAsGuest = {
          bypassLoginForPreview = true
          showSignInScreen = false
        },
      )
    }
    else -> {
      key(KineticRepository.PREVIEW_USER_ID) {
        MainFeatureHost(
          currentUserId = KineticRepository.PREVIEW_USER_ID,
          defaultDisplayName = "Preview Pilot",
          isGuestPreview = true,
          onRequestSignIn = { showSignInScreen = true },
        )
      }
    }
  }
}

@Composable
private fun MainFeatureHost(
  currentUserId: String,
  defaultDisplayName: String,
  isGuestPreview: Boolean,
  onRequestSignIn: () -> Unit,
) {
  val viewModel: KineticViewModel = viewModel(
    key = currentUserId,
    factory = viewModelFactory {
      initializer {
        val app = checkNotNull(this[APPLICATION_KEY]) {
          "APPLICATION_KEY missing from CreationExtras"
        }
        val databaseId = app.getString(R.string.firestore_database_id)
        val db = FirebaseFirestore.getInstance(databaseId)
        val dao = KineticDatabase.getInstance(app).kineticDao()
        val repo = KineticRepository(db, dao)
        KineticViewModel(
          repository = repo,
          currentUserId = currentUserId,
          defaultDisplayName = defaultDisplayName,
          isOnlineFlow = NetworkMonitor.observeIsOnline(app),
        )
      }
    },
  )

  val profile by viewModel.profile.collectAsStateWithLifecycle()
  val progressMap by viewModel.progressMap.collectAsStateWithLifecycle()
  val myCustomLevels by viewModel.myCustomLevels.collectAsStateWithLifecycle()
  val themePreference by viewModel.themePreference.collectAsStateWithLifecycle()
  val themePurchases by viewModel.themePurchases.collectAsStateWithLifecycle()
  val leaderboardState by viewModel.leaderboardState.collectAsStateWithLifecycle()
  val communityState by viewModel.communityLevelsState.collectAsStateWithLifecycle()
  val syncState by viewModel.syncState.collectAsStateWithLifecycle()
  val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()
  val dailyRewardBanner by viewModel.dailyRewardBanner.collectAsStateWithLifecycle()

  val activeThemeId = themePreference?.selectedThemeId ?: AppThemeCatalog.DEFAULT_THEME_ID
  val preferredRegion = remember(themePreference?.preferredRegion) {
    BillingRegion.entries.firstOrNull { it.name == themePreference?.preferredRegion }
      ?: BillingRegion.WORLDWIDE
  }

  val snackbarHostState = remember { SnackbarHostState() }
  LaunchedEffect(toastMessage) {
    val msg = toastMessage ?: return@LaunchedEffect
    snackbarHostState.showSnackbar(msg)
    viewModel.clearToast()
  }

  var activeTab by rememberSaveable { mutableStateOf(MainTab.CAMPAIGN) }
  var activeCampaignLevelNum by rememberSaveable { mutableStateOf<Int?>(null) }
  var activeCustomPlay by remember { mutableStateOf<Pair<PuzzleLevel, String?>?>(null) }
  var isEditingLevel by remember { mutableStateOf(false) }
  var editingEntity by remember { mutableStateOf<LocalCustomLevelEntity?>(null) }
  var showThemeSheet by remember { mutableStateOf(false) }
  var checkoutTheme by remember { mutableStateOf<AppThemeDefinition?>(null) }

  val dailyClaimable = viewModel.isDailyRewardClaimable(profile)

  KineticLabTheme(themeId = activeThemeId) {
    val kinetic = LocalKineticColors.current

    // Fullscreen Gameplay / Studio Editor Routes
    val campaignNum = activeCampaignLevelNum
    if (campaignNum != null) {
      val puzzle = remember(campaignNum) { LevelCatalog.getLevel(campaignNum) }
      PlayLevelScreen(
        level = puzzle,
        orbSkin = profile?.selectedSkin ?: "cyan",
        hintsRemaining = profile?.hintsRemaining ?: 0,
        playerName = profile?.displayName ?: defaultDisplayName,
        totalStars = profile?.totalStars ?: 0,
        levelsCompleted = profile?.levelsCompleted ?: 0,
        streakDays = profile?.streakDays ?: 0,
        onUseHint = { viewModel.tryConsumeHint() },
        onLevelSolved = { score, stars, toolsUsed ->
          viewModel.recordCampaignWin(
            levelNumber = puzzle.number,
            worldIndex = puzzle.worldIndex,
            score = score,
            stars = stars,
            toolsUsed = toolsUsed,
          )
        },
        onNextLevel = if (campaignNum < LevelCatalog.TOTAL_LEVELS) {
          { activeCampaignLevelNum = campaignNum + 1 }
        } else null,
        onBack = { activeCampaignLevelNum = null },
      )
      return@KineticLabTheme
    }

  val customPlay = activeCustomPlay
  if (customPlay != null) {
    val (puzzle, communityId) = customPlay
    PlayLevelScreen(
      level = puzzle,
      orbSkin = profile?.selectedSkin ?: "cyan",
      hintsRemaining = 0,
      playerName = profile?.displayName ?: defaultDisplayName,
      totalStars = profile?.totalStars ?: 0,
      levelsCompleted = profile?.levelsCompleted ?: 0,
      streakDays = profile?.streakDays ?: 0,
      onUseHint = { false },
      onLevelSolved = { _, _, _ ->
        if (communityId != null) {
          viewModel.recordCommunityPlay(communityId)
        }
      },
      onNextLevel = null,
      onBack = { activeCustomPlay = null },
    )
    return@KineticLabTheme
  }

  if (isEditingLevel) {
    LevelEditorScreen(
      existingEntity = editingEntity,
      authorName = profile?.displayName ?: defaultDisplayName,
      orbSkin = profile?.selectedSkin ?: "cyan",
      onSaveLevel = { level, title, desc, published, verified, existingId ->
        viewModel.saveCustomLevel(
          level = level,
          title = title,
          description = desc,
          published = published,
          verified = verified,
          existingId = existingId,
          onSaved = {
            isEditingLevel = false
            editingEntity = null
          },
        )
      },
      onBack = {
        isEditingLevel = false
        editingEntity = null
      },
    )
    return@KineticLabTheme
  }

  // Adaptive Tablet NavigationRail vs Mobile NavigationBar
  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useRail = maxWidth >= 600.dp

    Scaffold(
      contentWindowInsets = WindowInsets.safeDrawing,
      snackbarHost = { SnackbarHost(snackbarHostState) },
      bottomBar = {
        if (!useRail) {
          NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
            MainTab.entries.forEach { tab ->
              val selected = tab == activeTab
              NavigationBarItem(
                selected = selected,
                onClick = { activeTab = tab },
                icon = {
                  val showBadge = tab == MainTab.PROFILE && dailyClaimable
                  BadgedBox(
                    badge = { if (showBadge) Badge(containerColor = kinetic.magentaBoost) }
                  ) {
                    Icon(
                      imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                      contentDescription = tab.label,
                    )
                  }
                },
                label = { Text(tab.label) },
                modifier = Modifier.testTag(tab.tag),
              )
            }
          }
        }
      },
    ) { innerPadding ->
      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding),
      ) {
        if (useRail) {
          NavigationRail(
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxHeight(),
          ) {
            Spacer(modifier = Modifier.height(12.dp))
            MainTab.entries.forEach { tab ->
              val selected = tab == activeTab
              NavigationRailItem(
                selected = selected,
                onClick = { activeTab = tab },
                icon = {
                  val showBadge = tab == MainTab.PROFILE && dailyClaimable
                  BadgedBox(
                    badge = { if (showBadge) Badge(containerColor = kinetic.magentaBoost) }
                  ) {
                    Icon(
                      imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                      contentDescription = tab.label,
                    )
                  }
                },
                label = { Text(tab.label) },
                modifier = Modifier.testTag(tab.tag),
              )
            }
          }
        }

        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
          when (activeTab) {
            MainTab.CAMPAIGN -> CampaignScreen(
              profile = profile,
              progressMap = progressMap,
              syncState = syncState,
              dailyRewardClaimable = dailyClaimable,
              onOpenLevel = { lvl -> activeCampaignLevelNum = lvl },
              onClaimDailyRewardClick = {
                if (dailyClaimable) {
                  viewModel.claimDailyReward()
                } else {
                  activeTab = MainTab.PROFILE
                }
              },
              onSyncClick = { viewModel.syncNow(silent = false) },
              onOpenThemeStore = { showThemeSheet = true },
            )
            MainTab.STUDIO -> EditorHubScreen(
              myLevels = myCustomLevels,
              communityState = communityState,
              isGuestPreview = isGuestPreview,
              onSignInClick = onRequestSignIn,
              onCreateNewLevel = {
                editingEntity = null
                isEditingLevel = true
              },
              onEditMyLevel = { entity ->
                editingEntity = entity
                isEditingLevel = true
              },
              onDeleteMyLevel = { id -> viewModel.deleteCustomLevel(id) },
              onPlayCustomLevel = { puzzle, commId -> activeCustomPlay = puzzle to commId },
              onLikeCommunityLevel = { id -> viewModel.likeCommunityLevel(id) },
            )
            MainTab.RANKS -> LeaderboardScreen(
              currentUserId = currentUserId,
              profile = profile,
              leaderboardState = leaderboardState,
              isGuestPreview = isGuestPreview,
              onSignInClick = onRequestSignIn,
            )
            MainTab.PROFILE -> ProfileAndRewardsScreen(
              profile = profile,
              syncState = syncState,
              dailyRewardClaimable = dailyClaimable,
              isGuestPreview = isGuestPreview,
              themePref = themePreference,
              themePurchases = themePurchases,
              onSelectOrUnlockTheme = { themeId -> viewModel.selectOrUnlockTheme(themeId) },
              onChangeBillingRegion = { reg -> viewModel.setPreferredRegion(reg) },
              onOpenThemeCheckout = { themeDef -> checkoutTheme = themeDef },
              onSignInClick = onRequestSignIn,
              onClaimDailyReward = { viewModel.claimDailyReward() },
              onSelectOrUnlockSkin = { skin, cost -> viewModel.selectOrUnlockSkin(skin, cost) },
              onBuyHint = { viewModel.buyHintWithCoins() },
              onUpdateDisplayName = { name -> viewModel.updateDisplayName(name) },
              onSyncNow = { viewModel.syncNow(silent = false) },
              onSignedOut = {},
            )
          }
        }
      }
    }
  }

  val reward = dailyRewardBanner
  if (reward != null) {
    AlertDialog(
      onDismissRequest = { viewModel.dismissDailyRewardBanner() },
      icon = {
        Icon(Icons.Rounded.CardGiftcard, contentDescription = null, tint = kinetic.amberGold)
      },
      title = {
        Text("Day ${reward.newStreak} Reward Claimed!", color = kinetic.amberGold)
      },
      text = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text("+${reward.tier.coins} Kinetic Coins", style = MaterialTheme.typography.titleLarge)
          if (reward.tier.hints > 0) {
            Text("+${reward.tier.hints} Solution Hints", style = MaterialTheme.typography.bodyMedium)
          }
          if (reward.tier.bonusSkin != null) {
            Text("Unlocked ${reward.tier.bonusSkin.uppercase()} Orb Skin!", color = kinetic.cyanGlow)
          }
        }
      },
      confirmButton = {
        Button(onClick = { viewModel.dismissDailyRewardBanner() }) {
          Text("Awesome!")
        }
      },
    )
  }

  if (showThemeSheet) {
    ThemeStoreModalSheet(
      themePref = themePreference,
      purchases = themePurchases,
      playerCoins = profile?.coins ?: 0,
      onSelectOrUnlockCoinTheme = { themeId -> viewModel.selectOrUnlockTheme(themeId) },
      onChangeRegion = { reg -> viewModel.setPreferredRegion(reg) },
      onOpenCheckout = { themeDef ->
        showThemeSheet = false
        checkoutTheme = themeDef
      },
      onDismiss = { showThemeSheet = false },
    )
  }

  val pendingCheckout = checkoutTheme
  if (pendingCheckout != null) {
    ThemeCheckoutDialog(
      theme = pendingCheckout,
      initialRegion = preferredRegion,
      onChangeRegion = { reg -> viewModel.setPreferredRegion(reg) },
      onConfirmPurchase = { region, method, mobile, cnic ->
        viewModel.purchasePremiumTheme(
          themeId = pendingCheckout.id,
          region = region,
          paymentMethod = method,
          mobileNumber = mobile,
          cnicLast6 = cnic,
        )
      },
      onDismiss = { checkoutTheme = null },
    )
  }
  }
}
