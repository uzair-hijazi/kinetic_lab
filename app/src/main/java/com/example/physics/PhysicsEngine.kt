package com.example.physics

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

object PhysicsEngine {
  private const val SUB_STEPS = 6
  private const val MAX_SPEED = 2.4f
  private const val MIN_SPEED_FLOOR = 0.08f
  private const val WALL_HALF_THICKNESS = 0.012f
  private const val DEFLECTOR_HALF_LEN = 0.11f
  private const val DEFLECTOR_HALF_THICKNESS = 0.014f
  private const val BUMPER_RADIUS = 0.048f
  private const val WELL_INFLUENCE_RADIUS = 0.27f
  private const val WELL_CORE_RADIUS = 0.022f
  private const val REPULSOR_RADIUS = 0.24f
  private const val BOOSTER_HALF_LEN = 0.08f
  private const val BOOSTER_RADIUS = 0.045f
  private const val PORTAL_RADIUS = 0.044f
  private const val STAR_PICKUP_RADIUS = 0.040f
  const val MAX_SIM_TIME = 9.0f

  fun initialState(level: PuzzleLevel): PhysicsState {
    val rad = level.emitter.angleDeg * (PI.toFloat() / 180f)
    val vx = cos(rad) * level.emitter.speed
    val vy = sin(rad) * level.emitter.speed
    return PhysicsState(
      orbPos = Vec2(level.emitter.x, level.emitter.y),
      orbVel = Vec2(vx, vy),
      trail = listOf(Vec2(level.emitter.x, level.emitter.y)),
      outcome = SimulationOutcome.IDLE,
    )
  }

  fun movingWallSegment(element: LevelElement, simTime: Float): Pair<Vec2, Vec2> {
    if (element.type != ElementType.MOVING_WALL) {
      return Vec2(element.x1, element.y1) to Vec2(element.x2, element.y2)
    }
    val dx = element.x2 - element.x1
    val dy = element.y2 - element.y1
    val len = kotlin.math.hypot(dx, dy).coerceAtLeast(1e-4f)
    // Oscillate perpendicular to wall direction
    val nx = -dy / len
    val ny = dx / len
    val shift = sin(simTime * element.speed) * element.amplitude
    return Vec2(element.x1 + nx * shift, element.y1 + ny * shift) to
      Vec2(element.x2 + nx * shift, element.y2 + ny * shift)
  }

  fun toolEndpoints(tool: PlacedTool, halfLen: Float = DEFLECTOR_HALF_LEN): Pair<Vec2, Vec2> {
    val rad = tool.angleDeg * (PI.toFloat() / 180f)
    val dx = cos(rad) * halfLen
    val dy = sin(rad) * halfLen
    return Vec2(tool.x - dx, tool.y - dy) to Vec2(tool.x + dx, tool.y + dy)
  }

  fun step(
    state: PhysicsState,
    level: PuzzleLevel,
    placedTools: List<PlacedTool>,
    dt: Float,
    emitParticles: Boolean = true,
  ): PhysicsState {
    if (state.outcome != SimulationOutcome.RUNNING) return state

    val clampedDt = dt.coerceIn(0.001f, 0.035f)
    val subDt = clampedDt / SUB_STEPS
    var pos = state.orbPos
    var vel = state.orbVel
    var time = state.simTime
    var bounces = state.bounces
    var portalCooldown = (state.portalCooldown - clampedDt).coerceAtLeast(0f)
    val collectedStars = state.collectedStarIndices.toMutableSet()
    val newParticles = if (emitParticles) {
      state.particles.mapNotNull { p ->
        val nextLife = p.life - clampedDt
        if (nextLife <= 0f) null
        else p.copy(x = p.x + p.vx * clampedDt, y = p.y + p.vy * clampedDt, life = nextLife)
      }.toMutableList()
    } else {
      mutableListOf()
    }

    var outcome = SimulationOutcome.RUNNING
    val gravityY = level.gravityMode.accelY

    for (stepIdx in 0 until SUB_STEPS) {
      time += subDt

      // 1. Gravity & gentle damping in non-zero G
      vel = Vec2(vel.x, vel.y + gravityY * subDt)
      if (level.gravityMode == GravityMode.ZERO_G) {
        vel *= (1f - 0.015f * subDt)
      }

      // 2. Field tools (Gravity Wells, Repulsors, Boosters)
      for (tool in placedTools) {
        when (tool.type) {
          ToolType.GRAVITY_WELL -> {
            val delta = Vec2(tool.x, tool.y) - pos
            val dist = delta.length()
            if (dist < WELL_INFLUENCE_RADIUS && dist > 1e-4f) {
              val dir = delta / dist
              val clampedDist = max(dist, 0.065f)
              val strength = 0.022f / (clampedDist * clampedDist)
              vel += dir * (strength.coerceAtMost(3.4f) * subDt)
              // Soft core deflection so orb slingshots cleanly rather than getting stuck
              if (dist < WELL_CORE_RADIUS + ORB_RADIUS) {
                val n = (pos - Vec2(tool.x, tool.y)).normalized()
                pos = Vec2(tool.x, tool.y) + n * (WELL_CORE_RADIUS + ORB_RADIUS)
                val vn = vel.dot(n)
                if (vn < 0f) {
                  vel -= n * (1.5f * vn)
                }
              }
            }
          }
          ToolType.REPULSOR -> {
            val delta = pos - Vec2(tool.x, tool.y)
            val dist = delta.length()
            if (dist < REPULSOR_RADIUS && dist > 1e-4f) {
              val dir = delta / dist
              val falloff = (1f - dist / REPULSOR_RADIUS)
              vel += dir * (2.6f * falloff * subDt)
            }
          }
          ToolType.BOOSTER -> {
            val dist = pos.distanceTo(Vec2(tool.x, tool.y))
            if (dist < BOOSTER_RADIUS + ORB_RADIUS) {
              val rad = tool.angleDeg * (PI.toFloat() / 180f)
              val boostDir = Vec2(cos(rad), sin(rad))
              vel += boostDir * (3.8f * subDt)
              val speed = vel.length()
              if (speed < 0.85f) {
                vel = boostDir * 0.92f
              }
            }
          }
          else -> Unit
        }
      }

      // 3. Subtle magnetic pull near goal vortex so near-hits feel fair
      val goalPos = Vec2(level.goal.x, level.goal.y)
      val toGoal = goalPos - pos
      val goalDist = toGoal.length()
      if (goalDist < level.goal.radius * 1.65f && goalDist > 1e-4f) {
        vel += (toGoal / goalDist) * (0.9f * subDt)
      }

      // Clamp max speed
      val curSpeed = vel.length()
      if (curSpeed > MAX_SPEED) {
        vel = vel.normalized() * MAX_SPEED
      }

      // 4. Integrate position
      pos += vel * subDt

      // 5. Arena containment boundaries
      if (pos.x - ORB_RADIUS < 0f) {
        pos = Vec2(ORB_RADIUS, pos.y)
        vel = Vec2(kotlin.math.abs(vel.x) * 0.86f, vel.y)
        bounces++
        if (emitParticles) addBurst(newParticles, pos.x, pos.y, isHazard = false, isStar = false)
      } else if (pos.x + ORB_RADIUS > ARENA_WIDTH) {
        pos = Vec2(ARENA_WIDTH - ORB_RADIUS, pos.y)
        vel = Vec2(-kotlin.math.abs(vel.x) * 0.86f, vel.y)
        bounces++
        if (emitParticles) addBurst(newParticles, pos.x, pos.y, isHazard = false, isStar = false)
      }
      if (pos.y - ORB_RADIUS < 0f) {
        pos = Vec2(pos.x, ORB_RADIUS)
        vel = Vec2(vel.x, kotlin.math.abs(vel.y) * 0.86f)
        bounces++
        if (emitParticles) addBurst(newParticles, pos.x, pos.y, isHazard = false, isStar = false)
      } else if (pos.y + ORB_RADIUS > ARENA_HEIGHT) {
        pos = Vec2(pos.x, ARENA_HEIGHT - ORB_RADIUS)
        vel = Vec2(vel.x, -kotlin.math.abs(vel.y) * 0.86f)
        bounces++
        if (emitParticles) addBurst(newParticles, pos.x, pos.y, isHazard = false, isStar = false)
      }

      // 6. Level elements (Walls, Hazards, Moving Walls, Portals)
      for (elem in level.elements) {
        when (elem.type) {
          ElementType.PORTAL_PAIR -> {
            if (portalCooldown <= 0f) {
              val pA = Vec2(elem.x1, elem.y1)
              val pB = Vec2(elem.x2, elem.y2)
              if (pos.distanceTo(pA) < PORTAL_RADIUS) {
                pos = pB + vel.normalized() * (PORTAL_RADIUS * 0.65f)
                portalCooldown = 0.35f
                if (emitParticles) addBurst(newParticles, pos.x, pos.y, isHazard = false, isStar = false)
              } else if (pos.distanceTo(pB) < PORTAL_RADIUS) {
                pos = pA + vel.normalized() * (PORTAL_RADIUS * 0.65f)
                portalCooldown = 0.35f
                if (emitParticles) addBurst(newParticles, pos.x, pos.y, isHazard = false, isStar = false)
              }
            }
          }
          ElementType.HAZARD -> {
            val hit = collideCircleSegment(pos, ORB_RADIUS + WALL_HALF_THICKNESS, Vec2(elem.x1, elem.y1), Vec2(elem.x2, elem.y2))
            if (hit != null) {
              outcome = SimulationOutcome.HAZARD_HIT
              if (emitParticles) addBurst(newParticles, pos.x, pos.y, isHazard = true, isStar = false, count = 14)
              break
            }
          }
          ElementType.WALL, ElementType.MOVING_WALL -> {
            val (a, b) = movingWallSegment(elem, time)
            val hit = collideCircleSegment(pos, ORB_RADIUS + WALL_HALF_THICKNESS, a, b)
            if (hit != null) {
              pos += hit.normal * hit.penetration
              val vn = vel.dot(hit.normal)
              if (vn < 0f) {
                vel -= hit.normal * (1.86f * vn)
                bounces++
                if (emitParticles) addBurst(newParticles, hit.contact.x, hit.contact.y, isHazard = false, isStar = false)
              }
            }
          }
        }
      }
      if (outcome != SimulationOutcome.RUNNING) break

      // 7. Player Deflectors & Elastic Bumpers
      for (tool in placedTools) {
        when (tool.type) {
          ToolType.DEFLECTOR -> {
            val (a, b) = toolEndpoints(tool, DEFLECTOR_HALF_LEN)
            val hit = collideCircleSegment(pos, ORB_RADIUS + DEFLECTOR_HALF_THICKNESS, a, b)
            if (hit != null) {
              pos += hit.normal * hit.penetration
              val vn = vel.dot(hit.normal)
              if (vn < 0f) {
                vel -= hit.normal * (1.94f * vn)
                // Gentle kinetic boost on crisp deflector hit
                val spd = vel.length()
                if (spd in MIN_SPEED_FLOOR..1.35f) {
                  vel *= 1.04f
                }
                bounces++
                if (emitParticles) addBurst(newParticles, hit.contact.x, hit.contact.y, isHazard = false, isStar = false)
              }
            }
          }
          ToolType.BUMPER -> {
            val center = Vec2(tool.x, tool.y)
            val delta = pos - center
            val dist = delta.length()
            val minDist = BUMPER_RADIUS + ORB_RADIUS
            if (dist < minDist && dist > 1e-5f) {
              val n = delta / dist
              pos = center + n * minDist
              val exitSpeed = max(vel.length() * 1.12f, 0.96f).coerceAtMost(MAX_SPEED)
              vel = n * exitSpeed
              bounces++
              if (emitParticles) addBurst(newParticles, pos.x, pos.y, isHazard = false, isStar = false, count = 8)
            }
          }
          else -> Unit
        }
      }

      // 8. Star pickups
      level.stars.forEachIndexed { index, starPos ->
        if (index !in collectedStars && pos.distanceTo(starPos) < STAR_PICKUP_RADIUS + ORB_RADIUS) {
          collectedStars.add(index)
          if (emitParticles) addBurst(newParticles, starPos.x, starPos.y, isHazard = false, isStar = true, count = 10)
        }
      }

      // 9. Goal vortex check
      if (pos.distanceTo(goalPos) <= level.goal.radius) {
        outcome = SimulationOutcome.WON
        if (emitParticles) addBurst(newParticles, goalPos.x, goalPos.y, isHazard = false, isStar = true, count = 16)
        break
      }

      if (time >= MAX_SIM_TIME) {
        outcome = SimulationOutcome.TIMED_OUT
        break
      }
    }

    val updatedTrail = if (emitParticles) {
      val last = state.trail.lastOrNull()
      if (last == null || last.distanceTo(pos) > 0.015f) {
        (state.trail + pos).takeLast(75)
      } else {
        state.trail
      }
    } else {
      state.trail
    }

    return state.copy(
      orbPos = pos,
      orbVel = vel,
      trail = updatedTrail,
      collectedStarIndices = collectedStars,
      outcome = outcome,
      simTime = time,
      bounces = bounces,
      portalCooldown = portalCooldown,
      particles = newParticles.takeLast(60),
    )
  }

  /** Fast forward preview for aiming before launch */
  fun computePreviewPath(
    level: PuzzleLevel,
    placedTools: List<PlacedTool>,
    steps: Int = 52,
  ): List<Vec2> {
    var sim = initialState(level).copy(outcome = SimulationOutcome.RUNNING)
    val points = ArrayList<Vec2>(steps)
    points.add(sim.orbPos)
    for (i in 0 until steps) {
      sim = step(sim, level, placedTools, dt = 0.02f, emitParticles = false)
      points.add(sim.orbPos)
      if (sim.outcome != SimulationOutcome.RUNNING) break
    }
    return points
  }

  /** Headless simulation used by level generator verification & tests */
  fun simulateHeadless(
    level: PuzzleLevel,
    placedTools: List<PlacedTool>,
    maxTime: Float = MAX_SIM_TIME,
  ): HeadlessResult {
    var sim = initialState(level).copy(outcome = SimulationOutcome.RUNNING)
    val path = ArrayList<Vec2>(300)
    path.add(sim.orbPos)
    val dt = 1f / 60f
    while (sim.outcome == SimulationOutcome.RUNNING && sim.simTime < maxTime) {
      sim = step(sim, level, placedTools, dt = dt, emitParticles = false)
      path.add(sim.orbPos)
    }
    return HeadlessResult(
      outcome = sim.outcome,
      starsCollected = sim.collectedStarIndices.size,
      simTime = sim.simTime,
      bounces = sim.bounces,
      path = path,
    )
  }

  fun calculateScore(
    level: PuzzleLevel,
    starsCollected: Int,
    toolsUsed: Int,
    simTime: Float,
    bounces: Int,
  ): LevelScoreBreakdown {
    val base = 500 + level.worldIndex * 100
    val starBonus = starsCollected * 350
    val budgetTotal = level.toolBudget.values.sum().coerceAtLeast(1)
    val unusedTools = (budgetTotal - toolsUsed).coerceAtLeast(0)
    val efficiencyBonus = unusedTools * 220
    val speedBonus = ((MAX_SIM_TIME - simTime).coerceAtLeast(0f) * 40f).toInt()
    val trickBonus = (bounces.coerceAtMost(8)) * 35
    val total = base + starBonus + efficiencyBonus + speedBonus + trickBonus
    val earnedStars = starsCollected.coerceIn(1, 3) // Solving grants at least 1 star, up to 3 with star crystals
    return LevelScoreBreakdown(
      baseScore = base,
      starBonus = starBonus,
      efficiencyBonus = efficiencyBonus,
      speedAndTrickBonus = speedBonus + trickBonus,
      totalScore = total,
      starsEarned = max(1, starsCollected.coerceAtMost(3)),
    )
  }

  data class HeadlessResult(
    val outcome: SimulationOutcome,
    val starsCollected: Int,
    val simTime: Float,
    val bounces: Int,
    val path: List<Vec2>,
  )

  data class LevelScoreBreakdown(
    val baseScore: Int,
    val starBonus: Int,
    val efficiencyBonus: Int,
    val speedAndTrickBonus: Int,
    val totalScore: Int,
    val starsEarned: Int,
  )

  private data class SegmentHit(
    val contact: Vec2,
    val normal: Vec2,
    val penetration: Float,
  )

  private fun collideCircleSegment(
    center: Vec2,
    radius: Float,
    a: Vec2,
    b: Vec2,
  ): SegmentHit? {
    val ab = b - a
    val abLenSq = ab.dot(ab)
    val t = if (abLenSq < 1e-8f) 0f else ((center - a).dot(ab) / abLenSq).coerceIn(0f, 1f)
    val closest = a + ab * t
    val diff = center - closest
    val dist = diff.length()
    if (dist >= radius) return null
    val normal = if (dist > 1e-5f) {
      diff / dist
    } else {
      val len = kotlin.math.sqrt(abLenSq).coerceAtLeast(1e-4f)
      Vec2(-ab.y / len, ab.x / len)
    }
    return SegmentHit(contact = closest, normal = normal, penetration = radius - dist)
  }

  private fun addBurst(
    out: MutableList<SparkParticle>,
    x: Float,
    y: Float,
    isHazard: Boolean,
    isStar: Boolean,
    count: Int = 5,
  ) {
    for (i in 0 until count) {
      val angle = (i.toFloat() / count) * (2f * PI.toFloat()) + (x * 13f + y * 7f)
      val speed = 0.22f + (i % 3) * 0.14f
      out.add(
        SparkParticle(
          x = x,
          y = y,
          vx = cos(angle) * speed,
          vy = sin(angle) * speed,
          life = 0.42f,
          maxLife = 0.42f,
          isHazard = isHazard,
          isStar = isStar,
        )
      )
    }
  }
}
