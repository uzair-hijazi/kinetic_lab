package com.example.physics

import org.junit.Assert.assertTrue
import org.junit.Test

class LevelCatalogPhysicsTest {

  @Test
  fun all240Levels_generateAndAreSolvableWithHintTools() {
    val failures = mutableListOf<String>()
    for (levelNumber in 1..LevelCatalog.TOTAL_LEVELS) {
      val level = LevelCatalog.getLevel(levelNumber)
      if (level.number != levelNumber) {
        failures.add("Lv $levelNumber: number mismatch ${level.number}")
        continue
      }
      if (level.toolBudget.isEmpty()) {
        failures.add("Lv $levelNumber: empty toolBudget")
        continue
      }
      if (level.stars.size != 3) {
        failures.add("Lv $levelNumber: stars size ${level.stars.size}")
        continue
      }

      val result = PhysicsEngine.simulateHeadless(level, level.hintTools, maxTime = 9.0f)
      if (result.outcome != SimulationOutcome.WON) {
        failures.add("Lv $levelNumber (${level.title}): outcome=${result.outcome}")
      } else if (result.starsCollected < 2) {
        failures.add("Lv $levelNumber (${level.title}): stars=${result.starsCollected}")
      }
    }
    assertTrue("Failed levels (${failures.size}):\n${failures.take(30).joinToString("\n")}", failures.isEmpty())
  }
}
