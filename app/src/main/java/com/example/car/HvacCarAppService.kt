package com.example.car

import android.app.Application
import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.Screen
import androidx.car.app.CarContext
import androidx.car.app.model.*
import androidx.car.app.validation.HostValidator
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.example.viewmodel.HvacViewModel
import com.example.viewmodel.HvacUiState
import com.example.model.ClimateZone

class HvacCarAppService : CarAppService() {
    override fun onCreateSession(): Session {
        return HvacCarSession()
    }

    override fun createHostValidator(): HostValidator {
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }
}

class HvacCarSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        return HvacCarScreen(carContext)
    }
}

class HvacCarScreen(carContext: CarContext) : Screen(carContext) {
    init {
        val viewModel = HvacViewModel.getInstance(carContext.applicationContext as Application)
        if (viewModel != null) {
            lifecycleScope.launch {
                viewModel.uiState.collect {
                    invalidate()
                }
            }
            lifecycleScope.launch {
                viewModel.autoLayoutStyle.collect {
                    invalidate()
                }
            }
            lifecycleScope.launch {
                viewModel.autoPrimaryZone.collect {
                    invalidate()
                }
            }
            lifecycleScope.launch {
                viewModel.autoShowFanAction.collect {
                    invalidate()
                }
            }
            lifecycleScope.launch {
                viewModel.autoShowPowerAction.collect {
                    invalidate()
                }
            }
            lifecycleScope.launch {
                viewModel.autoTempStep.collect {
                    invalidate()
                }
            }
        }
    }

    override fun onGetTemplate(): Template {
        val viewModel = HvacViewModel.getInstance(carContext.applicationContext as Application)
        if (viewModel == null) {
            return MessageTemplate.Builder("Please open the HVAC Controller app on your phone to establish the connection.")
                .setTitle("HVAC Connection Required")
                .setHeaderAction(Action.APP_ICON)
                .addAction(
                    Action.Builder()
                        .setTitle("Retry")
                        .setOnClickListener { invalidate() }
                        .build()
                )
                .build()
        }

        val uiState = viewModel.uiState.value
        if (uiState !is HvacUiState.Success) {
            return MessageTemplate.Builder("Synchronizing smart home device connection status...")
                .setTitle("Awaiting HVAC Connection")
                .setHeaderAction(Action.APP_ICON)
                .addAction(
                    Action.Builder()
                        .setTitle("Refresh")
                        .setOnClickListener { invalidate() }
                        .build()
                )
                .build()
        }

        val zones = uiState.zones
        if (zones.isEmpty()) {
            return MessageTemplate.Builder("No zones found in the configuration.")
                .setTitle("No Climate Zones Registered")
                .setHeaderAction(Action.APP_ICON)
                .build()
        }

        // Read layouts and customization choices from preference streams managed by ViewModel
        val layoutStyle = viewModel.autoLayoutStyle.value
        val primaryZoneKey = viewModel.autoPrimaryZone.value
        val showFanAction = viewModel.autoShowFanAction.value
        val showPowerAction = viewModel.autoShowPowerAction.value
        val tempStep = viewModel.autoTempStep.value.toDouble()

        if (layoutStyle == "pane" && primaryZoneKey != "all") {
            val selectedZone = zones.find { it.key == primaryZoneKey } ?: zones.first()
            return buildPaneTemplateForZone(selectedZone, viewModel, showFanAction, showPowerAction, tempStep)
        } else {
            return buildListTemplateForZones(zones, viewModel)
        }
    }

    private fun buildListTemplateForZones(
        zones: List<ClimateZone>,
        viewModel: HvacViewModel
    ): Template {
        val listBuilder = ItemList.Builder()

        zones.forEach { zone ->
            val currWord = zone.currentTemp?.let { "$it°F" } ?: "--"
            val targetWord = zone.targetTemp?.let { "$it°F" } ?: "--"
            val modeWord = zone.currentHvacMode.uppercase()
            val fanWord = zone.fanMode

            val desc = "Current: $currWord  •  Target: $targetWord  •  Mode: $modeWord  •  Fan: $fanWord"

            listBuilder.addItem(
                Row.Builder()
                    .setTitle(zone.name)
                    .addText(desc)
                    .setOnClickListener {
                        // Dynamically switch focus to this single zone pane
                        viewModel.setAutoPrimaryZone(zone.key)
                        viewModel.setAutoLayoutStyle("pane")
                        invalidate()
                    }
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setTitle("Climate Zones")
            .setHeaderAction(Action.APP_ICON)
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(
                        Action.Builder()
                            .setTitle("Sync")
                            .setOnClickListener {
                                viewModel.forceSyncOnResume()
                                invalidate()
                            }
                            .build()
                    )
                    .build()
            )
            .build()
    }

    private fun buildPaneTemplateForZone(
        zone: ClimateZone,
        viewModel: HvacViewModel,
        showFanAction: Boolean,
        showPowerAction: Boolean,
        tempStep: Double
    ): Template {
        val paneBuilder = Pane.Builder()

        val currTempStr = zone.currentTemp?.let { "$it°F" } ?: "Not available"
        val targetTempStr = zone.targetTemp?.let { "$it°F" } ?: "Not available"

        paneBuilder.addRow(
            Row.Builder()
                .setTitle("Current Temperature")
                .addText(currTempStr)
                .build()
        )

        paneBuilder.addRow(
            Row.Builder()
                .setTitle("Target Temperature")
                .addText(targetTempStr)
                .build()
        )

        paneBuilder.addRow(
            Row.Builder()
                .setTitle("Operating Settings")
                .addText("Mode: ${zone.currentHvacMode.uppercase()}  •  Fan: ${zone.fanMode}")
                .build()
        )

        // Add action controls
        val stepConverted = if (tempStep > 0.0) tempStep else 1.0

        paneBuilder.addAction(
            Action.Builder()
                .setTitle("Cooler")
                .setOnClickListener {
                    zone.targetTemp?.let { curr ->
                        viewModel.setTargetTemperature(zone.climateEntityId, curr - stepConverted, zone.name)
                    }
                    invalidate()
                }
                .build()
        )

        paneBuilder.addAction(
            Action.Builder()
                .setTitle("Warmer")
                .setOnClickListener {
                    zone.targetTemp?.let { curr ->
                        viewModel.setTargetTemperature(zone.climateEntityId, curr + stepConverted, zone.name)
                    }
                    invalidate()
                }
                .build()
        )

        if (showPowerAction) {
            val isOff = zone.currentHvacMode.lowercase() == "off"
            paneBuilder.addAction(
                Action.Builder()
                    .setTitle(if (isOff) "Turn On" else "Turn Off")
                    .setOnClickListener {
                        val activeThemePreset = viewModel.selectedThemePreset.value
                        viewModel.toggleZonePower(
                            zone.climateEntityId,
                            zone.currentHvacMode,
                            if (isOff) "heat" else "off",
                            zone.name
                        )
                        invalidate()
                    }
                    .build()
            )
        }

        if (showFanAction) {
            paneBuilder.addAction(
                Action.Builder()
                    .setTitle("Fan Cycle")
                    .setOnClickListener {
                        val currentIdx = zone.fanOptions.indexOf(zone.fanMode)
                        val nextIdx = if (currentIdx == -1 || currentIdx >= zone.fanOptions.size - 1) 0 else currentIdx + 1
                        val nextMode = zone.fanOptions[nextIdx]
                        viewModel.selectFanMode(zone.fanEntityId, nextMode, zone.name)
                        invalidate()
                    }
                    .build()
            )
        }

        return PaneTemplate.Builder(paneBuilder.build())
            .setTitle(zone.name)
            .setHeaderAction(
                Action.Builder()
                    .setIcon(CarIcon.BACK)
                    .setOnClickListener {
                        // Switch back to layout list
                        viewModel.setAutoLayoutStyle("list")
                        invalidate()
                    }
                    .build()
            )
            .build()
    }
}
