package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RotateLeft
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.physics.LevelCatalog
import com.example.physics.PhysicsEngine
import com.example.physics.PhysicsState
import com.example.physics.PlacedTool
import com.example.physics.PuzzleLevel
import com.example.physics.SimulationOutcome
import com.example.physics.ToolType
import com.example.physics.Vec2
import com.example.social.SocialShareHelper
import com.example.ui.components.PhysicsArenaCanvas
import com.example.ui.theme.LocalKineticColors
import java.util.UUID
import kotlinx.coroutines.launch

@Composable
fun PlayLevelScreen(
  level: PuzzleLevel,
  orbSkin: String,
  hintsRemaining: Int,
  playerName: String,
  totalStars: Int,
  levelsCompleted: Int,
  streakDays: Int,
  onUseHint: suspend () -> Boolean,
  onLevelSolved: (score: Int, stars: Int, toolsUsed: Int) -> Unit,
  onNextLevel: (() -> Unit)?,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)

  val context = LocalContext.current
  val haptics = LocalHapticFeedback.current
  val scope = rememberCoroutineScope()
  val kinetic = LocalKineticColors.current

  var placedTools by remember(level.id) { mutableStateOf<List<PlacedTool>>(emptyList()) }
  var selectedToolId by remember(level.id) { mutableStateOf<String?>(null) }
  var activePlacementType by remember(level.id) { mutableStateOf(level.toolBudget.keys.firstOrNull()) }
  var physicsState by remember(level.id) { mutableStateOf(PhysicsEngine.initialState(level)) }
  var speedMultiplier by remember { mutableFloatStateOf(1f) }
  var revealedHintTools by remember(level.id) { mutableStateOf<List<PlacedTool>>(emptyList()) }
  var winBreakdown by remember(level.id) { mutableStateOf<PhysicsEngine.LevelScoreBreakdown?>(null) }
  var failNotice by remember(level.id) { mutableStateOf<String?>(null) }
  var lastBounces by remember(level.id) { mutableIntStateOf(0) }
  var lastStarCount by remember(level.id) { mutableIntStateOf(0) }

  val previewPath = remember(level, placedTools, physicsState.outcome) {
    if (physicsState.outcome == SimulationOutcome.IDLE) {
      PhysicsEngine.computePreviewPath(level, placedTools)
    } else {
      emptyList()
    }
  }

  // 60Hz frame-accurate physics simulation loop
  LaunchedEffect(level.id, physicsState.outcome, speedMultiplier) {
    if (physicsState.outcome != SimulationOutcome.RUNNING) return@LaunchedEffect
    var lastNanos = 0L
    while (physicsState.outcome == SimulationOutcome.RUNNING) {
      withFrameNanos { nanos ->
        if (lastNanos == 0L) {
          lastNanos = nanos
          return@withFrameNanos
        }
        val rawDt = ((nanos - lastNanos) / 1_000_000_000f).coerceIn(0.001f, 0.033f)
        lastNanos = nanos
        val next = PhysicsEngine.step(
          state = physicsState,
          level = level,
          placedTools = placedTools,
          dt = rawDt * speedMultiplier,
          emitParticles = true,
        )
        if (next.bounces > lastBounces) {
          lastBounces = next.bounces
          haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        if (next.collectedStarIndices.size > lastStarCount) {
          lastStarCount = next.collectedStarIndices.size
          haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        physicsState = next
        when (next.outcome) {
          SimulationOutcome.WON -> {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            val breakdown = PhysicsEngine.calculateScore(
              level = level,
              starsCollected = next.collectedStarIndices.size,
              toolsUsed = placedTools.size,
              simTime = next.simTime,
              bounces = next.bounces,
            )
            winBreakdown = breakdown
            onLevelSolved(breakdown.totalScore, breakdown.starsEarned, placedTools.size)
          }
          SimulationOutcome.HAZARD_HIT -> {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            failNotice = "Orb hit a crimson laser hazard! Adjust your trajectory and reset."
          }
          SimulationOutcome.TIMED_OUT -> {
            failNotice = "Simulation timed out. Try repositioning or rotating your tools."
          }
          else -> Unit
        }
      }
    }
  }

  fun resetSimulation() {
    physicsState = PhysicsEngine.initialState(level)
    failNotice = null
    winBreakdown = null
    lastBounces = 0
    lastStarCount = 0
  }

  fun launchSimulation() {
    failNotice = null
    winBreakdown = null
    lastBounces = 0
    lastStarCount = 0
    physicsState = PhysicsEngine.initialState(level).copy(outcome = SimulationOutcome.RUNNING)
  }

  fun placeToolAt(type: ToolType, pos: Vec2) {
    val usedCount = placedTools.count { it.type == type }
    val maxAllowed = level.toolBudget[type] ?: 0
    if (usedCount >= maxAllowed) return
    val newTool = PlacedTool(
      id = UUID.randomUUID().toString(),
      type = type,
      x = pos.x.coerceIn(0.08f, 0.92f),
      y = pos.y.coerceIn(0.08f, 1.12f),
      angleDeg = if (type == ToolType.DEFLECTOR) 135f else 0f,
    )
    placedTools = placedTools + newTool
    selectedToolId = newTool.id
  }

  val selectedTool = placedTools.firstOrNull { it.id == selectedToolId }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .windowInsetsPadding(WindowInsets.safeDrawing)
      .padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    // Top HUD
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
        IconButton(onClick = onBack, modifier = Modifier.testTag("play_back_button")) {
          Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
        }
        Column {
          Text(
            text = if (level.number > 0) "Lv ${level.number}: ${level.title}" else level.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = "${level.gravityMode.label} • ${level.worldName}",
            style = MaterialTheme.typography.labelSmall,
            color = kinetic.cyanGlow,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (level.hintTools.isNotEmpty()) {
          OutlinedButton(
            onClick = {
              scope.launch {
                if (revealedHintTools.size < level.hintTools.size && onUseHint()) {
                  revealedHintTools = revealedHintTools + level.hintTools[revealedHintTools.size]
                }
              }
            },
            shape = RoundedCornerShape(12.dp),
            enabled = revealedHintTools.size < level.hintTools.size && hintsRemaining > 0,
            modifier = Modifier.testTag("hint_button"),
          ) {
            Icon(Icons.Rounded.Lightbulb, contentDescription = "Use Hint", tint = kinetic.amberGold, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Hint ($hintsRemaining)", style = MaterialTheme.typography.labelMedium)
          }
        }
      }
    }

    if (failNotice != null) {
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = failNotice!!,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.weight(1f),
          )
          TextButton(onClick = ::resetSimulation) { Text("Reset") }
        }
      }
    }

    BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
      val isWide = maxWidth >= 600.dp
      if (isWide) {
        Row(
          modifier = Modifier.fillMaxSize(),
          horizontalArrangement = Arrangement.spacedBy(16.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(
            modifier = Modifier
              .weight(1f)
              .fillMaxHeight(),
            contentAlignment = Alignment.Center,
          ) {
            PhysicsArenaCanvas(
              level = level,
              physicsState = physicsState,
              placedTools = placedTools,
              selectedToolId = selectedToolId,
              previewPath = previewPath,
              hintTools = revealedHintTools,
              orbSkin = orbSkin,
              interactive = physicsState.outcome == SimulationOutcome.IDLE,
              onTapArena = { pos ->
                val hit = placedTools.minByOrNull { Vec2(it.x, it.y).distanceTo(pos) }
                if (hit != null && Vec2(hit.x, hit.y).distanceTo(pos) < 0.09f) {
                  selectedToolId = hit.id
                } else {
                  val typeToPlace = activePlacementType ?: level.toolBudget.keys.firstOrNull()
                  if (typeToPlace != null) placeToolAt(typeToPlace, pos)
                }
              },
              onDragTool = { id, newPos ->
                selectedToolId = id
                placedTools = placedTools.map { t ->
                  if (t.id == id) t.copy(x = newPos.x, y = newPos.y) else t
                }
              },
              modifier = Modifier.fillMaxHeight(),
            )
          }

          Column(
            modifier = Modifier
              .width(340.dp)
              .fillMaxHeight()
              .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            PlayControlDeck(
              level = level,
              placedTools = placedTools,
              selectedTool = selectedTool,
              activePlacementType = activePlacementType,
              isRunning = physicsState.outcome == SimulationOutcome.RUNNING,
              isIdle = physicsState.outcome == SimulationOutcome.IDLE,
              speedMultiplier = speedMultiplier,
              starsCollected = physicsState.collectedStarIndices.size,
              onSelectBudgetType = { type ->
                activePlacementType = type
                val used = placedTools.count { it.type == type }
                val cap = level.toolBudget[type] ?: 0
                if (used < cap && physicsState.outcome == SimulationOutcome.IDLE) {
                  placeToolAt(type, Vec2(0.50f, 0.55f))
                }
              },
              onRotateTool = { delta ->
                val id = selectedTool?.id ?: return@PlayControlDeck
                placedTools = placedTools.map { if (it.id == id) it.copy(angleDeg = (it.angleDeg + delta + 360f) % 360f) else it }
              },
              onSetAngle = { angle ->
                val id = selectedTool?.id ?: return@PlayControlDeck
                placedTools = placedTools.map { if (it.id == id) it.copy(angleDeg = angle) else it }
              },
              onDeleteSelected = {
                val id = selectedTool?.id ?: return@PlayControlDeck
                placedTools = placedTools.filterNot { it.id == id }
                selectedToolId = null
              },
              onClearAll = {
                placedTools = emptyList()
                selectedToolId = null
                resetSimulation()
              },
              onToggleSpeed = { speedMultiplier = if (speedMultiplier > 1.5f) 1f else 2f },
              onLaunchOrReset = {
                if (physicsState.outcome == SimulationOutcome.IDLE) launchSimulation() else resetSimulation()
              },
            )
          }
        }
      } else {
        Column(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.spacedBy(10.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Box(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth(),
            contentAlignment = Alignment.Center,
          ) {
            PhysicsArenaCanvas(
              level = level,
              physicsState = physicsState,
              placedTools = placedTools,
              selectedToolId = selectedToolId,
              previewPath = previewPath,
              hintTools = revealedHintTools,
              orbSkin = orbSkin,
              interactive = physicsState.outcome == SimulationOutcome.IDLE,
              onTapArena = { pos ->
                val hit = placedTools.minByOrNull { Vec2(it.x, it.y).distanceTo(pos) }
                if (hit != null && Vec2(hit.x, hit.y).distanceTo(pos) < 0.09f) {
                  selectedToolId = hit.id
                } else {
                  val typeToPlace = activePlacementType ?: level.toolBudget.keys.firstOrNull()
                  if (typeToPlace != null) placeToolAt(typeToPlace, pos)
                }
              },
              onDragTool = { id, newPos ->
                selectedToolId = id
                placedTools = placedTools.map { t ->
                  if (t.id == id) t.copy(x = newPos.x, y = newPos.y) else t
                }
              },
            )
          }

          PlayControlDeck(
            level = level,
            placedTools = placedTools,
            selectedTool = selectedTool,
            activePlacementType = activePlacementType,
            isRunning = physicsState.outcome == SimulationOutcome.RUNNING,
            isIdle = physicsState.outcome == SimulationOutcome.IDLE,
            speedMultiplier = speedMultiplier,
            starsCollected = physicsState.collectedStarIndices.size,
            onSelectBudgetType = { type ->
              activePlacementType = type
              val used = placedTools.count { it.type == type }
              val cap = level.toolBudget[type] ?: 0
              if (used < cap && physicsState.outcome == SimulationOutcome.IDLE) {
                placeToolAt(type, Vec2(0.50f, 0.55f))
              }
            },
            onRotateTool = { delta ->
              val id = selectedTool?.id ?: return@PlayControlDeck
              placedTools = placedTools.map { if (it.id == id) it.copy(angleDeg = (it.angleDeg + delta + 360f) % 360f) else it }
            },
            onSetAngle = { angle ->
              val id = selectedTool?.id ?: return@PlayControlDeck
              placedTools = placedTools.map { if (it.id == id) it.copy(angleDeg = angle) else it }
            },
            onDeleteSelected = {
              val id = selectedTool?.id ?: return@PlayControlDeck
              placedTools = placedTools.filterNot { it.id == id }
              selectedToolId = null
            },
            onClearAll = {
              placedTools = emptyList()
              selectedToolId = null
              resetSimulation()
            },
            onToggleSpeed = { speedMultiplier = if (speedMultiplier > 1.5f) 1f else 2f },
            onLaunchOrReset = {
              if (physicsState.outcome == SimulationOutcome.IDLE) launchSimulation() else resetSimulation()
            },
          )
        }
      }
    }
  }

  val breakdown = winBreakdown
  if (breakdown != null) {
    AlertDialog(
      onDismissRequest = {},
      title = {
        Text(
          text = "Chamber Solved!",
          style = MaterialTheme.typography.headlineMedium,
          color = kinetic.emeraldSuccess,
        )
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (s in 1..3) {
              Icon(
                imageVector = if (s <= breakdown.starsEarned) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                contentDescription = null,
                tint = kinetic.amberGold,
                modifier = Modifier.size(32.dp),
              )
            }
          }
          Text(
            text = "${breakdown.totalScore} PTS",
            style = MaterialTheme.typography.displayMedium,
            color = kinetic.amberGold,
          )
          Text(
            text = "Base: ${breakdown.baseScore} • Stars: +${breakdown.starBonus}\nEfficiency: +${breakdown.efficiencyBonus} • Trick: +${breakdown.speedAndTrickBonus}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          OutlinedButton(
            onClick = {
              SocialShareHelper.shareHighScore(
                context = context,
                playerName = playerName,
                headline = if (level.number > 0) "Solved Level ${level.number}: ${level.title}" else "Solved ${level.title}",
                score = breakdown.totalScore,
                stars = totalStars + breakdown.starsEarned,
                levelsCompleted = levelsCompleted + 1,
                streakDays = streakDays,
              )
            },
            modifier = Modifier.fillMaxWidth().testTag("share_win_button"),
          ) {
            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Share Score Card")
          }
        }
      },
      confirmButton = {
        if (onNextLevel != null && level.number < LevelCatalog.TOTAL_LEVELS) {
          Button(
            onClick = {
              winBreakdown = null
              onNextLevel()
            },
            colors = ButtonDefaults.buttonColors(containerColor = kinetic.cyanGlow),
            modifier = Modifier.testTag("next_level_button"),
          ) {
            Text("Next Level")
            Spacer(modifier = Modifier.width(4.dp))
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
          }
        } else {
          Button(onClick = { winBreakdown = null; onBack() }) {
            Text("Done")
          }
        }
      },
      dismissButton = {
        TextButton(onClick = ::resetSimulation) { Text("Replay") }
      },
    )
  }
}

@Composable
private fun PlayControlDeck(
  level: PuzzleLevel,
  placedTools: List<PlacedTool>,
  selectedTool: PlacedTool?,
  activePlacementType: ToolType?,
  isRunning: Boolean,
  isIdle: Boolean,
  speedMultiplier: Float,
  starsCollected: Int,
  onSelectBudgetType: (ToolType) -> Unit,
  onRotateTool: (Float) -> Unit,
  onSetAngle: (Float) -> Unit,
  onDeleteSelected: () -> Unit,
  onClearAll: () -> Unit,
  onToggleSpeed: () -> Unit,
  onLaunchOrReset: () -> Unit,
) {
  val kinetic = LocalKineticColors.current
  Surface(
    shape = RoundedCornerShape(18.dp),
    color = MaterialTheme.colorScheme.surfaceVariant,
    tonalElevation = 4.dp,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      // Tool budget strip
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        level.toolBudget.forEach { (type, total) ->
          val used = placedTools.count { it.type == type }
          val remaining = (total - used).coerceAtLeast(0)
          val selected = activePlacementType == type
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (selected) kinetic.cyanGlow.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
            modifier = Modifier
              .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) kinetic.cyanGlow else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                shape = RoundedCornerShape(12.dp),
              )
              .clip(RoundedCornerShape(12.dp))
              .clickable(enabled = isIdle) { onSelectBudgetType(type) }
              .testTag("tool_budget_${type.name}"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
              Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = kinetic.cyanGlow)
              Text(
                text = "${type.shortTag} $remaining/$total",
                style = MaterialTheme.typography.labelMedium,
                color = if (remaining > 0) MaterialTheme.colorScheme.onSurface else kinetic.mutedText,
              )
            }
          }
        }
      }

      // Selected tool angle / delete controls when IDLE
      if (isIdle && selectedTool != null) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          if (selectedTool.type.rotatable) {
            IconButton(onClick = { onRotateTool(-15f) }, modifier = Modifier.size(48.dp)) {
              Icon(Icons.Rounded.RotateLeft, contentDescription = "Rotate Left")
            }
            Slider(
              value = selectedTool.angleDeg,
              onValueChange = onSetAngle,
              valueRange = 0f..360f,
              colors = SliderDefaults.colors(thumbColor = kinetic.cyanGlow, activeTrackColor = kinetic.cyanGlow),
              modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onRotateTool(15f) }, modifier = Modifier.size(48.dp)) {
              Icon(Icons.Rounded.RotateRight, contentDescription = "Rotate Right")
            }
          } else {
            Text(
              text = "${selectedTool.type.displayName} selected — drag on arena",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.weight(1f),
            )
          }
          IconButton(onClick = onDeleteSelected, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Rounded.Delete, contentDescription = "Remove Tool", tint = kinetic.crimsonHazard)
          }
        }
      } else {
        Text(
          text = if (isIdle) "Tap a tool to add, drag to move. Stars: $starsCollected/3" else "Simulating physics • Stars: $starsCollected/3",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      // Action bar: Clear, 1x/2x Speed, Launch / Reset
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        OutlinedButton(
          onClick = onClearAll,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.height(48.dp),
        ) {
          Text("Clear")
        }
        OutlinedButton(
          onClick = onToggleSpeed,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.height(48.dp),
        ) {
          Icon(Icons.Rounded.FastForward, contentDescription = "Speed", modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("${speedMultiplier.toInt()}x", style = MaterialTheme.typography.labelMedium)
        }
        Button(
          onClick = onLaunchOrReset,
          shape = RoundedCornerShape(12.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = if (isIdle) kinetic.emeraldSuccess else kinetic.amberGold,
            contentColor = MaterialTheme.colorScheme.onPrimary,
          ),
          modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .testTag("launch_reset_button"),
        ) {
          Icon(
            imageVector = if (isIdle) Icons.Rounded.PlayArrow else Icons.Rounded.Refresh,
            contentDescription = null,
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = when {
              isIdle -> "LAUNCH"
              isRunning -> "RESET"
              else -> "RETRY"
            },
            fontWeight = FontWeight.Bold,
          )
        }
      }
    }
  }
}
