package com.example.car

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.lifecycleScope
import com.example.R
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Screen 2: Zone Temperatures screen for Android Auto.
 * Displays individual room climate readings and HVAC active states.
 */
class ZonesListScreen(
    carContext: CarContext,
    private val repository: CarHaRepositoryHelper = CarHaRepositoryHelper.getInstance(carContext)
) : Screen(carContext) {

    init {
        // Collect reactive state updates to trigger car screen re-renders
        lifecycleScope.launch {
            repository.states.collect {
                invalidate()
            }
        }
        lifecycleScope.launch {
            repository.connectionState.collect {
                invalidate()
            }
        }
    }

    override fun onGetTemplate(): Template {
        val zones = repository.getClimateZones()

        if (zones.isEmpty()) {
            return MessageTemplate.Builder("Loading climate zone sensor data...")
                .setTitle("Zone Temperatures")
                .setHeaderAction(Action.BACK)
                .addAction(
                    Action.Builder()
                        .setTitle("Refresh")
                        .setOnClickListener {
                            repository.initializeConnection()
                            invalidate()
                        }
                        .build()
                )
                .build()
        }

        val listBuilder = ItemList.Builder()

        zones.forEach { zone ->
            val currTempStr = zone.currentTemp?.let { String.format(Locale.US, "%.1f°F", it) } ?: "--"
            val targetTempStr = zone.targetTemp?.let { String.format(Locale.US, "%.0f°F", it) } ?: "--"
            val modeStr = zone.hvacMode.uppercase(Locale.US)
            val fanStr = zone.fanMode

            val primaryText = "Current: $currTempStr  •  Target: $targetTempStr"
            val secondaryText = "Mode: $modeStr  •  Fan: $fanStr"

            // Set appropriate tint color based on HVAC active mode
            val modeTint = when (modeStr) {
                "HEAT" -> CarColor.RED
                "COOL" -> CarColor.BLUE
                "AUTO" -> CarColor.GREEN
                else -> CarColor.SECONDARY
            }

            val icon = CarIcon.Builder(
                IconCompat.createWithResource(carContext, R.drawable.ic_climate_zone)
            ).setTint(modeTint).build()

            val rowBuilder = Row.Builder()
                .setTitle(zone.name)
                .setImage(icon, Row.IMAGE_TYPE_ICON)
                .addText(primaryText)
                .addText(secondaryText)
                .setOnClickListener {
                    // Tap to cycle zone HVAC mode: HEAT -> COOL -> OFF -> HEAT
                    repository.toggleZoneHvacMode(zone.climateEntityId, zone.hvacMode) { newMode ->
                        CarToast.makeText(
                            carContext,
                            "${zone.name}: ${newMode.uppercase(Locale.US)}",
                            CarToast.LENGTH_SHORT
                        ).show()
                        invalidate()
                    }
                }

            listBuilder.addItem(rowBuilder.build())
        }

        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle("Sync")
                    .setIcon(
                        CarIcon.Builder(
                            IconCompat.createWithResource(carContext, R.drawable.ic_sync)
                        ).build()
                    )
                    .setOnClickListener {
                        repository.initializeConnection()
                        CarToast.makeText(carContext, "Synchronizing zone data...", CarToast.LENGTH_SHORT).show()
                        invalidate()
                    }
                    .build()
            )
            .build()

        return ListTemplate.Builder()
            .setTitle("Zone Temperatures")
            .setHeaderAction(Action.BACK)
            .setSingleList(listBuilder.build())
            .setActionStrip(actionStrip)
            .build()
    }
}
