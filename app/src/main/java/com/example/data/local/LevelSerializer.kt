package com.example.data.local

import com.example.physics.ElementType
import com.example.physics.EmitterConfig
import com.example.physics.GoalConfig
import com.example.physics.GravityMode
import com.example.physics.LevelElement
import com.example.physics.PuzzleLevel
import com.example.physics.ToolType
import com.example.physics.Vec2
import org.json.JSONArray
import org.json.JSONObject

object LevelSerializer {

  fun toJsonString(level: PuzzleLevel): String {
    val root = JSONObject()
    root.put("emitter", JSONObject().apply {
      put("x", level.emitter.x.toDouble())
      put("y", level.emitter.y.toDouble())
      put("angleDeg", level.emitter.angleDeg.toDouble())
      put("speed", level.emitter.speed.toDouble())
    })
    root.put("goal", JSONObject().apply {
      put("x", level.goal.x.toDouble())
      put("y", level.goal.y.toDouble())
      put("radius", level.goal.radius.toDouble())
    })
    val budgetObj = JSONObject()
    level.toolBudget.forEach { (k, v) -> budgetObj.put(k.name, v) }
    root.put("toolBudget", budgetObj)

    val elementsArr = JSONArray()
    level.elements.forEach { el ->
      elementsArr.put(JSONObject().apply {
        put("id", el.id)
        put("type", el.type.name)
        put("x1", el.x1.toDouble())
        put("y1", el.y1.toDouble())
        put("x2", el.x2.toDouble())
        put("y2", el.y2.toDouble())
        put("speed", el.speed.toDouble())
        put("amplitude", el.amplitude.toDouble())
      })
    }
    root.put("elements", elementsArr)

    val starsArr = JSONArray()
    level.stars.forEach { st ->
      starsArr.put(JSONObject().apply {
        put("x", st.x.toDouble())
        put("y", st.y.toDouble())
      })
    }
    root.put("stars", starsArr)
    return root.toString()
  }

  fun fromEntity(entity: LocalCustomLevelEntity): PuzzleLevel {
    val root = JSONObject(entity.levelJson)
    val emitterObj = root.optJSONObject("emitter")
    val emitter = EmitterConfig(
      x = emitterObj?.optDouble("x", 0.2)?.toFloat() ?: 0.2f,
      y = emitterObj?.optDouble("y", 0.25)?.toFloat() ?: 0.25f,
      angleDeg = emitterObj?.optDouble("angleDeg", 20.0)?.toFloat() ?: 20f,
      speed = emitterObj?.optDouble("speed", 0.7)?.toFloat() ?: 0.7f,
    )
    val goalObj = root.optJSONObject("goal")
    val goal = GoalConfig(
      x = goalObj?.optDouble("x", 0.8)?.toFloat() ?: 0.8f,
      y = goalObj?.optDouble("y", 0.95)?.toFloat() ?: 0.95f,
      radius = goalObj?.optDouble("radius", 0.056)?.toFloat() ?: 0.056f,
    )
    val budget = mutableMapOf<ToolType, Int>()
    val budgetObj = root.optJSONObject("toolBudget")
    if (budgetObj != null) {
      ToolType.entries.forEach { type ->
        val count = budgetObj.optInt(type.name, 0)
        if (count > 0) budget[type] = count
      }
    }
    val elements = mutableListOf<LevelElement>()
    val elArr = root.optJSONArray("elements") ?: JSONArray()
    for (i in 0 until elArr.length()) {
      val o = elArr.getJSONObject(i)
      val type = runCatching { ElementType.valueOf(o.optString("type", "WALL")) }
        .getOrDefault(ElementType.WALL)
      elements.add(
        LevelElement(
          id = o.optString("id", "el_$i"),
          type = type,
          x1 = o.optDouble("x1", 0.3).toFloat(),
          y1 = o.optDouble("y1", 0.5).toFloat(),
          x2 = o.optDouble("x2", 0.7).toFloat(),
          y2 = o.optDouble("y2", 0.5).toFloat(),
          speed = o.optDouble("speed", 1.6).toFloat(),
          amplitude = o.optDouble("amplitude", 0.12).toFloat(),
        )
      )
    }
    val stars = mutableListOf<Vec2>()
    val stArr = root.optJSONArray("stars") ?: JSONArray()
    for (i in 0 until stArr.length()) {
      val o = stArr.getJSONObject(i)
      stars.add(Vec2(o.optDouble("x", 0.5).toFloat(), o.optDouble("y", 0.5).toFloat()))
    }
    val gravityMode = runCatching { GravityMode.valueOf(entity.gravityMode) }
      .getOrDefault(GravityMode.EARTH)

    return PuzzleLevel(
      id = entity.id,
      number = 0,
      worldIndex = 0,
      worldName = "Custom Lab",
      title = entity.title,
      subtitle = entity.description.ifBlank { gravityMode.label },
      gravityMode = gravityMode,
      emitter = emitter,
      goal = goal,
      elements = elements,
      stars = stars,
      toolBudget = budget,
      parTools = budget.values.sum().coerceAtLeast(1),
      isCustom = true,
      authorName = entity.authorName,
    )
  }

  fun toFirestoreMaps(level: PuzzleLevel): FirestoreLevelPayload {
    val emitterMap = mapOf(
      "x" to level.emitter.x.toDouble(),
      "y" to level.emitter.y.toDouble(),
      "angleDeg" to level.emitter.angleDeg.toDouble(),
      "speed" to level.emitter.speed.toDouble(),
    )
    val goalMap = mapOf(
      "x" to level.goal.x.toDouble(),
      "y" to level.goal.y.toDouble(),
      "radius" to level.goal.radius.toDouble(),
    )
    val budgetMap = level.toolBudget.entries.associate { it.key.name to it.value.toLong() }
    val elementsList = level.elements.take(40).map { el ->
      mapOf(
        "id" to el.id,
        "type" to el.type.name,
        "x1" to el.x1.toDouble(),
        "y1" to el.y1.toDouble(),
        "x2" to el.x2.toDouble(),
        "y2" to el.y2.toDouble(),
        "speed" to el.speed.toDouble(),
        "amplitude" to el.amplitude.toDouble(),
      )
    }
    val starsList = level.stars.take(3).map { st ->
      mapOf("x" to st.x.toDouble(), "y" to st.y.toDouble())
    }
    return FirestoreLevelPayload(emitterMap, goalMap, budgetMap, elementsList, starsList)
  }

  fun fromFirestoreMaps(
    id: String,
    authorName: String,
    title: String,
    description: String,
    gravityModeStr: String,
    emitter: Map<String, Any?>,
    goal: Map<String, Any?>,
    toolBudget: Map<String, Any?>,
    elements: List<Map<String, Any?>>,
    stars: List<Map<String, Any?>>,
  ): PuzzleLevel {
    val gravityMode = runCatching { GravityMode.valueOf(gravityModeStr) }.getOrDefault(GravityMode.EARTH)
    val parsedEmitter = EmitterConfig(
      x = (emitter["x"] as? Number)?.toFloat() ?: 0.2f,
      y = (emitter["y"] as? Number)?.toFloat() ?: 0.25f,
      angleDeg = (emitter["angleDeg"] as? Number)?.toFloat() ?: 20f,
      speed = (emitter["speed"] as? Number)?.toFloat() ?: 0.7f,
    )
    val parsedGoal = GoalConfig(
      x = (goal["x"] as? Number)?.toFloat() ?: 0.8f,
      y = (goal["y"] as? Number)?.toFloat() ?: 0.95f,
      radius = (goal["radius"] as? Number)?.toFloat() ?: 0.056f,
    )
    val parsedBudget = mutableMapOf<ToolType, Int>()
    toolBudget.forEach { (k, v) ->
      val t = runCatching { ToolType.valueOf(k) }.getOrNull()
      val count = (v as? Number)?.toInt() ?: 0
      if (t != null && count > 0) parsedBudget[t] = count
    }
    val parsedElements = elements.mapIndexed { idx, m ->
      val type = runCatching { ElementType.valueOf(m["type"]?.toString() ?: "WALL") }
        .getOrDefault(ElementType.WALL)
      LevelElement(
        id = m["id"]?.toString() ?: "el_$idx",
        type = type,
        x1 = (m["x1"] as? Number)?.toFloat() ?: 0.3f,
        y1 = (m["y1"] as? Number)?.toFloat() ?: 0.5f,
        x2 = (m["x2"] as? Number)?.toFloat() ?: 0.7f,
        y2 = (m["y2"] as? Number)?.toFloat() ?: 0.5f,
        speed = (m["speed"] as? Number)?.toFloat() ?: 1.6f,
        amplitude = (m["amplitude"] as? Number)?.toFloat() ?: 0.12f,
      )
    }
    val parsedStars = stars.map { m ->
      Vec2(
        x = (m["x"] as? Number)?.toFloat() ?: 0.5f,
        y = (m["y"] as? Number)?.toFloat() ?: 0.5f,
      )
    }
    return PuzzleLevel(
      id = id,
      number = 0,
      worldIndex = 0,
      worldName = "Community Lab",
      title = title,
      subtitle = description.ifBlank { gravityMode.label },
      gravityMode = gravityMode,
      emitter = parsedEmitter,
      goal = parsedGoal,
      elements = parsedElements,
      stars = parsedStars,
      toolBudget = parsedBudget,
      parTools = parsedBudget.values.sum().coerceAtLeast(1),
      isCustom = true,
      authorName = authorName,
    )
  }

  data class FirestoreLevelPayload(
    val emitter: Map<String, Double>,
    val goal: Map<String, Double>,
    val toolBudget: Map<String, Long>,
    val elements: List<Map<String, Any>>,
    val stars: List<Map<String, Double>>,
  )
}
