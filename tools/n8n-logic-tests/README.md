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

```bash
node test-controller-watchdog.js
node test-bot-verify.js
```

The cases cover Main Level's "Living room only" choice (`input_select.main_level_heads`, added
2026-11-03): switching on and off in every house state, schedule and mode changes while paused,
a paused head switched on by hand, restarts and Wi-Fi recoveries, fan/vane, the Watchdog's ticks and
its 3-minute settling window, the bot's mode confirmation, plus the future "Dining room only" option
and checks that the both-heads behaviour is unchanged.
