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
 * Screen 1: HomeScreen (Root Dashboard for Android Auto).
 * Strict driving-safe 6-row layout satisfying Android for Cars Guidelines:
 * 1. House Status Row
 * 2. South Garage Door Row
 * 3. Left Garage Door Row
 * 4. Heat Pump Water Heater Row
 * 5. Pool Water Row
 * 6. Zone Temperatures Row (Browsable chevron to ZonesListScreen)
 */
class HomeScreen(
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
        val statesMap = repository.states.value

        // If states are completely empty, provide an initial waiting/sync message
        if (statesMap.isEmpty()) {
            return MessageTemplate.Builder("Connecting to Home Assistant dashboard...")
                .setTitle("Home Control")
                .setHeaderAction(Action.APP_ICON)
                .addAction(
                    Action.Builder()
                        .setTitle("Connect")
                        .setOnClickListener {
                            repository.initializeConnection()
                            invalidate()
                        }
                        .build()
                )
                .build()
        }

        val listBuilder = ItemList.Builder()

        // -------------------------------------------------------------
        // Row 1: House Status Row (Schedule, Global Mode, Indoor & Outdoor)
        // -------------------------------------------------------------
        val houseOverview = repository.getHouseOverviewState()
        val indoorStr = houseOverview.indoorAvgTemp?.let { String.format(Locale.US, "%.1f°F", it) } ?: "--"
        val outdoorStr = houseOverview.outdoorTemp?.let { String.format(Locale.US, "%.1f°F", it) } ?: "--"
        val scheduleStr = houseOverview.scheduleState
        val modeStr = houseOverview.globalHvacMode

        val houseIcon = CarIcon.Builder(
            IconCompat.createWithResource(carContext, R.drawable.ic_home_mode)
        ).setTint(CarColor.PRIMARY).build()

        val houseRow = Row.Builder()
            .setTitle("House: $scheduleStr Schedule ($modeStr)")
            .setImage(houseIcon, Row.IMAGE_TYPE_ICON)
            .addText("Indoor Avg: $indoorStr  •  Outdoor: $outdoorStr")
            .addText("Tap to cycle schedule (Day / Night / Away)")
            .setOnClickListener {
                repository.cycleHouseScheduleState { nextState ->
                    CarToast.makeText(carContext, "Schedule set to $nextState", CarToast.LENGTH_SHORT).show()
                    invalidate()
                }
            }
            .build()
        listBuilder.addItem(houseRow)

        // -------------------------------------------------------------
        // Row 2: South Garage Door Row (Status & 1-Tap Toggle)
        // -------------------------------------------------------------
        val southGarage = repository.getSouthGarageState()
        val southColor = if (southGarage.isOpen) CarColor.RED else CarColor.GREEN
        val southStatus = if (southGarage.isOpen) "OPEN" else "CLOSED"

        val southIcon = CarIcon.Builder(
            IconCompat.createWithResource(carContext, R.drawable.ic_garage_door)
        ).setTint(southColor).build()

        val southRow = Row.Builder()
            .setTitle("South Garage: $southStatus")
            .setImage(southIcon, Row.IMAGE_TYPE_ICON)
            .addText("Status: $southStatus  •  Tap to ${if (southGarage.isOpen) "Close" else "Open"}")
            .setOnClickListener {
                CarToast.makeText(carContext, "Triggering South Garage...", CarToast.LENGTH_SHORT).show()
                repository.toggleSouthGarage { success ->
                    val msg = if (success) "South Garage triggered" else "Command dispatched"
                    CarToast.makeText(carContext, msg, CarToast.LENGTH_SHORT).show()
                    invalidate()
                }
            }
            .build()
        listBuilder.addItem(southRow)

        // -------------------------------------------------------------
        // Row 3: Left Garage Door Row (Status & 1-Tap Toggle)
        // -------------------------------------------------------------
        val leftGarage = repository.getLeftGarageState()
        val leftColor = if (leftGarage.isOpen) CarColor.RED else CarColor.GREEN
        val leftStatus = if (leftGarage.isOpen) "OPEN" else "CLOSED"

        val leftIcon = CarIcon.Builder(
            IconCompat.createWithResource(carContext, R.drawable.ic_garage_door)
        ).setTint(leftColor).build()

        val leftRow = Row.Builder()
            .setTitle("Left Garage: $leftStatus")
            .setImage(leftIcon, Row.IMAGE_TYPE_ICON)
            .addText("Status: $leftStatus  •  Tap to toggle")
            .setOnClickListener {
                CarToast.makeText(carContext, "Triggering Left Garage...", CarToast.LENGTH_SHORT).show()
                repository.toggleLeftGarage { success ->
                    val msg = if (success) "Left Garage toggled" else "Command dispatched"
                    CarToast.makeText(carContext, msg, CarToast.LENGTH_SHORT).show()
                    invalidate()
                }
            }
            .build()
        listBuilder.addItem(leftRow)

        // -------------------------------------------------------------
        // Row 4: Heat Pump Water Heater Row (Mode & Available %)
        // -------------------------------------------------------------
        val waterHeater = repository.getWaterHeaterState()
        val whIcon = CarIcon.Builder(
            IconCompat.createWithResource(carContext, R.drawable.ic_water_heater)
        ).setTint(CarColor.PRIMARY).build()

        val whRow = Row.Builder()
            .setTitle("Hot Water: ${waterHeater.mode} (${waterHeater.availablePercent}%)")
            .setImage(whIcon, Row.IMAGE_TYPE_ICON)
            .addText("Tank Reserve: ${waterHeater.availablePercent}% available")
            .addText("Tap to cycle mode (Eco / Heat Pump / High Demand)")
            .setOnClickListener {
                repository.cycleWaterHeaterMode { newMode ->
                    CarToast.makeText(carContext, "Water Heater: $newMode", CarToast.LENGTH_SHORT).show()
                    invalidate()
                }
            }
            .build()
        listBuilder.addItem(whRow)

        // -------------------------------------------------------------
        // Row 5: Pool Water Row (Temp, Health & Pump Toggle)
        // -------------------------------------------------------------
        val pool = repository.getPoolState()
        val poolTempStr = pool.waterTemp?.let { String.format(Locale.US, "%.1f°F", it) } ?: "--"
        val pumpStr = if (pool.isPumpOn) "ON" else "OFF"
        val poolIconColor = if (pool.isPumpOn) CarColor.BLUE else CarColor.SECONDARY

        val poolIcon = CarIcon.Builder(
            IconCompat.createWithResource(carContext, R.drawable.ic_pool)
        ).setTint(poolIconColor).build()

        val poolRow = Row.Builder()
            .setTitle("Pool Water: $poolTempStr (${pool.statusBadge})")
            .setImage(poolIcon, Row.IMAGE_TYPE_ICON)
            .addText("Temp: $poolTempStr  •  Status: ${pool.statusBadge}  •  Pump: $pumpStr")
            .addText("Tap to toggle pool pump")
            .setOnClickListener {
                repository.togglePoolPump { success ->
                    val msg = if (success) "Pool pump toggled" else "Command dispatched"
                    CarToast.makeText(carContext, msg, CarToast.LENGTH_SHORT).show()
                    invalidate()
                }
            }
            .build()
        listBuilder.addItem(poolRow)

        // -------------------------------------------------------------
        // Row 6: Zone Temperatures Row (Browsable chevron to ZonesListScreen)
        // -------------------------------------------------------------
        val zoneIcon = CarIcon.Builder(
            IconCompat.createWithResource(carContext, R.drawable.ic_climate_zone)
        ).setTint(CarColor.YELLOW).build()

        val zones = repository.getClimateZones()
        val activeZonesCount = zones.count { !it.hvacMode.equals("OFF", ignoreCase = true) }
        val zonesRow = Row.Builder()
            .setTitle("Zone Temperatures")
            .setImage(zoneIcon, Row.IMAGE_TYPE_ICON)
            .addText("${zones.size} rooms configured  •  $activeZonesCount active")
            .setBrowsable(true)
            .setOnClickListener {
                // Navigate into detailed Zone Temperatures screen
                screenManager.push(ZonesListScreen(carContext, repository))
            }
            .build()
        listBuilder.addItem(zonesRow)

        // -------------------------------------------------------------
        // Action Strip with Sync action
        // -------------------------------------------------------------
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
                        CarToast.makeText(carContext, "Syncing Home Assistant...", CarToast.LENGTH_SHORT).show()
                        invalidate()
                    }
                    .build()
            )
            .build()

        return ListTemplate.Builder()
            .setTitle("Home Control")
            .setHeaderAction(Action.APP_ICON)
            .setSingleList(listBuilder.build())
            .setActionStrip(actionStrip)
            .build()
    }
}
