package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.LocalPlayerProfileEntity
import com.example.data.remote.CloudLeaderboardEntry
import com.example.social.SocialShareHelper
import com.example.ui.UiState
import com.example.ui.theme.LocalKineticColors

@Composable
fun LeaderboardScreen(
  currentUserId: String,
  profile: LocalPlayerProfileEntity?,
  leaderboardState: UiState<List<CloudLeaderboardEntry>>,
  isGuestPreview: Boolean = false,
  onSignInClick: () -> Unit = {},
) {
  val kinetic = LocalKineticColors.current
  val context = LocalContext.current

  BoxWithConstraints(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp, vertical = 12.dp),
  ) {
    val isWide = maxWidth >= 600.dp

    when (leaderboardState) {
      UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = kinetic.cyanGlow)
      }
      is UiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
          modifier = Modifier.fillMaxWidth(0.92f),
        ) {
          Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Icon(Icons.Rounded.WifiOff, contentDescription = null, tint = kinetic.amberGold, modifier = Modifier.size(40.dp))
            Text(
              text = if (isGuestPreview) "Global Ranks (Preview Mode)" else "Global Ranks Offline",
              style = MaterialTheme.typography.titleLarge,
            )
            Text(
              text = leaderboardState.message,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (profile != null) {
              Text(
                text = "Your Local Score: ${profile.totalScore} pts • ${profile.totalStars} ★",
                style = MaterialTheme.typography.labelLarge,
                color = kinetic.cyanGlow,
              )
              OutlinedButton(
                onClick = {
                  SocialShareHelper.shareHighScore(
                    context = context,
                    playerName = profile.displayName,
                    headline = "Kinetic Lab Score",
                    score = profile.totalScore,
                    stars = profile.totalStars,
                    levelsCompleted = profile.levelsCompleted,
                    streakDays = profile.streakDays,
                  )
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("share_preview_score_button"),
              ) {
                Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Share Career Score Card")
              }
            }
            if (isGuestPreview) {
              Button(
                onClick = onSignInClick,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                  containerColor = kinetic.cyanGlow,
                  contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier.testTag("ranks_sign_in_button"),
              ) {
                Icon(Icons.AutoMirrored.Rounded.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Sign in with Google", fontWeight = FontWeight.Bold)
              }
            }
          }
        }
      }
      is UiState.Success -> {
        val entries = leaderboardState.data
        val myRankIdx = entries.indexOfFirst { it.userId == currentUserId }

        if (isWide) {
          Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
          ) {
            Column(
              modifier = Modifier.weight(0.42f).fillMaxHeight(),
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              LeaderboardHeader(
                profile = profile,
                myRank = if (myRankIdx >= 0) myRankIdx + 1 else null,
                onShare = {
                  if (profile != null) {
                    val rankText = if (myRankIdx >= 0) "Global Rank #${myRankIdx + 1}" else "High Score"
                    SocialShareHelper.shareHighScore(
                      context = context,
                      playerName = profile.displayName,
                      headline = rankText,
                      score = profile.totalScore,
                      stars = profile.totalStars,
                      levelsCompleted = profile.levelsCompleted,
                      streakDays = profile.streakDays,
                    )
                  }
                },
              )
              if (entries.isNotEmpty()) {
                TopThreePodium(entries.take(3))
              }
            }
            LeaderboardList(
              entries = entries,
              currentUserId = currentUserId,
              modifier = Modifier.weight(0.58f).fillMaxHeight(),
            )
          }
        } else {
          Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            LeaderboardHeader(
              profile = profile,
              myRank = if (myRankIdx >= 0) myRankIdx + 1 else null,
              onShare = {
                if (profile != null) {
                  val rankText = if (myRankIdx >= 0) "Global Rank #${myRankIdx + 1}" else "High Score"
                  SocialShareHelper.shareHighScore(
                    context = context,
                    playerName = profile.displayName,
                    headline = rankText,
                    score = profile.totalScore,
                    stars = profile.totalStars,
                    levelsCompleted = profile.levelsCompleted,
                    streakDays = profile.streakDays,
                  )
                }
              },
            )
            if (entries.isNotEmpty()) {
              TopThreePodium(entries.take(3))
            }
            LeaderboardList(
              entries = entries,
              currentUserId = currentUserId,
              modifier = Modifier.weight(1f),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun LeaderboardHeader(
  profile: LocalPlayerProfileEntity?,
  myRank: Int?,
  onShare: () -> Unit,
) {
  val kinetic = LocalKineticColors.current
  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.padding(16.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text("GLOBAL LEADERBOARD", style = MaterialTheme.typography.titleLarge, color = kinetic.cyanGlow)
        val rankLabel = if (myRank != null) "Your Rank: #$myRank" else "Solve a level to enter rankings"
        Text(
          text = "$rankLabel • ${profile?.totalScore ?: 0} pts",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Button(
        onClick = onShare,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = kinetic.amberGold),
        modifier = Modifier.testTag("share_leaderboard_button"),
      ) {
        Icon(Icons.Rounded.Share, contentDescription = "Share Rank", modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Share", fontWeight = FontWeight.Bold)
      }
    }
  }
}

@Composable
private fun TopThreePodium(top: List<CloudLeaderboardEntry>) {
  val kinetic = LocalKineticColors.current
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.Bottom,
  ) {
    top.forEachIndexed { idx, entry ->
      val badgeColor = when (idx) {
        0 -> kinetic.amberGold
        1 -> kinetic.cyanGlow
        else -> kinetic.magentaBoost
      }
      Surface(
        shape = RoundedCornerShape(16.dp),
        color = badgeColor.copy(alpha = 0.14f),
        modifier = Modifier
          .weight(1f)
          .border(1.dp, badgeColor.copy(alpha = 0.55f), RoundedCornerShape(16.dp)),
      ) {
        Column(
          modifier = Modifier.padding(10.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = badgeColor, modifier = Modifier.size(22.dp))
          Text("#${idx + 1}", style = MaterialTheme.typography.labelLarge, color = badgeColor, fontWeight = FontWeight.Bold)
          Text(
            text = entry.displayName,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = "${entry.totalScore}",
            style = MaterialTheme.typography.labelMedium,
            color = kinetic.amberGold,
            fontWeight = FontWeight.Bold,
          )
        }
      }
    }
  }
}

@Composable
private fun LeaderboardList(
  entries: List<CloudLeaderboardEntry>,
  currentUserId: String,
  modifier: Modifier = Modifier,
) {
  val kinetic = LocalKineticColors.current
  if (entries.isEmpty()) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
      Text("No ranked pilots yet — be the first!", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    return
  }

  LazyColumn(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(8.dp),
    contentPadding = PaddingValues(bottom = 16.dp),
  ) {
    itemsIndexed(entries, key = { _, item -> item.userId }) { idx, entry ->
      val isMe = entry.userId == currentUserId
      val orbColor = when (entry.avatarBadge) {
        "amber" -> kinetic.amberGold
        "emerald" -> kinetic.emeraldSuccess
        "magenta" -> kinetic.magentaBoost
        "prism" -> Color.White
        else -> kinetic.cyanGlow
      }
      Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isMe) kinetic.cyanGlow.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
          .fillMaxWidth()
          .border(
            width = if (isMe) 1.5.dp else 1.dp,
            color = if (isMe) kinetic.cyanGlow else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
            shape = RoundedCornerShape(14.dp),
          ),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text(
            text = "#${idx + 1}",
            style = MaterialTheme.typography.labelLarge,
            color = if (idx < 3) kinetic.amberGold else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(34.dp),
          )
          Box(
            modifier = Modifier
              .size(16.dp)
              .clip(CircleShape)
              .background(orbColor),
          )
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = if (isMe) "${entry.displayName} (You)" else entry.displayName,
              style = MaterialTheme.typography.titleMedium,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = "${entry.levelsCompleted} levels solved",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Rounded.Star, contentDescription = null, tint = kinetic.amberGold, modifier = Modifier.size(16.dp))
            Text("${entry.totalStars}", style = MaterialTheme.typography.labelMedium)
          }
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "${entry.totalScore}",
            style = MaterialTheme.typography.labelLarge,
            color = kinetic.cyanGlow,
            fontWeight = FontWeight.Bold,
          )
        }
      }
    }
  }
}
