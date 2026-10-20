package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import com.example.model.AdaptiveScreenProfile
import com.example.model.NormalizedScanZone

object AdaptiveUiEngine {

    /**
     * Resolves the current screen dimensions, aspect ratio, and foldable/tablet state
     * across Android 8 through Android 16 without deprecated or hardcoded assumptions.
     */
    fun resolveScreenProfile(context: Context): AdaptiveScreenProfile {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val widthPx: Int
        val heightPx: Int
        val densityDpi: Int

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val currentBounds = windowManager.currentWindowMetrics.bounds
            widthPx = currentBounds.width().coerceAtLeast(1080)
            heightPx = currentBounds.height().coerceAtLeast(1920)
            densityDpi = context.resources.configuration.densityDpi
        } else {
            val displayMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(displayMetrics)
            widthPx = displayMetrics.widthPixels.coerceAtLeast(1080)
            heightPx = displayMetrics.heightPixels.coerceAtLeast(1920)
            densityDpi = displayMetrics.densityDpi
        }

        val aspectRatio = heightPx.toFloat() / widthPx.toFloat()
        val widthDp = (widthPx * 160) / densityDpi
        val isTabletOrFoldable = widthDp >= 600 || aspectRatio < 1.35f

        return AdaptiveScreenProfile(
            screenWidthPx = widthPx,
            screenHeightPx = heightPx,
            densityDpi = densityDpi,
            aspectRatio = aspectRatio,
            isTabletOrFoldable = isTabletOrFoldable
        )
    }

    /**
     * Dynamically calculates normalized OCR scanning zone [left, top, right, bottom]
     * tailored to specific ride-hailing apps (Uber, InDrive, DiDi, Careem, Bolt).
     * Returns relative fractions (0.0f to 1.0f) so it automatically scales across any resolution.
     */
    fun getNormalizedScanZone(packageName: String?, profile: AdaptiveScreenProfile): NormalizedScanZone {
        val lower = packageName?.lowercase() ?: ""

        return when {
            // Uber Driver offer cards are anchored to the bottom sheet
            lower.contains("uber") -> {
                if (profile.isTabletOrFoldable) {
                    NormalizedScanZone(0.15f, 0.35f, 0.85f, 0.98f)
                } else {
                    NormalizedScanZone(0.0f, 0.30f, 1.0f, 0.98f)
                }
            }
            // InDrive dialogs appear in a floating center-bottom card
            lower.contains("indriver") || lower.contains("mizink") || lower.contains("sinet") -> {
                if (profile.isTabletOrFoldable) {
                    NormalizedScanZone(0.10f, 0.20f, 0.90f, 0.95f)
                } else {
                    NormalizedScanZone(0.02f, 0.18f, 0.98f, 0.96f)
                }
            }
            // DiDi Driver alerts span between mid-screen dispatch cards and bottom CTA
            lower.contains("didi") -> {
                if (profile.isTabletOrFoldable) {
                    NormalizedScanZone(0.15f, 0.25f, 0.85f, 0.95f)
                } else {
                    NormalizedScanZone(0.0f, 0.25f, 1.0f, 0.95f)
                }
            }
            // Bolt & Careem
            lower.contains("bolt") || lower.contains("careem") -> {
                NormalizedScanZone(0.0f, 0.28f, 1.0f, 0.96f)
            }
            // Generic fallback zone
            else -> {
                NormalizedScanZone(0.0f, 0.15f, 1.0f, 0.98f)
            }
        }
    }

    /**
     * Efficiently crops full-screen frame to the app's target zone for faster OCR inference.
     */
    fun cropToScanZone(bitmap: Bitmap, packageName: String?, profile: AdaptiveScreenProfile): Bitmap {
        val zone = getNormalizedScanZone(packageName, profile)
        val x = (zone.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
        val y = (zone.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
        val w = ((zone.right - zone.left) * bitmap.width).toInt().coerceIn(1, bitmap.width - x)
        val h = ((zone.bottom - zone.top) * bitmap.height).toInt().coerceIn(1, bitmap.height - y)

        return try {
            Bitmap.createBitmap(bitmap, x, y, w, h)
        } catch (e: Exception) {
            bitmap // Return original if crop fails
        }
    }

    /**
     * Maximum traversal recursion depth tailored per app hierarchy complexity.
     */
    fun getMaxTraversalDepth(packageName: String?): Int {
        val lower = packageName?.lowercase() ?: ""
        return when {
            lower.contains("uber") -> 50 // Deep nested Compose/View hierarchy
            lower.contains("didi") -> 45
            lower.contains("indriver") -> 35
            else -> 40
        }
    }
}
