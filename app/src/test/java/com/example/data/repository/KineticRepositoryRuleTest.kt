package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.base.FirestoreEmulatorTestBase
import com.example.data.local.KineticDatabase
import com.example.physics.LevelCatalog
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class KineticRepositoryRuleTest : FirestoreEmulatorTestBase() {

  private lateinit var localDb: KineticDatabase
  private lateinit var repository: KineticRepository

  @Before
  fun setUpLocalDb() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    localDb = Room.inMemoryDatabaseBuilder(context, KineticDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    repository = KineticRepository(firestore, localDb.kineticDao(), auth)
  }

  @After
  fun closeLocalDb() {
    localDb.close()
  }

  @Test
  fun saveLevelWin_authenticatedOwner_syncsProgressProfileAndLeaderboard() = runBlocking {
    val uid = signInTestUser("alice_${UUID.randomUUID()}@kinetic.test")
    repository.ensureLocalProfile(uid, "AliceAce")

    val synced = withTimeout(TIMEOUT_MS) {
      repository.saveLevelWin(
        levelNumber = 1,
        worldIndex = 0,
        score = 1650,
        stars = 3,
        toolsUsed = 1,
      ).getOrThrow()
    }
    assertTrue("Expected saveLevelWin to sync to Firestore", synced)

    val leaderboard = withTimeout(FLOW_TIMEOUT_MS) {
      repository.observeLeaderboard().first { list -> list.any { it.userId == uid } }
    }
    val entry = leaderboard.first { it.userId == uid }
    assertEquals(1650L, entry.totalScore)
    assertEquals(3L, entry.totalStars)
  }

  @Test
  fun saveCustomLevel_published_visibleToCommunityAndIncrementable() = runBlocking {
    val aliceUid = signInTestUser("alice_${UUID.randomUUID()}@kinetic.test")
    val sampleLevel = LevelCatalog.getLevel(1)
    val customId = "custom_${UUID.randomUUID().toString().replace("-", "").take(12)}"

    val savedId = withTimeout(TIMEOUT_MS) {
      repository.saveCustomLevel(
        authorName = "AliceAce",
        level = sampleLevel,
        title = "Quantum Loop",
        description = "Bounce twice",
        published = true,
        verified = true,
        existingId = customId,
      ).getOrThrow()
    }
    assertEquals(customId, savedId)

    // Switch to Bob and verify Bob can read community levels and increment likes
    signInTestUser("bob_${UUID.randomUUID()}@kinetic.test")
    val community = withTimeout(FLOW_TIMEOUT_MS) {
      repository.observeCommunityLevels().first { list -> list.any { it.id == customId } }
    }
    assertTrue(community.any { it.id == customId })

    val likeResult = withTimeout(TIMEOUT_MS) {
      repository.incrementCommunityLike(customId)
    }
    assertTrue(likeResult.isSuccess)
    assertFalse(aliceUid == auth.currentUser?.uid)
  }

  @Test
  fun directCrossUserProfileRead_failsWithPermissionDenied() = runBlocking {
    val aliceUid = signInTestUser("alice_${UUID.randomUUID()}@kinetic.test")
    repository.ensureLocalProfile(aliceUid, "AlicePrivate")
    withTimeout(TIMEOUT_MS) {
      repository.saveLevelWin(1, 0, 1200, 2, 1).getOrThrow()
    }

    // Sign in as Bob and attempt to read Alice's private user document
    signInTestUser("bob_${UUID.randomUUID()}@kinetic.test")
    var caught: FirebaseFirestoreException? = null
    try {
      withTimeout(TIMEOUT_MS) {
        firestore.collection("users").document(aliceUid).get().await()
      }
    } catch (e: Exception) {
      caught = unwrapFirestoreException(e)
    }
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, caught?.code)
  }

  @Test
  fun observeLeaderboard_unauthenticatedCaller_failsWithPermissionDenied() = runBlocking {
    auth.signOut()
    var caught: FirebaseFirestoreException? = null
    try {
      withTimeout(FLOW_TIMEOUT_MS) {
        // Test raw Firestore query unauthenticated
        firestore.collection("leaderboard").whereEqualTo("isPublic", true).get().await()
      }
    } catch (e: Exception) {
      caught = unwrapFirestoreException(e)
    }
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, caught?.code)
  }

  private fun unwrapFirestoreException(t: Throwable): FirebaseFirestoreException? {
    var cur: Throwable? = t
    while (cur != null) {
      if (cur is FirebaseFirestoreException) return cur
      cur = if (cur is CancellationException) cur.cause else cur.cause
    }
    return null
  }

  private companion object {
    const val TIMEOUT_MS = 6000L
    const val FLOW_TIMEOUT_MS = 4000L
  }
}
