package com.example.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.AetosApplication
import com.example.engine.AdaptiveUiEngine
import com.example.engine.FareCalculatorEngine
import com.example.engine.OcrProcessor
import com.example.engine.RideHailingPackages
import com.example.engine.TripMetricParser
import com.example.engine.UiMutationDetector
import com.example.model.ExtractionEngine
import com.example.model.RideTelemetryState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AetosAccessibilityService : AccessibilityService() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)
    private val repository by lazy { AetosApplication.instance.repository }
    private val ocrProcessor by lazy { OcrProcessor() }

    private var lastProcessedHash: Int = 0
    private var lastProcessTime: Long = 0L
    private var isOcrProcessing = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: ""

        // Multi-App Adaptive Target Filtering (Uber, InDrive, DiDi, Careem, Bolt, Yango, Lyft, Grab)
        if (!RideHailingPackages.isTargetPackage(packageName)) {
            return
        }

        // Throttle rapid repeated events (minimum 350ms between scans)
        val now = System.currentTimeMillis()
        if (now - lastProcessTime < 350L) return

        val rootNode = try {
            rootInActiveWindow
        } catch (e: Exception) {
            null
        } ?: return

        val textNodes = mutableListOf<String>()
        var measuredMaxDepth = 0
        var totalNodeCount = 0

        val maxAllowedDepth = AdaptiveUiEngine.getMaxTraversalDepth(packageName)

        try {
            val stats = IntArray(2) // [0] = maxDepth, [1] = nodeCount
            traverseNodeSafely(rootNode, textNodes, depth = 0, maxDepth = maxAllowedDepth, stats = stats)
            measuredMaxDepth = stats[0]
            totalNodeCount = stats[1]
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val contentHash = textNodes.hashCode()
        if (contentHash == lastProcessedHash && textNodes.isNotEmpty()) {
            return
        }
        lastProcessedHash = contentHash
        lastProcessTime = now

        val finalMeasuredDepth = measuredMaxDepth
        val finalNodeCount = totalNodeCount

        serviceScope.launch {
            processDualEngine(
                packageName = packageName,
                texts = textNodes,
                nodeCount = finalNodeCount,
                maxDepth = finalMeasuredDepth
            )
        }
    }

    /**
     * Safely traverses AccessibilityNodeInfo tree with adaptive depth limiter and exception safeguards
     * against recycled nodes on modern Android 14-16 systems.
     */
    private fun traverseNodeSafely(
        node: AccessibilityNodeInfo?,
        collected: MutableList<String>,
        depth: Int,
        maxDepth: Int,
        stats: IntArray
    ) {
        if (node == null || depth > maxDepth) return

        stats[0] = maxOf(stats[0], depth)
        stats[1]++

        try {
            node.text?.toString()?.trim()?.let {
                if (it.isNotEmpty() && !collected.contains(it)) collected.add(it)
            }
            node.contentDescription?.toString()?.trim()?.let {
                if (it.isNotEmpty() && !collected.contains(it)) collected.add(it)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                node.hintText?.toString()?.trim()?.let {
                    if (it.isNotEmpty() && !collected.contains(it)) collected.add(it)
                }
            }

            val childCount = node.childCount
            for (i in 0 until childCount) {
                val child = try {
                    node.getChild(i)
                } catch (e: Exception) {
                    null
                }
                if (child != null) {
                    traverseNodeSafely(child, collected, depth + 1, maxDepth, stats)
                }
            }
        } catch (e: Exception) {
            // Protect against recycled nodes
        }
    }

    /**
     * Dual-Engine Pipeline: Primary Accessibility Node Tree parsing with Visual OCR Fallback
     * and Autonomous UI Mutation Detection.
     */
    private suspend fun processDualEngine(
        packageName: String,
        texts: List<String>,
        nodeCount: Int,
        maxDepth: Int
    ) {
        val settings = repository.getSettingsSync()
        val areas = repository.getAreasSync()
        val readableAppName = RideHailingPackages.getReadableAppName(packageName)

        // 1. Primary Engine: Parse node tree texts
        val (pickup, trip) = TripMetricParser.parseRideDetails(texts)
        val fullJoined = texts.joinToString(" ")
        val offerFare = TripMetricParser.extractFareAmount(fullJoined)

        val hasValidRideDetails = (trip.distanceKm > 0.0 && trip.distanceKm != 8.5) || offerFare > 0.0

        if (hasValidRideDetails) {
            // Primary Engine succeeded
            computeAndPublishTelemetry(
                packageName = packageName,
                pickup = pickup,
                trip = trip,
                offerFare = offerFare,
                fullText = fullJoined,
                source = ExtractionEngine.ACCESSIBILITY_TREE,
                settings = settings,
                areas = areas
            )

            // Autonomous Mutation Analysis (for depth changes or tag shifts)
            val mutationAlert = UiMutationDetector.analyzeHierarchy(
                packageName = packageName,
                appName = readableAppName,
                nodeCount = nodeCount,
                maxDepth = maxDepth,
                nodeExtractionSuccess = true,
                ocrFallbackSuccess = false,
                rawTexts = texts
            )
            if (mutationAlert != null) {
                repository.insertMutation(mutationAlert)
            }
            return
        }

        // 2. Fallback Engine: Visual OCR via ML Kit Text Recognition with Adaptive Bounding Zone
        if (!isOcrProcessing && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val ocrSuccess = performOcrFallback(packageName, nodeCount, maxDepth, texts)
            // Autonomous Mutation Analysis (detects canvas restriction if OCR succeeded while node tree was blank)
            val mutationAlert = UiMutationDetector.analyzeHierarchy(
                packageName = packageName,
                appName = readableAppName,
                nodeCount = nodeCount,
                maxDepth = maxDepth,
                nodeExtractionSuccess = false,
                ocrFallbackSuccess = ocrSuccess,
                rawTexts = texts
            )
            if (mutationAlert != null) {
                repository.insertMutation(mutationAlert)
            }
        }
    }

    /**
     * Triggers visual OCR fallback asynchronously using platform screenshot API and adaptive crop.
     */
    private suspend fun performOcrFallback(
        packageName: String,
        nodeCount: Int,
        maxDepth: Int,
        nodeTexts: List<String>
    ): Boolean {
        isOcrProcessing = true
        try {
            val rawBitmap = captureActiveScreen() ?: return false
            val screenProfile = AdaptiveUiEngine.resolveScreenProfile(this)
            val croppedBitmap = AdaptiveUiEngine.cropToScanZone(rawBitmap, packageName, screenProfile)

            val ocrResult = ocrProcessor.processFrame(croppedBitmap)
            if (croppedBitmap != rawBitmap) {
                croppedBitmap.recycle()
            }
            rawBitmap.recycle()

            if (ocrResult.success && (ocrResult.trip.distanceKm > 0.0 || ocrResult.fareOffer > 0.0)) {
                val settings = repository.getSettingsSync()
                val areas = repository.getAreasSync()
                computeAndPublishTelemetry(
                    packageName = packageName,
                    pickup = ocrResult.pickup,
                    trip = ocrResult.trip,
                    offerFare = ocrResult.fareOffer,
                    fullText = ocrResult.rawFullText,
                    source = ExtractionEngine.OCR_VISION,
                    settings = settings,
                    areas = areas
                )
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isOcrProcessing = false
        }
        return false
    }

    /**
     * Captures current active screen bitmap via Accessibility API on Android 11+ (API 30 to 36).
     */
    private suspend fun captureActiveScreen(): Bitmap? = suspendCancellableCoroutine { continuation ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    mainExecutor,
                    object : TakeScreenshotCallback {
                        override fun onSuccess(screenshotResult: ScreenshotResult) {
                            try {
                                val hardwareBuffer = screenshotResult.hardwareBuffer
                                val colorSpace = screenshotResult.colorSpace
                                val hwBitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                                val softwareBitmap = hwBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                                hardwareBuffer.close()
                                continuation.resume(softwareBitmap)
                            } catch (e: Exception) {
                                continuation.resume(null)
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            continuation.resume(null)
                        }
                    }
                )
            } catch (e: Exception) {
                continuation.resume(null)
            }
        } else {
            continuation.resume(null)
        }
    }

    /**
     * Public manual trigger for on-demand OCR scanning from HUD or Dashboard.
     */
    fun triggerManualOcr(onComplete: ((Boolean) -> Unit)? = null) {
        serviceScope.launch {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                isOcrProcessing = true
                val rawBitmap = captureActiveScreen()
                if (rawBitmap != null) {
                    val screenProfile = AdaptiveUiEngine.resolveScreenProfile(this@AetosAccessibilityService)
                    val croppedBitmap = AdaptiveUiEngine.cropToScanZone(rawBitmap, "manual_scan", screenProfile)

                    val result = ocrProcessor.processFrame(croppedBitmap)
                    if (croppedBitmap != rawBitmap) {
                        croppedBitmap.recycle()
                    }
                    rawBitmap.recycle()

                    val settings = repository.getSettingsSync()
                    val areas = repository.getAreasSync()
                    computeAndPublishTelemetry(
                        packageName = "Manual Screen Scan",
                        pickup = result.pickup,
                        trip = result.trip,
                        offerFare = result.fareOffer,
                        fullText = result.rawFullText,
                        source = ExtractionEngine.OCR_VISION,
                        settings = settings,
                        areas = areas
                    )
                    isOcrProcessing = false
                    onComplete?.invoke(true)
                    return@launch
                }
            }
            isOcrProcessing = false
            onComplete?.invoke(false)
        }
    }

    private suspend fun computeAndPublishTelemetry(
        packageName: String,
        pickup: com.example.model.PickupMetric,
        trip: com.example.model.TripMetric,
        offerFare: Double,
        fullText: String,
        source: ExtractionEngine,
        settings: com.example.data.local.FareSettingsEntity,
        areas: List<com.example.data.local.CustomAreaEntity>
    ) {
        // Evaluate Surge Zone Multipliers
        var surgeMultiplier = 1.0
        for (area in areas) {
            if (fullText.contains(area.name, ignoreCase = true)) {
                surgeMultiplier = area.surgeMultiplier
                break
            }
        }

        // Strictly isolated fare calculation
        val engine = FareCalculatorEngine(settings)
        val fare = engine.calculate(pickup, trip, offerFare, surgeMultiplier)
        val readableName = RideHailingPackages.getReadableAppName(packageName)

        val state = RideTelemetryState(
            appName = readableName,
            packageName = packageName,
            timestamp = System.currentTimeMillis(),
            pickup = pickup,
            trip = trip,
            fare = fare,
            surgeMultiplier = surgeMultiplier,
            engineSource = source
        )

        repository.updateLiveTelemetry(state)
        repository.insertRideLog(state)
    }

    override fun onInterrupt() {
        // Interrupted
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        serviceJob.cancel()
        ocrProcessor.release()
    }

    companion object {
        var instance: AetosAccessibilityService? = null
            private set
    }
}
