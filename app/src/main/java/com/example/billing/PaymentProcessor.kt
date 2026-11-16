package com.example.billing

import com.example.ui.theme.AppThemeDefinition
import com.example.ui.theme.ThemeUnlockType
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

enum class BillingRegion(
  val label: String,
  val shortLabel: String,
  val currencyCode: String,
) {
  WORLDWIDE("Worldwide (USD)", "Worldwide", "USD"),
  PAKISTAN("Pakistan (PKR)", "Pakistan", "PKR"),
}

enum class PaymentMethod(
  val id: String,
  val displayName: String,
  val subtitle: String,
  val supportsWorldwide: Boolean,
  val supportsPakistan: Boolean,
) {
  GOOGLE_PAY(
    id = "GOOGLE_PAY",
    displayName = "Google Pay (G Pay)",
    subtitle = "Cards & linked wallets • Worldwide & Pakistan",
    supportsWorldwide = true,
    supportsPakistan = true,
  ),
  EASYPAISA(
    id = "EASYPAISA",
    displayName = "Easypaisa",
    subtitle = "Pakistani mobile account (03XX-XXXXXXX)",
    supportsWorldwide = false,
    supportsPakistan = true,
  ),
  JAZZCASH(
    id = "JAZZCASH",
    displayName = "JazzCash",
    subtitle = "Pakistani mobile wallet + 6-digit CNIC",
    supportsWorldwide = false,
    supportsPakistan = true,
  ),
}

data class CheckoutValidationResult(
  val isValid: Boolean,
  val errorMessage: String? = null,
  val normalizedMobile: String = "",
)

data class PurchaseTransactionResult(
  val transactionId: String,
  val themeId: String,
  val paymentMethod: PaymentMethod,
  val region: BillingRegion,
  val currency: String,
  val amountDisplay: String,
  val maskedAccount: String,
  val gatewayPayloadJson: String,
  val purchasedAtMillis: Long,
)

object PaymentProcessor {

  fun availableMethodsFor(region: BillingRegion): List<PaymentMethod> {
    return when (region) {
      BillingRegion.WORLDWIDE -> listOf(PaymentMethod.GOOGLE_PAY)
      BillingRegion.PAKISTAN -> listOf(
        PaymentMethod.GOOGLE_PAY,
        PaymentMethod.EASYPAISA,
        PaymentMethod.JAZZCASH,
      )
    }
  }

  fun formattedPrice(theme: AppThemeDefinition, region: BillingRegion): String {
    return when (region) {
      BillingRegion.WORLDWIDE -> theme.formattedUsd
      BillingRegion.PAKISTAN -> theme.formattedPkr
    }
  }

  fun normalizePakistanMobile(raw: String): String {
    val digits = raw.filter { it.isDigit() }
    return when {
      digits.startsWith("923") && digits.length == 12 -> "0${digits.substring(2)}"
      digits.startsWith("03") && digits.length == 11 -> digits
      else -> digits
    }
  }

  fun validateInputs(
    theme: AppThemeDefinition,
    region: BillingRegion,
    method: PaymentMethod,
    mobileNumber: String,
    cnicLast6: String,
  ): CheckoutValidationResult {
    if (theme.unlockType != ThemeUnlockType.REAL_PURCHASE) {
      return CheckoutValidationResult(false, "Selected theme does not require a real purchase.")
    }
    if (region == BillingRegion.WORLDWIDE && !method.supportsWorldwide) {
      return CheckoutValidationResult(false, "${method.displayName} is available for Pakistan (PKR) checkout.")
    }

    return when (method) {
      PaymentMethod.GOOGLE_PAY -> CheckoutValidationResult(isValid = true)
      PaymentMethod.EASYPAISA -> {
        val normalized = normalizePakistanMobile(mobileNumber)
        if (!Regex("^03[0-9]{9}$").matches(normalized)) {
          CheckoutValidationResult(
            isValid = false,
            errorMessage = "Enter a valid 11-digit Easypaisa mobile number (e.g. 03451234567).",
          )
        } else {
          CheckoutValidationResult(isValid = true, normalizedMobile = normalized)
        }
      }
      PaymentMethod.JAZZCASH -> {
        val normalized = normalizePakistanMobile(mobileNumber)
        val cleanCnic = cnicLast6.filter { it.isDigit() }
        when {
          !Regex("^03[0-9]{9}$").matches(normalized) -> CheckoutValidationResult(
            isValid = false,
            errorMessage = "Enter a valid 11-digit JazzCash mobile number (e.g. 03001234567).",
          )
          !Regex("^[0-9]{6}$").matches(cleanCnic) -> CheckoutValidationResult(
            isValid = false,
            errorMessage = "Enter the last 6 digits of your CNIC for JazzCash MPIN authorization.",
          )
          else -> CheckoutValidationResult(isValid = true, normalizedMobile = normalized)
        }
      }
    }
  }

  fun buildGooglePayPaymentDataRequestJson(
    theme: AppThemeDefinition,
    region: BillingRegion,
  ): String {
    val totalPrice = when (region) {
      BillingRegion.WORLDWIDE -> "${theme.priceUsdCents / 100}.${(theme.priceUsdCents % 100).toString().padStart(2, '0')}"
      BillingRegion.PAKISTAN -> "${theme.pricePkr}.00"
    }
    val cardParams = JSONObject().apply {
      put("allowedAuthMethods", JSONArray().put("PAN_ONLY").put("CRYPTOGRAM_3DS"))
      put("allowedCardNetworks", JSONArray().put("VISA").put("MASTERCARD"))
    }
    val paymentMethodObj = JSONObject().apply {
      put("type", "CARD")
      put("parameters", cardParams)
      put(
        "tokenizationSpecification",
        JSONObject().apply {
          put("type", "PAYMENT_GATEWAY")
          put(
            "parameters",
            JSONObject().apply {
              put("gateway", "kineticlab_preview")
              put("gatewayMerchantId", "kinetic_lab_${theme.id}")
            },
          )
        },
      )
    }
    val transactionInfo = JSONObject().apply {
      put("totalPriceStatus", "FINAL")
      put("totalPrice", totalPrice)
      put("currencyCode", region.currencyCode)
      if (region == BillingRegion.PAKISTAN) {
        put("countryCode", "PK")
      } else {
        put("countryCode", "US")
      }
    }
    return JSONObject().apply {
      put("apiVersion", 2)
      put("apiVersionMinor", 0)
      put("allowedPaymentMethods", JSONArray().put(paymentMethodObj))
      put("transactionInfo", transactionInfo)
      put("merchantInfo", JSONObject().put("merchantName", "Kinetic Lab Studio"))
    }.toString()
  }

  fun processCheckout(
    theme: AppThemeDefinition,
    region: BillingRegion,
    method: PaymentMethod,
    mobileNumber: String,
    cnicLast6: String,
    accountLabel: String = "Google Pay Wallet",
  ): Result<PurchaseTransactionResult> {
    val validation = validateInputs(theme, region, method, mobileNumber, cnicLast6)
    if (!validation.isValid) {
      return Result.failure(IllegalArgumentException(validation.errorMessage ?: "Invalid payment details."))
    }

    val prefix = when (method) {
      PaymentMethod.GOOGLE_PAY -> "GP"
      PaymentMethod.EASYPAISA -> "EP"
      PaymentMethod.JAZZCASH -> "JC"
    }
    val txnId = "$prefix-${UUID.randomUUID().toString().replace("-", "").take(12).uppercase()}"
    val masked = when (method) {
      PaymentMethod.GOOGLE_PAY -> accountLabel.ifBlank { "Google Pay Wallet" }
      PaymentMethod.EASYPAISA -> {
        val m = validation.normalizedMobile
        "Easypaisa ${m.take(4)}••••${m.takeLast(3)}"
      }
      PaymentMethod.JAZZCASH -> {
        val m = validation.normalizedMobile
        "JazzCash ${m.take(4)}••••${m.takeLast(3)}"
      }
    }

    val payloadJson = when (method) {
      PaymentMethod.GOOGLE_PAY -> buildGooglePayPaymentDataRequestJson(theme, region)
      PaymentMethod.EASYPAISA -> JSONObject().apply {
        put("provider", "EASYPAISA_MWALLET")
        put("orderRef", txnId)
        put("amountPkr", theme.pricePkr)
        put("currency", "PKR")
        put("msisdnMasked", masked)
      }.toString()
      PaymentMethod.JAZZCASH -> JSONObject().apply {
        put("provider", "JAZZCASH_MWALLET")
        put("pp_TxnRefNo", txnId)
        put("pp_Amount", theme.pricePkr * 100)
        put("pp_TxnCurrency", "PKR")
        put("msisdnMasked", masked)
      }.toString()
    }

    return Result.success(
      PurchaseTransactionResult(
        transactionId = txnId,
        themeId = theme.id,
        paymentMethod = method,
        region = region,
        currency = region.currencyCode,
        amountDisplay = formattedPrice(theme, region),
        maskedAccount = masked,
        gatewayPayloadJson = payloadJson,
        purchasedAtMillis = System.currentTimeMillis(),
      )
    )
  }
}
