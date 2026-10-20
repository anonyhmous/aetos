package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.databinding.ActivityMainBinding
import com.example.service.AetosAccessibilityService
import com.example.service.FloatingOverlayService
import com.example.ui.AreasActivity
import com.example.ui.SettingsActivity
import com.example.ui.SubscribeActivity
import com.example.ui.adapter.RideLogAdapter
import com.example.ui.adapter.UiMutationAdapter
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var logAdapter: RideLogAdapter
    private lateinit var mutationAdapter: UiMutationAdapter

    private var isOverlayServiceRunning = false

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            updatePermissionStatus()
            if (isGranted) {
                Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val repository = AetosApplication.instance.repository
        viewModel = ViewModelProvider(this, MainViewModel.Factory(repository))[MainViewModel::class.java]

        setupRecyclerViews()
        setupListeners()
        observeData()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }

    private fun setupRecyclerViews() {
        logAdapter = RideLogAdapter()
        binding.rvRideLogs.layoutManager = LinearLayoutManager(this)
        binding.rvRideLogs.adapter = logAdapter

        mutationAdapter = UiMutationAdapter()
        binding.rvMutations.layoutManager = LinearLayoutManager(this)
        binding.rvMutations.adapter = mutationAdapter
    }

    private fun setupListeners() {
        // Master Engine Switch
        binding.switchMasterEngine.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (checkAndRequestOverlayPermission()) {
                    FloatingOverlayService.start(this)
                    isOverlayServiceRunning = true
                    updateEngineUi(true)
                } else {
                    binding.switchMasterEngine.isChecked = false
                }
            } else {
                FloatingOverlayService.stop(this)
                isOverlayServiceRunning = false
                updateEngineUi(false)
            }
        }

        // Permission Card 1: Overlay
        binding.btnGrantOverlay.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } else {
                Toast.makeText(this, "Overlay permission is already active", Toast.LENGTH_SHORT).show()
            }
        }

        // Permission Card 2: Accessibility
        binding.btnGrantAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(
                this,
                "Please enable 'Aetos Driver Automation Engine' in the list",
                Toast.LENGTH_LONG
            ).show()
        }

        // Permission Card 3: Notification
        binding.btnGrantNotification.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                Toast.makeText(this, "Notification permission is granted by default", Toast.LENGTH_SHORT).show()
            }
        }

        // Launch / Stop Floating HUD
        binding.btnToggleOverlay.setOnClickListener {
            if (!isOverlayServiceRunning) {
                if (checkAndRequestOverlayPermission()) {
                    FloatingOverlayService.start(this)
                    isOverlayServiceRunning = true
                    updateEngineUi(true)
                    Toast.makeText(this, "Aetos Floating HUD Launched!", Toast.LENGTH_SHORT).show()
                }
            } else {
                FloatingOverlayService.stop(this)
                isOverlayServiceRunning = false
                updateEngineUi(false)
                Toast.makeText(this, "Floating HUD Stopped", Toast.LENGTH_SHORT).show()
            }
        }

        // Simulate Ride Alert
        binding.btnSimulateRide.setOnClickListener {
            viewModel.simulateAlert()
            Toast.makeText(this, "Simulating ride alert: Check floating HUD & logs!", Toast.LENGTH_SHORT).show()
        }

        // Test Screen OCR
        binding.btnScanOcrMain.setOnClickListener {
            viewModel.triggerOcrScan { success ->
                if (success) {
                    Toast.makeText(this, "Screen OCR scan completed! Telemetry updated.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Enable 'Aetos Driver Automation Engine' in Accessibility to capture screen.", Toast.LENGTH_LONG).show()
                }
            }
        }

        // Autonomous AI Heuristic Simulation & Clear
        binding.btnSimulateMutation.setOnClickListener {
            viewModel.simulateUiMutation()
            Toast.makeText(this, "Simulated UI Shift: Check Floating HUD & AI Monitor!", Toast.LENGTH_SHORT).show()
        }

        binding.btnClearMutations.setOnClickListener {
            viewModel.clearMutations()
            Toast.makeText(this, "AI Mutation logs cleared", Toast.LENGTH_SHORT).show()
        }

        // Clear Logs
        binding.btnClearLogs.setOnClickListener {
            viewModel.clearLogs()
            Toast.makeText(this, "Ride logs cleared", Toast.LENGTH_SHORT).show()
        }

        // Navigation Tiles
        binding.navTileSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.navTileAreas.setOnClickListener {
            startActivity(Intent(this, AreasActivity::class.java))
        }

        binding.navTileSubscribe.setOnClickListener {
            startActivity(Intent(this, SubscribeActivity::class.java))
        }
    }

    private fun checkAndRequestOverlayPermission(): Boolean {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Please enable 'Display over other apps' first", Toast.LENGTH_LONG).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            return false
        }
        return true
    }

    private fun updatePermissionStatus() {
        // Overlay Permission
        val hasOverlay = Settings.canDrawOverlays(this)
        if (hasOverlay) {
            binding.btnGrantOverlay.text = getString(R.string.permission_granted)
            binding.btnGrantOverlay.isEnabled = false
            binding.btnGrantOverlay.setTextColor(ContextCompat.getColor(this, R.color.accent_driver_green))
        } else {
            binding.btnGrantOverlay.text = getString(R.string.grant_permission)
            binding.btnGrantOverlay.isEnabled = true
            binding.btnGrantOverlay.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        }

        // Accessibility Permission
        val hasAccessibility = isAccessibilityServiceEnabled(this, AetosAccessibilityService::class.java)
        if (hasAccessibility) {
            binding.btnGrantAccessibility.text = getString(R.string.permission_granted)
            binding.btnGrantAccessibility.isEnabled = false
            binding.btnGrantAccessibility.setTextColor(ContextCompat.getColor(this, R.color.accent_driver_green))
        } else {
            binding.btnGrantAccessibility.text = getString(R.string.grant_permission)
            binding.btnGrantAccessibility.isEnabled = true
            binding.btnGrantAccessibility.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        }

        // Notification Permission
        val hasNotification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        if (hasNotification) {
            binding.btnGrantNotification.text = getString(R.string.permission_granted)
            binding.btnGrantNotification.isEnabled = false
            binding.btnGrantNotification.setTextColor(ContextCompat.getColor(this, R.color.accent_driver_green))
        } else {
            binding.btnGrantNotification.text = getString(R.string.grant_permission)
            binding.btnGrantNotification.isEnabled = true
            binding.btnGrantNotification.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        }
    }

    private fun updateEngineUi(active: Boolean) {
        if (active) {
            binding.tvEngineStatus.text = getString(R.string.service_active)
            binding.tvEngineStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_driver_green))
            binding.btnToggleOverlay.text = "Stop Floating HUD"
            binding.btnToggleOverlay.setBackgroundColor(ContextCompat.getColor(this, R.color.alert_red))
        } else {
            binding.tvEngineStatus.text = getString(R.string.service_inactive)
            binding.tvEngineStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            binding.btnToggleOverlay.text = "Launch Floating HUD"
            binding.btnToggleOverlay.setBackgroundColor(ContextCompat.getColor(this, R.color.accent_driver_green))
        }
    }

    private fun observeData() {
        lifecycleScope.launch {
            viewModel.logs.collectLatest { logsList ->
                logAdapter.submitList(logsList)
                if (logsList.isEmpty()) {
                    binding.tvEmptyLogs.visibility = View.VISIBLE
                    binding.rvRideLogs.visibility = View.GONE
                } else {
                    binding.tvEmptyLogs.visibility = View.GONE
                    binding.rvRideLogs.visibility = View.VISIBLE
                }
            }
        }

        lifecycleScope.launch {
            viewModel.mutations.collectLatest { mutationList ->
                mutationAdapter.submitList(mutationList)
                if (mutationList.isNotEmpty()) {
                    binding.rvMutations.visibility = View.VISIBLE
                    binding.tvAiHeuristicStatus.text = "ACTIVE (${mutationList.size} PATCHES)"
                    binding.tvAiHeuristicStatus.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.accent_cyan_info))
                } else {
                    binding.rvMutations.visibility = View.GONE
                    binding.tvAiHeuristicStatus.text = "CALIBRATED"
                    binding.tvAiHeuristicStatus.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.accent_driver_green))
                }
            }
        }
    }

    private fun isAccessibilityServiceEnabled(context: Context, service: Class<*>): Boolean {
        val expectedComponentName = "${context.packageName}/${service.name}"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices.split(":").any { it.equals(expectedComponentName, ignoreCase = true) }
    }
}
