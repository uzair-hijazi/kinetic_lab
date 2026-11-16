package com.example.billing

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.example.data.local.KineticDatabase
import com.example.data.repository.KineticRepository
import com.example.ui.theme.AppThemeCatalog
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PaymentAndThemeTest {

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
  fun defaultBasicThemeIsUnlocked_andRealPurchaseThemesRequireCheckout() = runBlocking {
    val uid = KineticRepository.PREVIEW_USER_ID
    repository.ensureLocalProfile(uid, "Preview Pilot")
    val pref = repository.ensureThemePreference(uid)

    assertEquals(AppThemeCatalog.DEFAULT_THEME_ID, pref.selectedThemeId)
    assertTrue(pref.unlockedThemesCsv.contains(AppThemeCatalog.DEFAULT_THEME_ID))

    // Real purchase theme cannot be bypassed via coin unlock
    val bypassAttempt = repository.selectOrUnlockCoinTheme("royal_aurum", userId = uid)
    assertTrue(bypassAttempt.isFailure)

    // Earning coins allows unlocking a coin-tier theme (Arctic Daylight = 180 coins)
    repository.claimDailyReward(20_001, userId = uid).getOrThrow() // +75 -> 195 coins
    val coinThemeResult = repository.selectOrUnlockCoinTheme("arctic_daylight", userId = uid)
    assertTrue(coinThemeResult.isSuccess)
    assertEquals("arctic_daylight", localDb.kineticDao().getThemePreference(uid)!!.selectedThemeId)
  }

  @Test
  fun billingRegionsAndRealPurchases_supportGooglePayEasypaisaAndJazzCash() = runBlocking {
    val uid = KineticRepository.PREVIEW_USER_ID
    repository.ensureLocalProfile(uid, "Preview Pilot")
    repository.ensureThemePreference(uid)

    // Region options verification
    assertEquals(listOf(PaymentMethod.GOOGLE_PAY), PaymentProcessor.availableMethodsFor(BillingRegion.WORLDWIDE))
    assertEquals(
      listOf(PaymentMethod.GOOGLE_PAY, PaymentMethod.EASYPAISA, PaymentMethod.JAZZCASH),
      PaymentProcessor.availableMethodsFor(BillingRegion.PAKISTAN),
    )

    // Google Pay JSON request sanity check
    val aurum = AppThemeCatalog.getTheme("royal_aurum")
    val gpayJson = JSONObject(PaymentProcessor.buildGooglePayPaymentDataRequestJson(aurum, BillingRegion.WORLDWIDE))
    assertEquals(2, gpayJson.getInt("apiVersion"))
    assertEquals("USD", gpayJson.getJSONObject("transactionInfo").getString("currencyCode"))
    assertEquals("2.99", gpayJson.getJSONObject("transactionInfo").getString("totalPrice"))

    // 1. Worldwide Google Pay purchase -> Royal Aurum
    val gpayReceipt = repository.purchasePremiumTheme(
      themeId = "royal_aurum",
      region = BillingRegion.WORLDWIDE,
      paymentMethod = PaymentMethod.GOOGLE_PAY,
      mobileNumber = "",
      cnicLast6 = "",
      userId = uid,
    ).getOrThrow()
    assertTrue(gpayReceipt.transactionId.startsWith("GP-"))
    assertEquals("USD", gpayReceipt.currency)

    // 2. Pakistan Easypaisa validation + purchase -> Emerald Bio-Core
    val invalidEp = repository.purchasePremiumTheme(
      themeId = "emerald_matrix",
      region = BillingRegion.PAKISTAN,
      paymentMethod = PaymentMethod.EASYPAISA,
      mobileNumber = "12345",
      cnicLast6 = "",
      userId = uid,
    )
    assertFalse(invalidEp.isSuccess)

    val validEp = repository.purchasePremiumTheme(
      themeId = "emerald_matrix",
      region = BillingRegion.PAKISTAN,
      paymentMethod = PaymentMethod.EASYPAISA,
      mobileNumber = "+923451234567",
      cnicLast6 = "",
      userId = uid,
    ).getOrThrow()
    assertTrue(validEp.transactionId.startsWith("EP-"))
    assertEquals("PKR", validEp.currency)

    // 3. Pakistan JazzCash validation + purchase -> Supernova Prism
    val missingCnic = repository.purchasePremiumTheme(
      themeId = "supernova_prism",
      region = BillingRegion.PAKISTAN,
      paymentMethod = PaymentMethod.JAZZCASH,
      mobileNumber = "03001234567",
      cnicLast6 = "123",
      userId = uid,
    )
    assertFalse(missingCnic.isSuccess)

    val validJc = repository.purchasePremiumTheme(
      themeId = "supernova_prism",
      region = BillingRegion.PAKISTAN,
      paymentMethod = PaymentMethod.JAZZCASH,
      mobileNumber = "03001234567",
      cnicLast6 = "654321",
      userId = uid,
    ).getOrThrow()
    assertTrue(validJc.transactionId.startsWith("JC-"))
    assertEquals("PKR", validJc.currency)

    val finalPref = localDb.kineticDao().getThemePreference(uid)!!
    assertEquals("supernova_prism", finalPref.selectedThemeId)
    assertTrue(finalPref.unlockedThemesCsv.contains("royal_aurum"))
    assertTrue(finalPref.unlockedThemesCsv.contains("emerald_matrix"))
    assertTrue(finalPref.unlockedThemesCsv.contains("supernova_prism"))
    assertEquals(3, localDb.kineticDao().getThemePurchases(uid).size)
  }
}
