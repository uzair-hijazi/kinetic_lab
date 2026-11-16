package com.example.physics

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

data class WorldMeta(
  val index: Int,
  val name: String,
  val subtitle: String,
  val levelRange: IntRange,
  val requiredStarsToUnlock: Int,
)

object LevelCatalog {
  const val TOTAL_LEVELS = 240
  const val LEVELS_PER_WORLD = 40

  val worlds: List<WorldMeta> = listOf(
    WorldMeta(0, "Kinetic Basics", "Deflectors & Precision Ricochets", 1..40, 0),
    WorldMeta(1, "Elastic Cascade", "Pinball Bumpers & Laser Hazards", 41..80, 12),
    WorldMeta(2, "Orbital Wells", "Gravity Slingshots & Zero-G", 81..120, 30),
    WorldMeta(3, "Quantum Portals", "Twin-Chamber Momentum Warps", 121..160, 55),
    WorldMeta(4, "Chrono Flux", "Moving Barriers & Plasma Boosters", 161..200, 85),
    WorldMeta(5, "Singularity", "Master Contraption Gauntlet", 201..240, 120),
  )

  private val cache = HashMap<Int, PuzzleLevel>(TOTAL_LEVELS)

  private val titlePrefixes = listOf(
    "Vector", "Prism", "Quantum", "Flux", "Orbital", "Kinetic", "Photon", "Echo",
    "Chrono", "Apex", "Nova", "Pulse", "Aether", "Helix", "Zenith", "Vortex",
    "Lorentz", "Kepler", "Tesla", "Planck",
  )

  private val titleSuffixes = listOf(
    "Ricochet", "Slingshot", "Corridor", "Chamber", "Relay", "Pendulum", "Cascade",
    "Warp", "Mirror", "Horizon", "Trajectory", "Resonance", "Paradox", "Deflection",
    "Conduit", "Orbit", "Matrix", "Impulse", "Vault", "Singularity",
  )

  fun getWorldForLevel(levelNumber: Int): WorldMeta {
    val idx = ((levelNumber - 1) / LEVELS_PER_WORLD).coerceIn(0, worlds.lastIndex)
    return worlds[idx]
  }

  fun getLevel(levelNumber: Int): PuzzleLevel {
    val clamped = levelNumber.coerceIn(1, TOTAL_LEVELS)
    synchronized(cache) {
      cache[clamped]?.let { return it }
    }
    val generated = buildLevel(clamped)
    synchronized(cache) {
      cache[clamped] = generated
    }
    return generated
  }

  fun getLevelTitle(levelNumber: Int): String {
    return when (levelNumber) {
      1 -> "First Bounce"
      2 -> "Laser Angle"
      3 -> "Elastic Kick"
      4 -> "Orbital Bend"
      5 -> "Quantum Leap"
      6 -> "Moving Gate"
      else -> {
        val p = titlePrefixes[(levelNumber * 7 + 3) % titlePrefixes.size]
        val s = titleSuffixes[(levelNumber * 11 + 5) % titleSuffixes.size]
        "$p $s"
      }
    }
  }

  private fun buildLevel(levelNumber: Int): PuzzleLevel {
    return when (levelNumber) {
      1 -> buildTutorialLevel1()
      2 -> buildTutorialLevel2()
      3 -> buildTutorialLevel3()
      4 -> buildTutorialLevel4()
      5 -> buildTutorialLevel5()
      6 -> buildTutorialLevel6()
      else -> buildProceduralVerifiedLevel(levelNumber)
    }
  }

  private fun buildTutorialLevel1(): PuzzleLevel {
    val emitter = EmitterConfig(x = 0.18f, y = 0.24f, angleDeg = 18f, speed = 0.65f)
    val hint = listOf(PlacedTool("h1", ToolType.DEFLECTOR, 0.72f, 0.42f, 130f))
    val base = PuzzleLevel(
      id = "level_1",
      number = 1,
      worldIndex = 0,
      worldName = worlds[0].name,
      title = "First Bounce",
      subtitle = "Place & rotate the Deflector to guide the orb into the vortex.",
      gravityMode = GravityMode.EARTH,
      emitter = emitter,
      goal = GoalConfig(0.45f, 0.96f),
      elements = listOf(
        LevelElement("w1", ElementType.WALL, 0.10f, 0.62f, 0.50f, 0.62f),
      ),
      stars = listOf(Vec2(0.42f, 0.30f), Vec2(0.68f, 0.56f), Vec2(0.55f, 0.78f)),
      toolBudget = mapOf(ToolType.DEFLECTOR to 1),
      hintTools = hint,
      parTools = 1,
    )
    return calibrateStarsAndGoal(base, hint)
  }

  private fun buildTutorialLevel2(): PuzzleLevel {
    val emitter = EmitterConfig(x = 0.16f, y = 0.84f, angleDeg = -56f, speed = 0.96f)
    val hint = listOf(PlacedTool("h1", ToolType.DEFLECTOR, 0.64f, 0.48f, 130f))
    val base = PuzzleLevel(
      id = "level_2",
      number = 2,
      worldIndex = 0,
      worldName = worlds[0].name,
      title = "Laser Angle",
      subtitle = "Bank over the crimson hazard beam.",
      gravityMode = GravityMode.EARTH,
      emitter = emitter,
      goal = GoalConfig(0.84f, 0.88f),
      elements = listOf(
        LevelElement("hz1", ElementType.HAZARD, 0.50f, 0.72f, 0.50f, 1.15f),
      ),
      stars = listOf(Vec2(0.34f, 0.60f), Vec2(0.56f, 0.46f), Vec2(0.76f, 0.70f)),
      toolBudget = mapOf(ToolType.DEFLECTOR to 2),
      hintTools = hint,
      parTools = 1,
    )
    return calibrateStarsAndGoal(base, hint)
  }

  private fun buildTutorialLevel3(): PuzzleLevel {
    val emitter = EmitterConfig(x = 0.20f, y = 0.22f, angleDeg = 12f, speed = 0.68f)
    val hint = listOf(PlacedTool("h1", ToolType.BUMPER, 0.64f, 0.44f, 0f))
    val base = PuzzleLevel(
      id = "level_3",
      number = 3,
      worldIndex = 0,
      worldName = worlds[0].name,
      title = "Elastic Kick",
      subtitle = "Use an Elastic Bumper to boost momentum across the chamber.",
      gravityMode = GravityMode.LOW_G,
      emitter = emitter,
      goal = GoalConfig(0.22f, 0.92f),
      elements = listOf(
        LevelElement("w1", ElementType.WALL, 0.08f, 0.58f, 0.48f, 0.58f),
      ),
      stars = listOf(Vec2(0.45f, 0.30f), Vec2(0.50f, 0.68f), Vec2(0.32f, 0.84f)),
      toolBudget = mapOf(ToolType.BUMPER to 1, ToolType.DEFLECTOR to 1),
      hintTools = hint,
      parTools = 1,
    )
    return calibrateStarsAndGoal(base, hint)
  }

  private fun buildTutorialLevel4(): PuzzleLevel {
    val emitter = EmitterConfig(x = 0.14f, y = 0.35f, angleDeg = 0f, speed = 0.72f)
    val hint = listOf(PlacedTool("h1", ToolType.GRAVITY_WELL, 0.54f, 0.56f, 0f))
    val base = PuzzleLevel(
      id = "level_4",
      number = 4,
      worldIndex = 0,
      worldName = worlds[0].name,
      title = "Orbital Bend",
      subtitle = "Curve the zero-G orb around the barrier using a Gravity Well.",
      gravityMode = GravityMode.ZERO_G,
      emitter = emitter,
      goal = GoalConfig(0.76f, 0.96f),
      elements = listOf(
        LevelElement("w1", ElementType.WALL, 0.30f, 0.68f, 0.62f, 0.68f),
      ),
      stars = listOf(Vec2(0.35f, 0.36f), Vec2(0.66f, 0.48f), Vec2(0.72f, 0.78f)),
      toolBudget = mapOf(ToolType.GRAVITY_WELL to 1),
      hintTools = hint,
      parTools = 1,
    )
    return calibrateStarsAndGoal(base, hint)
  }

  private fun buildTutorialLevel5(): PuzzleLevel {
    val emitter = EmitterConfig(x = 0.16f, y = 0.25f, angleDeg = 22f, speed = 0.70f)
    val hint = listOf(PlacedTool("h1", ToolType.DEFLECTOR, 0.42f, 0.36f, 65f))
    val base = PuzzleLevel(
      id = "level_5",
      number = 5,
      worldIndex = 0,
      worldName = worlds[0].name,
      title = "Quantum Leap",
      subtitle = "Warp through twin violet portals to bypass the solid wall.",
      gravityMode = GravityMode.ZERO_G,
      emitter = emitter,
      goal = GoalConfig(0.82f, 0.92f),
      elements = listOf(
        LevelElement("w1", ElementType.WALL, 0.52f, 0.08f, 0.52f, 1.12f),
        LevelElement("p1", ElementType.PORTAL_PAIR, 0.28f, 0.86f, 0.72f, 0.26f),
      ),
      stars = listOf(Vec2(0.34f, 0.55f), Vec2(0.76f, 0.54f), Vec2(0.80f, 0.74f)),
      toolBudget = mapOf(ToolType.DEFLECTOR to 2),
      hintTools = hint,
      parTools = 1,
    )
    return calibrateStarsAndGoal(base, hint)
  }

  private fun buildTutorialLevel6(): PuzzleLevel {
    val emitter = EmitterConfig(x = 0.18f, y = 0.96f, angleDeg = -45f, speed = 0.78f)
    val hint = listOf(PlacedTool("h1", ToolType.BOOSTER, 0.44f, 0.58f, -15f))
    val base = PuzzleLevel(
      id = "level_6",
      number = 6,
      worldIndex = 0,
      worldName = worlds[0].name,
      title = "Moving Gate",
      subtitle = "Accelerate with a Plasma Booster to thread the oscillating wall.",
      gravityMode = GravityMode.LOW_G,
      emitter = emitter,
      goal = GoalConfig(0.85f, 0.38f),
      elements = listOf(
        LevelElement("mw1", ElementType.MOVING_WALL, 0.64f, 0.25f, 0.64f, 0.52f, speed = 1.8f, amplitude = 0.16f),
      ),
      stars = listOf(Vec2(0.32f, 0.72f), Vec2(0.56f, 0.50f), Vec2(0.76f, 0.42f)),
      toolBudget = mapOf(ToolType.BOOSTER to 1, ToolType.DEFLECTOR to 1),
      hintTools = hint,
      parTools = 1,
    )
    return calibrateStarsAndGoal(base, hint)
  }

  /** Ensures tutorial levels have reachable stars & goal on the verified hint trajectory */
  private fun calibrateStarsAndGoal(level: PuzzleLevel, hintTools: List<PlacedTool>): PuzzleLevel {
    val sim = PhysicsEngine.simulateHeadless(level, hintTools, maxTime = 8.0f)
    if (sim.outcome == SimulationOutcome.WON) {
      if (sim.starsCollected >= 2) return level
      val wp = sim.path
      if (wp.size >= 12) {
        return level.copy(
          stars = listOf(
            wp[(wp.lastIndex * 0.25f).toInt().coerceIn(2, wp.lastIndex)],
            wp[(wp.lastIndex * 0.50f).toInt().coerceIn(4, wp.lastIndex)],
            wp[(wp.lastIndex * 0.75f).toInt().coerceIn(6, wp.lastIndex)],
          ),
        )
      }
      return level
    }
    // Simulate without premature goal capture to find clean interior points along the real path
    val probe = level.copy(goal = GoalConfig(0.98f, 1.18f, 0.005f))
    val probeSim = PhysicsEngine.simulateHeadless(probe, hintTools, maxTime = 6.5f)
    val path = probeSim.path
    if (path.size < 20) return level
    val emitterPt = Vec2(level.emitter.x, level.emitter.y)
    val candidates = path.indices.filter { idx ->
      idx >= 18 &&
        path[idx].x in 0.10f..0.90f &&
        path[idx].y in 0.12f..1.08f &&
        path[idx].distanceTo(emitterPt) > 0.24f
    }
    val goalIdx = if (candidates.isNotEmpty()) {
      candidates[(candidates.size * 0.75f).toInt().coerceIn(0, candidates.lastIndex)]
    } else {
      (path.size * 0.72f).toInt().coerceIn(0, path.lastIndex)
    }
    val goalPt = path[goalIdx]
    val newGoal = level.goal.copy(x = goalPt.x.coerceIn(0.10f, 0.90f), y = goalPt.y.coerceIn(0.12f, 1.08f))
    val verify = PhysicsEngine.simulateHeadless(level.copy(goal = newGoal), hintTools, maxTime = 8.0f)
    val vp = if (verify.path.size >= 6) verify.path else path
    val maxI = vp.lastIndex.coerceAtLeast(0)
    return level.copy(
      goal = newGoal,
      stars = listOf(
        vp[(maxI * 0.25f).toInt().coerceIn(0, maxI)],
        vp[(maxI * 0.50f).toInt().coerceIn(0, maxI)],
        vp[(maxI * 0.75f).toInt().coerceIn(0, maxI)],
      ),
    )
  }

  private fun buildProceduralVerifiedLevel(levelNumber: Int): PuzzleLevel {
    val world = getWorldForLevel(levelNumber)
    val worldIndex = world.index
    val localIdx = (levelNumber - 1) % LEVELS_PER_WORLD
    val difficulty = localIdx / (LEVELS_PER_WORLD - 1f)
    val rng = Random(levelNumber * 31337L + worldIndex * 99991L)

    val gravityMode = when (worldIndex) {
      0 -> if (localIdx % 4 == 3) GravityMode.LOW_G else GravityMode.EARTH
      1 -> if (localIdx % 3 == 0) GravityMode.LOW_G else GravityMode.EARTH
      2 -> if (localIdx % 2 == 0) GravityMode.ZERO_G else GravityMode.LOW_G
      3 -> if (localIdx % 3 == 0) GravityMode.EARTH else GravityMode.ZERO_G
      4 -> when (localIdx % 4) {
        0 -> GravityMode.INVERTED
        1 -> GravityMode.LOW_G
        else -> GravityMode.EARTH
      }
      else -> GravityMode.entries[localIdx % GravityMode.entries.size]
    }

    val leftToRight = rng.nextBoolean()
    val emitterX = if (leftToRight) rng.nextFloat() * 0.14f + 0.10f else 0.90f - rng.nextFloat() * 0.14f
    val emitterY = when (gravityMode) {
      GravityMode.INVERTED -> 0.85f + rng.nextFloat() * 0.20f
      GravityMode.ZERO_G -> 0.20f + rng.nextFloat() * 0.80f
      else -> 0.16f + rng.nextFloat() * 0.35f
    }
    val baseAngle = if (leftToRight) rng.nextFloat() * 50f - 25f else 180f + (rng.nextFloat() * 50f - 25f)
    val emitter = EmitterConfig(
      x = emitterX,
      y = emitterY,
      angleDeg = baseAngle,
      speed = 0.62f + rng.nextFloat() * 0.28f,
    )

    // Probe initial free-flight path to place the first solution tool right in the orb's path!
    val emptyProbe = PuzzleLevel(
      id = "probe_$levelNumber",
      number = levelNumber,
      worldIndex = worldIndex,
      worldName = world.name,
      title = getLevelTitle(levelNumber),
      subtitle = world.subtitle,
      gravityMode = gravityMode,
      emitter = emitter,
      goal = GoalConfig(0.95f, 1.15f, 0.01f),
      elements = emptyList(),
      stars = emptyList(),
      toolBudget = emptyMap(),
    )

    val toolCount = when {
      worldIndex == 0 && localIdx < 15 -> 1
      worldIndex <= 1 -> if (difficulty > 0.55f) 2 else 1
      worldIndex <= 3 -> 2
      else -> if (difficulty > 0.6f) 3 else 2
    }

    val allowedTools = when (worldIndex) {
      0 -> listOf(ToolType.DEFLECTOR, ToolType.BUMPER)
      1 -> listOf(ToolType.BUMPER, ToolType.DEFLECTOR)
      2 -> listOf(ToolType.GRAVITY_WELL, ToolType.REPULSOR, ToolType.DEFLECTOR)
      3 -> listOf(ToolType.DEFLECTOR, ToolType.GRAVITY_WELL, ToolType.BUMPER)
      4 -> listOf(ToolType.BOOSTER, ToolType.DEFLECTOR, ToolType.REPULSOR)
      else -> ToolType.entries
    }

    // Iteratively place tools along the evolving trajectory!
    val solutionTools = mutableListOf<PlacedTool>()
    val elements = mutableListOf<LevelElement>()

    // Optional Portal Pair in Worlds 3 and 5
    var portalA: Vec2? = null
    if (worldIndex == 3 || (worldIndex == 5 && localIdx % 2 == 0)) {
      val freeSim = PhysicsEngine.simulateHeadless(emptyProbe, emptyList(), maxTime = 2.6f)
      val pIdx = (freeSim.path.size * 0.62f).toInt().coerceIn(15, freeSim.path.lastIndex)
      val pA = freeSim.path[pIdx]
      val destX = if (pA.x < 0.5f) 0.72f + rng.nextFloat() * 0.14f else 0.14f + rng.nextFloat() * 0.14f
      val destY = if (pA.y < 0.6f) 0.78f + rng.nextFloat() * 0.22f else 0.20f + rng.nextFloat() * 0.22f
      portalA = pA
      elements.add(
        LevelElement(
          id = "portal_${levelNumber}",
          type = ElementType.PORTAL_PAIR,
          x1 = pA.x.coerceIn(0.10f, 0.90f),
          y1 = pA.y.coerceIn(0.10f, 1.10f),
          x2 = destX,
          y2 = destY,
        )
      )
    }

    for (tIdx in 0 until toolCount) {
      val sim = PhysicsEngine.simulateHeadless(
        emptyProbe.copy(elements = elements),
        solutionTools,
        maxTime = 5.0f,
      )
      val sampleMin = (sim.path.size * (0.22f + tIdx * 0.20f)).toInt().coerceIn(10, (sim.path.size - 10).coerceAtLeast(10))
      val sampleMax = (sim.path.size * (0.45f + tIdx * 0.22f)).toInt().coerceIn(sampleMin, sim.path.lastIndex)
      val anchorIdx = if (sampleMax > sampleMin) rng.nextInt(sampleMin, sampleMax + 1) else sampleMin.coerceAtMost(sim.path.lastIndex)
      val anchor = sim.path[anchorIdx]
      val prev = sim.path[(anchorIdx - 2).coerceAtLeast(0)]
      val travelAngle = atan2(anchor.y - prev.y, anchor.x - prev.x) * (180f / PI.toFloat())

      val toolType = allowedTools[(levelNumber + tIdx * 3) % allowedTools.size]
      val placed = when (toolType) {
        ToolType.DEFLECTOR -> PlacedTool(
          id = "ht_$tIdx",
          type = ToolType.DEFLECTOR,
          x = anchor.x.coerceIn(0.12f, 0.88f),
          y = anchor.y.coerceIn(0.14f, 1.06f),
          angleDeg = (travelAngle + 45f + rng.nextFloat() * 50f) % 360f,
        )
        ToolType.BUMPER -> PlacedTool(
          id = "ht_$tIdx",
          type = ToolType.BUMPER,
          x = (anchor.x + (rng.nextFloat() - 0.5f) * 0.03f).coerceIn(0.12f, 0.88f),
          y = (anchor.y + 0.02f).coerceIn(0.14f, 1.06f),
          angleDeg = 0f,
        )
        ToolType.GRAVITY_WELL, ToolType.REPULSOR -> {
          val offsetAngle = (travelAngle + 90f) * (PI.toFloat() / 180f)
          val dist = 0.11f
          PlacedTool(
            id = "ht_$tIdx",
            type = toolType,
            x = (anchor.x + cos(offsetAngle) * dist).coerceIn(0.14f, 0.86f),
            y = (anchor.y + sin(offsetAngle) * dist).coerceIn(0.16f, 1.04f),
            angleDeg = 0f,
          )
        }
        ToolType.BOOSTER -> PlacedTool(
          id = "ht_$tIdx",
          type = ToolType.BOOSTER,
          x = anchor.x.coerceIn(0.12f, 0.88f),
          y = anchor.y.coerceIn(0.14f, 1.06f),
          angleDeg = (travelAngle + rng.nextFloat() * 60f - 30f) % 360f,
        )
      }
      if (placed.x in 0.10f..0.90f && placed.y in 0.12f..1.08f &&
        Vec2(placed.x, placed.y).distanceTo(Vec2(emitter.x, emitter.y)) > 0.12f
      ) {
        solutionTools.add(placed)
      }
    }

    // Run final simulation with solutionTools to place Goal & Stars along the verified path
    val solvedSim = PhysicsEngine.simulateHeadless(
      emptyProbe.copy(elements = elements),
      solutionTools,
      maxTime = 5.6f,
    )
    val path = solvedSim.path
    val goalRadius = (0.058f - difficulty * 0.008f).coerceAtLeast(0.048f)
    val interiorIndices = path.indices.filter { idx ->
      idx >= 24 &&
        path[idx].x in 0.12f..0.88f &&
        path[idx].y in 0.14f..1.06f &&
        path[idx].distanceTo(Vec2(emitter.x, emitter.y)) > 0.22f
    }
    val goalIdx = if (interiorIndices.isNotEmpty()) {
      interiorIndices[(interiorIndices.size * 0.78f).toInt().coerceIn(0, interiorIndices.lastIndex)]
    } else {
      val fallbackIdx = path.indices.drop(16).maxByOrNull { idx ->
        path[idx].distanceTo(Vec2(emitter.x, emitter.y))
      } ?: (path.size * 0.75f).toInt().coerceIn(10.coerceAtMost(path.lastIndex), path.lastIndex)
      fallbackIdx
    }
    val goalPos = path[goalIdx]
    var finalGoal = GoalConfig(
      x = goalPos.x.coerceIn(0.08f, 0.92f),
      y = goalPos.y.coerceIn(0.10f, 1.10f),
      radius = goalRadius,
    )

    // Verify winning baseline with exact goal position (retry candidate path indices if needed)
    var winningBaseline = PhysicsEngine.simulateHeadless(
      emptyProbe.copy(goal = finalGoal, elements = elements, stars = emptyList()),
      solutionTools,
      maxTime = 8.0f,
    )
    if (winningBaseline.outcome != SimulationOutcome.WON) {
      val fallbackFractions = listOf(0.65f, 0.50f, 0.38f, 0.25f, 0.88f)
      for (frac in fallbackFractions) {
        val cIdx = (path.size * frac).toInt().coerceIn(8.coerceAtMost(path.lastIndex), path.lastIndex)
        val pt = path[cIdx]
        val candGoal = GoalConfig(pt.x.coerceIn(0.04f, 0.96f), pt.y.coerceIn(0.04f, 1.16f), goalRadius)
        val attemptSim = PhysicsEngine.simulateHeadless(
          emptyProbe.copy(goal = candGoal, elements = elements, stars = emptyList()),
          solutionTools,
          maxTime = 8.0f,
        )
        if (attemptSim.outcome == SimulationOutcome.WON) {
          finalGoal = candGoal
          winningBaseline = attemptSim
          break
        }
      }
    }

    val winPath = winningBaseline.path.ifEmpty { path }
    val wLast = winPath.lastIndex.coerceAtLeast(0)
    val finalStars = listOf(
      winPath[(wLast * 0.24f).toInt().coerceIn(0, wLast)],
      winPath[(wLast * 0.50f).toInt().coerceIn(0, wLast)],
      winPath[(wLast * 0.76f).toInt().coerceIn(0, wLast)],
    )

    // Now place Walls, Hazards, and Moving Walls SAFELY clear of the solution path AND physics-verified!
    val activePath = winPath
    val desiredObstacles = 2 + (worldIndex / 2) + (if (difficulty > 0.5f) 1 else 0)
    var attempts = 0
    var addedObstacles = 0

    // First try placing a barrier in the direct line between emitter and goal so 0-tool shot is blocked
    val midX = (emitter.x + finalGoal.x) * 0.5f
    val midY = (emitter.y + finalGoal.y) * 0.5f
    val directDx = finalGoal.x - emitter.x
    val directDy = finalGoal.y - emitter.y
    val directLen = kotlin.math.hypot(directDx, directDy).coerceAtLeast(0.1f)
    val perpX = -directDy / directLen * 0.13f
    val perpY = directDx / directLen * 0.13f
    val blocker = LevelElement(
      id = "blk_$levelNumber",
      type = if (worldIndex >= 1 && localIdx % 2 == 1) ElementType.HAZARD else ElementType.WALL,
      x1 = (midX - perpX).coerceIn(0.08f, 0.92f),
      y1 = (midY - perpY).coerceIn(0.08f, 1.12f),
      x2 = (midX + perpX).coerceIn(0.08f, 0.92f),
      y2 = (midY + perpY).coerceIn(0.08f, 1.12f),
    )
    if (isSegmentClearOfPath(blocker, activePath, minDist = 0.085f)) {
      val blockerTest = emptyProbe.copy(goal = finalGoal, elements = elements + blocker, stars = finalStars)
      val blockerVerify = PhysicsEngine.simulateHeadless(blockerTest, solutionTools, maxTime = 7.5f)
      if (blockerVerify.outcome == SimulationOutcome.WON && blockerVerify.starsCollected >= 2) {
        elements.add(blocker)
        addedObstacles++
      }
    }

    while (addedObstacles < desiredObstacles && attempts < 24) {
      attempts++
      val cx = 0.16f + rng.nextFloat() * 0.68f
      val cy = 0.16f + rng.nextFloat() * 0.88f
      val halfLen = 0.09f + rng.nextFloat() * 0.08f
      val ang = rng.nextFloat() * PI.toFloat()
      val elemType = when {
        worldIndex >= 4 && addedObstacles == 1 -> ElementType.MOVING_WALL
        worldIndex >= 1 && rng.nextFloat() < (0.28f + difficulty * 0.25f) -> ElementType.HAZARD
        else -> ElementType.WALL
      }
      val cand = LevelElement(
        id = "el_${levelNumber}_$attempts",
        type = elemType,
        x1 = (cx - cos(ang) * halfLen).coerceIn(0.06f, 0.94f),
        y1 = (cy - sin(ang) * halfLen).coerceIn(0.06f, 1.14f),
        x2 = (cx + cos(ang) * halfLen).coerceIn(0.06f, 0.94f),
        y2 = (cy + sin(ang) * halfLen).coerceIn(0.06f, 1.14f),
        speed = 1.4f + rng.nextFloat() * 1.1f,
        amplitude = if (elemType == ElementType.MOVING_WALL) 0.10f else 0f,
      )
      val clearance = if (elemType == ElementType.MOVING_WALL) 0.18f else 0.095f
      if (isSegmentClearOfPath(cand, activePath, clearance) &&
        Vec2(cx, cy).distanceTo(Vec2(emitter.x, emitter.y)) > 0.14f &&
        Vec2(cx, cy).distanceTo(Vec2(finalGoal.x, finalGoal.y)) > 0.14f &&
        (portalA == null || Vec2(cx, cy).distanceTo(portalA) > 0.14f)
      ) {
        val testLevel = emptyProbe.copy(goal = finalGoal, elements = elements + cand, stars = finalStars)
        val verify = PhysicsEngine.simulateHeadless(testLevel, solutionTools, maxTime = 7.5f)
        if (verify.outcome == SimulationOutcome.WON && verify.starsCollected >= 2) {
          elements.add(cand)
          addedObstacles++
        }
      }
    }

    val budget = mutableMapOf<ToolType, Int>()
    for (tool in solutionTools) {
      budget[tool.type] = (budget[tool.type] ?: 0) + 1
    }
    if (budget.isEmpty()) {
      budget[ToolType.DEFLECTOR] = 1
    } else if (worldIndex <= 1 && localIdx % 3 == 0) {
      // Bonus flexible tool on select early levels
      budget[ToolType.DEFLECTOR] = (budget[ToolType.DEFLECTOR] ?: 0) + 1
    }

    val subtitle = buildString {
      append(gravityMode.label)
      if (elements.any { it.type == ElementType.PORTAL_PAIR }) append(" • Quantum Portals")
      if (elements.any { it.type == ElementType.MOVING_WALL }) append(" • Moving Barriers")
      if (elements.any { it.type == ElementType.HAZARD }) append(" • Hazard Field")
    }

    return PuzzleLevel(
      id = "level_$levelNumber",
      number = levelNumber,
      worldIndex = worldIndex,
      worldName = world.name,
      title = getLevelTitle(levelNumber),
      subtitle = subtitle,
      gravityMode = gravityMode,
      emitter = emitter,
      goal = finalGoal,
      elements = elements,
      stars = finalStars,
      toolBudget = budget,
      hintTools = solutionTools,
      parTools = solutionTools.size.coerceAtLeast(1),
    )
  }

  private fun isSegmentClearOfPath(
    elem: LevelElement,
    path: List<Vec2>,
    minDist: Float,
  ): Boolean {
    val a = Vec2(elem.x1, elem.y1)
    val b = Vec2(elem.x2, elem.y2)
    val ab = b - a
    val abLenSq = ab.dot(ab)
    for (i in path.indices step 2) {
      val p = path[i]
      val t = if (abLenSq < 1e-6f) 0f else ((p - a).dot(ab) / abLenSq).coerceIn(0f, 1f)
      val closest = a + ab * t
      if (abs(p.x - closest.x) < minDist && abs(p.y - closest.y) < minDist && p.distanceTo(closest) < minDist) {
        return false
      }
    }
    return true
  }
}
