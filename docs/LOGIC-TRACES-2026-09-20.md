# Logic traces — 2026-09-20

Step-by-step simulation of real control flows with live values, rather than reading code for
smells. House state during these traces: `global_hvac_mode = heat`, `house_schedule_state = Day`.

Each trace runs a user action all the way through app, Home Assistant and n8n, and states where
it ends up. Several of these found defects that a straight read had missed — including two in
code written earlier the same day.

---

## Trace 1 — Tap HEAT on Autumn, which is off

**Setup.** Autumn heat day target 62. Autumn cool day target 72. The head last ran cooling, so it
holds 72. House is heating.

| Step | What happens | Verdict |
|---|---|---|
| `detectModeConflict("autumn","heat")` | House family HEAT equals requested HEAT, short-circuits to null | correct |
| `setZoneHvacMode` sees state `off` | sends `climate.turn_on` | correct |
| waits for the head to report not-off | bounded 8s | **was a fixed 1.2s sleep** |
| sends `set_hvac_mode heat` | head switches | correct |
| waits for state to equal `heat` | bounded 8s | **was a fixed 1.2s sleep** |
| reads setpoint, finds 72, wants 62 | 10 degrees apart, sends `set_temperature 62` | **was not sent at all** |
| Watchdog tick within 60s | state heat equals global heat; 62 vs 62 is inside the 0.6 tolerance | no drift |

**Before today this ended differently.** The mode never applied at all, because these heads keep
`onoff` and `op_mode` in separate registers and a mode write alone leaves a sleeping head asleep.
Once that was fixed, the head came up holding 72 against a 62 target, and the watchdog read the
10 degree gap as a manual adjustment and suspended the zone's automation inside a minute. The
fix only looked complete until the trace was run.

**The gap is not theoretical.** Heat and cool day targets per zone:

| Zone | Heat | Cool | Gap |
|---|---|---|---|
| autumn | 62 | 72 | 10 |
| basement | 65 | 70 | 5 |
| bedroom_2 | 70 | 73 | 3 |
| anthony | 70 | 72 | 2 |
| bedroom_1 | 65 | 67 | 2 |
| main_level | 70 | 71 | 1 |

---

## Trace 2 — The same tap, but using the power button

This is where the trace paid for itself. The power button reaches the same outcome by a
different path, and that path had none of the protections.

| | Mode button | Power button (before) |
|---|---|---|
| Conflict check | yes | **none** |
| `turn_on` first | yes | no |
| Scheduled setpoint | yes | no |

Powering a zone on *is* a mode change: it picks heat, cool or dry from the house mode. So the
power button was the one remaining way to reach a cross-family combination the panel would
otherwise refuse, and it left the head on the other family's setpoint when it got there.

**Worked example.** House mode off, living room running cool on unit 1, basement asleep. Press
power on the basement. It resolves `lastNonOffHvacMode`, gets heat, and sets the basement to
heat — heat and cool on one condenser, which the mode buttons refuse. The watchdog then turns a
head off 55 seconds later.

Now routed through the same path as the mode buttons and gated by the same dialog.

---

## Trace 3 — Tap COOL on Anthony while the house is heating

| Step | Result |
|---|---|
| `detectModeConflict` | house family HEAT, requested COOL, conflict raised naming the house mode |
| Dialog offers override | user confirms |
| `applyHouseModeOverride` | global helper is `heat`, target `cool`, differs, so the write happens |
| | also applies `cool` to Anthony, which the old code never did |
| | Telegram says the whole house moved, and says so if scheduling was paused |
| HA helper change fires the forwarder | n8n sweeps every zone to cool |
| Race: app writes Anthony, n8n writes Anthony | both write `cool` and both write `anthony_day_cool` = 72 |

**Converges.** The two writers agree on the value, so the race is benign. Worth noting the app
holds no lock while n8n does, so ordering is not guaranteed — it is only safe because the values
match.

**Previously this path did nothing at all.** When the house mode already matched the request,
`select_option` was called with the option already selected, which is not a state change, so the
sequencer never ran and the request was silently lost.

---

## Trace 4 — Option casing, checked on every helper

The car's schedule button never worked because it sent `night` to a helper whose options are
capitalised. That is a whole class of bug, so every option write was checked against the live
helper rather than assumed.

| Control | Sends | Helper options | Verdict |
|---|---|---|---|
| Panel schedule | `Day` `Night` `Away` | `Day` `Night` `Away` | correct |
| Panel global mode | `heat` `cool` `dry` `off` | `heat` `cool` `dry` `off` | correct |
| Panel water heater | `eco` `heat_pump` `high_demand` | same | correct |
| Conflict override | lowercased target | matches | correct |
| Car schedule | `night` | `Night` | **was broken, fixed** |
| Car water heater | lowercase | matches | correct |

Only the car was wrong. Recording the negative result so this is not re-investigated.

---

## Trace 5 — Would forwarding the other five heads to n8n help?

Tempting, because n8n only hears about mode changes on two of seven heads. Traced before
changing anything.

A head event entering the controller hits the main-level mirror block first. That block is
guarded by `MAIN.indexOf(trig) !== -1`, so a non-main head falls through to the normal sequencer
path — no cascade risk, which was the first concern and it is unfounded.

But the normal sequencer path drives heads *toward the schedule*. Forwarding a hand-set head
means the sequencer reverts it within seconds, which is the opposite of the stated rule that a
rogue head may stay and trigger manual mode. The 60 second watchdog poll is the intended path.

**Conclusion: leave it.** The apparent gap is load-bearing.

---

## Trace 6 — What can clear a latched override

The audit called a latched `input_boolean.override_<zone>` the one thing that genuinely gets
stuck, since nothing clears it on a timer.

Paths that clear it:

1. A schedule transition, via n8n's sequencer.
2. A matching `zone_enable` toggle for that zone.
3. **The panel.** The zone popup toggles the override helper directly.

Path 3 means a suspended zone is recoverable from the wall without waiting for a schedule
boundary. The audit under-stated this. Still worth knowing that Night to Day depends on a phone's
charging state, so path 1 is less reliable than it looks.

---

## Trace 7 — Setpoint selection has to match n8n exactly

The app now sends a setpoint after a mode change, so it has to choose the same number n8n would.
Any disagreement shows up as the two systems correcting each other.

n8n's rule, in both the sequencer and the watchdog, is `cool ? _cool : _temp`, then a 64.5 floor
for cool **and** dry. Two consequences that look like bugs and are not:

- **Dry reads the heat helper.** Dry is a cooling function, but the suffix rule only special-cases
  `cool`.
- **Those two compound.** Dry on a zone whose heat target is 62 reads 62, then floors to 64.5.

A test asserting the tidier answer failed, which is how this surfaced. The code was right and the
expectation was wrong. Both behaviours are now pinned by tests with the reasoning written down,
so a future tidy-up has to be deliberate.

---

## Trace 8 — Non-ASCII characters through a PowerShell rewrite

Not a runtime flow, but the same discipline caught it.

Two source files were rewritten during this session using PowerShell 5.1 `Set-Content -Encoding
utf8` after being read with `Get-Content`, which defaults to the ANSI codepage. Every non-ASCII
character in both files was double-encoded.

User-visible result, had it stayed:

| Intended | Would have displayed |
|---|---|
| `70°F` | `70Â°F` |
| `• IDEAL` | `â€¢ IDEAL` |

Caught while reading an unrelated line of the debounce path. It had already shipped to all three
devices. Both files are back to UTF-8 with no BOM, verified against the pre-corruption revision by
special-character count, and redeployed.

**Lesson worth keeping:** do not round-trip source files through PowerShell 5.1 text cmdlets. Use
the editing tools, or .NET with an explicit encoding.

---

## Trace 9 — How a setpoint actually reaches a head

An earlier draft of this document claimed the zone card had a temperature dial writing the head
directly, sitting alongside presets that wrote helpers, and called that a contradiction. **That
was wrong.** There is no dial. Corrected here because the wrong version was committed.

`setTargetTemperature` has exactly one call site, inside the preset stepper:

```kotlin
viewModel.setPresetTemperature(entityId, newVal, ...)      // the schedule helper
if (isActive) {
    viewModel.setTargetTemperature(zone.climateEntityId, ...)   // apply-now shortcut
}
```

So adjusting a preset writes the `input_number`, and *if that preset is the slot currently in
force*, the value is also pushed straight at the head so the room responds without waiting for
the forwarder, the run mutex and Apply's confirm window. One control, with a shortcut.

Traced for convergence:

| Path | Lands on |
|---|---|
| `input_number.main_level_day_temp` = 71 | forwarder `setpoint` trigger, 3s dwell, to the sequencer |
| direct `set_temperature` 71 on the living room head | forwarder `main_head_temp` trigger, 2s dwell, mirrors dining |

Both arrive at 71, so the two writers agree and the shortcut is safe.

One cosmetic edge: the app clamps a cool preset at 64.0 while n8n floors it at 64.5. Set a cool
preset to 64 and the helper keeps 64 while the head ends at 64.5. The watchdog compares the head
against the floored value, so it does not flag drift; the card just reads one notch below the
head.

**This also settles whether the app should write a setpoint after powering a zone on.** It should,
and it is consistent rather than novel: the preset path already writes the head directly for the
same reason. For the five heads outside the forwarder's mode triggers there is no other writer
that would hear about the zone coming on at all.

---

## Still open, needing a decision rather than a fix

- **Turning the house off asks for no confirmation**, while switching heat to cool does. The
  guard protects the compressor and leaves the house unprotected in winter.
- **Temperature limits come from different bases.** The apply-now path clamps against the zone's
  own mode; the preset path clamps against whether the edited preset is a heat or cool one.
  Defensible either way, but worth knowing they are not the same test.
