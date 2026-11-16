package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.LocalLevelProgressEntity
import com.example.data.local.LocalPlayerProfileEntity
import com.example.physics.LevelCatalog
import com.example.physics.WorldMeta
import com.example.ui.SyncBannerState
import com.example.ui.theme.LocalKineticColors

@Composable
fun CampaignScreen(
  profile: LocalPlayerProfileEntity?,
  progressMap: Map<Int, LocalLevelProgressEntity>,
  syncState: SyncBannerState,
  dailyRewardClaimable: Boolean,
  onOpenLevel: (Int) -> Unit,
  onClaimDailyRewardClick: () -> Unit,
  onSyncClick: () -> Unit,
  onOpenThemeStore: () -> Unit = {},
) {
  val kinetic = LocalKineticColors.current
  var selectedWorldIdx by rememberSaveable { mutableIntStateOf(0) }
  val totalStars = progressMap.values.sumOf { it.stars }
  val levelsCompleted = progressMap.values.count { it.completed }
  val nextLevel = ((1..LevelCatalog.TOTAL_LEVELS).firstOrNull { progressMap[it]?.completed != true } ?: 1)

  val selectedWorld = LevelCatalog.worlds[selectedWorldIdx]
  val worldUnlocked = totalStars >= selectedWorld.requiredStarsToUnlock
  val worldLevels = selectedWorld.levelRange.toList()

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val isWide = maxWidth >= 600.dp

    if (isWide) {
      // Tablet / Foldable Two-Pane Canonical Layout
      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        LazyColumn(
          modifier = Modifier
            .weight(0.42f)
            .fillMaxHeight(),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          item {
            CampaignHeroCard(
              profile = profile,
              totalStars = totalStars,
              levelsCompleted = levelsCompleted,
              nextLevel = nextLevel,
              syncState = syncState,
              dailyRewardClaimable = dailyRewardClaimable,
              onContinueClick = { onOpenLevel(nextLevel) },
              onClaimDailyRewardClick = onClaimDailyRewardClick,
              onSyncClick = onSyncClick,
              onOpenThemeStore = onOpenThemeStore,
            )
          }
          items(LevelCatalog.worlds, key = { it.index }) { world ->
            WorldSelectorCard(
              world = world,
              selected = world.index == selectedWorldIdx,
              totalStars = totalStars,
              progressMap = progressMap,
              onClick = { selectedWorldIdx = world.index },
            )
          }
        }

        Column(
          modifier = Modifier
            .weight(0.58f)
            .fillMaxHeight(),
        ) {
          WorldHeaderBar(world = selectedWorld, unlocked = worldUnlocked, totalStars = totalStars)
          Spacer(modifier = Modifier.height(10.dp))
          LevelNumberGrid(
            levels = worldLevels,
            worldUnlocked = worldUnlocked,
            progressMap = progressMap,
            onOpenLevel = onOpenLevel,
            modifier = Modifier.fillMaxSize(),
          )
        }
      }
    } else {
      // Mobile Compact Layout
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        CampaignHeroCard(
          profile = profile,
          totalStars = totalStars,
          levelsCompleted = levelsCompleted,
          nextLevel = nextLevel,
          syncState = syncState,
          dailyRewardClaimable = dailyRewardClaimable,
          onContinueClick = { onOpenLevel(nextLevel) },
          onClaimDailyRewardClick = onClaimDailyRewardClick,
          onSyncClick = onSyncClick,
          onOpenThemeStore = onOpenThemeStore,
        )

        LazyRow(
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          contentPadding = PaddingValues(vertical = 2.dp),
        ) {
          items(LevelCatalog.worlds, key = { it.index }) { world ->
            val unlocked = totalStars >= world.requiredStarsToUnlock
            val selected = world.index == selectedWorldIdx
            Surface(
              shape = RoundedCornerShape(14.dp),
              color = if (selected) kinetic.cyanGlow.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
              modifier = Modifier
                .border(
                  width = if (selected) 1.5.dp else 1.dp,
                  color = if (selected) kinetic.cyanGlow else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                  shape = RoundedCornerShape(14.dp),
                )
                .clip(RoundedCornerShape(14.dp))
                .clickable { selectedWorldIdx = world.index }
                .testTag("world_tab_${world.index}"),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                if (!unlocked) {
                  Icon(Icons.Rounded.Lock, contentDescription = "Locked", modifier = Modifier.size(16.dp), tint = kinetic.mutedText)
                }
                Text(
                  text = "W${world.index + 1}: ${world.name}",
                  style = MaterialTheme.typography.labelLarge,
                  color = if (selected) kinetic.cyanGlow else MaterialTheme.colorScheme.onSurface,
                )
              }
            }
          }
        }

        WorldHeaderBar(world = selectedWorld, unlocked = worldUnlocked, totalStars = totalStars)

        LevelNumberGrid(
          levels = worldLevels,
          worldUnlocked = worldUnlocked,
          progressMap = progressMap,
          onOpenLevel = onOpenLevel,
          modifier = Modifier.weight(1f),
        )
      }
    }
  }
}

@Composable
private fun CampaignHeroCard(
  profile: LocalPlayerProfileEntity?,
  totalStars: Int,
  levelsCompleted: Int,
  nextLevel: Int,
  syncState: SyncBannerState,
  dailyRewardClaimable: Boolean,
  onContinueClick: () -> Unit,
  onClaimDailyRewardClick: () -> Unit,
  onSyncClick: () -> Unit,
  onOpenThemeStore: () -> Unit,
) {
  val kinetic = LocalKineticColors.current
  Card(
    shape = RoundedCornerShape(22.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Box(modifier = Modifier.fillMaxWidth()) {
      Image(
        painter = painterResource(id = R.drawable.img_hero_lab_banner),
        contentDescription = "Kinetic Lab Hero Banner",
        contentScale = ContentScale.Crop,
        modifier = Modifier
          .matchParentSize()
          .clip(RoundedCornerShape(22.dp)),
        alpha = 0.36f,
      )
      Box(
        modifier = Modifier
          .matchParentSize()
          .background(
            Brush.horizontalGradient(
              colors = listOf(
                MaterialTheme.colorScheme.background.copy(alpha = 0.90f),
                MaterialTheme.colorScheme.background.copy(alpha = 0.55f),
              )
            )
          )
      )

      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = profile?.displayName ?: "Kinetic Pilot",
              style = MaterialTheme.typography.titleLarge,
              color = MaterialTheme.colorScheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = "$levelsCompleted / ${LevelCatalog.TOTAL_LEVELS} Solved • $totalStars Stars",
              style = MaterialTheme.typography.labelMedium,
              color = kinetic.amberGold,
            )
          }

          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
              shape = CircleShape,
              color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
              modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(onClick = onOpenThemeStore)
                .testTag("open_themes_button"),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = Icons.Rounded.Palette,
                  contentDescription = "Game Themes",
                  tint = kinetic.cyanGlow,
                )
              }
            }
            SyncChip(syncState = syncState, onClick = onSyncClick)
            Surface(
              shape = CircleShape,
              color = if (dailyRewardClaimable) kinetic.amberGold.copy(alpha = 0.24f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
              modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(onClick = onClaimDailyRewardClick)
                .testTag("daily_reward_button"),
            ) {
              Box(contentAlignment = Alignment.Center) {
                BadgedBox(
                  badge = {
                    if (dailyRewardClaimable) {
                      Badge(containerColor = kinetic.magentaBoost)
                    }
                  }
                ) {
                  Icon(
                    imageVector = Icons.Rounded.CardGiftcard,
                    contentDescription = "Daily Reward",
                    tint = if (dailyRewardClaimable) kinetic.amberGold else MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          }
        }

        LinearProgressIndicator(
          progress = { (levelsCompleted.toFloat() / LevelCatalog.TOTAL_LEVELS).coerceIn(0f, 1f) },
          color = kinetic.cyanGlow,
          trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
          modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape),
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "${profile?.coins ?: 0} Coins • ${profile?.hintsRemaining ?: 0} Hints",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Button(
            onClick = onContinueClick,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = kinetic.cyanGlow,
              contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier.testTag("continue_level_button"),
          ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Play Lv $nextLevel", fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }
}

@Composable
private fun SyncChip(syncState: SyncBannerState, onClick: () -> Unit) {
  val kinetic = LocalKineticColors.current
  val (icon, tint, label) = when (syncState) {
    SyncBannerState.Synced -> Triple(Icons.Rounded.CloudDone, kinetic.emeraldSuccess, "Synced")
    SyncBannerState.Syncing -> Triple(Icons.Rounded.CloudSync, kinetic.cyanGlow, "Syncing")
    SyncBannerState.OfflineLocal -> Triple(Icons.Rounded.CloudOff, kinetic.amberGold, "Offline")
    is SyncBannerState.Warning -> Triple(Icons.Rounded.CloudSync, kinetic.amberGold, "Queued")
  }
  Surface(
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
    modifier = Modifier
      .clip(RoundedCornerShape(12.dp))
      .clickable(onClick = onClick)
      .testTag("cloud_sync_chip"),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
      Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(16.dp))
      Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
  }
}

@Composable
private fun WorldSelectorCard(
  world: WorldMeta,
  selected: Boolean,
  totalStars: Int,
  progressMap: Map<Int, LocalLevelProgressEntity>,
  onClick: () -> Unit,
) {
  val kinetic = LocalKineticColors.current
  val unlocked = totalStars >= world.requiredStarsToUnlock
  val solved = world.levelRange.count { progressMap[it]?.completed == true }
  val starsInWorld = world.levelRange.sumOf { progressMap[it]?.stars ?: 0 }
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = if (selected) kinetic.cyanGlow.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
    modifier = Modifier
      .fillMaxWidth()
      .border(
        width = if (selected) 1.5.dp else 1.dp,
        color = if (selected) kinetic.cyanGlow else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
        shape = RoundedCornerShape(16.dp),
      )
      .clip(RoundedCornerShape(16.dp))
      .clickable(onClick = onClick),
  ) {
    Row(
      modifier = Modifier.padding(14.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "World ${world.index + 1}: ${world.name}",
          style = MaterialTheme.typography.titleMedium,
          color = if (selected) kinetic.cyanGlow else MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = world.subtitle,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      if (!unlocked) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          Icon(Icons.Rounded.Lock, contentDescription = "Locked", tint = kinetic.amberGold, modifier = Modifier.size(16.dp))
          Text("${world.requiredStarsToUnlock}★", style = MaterialTheme.typography.labelMedium, color = kinetic.amberGold)
        }
      } else {
        Column(horizontalAlignment = Alignment.End) {
          Text("$solved/40", style = MaterialTheme.typography.labelMedium, color = kinetic.emeraldSuccess)
          Text("$starsInWorld/120 ★", style = MaterialTheme.typography.labelSmall, color = kinetic.amberGold)
        }
      }
    }
  }
}

@Composable
private fun WorldHeaderBar(world: WorldMeta, unlocked: Boolean, totalStars: Int) {
  val kinetic = LocalKineticColors.current
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = "World ${world.index + 1}: ${world.name}",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = if (unlocked) world.subtitle else "Collect ${world.requiredStarsToUnlock} total stars to unlock ($totalStars/${world.requiredStarsToUnlock})",
        style = MaterialTheme.typography.bodyMedium,
        color = if (unlocked) MaterialTheme.colorScheme.onSurfaceVariant else kinetic.amberGold,
      )
    }
  }
}

@Composable
private fun LevelNumberGrid(
  levels: List<Int>,
  worldUnlocked: Boolean,
  progressMap: Map<Int, LocalLevelProgressEntity>,
  onOpenLevel: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  val kinetic = LocalKineticColors.current
  LazyVerticalGrid(
    columns = GridCells.Adaptive(minSize = 74.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    modifier = modifier,
    contentPadding = PaddingValues(bottom = 16.dp),
  ) {
    items(levels, key = { it }) { levelNum ->
      val prog = progressMap[levelNum]
      val isSolved = prog?.completed == true
      val prevSolved = levelNum == 1 || progressMap[levelNum - 1]?.completed == true
      val isUnlocked = worldUnlocked && (isSolved || prevSolved || (levelNum - 1) % LevelCatalog.LEVELS_PER_WORLD == 0)
      val stars = prog?.stars ?: 0

      Surface(
        shape = RoundedCornerShape(16.dp),
        color = when {
          isSolved -> kinetic.emeraldSuccess.copy(alpha = 0.14f)
          isUnlocked -> MaterialTheme.colorScheme.surfaceVariant
          else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
        },
        modifier = Modifier
          .aspectRatio(1f)
          .border(
            width = if (isSolved) 1.5.dp else 1.dp,
            color = when {
              isSolved -> kinetic.emeraldSuccess.copy(alpha = 0.65f)
              isUnlocked -> kinetic.cyanGlow.copy(alpha = 0.45f)
              else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
            },
            shape = RoundedCornerShape(16.dp),
          )
          .clip(RoundedCornerShape(16.dp))
          .clickable(enabled = isUnlocked) { onOpenLevel(levelNum) }
          .testTag("level_tile_$levelNum"),
      ) {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(6.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
          ) {
            when {
              isSolved -> Icon(Icons.Rounded.CheckCircle, contentDescription = "Solved", tint = kinetic.emeraldSuccess, modifier = Modifier.size(14.dp))
              !isUnlocked -> Icon(Icons.Rounded.Lock, contentDescription = "Locked", tint = kinetic.mutedText, modifier = Modifier.size(14.dp))
              else -> Spacer(modifier = Modifier.size(14.dp))
            }
          }
          Text(
            text = levelNum.toString(),
            style = MaterialTheme.typography.titleLarge,
            color = if (isUnlocked) MaterialTheme.colorScheme.onSurface else kinetic.mutedText,
            fontWeight = FontWeight.Bold,
          )
          Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (s in 1..3) {
              Icon(
                imageVector = if (s <= stars) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                contentDescription = null,
                tint = if (s <= stars) kinetic.amberGold else Color.Gray.copy(alpha = 0.4f),
                modifier = Modifier.size(13.dp),
              )
            }
          }
        }
      }
    }
  }
}
