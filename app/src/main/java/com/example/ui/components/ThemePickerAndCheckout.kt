package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.billing.BillingRegion
import com.example.billing.PaymentMethod
import com.example.billing.PaymentProcessor
import com.example.billing.PurchaseTransactionResult
import com.example.data.local.LocalThemePreferenceEntity
import com.example.data.local.LocalThemePurchaseEntity
import com.example.ui.theme.AppThemeCatalog
import com.example.ui.theme.AppThemeDefinition
import com.example.ui.theme.LocalKineticColors
import com.example.ui.theme.ThemeUnlockType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ThemeGalleryCard(
  themePref: LocalThemePreferenceEntity?,
  purchases: List<LocalThemePurchaseEntity>,
  playerCoins: Int,
  onSelectOrUnlockCoinTheme: (String) -> Unit,
  onChangeRegion: (BillingRegion) -> Unit,
  onOpenCheckout: (AppThemeDefinition) -> Unit,
  modifier: Modifier = Modifier,
) {
  val kinetic = LocalKineticColors.current
  val activeThemeId = themePref?.selectedThemeId ?: AppThemeCatalog.DEFAULT_THEME_ID
  val activeTheme = AppThemeCatalog.getTheme(activeThemeId)
  val unlockedIds = remember(themePref?.unlockedThemesCsv) {
    val parsed = (themePref?.unlockedThemesCsv ?: AppThemeCatalog.DEFAULT_THEME_ID)
      .split(",")
      .map { it.trim() }
      .filter { it.isNotBlank() }
      .toSet()
    parsed + AppThemeCatalog.DEFAULT_THEME_ID
  }
  val region = remember(themePref?.preferredRegion) {
    BillingRegion.entries.firstOrNull { it.name == themePref?.preferredRegion } ?: BillingRegion.WORLDWIDE
  }
  var showReceiptsDialog by remember { mutableStateOf(false) }

  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    modifier = modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Rounded.Palette, contentDescription = null, tint = kinetic.cyanGlow, modifier = Modifier.size(20.dp))
            Text("GAME THEMES", style = MaterialTheme.typography.titleMedium)
          }
          Text(
            text = "Active: ${activeTheme.name} • Changes full UI & physics arena",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        if (purchases.isNotEmpty()) {
          OutlinedButton(
            onClick = { showReceiptsDialog = true },
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.testTag("theme_receipts_button"),
          ) {
            Icon(Icons.AutoMirrored.Rounded.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Receipts", style = MaterialTheme.typography.labelMedium)
          }
        }
      }

      // Region pricing toggle (Worldwide USD vs Pakistan PKR with Easypaisa + JazzCash + G Pay)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        BillingRegion.entries.forEach { reg ->
          val selected = reg == region
          FilterChip(
            selected = selected,
            onClick = { onChangeRegion(reg) },
            label = {
              Text(
                text = reg.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
              )
            },
            leadingIcon = {
              Icon(
                imageVector = if (reg == BillingRegion.WORLDWIDE) Icons.Rounded.Public else Icons.Rounded.AccountBalanceWallet,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
            },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = kinetic.cyanGlow.copy(alpha = 0.20f),
              selectedLabelColor = kinetic.cyanGlow,
              selectedLeadingIconColor = kinetic.cyanGlow,
            ),
            modifier = Modifier.testTag("theme_region_${reg.name.lowercase()}"),
          )
        }
      }

      // Horizontal Theme Cards
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        AppThemeCatalog.themes.forEach { theme ->
          val isUnlocked = theme.id in unlockedIds || theme.unlockType == ThemeUnlockType.FREE_DEFAULT
          val isSelected = theme.id == activeThemeId
          ThemeOptionCard(
            theme = theme,
            region = region,
            isUnlocked = isUnlocked,
            isSelected = isSelected,
            playerCoins = playerCoins,
            onClick = {
              if (isUnlocked || theme.unlockType != ThemeUnlockType.REAL_PURCHASE) {
                onSelectOrUnlockCoinTheme(theme.id)
              } else {
                onOpenCheckout(theme)
              }
            },
          )
        }
      }
    }
  }

  if (showReceiptsDialog) {
    ThemeReceiptsDialog(
      purchases = purchases,
      onDismiss = { showReceiptsDialog = false },
    )
  }
}

@Composable
private fun ThemeOptionCard(
  theme: AppThemeDefinition,
  region: BillingRegion,
  isUnlocked: Boolean,
  isSelected: Boolean,
  playerCoins: Int,
  onClick: () -> Unit,
) {
  val kinetic = LocalKineticColors.current
  val borderColor = when {
    isSelected -> theme.colorScheme.primary
    isUnlocked -> theme.colorScheme.outline
    theme.unlockType == ThemeUnlockType.REAL_PURCHASE -> kinetic.amberGold.copy(alpha = 0.6f)
    else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
  }

  Surface(
    shape = RoundedCornerShape(16.dp),
    color = theme.colorScheme.surface,
    modifier = Modifier
      .width(168.dp)
      .border(
        width = if (isSelected) 2.dp else 1.dp,
        color = borderColor,
        shape = RoundedCornerShape(16.dp),
      )
      .clip(RoundedCornerShape(16.dp))
      .clickable(onClick = onClick)
      .testTag("theme_card_${theme.id}"),
  ) {
    Column(
      modifier = Modifier.padding(10.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      // Mini Physics Arena + Palette Swatch Preview
      MiniArenaPreview(theme = theme)

      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = theme.name,
            style = MaterialTheme.typography.titleSmall,
            color = theme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
          )
          when {
            isSelected -> Icon(
              Icons.Rounded.CheckCircle,
              contentDescription = "Equipped",
              tint = theme.kineticColors.emeraldSuccess,
              modifier = Modifier.size(16.dp),
            )
            !isUnlocked && theme.unlockType == ThemeUnlockType.REAL_PURCHASE -> Icon(
              Icons.Rounded.Diamond,
              contentDescription = "Premium Real Purchase",
              tint = theme.kineticColors.amberGold,
              modifier = Modifier.size(16.dp),
            )
            !isUnlocked -> Icon(
              Icons.Rounded.Lock,
              contentDescription = "Locked",
              tint = theme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(15.dp),
            )
          }
        }
        Text(
          text = theme.subtitle,
          style = MaterialTheme.typography.labelSmall,
          color = theme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          minLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }

      // Action / Price Badge
      val (badgeBg, badgeTextColor, badgeLabel) = when {
        isSelected -> Triple(
          theme.kineticColors.emeraldSuccess.copy(alpha = 0.22f),
          theme.kineticColors.emeraldSuccess,
          "Equipped",
        )
        isUnlocked -> Triple(
          theme.colorScheme.primary.copy(alpha = 0.18f),
          theme.colorScheme.primary,
          "Apply Theme",
        )
        theme.unlockType == ThemeUnlockType.COINS -> {
          val canAfford = playerCoins >= theme.coinCost
          Triple(
            theme.kineticColors.amberGold.copy(alpha = if (canAfford) 0.22f else 0.12f),
            theme.kineticColors.amberGold,
            "${theme.coinCost} Coins",
          )
        }
        else -> Triple(
          theme.kineticColors.magentaBoost.copy(alpha = 0.22f),
          theme.colorScheme.primary,
          "Buy • ${PaymentProcessor.formattedPrice(theme, region)}",
        )
      }

      Surface(
        shape = RoundedCornerShape(10.dp),
        color = badgeBg,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          text = badgeLabel,
          style = MaterialTheme.typography.labelMedium,
          color = badgeTextColor,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Composable
private fun MiniArenaPreview(theme: AppThemeDefinition) {
  val k = theme.kineticColors
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(68.dp)
      .clip(RoundedCornerShape(10.dp))
      .background(k.arenaBackground)
      .border(1.dp, theme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
  ) {
    Canvas(modifier = Modifier.matchParentSize()) {
      val stepX = size.width / 5f
      val stepY = size.height / 3f
      for (i in 1..4) {
        drawLine(k.arenaGrid.copy(alpha = 0.5f), Offset(stepX * i, 0f), Offset(stepX * i, size.height), 1f)
      }
      for (j in 1..2) {
        drawLine(k.arenaGrid.copy(alpha = 0.5f), Offset(0f, stepY * j), Offset(size.width, stepY * j), 1f)
      }
      // Emitter orb + beam
      drawLine(
        color = k.cyanGlow.copy(alpha = 0.7f),
        start = Offset(size.width * 0.18f, size.height * 0.28f),
        end = Offset(size.width * 0.52f, size.height * 0.66f),
        strokeWidth = 3f,
        cap = StrokeCap.Round,
      )
      drawLine(
        color = k.amberGold,
        start = Offset(size.width * 0.42f, size.height * 0.74f),
        end = Offset(size.width * 0.62f, size.height * 0.58f),
        strokeWidth = 4.5f,
        cap = StrokeCap.Round,
      )
      drawCircle(color = k.cyanGlow, radius = 7.dp.toPx(), center = Offset(size.width * 0.20f, size.height * 0.30f))
      drawCircle(color = k.crimsonHazard, radius = 4.dp.toPx(), center = Offset(size.width * 0.50f, size.height * 0.28f))
      drawCircle(
        color = k.emeraldSuccess,
        radius = 8.dp.toPx(),
        center = Offset(size.width * 0.82f, size.height * 0.62f),
        style = Stroke(width = 2.5.dp.toPx()),
      )
    }

    // Mini palette dots top-right
    Row(
      modifier = Modifier
        .align(Alignment.TopEnd)
        .padding(6.dp),
      horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
      listOf(
        theme.colorScheme.primary,
        theme.colorScheme.secondary,
        theme.colorScheme.tertiary,
      ).forEach { dot ->
        Box(
          modifier = Modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(dot),
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeStoreModalSheet(
  themePref: LocalThemePreferenceEntity?,
  purchases: List<LocalThemePurchaseEntity>,
  playerCoins: Int,
  onSelectOrUnlockCoinTheme: (String) -> Unit,
  onChangeRegion: (BillingRegion) -> Unit,
  onOpenCheckout: (AppThemeDefinition) -> Unit,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)
        .padding(bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      ThemeGalleryCard(
        themePref = themePref,
        purchases = purchases,
        playerCoins = playerCoins,
        onSelectOrUnlockCoinTheme = onSelectOrUnlockCoinTheme,
        onChangeRegion = onChangeRegion,
        onOpenCheckout = onOpenCheckout,
      )
    }
  }
}

@Composable
fun ThemeCheckoutDialog(
  theme: AppThemeDefinition,
  initialRegion: BillingRegion,
  onChangeRegion: (BillingRegion) -> Unit,
  onConfirmPurchase: suspend (
    region: BillingRegion,
    method: PaymentMethod,
    mobileNumber: String,
    cnicLast6: String,
  ) -> Result<PurchaseTransactionResult>,
  onDismiss: () -> Unit,
) {
  val kinetic = LocalKineticColors.current
  val scope = rememberCoroutineScope()

  var region by remember(theme.id) { mutableStateOf(initialRegion) }
  var selectedMethod by remember(theme.id) { mutableStateOf(PaymentMethod.GOOGLE_PAY) }
  var mobileNumber by remember { mutableStateOf("") }
  var cnicLast6 by remember { mutableStateOf("") }
  var errorText by remember { mutableStateOf<String?>(null) }
  var isProcessing by remember { mutableStateOf(false) }
  var completedReceipt by remember { mutableStateOf<PurchaseTransactionResult?>(null) }

  val availableMethods = remember(region) { PaymentProcessor.availableMethodsFor(region) }
  val activePrice = PaymentProcessor.formattedPrice(theme, region)

  AlertDialog(
    onDismissRequest = { if (!isProcessing) onDismiss() },
    title = {
      val receipt = completedReceipt
      if (receipt != null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Icon(Icons.Rounded.Verified, contentDescription = null, tint = kinetic.emeraldSuccess)
          Text("Theme Unlocked!", color = kinetic.emeraldSuccess)
        }
      } else {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text("Unlock ${theme.name}", style = MaterialTheme.typography.titleLarge)
          Text(
            text = "Premium Theme • Real Purchase Only",
            style = MaterialTheme.typography.labelMedium,
            color = kinetic.amberGold,
          )
        }
      }
    },
    text = {
      val receipt = completedReceipt
      if (receipt != null) {
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          MiniArenaPreview(theme = theme)
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = kinetic.emeraldSuccess.copy(alpha = 0.12f),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier.padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Text("Receipt ID: ${receipt.transactionId}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
              Text("Method: ${receipt.paymentMethod.displayName}", style = MaterialTheme.typography.bodyMedium)
              Text("Account: ${receipt.maskedAccount}", style = MaterialTheme.typography.bodyMedium)
              Text("Total: ${receipt.amountDisplay} (${receipt.currency})", style = MaterialTheme.typography.bodyMedium, color = kinetic.amberGold)
            }
          }
          Text(
            text = "${theme.name} is now active across your campaign, physics arena, editor, and profile!",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      } else {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          MiniArenaPreview(theme = theme)

          // Region Toggle (Worldwide USD vs Pakistan PKR)
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Billing Region & Currency", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              BillingRegion.entries.forEach { reg ->
                val active = reg == region
                FilterChip(
                  selected = active,
                  onClick = {
                    region = reg
                    onChangeRegion(reg)
                    if (reg == BillingRegion.WORLDWIDE && !selectedMethod.supportsWorldwide) {
                      selectedMethod = PaymentMethod.GOOGLE_PAY
                    }
                    errorText = null
                  },
                  label = { Text(reg.label, style = MaterialTheme.typography.labelSmall) },
                  modifier = Modifier
                    .weight(1f)
                    .testTag("checkout_region_${reg.name.lowercase()}"),
                )
              }
            }
          }

          // Payment Method Options
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
              text = if (region == BillingRegion.PAKISTAN) {
                "Select Payment Method (Pakistan: G Pay, Easypaisa & JazzCash)"
              } else {
                "Select Payment Method (Worldwide)"
              },
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            availableMethods.forEach { method ->
              val isSelected = method == selectedMethod
              PaymentMethodOptionRow(
                method = method,
                selected = isSelected,
                priceText = activePrice,
                onClick = {
                  selectedMethod = method
                  errorText = null
                },
              )
            }

            if (region == BillingRegion.WORLDWIDE) {
              TextButton(
                onClick = {
                  region = BillingRegion.PAKISTAN
                  onChangeRegion(BillingRegion.PAKISTAN)
                  selectedMethod = PaymentMethod.EASYPAISA
                  errorText = null
                },
                modifier = Modifier.testTag("switch_to_pakistan_payments_button"),
              ) {
                Text("In Pakistan? Pay with Easypaisa, JazzCash or G Pay (PKR)")
              }
            }
          }

          // Method-specific details
          when (selectedMethod) {
            PaymentMethod.GOOGLE_PAY -> {
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                  Text(
                    text = "Google Pay (G Pay) Instant Checkout",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                  )
                  Text(
                    text = "Total: $activePrice (${region.currencyCode}) • Visa, Mastercard & linked G Pay wallet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
            PaymentMethod.EASYPAISA -> {
              OutlinedTextField(
                value = mobileNumber,
                onValueChange = { input ->
                  mobileNumber = input.filter { it.isDigit() || it == '+' }.take(13)
                  errorText = null
                },
                label = { Text("Easypaisa Mobile Number") },
                placeholder = { Text("03451234567") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("easypaisa_mobile_input"),
              )
            }
            PaymentMethod.JAZZCASH -> {
              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                  value = mobileNumber,
                  onValueChange = { input ->
                    mobileNumber = input.filter { it.isDigit() || it == '+' }.take(13)
                    errorText = null
                  },
                  label = { Text("JazzCash Mobile Number") },
                  placeholder = { Text("03001234567") },
                  singleLine = true,
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("jazzcash_mobile_input"),
                )
                OutlinedTextField(
                  value = cnicLast6,
                  onValueChange = { input ->
                    cnicLast6 = input.filter { it.isDigit() }.take(6)
                    errorText = null
                  },
                  label = { Text("Last 6 Digits of CNIC") },
                  placeholder = { Text("123456") },
                  singleLine = true,
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("jazzcash_cnic_input"),
                )
              }
            }
          }

          if (errorText != null) {
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = MaterialTheme.colorScheme.errorContainer,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                text = errorText!!,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(10.dp),
              )
            }
          }

          Text(
            text = "Preview Gateway Mode — Merchant API keys are not configured in .env; confirms a test receipt locally with no real charge.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    },
    confirmButton = {
      if (completedReceipt != null) {
        Button(
          onClick = onDismiss,
          colors = ButtonDefaults.buttonColors(containerColor = kinetic.emeraldSuccess),
          modifier = Modifier.testTag("checkout_done_button"),
        ) {
          Text("Done", fontWeight = FontWeight.Bold)
        }
      } else {
        val payButtonBg = when (selectedMethod) {
          PaymentMethod.GOOGLE_PAY -> Color(0xFF1A1C1E)
          PaymentMethod.EASYPAISA -> Color(0xFF00A859)
          PaymentMethod.JAZZCASH -> Color(0xFFD71920)
        }
        val payButtonFg = Color.White
        val payLabel = when (selectedMethod) {
          PaymentMethod.GOOGLE_PAY -> "Pay $activePrice with G Pay"
          PaymentMethod.EASYPAISA -> "Pay $activePrice • Easypaisa"
          PaymentMethod.JAZZCASH -> "Pay $activePrice • JazzCash"
        }

        Button(
          onClick = {
            val validation = PaymentProcessor.validateInputs(
              theme = theme,
              region = region,
              method = selectedMethod,
              mobileNumber = mobileNumber,
              cnicLast6 = cnicLast6,
            )
            if (!validation.isValid) {
              errorText = validation.errorMessage
              return@Button
            }
            errorText = null
            isProcessing = true
            scope.launch {
              delay(450)
              val res = onConfirmPurchase(region, selectedMethod, mobileNumber, cnicLast6)
              isProcessing = false
              if (res.isSuccess) {
                completedReceipt = res.getOrThrow()
              } else {
                errorText = res.exceptionOrNull()?.message ?: "Checkout failed."
              }
            }
          },
          enabled = !isProcessing,
          colors = ButtonDefaults.buttonColors(
            containerColor = payButtonBg,
            contentColor = payButtonFg,
          ),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.testTag("confirm_theme_purchase_button"),
        ) {
          if (isProcessing) {
            CircularProgressIndicator(
              modifier = Modifier.size(18.dp),
              color = payButtonFg,
              strokeWidth = 2.dp,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Authorizing...")
          } else {
            Text(payLabel, fontWeight = FontWeight.Bold)
          }
        }
      }
    },
    dismissButton = {
      if (completedReceipt == null && !isProcessing) {
        TextButton(onClick = onDismiss) {
          Text("Cancel")
        }
      }
    },
  )
}

@Composable
private fun PaymentMethodOptionRow(
  method: PaymentMethod,
  selected: Boolean,
  priceText: String,
  onClick: () -> Unit,
) {
  val kinetic = LocalKineticColors.current
  val (badgeBg, badgeCode) = when (method) {
    PaymentMethod.GOOGLE_PAY -> Color(0xFF1F2937) to "G Pay"
    PaymentMethod.EASYPAISA -> Color(0xFF00A859) to "EP"
    PaymentMethod.JAZZCASH -> Color(0xFFD71920) to "JC"
  }

  Surface(
    shape = RoundedCornerShape(14.dp),
    color = if (selected) kinetic.cyanGlow.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
    modifier = Modifier
      .fillMaxWidth()
      .border(
        width = if (selected) 1.5.dp else 1.dp,
        color = if (selected) kinetic.cyanGlow else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
        shape = RoundedCornerShape(14.dp),
      )
      .clip(RoundedCornerShape(14.dp))
      .clickable(onClick = onClick)
      .testTag("payment_method_${method.id.lowercase()}"),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Surface(
        shape = RoundedCornerShape(8.dp),
        color = badgeBg,
      ) {
        Text(
          text = badgeCode,
          color = Color.White,
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
        )
      }
      Column(modifier = Modifier.weight(1f)) {
        Text(method.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(
          text = method.subtitle,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Text(
        text = priceText,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) kinetic.cyanGlow else kinetic.amberGold,
        fontWeight = FontWeight.Bold,
      )
    }
  }
}

@Composable
private fun ThemeReceiptsDialog(
  purchases: List<LocalThemePurchaseEntity>,
  onDismiss: () -> Unit,
) {
  val kinetic = LocalKineticColors.current
  val formatter = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Theme Purchase Receipts") },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        purchases.forEach { p ->
          val themeName = AppThemeCatalog.getTheme(p.themeId).name
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text(themeName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(p.amountDisplay, style = MaterialTheme.typography.labelLarge, color = kinetic.amberGold)
              }
              Text("${p.maskedAccount} • ${p.currency}", style = MaterialTheme.typography.bodySmall)
              Text(
                text = "${p.transactionId} • ${formatter.format(Date(p.purchasedAtMillis))}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    },
    confirmButton = {
      Button(onClick = onDismiss) {
        Text("Close")
      }
    },
  )
}
