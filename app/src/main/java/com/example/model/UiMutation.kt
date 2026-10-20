package com.example.model

enum class MutationSeverity {
    INFO_PATCH,
    WARNING_SHIFT,
    CRITICAL_REDESIGN
}

enum class MutationType {
    TAG_RENAMED,
    DEPTH_ALTERED,
    COORDINATE_SHIFTED,
    NODE_TREE_RESTRICTED,
    CURRENCY_LOCALE_SHIFT
}

data class UiMutationAlert(
    val id: Long = 0L,
    val packageName: String,
    val appName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val mutationType: MutationType,
    val severity: MutationSeverity,
    val description: String,
    val confidenceScore: Double,
    val autoPatchApplied: Boolean = true,
    val patchActionMessage: String = "",
    val isDismissed: Boolean = false
)

data class AdaptiveScreenProfile(
    val screenWidthPx: Int,
    val screenHeightPx: Int,
    val densityDpi: Int,
    val aspectRatio: Float,
    val isTabletOrFoldable: Boolean,
    val topInsetPx: Int = 0,
    val bottomInsetPx: Int = 0
)

data class NormalizedScanZone(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)
