package com.example.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.AetosApplication
import com.example.MainActivity
import com.example.R
import com.example.databinding.FloatingWidgetLayoutBinding
import com.example.engine.FareCalculatorEngine
import com.example.model.ExtractionEngine
import com.example.model.FareBreakdown
import com.example.model.PickupMetric
import com.example.model.ProfitRating
import com.example.model.RideTelemetryState
import com.example.model.TripMetric
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.random.Random

class FloatingOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var binding: FloatingWidgetLayoutBinding? = null
    private var windowParams: WindowManager.LayoutParams? = null
    private var isOverlayAdded = false
    private var isCollapsed = false

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private val repository by lazy { AetosApplication.instance.repository }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceSafely()
        initFloatingOverlay()
        observeTelemetry()
    }

    /**
     * Starts foreground service with Android 14-16 compliance.
     * Guards against ForegroundServiceStartNotAllowedException on Samsung One UI and modern OS.
     */
    private fun startForegroundServiceSafely() {
        try {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notification: Notification = NotificationCompat.Builder(this, AetosApplication.CHANNEL_ID_OVERLAY)
                .setContentTitle(getString(R.string.app_name))
                .setContentText("Aetos HUD is active: Monitoring ride metrics & true fares")
                .setSmallIcon(R.drawable.ic_app_logo)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                }
                startForeground(NOTIFICATION_ID, notification, serviceType)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Initializes the floating overlay window with strict permission and state validation.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun initFloatingOverlay() {
        // Strict permission check to prevent SecurityException/BadTokenException
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        try {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val inflater = LayoutInflater.from(this)
            binding = FloatingWidgetLayoutBinding.inflate(inflater)
            floatingView = binding?.root

            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            windowParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 40
                y = 150
            }

            setupDragListener()
            setupButtons()

            if (!isOverlayAdded && floatingView != null) {
                windowManager?.addView(floatingView, windowParams)
                isOverlayAdded = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDragListener() {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        binding?.headerDragBar?.setOnTouchListener { _, event ->
            val params = windowParams ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    if (isOverlayAdded && floatingView != null) {
                        try {
                            windowManager?.updateViewLayout(floatingView, params)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun setupButtons() {
        binding?.ivCloseOverlay?.setOnClickListener {
            stopSelf()
        }

        binding?.ivCollapseOverlay?.setOnClickListener {
            toggleCollapse()
        }

        binding?.btnSimulateNext?.setOnClickListener {
            simulateNextRide()
        }

        binding?.btnScanOcr?.setOnClickListener {
            triggerOcrScan()
        }

        binding?.btnDismissMutation?.setOnClickListener {
            repository.dismissActiveMutationAlert()
        }
    }

    private fun toggleCollapse() {
        val b = binding ?: return
        isCollapsed = !isCollapsed
        if (isCollapsed) {
            b.metricsContentContainer.visibility = View.GONE
            b.ivCollapseOverlay.rotation = 180f
        } else {
            b.metricsContentContainer.visibility = View.VISIBLE
            b.ivCollapseOverlay.rotation = 0f
        }
    }

    private fun triggerOcrScan() {
        val accessibilityService = AetosAccessibilityService.instance
        if (accessibilityService != null) {
            Toast.makeText(this, "Capturing screen for OCR analysis...", Toast.LENGTH_SHORT).show()
            accessibilityService.triggerManualOcr { success ->
                if (!success) {
                    Toast.makeText(this, "Screen capture unavailable or restricted", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(this, "Enable Aetos Accessibility in Settings to use OCR capture", Toast.LENGTH_LONG).show()
        }
    }

    private fun observeTelemetry() {
        serviceScope.launch {
            repository.liveTelemetry.collectLatest { state ->
                if (state != null) {
                    renderTelemetry(state)
                } else {
                    simulateDefaultData()
                }
            }
        }

        serviceScope.launch {
            repository.activeMutationAlert.collectLatest { alert ->
                val b = binding ?: return@collectLatest
                if (alert != null && !alert.isDismissed) {
                    b.bannerAiMutation.visibility = View.VISIBLE
                    b.tvAiMutationTitle.text = "AI Heuristic: ${alert.appName}"
                    b.tvAiMutationDesc.text = "${alert.description} (${(alert.confidenceScore * 100).toInt()}% Conf) • ${alert.patchActionMessage}"
                } else {
                    b.bannerAiMutation.visibility = View.GONE
                }
            }
        }
    }

    private fun renderTelemetry(state: RideTelemetryState) {
        val b = binding ?: return

        // 1. ISOLATED PICKUP METRICS (Operational Overhead)
        b.tvPickupDistance.text = String.format(Locale.US, "%.1f km", state.pickup.distanceKm)
        b.tvPickupTime.text = "${state.pickup.durationMinutes} min away"

        // 2. ISOLATED TRIP METRICS (Passenger Journey)
        b.tvTripDistance.text = String.format(Locale.US, "%.1f km", state.trip.distanceKm)
        b.tvTripDuration.text = "${state.trip.durationMinutes} mins"

        // 3. FARE METRICS & SURPLUS/DEFICIT
        val curr = state.fare.currencySymbol
        b.tvCalculatedFare.text = String.format(Locale.US, "%s%.2f", curr, state.fare.calculatedTrueFare)
        b.tvAppOfferFare.text = String.format(Locale.US, "Offer: %s%.2f", curr, state.fare.appOfferFare)

        val diff = state.fare.difference
        if (diff >= 0.0) {
            b.tvFareDifference.text = String.format(Locale.US, "+ %s%.2f (Surplus)", curr, diff)
            b.tvFareDifference.setTextColor(ContextCompat.getColor(this, R.color.accent_driver_green))
        } else {
            b.tvFareDifference.text = String.format(Locale.US, "- %s%.2f (Underpriced)", curr, abs(diff))
            b.tvFareDifference.setTextColor(ContextCompat.getColor(this, R.color.alert_red))
        }

        b.tvRatePerKm.text = String.format(Locale.US, "Trip Rate: %s%.2f/km", curr, state.fare.ratePerKm)
        b.tvNetDriverProfit.text = String.format(Locale.US, "Driver Net: %s%.2f", curr, state.fare.netDriverFare)
        b.tvTotalDistanceSummary.text = String.format(Locale.US, "Total Op: %.1f km", state.fare.totalOperationalDistanceKm)

        // 4. ENGINE SOURCE BADGE
        when (state.engineSource) {
            ExtractionEngine.ACCESSIBILITY_TREE -> {
                b.tvEngineSourceBadge.text = "NODE TREE"
                b.tvEngineSourceBadge.setTextColor(ContextCompat.getColor(this, R.color.accent_cyan_info))
            }
            ExtractionEngine.OCR_VISION -> {
                b.tvEngineSourceBadge.text = "OCR VISION"
                b.tvEngineSourceBadge.setTextColor(ContextCompat.getColor(this, R.color.accent_amber))
            }
            ExtractionEngine.SIMULATION -> {
                b.tvEngineSourceBadge.text = "SIMULATED"
                b.tvEngineSourceBadge.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            }
        }

        // 5. PROFITABILITY BADGE
        when (state.fare.profitRating) {
            ProfitRating.HIGH_PROFIT -> {
                b.tvProfitabilityBadge.text = "PROFITABLE"
                b.tvProfitabilityBadge.setTextColor(ContextCompat.getColor(this, R.color.accent_driver_green))
            }
            ProfitRating.ACCEPTABLE -> {
                b.tvProfitabilityBadge.text = "FAIR"
                b.tvProfitabilityBadge.setTextColor(ContextCompat.getColor(this, R.color.accent_amber))
            }
            ProfitRating.LOW_PROFIT -> {
                b.tvProfitabilityBadge.text = "SKIP / LOW"
                b.tvProfitabilityBadge.setTextColor(ContextCompat.getColor(this, R.color.alert_red))
            }
        }

        b.tvAppSource.text = "${state.appName} • Surge: ${state.surgeMultiplier}x"
    }

    private fun simulateDefaultData() {
        serviceScope.launch {
            val settings = repository.getSettingsSync()
            val engine = FareCalculatorEngine(settings)
            val pickup = PickupMetric(1.2, 3, "1.2 km / 3 min away")
            val trip = TripMetric(8.5, 22, "8.5 km / 22 mins")
            val fare = engine.calculate(pickup, trip, 28.50, 1.0)
            val state = RideTelemetryState(
                appName = "Uber X",
                pickup = pickup,
                trip = trip,
                fare = fare,
                engineSource = ExtractionEngine.ACCESSIBILITY_TREE
            )
            renderTelemetry(state)
        }
    }

    fun simulateNextRide() {
        serviceScope.launch {
            val settings = repository.getSettingsSync()
            val engine = FareCalculatorEngine(settings)
            val apps = listOf("Uber X", "InDrive", "Careem GO", "Bolt", "DiDi Fast", "Yango Pro")
            val app = apps.random()

            val pickupKm = (5 + Random.nextInt(25)) / 10.0 // 0.5 - 3.0 km
            val pickupMin = (pickupKm * 2.5 + Random.nextInt(3)).toInt().coerceAtLeast(1)
            val tripKm = (30 + Random.nextInt(150)) / 10.0 // 3.0 - 18.0 km
            val tripMin = (tripKm * 2.2 + Random.nextInt(6)).toInt().coerceAtLeast(5)

            // Calculate ideal passenger fare based strictly on trip distance + trip duration
            val idealFare = settings.baseFare + (tripKm * settings.pricePerKm) + (tripMin * settings.pricePerMinute)
            val offerRatio = listOf(0.78, 0.92, 1.15, 1.35).random()
            val offerFare = (idealFare * offerRatio * 100).toInt() / 100.0

            val pickup = PickupMetric(pickupKm, pickupMin, "$pickupKm km / $pickupMin min away")
            val trip = TripMetric(tripKm, tripMin, "$tripKm km / $tripMin min trip")
            val fare = engine.calculate(pickup, trip, offerFare, 1.0)

            val state = RideTelemetryState(
                appName = app,
                pickup = pickup,
                trip = trip,
                fare = fare,
                engineSource = ExtractionEngine.SIMULATION
            )
            repository.updateLiveTelemetry(state)
            repository.insertRideLog(state)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        if (isOverlayAdded && floatingView != null) {
            try {
                windowManager?.removeView(floatingView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        isOverlayAdded = false
        floatingView = null
        binding = null
    }

    companion object {
        private const val NOTIFICATION_ID = 9021

        fun start(context: Context) {
            val intent = Intent(context, FloatingOverlayService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingOverlayService::class.java)
            try {
                context.stopService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
