# System reference — the whole HVAC controller project

**Written 2026-09-22.** This is the rebuild document. If something in Home Assistant, n8n or the
app gets broken, deleted or overwritten by other work, this is what you read to put it back.

It describes what exists, why each piece is shaped the way it is, and which values in one system
must match values in another. The failure mode this project keeps hitting is not a crash — it is
two systems quietly disagreeing about a number. Those are collected in
**[§8 Things that must agree](#8-things-that-must-agree)**. Read that section first if something
is misbehaving.

Companion documents in this folder:

- `AUDIT-2026-09-21.md` — the defect list, with file:line, node names and execution ids
- `AUDIT-2026-09-20.md` — the earlier audit
- `LOGIC-TRACES-2026-09-20.md` — step-by-step traces of individual user actions
- `HANDOVER-2026-09-21.md` — session knowledge, adb quirks, the 61 °F story
- `N8N-EDITING.md` — the three ways an n8n edit silently fails to take effect

---

## Contents

1. [What the system is](#1-what-the-system-is)
2. [System map](#2-system-map)
3. [The physical plant](#3-the-physical-plant)
4. [Home Assistant](#4-home-assistant)
5. [n8n](#5-n8n)
6. [The Android app](#6-the-android-app)
7. [How a single action flows end to end](#7-how-a-single-action-flows-end-to-end)
8. [Things that must agree](#8-things-that-must-agree)
9. [Rebuild procedures](#9-rebuild-procedures)
10. [Failure modes and how they present](#10-failure-modes-and-how-they-present)
11. [Secrets, credentials and where they live](#11-secrets-credentials-and-where-they-live)
12. [Known open items](#12-known-open-items)
13. [What has been verified live](#13-what-has-been-verified-live-not-just-compiled)

---

## 1. What the system is

Seven Fujitsu Airstage mini-split heads, on two outdoor condensers, heat and cool a house. Home
Assistant owns the devices. A set of HA "helper" entities holds the user's *intent* — which mode
the house is in, which schedule slot it is in, what temperature each room should be in each slot.
An Android app on three wall panels is the front end for those helpers. n8n does all the thinking:
it watches the helpers change, works out what each head should be doing, and writes to the heads
through a single serialised gate so two things never talk to one head at once. A watchdog checks
once a minute that reality matches intent, and suspends a zone's automation if someone has
adjusted it by hand.

**Nothing writes to a head except n8n's HVAC Apply workflow** — with one deliberate exception: the
app's per-zone mode buttons, which are manual control, and which the watchdog then notices and
latches an override for.

The app never talks to n8n. It writes helpers in HA; HA's forwarder automations tell n8n a helper
moved; n8n does the rest. That one-way arrangement is deliberate — the app can be uninstalled from
every panel and the house still runs.

---

## 2. System map

```
                 +----------------------------------------------+
  3 Android      |  Home Assistant   https://ha.stoneyshome.com  |
  wall panels -->|                                              |
  + Android Auto |  helpers (intent)       climate.hp_* (plant)  |
                 |  input_select /         fujitsu_airstage x7   |
                 |  input_boolean /                             |
                 |  input_number                                |
                 +-------+------------------------------+-------+
                         |  state_changed               ^  service
                         |  (2 forwarder automations)   |  calls
                         v                              |
                 +-------+------------------------------+-------+
                 |  n8n              https://n8n.stoneyshome.com |
                 |                                              |
                 |  HVAC Controller --> HVAC Zone Runner -->     |
                 |     (decides)          (orders)       HVAC    |
                 |                                       Apply   |
                 |  HVAC Watchdog ---------------------> (writes)|
                 |     (every minute)                           |
                 |                                              |
                 |  data table: hvac_write_lock (all the locks)  |
                 +----------------------+-----------------------+
                                        |
                                        v
                              Telegram (all alerts)
```

---

## 3. The physical plant

### 3.1 Heads and outdoor units

Two condensers. **A multi-split serves one thermal family at a time.** Heat and cool cannot run on
the same outdoor unit simultaneously. `dry` is refrigeration on these heads, so it belongs to the
*cool* family — heat + dry is a real conflict, not a cosmetic one.

| Zone key | Display name | Head entity | Outdoor unit |
|---|---|---|---|
| `main_level` | Main Level | `climate.hp_living_room` | **1** |
| `main_level` | Main Level | `climate.hp_dining_room` | **2** |
| `bedroom_1` | Master 1 | `climate.hp_bedroom` | 1 |
| `bedroom_2` | Master 2 | `climate.hp_bedroom_2` | 1 |
| `basement` | Basement | `climate.hp_basement` | 1 |
| `anthony` | Anthony | `climate.hp_anthony` | 2 |
| `autumn` | Autumn | `climate.hp_autumn` | 2 |

**Main Level is one open space served by two heads on two different units.** This is the most
awkward fact in the system and the cause of a whole class of bugs:

- It is the only zone touching both condensers, so it can conflict with anything.
- Its two heads are kept mirrored by the Controller. When the mirror fails you get a "split".
- Because the two heads sit on *different* units, the per-unit conflict check **cannot see** a
  split between them. That is why there is a separate split check (watchdog section D), and why
  it only reports rather than switching anything off — shutting the main level down in heating
  season because a mirror failed would be worse than the split.

Unit membership is duplicated in three places and must stay identical: `HvacModels.kt`
(`headOutdoorUnit` / `zoneOutdoorUnits`), the Watchdog's `UNITS` map, and the Controller's
`HEADS` map.

### 3.2 The integration

`fujitsu_airstage`, local mode, **seven separate config entries** — one per head, each titled
`local - <MAC>`:

```
01M2EFRB1RK5JBR4MMSVZWQEZ2   local - 502E912B428F
01M2EFW0KWEXDA0E5E6JEWM8EE   local - 502E912B2B97
01M2EFWCT8V2GJEQ8MXGZEPMTV   local - 502E912B454D
01M2EFZPQ8A0BJAYPTGHWDRVAT   local - 502E912B67B5
01M2EG01Z2GX7GJNF4F4WRCCVY   local - 502E912BAD75
01M2EG0PZXF7Z910KNY5KF5BGJ   local - 502E912B7A7F
01M2EG47P4ENW49ZNTCGFYPYXQ   local - 502E912B8C73
```

### 3.3 What a head actually reports

Live example, `climate.hp_living_room`:

```
state: off
hvac_modes:   [off, cool, heat, fan_only, dry, auto]
min_temp: 60        max_temp: 86        target_temp_step: 0.5
fan_modes:    ["Quiet", "low", "medium", "high", "auto"]      <-- note the casing
swing_modes:  ["Vertical Swing", "Highest", "High", "Low", "Lowest"]
current_temperature: 68      temperature: 73
set_tmp: "230"      <-- the raw register: degrees C x 10, so 23.0 C = 73.4 F
indoor_tmp: "6975"   outdoor_tmp: "5700"   model: ASUH15KPTA
supported_features: 441
```

Two things to notice:

1. **`fan_modes` is mixed case.** `Quiet` is capitalised, the rest lowercase. Hence the
   Controller's `fanPayload()`: `raw === 'Quiet' ? 'Quiet' : raw.toLowerCase()`. The UI helpers use
   Title Case (`Quiet, Low, Medium, High, Auto`), so a conversion is needed in both directions —
   `titleCase()` handles the feedback direction.
2. **`set_tmp` is °C × 10.** This is the whole 61 °F story. See [§8](#8-things-that-must-agree).

---

## 4. Home Assistant

Base URL `https://ha.stoneyshome.com`, LAN fallback `http://10.10.1.116:8123`. Timezone
`America/New_York`. Roughly 1183 entities.

### 4.1 The intent helpers — the heart of the system

Everything the user decides lives in these. n8n reads them; the app writes them. They are the
contract between the two systems.

**House-wide (2):**

| Entity | Options | Meaning |
|---|---|---|
| `input_select.global_hvac_mode` | `heat`, `cool`, `dry`, `off` | what the whole house should do. `off` means *scheduling is paused*, not that nothing may run |
| `input_select.house_schedule_state` | `Day`, `Night`, `Away` | which preset column to read. **Capitalised in HA, lowercased everywhere in code** |

**Per zone — 6 zones × 10 helpers = 60:**

For each of `main_level`, `anthony`, `autumn`, `bedroom_1`, `bedroom_2`, `basement`:

```
input_boolean.zone_enable_<zone>     on = this zone follows the schedule
input_boolean.override_<zone>        on = automation suspended, manual setting held
input_select.<zone>_fan_mode         Quiet | Low | Medium | High | Auto
input_select.<zone>_tilt_mode        Highest | High | Low | Lowest | Vertical Swing
input_number.<zone>_day_temp         heating setpoint, Day
input_number.<zone>_night_temp       heating setpoint, Night
input_number.<zone>_away_temp        heating setpoint, Away
input_number.<zone>_day_cool         cooling setpoint, Day
input_number.<zone>_night_cool       cooling setpoint, Night
input_number.<zone>_away_cool        cooling setpoint, Away
```

**The suffix rule, which is subtle and duplicated:** the `_cool` helpers are used **only** for
`cool`. `dry` reads the `_temp` (heating) number. This is not elegant, it is just what n8n does,
and the app copies it deliberately so the two cannot correct each other back and forth. See
`presetHelperId()` and `scheduledSetpoint()` in `HvacModels.kt`, the `suffix` variable in the
Controller's `Compute Sequencer Actions`, and the same in the Watchdog's `Evaluate`.

### 4.2 The HA → n8n bridge

Several `rest_command`s (defined in `configuration.yaml`, not readable through the API) POST to
n8n webhooks with a shared secret in an `x-ha-token` header:

| rest_command | n8n webhook |
|---|---|
| `rest_command.n8n_hvac_sequencer` | `POST https://n8n.stoneyshome.com/webhook/hvac-sequencer` |
| `rest_command.n8n_hvac_fan_tilt` | `POST https://n8n.stoneyshome.com/webhook/hvac-fan-tilt` |
| `rest_command.n8n_hvac_notify` | `POST https://n8n.stoneyshome.com/webhook/hvac-notify` |
| `rest_command.n8n_water_heater` | `POST https://n8n.stoneyshome.com/webhook/water-heater` |
| `rest_command.n8n_home_event` | `POST https://n8n.stoneyshome.com/webhook/home-events` |

> The shared secret is **deliberately not written down here**. It is the `rightValue` of the
> `Verify … Token` IF node in each n8n workflow, and in the `headers:` block of the rest_command
> in HA's `configuration.yaml`. Both must match. If it is ever rotated, change it in **both**
> places — the IF node's false branch goes nowhere, so a mismatch drops every event silently with
> no error anywhere.

**`automation.hvac_n8n_sequencer_forwarder`** — *"HVAC - n8n Sequencer Forwarder"*, mode
`parallel` max 20. Triggers:

- `homeassistant.start`
- any of the 2 house helpers, or the 12 `zone_enable` / `override` booleans — no debounce
- any of the 36 setpoint `input_number`s — `for: 3s`
- `climate.hp_living_room` / `climate.hp_dining_room` **state** — `for: 2s` (twin-head mirror)
- those same two heads' **`temperature`** attribute — `for: 2s`
- **any** of the seven heads returning from `unavailable`/`unknown` — `for: 30s`, sent as
  `recovered:<entity_id>`

Payload is `{"entity_id": "<evt>"}`. On a non-200 it waits 20 s, retries once, then pushes a phone
notification tagged `n8n-hvac-down`.

**`automation.hvac_fan_tilt_forwarder_n8n`** — same shape. Triggers on the 12 fan/tilt selects (no
debounce), all seven heads' `fan_mode` and `swing_mode` attributes (`for: 2s`), plus recovery.

Both carry a condition that drops transitions *from* `unavailable`/`unknown` on the attribute and
twin-head triggers, so an HA restart does not stampede n8n.

Two more forwarders exist for the non-HVAC workflows:
`automation.home_n8n_presence_forwarder` (person arrivals/departures → Home Events) and
`automation.water_heater_n8n_forwarder` (water heater select, schedule state, people → Water
Heater).

### 4.3 Retired HA automations — LEAVE THESE OFF

These were the original implementation, before n8n. They are all **disabled**. If any one is
re-enabled it becomes a second writer fighting n8n, and the symptom is heads flapping between two
values with no obvious cause:

```
automation.gemini_hvac_master_sequencer_6              HVAC - Master Sequencer              off
automation.gemini_hvac_expected_vs_actual_watchdog_2   HVAC - Manual Override Watchdog      off
automation.gemini_hvac_head_unavailable_notifier       HVAC - Head Unavailable Notifier     off
automation.gemini_hvac_main_level_bi_directional_sync  HVAC - Main Level Bi-Directional Sync off
automation.gemini_hvac_ui_fan_and_tilt_controller      HVAC - UI Fan & Tilt Controller      off
automation.gemini_hvac_ui_feedback_sync_fan_tilt       HVAC - UI Feedback Sync (Fan & Tilt) off
automation.gemini_hvac_counter_reaper_boot             HVAC - Busy Counter Reset (Boot)     off
automation.gemini_hvac_dead_man_reaper                 HVAC - Busy Counter Dead-Man Reaper  off
automation.hvac_engine_override_recovery_11            HVAC - Manual Override Recovery      off
```

Only these two are **on**: `automation.hvac_n8n_sequencer_forwarder` and
`automation.hvac_fan_tilt_forwarder_n8n`.

They are kept rather than deleted because they are the fallback if n8n is lost entirely. To fall
back: turn the two forwarders off, turn the nine above on.

### 4.4 Everything else the app reads

**Room temperatures** (`roomSensors` in the layout config):

```
living_room   sensor.living_room_temperature
dining_room   climate.hp_dining_room        attribute current_temperature
upstairs      climate.upstairs              attribute current_temperature
bedroom       sensor.bedroom_temperature
basement      climate.basement_thermostat   attribute current_temperature
```

`hideRoomSensorIds: [living_room, dining_room, basement]` keeps rooms that already appear as zone
cards out of the secondary strip, so one room never shows two different numbers on one screen.

**Home status:** outdoor `sensor.hp_living_room_outdoor_temperature`; humidity
`sensor.dining_room_humidity` and `sensor.upstairs_humidity`; presence `person.chris`,
`person.jessy`, `person.anthony`, `person.autumn`, `person.ashley`, `person.terri` with a 30-day
history window (matched to the recorder's `purge_keep_days` — querying further back just reports
"no change recorded" for everyone).

**Garage doors — use the cover entities, never the wired sensors.**
`cover.konnected_d332ec_garage_door` (South) and
`cover.garage_garage_door_north_garage_door` (North). The matching `binary_sensor.*_wired_sensor`
entities report **the opposite** of the door: `on` while the cover reads `closed`. Using them
showed both doors permanently open.

**Water heater:** `input_select.water_heater_mode` (`eco`, `heat_pump`, `high_demand`),
`sensor.heat_pump_water_heater_available_hot_water`,
`binary_sensor.heat_pump_water_heater_running`.

**Quick actions:** `script.nightly_shutdown` ("Goodnight"), `script.global_house_shutdown`
("House off").

**Lights (9), switches (5), covers (3), pool (20 entities), solar (18 entities)** — the current
list is in `layout_config.json`, which is the authoritative inventory. All of it is OTA-editable,
so that file is the source of truth rather than any Kotlin default.

**Alarm (planned, not yet built in HA):** `alarm_control_panel.alarmo` plus 8 sensors — the two
garage covers, `binary_sensor.mudroom_motion_motion_2`,
`binary_sensor.third_reality_inc_3rms16bz_motion`, and four Ecobee occupancy sensors
(`binary_sensor.living_room_occupancy`, `.dining_room_occupancy`, `.upstairs_occupancy`,
`.bedroom_occupancy`). Alarmo is not installed yet.

---

## 5. n8n

`https://n8n.stoneyshome.com`. Project `0n8vh9iQjkOjQ5bj`. Credentials used throughout:
Home Assistant account `2i5NAqpKqoJI2ydI`, Telegram account `g3BAkFvsxgdUVwyX`.

### 5.1 Workflow inventory

| Workflow | ID | Active | Role |
|---|---|---|---|
| **HVAC Controller (n8n)** | `xUHRrkXfO8QpSVek` | yes | decides what every head should do |
| **HVAC Zone Runner** | `5Bqk9zaTeSy9Ft17` | yes | applies one head's actions strictly in order |
| **HVAC Apply (lock + throttle)** | `Knp3J2y75lmowdCm` | yes | the only thing that writes to a head |
| **HVAC Watchdog (n8n)** | `u9GjcXPRdD6M5pkY` | yes | once a minute: drift, conflict, split, unavailable |
| **HVAC Notify (n8n)** | `suV4FPYw3NwlrGSC` | yes | webhook → Telegram (HA has no telegram_bot) |
| HVAC Bot (n8n) | `LE8HFA7HW9SQ29na` | yes | Telegram bot for HVAC control |
| HA Bot (n8n) | `jyl5o2h8SME5p42x` | yes | Telegram bot for general HA control |
| Home Events (n8n) | `j54EV1P4rgFMbMKs` | yes | away/arrival routines, sets `house_schedule_state` |
| Water Heater (n8n) | `U8Qlg8378LOUBstx` | yes | water heater UI, schedule sync, shower prep |
| n8n Error Alerts (Telegram) | `h1zqYDokCpba1I2q` | yes | shared error handler, 1 alert / workflow / 10 min |
| Network - Nabu Casa Watchdog | `Nxjr39xlsfuOtNWZ` | no | remote-access up/down alerts |
| HVAC Sequencer (n8n) | `6H3mKRYWWIOX7YLG` | **no** | superseded by HVAC Controller |
| HVAC Fan & Tilt (n8n) | `Yq4HN4bGtt7x4T7V` | **no** | superseded by HVAC Controller |
| HVAC Migration Notifier | `dBQr7EvELA6g1FRa` | no | manual-only milestone messages |
| Notify Me (Telegram) | `ognVshx4LaK65dwa` | no | generic one-way notifier |

The four core HVAC workflows all set `errorWorkflow: h1zqYDokCpba1I2q`.

### 5.2 Data tables

**`hvac_write_lock`** — id `ufrztPiFJxunjEew`. Everything in n8n coordinates through this one
table. Columns:

| Column | Type | Meaning |
|---|---|---|
| `lock_key` | string | which lock this row is |
| `owner` | string | the token of whoever holds it; empty = free |
| `expires_at` | number | epoch ms; a row is free when this is in the past |
| `last_write_at` | number | when we last wrote mode/temp to this entity (the echo gate) |
| `last_fan_write_at` | number | same, for fan/tilt writes |
| `echo_probe` | number | side effect of the Controller's gate check; diagnostic only |

Ten rows, and they must all exist — an absent row means the lock can never be acquired:

```
airstage_write                    (legacy house-wide lock, still used for non-head entities)
run_mutex                         (the Controller's one-run-at-a-time gate)
entity:climate.hp_living_room
entity:climate.hp_dining_room
entity:climate.hp_anthony
entity:climate.hp_autumn
entity:climate.hp_bedroom
entity:climate.hp_bedroom_2
entity:climate.hp_basement
home_mutex                        (Home Events workflow)
```

**`alert_throttle`** — id `6a3htTi5b3fDJxbp`, columns `alert_key`, `last_sent_at`. Used by the
shared error handler.

**`nabu_casa_watchdog_state`** — id `pjBqRqaNOmqO5gVO`, columns `state_key`, `status`,
`changed_at`.

### 5.3 HVAC Controller — `xUHRrkXfO8QpSVek`

50 nodes. Two webhooks, one serialised pipeline. Execution timeout 900 s.

**Flow:**

```
Sequencer Webhook  --> Verify Sequencer Token --> Tag Sequencer Event  --\
Fan Tilt Webhook   --> Verify Fan Tilt Token  --> Tag Fan Tilt Event   --+--> Init Run
                                                                            |
  Valid Event? --> Check Recent Self-Write --> Recent Self-Write? --(yes)--> Skipped
                                                     |
                                                    (no)
                                                     v
                                          Try Acquire Run Mutex --> Got Mutex?
                                                     |                  |
                                                    (no)               (yes)
                                                     v                  v
                                       Mutex Wait Expired?        Get All HA States
                                        |          |                    |
                                   Notify     Wait 1s -> retry    Check State Fetch Health
                                                                        |
                                                            Is Fan Tilt Event?
                                                              /              \
                                              Compute Fan Tilt Actions   Compute Sequencer Actions
                                                              \              /
                                                             Any Actions To Run?
                                                                     |
                                                            Attach Mutex Token
                                                                     |
                             8 parallel lanes: Lane Filter -> Lane Has Actions? -> Dispatch Zone
                             (living room, dining room, anthony, autumn, bedroom,
                              bedroom 2, basement, misc)
                                                                     |
                                                        Merge HVAC Apply Lanes (append, 8 inputs)
                                                                     |
                                                            Release Run Mutex
```

**`Init Run`** builds the run token (`<executionId>-<now>-<random>`), a 600 s deadline, and
validates the entity id against one regex. Anything not matching is dropped:

```
^(recovered:)?(climate\.hp_[a-z0-9_]+
  |input_select\.(global_hvac_mode|house_schedule_state|[a-z0-9_]+_(fan|tilt)_mode)
  |input_boolean\.(zone_enable|override)_[a-z0-9_]+
  |input_number\.[a-z0-9_]+_(day|night|away)_(temp|cool)
  |homeassistant\.start)$
```

**`Check Recent Self-Write`** is the **echo gate**. It tries a data-table update on
`entity:<head>` where `last_write_at > now - 15000`. If a row matches, we wrote to that head
within the last 15 s, so this event is our own settle-echo and the run is abandoned. Without it,
our own write comes back as a state_changed, gets mirrored to the twin head, which echoes back,
and the main level ping-pongs — that was the 40-minute split on 2026-09-20.

**`Try Acquire Run Mutex`** is a compare-and-swap: update the `run_mutex` row where
`expires_at < now`, setting `owner` to our token and `expires_at` to `now + 150000`. The data
table's atomic update is what makes it a real mutex — if a row comes back we won, if nothing comes
back someone else holds it. Waits 1 s and retries until the 600 s deadline.

**`Check State Fetch Health`** throws if the HA state fetch returned fewer than 20 entities.
Acting on a truncated state map would turn heads off because they "aren't running".

**`Compute Sequencer Actions`** — the core decision code. In order:

1. If the trigger is a main-level head (and not a recovery event), **mirror** mode and temperature
   to the twin and stop. Skipped entirely if either head is unavailable.
2. Read `global_hvac_mode` and `house_schedule_state`. If either is not a legal value, do nothing.
3. Classify the trigger: schedule / global / zone_enable / override / setpoint / start / recovered.
   Anything else is not a sequencer trigger — do nothing. `isStructural` = everything except a
   bare setpoint change.
4. Narrow the target zones: a recovery event targets only that head's zone; a setpoint,
   zone_enable or override event targets only its own zone; everything else targets all six.
5. For each target zone:
   - Zone disabled → only a *schedule* change clears its override; otherwise skip.
   - Override on → a schedule change, or a zone_enable change for this zone, clears it; otherwise
     skip the zone entirely.
   - Work out the target temperature from `input_number.<zone>_<sched><suffix>`, where suffix is
     `_cool` for cool and `_temp` for everything else, then clamp: `COOL_FLOOR = 64.5` for
     cool/dry, `HEAT_FLOOR = 61` otherwise. Values outside 40–100 are rejected as implausible.
   - For each head in the zone: if structural and the head is off and global isn't off, emit
     `turn_on`; if the head's mode differs from global, emit `set_hvac_mode`; if global isn't off
     and the head's temperature differs by more than `TEMP_EPS = 0.5`, emit `set_temperature`.

Every action carries its own verification contract:

```js
turn_on   -> verify_type: 'not_off'                                        gap 2s
set_mode  -> verify_type: 'state_eq',  verify_value: mode                  gap 2s
set_temp  -> verify_type: 'attr_num',  attr 'temperature', tolerance 0.6   gap 1s
override  -> verify_type: 'state_eq',  verify_value: 'off'                 gap 0s
```

**`Compute Fan Tilt Actions`** handles three separate jobs:

1. A UI select changed → push it to that zone's heads (checking the value is in the head's own
   `fan_modes` / `swing_modes` first).
2. A head recovered from unavailable → re-apply the UI selections, because the UI is the desired
   state.
3. Main-level mirror for fan and tilt.
4. **Feedback sync** — push a head's actual fan/swing back into its UI select, so a change made on
   the remote shows up in the app. Guarded three ways: the value must exist as an option, the
   helper must not have changed in the last 5 s, and it is suppressed for 20 s after *we* changed
   that head's mode (`sd.lastModeChangeAt`, shared with the sequencer code via workflow static
   data). Without that last guard, a head's own post-mode-change swing reset looks like a manual
   change and silently overwrites what the user picked.

**The eight lanes.** `Attach Mutex Token` fans out to eight `Lane Filter` code nodes, one per head
plus a "misc" lane for everything that isn't a `climate.hp_*` entity. Each lane keeps only its own
actions, then dispatches them to the Zone Runner. This is what makes different heads run
concurrently while each head's own actions stay strictly ordered.

### 5.4 HVAC Zone Runner — `5Bqk9zaTeSy9Ft17`

Two nodes, and that is the whole point of it.

```
Zone Runner Trigger (passthrough)  -->  Apply Action (ordered)
                                        Execute Workflow: HVAC Apply
                                        mode: each
                                        waitForSubWorkflow: true
```

`mode: each` plus `waitForSubWorkflow: true` is what preserves
**turn_on → set_hvac_mode → set_temperature** for one head. The Controller calls the Zone Runner
*non-blocking* (one call per head), so heads run in parallel with each other while each head's own
sequence stays in order.

### 5.5 HVAC Apply — `Knp3J2y75lmowdCm`

30 nodes. **One action per run.** This is the only thing that writes to a head, and it is the
reason nothing collides.

```
Called By HVAC Workflows --> Init Action --> Has Mutex Token? --(yes)--> Renew Run Mutex --\
                                                    |                                      |
                                                   (no) ------------------------------------+
                                                                                            v
                                                                              Try Acquire Lock
                                                                                     |
                                                                                Got Lock?
                                                                            /            \
                                                                        (yes)            (no)
                                                                          |                |
                                                                 Read Live State    Lock Wait Expired?
                                                                          |            /        \
                                                             Decide If Write Needed  Notify   Wait 1s
                                                                          |                   -> retry
                                                                   Write Needed?
                                                                   /          \
                                                                (yes)        (no)
                                                                  |            |
                                                        Send Command To HA   Release Lock No Write
                                                                  |
                                                          Stamp Write Time   <-- the echo gate
                                                                  |
                                                        Start Confirm Window (15s)
                                                                  |
                                                          Poll State --> Evaluate Confirmation
                                                                  |
                                                        Confirmed Or Timed Out?
                                                            /            \
                                                        (yes)          (no) --> Wait 1s --> Poll
                                                          |
                                                  Unconfirmed Write? --> Notify
                                                          |
                                                  Hold Minimum Gap (gap_s)
                                                          |
                                                  Release Lock After Write
```

**The input contract** — this is what any caller must send:

```js
{
  entity_id:    "climate.hp_bedroom",
  domain:       "climate",
  service:      "set_temperature",
  attr_name:    "temperature",        // defaults to "entity_id" if absent
  attr_value:   "68",
  verify_type:  "attr_num",           // not_off | state_eq | attr_eq | attr_num
  verify_attr:  "temperature",        // for attr_eq / attr_num
  verify_value: 68,
  tolerance:    0.6,                  // for attr_num, default 0.4
  zone:         "bedroom_1",
  source:       "sequencer",          // "fan_tilt" routes the timestamp differently
  gap_s:        1,                    // hold after the write, default 1
  mutex_token:  "<the Controller's run token>"   // optional
}
```

**Key behaviours, and why:**

- **The lock key** is `entity:<entity_id>` for `climate.hp_*` and the legacy `airstage_write` for
  anything else. Per-head locking is what allows seven heads to be written at once.
- **`Renew Run Mutex`** extends the Controller's mutex by 150 s whenever a `mutex_token` is
  supplied, so a long sweep does not lose its mutex mid-flight.
- **`Decide If Write Needed`** re-reads live state under the lock and skips the write if the
  entity already satisfies the contract. It also captures `about_to_send_at` **before** the HA
  call. That matters: `Send Command To HA` has been observed taking 6.1 s, and HA's resulting
  `last_changed` was 1.75 s *earlier* than a timestamp taken after the call returned. Stamping
  from before the call puts the echo window's start where it belongs.
- **`Stamp Write Time`** writes `last_write_at` (or `last_fan_write_at` when
  `source === 'fan_tilt'`) immediately after the send. It used to be written by
  `Release Lock After Write` — after the confirm window and the minimum gap — so the echo gate
  opened 3–17 s late and often never opened at all. Verified fixed on execution `7870`:
  `about_to_send_at` 18:44:40.912, row stamped 18:44:45.531, run ended 18:44:48.272, echo lands
  ~:47.
- **The confirm window** is 15 s, polled every second. An unrecognised `verify_type` returns
  *not satisfied*, not satisfied-by-default — it used to return true, which meant a typo in a
  verify type produced a write that was declared confirmed without checking anything.
- **Unconfirmed writes** (HA accepted the call but the head never changed) raise a Telegram alert,
  throttled per `entity|service|value` to one per 15 minutes via workflow static data.

### 5.6 HVAC Watchdog — `u9GjcXPRdD6M5pkY`

9 nodes, schedule trigger **every minute** (America/New_York). Read-only on the lock table.

```
Every Minute --> Get All HA States --> Check State Fetch Health --> Get Write Lock --> Evaluate
                                                                                          |
                                                                                  Call HA Service
```

`Evaluate` is one large code node with four independent sections. All of its timers live in
workflow static data (`sd.driftSince`, `sd.conflictSince`, `sd.notified`, `sd.selfOffAt`,
`sd.splitSince`, `sd.splitNotifiedAt`, `sd.lastEvalAt`).

**Constants:**

```js
DRIFT_HOLD_MS       45000     // 45s of continuous difference before acting
CONFLICT_HOLD_MS    45000
SPLIT_HOLD_MS      115000
SPLIT_RENOTIFY_MS 1800000     // 30 min
RECENT_WRITE_MS     30000
UNAVAILABLE_HOLD_MS 60000
STALE_RUN_MS       180000
TEMP_TOLERANCE         0.6
COOL_FLOOR            64.5
HEAT_FLOOR            61      // must equal the Controller and the app
SELF_OFF_GRACE_MS  180000
MAX_LOCK_TTL_MS    300000     // a lock claiming longer than this is corrupt, not held
```

Because the tick is 60 s, **any hold below 60 s behaves the same**: seen on one tick, acted on at
the next. Expect roughly two minutes of wall-clock, not 45 seconds.

**Skip conditions** — evaluation is paused when global mode or schedule state is unreadable, when
a lock or the run mutex is held, or when anything was written in the last 30 s. A lock/recent-write
skip is *transient*: the per-zone drift timers are preserved, so a genuinely drifting zone doesn't
get a fresh grace period every time the house is busy elsewhere. An invalid global/schedule state,
or a gap longer than `STALE_RUN_MS`, clears every timer.

**`sane(ts)`** rejects any timestamp more than 5 s in the future. A future-dated row makes
`now - ts` negative, which satisfies every `<= WINDOW` test forever — one bad row used to pin
`recentWrite` true and silently disable sections A, C and D permanently, with no alert and nothing
to notice.

**Section C — cross-family conflict, per outdoor unit.** Runs *first*, so a head about to be
turned off is not also flagged as a manual override. Each unit's required family comes from the
global mode; when global is `off`, each unit decides for itself, led by its own main-level head
(`unit1` → living room, `unit2` → dining room). A head in the wrong family for 45 s gets its
**whole zone** turned off — explicitly, every head, so main level doesn't race with its own mirror
— and `sd.selfOffAt[head]` is stamped. A write to *that specific head* landing after the timer
started resets it, because the sequencer is still reconciling.

**Section D — main-level split.** The two main heads sit on different units, so section C can't
see a disagreement between them. After 115 s of split it sends one Telegram, re-notifying at most
every 30 minutes. **Nothing is switched off.**

**Section A — manual-override drift.** For each enabled zone with its override off, compare every
head against what the app asks for: mode (including a head somebody switched *off*) and setpoint,
clamped to the same floors the Controller uses. Unavailable heads are section B's business —
latching an override on a wifi dropout would suspend a zone for something that isn't a manual act
at all. A head section C turned off within `SELF_OFF_GRACE_MS` is excused. After 45 s of
continuous difference, `input_boolean.override_<zone>` is turned on and one combined Telegram is
sent for the whole tick.

**Section B — head unavailable.** Always runs, even when the others are skipped. One coalesced
message per tick after 60 s of unavailability, deduplicated on the entity's `last_changed`.

**Vane and fan are deliberately not checked here.** The Controller's fan/tilt lane copies a head's
actual values back into its UI select, so a manual change reconciles within seconds rather than
being overridden.

All alerts leave through `rest_command.n8n_hvac_notify` → the HVAC Notify workflow → Telegram,
because HA has no `telegram_bot` integration — the bot credential lives in n8n.

### 5.7 HVAC Notify — `suV4FPYw3NwlrGSC`

Three nodes: `Notify Webhook` (POST `/webhook/hvac-notify`) → `Verify Token` → `Send Telegram`.
Body is `{title, message}`. This exists purely because HA cannot send Telegram itself.

### 5.8 Supporting workflows

- **Home Events** (`j54EV1P4rgFMbMKs`, 33 nodes) — `POST /webhook/home-events` plus a
  `Reconcile Every 5 Min` schedule trigger. Replaces the HA "Eco Mode" and "Home Arrival"
  automations: sets `input_select.house_schedule_state` and runs the light/switch away/arrival
  routines, with Telegram notifications. Uses the `home_mutex` lock row.
  **This is the upstream of the whole HVAC schedule** — if the house stops changing between
  Day/Night/Away, look here first, not at the HVAC workflows.
- **Water Heater** (`U8Qlg8378LOUBstx`, 25 nodes) — `POST /webhook/water-heater` plus
  `Daily 18:00 Trigger` and `Daily 20:00 Trigger` (America/New_York). Replaces the HA water heater
  UI controller, schedule sync, evening shower prep and apply-schedule-mode script. Owns
  `input_select.water_heater_mode`.
- **HVAC Bot** (`LE8HFA7HW9SQ29na`) and **HA Bot** (`jyl5o2h8SME5p42x`) — Telegram bots, owner
  only, with confirm buttons on sensitive actions. They write the same helpers the app does.
- **n8n Error Alerts** (`h1zqYDokCpba1I2q`) — set as `errorWorkflow` on the HVAC workflows. One
  Telegram per workflow per 10 minutes, throttled via the `alert_throttle` data table.

### 5.9 Editing n8n — read `N8N-EDITING.md` first

Three things can stop an n8n change taking effect, and two of them look like success:

1. **Auto mode silently refuses the write.** Fixed by explicit allow rules in
   `.claude/settings.local.json` for `update_workflow` and `publish_workflow`. Claude cannot add
   those itself. If a write is refused, **say so** rather than quietly handing over a file.
2. **`update_workflow` only writes the DRAFT.** Always call `publish_workflow` afterwards and
   confirm with a fresh read that `activeVersionId === versionId` and
   `activeVersion.sameAsDraft === true`.
3. **The connector ID can change** if the connector is reinstalled, which makes the allow rules
   stop matching and looks exactly like problem 1.

To verify a Watchdog change is really live: the schedule trigger fires at **31 seconds past the
minute**. A helper written at `:31` is the recurring schedule, not something done by hand.

---

## 6. The Android app

### 6.1 Identity

```
applicationId  com.aistudio.hvac_controller.krnyzs
MainActivity   com.example.MainActivity
namespace      com.example
versionCode 13   versionName "13.0"
minSdk 24        targetSdk 36     compileSdk 36.1
repo           cstone1983/HVAC-Android-App  (branch main)
```

Kotlin + Jetpack Compose (Material 3), Retrofit + Moshi + OkHttp, Room, Robolectric/Roborazzi for
tests. Secrets come from `.env` via the Secrets Gradle Plugin into `BuildConfig`
(`HA_URL`, `HA_BACKUP_URL`, `HA_TOKEN`, `GITHUB_TOKEN`).

### 6.2 File map

| File | Lines | What it is |
|---|---|---|
| `ui/HvacDashboard.kt` | 7477 | the entire wall UI: tabs, zone cards, popups, settings, updates |
| `viewmodel/HvacViewModel.kt` | 4178 | all state and every command path |
| `ui/SolarDashboardView.kt` | 2182 | solar tab |
| `ui/PoolDashboardView.kt` | 1325 | pool tab |
| `ui/HomeStatus.kt` | 966 | alert strip, humidity, presence, quick actions, conflict dialog |
| `model/HvacModels.kt` | 905 | **the shared logic**: config schema, zones, families, floors, alarm |
| `api/HomeAssistantWebSocketManager.kt` | 794 | live state over WebSocket |
| `ui/Placeholders.kt` | 716 | dynamic-section card renderers |
| `ui/AlarmView.kt` | 648 | the (hidden) security tab and keypad |
| `car/CarHaRepositoryHelper.kt` | 631 | Android Auto data + commands |
| `service/HvacForegroundService.kt` | 574 | persistent notification, keeps the socket alive |
| `car/HomeScreen.kt`, `ZonesListScreen.kt` | 411 | Android Auto screens |

Tests: `app/src/test/java/com/example/` — 83 tests, 0 failures.
`AlarmStateTest` (16), `ModeConflictTest`, `ClimateZoneStatusTest`, `ScheduledSetpointTest`,
`OutdoorUnitTest`, `HvacFamilyTest`, `PresenceTimeTest`.

### 6.3 `layout_config.json` — the OTA contract

**This is the most important file in the app.** Two copies exist and must stay identical:

```
/layout_config.json                       what panels download from GitHub
/app/src/main/assets/layout_config.json   what a fresh install starts with
```

Current version `6.5.1`. It defines: tabs, theme colours, zones (entity ids, presets, override and
enable booleans), room sensors, lights, switches, covers, pool and solar entity maps, home status,
limits, the alarm block, and the idle timings.

**Everything in it is changeable without rebuilding the app.** Push a change to `main` and the
panels offer it on their next check; tap the banner and it applies. This is why `AGENTS.md` insists
that entities, titles, colours and thresholds are never hardcoded in Kotlin.

Structure worth knowing:

```jsonc
{
  "version": "6.5.1",
  "limits":  { "minCoolingTemp": 64.0, "maxHeatingTemp": 80.0 },
  "theme":   { "accentColorHex": "#10B981", "heatColorHex": "#F59E0B", ... },
  "tabs":    [ { "id", "title", "icon", "sections": [...], "shortTitle", "hidden" } ],
  "zones":   [ { "key", "name", "climateEntityId", "secondaryClimateEntityId",
                 "autoEntityId", "overrideEntityId", "tiltEntityId", "fanEntityId",
                 "presetsHeat": {day,night,away}, "presetsCool": {day,night,away} } ],
  "roomSensors": [...], "lights": [...], "switches": [...], "covers": [...],
  "poolSensors": {...}, "solarSensors": {...},
  "homeStatus": { "outdoorTempEntityId", "humiditySensors", "presenceEntityIds",
                  "doorAlerts", "quickActions", "hideRoomSensorIds", "presenceHistoryDays" },
  "alarm":   { "entityId", "sensors": [ {entityId, name, type?} ] },
  "idleReturnSeconds": 20, "popupTimeoutSeconds": 20
}
```

**Tabs and sections.** A tab lists section ids; the renderer switches on them:
`sensors`, `zones`, `condensed_power`, `solar`, `pool`, `alarm`, `updates`. Current tabs are
HOME (`sensors`+`zones`), POWER, SOLAR, POOL, **ALARM (`"hidden": true`)**, SYSTEM.
`"hidden": true` withholds only the nav button — the code still ships. Flip it to `false`, push,
and the panels pick it up with no rebuild.

### 6.4 The ViewModel

One `HvacViewModel` owns everything. State reaches the UI as `StateFlow`s; commands go out as HA
service calls.

**State sources, in order of preference:** WebSocket `state_changed` events → REST `/api/states`
poll → cached SharedPreferences. `processStatesMap()` is the single funnel that turns a raw state
map into `HvacUiState.Success`, and it is the only writer of `lastNonOffHvacMode`.

**Token resolution** — `resolveHaToken()`. This was the single worst bug in the project:

```kotlin
private fun resolveHaToken(): String {
    val build = try { com.example.BuildConfig.HA_TOKEN } catch (e: Exception) { "" }
    if (build.isNotEmpty() && build != "YOUR_HOME_ASSISTANT_TOKEN") return build
    return sharedPrefs.getString("ha_token", null).orEmpty()
}
```

The old code was `sharedPrefs.getString("ha_token", "") ?: ""`. `getString` with a non-null default
**never returns null**, so the `?: BuildConfig.HA_TOKEN` fallback was dead code and every
`if (token.isNotEmpty())` guard failed. **The app had never once opened its WebSocket in
production.** REST covered for it, so nothing looked broken — but `awaitHeadState` falls back to a
fixed 1.5 s sleep when the socket is down, which quietly disabled the turn_on-before-mode ordering.
Eight call sites were fixed.

**`setZoneHvacMode()`** — the app's one direct write to a head. Per-head mutex plus supersede:

```kotlin
latestRequestedMode[climateEntityId] = target          // newest tap wins
val lock = zoneCommandLocks.getOrPut(climateEntityId) { Mutex() }
viewModelScope.launch { lock.withLock {
    if (latestRequestedMode[climateEntityId] != target) return@withLock   // superseded
    if (isNotReporting(current)) { feedback("not reporting"); return@withLock }
    if (target != "off" && current == "off") {
        val powered = withContext(NonCancellable) { performServiceCall("climate","turn_on",...) }
        if (!powered) { feedback("FAILED"); return@withLock }
        awaitHeadState(climateEntityId) { it != "off" }
    }
    // set_hvac_mode, checked
    scheduleSetpointFor(...)?.let { wanted ->
        if (!awaitHeadState(climateEntityId) { it == target }) { ...; return@withLock }
        // set_temperature
    }
}}
```

`awaitHeadState` waits up to 15 s for the WebSocket to confirm, falling back to a fixed sleep if
the socket is down. The ordering **turn_on → set_hvac_mode → set_temperature** is not optional: a
sleeping head rejects a mode change, and a head still in the old mode applies the setpoint to the
wrong family.

**The conflict guard** — `findModeConflict()` in `HvacModels.kt`, pure and unit-tested. Three
rules: the house mode outranks everything (but when it *agrees* with the request, a stray head must
not veto the corrective action, or a house and a zone that disagree can never be brought back into
line); only heads on the same condenser can conflict; the zones named as affected are the ones an
override would actually move. Returns null when the request is fine. This is a courtesy check for
immediate feedback — **the n8n watchdog is the real enforcement.**

Snapshots are taken **per head**, not per zone, so a main level where the dining head is the one
committed to a family is seen correctly:

```kotlin
zones = state.zones.flatMap { zone ->
    listOfNotNull(
        ZoneModeSnapshot(zone.key, zone.name, zone.currentHvacMode),
        zone.secondaryHvacMode?.let { ZoneModeSnapshot(zone.key, zone.name, it) }
    )
}
```

**Other command paths:** `selectGlobalHvacMode`, `selectHouseSchedule`, `selectWaterHeaterMode`,
`toggleInputBoolean` (enable/override), `selectVaneMode` / `selectFanMode`, `setTargetTemperature`,
`setPresetTemperature`, `toggleZonePower`, `toggleLight` / `setLightBrightness`, `toggleSwitch`,
`controlCover`, `applyHouseModeOverride`, `sendHvacNotification`, `submitAlarmCommand`.

### 6.5 WebSocket

`HomeAssistantWebSocketManager`, singleton. `auth_required` → `auth` → `auth_ok` → `get_states`
baseline → `subscribe_events` (`state_changed`). Ping every 15 s with a 5 s timeout; backoff 1–10 s;
failover to the backup URL after 2 consecutive failures; full reconcile every 5 minutes.

`@Volatile authRejected` is set on `auth_invalid` and honoured in `onClosed` / `onFailure` /
`scheduleReconnect`. Without it a bad token produced an unbounded reconnect loop — thousands of
failed logins per panel per day against HA's auth endpoint, which trips `ip_ban`.

### 6.6 Android Auto

`HvacCarAppService` → `HvacCarSession` → `HomeScreen` / `ZonesListScreen`, data and commands via
`CarHaRepositoryHelper`. It is a **second copy** of the command logic, which is a standing risk —
it has drifted from the phone before. It now shares `findModeConflict`, `presetHelperId`,
`clampToHardwareFloor` and `headZoneKey` with the app rather than reimplementing them, has its own
`awaitHeadState`, and reports "FAILED - not sent" honestly instead of always claiming success.

### 6.7 Foreground service

`HvacForegroundService` holds a WiFi lock and shows a persistent notification with live zone
temperatures and HEAT / COOL / OFF actions. It reschedules itself every five minutes if killed.

### 6.8 Updates

Two completely separate paths:

**Layout (works, this is the one that matters).** `checkForUpdates` reads
`api.github.com/repos/<repo>/commits/<branch>`; applying downloads
`raw.githubusercontent.com/<repo>/<sha>/layout_config.json` and stores the **raw bytes**, not a
re-serialized parse — re-serializing silently dropped every key the running build didn't know
about. It writes `layout_config_json`, `layout_version` and `layout_commit_sha`, and deliberately
does **not** touch the displayed app version.

**APK (does not work).** `.github/workflows/android-release.yml` runs `assembleDebug` on every push
to `main` and attaches `app-debug.apk` to a rolling `v1.0` release. **CI mints a fresh debug
keystore each run**, so those APKs cannot install over anything — including each other. There is no
working OTA path for code. Fixing it needs a keystore in GitHub secrets plus a one-time
uninstall/reinstall on all three panels.

### 6.9 Deploy targets

Exactly three devices:

```
SM-X200    Galaxy Tab A8    wall panel
SM-X110    Galaxy Tab A9    wall panel
SM-F966U1  Z Fold 7         phone
```

A fourth phone (**SM-N986U1**) is reachable over adb and **must never receive the app**.

adb quirks that cost real time (full detail in `HANDOVER-2026-09-21.md`):

- The Fold needs `adb shell screencap -p /sdcard/x.png` then `pull` — piping `exec-out screencap`
  gets corrupted by a multiple-display warning. Set `MSYS_NO_PATHCONV=1` in Git Bash or
  `/sdcard/...` is rewritten to a Windows path.
- A `while read` loop over `adb devices` only processes the first device, because `adb shell` eats
  stdin. Use `for s in $SERIALS` with `</dev/null` on each call.
- The Fold's wireless-debugging pairing does not survive reboots. Re-pair by reading the pairing
  port from `adb mdns services` (`_adb-tls-pairing._tcp`); the pairing port differs from the
  connect port and both change.
- **Never tap coordinates without taking a fresh screenshot first.** Doing so once set the whole
  house to HEAT: the idle timer had returned the panel to Home, so a tap aimed at a keypad landed
  on the house-mode row. Seven heads came on and all six zones latched into override.

---

## 7. How a single action flows end to end

**Someone taps "Night" on a wall panel.**

1. App → `input_select.select_option` on `input_select.house_schedule_state` = `Night`.
2. HA fires `state_changed`. `automation.hvac_n8n_sequencer_forwarder` matches (no debounce) and
   POSTs `{entity_id: "input_select.house_schedule_state"}` to `/webhook/hvac-sequencer`.
3. Controller: token verified → `Init Run` validates the id → echo gate passes (not a head) →
   acquires `run_mutex` → fetches all HA states → `Compute Sequencer Actions`.
4. Trigger is a schedule change, so `isStructural` is true and **all six zones** are targets. For
   each: clear any override, read `input_number.<zone>_night_<suffix>`, clamp to the floor, and
   emit `turn_on` / `set_hvac_mode` / `set_temperature` per head as needed.
5. Actions fan out into eight lanes. Each non-empty lane calls the Zone Runner **non-blocking**, so
   all seven heads proceed at once.
6. Each Zone Runner walks its head's actions **in order**, each through HVAC Apply: take the
   per-head lock, re-read, write only if needed, stamp `last_write_at`, confirm within 15 s, hold
   the gap, release.
7. Controller releases `run_mutex`.
8. Each write echoes back as a `state_changed`. The forwarder sends it; the Controller's echo gate
   sees `last_write_at` within 15 s and drops it.
9. Within a minute the Watchdog ticks, sees heads matching intent, and does nothing.

A real transition of this shape (Away → Day, kids home at 15:03) completed in one clean Controller
run in 3.8 s with no zones dropping out.

**Someone changes a head on the Fujitsu remote instead.**

1. Head reports a new mode/temperature. The forwarder ignores it (only main-level heads and
   attribute changes are forwarded, and only after a 2 s debounce).
2. Watchdog tick sees the head differs from intent; starts `sd.driftSince[zone]`.
3. Next tick, 45+ s later: turns on `input_boolean.override_<zone>` and sends one Telegram naming
   what changed.
4. The zone is now suspended. The app shows the override.
5. Clearing the override in the app fires the forwarder → Controller → the zone is put back to the
   scheduled settings. Measured: reverted 6 s after clearing.

---

## 8. Things that must agree

**This is the section to read when something is misbehaving.** Every entry here is a value or rule
duplicated across systems. When two copies disagree, the systems take turns correcting each other
and nothing looks broken.

### 8.1 The heating floor — 61 °F

**The single most expensive fact in this project.**

The heads work in 0.5 °C steps and their heating range starts at **16.0 °C**. Home Assistant
advertises `min_temp: 60` because it rounds 60.8 °F down — but it *validates* against the unrounded
16.0 °C. So a write of 60 is **rejected outright** and the head silently keeps its previous
setpoint.

That is worse than a wrong number. The head then reads differently from what the system intended,
the watchdog scores it as drift, and the zone latches into manual override **on every schedule
transition**. Two zones were configured at 60 and had been doing exactly this.

Measured against the real heads on 2026-09-21: **60 rejected, 60.5 rejected, 61 accepted**
(`set_tmp 160`). Fujitsu's own app labels that same rung "60"; HA labels it "61". Same physical
setpoint — nothing is lost by clamping.

| Where | Name | Value |
|---|---|---|
| `model/HvacModels.kt` | `HEAT_FLOOR_F` | `61.0` |
| Controller → `Compute Sequencer Actions` | `HEAT_FLOOR` | `61` |
| Watchdog → `Evaluate` | `HEAT_FLOOR` | `61` |

The cooling floor is **64.5 °F** (18 °C), in the same three places as `COOL_FLOOR_F` / `COOL_FLOOR`.

### 8.2 Other duplicated values

| Thing | App | Controller | Watchdog |
|---|---|---|---|
| Zone → head map | `layout_config.json` `zones[]` + `headZoneKey` | `HEADS` | `ZONES` |
| Head → outdoor unit | `headOutdoorUnit`, `zoneOutdoorUnits` | (implicit in `HEADS`) | `UNITS`, `UNIT_LEAD` |
| Thermal family (`dry` → cool) | `hvacFamilyOf()` | `familyOf()` | `familyOf()` |
| Preset suffix (`_cool` only for cool) | `presetHelperId()`, `scheduledSetpoint()` | `suffix` | `suffix` |
| Temperature tolerance | — | `TEMP_EPS = 0.5` | `TEMP_TOLERANCE = 0.6` |
| Setpoint verify tolerance | — | `tolerance: 0.6` | — |
| Not-reporting states | `NOT_REPORTING_MODES` | `bad()` | `UNAVAILABLE` |

### 8.3 Timing invariants

- **Echo gate 15 s** — the Controller drops any head event whose `last_write_at` is within 15 s.
  The Apply workflow's confirm window is also 15 s, and `Stamp Write Time` runs immediately after
  the send so the window starts before the echo. If the stamp moves later in the flow, the gate
  opens after the echo has already been forwarded and the main level ping-pongs.
- **Run mutex 150 s TTL**, renewed by every Apply call carrying a `mutex_token`. The Controller
  waits up to 600 s to acquire it.
- **Per-head lock 90 s TTL**, acquired per Apply run, with a 90 s acquisition deadline.
- **Forwarder debounces** — 2 s on head/attribute triggers, 3 s on setpoints, 30 s on recovery.
  These coalesce multi-step command bursts.
- **Watchdog tick 60 s**, holds 45 s (drift, conflict) / 115 s (split). Lowering a hold below 60 s
  changes nothing; the tick interval governs response time.

### 8.4 The two copies of `layout_config.json`

`/layout_config.json` and `/app/src/main/assets/layout_config.json` must be identical. The root
copy is what panels download; the assets copy is what a fresh install starts with. A change to only
one produces a device that behaves differently depending on whether it has ever taken an update.

### 8.5 Option casing

- `house_schedule_state` is `Day`/`Night`/`Away` in HA, lowercased in all comparisons.
- Head `fan_modes` are `Quiet` + lowercase; UI helpers are Title Case. `fanPayload()` converts
  down, `titleCase()` converts up.
- `swing_modes` are Title Case on both sides — no conversion.
- The app's fallback option lists (used when a helper is missing from the state map) must match
  the real helpers: vane `Highest, High, Low, Lowest, Vertical Swing`; fan
  `Quiet, Low, Medium, High, Auto`. An earlier fallback listed seven vane values, **not one of
  which the helper accepts**, while the optimistic UI showed the change as applied.

---

## 9. Rebuild procedures

### 9.1 If a helper is deleted

Recreate it with the exact entity id from §4.1. The naming is mechanical:
`input_boolean.zone_enable_<zone>`, `input_boolean.override_<zone>`,
`input_select.<zone>_{fan,tilt}_mode`, `input_number.<zone>_{day,night,away}_{temp,cool}` for the
six zone keys `main_level, anthony, autumn, bedroom_1, bedroom_2, basement`.

`input_select` options must match §4.1 exactly. Then add it to the forwarder automation's trigger
list if it is new, and to `layout_config.json` so the app binds to it.

### 9.2 If a forwarder automation is lost

Both are in §4.2 with their full trigger lists. The essentials: mode `parallel` max 20, the
`x-ha-token` header must match n8n, the payload key is `entity_id`, recovery events are prefixed
`recovered:`, and the condition dropping transitions *from* unavailable must be kept or an HA
restart stampedes n8n.

### 9.3 If an n8n workflow is lost

Rebuild order matters, because they call each other by ID:

1. **HVAC Notify** — 3 nodes, no dependencies.
2. **HVAC Apply** — depends on the `hvac_write_lock` data table.
3. **HVAC Zone Runner** — 2 nodes, references HVAC Apply's ID.
4. **HVAC Controller** — references the Zone Runner's ID in all eight Dispatch nodes.
5. **HVAC Watchdog** — independent; reads the lock table, writes through HVAC Notify.

If IDs change, update: the Zone Runner's `Apply Action` node, the Controller's eight
`Dispatch Zone - *` nodes, and `errorWorkflow` on all four.

**After every edit: `publish_workflow`, then re-read and confirm
`activeVersionId === versionId`.**

### 9.4 If the data table is lost

Create `hvac_write_lock` with the six columns in §5.2, then insert the **ten rows** with the
`lock_key` values listed there, `owner: ""`, `expires_at: 0`. A missing row means that lock can
never be acquired — Apply will wait 90 s and then send a "gave up waiting for the write lock"
Telegram every time.

### 9.5 If the app needs rebuilding

```bash
./gradlew assembleDebug
```

Needs a `.env` with `HA_URL`, `HA_BACKUP_URL`, `HA_TOKEN`, `GITHUB_TOKEN` (see `.env.example`).
Tests: `./gradlew test` — 83 tests should pass.

Install to the three devices only (see §6.9). Because CI's keystore changes every run, an existing
install may need uninstalling first.

### 9.6 If only the layout needs changing

Edit **both** copies of `layout_config.json`, bump `version`, commit, push to `main`. The panels
offer it on their next check. No rebuild, no reinstall, no adb.

---

## 10. Failure modes and how they present

| Symptom | Most likely cause | Where to look |
|---|---|---|
| A zone keeps latching into override every schedule change | its setpoint is below the hardware floor | §8.1; check the `input_number` values |
| Main level heads disagree for 40 minutes | echo gate not opening in time | Apply's `Stamp Write Time` position; `last_write_at` in the lock table |
| Heads flap between two values | a retired HA automation was re-enabled | §4.3 |
| Nothing happens when a helper changes | shared secret mismatch, or the forwarder is off | the `Verify … Token` node vs `configuration.yaml` |
| Telegram says "gave up waiting 90s for the write lock" | a lock row is missing, or stuck with a future `expires_at` | the `hvac_write_lock` rows |
| Watchdog silently stops acting | a future-dated timestamp in the lock table | `sane()` guards this now; check `last_write_at` |
| App shows stale data, panel feels laggy | WebSocket never connected | logcat for `HA_WS_Manager`; check the token |
| App claims a vane change applied but nothing moved | fallback option list doesn't match the helper | §8.5 |
| Panel shows "AT TARGET" for an unreachable head | `statusLabel` falling through | fixed; returns UNAVAILABLE |
| An n8n edit appears saved but the old code runs | the draft was written, not published | `N8N-EDITING.md` item 2 |
| Both garage doors show open | wired sensors used instead of covers | §4.4 |

---

## 11. Secrets, credentials and where they live

Nothing secret is written into this document. This is the inventory of *where* to look.

| Secret | Lives in | Notes |
|---|---|---|
| HA long-lived token | `.env` (gitignored) → `BuildConfig.HA_TOKEN`; SharedPreferences `ha_token` | rotation declined |
| GitHub PAT | `.env` → `BuildConfig.GITHUB_TOKEN`; masked in the panel's settings UI | rotation declined |
| n8n webhook shared secret | the `Verify … Token` IF nodes; HA `configuration.yaml` rest_command headers | must match in both |
| Telegram bot | n8n credential `g3BAkFvsxgdUVwyX` | HA has no telegram_bot integration |
| HA API for n8n | n8n credential `2i5NAqpKqoJI2ydI` | |
| Telegram chat id | hardcoded in each Telegram node | owner only |
| Release keystore | `KEYSTORE_PATH` / `STORE_PASSWORD` / `KEY_PASSWORD` env vars | not in CI — see §6.8 |

Logging interceptors are `BASIC` in debug, `NONE` in release, with `redactHeader("Authorization")`
in both the HA and GitHub clients. `backup_rules.xml` / `data_extraction_rules.xml` keep tokens out
of cloud backup.

---

## 12. Known open items

**Deferred by choice:**

- **Run mutex (n8n).** All eight `Dispatch Zone - *` nodes use `waitForSubWorkflow: false`, so the
  mutex is released roughly 11 s before the writes it guards land. Fixing it means flipping that
  flag **and** raising the 150 s mutex TTL in the same change — a full sweep is about 350 s. The
  most behaviour-changing edit on the list, and the reason it is parked.
- **OTA / CI signing.** See §6.8. No working APK update path exists.

**Known and unfixed in n8n:**

- **N6/N8** — the echo gate's read side checks only `last_write_at`, so a fan/vane write's own echo
  is not suppressed, and a genuine vane change within 15 s of a mode change is dropped.
  `Check Recent Self-Write` needs splitting by channel.
- **N7** — an unconfirmed write is reported to the Zone Runner as success, so the sequence
  continues into a head that may still be off.
- **Bot N11** — `Check Verify` excludes overridden/disabled zones from both numerator and
  denominator, so it can report "5 of 5 confirmed" while a held zone sits on the opposing family.

**Alarm tab** — built, tested (16 unit tests), and **hidden** pending Alarmo in HA. Set
`"hidden": false` on the alarm tab in both copies of `layout_config.json` to reveal it. If Alarmo's
entity id is not `alarm_control_panel.alarmo`, change `alarm.entityId` — one line, no rebuild. Arm
buttons are derived from the panel's `supported_features`, so whichever modes Alarmo is configured
for are the ones that appear. **The PIN is typed on the keypad and passed straight to the service
call — never stored, cached or logged, and there is deliberately no config field for it. Keep it
that way.**

---

## 13. What has been verified live, not just compiled

- **Heat floor:** bedroom_1 and basement applied 61 and held through three watchdog ticks with no
  override, against the same sequence that latched both at 09:30 that morning.
- **WebSocket:** full auth handshake on all three devices, where there had previously been zero log
  lines and no `last_known_*` preference keys.
- **Echo gate timing:** execution `7870` — `about_to_send_at` 18:44:40.912, row stamped
  18:44:45.531, run ended 18:44:48.272, echo lands ~:47. Went from missed to caught with 1.5 s
  spare.
- **Override latch and revert:** latched 71 s after a head was turned off by hand; reverted 6 s
  after the override was cleared.
- **A real schedule transition** (Away → Day, 15:03) completed in one clean Controller run, 3.8 s,
  no zones dropping out.
- **Boundary testing on the real heads:** 60 rejected, 60.5 rejected, 60.8 rejected, 61 accepted.
  Ten days of history show no head ever below `set_tmp 160`.
