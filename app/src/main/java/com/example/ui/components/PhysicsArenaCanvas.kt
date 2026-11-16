package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.physics.ARENA_HEIGHT
import com.example.physics.ARENA_WIDTH
import com.example.physics.ElementType
import com.example.physics.ORB_RADIUS
import com.example.physics.PhysicsEngine
import com.example.physics.PhysicsState
import com.example.physics.PlacedTool
import com.example.physics.PuzzleLevel
import com.example.physics.SimulationOutcome
import com.example.physics.ToolType
import com.example.physics.Vec2
import com.example.ui.theme.LocalKineticColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun PhysicsArenaCanvas(
  level: PuzzleLevel,
  physicsState: PhysicsState,
  placedTools: List<PlacedTool>,
  selectedToolId: String?,
  previewPath: List<Vec2>,
  hintTools: List<PlacedTool>,
  orbSkin: String,
  interactive: Boolean,
  onTapArena: (Vec2) -> Unit,
  onDragTool: (String, Vec2) -> Unit,
  modifier: Modifier = Modifier,
) {
  val kinetic = LocalKineticColors.current
  val currentTools by rememberUpdatedState(placedTools)
  val currentSelectedId by rememberUpdatedState(selectedToolId)
  val currentInteractive by rememberUpdatedState(interactive)

  val infiniteTransition = rememberInfiniteTransition(label = "arena_pulse")
  val pulsePhase by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 360f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 4000, easing = LinearEasing),
      repeatMode = RepeatMode.Restart,
    ),
    label = "pulse_phase",
  )

  val orbColor = when (orbSkin) {
    "amber" -> kinetic.amberGold
    "emerald" -> kinetic.emeraldSuccess
    "magenta" -> kinetic.magentaBoost
    "prism" -> Color.White
    else -> kinetic.cyanGlow
  }

  val borderColor = MaterialTheme.colorScheme.outline

  Canvas(
    modifier = modifier
      .aspectRatio(ARENA_WIDTH / ARENA_HEIGHT)
      .clip(RoundedCornerShape(18.dp))
      .background(kinetic.arenaBackground)
      .border(1.5.dp, borderColor, RoundedCornerShape(18.dp))
      .pointerInput(level.id) {
        detectTapGestures { offset ->
          if (!currentInteractive) return@detectTapGestures
          val nx = (offset.x / size.width) * ARENA_WIDTH
          val ny = (offset.y / size.height) * ARENA_HEIGHT
          onTapArena(Vec2(nx, ny))
        }
      }
      .pointerInput(level.id) {
        var draggingId: String? = null
        detectDragGestures(
          onDragStart = { offset ->
            if (!currentInteractive) return@detectDragGestures
            val nx = (offset.x / size.width) * ARENA_WIDTH
            val ny = (offset.y / size.height) * ARENA_HEIGHT
            val tap = Vec2(nx, ny)
            val hit = currentTools.minByOrNull { Vec2(it.x, it.y).distanceTo(tap) }
            draggingId = if (hit != null && Vec2(hit.x, hit.y).distanceTo(tap) < 0.11f) {
              hit.id
            } else {
              currentSelectedId
            }
          },
          onDragEnd = { draggingId = null },
          onDragCancel = { draggingId = null },
          onDrag = { change, _ ->
            if (!currentInteractive) return@detectDragGestures
            val id = draggingId ?: return@detectDragGestures
            change.consume()
            val nx = (change.position.x / size.width).coerceIn(0.06f, 0.94f) * ARENA_WIDTH
            val ny = (change.position.y / size.height).coerceIn(0.06f, 0.94f) * ARENA_HEIGHT
            onDragTool(id, Vec2(nx, ny))
          },
        )
      },
  ) {
    val scaleX = size.width / ARENA_WIDTH
    val scaleY = size.height / ARENA_HEIGHT
    fun toPx(v: Vec2) = Offset(v.x * scaleX, v.y * scaleY)

    // 1. Subtle Blueprint Grid
    val gridStep = 0.1f
    var gx = gridStep
    while (gx < ARENA_WIDTH) {
      drawLine(
        color = kinetic.arenaGrid.copy(alpha = 0.45f),
        start = Offset(gx * scaleX, 0f),
        end = Offset(gx * scaleX, size.height),
        strokeWidth = 1f,
      )
      gx += gridStep
    }
    var gy = gridStep
    while (gy < ARENA_HEIGHT) {
      drawLine(
        color = kinetic.arenaGrid.copy(alpha = 0.45f),
        start = Offset(0f, gy * scaleY),
        end = Offset(size.width, gy * scaleY),
        strokeWidth = 1f,
      )
      gy += gridStep
    }

    // 2. Hint Ghost Tools (when player uses a Hint!)
    for (hint in hintTools) {
      val center = toPx(Vec2(hint.x, hint.y))
      drawCircle(
        color = kinetic.amberGold.copy(alpha = 0.18f),
        radius = 0.07f * scaleX,
        center = center,
      )
      drawCircle(
        color = kinetic.amberGold.copy(alpha = 0.85f),
        radius = 0.045f * scaleX,
        center = center,
        style = Stroke(
          width = 3f,
          pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f),
        ),
      )
      if (hint.type.rotatable) {
        val (a, b) = PhysicsEngine.toolEndpoints(hint, 0.10f)
        drawLine(
          color = kinetic.amberGold.copy(alpha = 0.75f),
          start = toPx(a),
          end = toPx(b),
          strokeWidth = 5f,
          cap = StrokeCap.Round,
          pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f),
        )
      }
    }

    // 3. Trajectory Preview (when IDLE)
    if (physicsState.outcome == SimulationOutcome.IDLE && previewPath.size > 1) {
      val path = Path()
      val first = toPx(previewPath.first())
      path.moveTo(first.x, first.y)
      for (i in 1 until previewPath.size) {
        val pt = toPx(previewPath[i])
        path.lineTo(pt.x, pt.y)
      }
      drawPath(
        path = path,
        color = orbColor.copy(alpha = 0.36f),
        style = Stroke(
          width = 3f,
          pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 12f), -pulsePhase * 0.4f),
        ),
      )
    }

    // 4. Level Elements
    for (elem in level.elements) {
      when (elem.type) {
        ElementType.WALL -> {
          val p1 = toPx(Vec2(elem.x1, elem.y1))
          val p2 = toPx(Vec2(elem.x2, elem.y2))
          drawLine(
            color = kinetic.mutedText.copy(alpha = 0.65f),
            start = p1,
            end = p2,
            strokeWidth = 0.024f * scaleX,
            cap = StrokeCap.Round,
          )
          drawLine(
            color = kinetic.mutedText,
            start = p1,
            end = p2,
            strokeWidth = 0.008f * scaleX,
            cap = StrokeCap.Round,
          )
        }
        ElementType.HAZARD -> {
          val p1 = toPx(Vec2(elem.x1, elem.y1))
          val p2 = toPx(Vec2(elem.x2, elem.y2))
          drawLine(
            color = kinetic.crimsonHazard.copy(alpha = 0.35f),
            start = p1,
            end = p2,
            strokeWidth = 0.036f * scaleX,
            cap = StrokeCap.Round,
          )
          drawLine(
            color = kinetic.crimsonHazard,
            start = p1,
            end = p2,
            strokeWidth = 0.014f * scaleX,
            cap = StrokeCap.Round,
          )
        }
        ElementType.MOVING_WALL -> {
          val (a, b) = PhysicsEngine.movingWallSegment(elem, physicsState.simTime)
          // Draw track baseline faintly
          drawLine(
            color = kinetic.amberGold.copy(alpha = 0.18f),
            start = toPx(Vec2(elem.x1, elem.y1)),
            end = toPx(Vec2(elem.x2, elem.y2)),
            strokeWidth = 3f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f),
          )
          drawLine(
            color = kinetic.amberGold.copy(alpha = 0.85f),
            start = toPx(a),
            end = toPx(b),
            strokeWidth = 0.024f * scaleX,
            cap = StrokeCap.Round,
          )
        }
        ElementType.PORTAL_PAIR -> {
          val pA = toPx(Vec2(elem.x1, elem.y1))
          val pB = toPx(Vec2(elem.x2, elem.y2))
          drawLine(
            color = kinetic.portalViolet.copy(alpha = 0.20f),
            start = pA,
            end = pB,
            strokeWidth = 2.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 12f), pulsePhase * 0.3f),
          )
          drawPortal(pA, 0.044f * scaleX, kinetic.portalViolet, pulsePhase)
          drawPortal(pB, 0.044f * scaleX, kinetic.portalViolet, -pulsePhase)
        }
      }
    }

    // 5. Collectible Stars
    level.stars.forEachIndexed { idx, star ->
      if (idx !in physicsState.collectedStarIndices) {
        val center = toPx(star)
        val r = 0.026f * scaleX
        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(kinetic.amberGold.copy(alpha = 0.45f), Color.Transparent),
            center = center,
            radius = r * 2.1f,
          ),
          center = center,
          radius = r * 2.1f,
        )
        drawStarDiamond(center, r, kinetic.amberGold, pulsePhase * 0.5f)
      }
    }

    // 6. Goal Vortex
    val goalCenter = toPx(Vec2(level.goal.x, level.goal.y))
    val goalR = level.goal.radius * scaleX
    drawCircle(
      brush = Brush.radialGradient(
        colors = listOf(kinetic.emeraldSuccess.copy(alpha = 0.55f), Color.Transparent),
        center = goalCenter,
        radius = goalR * 1.9f,
      ),
      center = goalCenter,
      radius = goalR * 1.9f,
    )
    drawCircle(
      color = kinetic.emeraldSuccess,
      radius = goalR,
      center = goalCenter,
      style = Stroke(width = 4.5f),
    )
    drawCircle(
      color = kinetic.emeraldSuccess.copy(alpha = 0.7f),
      radius = goalR * (0.45f + 0.2f * sin(pulsePhase * PI.toFloat() / 180f)),
      center = goalCenter,
      style = Stroke(width = 2.5f),
    )

    // 7. Emitter Node
    val emitCenter = toPx(Vec2(level.emitter.x, level.emitter.y))
    val emitRad = level.emitter.angleDeg * (PI.toFloat() / 180f)
    val barrelTip = Offset(
      emitCenter.x + cos(emitRad) * 0.048f * scaleX,
      emitCenter.y + sin(emitRad) * 0.048f * scaleX,
    )
    drawLine(
      color = orbColor.copy(alpha = 0.9f),
      start = emitCenter,
      end = barrelTip,
      strokeWidth = 0.022f * scaleX,
      cap = StrokeCap.Round,
    )
    drawCircle(
      color = kinetic.arenaGrid,
      radius = 0.032f * scaleX,
      center = emitCenter,
    )
    drawCircle(
      color = orbColor,
      radius = 0.032f * scaleX,
      center = emitCenter,
      style = Stroke(width = 3.5f),
    )

    // 8. Player-Placed Tools
    for (tool in placedTools) {
      val isSelected = tool.id == currentSelectedId
      val center = toPx(Vec2(tool.x, tool.y))
      if (isSelected) {
        drawCircle(
          color = kinetic.cyanGlow.copy(alpha = 0.22f),
          radius = 0.085f * scaleX,
          center = center,
        )
        drawCircle(
          color = kinetic.cyanGlow,
          radius = 0.085f * scaleX,
          center = center,
          style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)),
        )
      }
      when (tool.type) {
        ToolType.DEFLECTOR -> {
          val (a, b) = PhysicsEngine.toolEndpoints(tool, 0.11f)
          drawLine(
            color = kinetic.cyanGlow.copy(alpha = 0.35f),
            start = toPx(a),
            end = toPx(b),
            strokeWidth = 0.036f * scaleX,
            cap = StrokeCap.Round,
          )
          drawLine(
            color = kinetic.cyanGlow,
            start = toPx(a),
            end = toPx(b),
            strokeWidth = 0.018f * scaleX,
            cap = StrokeCap.Round,
          )
          drawCircle(color = Color.White, radius = 0.008f * scaleX, center = center)
        }
        ToolType.BUMPER -> {
          val r = 0.048f * scaleX
          drawCircle(color = kinetic.amberGold.copy(alpha = 0.28f), radius = r * 1.25f, center = center)
          drawCircle(color = kinetic.amberGold, radius = r, center = center, style = Stroke(width = 5f))
          drawCircle(color = Color.White, radius = r * 0.35f, center = center)
        }
        ToolType.GRAVITY_WELL -> {
          val r = 0.13f * scaleX
          drawCircle(
            brush = Brush.radialGradient(
              colors = listOf(kinetic.portalViolet.copy(alpha = 0.45f), Color.Transparent),
              center = center,
              radius = r,
            ),
            radius = r,
            center = center,
          )
          drawCircle(color = kinetic.portalViolet, radius = 0.026f * scaleX, center = center)
          drawCircle(
            color = kinetic.portalViolet.copy(alpha = 0.65f),
            radius = 0.068f * scaleX,
            center = center,
            style = Stroke(width = 2.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), pulsePhase)),
          )
        }
        ToolType.REPULSOR -> {
          val r = 0.12f * scaleX
          drawCircle(
            brush = Brush.radialGradient(
              colors = listOf(kinetic.magentaBoost.copy(alpha = 0.38f), Color.Transparent),
              center = center,
              radius = r,
            ),
            radius = r,
            center = center,
          )
          drawCircle(color = kinetic.magentaBoost, radius = 0.028f * scaleX, center = center, style = Stroke(width = 4f))
          drawCircle(
            color = kinetic.magentaBoost.copy(alpha = 0.5f),
            radius = 0.072f * scaleX,
            center = center,
            style = Stroke(width = 2f),
          )
        }
        ToolType.BOOSTER -> {
          val rad = tool.angleDeg * (PI.toFloat() / 180f)
          val dir = Offset(cos(rad), sin(rad))
          val start = Offset(center.x - dir.x * 0.045f * scaleX, center.y - dir.y * 0.045f * scaleX)
          val end = Offset(center.x + dir.x * 0.055f * scaleX, center.y + dir.y * 0.055f * scaleX)
          drawCircle(color = kinetic.magentaBoost.copy(alpha = 0.25f), radius = 0.05f * scaleX, center = center)
          drawLine(
            color = kinetic.magentaBoost,
            start = start,
            end = end,
            strokeWidth = 0.020f * scaleX,
            cap = StrokeCap.Round,
          )
          drawCircle(color = Color.White, radius = 0.012f * scaleX, center = end)
        }
      }
    }

    // 9. Orb Trail
    if (physicsState.trail.size > 1) {
      val count = physicsState.trail.size
      for (i in 1 until count) {
        val alpha = (i.toFloat() / count) * 0.65f
        drawLine(
          color = orbColor.copy(alpha = alpha),
          start = toPx(physicsState.trail[i - 1]),
          end = toPx(physicsState.trail[i]),
          strokeWidth = (ORB_RADIUS * 1.15f * scaleX) * (i.toFloat() / count),
          cap = StrokeCap.Round,
        )
      }
    }

    // 10. Spark Particles
    for (p in physicsState.particles) {
      val alpha = (p.life / p.maxLife).coerceIn(0f, 1f)
      val pColor = when {
        p.isHazard -> kinetic.crimsonHazard
        p.isStar -> kinetic.amberGold
        else -> orbColor
      }
      drawCircle(
        color = pColor.copy(alpha = alpha),
        radius = 0.009f * scaleX * alpha,
        center = toPx(Vec2(p.x, p.y)),
      )
    }

    // 11. Active Energy Orb
    if (physicsState.outcome != SimulationOutcome.HAZARD_HIT) {
      val orbPx = toPx(physicsState.orbPos)
      val orbR = ORB_RADIUS * scaleX
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(orbColor.copy(alpha = 0.65f), Color.Transparent),
          center = orbPx,
          radius = orbR * 2.4f,
        ),
        center = orbPx,
        radius = orbR * 2.4f,
      )
      drawCircle(color = orbColor, radius = orbR, center = orbPx)
      drawCircle(color = Color.White, radius = orbR * 0.45f, center = orbPx)
    }
  }
}

private fun DrawScope.drawPortal(center: Offset, radius: Float, color: Color, phaseDeg: Float) {
  drawCircle(
    brush = Brush.radialGradient(
      colors = listOf(color.copy(alpha = 0.5f), Color.Transparent),
      center = center,
      radius = radius * 1.6f,
    ),
    radius = radius * 1.6f,
    center = center,
  )
  drawCircle(
    color = color,
    radius = radius,
    center = center,
    style = Stroke(width = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), phaseDeg)),
  )
  drawCircle(color = Color.White.copy(alpha = 0.85f), radius = radius * 0.35f, center = center)
}

private fun DrawScope.drawStarDiamond(center: Offset, radius: Float, color: Color, rotDeg: Float) {
  val path = Path()
  val pts = 4
  for (i in 0 until pts * 2) {
    val r = if (i % 2 == 0) radius else radius * 0.42f
    val angle = (rotDeg + i * 45f) * (PI.toFloat() / 180f)
    val x = center.x + cos(angle) * r
    val y = center.y + sin(angle) * r
    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
  }
  path.close()
  drawPath(path = path, color = color)
}
