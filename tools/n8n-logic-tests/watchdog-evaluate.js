const ZONES = {
  main_level: ['climate.hp_living_room', 'climate.hp_dining_room'],
  anthony: ['climate.hp_anthony'],
  autumn: ['climate.hp_autumn'],
  bedroom_1: ['climate.hp_bedroom'],
  bedroom_2: ['climate.hp_bedroom_2'],
  basement: ['climate.hp_basement']
};
const ZONE_NAMES = ['main_level', 'anthony', 'autumn', 'bedroom_1', 'bedroom_2', 'basement'];
const ALL_HEADS = ['climate.hp_living_room', 'climate.hp_dining_room', 'climate.hp_anthony', 'climate.hp_autumn', 'climate.hp_bedroom', 'climate.hp_bedroom_2', 'climate.hp_basement'];

// TWO outdoor units. Each is an independent compressor serving one thermal family at a time, but
// the two are free to disagree with each other: heat on unit 1 while unit 2 cools is physically
// legal. Treating all seven heads as one family turns off correctly running equipment.
const UNITS = {
  unit1: ['climate.hp_living_room', 'climate.hp_bedroom', 'climate.hp_bedroom_2', 'climate.hp_basement'],
  unit2: ['climate.hp_dining_room', 'climate.hp_anthony', 'climate.hp_autumn']
};
const UNIT_NAMES = ['unit1', 'unit2'];
// Main level spans both units and is mirrored, so each unit is led by its own main-level head.
const UNIT_LEAD = { unit1: 'climate.hp_living_room', unit2: 'climate.hp_dining_room' };
const MAIN_HEADS = ['climate.hp_living_room', 'climate.hp_dining_room'];
// Which heads a multi-head zone is using. Keep identical to HEAD_CHOICE in the Controller's
// 'Compute Sequencer Actions' (which explains it). A paused head is meant to be off: it is left out
// of the override check (A) and the split check (D), and if it is found running it is quietly
// switched off again (E). For CHOICE_GRACE_MS after the choice changes the zone is not judged at
// all, so a head being brought back (or paused) by the Controller is not mistaken for a manual
// change half-way through.
const HEAD_CHOICE = {
  main_level: {
    helper: 'input_select.main_level_heads',
    paused: {
      'living room only': ['climate.hp_dining_room'],
      'dining room only': ['climate.hp_living_room']
    }
  }
};
const CHOICE_GRACE_MS = 180000;
const PAUSED_ON_HOLD_MS = 45000;

const UNAVAILABLE = ['unavailable', 'unknown'];
// How long a difference must persist before it is acted on. Note that the trigger runs once a
// minute, so any hold below 60s behaves identically: the difference is seen on one tick and acted
// on at the next. Lowering this alone does not make the response faster - the tick interval is
// what governs that. It is kept under a minute so a single missed tick does not add a whole extra
// cycle, and above zero so a state caught mid-transition is not treated as deliberate.
const DRIFT_HOLD_MS = 45000;
const CONFLICT_HOLD_MS = 45000;
const SPLIT_HOLD_MS = 115000;
const SPLIT_RENOTIFY_MS = 1800000;
const RECENT_WRITE_MS = 30000;
const UNAVAILABLE_HOLD_MS = 60000;
const STALE_RUN_MS = 180000;
const TEMP_TOLERANCE = 0.6;
const COOL_FLOOR = 64.5;
// The heads work in 0.5 C steps and their heating range starts at 16.0 C. HA advertises
// min_temp 60 because it rounds 60.8 F down, but validates against the unrounded 16.0 C, so
// anything under 60.8 F is rejected outright and the head silently keeps its old setpoint.
// Without this floor the watchdog compares the head against a target that can never be written,
// scores the difference as a manual adjustment, and latches the zone into override every
// transition. Verified against the real heads 2026-09-21: 60 and 60.5 rejected, 61 accepted.
// Must stay equal to HEAT_FLOOR in the Controller and HEAT_FLOOR_F in the app.
const HEAT_FLOOR = 61;
// How long section C's own turn-off excuses a head for being off. Covers a few ticks so the
// zone is not then reported as a manual change (see sd.selfOffAt below).
const SELF_OFF_GRACE_MS = 180000;
// Any lock claiming to expire further out than this is treated as corrupt rather than held.
const MAX_LOCK_TTL_MS = 300000;
const DEG = String.fromCharCode(176) + 'F';
const NL = String.fromCharCode(10);
const DOT = String.fromCharCode(8226);

const HEAD_ZONE = {};
for (const z of ZONE_NAMES) { for (const h of ZONES[z]) { HEAD_ZONE[h] = z; } }
const HEAD_UNIT = {};
for (const u of UNIT_NAMES) { for (const h of UNITS[u]) { HEAD_UNIT[h] = u; } }

const now = Date.now();
const sd = $getWorkflowStaticData('global');
if (!sd.driftSince) sd.driftSince = {};
if (!sd.conflictSince) sd.conflictSince = {};
if (!sd.notified) sd.notified = {};
if (!sd.selfOffAt) sd.selfOffAt = {};
if (!sd.pausedOnSince) sd.pausedOnSince = {};

// Forget our own turn-offs once they are older than the grace period, so the map cannot grow.
for (const k of Object.keys(sd.selfOffAt)) {
  if (now - sd.selfOffAt[k] > SELF_OFF_GRACE_MS) delete sd.selfOffAt[k];
}

const states = {};
for (const it of $('Check State Fetch Health').all()) {
  const eid = it.json ? it.json.entity_id : undefined;
  if (eid) states[eid] = it.json;
}

function stateOf(eid) {
  return states[eid] ? states[eid].state : undefined;
}

function nameOf(eid) {
  const s = states[eid];
  return (s && s.attributes && s.attributes.friendly_name) ? s.attributes.friendly_name : eid;
}

function st(eid) { return stateOf(eid); }
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
// True while a condition covering the zone changed less than CHOICE_GRACE_MS ago, so heads still
// moving to the new target are not mistaken for a manual change.
function condSettling(zone) {
  for (const c of CONDITIONS) {
    if (!condFor(c, zone)) continue;
    const s = states[c.helper];
    const t = s && s.last_changed ? Date.parse(s.last_changed) : NaN;
    if (!isNaN(t) && (now - t) < CHOICE_GRACE_MS) return true;
  }
  return false;
}

function titleZone(z) {
  return z.split('_').map(function (w) { return w.charAt(0).toUpperCase() + w.slice(1); }).join(' ');
}

function pausedHeads(zone) {
  const c = HEAD_CHOICE[zone];
  if (!c) return [];
  return c.paused[String(stateOf(c.helper) || '').toLowerCase()] || [];
}
// True while the zone's heads choice changed less than CHOICE_GRACE_MS ago.
function choiceSettling(zone) {
  const c = HEAD_CHOICE[zone];
  const s = c ? states[c.helper] : undefined;
  const t = s && s.last_changed ? Date.parse(s.last_changed) : NaN;
  return !isNaN(t) && (now - t) < CHOICE_GRACE_MS;
}

// A multi-split serves ONE thermal family at a time from a single outdoor unit. Dry is
// refrigeration on these heads, so heat + dry is a real conflict and not a cosmetic one.
function familyOf(mode) {
  if (mode === 'heat') return 'heat';
  if (mode === 'cool' || mode === 'dry') return 'cool';
  return 'neutral';
}

function svc(domain, service, n1, v1, n2, v2) {
  return { json: { domain: domain, service: service, attr1_name: n1, attr1_value: v1, attr2_name: n2, attr2_value: v2 } };
}

// Telegram, via the HVAC Notify webhook. HA has no telegram_bot integration; the bot credential
// lives in n8n, so every alert leaves through rest_command.n8n_hvac_notify.
function notify(title, message) {
  return svc('rest_command', 'n8n_hvac_notify', 'title', title, 'message', message);
}

// A timestamp we can believe. A value in the FUTURE is the dangerous case: 'now - ts' goes
// negative, which satisfies every '<= WINDOW' test forever, so a single bad row used to pin
// recentWrite true and silently disable sections A, C and D permanently - no alert, no error,
// nothing to notice. Clock skew between the n8n host and whatever stamped the row, or one
// seconds-vs-milliseconds slip in any writer, was enough to do it.
function sane(ts) {
  return (!isNaN(ts) && ts <= now + 5000) ? ts : NaN;
}

// Lock rows. last_write_at is kept PER HEAD: the conflict and split timers below compare against
// it, and taking a MAX across every row meant a write to any unrelated head reset the timer for
// a head nobody had touched - so a genuine conflict could never reach its hold during a
// multi-zone sweep. The global max is still used for the coarse 'recent write' skip, where
// 'anything was written just now' is exactly the question being asked.
let expiresAt = NaN;
let lastWriteAt = NaN;
const writeAtByKey = {};
for (const li of $input.all()) {
  const j = li.json || {};
  const e = sane(parseFloat(j.expires_at));
  const w = sane(parseFloat(j.last_write_at));
  if (j.lock_key) writeAtByKey[j.lock_key] = w;
  if (!isNaN(e) && (isNaN(expiresAt) || e > expiresAt)) expiresAt = e;
  if (!isNaN(w) && (isNaN(lastWriteAt) || w > lastWriteAt)) lastWriteAt = w;
}
function writeAtFor(head) {
  const v = writeAtByKey['entity:' + head];
  return v === undefined ? NaN : v;
}
function writeAtForHeads(heads) {
  let best = NaN;
  for (const h of heads) {
    const v = writeAtFor(h);
    if (!isNaN(v) && (isNaN(best) || v > best)) best = v;
  }
  return best;
}

const lockHeld = !isNaN(expiresAt) && expiresAt > now && expiresAt <= now + MAX_LOCK_TTL_MS;
const recentWrite = !isNaN(lastWriteAt) && (now - lastWriteAt) <= RECENT_WRITE_MS;

const globalRaw = stateOf('input_select.global_hvac_mode');
const schedRaw = stateOf('input_select.house_schedule_state');
const globalMode = typeof globalRaw === 'string' ? globalRaw.trim().toLowerCase() : '';
const sched = typeof schedRaw === 'string' ? schedRaw.trim().toLowerCase() : '';

let skipReason = '';
if (!globalMode || UNAVAILABLE.indexOf(globalMode) !== -1) skipReason = 'global mode unavailable';
else if (!sched || UNAVAILABLE.indexOf(sched) !== -1) skipReason = 'schedule state unavailable';
else if (lockHeld) skipReason = 'write lock or run mutex held';
else if (recentWrite) skipReason = 'recent write';

// A lock/recent-write skip is routine and often unrelated to a given zone: pause evaluation but
// keep each zone's accumulated drift-start time, so a genuinely drifting zone isn't given a fresh
// grace period every time the house is busy elsewhere. Only a truly invalid global-mode or
// schedule state, or a long gap since the last run, clears the whole map.
const transientSkip = skipReason === 'write lock or run mutex held' || skipReason === 'recent write';
const stale = sd.lastEvalAt && (now - sd.lastEvalAt) > STALE_RUN_MS;
sd.lastEvalAt = now;
if ((skipReason && !transientSkip) || stale) { sd.driftSince = {}; sd.conflictSince = {}; sd.pausedOnSince = {}; delete sd.splitSince; delete sd.splitNotifiedAt; }

const out = [];
const conflictedZones = {};

// C) Cross-head thermal conflict, evaluated PER OUTDOOR UNIT. Runs before the drift check so a
// head that is about to be turned off is not also flagged as a manual override.
if (!skipReason) {
  const globalFamily = familyOf(globalMode);
  const unitFamily = {};
  const unitSource = {};

  for (const unit of UNIT_NAMES) {
    if (globalFamily !== 'neutral') {
      unitFamily[unit] = globalFamily;
      unitSource[unit] = '';
      continue;
    }
    // Global mode off means scheduling is paused, not that nothing may run. Each unit then
    // decides for itself, led by its own main-level head.
    let fam = 'neutral';
    let src = '';
    const lead = UNIT_LEAD[unit];
    if (familyOf(stateOf(lead)) !== 'neutral') {
      fam = familyOf(stateOf(lead));
      src = nameOf(lead);
    } else {
      for (const head of UNITS[unit]) {
        if (familyOf(stateOf(head)) !== 'neutral') {
          fam = familyOf(stateOf(head));
          src = nameOf(head);
          break;
        }
      }
    }
    unitFamily[unit] = fam;
    unitSource[unit] = src;
  }

  const offenders = {};
  for (const head of ALL_HEADS) {
    const s = states[head];
    const unit = HEAD_UNIT[head];
    const fam = unitFamily[unit];
    if (!s || fam === 'neutral') { delete sd.conflictSince[head]; continue; }
    const hf = familyOf(s.state);
    if (hf === 'neutral' || hf === fam) { delete sd.conflictSince[head]; continue; }
    if (sd.conflictSince[head] === undefined) sd.conflictSince[head] = now;
    // A write to THIS head that landed after we started timing means the sequencer is still
    // reconciling this transition. The lock and recent-write skips cover the middle of a
    // transition but not its leading edge, where the event has been forwarded and nothing has
    // taken a lock yet.
    const headWriteAt = writeAtFor(head);
    if (!isNaN(headWriteAt) && headWriteAt > sd.conflictSince[head]) {
      sd.conflictSince[head] = now;
      continue;
    }
    if (now - sd.conflictSince[head] >= CONFLICT_HOLD_MS) {
      offenders[HEAD_ZONE[head]] = { head: head, mode: String(s.state).toUpperCase(), unit: unit };
    }
  }

  const conflictLines = [];
  for (const zone of Object.keys(offenders)) {
    const info = offenders[zone];
    // Turn off every head in the zone explicitly. Main level spans both outdoor units and is
    // mirrored, so switching one head off would drag the other off anyway - doing it here makes
    // the outcome deterministic instead of a race with the mirror.
    for (const h of ZONES[zone]) {
      out.push(svc('climate', 'turn_off', 'entity_id', h, 'entity_id', h));
      delete sd.conflictSince[h];
      // Remember that WE did this. The watchdog's own writes never stamp last_write_at (its lock
      // node is a read-only get), so without this the next tick saw a head sitting off while the
      // house asked for heat, scored it as drift, and latched a manual override - telling the
      // user by Telegram that somebody had turned it off by hand. Nobody had.
      sd.selfOffAt[h] = now;
    }
    const fam = unitFamily[info.unit].toUpperCase();
    const because = unitSource[info.unit]
      ? unitSource[info.unit] + ' is running ' + fam + ' on the same outdoor unit'
      : 'the house mode is ' + fam;
    const scope = ZONES[zone].length > 1 ? titleZone(zone) + ' (both heads)' : nameOf(info.head);
    conflictLines.push(DOT + ' ' + scope + ' was set to ' + info.mode + ' while ' + because);
    conflictedZones[zone] = true;
    delete sd.driftSince[zone];
  }

  // Combined, for the same reason as the override alert below: several heads can trip on one tick.
  if (conflictLines.length > 0) {
    out.push(notify(
      conflictLines.length === 1 ? 'HVAC conflict' : 'HVAC conflict: ' + conflictLines.length + ' zones',
      conflictLines.join(NL) + NL + NL + 'Those cannot run at the same time, so they have been turned off.'
    ));
  }
}

// E) A paused head found running (its remote, HA's own UI, or a Controller event that was missed)
// is switched off again, quietly: the heads choice says it should be off. Only once it has been
// seen on for PAUSED_ON_HOLD_MS, and never while the choice is still settling.
if (!skipReason) {
  const pausedNow = {};
  for (const zone of ZONE_NAMES) {
    if (conflictedZones[zone] || choiceSettling(zone)) continue;
    for (const head of pausedHeads(zone)) pausedNow[head] = zone;
  }
  for (const head of ALL_HEADS) {
    const s = stateOf(head);
    if (pausedNow[head] === undefined || s === undefined || s === 'off' || UNAVAILABLE.indexOf(s) !== -1) { delete sd.pausedOnSince[head]; continue; }
    if (sd.pausedOnSince[head] === undefined) { sd.pausedOnSince[head] = now; continue; }
    if (now - sd.pausedOnSince[head] >= PAUSED_ON_HOLD_MS) {
      out.push(svc('climate', 'turn_off', 'entity_id', head, 'entity_id', head));
      sd.selfOffAt[head] = now;
      delete sd.pausedOnSince[head];
    }
  }
}

// D) Main-level twin split. The living room and dining heads are one open space on two different
// outdoor units, kept identical by the sequencer's mirror. Because they sit on separate units,
// section C cannot see a split between them. A split means the mirror failed, so report it rather
// than shutting the main level down in heating season. Not checked while one head is paused:
// they are meant to differ then, and E deals with a paused head that is running.
if (!skipReason) {
  const famA = familyOf(stateOf(MAIN_HEADS[0]));
  const famB = familyOf(stateOf(MAIN_HEADS[1]));
  const split = famA !== 'neutral' && famB !== 'neutral' && famA !== famB && pausedHeads('main_level').length === 0;
  if (!split) {
    delete sd.splitSince;
    delete sd.splitNotifiedAt;
  } else {
    if (sd.splitSince === undefined) sd.splitSince = now;
    // Only a write to one of the two main heads means this split is still being reconciled.
    const mainWriteAt = writeAtForHeads(MAIN_HEADS);
    if (!isNaN(mainWriteAt) && mainWriteAt > sd.splitSince) sd.splitSince = now;
    else if (now - sd.splitSince >= SPLIT_HOLD_MS && (sd.splitNotifiedAt === undefined || (now - sd.splitNotifiedAt) >= SPLIT_RENOTIFY_MS)) {
      sd.splitNotifiedAt = now;
      out.push(notify('HVAC main level split', nameOf(MAIN_HEADS[0]) + ' is ' + String(stateOf(MAIN_HEADS[0])).toUpperCase() + ' while ' + nameOf(MAIN_HEADS[1]) + ' is ' + String(stateOf(MAIN_HEADS[1])).toUpperCase() + '. They share one open space and should match, so the sync has not taken. Nothing has been switched off.'));
    }
  }
}

// A) Manual-override drift.
//
// Any difference between a head and what the app asks for counts: the mode, which now includes a
// head somebody switched OFF, and the setpoint. A head that is off used to be skipped outright,
// which is why turning heads off never raised an override. Paused heads are left to E.
if (!skipReason) {
  const drifted = [];
  for (const zone of ZONE_NAMES) {
    if (conflictedZones[zone]) { delete sd.driftSince[zone]; continue; }
    if (choiceSettling(zone) || condSettling(zone)) { delete sd.driftSince[zone]; continue; }
    const enabled = stateOf('input_boolean.zone_enable_' + zone) === 'on';
    const overrideOff = stateOf('input_boolean.override_' + zone) === 'off';
    if (!enabled || !overrideOff) { delete sd.driftSince[zone]; continue; }

    // The value the sequencer will really write: the zone's schedule helper after the house
    // conditions, clamped to what the equipment accepts. The setpoint only means something while
    // the house is conditioning; with the house off there is nothing to compare a temperature
    // against, and requiring one here would skip the zone before the mode check below ever ran.
    const expected = zoneTarget(zone, globalMode, sched);
    if (globalMode !== 'off' && isNaN(expected)) { delete sd.driftSince[zone]; continue; }

    const paused = pausedHeads(zone);
    let drifting = false;
    let reason = '';
    for (const head of ZONES[zone]) {
      if (paused.indexOf(head) !== -1) continue;
      const s = states[head];
      // A head we cannot hear from is section B's business. Latching an override on a wifi
      // dropout would suspend the zone for something that is not a manual act at all.
      if (!s || UNAVAILABLE.indexOf(s.state) !== -1) continue;

      // A head section C switched off a moment ago is not a manual change. See sd.selfOffAt.
      if (s.state === 'off' && sd.selfOffAt[head] !== undefined) continue;

      if (s.state !== globalMode) {
        drifting = true;
        reason = s.state === 'off'
          ? nameOf(head) + ' was turned off while the house is set to ' + globalMode.toUpperCase()
          : nameOf(head) + ' is set to ' + String(s.state).toUpperCase() + ' while the house is set to ' + globalMode.toUpperCase();
        continue;
      }

      if (globalMode === 'off') continue;
      const attrs = s.attributes || {};
      const actual = parseFloat(attrs.temperature);
      const diff = Math.round(Math.abs(actual - expected) * 100) / 100;
      if (!isNaN(diff) && diff > TEMP_TOLERANCE) {
        drifting = true;
        if (!reason) reason = nameOf(head) + ' is set to ' + actual + DEG + ' instead of ' + expected + DEG;
      }
    }

    if (!drifting) { delete sd.driftSince[zone]; continue; }
    if (sd.driftSince[zone] === undefined) sd.driftSince[zone] = now;
    if (now - sd.driftSince[zone] >= DRIFT_HOLD_MS) {
      drifted.push({ zone: zone, reason: reason });
      delete sd.driftSince[zone];
    }
  }

  // One message for the whole tick. Zones are evaluated together, so switching four heads off
  // used to produce four separate Telegrams arriving at once. The override booleans are still
  // written one per zone - only the alert is combined.
  if (drifted.length > 0) {
    for (const d of drifted) {
      out.push(svc('input_boolean', 'turn_on', 'entity_id', 'input_boolean.override_' + d.zone, 'entity_id', 'input_boolean.override_' + d.zone));
    }
    const lines = drifted.map(function (d) {
      return DOT + ' ' + (d.reason || titleZone(d.zone) + ' was adjusted by hand');
    });
    const names = drifted.map(function (d) { return titleZone(d.zone); }).join(', ');
    const title = drifted.length === 1 ? 'HVAC Override' : 'HVAC Override: ' + drifted.length + ' zones';
    out.push(notify(
      title,
      lines.join(NL) + NL + NL +
      'Automation is suspended for ' + names + ' until you clear the override, which puts it back to the app settings.'
    ));
  }
}

// B) Head unavailable notifier (always runs).
//
// Coalesced like A, C and D. This was the one class still sending a message per head, so an AP
// or router reboot that took all seven offline in the same minute fired seven rest_command calls
// into the same chat in the same second - against Telegram's ~1 msg/sec per-chat guidance, with
// no onError and no retry on the send, so a 429 simply lost the alert.
const unavailableNames = [];
for (const head of ALL_HEADS) {
  const s = states[head];
  if (!s) continue;
  if (UNAVAILABLE.indexOf(s.state) !== -1) {
    const changedAt = Date.parse(s.last_changed);
    if (!isNaN(changedAt) && (now - changedAt) >= UNAVAILABLE_HOLD_MS && sd.notified[head] !== s.last_changed) {
      unavailableNames.push(nameOf(head));
      sd.notified[head] = s.last_changed;
    }
  } else {
    delete sd.notified[head];
  }
}
if (unavailableNames.length === 1) {
  out.push(notify('HVAC Head Unavailable', unavailableNames[0] + ' has been unavailable for 1+ minute (likely WiFi dropout). Zone control is unaffected - no manual override was set.'));
} else if (unavailableNames.length > 1) {
  out.push(notify(
    'HVAC Heads Unavailable: ' + unavailableNames.length,
    unavailableNames.map(function (n) { return DOT + ' ' + n; }).join(NL) + NL + NL +
    'All unavailable for 1+ minute (likely a WiFi or AP dropout). Zone control is unaffected - no manual overrides were set.'
  ));
}

return out;
