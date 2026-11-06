# n8n logic tests

Copies of the n8n Code-node scripts, run on this PC against simulated Home Assistant states. These are
copies, not the live code: n8n holds the real thing. After changing one of these nodes in n8n, paste the
new code over the matching file here and re-run the tests before publishing.

| File | n8n workflow → node |
|---|---|
| `controller-compute-sequencer.js` | HVAC Controller (n8n) → Compute Sequencer Actions |
| `controller-compute-fan-tilt.js` | HVAC Controller (n8n) → Compute Fan Tilt Actions |
| `watchdog-evaluate.js` | HVAC Watchdog (n8n) → Evaluate |
| `bot-check-verify.js` | HVAC Bot (n8n) → Check Verify |
| `bot-handle.js` | HVAC Bot (n8n) → Handle |

```bash
node test-controller-watchdog.js
node test-bot-verify.js
node test-bot-handle.js
```

The cases cover Main Level's "Living room only" choice (`input_select.main_level_heads`, added
2026-11-03): switching on and off in every house state, schedule and mode changes while paused,
a paused head switched on by hand, restarts and Wi-Fi recoveries, fan/vane, the Watchdog's ticks and
its 3-minute settling window, the bot's mode confirmation, plus the future "Dining room only" option
and checks that the both-heads behaviour is unchanged.

Since 2026-11-06 they also cover the house conditions from the app's Heat pump settings page: Kids away
(`input_boolean.hvac_kids_away`), Night early (`input_boolean.hvac_early_night`) and the Main Level Boost
(`timer.hvac_boost_main_level`, size from `input_number.hvac_boost_degrees`). The CONDITIONS table and
`zoneSched` / `zoneBoost` / `zoneTarget` are pasted identically into the sequencer, the Watchdog and both Bot
nodes; the app reads the same rules from `hvacSettings` in layout_config.json (`HouseConditions.kt`).
