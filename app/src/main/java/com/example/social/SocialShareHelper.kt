package com.example.social

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object SocialShareHelper {

  fun shareHighScore(
    context: Context,
    playerName: String,
    headline: String,
    score: Int,
    stars: Int,
    levelsCompleted: Int,
    streakDays: Int,
  ) {
    val shareText = buildString {
      append("Kinetic Lab — $headline\n")
      append("Pilot: $playerName\n")
      append("Score: $score pts | Stars: $stars | Levels Solved: $levelsCompleted\n")
      if (streakDays > 0) append("Daily Streak: ${streakDays}d\n")
      append("#KineticLab #PhysicsPuzzle")
    }

    val imageUri = runCatching {
      val bitmap = renderScoreCardBitmap(
        playerName = playerName,
        headline = headline,
        score = score,
        stars = stars,
        levelsCompleted = levelsCompleted,
        streakDays = streakDays,
      )
      val dir = File(context.cacheDir, "shared_scores").apply { mkdirs() }
      val file = File(dir, "kinetic_score_${System.currentTimeMillis()}.png")
      FileOutputStream(file).use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
      }
      bitmap.recycle()
      FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrNull()

    val intent = Intent(Intent.ACTION_SEND).apply {
      if (imageUri != null) {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, imageUri)
        putExtra(Intent.EXTRA_TEXT, shareText)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      } else {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, shareText)
      }
    }
    val chooser = Intent.createChooser(intent, "Share Kinetic Lab Score")
    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
  }

  private fun renderScoreCardBitmap(
    playerName: String,
    headline: String,
    score: Int,
    stars: Int,
    levelsCompleted: Int,
    streakDays: Int,
  ): Bitmap {
    val w = 1080
    val h = 640
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Deep space gradient background
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      shader = LinearGradient(
        0f, 0f, w.toFloat(), h.toFloat(),
        intArrayOf(0xFF0B0E17.toInt(), 0xFF141D33.toInt(), 0xFF0E1526.toInt()),
        null,
        Shader.TileMode.CLAMP,
      )
    }
    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

    // Subtle grid
    val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = 0x1F4F6D9A
      strokeWidth = 2f
    }
    for (x in 0..w step 90) canvas.drawLine(x.toFloat(), 0f, x.toFloat(), h.toFloat(), gridPaint)
    for (y in 0..h step 90) canvas.drawLine(0f, y.toFloat(), w.toFloat(), y.toFloat(), gridPaint)

    // Card border
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = 5f
      color = 0xFF00E5FF.toInt()
    }
    canvas.drawRoundRect(RectF(28f, 28f, w - 28f, h - 28f), 36f, 36f, borderPaint)

    // Decorative trajectory arc + glowing orb
    val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = 6f
      color = 0x6600E5FF
    }
    canvas.drawArc(RectF(620f, 110f, 1020f, 510f), 190f, 140f, false, arcPaint)
    val orbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF00E5FF.toInt() }
    canvas.drawCircle(900f, 180f, 24f, orbPaint)

    // Text
    val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = 0xFF00E5FF.toInt()
      textSize = 36f
      typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    canvas.drawText("KINETIC LAB", 72f, 106f, brandPaint)

    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = 0xFFF2F6FF.toInt()
      textSize = 48f
      typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    canvas.drawText(headline.take(28), 72f, 178f, titlePaint)

    val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = 0xFF9FB0D1.toInt()
      textSize = 32f
      typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }
    canvas.drawText("Pilot: $playerName", 72f, 232f, subPaint)

    val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = 0xFFFFB300.toInt()
      textSize = 94f
      typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    canvas.drawText("$score PTS", 72f, 380f, scorePaint)

    val statPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = 0xFF00E676.toInt()
      textSize = 34f
      typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    canvas.drawText("STARS: $stars   |   LEVELS: $levelsCompleted   |   STREAK: ${streakDays}D", 72f, 540f, statPaint)

    return bitmap
  }
}
