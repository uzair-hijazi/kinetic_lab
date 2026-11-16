package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.example.data.local.KineticDatabase
import com.example.physics.LevelCatalog
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class KineticRepositoryPreviewTest {

  private lateinit var localDb: KineticDatabase
  private lateinit var repository: KineticRepository

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    if (FirebaseApp.getApps(context).isEmpty()) {
      FirebaseApp.initializeApp(context)
    }
    val firestore = FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id))
    val auth = Firebase.auth
    auth.signOut()

    localDb = Room.inMemoryDatabaseBuilder(context, KineticDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    repository = KineticRepository(firestore, localDb.kineticDao(), auth)
  }

  @After
  fun tearDown() {
    localDb.close()
  }

  @Test
  fun previewMode_allowsGameplayRewardsSkinsAndCustomLevelsWithoutLogin() = runBlocking {
    val uid = KineticRepository.PREVIEW_USER_ID
    val initial = repository.ensureLocalProfile(uid, "Preview Pilot")
    assertEquals("Preview Pilot", initial.displayName)
    assertEquals(120, initial.coins)
    assertEquals(3, initial.hintsRemaining)

    val winRes = repository.saveLevelWin(
      levelNumber = 1,
      worldIndex = 0,
      score = 1500,
      stars = 3,
      toolsUsed = 1,
      userId = uid,
    )
    assertTrue(winRes.isSuccess)
    assertFalse("Preview mode should save locally without cloud sync", winRes.getOrThrow())

    val epochDay = 20_000
    val rewardRes = repository.claimDailyReward(epochDay, userId = uid)
    assertTrue(rewardRes.isSuccess)

    val skinRes = repository.selectOrUnlockSkin("amber", 150, userId = uid)
    assertTrue(skinRes.isSuccess)

    val customId = repository.saveCustomLevel(
      authorName = "Preview Pilot",
      level = LevelCatalog.getLevel(1),
      title = "Local Preview Lab",
      description = "Saved in preview mode",
      published = true,
      verified = true,
      userId = uid,
    ).getOrThrow()

    val savedLevel = localDb.kineticDao().getCustomLevel(customId)
    assertNotNull(savedLevel)
    assertFalse("Preview custom levels stay unpublished locally", savedLevel!!.published)

    val updatedProfile = localDb.kineticDao().getProfile(uid)!!
    assertEquals(1500, updatedProfile.totalScore)
    assertEquals(3, updatedProfile.totalStars)
    assertEquals("amber", updatedProfile.selectedSkin)
  }
}
