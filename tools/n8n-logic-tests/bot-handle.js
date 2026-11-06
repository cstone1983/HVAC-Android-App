const ev = $('Normalize').first().json;
const NL = String.fromCharCode(10);
const ZONES = [
  { id: 'main_level', name: 'Main Level', heads: ['climate.hp_living_room', 'climate.hp_dining_room'] },
  { id: 'anthony', name: 'Anthony', heads: ['climate.hp_anthony'] },
  { id: 'autumn', name: 'Autumn', heads: ['climate.hp_autumn'] },
  { id: 'bedroom_1', name: 'Bedroom 1', heads: ['climate.hp_bedroom'] },
  { id: 'bedroom_2', name: 'Bedroom 2', heads: ['climate.hp_bedroom_2'] },
  { id: 'basement', name: 'Basement', heads: ['climate.hp_basement'] }
];
const FAN = ['Quiet', 'Low', 'Medium', 'High', 'Auto'];
const TILT = ['Highest', 'High', 'Low', 'Lowest', 'Vertical Swing'];
const WATER = ['eco', 'heat_pump', 'high_demand'];
const MODES = ['heat', 'cool', 'dry', 'off'];
const MODE_ICON = { heat: '🔥', cool: '❄️', dry: '💧', off: '⏻' };
const WH = 'water_heater.heat_pump_water_heater';
const stateMap = {};
for (const it of $input.all()) { const e = it.json && it.json.entity_id; if (e) stateMap[e] = it.json; }
function st(e) { const s = stateMap[e]; return s ? s.state : undefined; }
function at(e, a) { const s = stateMap[e]; return s && s.attributes ? s.attributes[a] : undefined; }
function esc(s) { return String(s === undefined || s === null ? '' : s).split('&').join('&amp;').split('<').join('&lt;').split('>').join('&gt;'); }
function bad(v) { return v === undefined || v === null || v === '' || v === 'unavailable' || v === 'unknown'; }
function num(v) { const n = parseFloat(v); return isNaN(n) ? NaN : n; }
function setState(e, v) { stateMap[e] = Object.assign({}, stateMap[e] || { entity_id: e, attributes: {} }, { state: String(v) }); }
const COOL_FLOOR = 64.5;
const HEAT_FLOOR = 61;
// House conditions (2026-11-06): the switches on the app's Heat pump settings page that change
// which temperature a zone should be at. Keep identical in the Controller ('Compute Sequencer
// Actions'), the Watchdog ('Evaluate') and the Bot ('Check Verify', 'Handle'), or one of them
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
function gmode() { const m = String(st('input_select.global_hvac_mode') || '').toLowerCase(); return MODES.indexOf(m) === -1 ? '?' : m; }
function sched() { const s = String(st('input_select.house_schedule_state') || '').toLowerCase(); return ['day', 'night', 'away'].indexOf(s) === -1 ? '?' : s; }
function setpointId(z, mode, sc) { return 'input_number.' + z + '_' + sc + '_' + (mode === 'cool' ? 'cool' : 'temp'); }
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
function base(o) { return { json: Object.assign({ view: 'main', text: '', f: {}, ha: null, verify: null, chat_id: ev.chat_id, delete_id: ev.message_id || null }, o) }; }
function haCall(domain, service, entity, xn, xv) { return { domain: domain, service: service, a1n: 'entity_id', a1v: entity, a2n: xn || 'entity_id', a2v: xn ? String(xv) : entity, a3n: 'entity_id', a3v: entity }; }
function mainView(extra) { return [base({ view: 'main', text: statusText(extra), f: mainF() })]; }
function zonesView(extra) {
  const f = {};
  ZONES.forEach(function (z, i) {
    const en = st('input_boolean.zone_enable_' + z.id) === 'on';
    const ov = st('input_boolean.override_' + z.id) === 'on';
    const hs = st(z.heads[0]);
    f['zt' + i] = (!en ? '🚫 ' : ov ? '✋ ' : (bad(hs) || hs === 'off') ? '⚪ ' : '✅ ') + z.name;
  });
  let t = '<b>Zones</b> — tap one to control it.' + NL + '✅ running  ⚪ off  ✋ manual override  🚫 disabled';
  if (extra) t += NL + NL + extra;
  return [base({ view: 'zones', text: t, f: f })];
}
function zoneView(z, extra) {
  // The block the zone is really on: Kids away and Night early can change it.
  const m = gmode(), sc = sched() === '?' ? '?' : zoneSched(z.id, sched());
  const enabled = st('input_boolean.zone_enable_' + z.id) === 'on';
  const ovr = st('input_boolean.override_' + z.id) === 'on';
  const spId = setpointId(z.id, m, sc);
  const spOk = m !== 'off' && m !== '?' && sc !== '?' && !bad(st(spId));
  const sp = spOk ? num(st(spId)) : NaN;
  const mn = num(at(spId, 'min')), mx = num(at(spId, 'max'));
  const step = num(at(spId, 'step')) || 1;
  const minusV = sp - step, plusV = sp + step;
  const f = {};
  f.ovr_t = ovr ? '✋ Override ON - tap to release' : '✋ Override off - tap to hold';
  f.ovr_d = 'zo:' + z.id + ':' + (ovr ? '0' : '1');
  f.en_t = enabled ? '✅ Enabled - tap to disable' : '🚫 Disabled - tap to enable';
  f.en_d = 'ze:' + z.id + ':' + (enabled ? '0' : '1');
  f.sp_m_t = '➖ ' + step + '°';
  f.sp_p_t = '➕ ' + step + '°';
  f.sp_m_d = (spOk && (isNaN(mn) || minusV >= mn)) ? 'zs:' + z.id + ':' + minusV : 'n:' + z.id;
  f.sp_p_d = (spOk && (isNaN(mx) || plusV <= mx)) ? 'zs:' + z.id + ':' + plusV : 'n:' + z.id;
  f.fan_t = '🌀 Fan: ' + (st('input_select.' + z.id + '_fan_mode') || '?');
  f.fan_d = 'zf:' + z.id;
  f.tilt_t = '↕ Tilt: ' + (st('input_select.' + z.id + '_tilt_mode') || '?');
  f.tilt_d = 'zt:' + z.id;
  let t = '<b>' + z.name + '</b>' + NL + zoneLine(z).replace('• <b>' + z.name + '</b>: ', '') + NL + 'Setpoint (' + sc + ', ' + (m === 'cool' ? 'cool' : 'heat') + '): ' + (spOk ? sp + '°' : 'n/a while mode is ' + m);
  if (extra) t += NL + NL + extra;
  return [base({ view: 'zone', text: t, f: f })];
}
function optionsView(z, kind) {
  const list = kind === 'fan' ? FAN : TILT;
  const cur = st('input_select.' + z.id + '_' + kind + '_mode');
  const f = {};
  list.forEach(function (o, i) { f['o' + i + '_t'] = (cur === o ? '✓ ' : '') + o; f['o' + i + '_d'] = (kind === 'fan' ? 'zfs:' : 'zts:') + z.id + ':' + i; });
  f.back_d = 'z:' + z.id;
  return [base({ view: 'options', text: '<b>' + z.name + '</b> — choose ' + (kind === 'fan' ? 'fan speed' : 'vane position') + '.', f: f })];
}
function waterView(extra) {
  const cur = st('input_select.water_heater_mode');
  const f = {};
  WATER.forEach(function (w, i) { f['w' + i + '_t'] = (cur === w ? '✓ ' : '') + w.split('_').join(' '); f['w' + i + '_d'] = 'w:' + i; });
  let t = '🚿 <b>Water heater</b>' + NL + 'Mode: ' + esc(cur) + NL + 'Target: ' + esc(at(WH, 'temperature')) + '° · state: ' + esc(st(WH));
  if (extra) t += NL + NL + extra;
  return [base({ view: 'water', text: t, f: f })];
}

if ($input.all().length < 20) return [base({ view: 'text', text: '⚠️ Home Assistant returned too little data. Try again in a moment.' })];
if (ev.kind === 'cmd') {
  if (ev.cmd === 'zones') return zonesView();
  if (ev.cmd === 'water') return waterView();
  return mainView(ev.cmd === '' ? 'I only understand the buttons and these commands: /status /zones /water' : '');
}
const p = String(ev.data || '').split(':');
const zone = p[1] ? ZONES.filter(function (z) { return z.id === p[1]; })[0] : undefined;
const needZone = ['z', 'zo', 'ze', 'zs', 'zf', 'zt', 'zfs', 'zts'].indexOf(p[0]) !== -1;
if (needZone && !zone) return mainView('Unknown zone.');
switch (p[0]) {
  case 'r': return mainView();
  case 'v': {
    if (p[1] === 'zones') return zonesView();
    if (p[1] === 'water') return waterView();
    return mainView();
  }
  case 'm': {
    const mode = p[1];
    if (MODES.indexOf(mode) === -1) return mainView('Unknown mode.');
    const same = gmode() === mode;
    if (!same) setState('input_select.global_hvac_mode', mode);
    const r = mainView('⏳ ' + (same ? 'Already in ' : 'Applying ') + '<b>' + mode + '</b> - checking every zone...');
    if (!same) r[0].json.ha = haCall('input_select', 'select_option', 'input_select.global_hvac_mode', 'option', mode);
    r[0].json.verify = { mode: mode, started_at: Date.now() };
    return r;
  }
  case 'z': return zoneView(zone);
  case 'zo': {
    const on = p[2] === '1';
    const e = 'input_boolean.override_' + zone.id;
    setState(e, on ? 'on' : 'off');
    const r = zoneView(zone, on ? '✔ Manual override ON - automation leaves this zone alone.' : '✔ Override released - automation resumes for this zone.');
    r[0].json.ha = haCall('input_boolean', on ? 'turn_on' : 'turn_off', e);
    return r;
  }
  case 'ze': {
    const on = p[2] === '1';
    const e = 'input_boolean.zone_enable_' + zone.id;
    setState(e, on ? 'on' : 'off');
    const r = zoneView(zone, on ? '✔ Zone enabled.' : '✔ Zone disabled - automation ignores it.');
    r[0].json.ha = haCall('input_boolean', on ? 'turn_on' : 'turn_off', e);
    return r;
  }
  case 'zs': {
    const m = gmode(), sc = sched() === '?' ? '?' : zoneSched(zone.id, sched());
    const spId = setpointId(zone.id, m, sc);
    const val = num(p[2]);
    if (m === 'off' || m === '?' || sc === '?' || bad(st(spId))) return zoneView(zone, 'Set a mode first - no setpoint applies while HVAC is off.');
    const mn = num(at(spId, 'min')), mx = num(at(spId, 'max'));
    if (isNaN(val) || (!isNaN(mn) && val < mn) || (!isNaN(mx) && val > mx)) return zoneView(zone, 'That setpoint is outside the allowed range.');
    setState(spId, val);
    const r = zoneView(zone, '✔ Setpoint set to ' + val + '° (' + sc + ' ' + (m === 'cool' ? 'cool' : 'heat') + ' schedule).');
    r[0].json.ha = haCall('input_number', 'set_value', spId, 'value', val);
    return r;
  }
  case 'zf': return optionsView(zone, 'fan');
  case 'zt': return optionsView(zone, 'tilt');
  case 'zfs': {
    const i = parseInt(p[2], 10);
    if (isNaN(i) || i < 0 || i >= FAN.length) return zoneView(zone, 'Unknown fan option.');
    const e = 'input_select.' + zone.id + '_fan_mode';
    setState(e, FAN[i]);
    const r = zoneView(zone, '✔ Fan set to ' + FAN[i] + '.');
    r[0].json.ha = haCall('input_select', 'select_option', e, 'option', FAN[i]);
    return r;
  }
  case 'zts': {
    const i = parseInt(p[2], 10);
    if (isNaN(i) || i < 0 || i >= TILT.length) return zoneView(zone, 'Unknown tilt option.');
    const e = 'input_select.' + zone.id + '_tilt_mode';
    setState(e, TILT[i]);
    const r = zoneView(zone, '✔ Tilt set to ' + TILT[i] + '.');
    r[0].json.ha = haCall('input_select', 'select_option', e, 'option', TILT[i]);
    return r;
  }
  case 'w': {
    const i = parseInt(p[1], 10);
    if (isNaN(i) || i < 0 || i >= WATER.length) return waterView('Unknown option.');
    setState('input_select.water_heater_mode', WATER[i]);
    const r = waterView('✔ Water heater mode set to ' + WATER[i].split('_').join(' ') + '. The water heater workflow applies it.');
    r[0].json.ha = haCall('input_select', 'select_option', 'input_select.water_heater_mode', 'option', WATER[i]);
    return r;
  }
  case 'n': return zone ? zoneView(zone, 'No change.') : mainView('No change.');
  default: return mainView();
}
