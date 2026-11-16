package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.auth.signOut
import com.example.billing.BillingRegion
import com.example.data.local.LocalPlayerProfileEntity
import com.example.data.local.LocalThemePreferenceEntity
import com.example.data.local.LocalThemePurchaseEntity
import com.example.data.repository.KineticRepository
import com.example.social.SocialShareHelper
import com.example.ui.SyncBannerState
import com.example.ui.components.ThemeGalleryCard
import com.example.ui.theme.AppThemeDefinition
import com.example.ui.theme.LocalKineticColors

private data class OrbSkinOption(
  val id: String,
  val name: String,
  val cost: Int,
  val color: Color,
)

@Composable
fun ProfileAndRewardsScreen(
  profile: LocalPlayerProfileEntity?,
  syncState: SyncBannerState,
  dailyRewardClaimable: Boolean,
  isGuestPreview: Boolean = false,
  themePref: LocalThemePreferenceEntity? = null,
  themePurchases: List<LocalThemePurchaseEntity> = emptyList(),
  onSelectOrUnlockTheme: (String) -> Unit = {},
  onChangeBillingRegion: (BillingRegion) -> Unit = {},
  onOpenThemeCheckout: (AppThemeDefinition) -> Unit = {},
  onSignInClick: () -> Unit = {},
  onClaimDailyReward: () -> Unit,
  onSelectOrUnlockSkin: (String, Int) -> Unit,
  onBuyHint: () -> Unit,
  onUpdateDisplayName: (String) -> Unit,
  onSyncNow: () -> Unit,
  onSignedOut: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }
  val kinetic = LocalKineticColors.current

  var handleInput by remember(profile?.displayName) { mutableStateOf(profile?.displayName ?: "") }
  val unlockedSkins = remember(profile?.unlockedSkinsCsv) {
    (profile?.unlockedSkinsCsv ?: "cyan").split(",").map { it.trim() }.toSet()
  }

  val skins = remember(kinetic) {
    listOf(
      OrbSkinOption("cyan", "Cyan Core", 0, kinetic.cyanGlow),
      OrbSkinOption("amber", "Solar Amber", 150, kinetic.amberGold),
      OrbSkinOption("emerald", "Emerald Pulse", 250, kinetic.emeraldSuccess),
      OrbSkinOption("magenta", "Plasma Rose", 400, kinetic.magentaBoost),
      OrbSkinOption("prism", "Quantum Prism", 750, Color.White),
    )
  }

  val streak = profile?.streakDays ?: 0
  val activeDayIndex = streak % 7

  Box(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(
      modifier = Modifier
        .widthIn(max = 760.dp)
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      // 1. Pilot Stats & Cloud Sync Card
      Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column {
              Text("PILOT PROFILE & CLOUD SAVE", style = MaterialTheme.typography.titleMedium, color = kinetic.cyanGlow)
              Text(
                text = if (isGuestPreview) {
                  "Preview Mode — saved locally on this device"
                } else {
                  when (syncState) {
                    SyncBannerState.Synced -> "Cross-device cloud save up to date"
                    SyncBannerState.Syncing -> "Syncing progress across devices..."
                    SyncBannerState.OfflineLocal -> "Offline mode — saved locally & queued"
                    is SyncBannerState.Warning -> syncState.message
                  }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            OutlinedButton(
              onClick = onSyncNow,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.testTag("manual_sync_button"),
            ) {
              Icon(Icons.Rounded.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Sync")
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            OutlinedTextField(
              value = handleInput,
              onValueChange = { handleInput = it.take(30) },
              label = { Text("Leaderboard Handle") },
              singleLine = true,
              modifier = Modifier.weight(1f),
            )
            Button(
              onClick = { onUpdateDisplayName(handleInput) },
              shape = RoundedCornerShape(12.dp),
              colors = ButtonDefaults.buttonColors(containerColor = kinetic.cyanGlow),
              modifier = Modifier.height(54.dp),
            ) {
              Text("Save", fontWeight = FontWeight.Bold)
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            StatBadge("Score", "${profile?.totalScore ?: 0}", kinetic.amberGold)
            StatBadge("Stars", "${profile?.totalStars ?: 0}", kinetic.cyanGlow)
            StatBadge("Coins", "${profile?.coins ?: 0}", kinetic.emeraldSuccess)
            StatBadge("Streak", "${streak}d", kinetic.magentaBoost)
          }

          OutlinedButton(
            onClick = {
              if (profile != null) {
                SocialShareHelper.shareHighScore(
                  context = context,
                  playerName = profile.displayName,
                  headline = "Kinetic Lab Career Stats",
                  score = profile.totalScore,
                  stars = profile.totalStars,
                  levelsCompleted = profile.levelsCompleted,
                  streakDays = profile.streakDays,
                )
              }
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().testTag("share_profile_card_button"),
          ) {
            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Share Career Score Card")
          }
        }
      }

      // 2. Overall Game Themes & Real Purchase Store
      ThemeGalleryCard(
        themePref = themePref,
        purchases = themePurchases,
        playerCoins = profile?.coins ?: 0,
        onSelectOrUnlockCoinTheme = onSelectOrUnlockTheme,
        onChangeRegion = onChangeBillingRegion,
        onOpenCheckout = onOpenThemeCheckout,
      )

      // 3. Daily Rewards 7-Day Streak Track
      Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Icon(Icons.Rounded.CardGiftcard, contentDescription = null, tint = kinetic.amberGold)
              Text("7-DAY DAILY REWARDS", style = MaterialTheme.typography.titleMedium)
            }
            Button(
              onClick = onClaimDailyReward,
              enabled = dailyRewardClaimable,
              colors = ButtonDefaults.buttonColors(containerColor = kinetic.amberGold),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.testTag("claim_daily_reward_button"),
            ) {
              Text(if (dailyRewardClaimable) "Claim Today" else "Claimed", fontWeight = FontWeight.Bold)
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            KineticRepository.dailyRewardTiers.forEachIndexed { idx, tier ->
              val claimed = idx < activeDayIndex || (!dailyRewardClaimable && idx == (streak - 1).coerceAtLeast(0) % 7 && streak > 0)
              val isNext = dailyRewardClaimable && idx == activeDayIndex
              Surface(
                shape = RoundedCornerShape(14.dp),
                color = when {
                  isNext -> kinetic.amberGold.copy(alpha = 0.22f)
                  claimed -> kinetic.emeraldSuccess.copy(alpha = 0.14f)
                  else -> MaterialTheme.colorScheme.surface
                },
                modifier = Modifier
                  .width(92.dp)
                  .border(
                    width = if (isNext) 1.5.dp else 1.dp,
                    color = if (isNext) kinetic.amberGold else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(14.dp),
                  ),
              ) {
                Column(
                  modifier = Modifier.padding(10.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  Text("Day ${tier.day}", style = MaterialTheme.typography.labelMedium, color = kinetic.cyanGlow)
                  Text("+${tier.coins}", style = MaterialTheme.typography.titleMedium, color = kinetic.amberGold, fontWeight = FontWeight.Bold)
                  Text(
                    text = if (tier.bonusSkin != null) "+Skin" else if (tier.hints > 0) "+${tier.hints} Hint" else "Coins",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  if (claimed) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = "Claimed", tint = kinetic.emeraldSuccess, modifier = Modifier.size(16.dp))
                  }
                }
              }
            }
          }
        }
      }

      // 3. Orb Skin Armory & Hint Synthesizer
      Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("QUANTUM ORB SKINS", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = onBuyHint, shape = RoundedCornerShape(12.dp)) {
              Icon(Icons.Rounded.Lightbulb, contentDescription = null, tint = kinetic.amberGold, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("+1 Hint (50c)", style = MaterialTheme.typography.labelMedium)
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            skins.forEach { skin ->
              val isUnlocked = skin.id in unlockedSkins
              val isSelected = profile?.selectedSkin == skin.id
              Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isSelected) skin.color.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface,
                modifier = Modifier
                  .width(114.dp)
                  .border(
                    width = if (isSelected) 1.5.dp else 1.dp,
                    color = if (isSelected) skin.color else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(14.dp),
                  )
                  .clip(RoundedCornerShape(14.dp))
                  .clickable { onSelectOrUnlockSkin(skin.id, skin.cost) },
              ) {
                Column(
                  modifier = Modifier.padding(12.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Box(
                    modifier = Modifier
                      .size(28.dp)
                      .clip(CircleShape)
                      .background(skin.color),
                  )
                  Text(skin.name, style = MaterialTheme.typography.labelMedium)
                  Text(
                    text = when {
                      isSelected -> "Equipped"
                      isUnlocked -> "Equip"
                      else -> "${skin.cost} Coins"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) kinetic.emeraldSuccess else kinetic.amberGold,
                  )
                }
              }
            }
          }
        }
      }

      // 4. Sign In (Preview Mode) or Sign Out (Cloud Account)
      if (isGuestPreview) {
        Button(
          onClick = onSignInClick,
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = kinetic.cyanGlow,
            contentColor = MaterialTheme.colorScheme.onPrimary,
          ),
          modifier = Modifier.fillMaxWidth().height(52.dp).testTag("preview_sign_in_button"),
        ) {
          Icon(Icons.AutoMirrored.Rounded.Login, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Sign in with Google (Enable Cloud Save)", fontWeight = FontWeight.Bold)
        }
      } else {
        OutlinedButton(
          onClick = {
            signOut(credentialManager = credentialManager, scope = scope, onSignOutComplete = onSignedOut)
          },
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth().height(50.dp).testTag("sign_out_button"),
        ) {
          Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, tint = kinetic.crimsonHazard)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Sign Out", color = kinetic.crimsonHazard)
        }
      }
    }
  }
}

@Composable
private fun StatBadge(label: String, value: String, color: Color) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(value, style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.Bold)
    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}
