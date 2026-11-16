package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.LevelSerializer
import com.example.data.local.LocalCustomLevelEntity
import com.example.data.remote.CloudCustomLevel
import com.example.physics.PuzzleLevel
import com.example.ui.UiState
import com.example.ui.theme.LocalKineticColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorHubScreen(
  myLevels: List<LocalCustomLevelEntity>,
  communityState: UiState<List<CloudCustomLevel>>,
  isGuestPreview: Boolean = false,
  onSignInClick: () -> Unit = {},
  onCreateNewLevel: () -> Unit,
  onEditMyLevel: (LocalCustomLevelEntity) -> Unit,
  onDeleteMyLevel: (String) -> Unit,
  onPlayCustomLevel: (PuzzleLevel, String?) -> Unit,
  onLikeCommunityLevel: (String) -> Unit,
) {
  val kinetic = LocalKineticColors.current
  var selectedTab by rememberSaveable { mutableIntStateOf(0) }

  Scaffold(
    floatingActionButton = {
      FloatingActionButton(
        onClick = onCreateNewLevel,
        containerColor = kinetic.cyanGlow,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.testTag("create_custom_level_fab"),
      ) {
        Icon(Icons.Rounded.Add, contentDescription = "New Level")
      }
    },
    containerColor = MaterialTheme.colorScheme.background,
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .padding(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Column {
        Text(
          text = "CONTRAPTION STUDIO",
          style = MaterialTheme.typography.headlineMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = "Build offline, test-verify physics, and publish to the global community.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      SecondaryTabRow(
        selectedTabIndex = selectedTab,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
      ) {
        Tab(
          selected = selectedTab == 0,
          onClick = { selectedTab = 0 },
          text = { Text("My Lab (${myLevels.size})") },
          modifier = Modifier.testTag("editor_tab_my_lab"),
        )
        Tab(
          selected = selectedTab == 1,
          onClick = { selectedTab = 1 },
          text = { Text("Community") },
          modifier = Modifier.testTag("editor_tab_community"),
        )
      }

      if (selectedTab == 0) {
        if (myLevels.isEmpty()) {
          EmptyStudioCard(
            title = "No Custom Chambers Yet",
            subtitle = "Tap + to design your own physics puzzle with portals, gravity wells, and hazards. Works offline!",
            actionLabel = "Create First Level",
            onAction = onCreateNewLevel,
          )
        } else {
          LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 290.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 84.dp),
            modifier = Modifier.fillMaxSize(),
          ) {
            items(myLevels, key = { it.id }) { item ->
              MyCustomLevelCard(
                item = item,
                onPlay = { onPlayCustomLevel(LevelSerializer.fromEntity(item), null) },
                onEdit = { onEditMyLevel(item) },
                onDelete = { onDeleteMyLevel(item.id) },
              )
            }
          }
        }
      } else {
        when (communityState) {
          UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = kinetic.cyanGlow)
          }
          is UiState.Error -> EmptyStudioCard(
            title = if (isGuestPreview) "Community Hub (Preview Mode)" else "Community Offline",
            subtitle = communityState.message,
            actionLabel = if (isGuestPreview) "Sign in with Google" else "Create Locally Instead",
            onAction = if (isGuestPreview) onSignInClick else onCreateNewLevel,
            secondaryActionLabel = if (isGuestPreview) "Create Locally Instead" else null,
            onSecondaryAction = if (isGuestPreview) onCreateNewLevel else null,
          )
          is UiState.Success -> {
            if (communityState.data.isEmpty()) {
              EmptyStudioCard(
                title = "Be the First Creator",
                subtitle = "Publish a verified chamber from My Lab so pilots worldwide can play it!",
                actionLabel = "Build & Publish",
                onAction = onCreateNewLevel,
              )
            } else {
              LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 290.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 84.dp),
                modifier = Modifier.fillMaxSize(),
              ) {
                items(communityState.data, key = { it.id }) { cloud ->
                  CommunityLevelCard(
                    item = cloud,
                    onPlay = {
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
                      onPlayCustomLevel(puzzle, cloud.id)
                    },
                    onLike = { onLikeCommunityLevel(cloud.id) },
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun MyCustomLevelCard(
  item: LocalCustomLevelEntity,
  onPlay: () -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
) {
  val kinetic = LocalKineticColors.current
  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = item.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = item.gravityMode.replace("_", " ") + if (item.published) " • Published" else " • Draft",
            style = MaterialTheme.typography.labelSmall,
            color = if (item.published) kinetic.emeraldSuccess else kinetic.amberGold,
          )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          if (item.verified) {
            Icon(Icons.Rounded.Verified, contentDescription = "Verified", tint = kinetic.cyanGlow, modifier = Modifier.size(18.dp))
          }
          Icon(
            imageVector = if (item.syncedToCloud) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff,
            contentDescription = if (item.syncedToCloud) "Synced" else "Local Only",
            tint = if (item.syncedToCloud) kinetic.emeraldSuccess else kinetic.amberGold,
            modifier = Modifier.size(18.dp),
          )
        }
      }

      if (item.description.isNotBlank()) {
        Text(
          text = item.description,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Button(
          onClick = onPlay,
          colors = ButtonDefaults.buttonColors(containerColor = kinetic.cyanGlow),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.weight(1f),
        ) {
          Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Play", fontWeight = FontWeight.Bold)
        }
        OutlinedButton(onClick = onEdit, shape = RoundedCornerShape(12.dp)) {
          Icon(Icons.Rounded.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
          Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = kinetic.crimsonHazard)
        }
      }
    }
  }
}

@Composable
private fun CommunityLevelCard(
  item: CloudCustomLevel,
  onPlay: () -> Unit,
  onLike: () -> Unit,
) {
  val kinetic = LocalKineticColors.current
  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = item.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = "by ${item.authorName} • ${item.gravityMode.replace("_", " ")}",
            style = MaterialTheme.typography.labelSmall,
            color = kinetic.cyanGlow,
          )
        }
        if (item.verified) {
          Surface(shape = RoundedCornerShape(8.dp), color = kinetic.emeraldSuccess.copy(alpha = 0.16f)) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Icon(Icons.Rounded.Verified, contentDescription = null, tint = kinetic.emeraldSuccess, modifier = Modifier.size(14.dp))
              Text("Verified", style = MaterialTheme.typography.labelSmall, color = kinetic.emeraldSuccess)
            }
          }
        }
      }

      if (item.description.isNotBlank()) {
        Text(
          text = item.description,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Rounded.Public, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            Text("${item.playsCount}", style = MaterialTheme.typography.labelMedium)
          }
          OutlinedButton(onClick = onLike, shape = RoundedCornerShape(10.dp)) {
            Icon(Icons.Rounded.Favorite, contentDescription = "Like", tint = kinetic.magentaBoost, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("${item.likesCount}", style = MaterialTheme.typography.labelMedium)
          }
        }

        Button(
          onClick = onPlay,
          colors = ButtonDefaults.buttonColors(containerColor = kinetic.cyanGlow),
          shape = RoundedCornerShape(12.dp),
        ) {
          Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Play", fontWeight = FontWeight.Bold)
        }
      }
    }
  }
}

@Composable
private fun EmptyStudioCard(
  title: String,
  subtitle: String,
  actionLabel: String,
  onAction: () -> Unit,
  secondaryActionLabel: String? = null,
  onSecondaryAction: (() -> Unit)? = null,
) {
  val kinetic = LocalKineticColors.current
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Card(
      shape = RoundedCornerShape(22.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
      modifier = Modifier.fillMaxWidth(0.92f),
    ) {
      Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Icon(Icons.Rounded.Science, contentDescription = null, tint = kinetic.cyanGlow, modifier = Modifier.size(44.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Button(
          onClick = onAction,
          colors = ButtonDefaults.buttonColors(containerColor = kinetic.cyanGlow),
          shape = RoundedCornerShape(12.dp),
        ) {
          Text(actionLabel, fontWeight = FontWeight.Bold)
        }
        if (secondaryActionLabel != null && onSecondaryAction != null) {
          OutlinedButton(
            onClick = onSecondaryAction,
            shape = RoundedCornerShape(12.dp),
          ) {
            Text(secondaryActionLabel)
          }
        }
      }
    }
  }
}
