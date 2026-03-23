package com.studiomk.mapkit.demo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Base64
import com.studiomk.mapkit.model.MKOverlayStyle
import java.io.ByteArrayOutputStream
import java.util.Locale

internal fun renderFilledCircleBase64Png(fillColorHex: String, sizePx: Int): String {
    val safeSize = sizePx.coerceAtLeast(16)
    val bmp = Bitmap.createBitmap(safeSize, safeSize, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = parseHexColor(fillColorHex, Color.parseColor("#F97316"))
    }
    val cx = safeSize / 2f
    val cy = safeSize / 2f
    val radius = safeSize * 0.42f
    canvas.drawCircle(cx, cy, radius, paint)

    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = (safeSize * 0.06f).coerceAtLeast(1f)
        color = Color.WHITE
    }
    canvas.drawCircle(cx, cy, radius, stroke)

    val out = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
    val bytes = out.toByteArray()
    return Base64.encodeToString(bytes, Base64.NO_WRAP)
}

internal fun renderPointTitleImageBase64Png(title: String, imageBase64Png: String): String {
    val iconSize = 40
    val width = 220
    val height = 56
    val corner = 18f
    val padding = 8f

    val decodedBytes = try {
        Base64.decode(imageBase64Png, Base64.DEFAULT)
    } catch (_: Throwable) {
        null
    }
    val iconBitmap = decodedBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        ?: run {
            val fallbackBytes = Base64.decode(renderFilledCircleBase64Png("#F97316", iconSize), Base64.DEFAULT)
            BitmapFactory.decodeByteArray(fallbackBytes, 0, fallbackBytes.size)
        }

    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#D1D5DB")
    }
    val bubbleRect = RectF(0f, 0f, width.toFloat(), height.toFloat())
    canvas.drawRoundRect(bubbleRect, corner, corner, bgPaint)
    canvas.drawRoundRect(bubbleRect, corner, corner, borderPaint)

    val iconTop = (height - iconSize) / 2f
    val iconRect = RectF(padding, iconTop, padding + iconSize, iconTop + iconSize)
    canvas.drawBitmap(iconBitmap, null, iconRect, null)

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#111827")
        textSize = 22f
    }
    val maxChars = 14
    val label = title.trim().ifBlank { "Pinned" }
        .let { if (it.length > maxChars) it.take(maxChars - 1) + "…" else it }
    val baseline = (height / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
    canvas.drawText(label, padding + iconSize + 10f, baseline, textPaint)

    val out = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
    return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
}

internal fun parseHexColor(hex: String, fallback: Int): Int {
    return try {
        Color.parseColor(hex.trim())
    } catch (_: Throwable) {
        fallback
    }
}

internal fun buildPolylineOverlayStyle(
    colorHex: String,
    widthText: String,
    dashed: Boolean,
    dashLengthText: String,
    gapLengthText: String
): MKOverlayStyle {
    val width = parsePositiveDouble(widthText, fallback = 4.0)
    val dashPattern = if (dashed) {
        listOf(
            parsePositiveDouble(dashLengthText, fallback = 10.0),
            parsePositiveDouble(gapLengthText, fallback = 6.0)
        )
    } else {
        null
    }
    return MKOverlayStyle(
        strokeColorHex = colorHex.ifBlank { "#0ea5e9" },
        strokeWidth = width,
        lineDashPattern = dashPattern
    )
}

internal fun buildPolygonOverlayStyle(
    strokeColorHex: String,
    fillColorHex: String,
    fillAlphaText: String,
    widthText: String,
    dashed: Boolean,
    dashLengthText: String,
    gapLengthText: String
): MKOverlayStyle {
    val width = parsePositiveDouble(widthText, fallback = 3.0)
    val dashPattern = if (dashed) {
        listOf(
            parsePositiveDouble(dashLengthText, fallback = 10.0),
            parsePositiveDouble(gapLengthText, fallback = 6.0)
        )
    } else {
        null
    }
    return MKOverlayStyle(
        strokeColorHex = strokeColorHex.ifBlank { "#22c55e" },
        strokeWidth = width,
        fillColorHex = buildRgbaColor(
            colorHex = fillColorHex.ifBlank { "#22c55e" },
            alphaText = fillAlphaText
        ),
        lineDashPattern = dashPattern
    )
}

internal fun buildCircleOverlayStyle(
    strokeColorHex: String,
    fillColorHex: String,
    fillAlphaText: String,
    widthText: String
): MKOverlayStyle {
    return MKOverlayStyle(
        strokeColorHex = strokeColorHex.ifBlank { "#2563eb" },
        strokeWidth = parsePositiveDouble(widthText, fallback = 3.0),
        fillColorHex = buildRgbaColor(
            colorHex = fillColorHex.ifBlank { "#2563eb" },
            alphaText = fillAlphaText
        )
    )
}

internal fun parsePositiveDouble(text: String, fallback: Double): Double {
    val parsed = text.toDoubleOrNull() ?: return fallback
    return if (parsed > 0.0) parsed else fallback
}

internal fun buildRgbaColor(colorHex: String, alphaText: String): String {
    val rgb = try {
        Color.parseColor(colorHex.trim())
    } catch (_: Throwable) {
        Color.parseColor("#22c55e")
    }
    val r = Color.red(rgb)
    val g = Color.green(rgb)
    val b = Color.blue(rgb)
    val a = (alphaText.toDoubleOrNull() ?: 0.2).coerceIn(0.0, 1.0)
    return "rgba($r, $g, $b, ${String.format(Locale.US, "%.3f", a)})"
}

internal fun Double.format6(): String = String.format("%.6f", this)
