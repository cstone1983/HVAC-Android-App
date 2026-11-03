const ZONES = ['main_level', 'anthony', 'autumn', 'bedroom_1', 'bedroom_2', 'basement'];
const HEADS = {
  main_level: ['climate.hp_living_room', 'climate.hp_dining_room'],
  anthony: ['climate.hp_anthony'],
  autumn: ['climate.hp_autumn'],
  bedroom_1: ['climate.hp_bedroom'],
  bedroom_2: ['climate.hp_bedroom_2'],
  basement: ['climate.hp_basement']
};
const HEAD_ZONE = {};
ZONES.forEach(function (z) { HEADS[z].forEach(function (h) { HEAD_ZONE[h] = z; }); });
const MAIN = HEADS.main_level;
// Which heads a multi-head zone is using. Keep identical to HEAD_CHOICE in 'Compute Sequencer
// Actions' (which explains it) and the Watchdog's Evaluate node.
const HEAD_CHOICE = {
  main_level: {
    helper: 'input_select.main_level_heads',
    paused: {
      'living room only': ['climate.hp_dining_room'],
      'dining room only': ['climate.hp_living_room']
    }
  }
};

const stateMap = {};
for (const item of $input.all()) { const e = item.json.entity_id; if (e) stateMap[e] = item.json; }
function st(e) { const s = stateMap[e]; return s ? s.state : undefined; }
function at(e, a) { const s = stateMap[e]; return s && s.attributes ? s.attributes[a] : undefined; }
function bad(v) { return v === undefined || v === null || v === '' || v === 'unavailable' || v === 'unknown'; }
function titleCase(v) { return String(v).replace(/\w\S*/g, function (w) { return w.charAt(0).toUpperCase() + w.slice(1).toLowerCase(); }); }
function fanPayload(raw) { return raw === 'Quiet' ? 'Quiet' : String(raw).toLowerCase(); }
function pausedHeads(zone) {
  const c = HEAD_CHOICE[zone];
  if (!c) return [];
  return c.paused[String(st(c.helper) || '').toLowerCase()] || [];
}
function inUse(zone) {
  const p = pausedHeads(zone);
  return HEADS[zone].filter(function (h) { return p.indexOf(h) === -1; });
}

const run = $('Init Run').first().json;
let trig = run.entity_id;
let recovered = false;
if (trig.indexOf('recovered:') === 0) { recovered = true; trig = trig.substring(10); }
const eventTs = run.event_ts;

// Shared with 'Compute Sequencer Actions': skip pushing a head's post-mode-change swing/fan
// reset back into the UI select when WE just changed that head's mode/turned it on - otherwise
// the device's own reset (e.g. swing returning to a default position on a mode change) looks
// like a manual change and silently overwrites the user's last deliberate UI selection.
const sd = $getWorkflowStaticData('global');
const recentModeChange = !!(sd.lastModeChangeAt && sd.lastModeChangeAt[trig] !== undefined && (eventTs - sd.lastModeChangeAt[trig]) < 20000);

function act(zone, head, service, attr, value) {
  return { entity_id: head, domain: 'climate', service: service, attr_name: attr, attr_value: String(value), verify_type: 'attr_eq', verify_attr: attr, verify_value: String(value), zone: zone, source: 'fan_tilt', gap_s: 1 };
}
function actSelect(zone, helper, value) {
  return { entity_id: helper, domain: 'input_select', service: 'select_option', attr_name: 'option', attr_value: String(value), verify_type: 'state_eq', verify_value: String(value), zone: zone, source: 'fan_tilt', gap_s: 0 };
}
function done(actions, why) {
  if (actions.length === 0) return [{ json: { noop: true, reason: why, trig: trig } }];
  return actions.map(function (a) { return { json: a }; });
}
function usable(head) { return !bad(st(head)); }
function helperAgeMs(h) {
  const s = stateMap[h];
  const t = s && s.last_changed ? Date.parse(s.last_changed) : NaN;
  return isNaN(t) ? Infinity : (eventTs - t);
}
function hasOption(h, v) {
  const opts = at(h, 'options');
  return Array.isArray(opts) && opts.indexOf(v) !== -1;
}
function allowed(head, attr, list, value) {
  const opts = at(head, list);
  return !Array.isArray(opts) || opts.indexOf(value) !== -1;
}

const actions = [];
const isFanHelper = trig.indexOf('input_select.') === 0 && trig.indexOf('_fan_mode') !== -1;
const isTiltHelper = trig.indexOf('input_select.') === 0 && trig.indexOf('_tilt_mode') !== -1;

function applyHelpersToHead(zone, head) {
  if (!usable(head)) return;
  // An OFF head will not accept fan/tilt. Airstage keeps reporting its previous louver position,
  // so the write never confirms and HVAC Apply raises "command not confirmed" 15s later. A WiFi
  // dropout on a head therefore produced a guaranteed-failing alert every time it recovered,
  // for any zone whose tilt helper disagreed with the head (seen 2026-09-30 on living room and
  // bedroom 2, helpers Lowest vs heads Highest, global mode off).
  // Matches the main-level twin-mirror branch below, which already skips when the source is off.
  // The UI selection stays the desired state: it is pushed on the next UI change, or by the
  // sequencer path once the head is actually running.
  if (st(head) === 'off') return;
  const fanRaw = st('input_select.' + zone + '_fan_mode');
  const tiltRaw = st('input_select.' + zone + '_tilt_mode');
  if (!bad(fanRaw)) { const v = fanPayload(fanRaw); if (allowed(head, 'fan_mode', 'fan_modes', v)) actions.push(act(zone, head, 'set_fan_mode', 'fan_mode', v)); }
  if (!bad(tiltRaw)) { const v2 = String(tiltRaw); if (allowed(head, 'swing_mode', 'swing_modes', v2)) actions.push(act(zone, head, 'set_swing_mode', 'swing_mode', v2)); }
}

// 1) UI select changed: apply it to the zone heads in use (a paused head is off and would never confirm).
if (isFanHelper || isTiltHelper) {
  let zone = '';
  for (const z of ZONES) { if (trig.indexOf(z) !== -1) { zone = z; break; } }
  const raw = st(trig);
  if (zone && !bad(raw)) {
    for (const head of inUse(zone)) {
      if (!usable(head)) continue;
      if (isFanHelper) { const v = fanPayload(raw); if (allowed(head, 'fan_mode', 'fan_modes', v)) actions.push(act(zone, head, 'set_fan_mode', 'fan_mode', v)); }
      else { const v2 = String(raw); if (allowed(head, 'swing_mode', 'swing_modes', v2)) actions.push(act(zone, head, 'set_swing_mode', 'swing_mode', v2)); }
    }
  }
  return done(actions, 'ui selection already applied');
}

// 1b) A head came back from unavailable: re-apply the UI selections (the UI is the desired state).
// A paused head is left alone; the sequencer is holding it off.
if (recovered && HEAD_ZONE[trig]) {
  if (pausedHeads(HEAD_ZONE[trig]).indexOf(trig) !== -1) return done(actions, 'recovered head is paused: fan/tilt not re-applied');
  applyHelpersToHead(HEAD_ZONE[trig], trig);
  return done(actions, st(trig) === 'off' ? 'recovered head is off: fan/tilt not re-applied (the device will not accept them while off)' : 'recovered head already matches UI selections');
}

// 2) Main-level twin-head mirror for fan and tilt, only while both heads are in use. Skipped when
// either head is unavailable or the source is off.
if (MAIN.indexOf(trig) !== -1 && pausedHeads('main_level').length === 0) {
  const src = trig;
  const dst = src === MAIN[0] ? MAIN[1] : MAIN[0];
  const sMode = st(src);
  const dMode = st(dst);
  if (!bad(sMode) && !bad(dMode) && sMode !== 'off') {
    const sFan = at(src, 'fan_mode');
    const dFan = at(dst, 'fan_mode');
    const sTilt = at(src, 'swing_mode');
    const dTilt = at(dst, 'swing_mode');
    if (sFan !== undefined && sFan !== null && sFan !== dFan) actions.push(act('main_level', dst, 'set_fan_mode', 'fan_mode', sFan));
    if (sTilt !== undefined && sTilt !== null && sTilt !== dTilt) actions.push(act('main_level', dst, 'set_swing_mode', 'swing_mode', sTilt));
  }
}

// 3) UI feedback: push a head's actual fan/tilt into its select. Only the zone's first head in use
// reports back (the living room on Main Level, as before, unless it is the paused one). Debounced
// against the event's own arrival time, and suppressed for 20s after WE changed this head's mode
// (see recentModeChange above).
const fbZone = HEAD_ZONE[trig];
if (fbZone && inUse(fbZone)[0] === trig && !bad(st(trig)) && !recentModeChange) {
  const zone = fbZone;
  const fanHelper = 'input_select.' + zone + '_fan_mode';
  const tiltHelper = 'input_select.' + zone + '_tilt_mode';
  const apiFan = at(trig, 'fan_mode');
  const apiTilt = at(trig, 'swing_mode');
  if (apiFan !== undefined && apiFan !== null && !bad(st(fanHelper))) {
    const ui = titleCase(apiFan);
    if (st(fanHelper) !== ui && hasOption(fanHelper, ui) && helperAgeMs(fanHelper) > 5000) actions.push(actSelect(zone, fanHelper, ui));
  }
  if (apiTilt !== undefined && apiTilt !== null && !bad(st(tiltHelper))) {
    const ui2 = titleCase(apiTilt);
    if (st(tiltHelper) !== ui2 && hasOption(tiltHelper, ui2) && helperAgeMs(tiltHelper) > 5000) actions.push(actSelect(zone, tiltHelper, ui2));
  }
}
return done(actions, 'fan/tilt already in sync');
