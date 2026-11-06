const spec = $('After Send').first().json;
const v = spec.verify;
const attempt = $runIndex;
const NL = String.fromCharCode(10);
const ZONES = [
  { id: 'main_level', name: 'Main Level', heads: ['climate.hp_living_room', 'climate.hp_dining_room'] },
  { id: 'anthony', name: 'Anthony', heads: ['climate.hp_anthony'] },
  { id: 'autumn', name: 'Autumn', heads: ['climate.hp_autumn'] },
  { id: 'bedroom_1', name: 'Bedroom 1', heads: ['climate.hp_bedroom'] },
  { id: 'bedroom_2', name: 'Bedroom 2', heads: ['climate.hp_bedroom_2'] },
  { id: 'basement', name: 'Basement', heads: ['climate.hp_basement'] }
];
// Which heads a multi-head zone is using. A paused head is held off on purpose, so it is not
// waited for below. Keep identical to HEAD_CHOICE in the HVAC Controller and Watchdog.
const HEAD_CHOICE = {
  main_level: {
    helper: 'input_select.main_level_heads',
    paused: {
      'living room only': ['climate.hp_dining_room'],
      'dining room only': ['climate.hp_living_room']
    }
  }
};
const MODES = ['heat', 'cool', 'dry', 'off'];
const MODE_ICON = { heat: '🔥', cool: '❄️', dry: '💧', off: '⏻' };
const WH = 'water_heater.heat_pump_water_heater';
const stateMap = {};
for (const it of $input.all()) { const e = it.json && it.json.entity_id; if (e) stateMap[e] = it.json; }
function st(e) { const s = stateMap[e]; return s ? s.state : undefined; }
function at(e, a) { const s = stateMap[e]; return s && s.attributes ? s.attributes[a] : undefined; }
function esc(s) { return String(s === undefined || s === null ? '' : s).split('&').join('&amp;').split('<').join('&lt;').split('>').join('&gt;'); }
function bad(x) { return x === undefined || x === null || x === '' || x === 'unavailable' || x === 'unknown'; }
function num(x) { const n = parseFloat(x); return isNaN(n) ? NaN : n; }
const COOL_FLOOR = 64.5;
const HEAT_FLOOR = 61;
// House conditions (2026-11-06): the switches on the app's Heat pump settings page that change
// which temperature a zone should be at. Keep identical in the Controller ('Compute Sequencer
// Actions'), the Watchdog ('Evaluate') and the Bot ('Check Verify'), or one of them
// will think the others are wrong.
//  - schedule: the zone follows that schedule block instead of the house's. fromSchedule limits
//    it to while the house is on that block.
//  - boostHelper: degrees added while heating and taken off while cooling (not in dry).
// Later entries win, so Kids away beats Night early for Anthony and Autumn.
const CONDITIONS = [
  { helper: 'input_boolean.hvac_early_night', on: 'on', zones: 'all', fromSchedule: 'day', schedule: 'night' },
  { helper: 'input_boolean.hvac_kids_away', on: 'on', zones: ['anthony', 'autumn'], schedule: 'away' },
  { helper: 'timer.hvac_boost_main_level', on: 'active', zones: ['main_level'], boostHelper: 'input_number.hvac_boost_degrees', boostDefault: 3 }
];
// A boost never takes a heating target past this (the schedule helpers' own maximum).
const BOOST_HEAT_MAX = 80;
function condOn(c) { return String(st(c.helper) || '') === c.on; }
function condFor(c, zone) { return c.zones === 'all' || c.zones.indexOf(zone) !== -1; }
function zoneSched(zone, houseSched) {
  let s = houseSched;
  for (const c of CONDITIONS) {
    if (!c.schedule || !condFor(c, zone) || !condOn(c)) continue;
    if (c.fromSchedule && c.fromSchedule !== houseSched) continue;
    s = c.schedule;
  }
  return s;
}
function zoneBoost(zone) {
  let b = 0;
  for (const c of CONDITIONS) {
    if (!c.boostHelper || !condFor(c, zone) || !condOn(c)) continue;
    const d = parseFloat(st(c.boostHelper));
    b += (!isNaN(d) && d > 0 && d <= 10) ? d : c.boostDefault;
  }
  return b;
}
// The temperature a zone should be at in this mode: its schedule helper (after the conditions
// above), plus any boost, clamped to what the heads accept. NaN when there is none.
function zoneTarget(zone, mode, houseSched) {
  if (mode !== 'heat' && mode !== 'cool' && mode !== 'dry') return NaN;
  const raw = parseFloat(st('input_number.' + zone + '_' + zoneSched(zone, houseSched) + (mode === 'cool' ? '_cool' : '_temp')));
  if (isNaN(raw) || raw < 40 || raw > 100) return NaN;
  const b = zoneBoost(zone);
  if (mode === 'heat') return Math.max(Math.min(raw + b, Math.max(raw, BOOST_HEAT_MAX)), HEAT_FLOOR);
  if (mode === 'cool') return Math.max(raw - b, COOL_FLOOR);
  return Math.max(raw, COOL_FLOOR);
}
function pausedHeads(zone) {
  const c = HEAD_CHOICE[zone];
  if (!c) return [];
  return c.paused[String(st(c.helper) || '').toLowerCase()] || [];
}
function gmode() { const m = String(st('input_select.global_hvac_mode') || '').toLowerCase(); return MODES.indexOf(m) === -1 ? '?' : m; }
function sched() { const s = String(st('input_select.house_schedule_state') || '').toLowerCase(); return ['day', 'night', 'away'].indexOf(s) === -1 ? '?' : s; }
function setpointId(z, mode, sc) { return 'input_number.' + z + '_' + sc + '_' + (mode === 'cool' ? 'cool' : 'temp'); }
// Includes the house conditions (Kids away, Night early, Boost), as the Controller does.
function expectedTemp(z, mode, sc) {
  if (mode === 'off' || mode === '?' || sc === '?') return NaN;
  return zoneTarget(z, mode, sc);
}
function zoneLine(z) {
  const enabled = st('input_boolean.zone_enable_' + z.id) === 'on';
  const ovr = st('input_boolean.override_' + z.id) === 'on';
  const h = z.heads[0];
  const hs = st(h);
  let s = '• <b>' + z.name + '</b>: ';
  if (bad(hs)) { s += 'unavailable'; }
  else {
    s += hs;
    const cur = num(at(h, 'current_temperature'));
    const tgt = num(at(h, 'temperature'));
    if (hs !== 'off') { if (!isNaN(cur)) s += ' ' + cur + '°'; if (!isNaN(tgt)) s += ' → ' + tgt + '°'; }
    else if (!isNaN(cur)) { s += ' (' + cur + '°)'; }
  }
  const flags = [];
  if (!enabled) flags.push('disabled');
  if (ovr) flags.push('manual override');
  if (flags.length) s += ' — ' + flags.join(', ');
  return s;
}
function statusText(extra) {
  const m = gmode();
  let t = '<b>HVAC</b> — ' + (MODE_ICON[m] || '') + ' <b>' + m.toUpperCase() + '</b> · schedule ' + esc(st('input_select.house_schedule_state'));
  t += NL + ZONES.map(zoneLine).join(NL);
  const wt = at(WH, 'temperature');
  t += NL + '🚿 Water heater: ' + esc(st('input_select.water_heater_mode')) + (wt ? ' · target ' + wt + '°' : '');
  if (extra) t += NL + NL + extra;
  return t;
}
function mainF() {
  const m = gmode();
  const f = {};
  MODES.forEach(function (k) { f['mt_' + k] = (m === k ? '✓ ' : '') + MODE_ICON[k] + ' ' + k.charAt(0).toUpperCase() + k.slice(1); });
  return f;
}
if ($input.all().length < 20) {
  return [{ json: { done: attempt >= 8, text: '⚠️ Could not read Home Assistant to confirm ' + v.mode + '. Tap Refresh.', f: mainF(), chat_id: spec.chat_id, sent_id: spec.sent_id } }];
}
const m = v.mode;
const sc = sched();
const pending = [];
let okCount = 0;
let total = 0;
for (const z of ZONES) {
  const enabled = st('input_boolean.zone_enable_' + z.id) === 'on';
  const ovr = st('input_boolean.override_' + z.id) === 'on';
  if (!enabled || ovr) continue;
  total++;
  const exp = expectedTemp(z.id, m, sc);
  const paused = pausedHeads(z.id);
  let zoneOk = true;
  for (const h of z.heads) {
    if (paused.indexOf(h) !== -1) continue;
    const hs = st(h);
    if (bad(hs)) { zoneOk = false; continue; }
    if (hs !== m) { zoneOk = false; continue; }
    if (m !== 'off' && !isNaN(exp)) {
      const tgt = num(at(h, 'temperature'));
      if (isNaN(tgt) || Math.abs(tgt - exp) > 0.6) zoneOk = false;
    }
  }
  if (zoneOk) okCount++; else pending.push(z.name);
}
const elapsed = Math.round((Date.now() - v.started_at) / 1000);
const done = pending.length === 0 || attempt >= 8;
let note;
if (total === 0) note = 'ℹ️ No enabled zones without an override, so there was nothing to confirm.';
else if (pending.length === 0) note = '✅ <b>' + m + '</b> confirmed on ' + okCount + ' of ' + total + ' zones in ' + elapsed + 's.';
else note = '⚠️ <b>' + m + '</b> not confirmed on: ' + pending.join(', ') + ' (after ' + elapsed + 's). Tap Refresh to check again.';
return [{ json: { done: done, text: statusText(note), f: mainF(), chat_id: spec.chat_id, sent_id: spec.sent_id } }];
