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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.LevelSerializer
import com.example.data.local.LocalCustomLevelEntity
import com.example.physics.ElementType
import com.example.physics.EmitterConfig
import com.example.physics.GoalConfig
import com.example.physics.GravityMode
import com.example.physics.LevelElement
import com.example.physics.PhysicsEngine
import com.example.physics.PuzzleLevel
import com.example.physics.ToolType
import com.example.physics.Vec2
import com.example.ui.components.PhysicsArenaCanvas
import com.example.ui.theme.LocalKineticColors
import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private enum class EditorBrush(val label: String) {
  EMITTER("Emitter"),
  GOAL("Goal"),
  STAR("Star"),
  WALL("Wall"),
  HAZARD("Laser"),
  MOVING("Moving"),
  PORTAL("Portal"),
}

@Composable
fun LevelEditorScreen(
  existingEntity: LocalCustomLevelEntity?,
  authorName: String,
  orbSkin: String,
  onSaveLevel: (
    level: PuzzleLevel,
    title: String,
    description: String,
    published: Boolean,
    verified: Boolean,
    existingId: String?,
  ) -> Unit,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val kinetic = LocalKineticColors.current

  val initialPuzzle = remember(existingEntity?.id) {
    existingEntity?.let { LevelSerializer.fromEntity(it) } ?: PuzzleLevel(
      id = "draft_new",
      number = 0,
      worldIndex = 0,
      worldName = "Custom Lab",
      title = "My Chamber",
      subtitle = "Earth 1G",
      gravityMode = GravityMode.EARTH,
      emitter = EmitterConfig(0.18f, 0.24f, 20f, 0.68f),
      goal = GoalConfig(0.82f, 0.94f, 0.056f),
      elements = listOf(LevelElement("w_init", ElementType.WALL, 0.30f, 0.62f, 0.68f, 0.62f)),
      stars = listOf(Vec2(0.45f, 0.35f), Vec2(0.75f, 0.55f)),
      toolBudget = mapOf(ToolType.DEFLECTOR to 2, ToolType.BUMPER to 1),
      isCustom = true,
      authorName = authorName,
    )
  }

  var title by remember(existingEntity?.id) { mutableStateOf(existingEntity?.title ?: "Quantum Contraption") }
  var description by remember(existingEntity?.id) { mutableStateOf(existingEntity?.description ?: "Guide the orb into the vortex.") }
  var gravityMode by remember(existingEntity?.id) { mutableStateOf(initialPuzzle.gravityMode) }
  var emitter by remember(existingEntity?.id) { mutableStateOf(initialPuzzle.emitter) }
  var goal by remember(existingEntity?.id) { mutableStateOf(initialPuzzle.goal) }
  var elements by remember(existingEntity?.id) { mutableStateOf(initialPuzzle.elements) }
  var stars by remember(existingEntity?.id) { mutableStateOf(initialPuzzle.stars) }
  var toolBudget by remember(existingEntity?.id) { mutableStateOf(initialPuzzle.toolBudget) }
  var verified by remember(existingEntity?.id) { mutableStateOf(existingEntity?.verified ?: false) }

  var activeBrush by remember { mutableStateOf(EditorBrush.WALL) }
  var newSegmentAngleDeg by remember { mutableStateOf(0f) }
  var isTestPlaying by remember { mutableStateOf(false) }
  var showSaveDialog by remember { mutableStateOf(false) }
  var publishChecked by remember(existingEntity?.id) { mutableStateOf(existingEntity?.published ?: false) }

  val currentLevel = remember(
    title, description, gravityMode, emitter, goal, elements, stars, toolBudget, authorName,
  ) {
    PuzzleLevel(
      id = existingEntity?.id ?: "preview_editor",
      number = 0,
      worldIndex = 0,
      worldName = "Custom Lab",
      title = title.ifBlank { "Custom Chamber" },
      subtitle = description,
      gravityMode = gravityMode,
      emitter = emitter,
      goal = goal,
      elements = elements,
      stars = stars,
      toolBudget = toolBudget.filterValues { it > 0 },
      parTools = toolBudget.values.sum().coerceAtLeast(1),
      isCustom = true,
      authorName = authorName,
    )
  }

  if (isTestPlaying) {
    PlayLevelScreen(
      level = currentLevel,
      orbSkin = orbSkin,
      hintsRemaining = 0,
      playerName = authorName,
      totalStars = 0,
      levelsCompleted = 0,
      streakDays = 0,
      onUseHint = { false },
      onLevelSolved = { _, _, _ ->
        verified = true
      },
      onNextLevel = null,
      onBack = { isTestPlaying = false },
    )
    return
  }

  val previewPath = remember(currentLevel) {
    PhysicsEngine.computePreviewPath(currentLevel, emptyList(), steps = 40)
  }

  fun handleArenaTap(pos: Vec2) {
    val cx = pos.x.coerceIn(0.08f, 0.92f)
    val cy = pos.y.coerceIn(0.08f, 1.12f)
    when (activeBrush) {
      EditorBrush.EMITTER -> emitter = emitter.copy(x = cx, y = cy)
      EditorBrush.GOAL -> goal = goal.copy(x = cx, y = cy)
      EditorBrush.STAR -> {
        val next = if (stars.size >= 3) stars.drop(1) + Vec2(cx, cy) else stars + Vec2(cx, cy)
        stars = next
      }
      EditorBrush.WALL, EditorBrush.HAZARD, EditorBrush.MOVING -> {
        val rad = newSegmentAngleDeg * (PI.toFloat() / 180f)
        val half = 0.13f
        val elType = when (activeBrush) {
          EditorBrush.HAZARD -> ElementType.HAZARD
          EditorBrush.MOVING -> ElementType.MOVING_WALL
          else -> ElementType.WALL
        }
        elements = (elements + LevelElement(
          id = UUID.randomUUID().toString().take(8),
          type = elType,
          x1 = (cx - cos(rad) * half).coerceIn(0.05f, 0.95f),
          y1 = (cy - sin(rad) * half).coerceIn(0.05f, 1.15f),
          x2 = (cx + cos(rad) * half).coerceIn(0.05f, 0.95f),
          y2 = (cy + sin(rad) * half).coerceIn(0.05f, 1.15f),
          speed = 1.7f,
          amplitude = if (elType == ElementType.MOVING_WALL) 0.11f else 0f,
        )).takeLast(35)
      }
      EditorBrush.PORTAL -> {
        val otherX = (1.0f - cx).coerceIn(0.12f, 0.88f)
        val otherY = (1.2f - cy).coerceIn(0.12f, 1.08f)
        val filtered = elements.filterNot { it.type == ElementType.PORTAL_PAIR }
        elements = filtered + LevelElement(
          id = "portal_custom",
          type = ElementType.PORTAL_PAIR,
          x1 = cx,
          y1 = cy,
          x2 = otherX,
          y2 = otherY,
        )
      }
    }
    verified = false
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .windowInsetsPadding(WindowInsets.safeDrawing)
      .padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
          Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
        }
        Column {
          Text("Level Studio", style = MaterialTheme.typography.titleLarge)
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (verified) {
              Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = kinetic.emeraldSuccess, modifier = Modifier.size(14.dp))
              Text("Play-Verified", style = MaterialTheme.typography.labelSmall, color = kinetic.emeraldSuccess)
            } else {
              Text("Test play to verify solvability", style = MaterialTheme.typography.labelSmall, color = kinetic.amberGold)
            }
          }
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
          onClick = { isTestPlaying = true },
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.testTag("editor_test_play_button"),
        ) {
          Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Test")
        }
        Button(
          onClick = { showSaveDialog = true },
          colors = ButtonDefaults.buttonColors(containerColor = kinetic.cyanGlow),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.testTag("editor_save_button"),
        ) {
          Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Save", fontWeight = FontWeight.Bold)
        }
      }
    }

    BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
      val isWide = maxWidth >= 600.dp
      if (isWide) {
        Row(
          modifier = Modifier.fillMaxSize(),
          horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            PhysicsArenaCanvas(
              level = currentLevel,
              physicsState = PhysicsEngine.initialState(currentLevel),
              placedTools = emptyList(),
              selectedToolId = null,
              previewPath = previewPath,
              hintTools = emptyList(),
              orbSkin = orbSkin,
              interactive = true,
              onTapArena = ::handleArenaTap,
              onDragTool = { _, _ -> },
              modifier = Modifier.fillMaxHeight(),
            )
          }
          Column(
            modifier = Modifier
              .width(350.dp)
              .fillMaxHeight()
              .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            EditorControlsPanel(
              activeBrush = activeBrush,
              onSelectBrush = { activeBrush = it },
              gravityMode = gravityMode,
              onSelectGravity = { gravityMode = it; verified = false },
              emitterAngle = emitter.angleDeg,
              onEmitterAngle = { emitter = emitter.copy(angleDeg = it); verified = false },
              segmentAngle = newSegmentAngleDeg,
              onSegmentAngle = { newSegmentAngleDeg = it },
              toolBudget = toolBudget,
              onAdjustBudget = { type, delta ->
                val cur = (toolBudget[type] ?: 0) + delta
                toolBudget = toolBudget.toMutableMap().apply {
                  if (cur <= 0) remove(type) else put(type, cur.coerceAtMost(5))
                }
              },
              onUndoElement = {
                if (elements.isNotEmpty()) elements = elements.dropLast(1)
                verified = false
              },
              onClearElements = {
                elements = emptyList()
                stars = emptyList()
                verified = false
              },
            )
          }
        }
      } else {
        Column(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.spacedBy(8.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            PhysicsArenaCanvas(
              level = currentLevel,
              physicsState = PhysicsEngine.initialState(currentLevel),
              placedTools = emptyList(),
              selectedToolId = null,
              previewPath = previewPath,
              hintTools = emptyList(),
              orbSkin = orbSkin,
              interactive = true,
              onTapArena = ::handleArenaTap,
              onDragTool = { _, _ -> },
            )
          }
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .height(215.dp)
              .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            EditorControlsPanel(
              activeBrush = activeBrush,
              onSelectBrush = { activeBrush = it },
              gravityMode = gravityMode,
              onSelectGravity = { gravityMode = it; verified = false },
              emitterAngle = emitter.angleDeg,
              onEmitterAngle = { emitter = emitter.copy(angleDeg = it); verified = false },
              segmentAngle = newSegmentAngleDeg,
              onSegmentAngle = { newSegmentAngleDeg = it },
              toolBudget = toolBudget,
              onAdjustBudget = { type, delta ->
                val cur = (toolBudget[type] ?: 0) + delta
                toolBudget = toolBudget.toMutableMap().apply {
                  if (cur <= 0) remove(type) else put(type, cur.coerceAtMost(5))
                }
              },
              onUndoElement = {
                if (elements.isNotEmpty()) elements = elements.dropLast(1)
                verified = false
              },
              onClearElements = {
                elements = emptyList()
                stars = emptyList()
                verified = false
              },
            )
          }
        }
      }
    }
  }

  if (showSaveDialog) {
    AlertDialog(
      onDismissRequest = { showSaveDialog = false },
      title = { Text("Save Custom Chamber") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = title,
            onValueChange = { title = it.take(50) },
            label = { Text("Level Title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = description,
            onValueChange = { description = it.take(160) },
            label = { Text("Hint / Description") },
            maxLines = 2,
            modifier = Modifier.fillMaxWidth(),
          )
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { publishChecked = !publishChecked },
          ) {
            Checkbox(checked = publishChecked, onCheckedChange = { publishChecked = it })
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = if (verified) "Publish to Community (Verified)" else "Publish to Community",
              style = MaterialTheme.typography.bodyMedium,
            )
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            showSaveDialog = false
            onSaveLevel(currentLevel, title, description, publishChecked, verified, existingEntity?.id)
          },
          colors = ButtonDefaults.buttonColors(containerColor = kinetic.cyanGlow),
        ) {
          Text("Save")
        }
      },
      dismissButton = {
        TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") }
      },
    )
  }
}

@Composable
private fun EditorControlsPanel(
  activeBrush: EditorBrush,
  onSelectBrush: (EditorBrush) -> Unit,
  gravityMode: GravityMode,
  onSelectGravity: (GravityMode) -> Unit,
  emitterAngle: Float,
  onEmitterAngle: (Float) -> Unit,
  segmentAngle: Float,
  onSegmentAngle: (Float) -> Unit,
  toolBudget: Map<ToolType, Int>,
  onAdjustBudget: (ToolType, Int) -> Unit,
  onUndoElement: () -> Unit,
  onClearElements: () -> Unit,
) {
  val kinetic = LocalKineticColors.current
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surfaceVariant,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text("Tap arena to place:", style = MaterialTheme.typography.labelLarge)
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        EditorBrush.entries.forEach { brush ->
          val selected = brush == activeBrush
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = if (selected) kinetic.cyanGlow.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surface,
            modifier = Modifier
              .border(
                width = 1.dp,
                color = if (selected) kinetic.cyanGlow else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                shape = RoundedCornerShape(10.dp),
              )
              .clip(RoundedCornerShape(10.dp))
              .clickable { onSelectBrush(brush) },
          ) {
            Text(
              text = brush.label,
              style = MaterialTheme.typography.labelMedium,
              color = if (selected) kinetic.cyanGlow else MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            )
          }
        }
      }

      // Gravity preset chips
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        GravityMode.entries.forEach { mode ->
          val selected = mode == gravityMode
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = if (selected) kinetic.amberGold.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surface,
            modifier = Modifier
              .clip(RoundedCornerShape(10.dp))
              .clickable { onSelectGravity(mode) },
          ) {
            Text(
              text = mode.label,
              style = MaterialTheme.typography.labelSmall,
              color = if (selected) kinetic.amberGold else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            )
          }
        }
      }

      if (activeBrush == EditorBrush.EMITTER) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text("Launch Angle", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(105.dp))
          Slider(
            value = emitterAngle,
            onValueChange = onEmitterAngle,
            valueRange = -180f..180f,
            modifier = Modifier.weight(1f),
          )
        }
      } else if (activeBrush == EditorBrush.WALL || activeBrush == EditorBrush.HAZARD || activeBrush == EditorBrush.MOVING) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text("Bar Angle", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(105.dp))
          Slider(
            value = segmentAngle,
            onValueChange = onSegmentAngle,
            valueRange = 0f..180f,
            modifier = Modifier.weight(1f),
          )
        }
      }

      // Player tool budget
      Text("Player Tool Budget:", style = MaterialTheme.typography.labelLarge)
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        ToolType.entries.forEach { type ->
          val count = toolBudget[type] ?: 0
          Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surface) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Text("${type.shortTag}: $count", style = MaterialTheme.typography.labelMedium)
              Text(
                text = "−",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.clickable { onAdjustBudget(type, -1) }.padding(4.dp),
              )
              Text(
                text = "+",
                style = MaterialTheme.typography.titleMedium,
                color = kinetic.cyanGlow,
                modifier = Modifier.clickable { onAdjustBudget(type, 1) }.padding(4.dp),
              )
            }
          }
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onUndoElement, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)) {
          Text("Undo Last")
        }
        OutlinedButton(onClick = onClearElements, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)) {
          Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = kinetic.crimsonHazard)
          Spacer(modifier = Modifier.width(4.dp))
          Text("Clear Board")
        }
      }
    }
  }
}
