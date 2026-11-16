package com.example.physics

import kotlin.math.hypot

const val ARENA_WIDTH = 1.0f
const val ARENA_HEIGHT = 1.2f
const val ORB_RADIUS = 0.024f

data class Vec2(val x: Float, val y: Float) {
  operator fun plus(o: Vec2) = Vec2(x + o.x, y + o.y)
  operator fun minus(o: Vec2) = Vec2(x - o.x, y - o.y)
  operator fun times(s: Float) = Vec2(x * s, y * s)
  operator fun div(s: Float) = Vec2(x / s, y / s)
  fun dot(o: Vec2): Float = x * o.x + y * o.y
  fun length(): Float = hypot(x, y)
  fun normalized(): Vec2 {
    val len = length()
    return if (len > 1e-6f) Vec2(x / len, y / len) else Vec2(0f, 0f)
  }
  fun distanceTo(o: Vec2): Float = hypot(x - o.x, y - o.y)
}

enum class GravityMode(val label: String, val accelY: Float) {
  EARTH("Earth 1G", 0.92f),
  LOW_G("Lunar 0.4G", 0.40f),
  ZERO_G("Zero-G", 0.0f),
  INVERTED("Inverted -G", -0.85f),
}

enum class ToolType(val displayName: String, val shortTag: String, val rotatable: Boolean) {
  DEFLECTOR("Deflector", "Mirror", true),
  BUMPER("Elastic Bumper", "Bumper", false),
  GRAVITY_WELL("Gravity Well", "Pull", false),
  REPULSOR("Repulsor Field", "Push", false),
  BOOSTER("Plasma Boost", "Boost", true),
}

enum class ElementType {
  WALL,
  HAZARD,
  MOVING_WALL,
  PORTAL_PAIR,
}

data class EmitterConfig(
  val x: Float = 0.2f,
  val y: Float = 0.2f,
  val angleDeg: Float = 35f,
  val speed: Float = 0.65f,
)

data class GoalConfig(
  val x: Float = 0.8f,
  val y: Float = 0.95f,
  val radius: Float = 0.056f,
)

data class LevelElement(
  val id: String,
  val type: ElementType,
  val x1: Float,
  val y1: Float,
  val x2: Float,
  val y2: Float,
  val speed: Float = 1.6f,
  val amplitude: Float = 0.14f,
)

data class PlacedTool(
  val id: String,
  val type: ToolType,
  val x: Float,
  val y: Float,
  val angleDeg: Float = 0f,
)

data class PuzzleLevel(
  val id: String,
  val number: Int,
  val worldIndex: Int,
  val worldName: String,
  val title: String,
  val subtitle: String,
  val gravityMode: GravityMode,
  val emitter: EmitterConfig,
  val goal: GoalConfig,
  val elements: List<LevelElement>,
  val stars: List<Vec2>,
  val toolBudget: Map<ToolType, Int>,
  val hintTools: List<PlacedTool> = emptyList(),
  val parTools: Int = 1,
  val isCustom: Boolean = false,
  val authorName: String = "Kinetic Lab",
)

enum class SimulationOutcome {
  IDLE,
  RUNNING,
  WON,
  HAZARD_HIT,
  TIMED_OUT,
}

data class SparkParticle(
  val x: Float,
  val y: Float,
  val vx: Float,
  val vy: Float,
  val life: Float,
  val maxLife: Float,
  val isHazard: Boolean = false,
  val isStar: Boolean = false,
)

data class PhysicsState(
  val orbPos: Vec2,
  val orbVel: Vec2,
  val trail: List<Vec2> = emptyList(),
  val collectedStarIndices: Set<Int> = emptySet(),
  val outcome: SimulationOutcome = SimulationOutcome.IDLE,
  val simTime: Float = 0f,
  val bounces: Int = 0,
  val portalCooldown: Float = 0f,
  val particles: List<SparkParticle> = emptyList(),
)
