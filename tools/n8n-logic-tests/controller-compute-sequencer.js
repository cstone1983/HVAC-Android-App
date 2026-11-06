const HEADS = {
  main_level: ['climate.hp_living_room', 'climate.hp_dining_room'],
  anthony: ['climate.hp_anthony'],
  autumn: ['climate.hp_autumn'],
  bedroom_1: ['climate.hp_bedroom'],
  bedroom_2: ['climate.hp_bedroom_2'],
  basement: ['climate.hp_basement']
};
const ZONES = ['main_level', 'anthony', 'autumn', 'bedroom_1', 'bedroom_2', 'basement'];
const MAIN = HEADS.main_level;
const HEAD_ZONE = {};
ZONES.forEach(function (z) { HEADS[z].forEach(function (h) { HEAD_ZONE[h] = z; }); });
// Which heads a multi-head zone is using, picked by an input_select. A head that is not in use is
// held OFF - even while the zone is on Override or Auto is off, because the choice was explicit -
// and is left out of the mirror, the schedule, setpoints and fan/tilt. An unknown or unavailable
// choice means every head (the behaviour before 2026-11-03). 'dining room only' has no option in
// HA yet; adding it there is all it takes.
// Keep identical to HEAD_CHOICE in 'Compute Fan Tilt Actions' and the Watchdog's Evaluate node.
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
const TEMP_MIN = 40;
const TEMP_MAX = 100;
// Heads report whole degrees F (device steps are 0.5 C), so a 64.5 cool clamp reads back as 64: treat a 0.5 difference as in sync.
const TEMP_EPS = 0.5;
// Hardware floors, one per thermal family. The heads work in 0.5 C steps: cooling rejects below
// 64.4 F and heating below 16.0 C / 60.8 F. HA advertises min_temp 60 because it rounds 60.8
// down, but it validates against the unrounded 16.0 C - so a 60 F write is rejected outright and
// the head silently keeps its previous setpoint, which the Watchdog then reads as a manual
// adjustment and latches the zone into override for. Verified against the real heads 2026-09-21:
// 60 and 60.5 rejected, 61 accepted (set_tmp 160).
// HEAT_FLOOR must stay equal to HEAT_FLOOR in the Watchdog's Evaluate node and HEAT_FLOOR_F in the app.
const COOL_FLOOR = 64.5;
const HEAT_FLOOR = 61;

const stateMap = {};
for (const item of $input.all()) { const e = item.json.entity_id; if (e) stateMap[e] = item.json; }
function st(e) { const s = stateMap[e]; return s ? s.state : undefined; }
function at(e, a) { const s = stateMap[e]; return s && s.attributes ? s.attributes[a] : undefined; }
function bad(v) { return v === undefined || v === null || v === '' || v === 'unavailable' || v === 'unknown'; }
// Strict numeric parse: plain numbers or plain numeric strings only (parseFloat would accept '70abc' and 'Infinity').
function num(v) {
  if (typeof v === 'number') return isFinite(v) ? v : NaN;
  if (typeof v === 'string' && /^\s*[-+]?\d+(\.\d+)?\s*$/.test(v)) return parseFloat(v);
  return NaN;
}
function plausible(t) { return !isNaN(t) && t >= TEMP_MIN && t <= TEMP_MAX; }
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
function inUse(zone) {
  const p = pausedHeads(zone);
  return HEADS[zone].filter(function (h) { return p.indexOf(h) === -1; });
}
function choiceZone(e) {
  for (const z in HEAD_CHOICE) { if (HEAD_CHOICE[z].helper === e) return z; }
  return '';
}
function fanPayload(raw) { return raw === 'Quiet' ? 'Quiet' : String(raw).toLowerCase(); }
function allowed(head, list, value) {
  const opts = at(head, list);
  return !Array.isArray(opts) || opts.indexOf(value) !== -1;
}

// Shared with 'Compute Fan Tilt Actions' in this same workflow: records when we last changed a
// head's hvac_mode/turn_on, so the fan/tilt feedback-sync branch can avoid mistaking the head's
// own post-mode-change swing/fan reset for a manual change and overwriting the user's UI selection.
const sd = $getWorkflowStaticData('global');
if (!sd.lastModeChangeAt) sd.lastModeChangeAt = {};
const now = Date.now();

const run = $('Init Run').first().json;
let trig = run.entity_id;
let recovered = false;
if (trig.indexOf('recovered:') === 0) { recovered = true; trig = trig.substring(10); }
if (trig === 'manual') trig = 'homeassistant.start';

function base(zone, head, service, attrName, attrValue, verify, gap) {
  return Object.assign({ entity_id: head, domain: 'climate', service: service, attr_name: attrName, attr_value: attrValue, zone: zone, source: 'sequencer', gap_s: gap }, verify);
}
function turnOn(zone, head) { return base(zone, head, 'turn_on', '', '', { verify_type: 'not_off' }, 2); }
function setMode(zone, head, mode) { return base(zone, head, 'set_hvac_mode', 'hvac_mode', mode, { verify_type: 'state_eq', verify_value: mode }, 2); }
function setTemp(zone, head, t) { return base(zone, head, 'set_temperature', 'temperature', String(t), { verify_type: 'attr_num', verify_attr: 'temperature', verify_value: t, tolerance: 0.6 }, 1); }
function setFan(zone, head, v) { return base(zone, head, 'set_fan_mode', 'fan_mode', String(v), { verify_type: 'attr_eq', verify_attr: 'fan_mode', verify_value: String(v) }, 1); }
function setSwing(zone, head, v) { return base(zone, head, 'set_swing_mode', 'swing_mode', String(v), { verify_type: 'attr_eq', verify_attr: 'swing_mode', verify_value: String(v) }, 1); }
function clearOverride(zone) {
  return { entity_id: 'input_boolean.override_' + zone, domain: 'input_boolean', service: 'turn_off', attr_name: '', attr_value: '', verify_type: 'state_eq', verify_value: 'off', zone: zone, source: 'sequencer', gap_s: 0 };
}
function done(actions, why) {
  if (actions.length === 0) return [{ json: { noop: true, reason: why, trig: trig } }];
  return actions.map(function (a) { return { json: a }; });
}
function holdOff(zone, head, out) {
  const hs = st(head);
  if (!bad(hs) && hs !== 'off') { out.push(setMode(zone, head, 'off')); sd.lastModeChangeAt[head] = now; }
}

const actions = [];

// The heads choice changed. Paused heads go off. A head coming back into use copies one that is
// running - on, mode, temperature, then the zone's fan and vane last, because a head powering up
// can come back with a default louver. With no head in use running (house off, zone off) the
// returning head stays off and joins in on the next schedule or mode change.
const choiceZ = choiceZone(trig);
if (choiceZ) {
  const zone = choiceZ;
  const using = inUse(zone);
  for (const head of pausedHeads(zone)) holdOff(zone, head, actions);
  let lead = '';
  for (const h of using) { const s = st(h); if (!bad(s) && s !== 'off') { lead = h; break; } }
  if (lead) {
    const lMode = st(lead);
    const lTemp = num(at(lead, 'temperature'));
    const fanRaw = st('input_select.' + zone + '_fan_mode');
    const tiltRaw = st('input_select.' + zone + '_tilt_mode');
    for (const head of using) {
      if (head === lead) continue;
      const hs = st(head);
      if (bad(hs)) continue;
      if (hs === 'off') { actions.push(turnOn(zone, head)); sd.lastModeChangeAt[head] = now; }
      if (hs !== lMode) { actions.push(setMode(zone, head, lMode)); sd.lastModeChangeAt[head] = now; }
      if (plausible(lTemp)) {
        const t = (lMode === 'cool' || lMode === 'dry') ? Math.max(lTemp, COOL_FLOOR) : Math.max(lTemp, HEAT_FLOOR);
        const cur = num(at(head, 'temperature'));
        if (hs === 'off' || isNaN(cur) || Math.abs(cur - t) > TEMP_EPS) actions.push(setTemp(zone, head, t));
      }
      if (!bad(fanRaw)) { const v = fanPayload(fanRaw); if (allowed(head, 'fan_modes', v)) actions.push(setFan(zone, head, v)); }
      if (!bad(tiltRaw)) { const v2 = String(tiltRaw); if (allowed(head, 'swing_modes', v2)) actions.push(setSwing(zone, head, v2)); }
    }
  }
  return done(actions, 'heads already match the choice (' + st(HEAD_CHOICE[zone].helper) + ')');
}

// Main-level twin-head mirror (mode + temperature). Only for plain head events, never for recovery events.
if (!recovered && MAIN.indexOf(trig) !== -1) {
  if (pausedHeads('main_level').length > 0) {
    // One head in use: nothing to mirror. A paused head that has been switched on (its remote, HA's
    // own UI) is put back off; changes on the head in use are left alone.
    if (pausedHeads('main_level').indexOf(trig) !== -1) holdOff('main_level', trig, actions);
    return done(actions, 'one main-level head in use: no mirror');
  }
  const src = trig;
  const dst = src === MAIN[0] ? MAIN[1] : MAIN[0];
  const sMode = st(src);
  const dMode = st(dst);
  if (bad(sMode) || bad(dMode)) return done(actions, 'mirror skipped: a main-level head is unavailable');
  if (sMode !== dMode) { actions.push(setMode('main_level', dst, sMode)); sd.lastModeChangeAt[dst] = now; }
  const sTemp = num(at(src, 'temperature'));
  const dTemp = num(at(dst, 'temperature'));
  if (sMode !== 'off' && !isNaN(sTemp) && sTemp > 50 && sTemp <= TEMP_MAX && (isNaN(dTemp) || Math.abs(sTemp - dTemp) > 0.5)) {
    actions.push(setTemp('main_level', dst, (sMode === 'cool' || sMode === 'dry') ? Math.max(sTemp, COOL_FLOOR) : sTemp));
  }
  return done(actions, 'main-level heads already in sync');
}

const globalMode = String(st('input_select.global_hvac_mode') || '').toLowerCase();
const sched = String(st('input_select.house_schedule_state') || '').toLowerCase();
if (MODES.indexOf(globalMode) === -1 || ['day', 'night', 'away'].indexOf(sched) === -1) {
  return done(actions, 'skipped: global mode or schedule state unavailable (' + globalMode + '/' + sched + ')');
}

// A house condition changed (Night early, Kids away, Boost). Only temperatures move: heads that are
// running in the zones it covers go to their new target. Nothing is switched on or off and no
// override is cleared, so a Boost can never wake a dining head that is off or paused.
const condTrig = CONDITIONS.filter(function (c) { return c.helper === trig; })[0];
if (condTrig) {
  if (globalMode === 'off') return done(actions, 'condition changed while the house is off');
  for (const zone of ZONES) {
    if (!condFor(condTrig, zone)) continue;
    if (st('input_boolean.zone_enable_' + zone) !== 'on' || st('input_boolean.override_' + zone) === 'on') continue;
    const t = zoneTarget(zone, globalMode, sched);
    if (isNaN(t)) continue;
    for (const head of inUse(zone)) {
      const hs = st(head);
      if (bad(hs) || hs === 'off') continue;
      const cur = num(at(head, 'temperature'));
      if (isNaN(cur) || Math.abs(cur - t) > TEMP_EPS) actions.push(setTemp(zone, head, t));
    }
  }
  return done(actions, 'condition changed: temperatures already match');
}

const isSchedule = trig === 'input_select.house_schedule_state';
const isGlobal = trig === 'input_select.global_hvac_mode';
const isZoneEnable = trig.indexOf('input_boolean.zone_enable_') === 0;
const isOverride = trig.indexOf('input_boolean.override_') === 0;
const isSetpoint = trig.indexOf('input_number.') === 0;
const isStart = trig === 'homeassistant.start' || trig === '';
const isStructural = isSchedule || isGlobal || isZoneEnable || isOverride || isStart || recovered;

// Anything else (a non-main head event, an unrelated entity) is not a sequencer trigger: do nothing.
if (!(isStructural || isSetpoint)) return done(actions, 'ignored: not a sequencer trigger (' + trig + ')');

let targetZones = ZONES.slice();
if (recovered) {
  if (!HEAD_ZONE[trig]) return done(actions, 'ignored: recovered entity is not a known head (' + trig + ')');
  targetZones = [HEAD_ZONE[trig]];
} else if (isSetpoint || isZoneEnable || isOverride) {
  let matched = '';
  for (const z of ZONES) { if (trig.indexOf(z) !== -1) { matched = z; break; } }
  if (!matched) return done(actions, 'ignored: entity does not belong to a known zone (' + trig + ')');
  targetZones = [matched];
}

for (const zone of targetZones) {
  // A paused head is held off whatever the zone's Auto and Override say.
  if (isStructural) for (const head of pausedHeads(zone)) holdOff(zone, head, actions);
  const enabled = st('input_boolean.zone_enable_' + zone) === 'on';
  let overrideActive = st('input_boolean.override_' + zone) === 'on';
  if (!enabled) {
    // Schedule change clears every override, including those of disabled zones (matches the original automation).
    if (isSchedule && overrideActive) actions.push(clearOverride(zone));
    continue;
  }
  if (overrideActive && (isSchedule || (isZoneEnable && trig.indexOf(zone) !== -1))) {
    actions.push(clearOverride(zone));
    overrideActive = false;
  }
  if (overrideActive) continue;

  // A setpoint is only needed when we will command a temperature. With global mode off we still must turn heads off.
  // House conditions included: Kids away, Night early, Boost.
  let targetTemp = NaN;
  if (globalMode !== 'off') {
    targetTemp = zoneTarget(zone, globalMode, sched);
    if (isNaN(targetTemp)) continue;
  }

  for (const head of inUse(zone)) {
    const hs = st(head);
    if (bad(hs)) continue;
    if (isStructural) {
      if (hs === 'off' && globalMode !== 'off') { actions.push(turnOn(zone, head)); sd.lastModeChangeAt[head] = now; }
      if (hs !== globalMode) { actions.push(setMode(zone, head, globalMode)); sd.lastModeChangeAt[head] = now; }
    }
    if (globalMode !== 'off') {
      const cur = num(at(head, 'temperature'));
      if (isNaN(cur) || Math.abs(cur - targetTemp) > TEMP_EPS) actions.push(setTemp(zone, head, targetTemp));
    }
  }
}
return done(actions, 'nothing to change');
