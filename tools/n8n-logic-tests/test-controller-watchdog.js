// Runs the n8n Code-node scripts against simulated Home Assistant states.
const fs = require('fs');
const path = require('path');
const load = (f) => new Function('$input', '$', '$getWorkflowStaticData', fs.readFileSync(path.join(__dirname, f), 'utf8'));
const SEQ = load('controller-compute-sequencer.js'), FAN = load('controller-compute-fan-tilt.js'), WD = load('watchdog-evaluate.js');

let clock = Date.parse('2026-11-03T22:00:00Z');
const realNow = Date.now;
Date.now = () => clock;
const iso = (ms) => new Date(ms).toISOString();

const LR = 'climate.hp_living_room', DR = 'climate.hp_dining_room';
function house(o = {}) {
  const head = (state, t, extra = {}) => ({ state, attributes: Object.assign({ temperature: t, fan_mode: 'auto', swing_mode: 'Lowest', fan_modes: ['auto', 'Quiet', 'low', 'medium', 'high'], swing_modes: ['Highest', 'High', 'Low', 'Lowest', 'Vertical Swing'], friendly_name: state }, extra), last_changed: iso(clock - 3600e3) });
  const s = {
    'input_select.global_hvac_mode': { state: 'heat' },
    'input_select.house_schedule_state': { state: 'Day' },
    'input_select.main_level_heads': { state: 'Both', last_changed: iso(clock - 3600e3) },
    'input_select.main_level_fan_mode': { state: 'Auto', attributes: { options: ['Quiet', 'Low', 'Medium', 'High', 'Auto'] }, last_changed: iso(clock - 3600e3) },
    'input_select.main_level_tilt_mode': { state: 'Lowest', attributes: { options: ['Highest', 'High', 'Low', 'Lowest', 'Vertical Swing'] }, last_changed: iso(clock - 3600e3) },
    [LR]: head('heat', 69), [DR]: head('heat', 69),
    'climate.hp_anthony': head('heat', 68), 'climate.hp_autumn': head('heat', 65),
    'climate.hp_bedroom': head('heat', 70), 'climate.hp_bedroom_2': head('heat', 66), 'climate.hp_basement': head('heat', 65),
  };
  for (const z of ['main_level', 'anthony', 'autumn', 'bedroom_1', 'bedroom_2', 'basement']) {
    s['input_boolean.zone_enable_' + z] = { state: 'on' };
    s['input_boolean.override_' + z] = { state: 'off' };
  }
  const P = { main_level: [69, 67, 66, 71, 75, 75], anthony: [68, 66, 64, 72, 72, 76], autumn: [65, 64, 62, 72, 72, 76], bedroom_1: [70, 68, 66, 72, 72, 76], bedroom_2: [66, 66, 62, 72, 72, 76], basement: [65, 65, 60, 75, 75, 78] };
  for (const z in P) ['day_temp', 'night_temp', 'away_temp', 'day_cool', 'night_cool', 'away_cool'].forEach((k, i) => { s['input_number.' + z + '_' + k] = { state: String(P[z][i]) }; });
  // overrides: {entity: state} or {entity: {state, attributes...}}
  for (const k in o) {
    const v = o[k];
    if (typeof v === 'string') s[k] = Object.assign({}, s[k] || {}, { state: v });
    else if (v.attrs) s[k] = Object.assign({}, s[k], { state: v.state || s[k].state, attributes: Object.assign({}, s[k].attributes, v.attrs) });
    else s[k] = Object.assign({}, s[k] || {}, v);
  }
  return Object.entries(s).map(([entity_id, v]) => ({ json: Object.assign({ entity_id }, v) }));
}

function runNode(fn, states, trig, sd) {
  const items = states;
  return fn({ all: () => items }, (n) => ({ first: () => ({ json: { entity_id: trig, event_ts: clock } }) }), () => sd);
}
function fmt(out) {
  if (out.length === 1 && out[0].json.noop) return 'NOOP (' + out[0].json.reason + ')';
  return out.map((o) => { const j = o.json; return (j.entity_id || '').replace('climate.hp_', '').replace(/^input_(select|boolean)\./, '') + ':' + j.service + (j.attr_value !== '' && j.attr_value !== undefined ? '=' + j.attr_value : ''); }).join(' | ');
}
let fails = 0;
function check(name, got, want) {
  const ok = got === want;
  if (!ok) fails++;
  console.log((ok ? 'PASS ' : 'FAIL ') + name + '\n     got:  ' + got + (ok ? '' : '\n     want: ' + want));
}
const seq = (name, o, trig, want) => check(name, fmt(runNode(SEQ, house(o), trig, {})), want);
const fan = (name, o, trig, want, sd = {}) => check(name, fmt(runNode(FAN, house(o), trig, sd)), want);

const LO = { 'input_select.main_level_heads': 'Living room only' };
const LOoff = Object.assign({}, LO, { [DR]: 'off' });

console.log('=== Controller: sequencer ===');
seq('S1  switch ON, both heating -> dining off only', LO, 'input_select.main_level_heads', 'dining_room:set_hvac_mode=off');
seq('S2  switch ON while Main Level on Override -> dining still off', Object.assign({}, LO, { 'input_boolean.override_main_level': 'on' }), 'input_select.main_level_heads', 'dining_room:set_hvac_mode=off');
seq('S3  switch ON while Main Level Auto off -> dining still off', Object.assign({}, LO, { 'input_boolean.zone_enable_main_level': 'off' }), 'input_select.main_level_heads', 'dining_room:set_hvac_mode=off');
seq('S3b switch ON, dining already off -> nothing', LOoff, 'input_select.main_level_heads', 'NOOP (heads already match the choice (Living room only))');
seq('S4  switch OFF, living heating 69 -> dining on, heat, 69, fan, vane (in order)', { [DR]: 'off' }, 'input_select.main_level_heads', 'dining_room:turn_on | dining_room:set_hvac_mode=heat | dining_room:set_temperature=69 | dining_room:set_fan_mode=auto | dining_room:set_swing_mode=Lowest');
seq('S5  switch OFF, living off (house off) -> dining stays off', { [LR]: 'off', [DR]: 'off', 'input_select.global_hvac_mode': 'off' }, 'input_select.main_level_heads', 'NOOP (heads already match the choice (Both))');
seq('S6  switch OFF, living cooling 71 -> dining on, cool, 71', { [LR]: { state: 'cool', attrs: { temperature: 71 } }, [DR]: 'off', 'input_select.global_hvac_mode': 'cool' }, 'input_select.main_level_heads', 'dining_room:turn_on | dining_room:set_hvac_mode=cool | dining_room:set_temperature=71 | dining_room:set_fan_mode=auto | dining_room:set_swing_mode=Lowest');
seq('S7  switch OFF, living on Override at 72 -> dining copies 72', { [LR]: { attrs: { temperature: 72 } }, [DR]: 'off', 'input_boolean.override_main_level': 'on' }, 'input_select.main_level_heads', 'dining_room:turn_on | dining_room:set_hvac_mode=heat | dining_room:set_temperature=72 | dining_room:set_fan_mode=auto | dining_room:set_swing_mode=Lowest');
seq('S7b switch OFF, fan High / vane Highest chosen -> dining gets High/Highest', { [DR]: 'off', 'input_select.main_level_fan_mode': 'High', 'input_select.main_level_tilt_mode': 'Highest' }, 'input_select.main_level_heads', 'dining_room:turn_on | dining_room:set_hvac_mode=heat | dining_room:set_temperature=69 | dining_room:set_fan_mode=high | dining_room:set_swing_mode=Highest');
seq('S7c switch OFF, dining unavailable -> nothing sent', { [DR]: 'unavailable' }, 'input_select.main_level_heads', 'NOOP (heads already match the choice (Both))');

const trigS = 'input_select.house_schedule_state';
const out8 = fmt(runNode(SEQ, house(Object.assign({}, LOoff, { 'input_select.house_schedule_state': 'Night' })), trigS, {}));
check('S8  Living only, Day->Night -> no dining action at all', /dining/.test(out8) ? 'dining touched: ' + out8 : 'no dining', 'no dining');
check('S8b Living only, Day->Night -> living gets night 67', /living_room:set_temperature=67/.test(out8) ? 'yes' : out8, 'yes');
const out9 = fmt(runNode(SEQ, house(Object.assign({}, LOoff, { 'input_select.global_hvac_mode': 'cool' })), 'input_select.global_hvac_mode', {}));
check('S9  Living only, Heat->Cool -> no dining action', /dining/.test(out9) ? 'dining touched: ' + out9 : 'no dining', 'no dining');
check('S9b Living only, Heat->Cool -> living cool 71', /living_room:set_hvac_mode=cool \| living_room:set_temperature=71/.test(out9) ? 'yes' : out9, 'yes');
const out10 = fmt(runNode(SEQ, house(Object.assign({}, LOoff, { 'input_select.global_hvac_mode': 'off' })), 'input_select.global_hvac_mode', {}));
check('S10 Living only, house Off -> living off, no dining action', (/living_room:set_hvac_mode=off/.test(out10) && !/dining/.test(out10)) ? 'yes' : out10, 'yes');
const out11 = fmt(runNode(SEQ, house(Object.assign({}, LOoff, { [LR]: 'off' })), 'input_select.global_hvac_mode', {}));
check('S11 Living only, Off->Heat -> living on/heat/69, dining untouched', (/living_room:turn_on \| living_room:set_hvac_mode=heat/.test(out11) && !/dining/.test(out11)) ? 'yes' : out11, 'yes');
seq('S12 Living only, setpoint 69->71 -> living only', Object.assign({}, LOoff, { 'input_number.main_level_day_temp': '71' }), 'input_number.main_level_day_temp', 'living_room:set_temperature=71');
seq('S13 Living only, dining switched on by remote -> back off', Object.assign({}, LO), DR, 'dining_room:set_hvac_mode=off');
seq('S14 Living only, living temp changed by remote -> no mirror', Object.assign({}, LOoff, { [LR]: { attrs: { temperature: 73 } } }), LR, 'NOOP (one main-level head in use: no mirror)');
seq('S15 Living only, living off by remote -> no mirror (Watchdog handles)', Object.assign({}, LOoff, { [LR]: 'off' }), LR, 'NOOP (one main-level head in use: no mirror)');
const out16 = fmt(runNode(SEQ, house(Object.assign({}, LO)), 'homeassistant.start', {}));
check('S16 Living only, HA restart with dining on -> dining off, not back on', (/dining_room:set_hvac_mode=off/.test(out16) && !/dining_room:(turn_on|set_temperature|set_hvac_mode=heat)/.test(out16)) ? 'yes' : out16, 'yes');
seq('S17 Living only, dining back from Wi-Fi running -> off', Object.assign({}, LO), 'recovered:' + DR, 'dining_room:set_hvac_mode=off');
seq('S17b Living only, dining back from Wi-Fi already off -> nothing', LOoff, 'recovered:' + DR, 'NOOP (nothing to change)');
seq('S18 Both, dining off by remote -> mirrors living off (unchanged)', { [DR]: 'off' }, DR, 'living_room:set_hvac_mode=off');
seq('S19 Helper unavailable -> behaves as Both (mirror works)', { 'input_select.main_level_heads': 'unavailable', [DR]: { attrs: { temperature: 72 } } }, DR, 'living_room:set_temperature=72');
seq('S19b Helper missing entirely -> behaves as Both', { 'input_select.main_level_heads': { state: undefined }, [DR]: { attrs: { temperature: 72 } } }, DR, 'living_room:set_temperature=72');
seq('S20 Living only, Override turned on -> nothing', Object.assign({}, LOoff, { 'input_boolean.override_main_level': 'on' }), 'input_boolean.override_main_level', 'NOOP (nothing to change)');
seq('S21 Living only, Override cleared, living off-target -> living only', Object.assign({}, LOoff, { [LR]: { attrs: { temperature: 72 } } }), 'input_boolean.override_main_level', 'living_room:set_temperature=69');
seq('S22 Living only, Auto turned back on -> living only', Object.assign({}, LOoff, { [LR]: 'off' }), 'input_boolean.zone_enable_main_level', 'living_room:turn_on | living_room:set_hvac_mode=heat');
seq('S23 Both, Day->Night -> both heads (unchanged)', { 'input_select.house_schedule_state': 'Night' }, 'input_select.house_schedule_state', 'living_room:set_temperature=67 | dining_room:set_temperature=67 | anthony:set_temperature=66 | autumn:set_temperature=64 | bedroom:set_temperature=68');
seq('S24 Both, setpoint change -> both heads (unchanged)', { 'input_number.main_level_day_temp': '71' }, 'input_number.main_level_day_temp', 'living_room:set_temperature=71 | dining_room:set_temperature=71');
seq('S25 Other zone unaffected (Anthony setpoint)', LOoff, 'input_number.anthony_day_temp', 'NOOP (nothing to change)');
// Future: dining room only
const DO = { 'input_select.main_level_heads': 'Dining room only' };
seq('S26 (future) Dining only ON -> living off', DO, 'input_select.main_level_heads', 'living_room:set_hvac_mode=off');
seq('S27 (future) Dining only -> Both: living copies dining', { [LR]: 'off', [DR]: { attrs: { temperature: 70 } } }, 'input_select.main_level_heads', 'living_room:turn_on | living_room:set_hvac_mode=heat | living_room:set_temperature=70 | living_room:set_fan_mode=auto | living_room:set_swing_mode=Lowest');
seq('S28 (future) Living only -> Dining only directly: living off, dining copies? (no lead running)', Object.assign({}, DO, { [DR]: 'off' }), 'input_select.main_level_heads', 'living_room:set_hvac_mode=off');

console.log('\n=== Controller: fan/vane ===');
fan('F1  Living only, fan set to High in app -> living only', Object.assign({}, LOoff, { 'input_select.main_level_fan_mode': 'High' }), 'input_select.main_level_fan_mode', 'living_room:set_fan_mode=high');
fan('F1b Both, fan set to High -> both heads (unchanged)', { 'input_select.main_level_fan_mode': 'High' }, 'input_select.main_level_fan_mode', 'living_room:set_fan_mode=high | dining_room:set_fan_mode=high');
fan('F2  Living only, dining back from Wi-Fi -> nothing', LO, 'recovered:' + DR, 'NOOP (recovered head is paused: fan/tilt not re-applied)');
fan('F3  Living only, living fan changed by remote -> app setting follows, no mirror', Object.assign({}, LOoff, { [LR]: { attrs: { fan_mode: 'high' } } }), LR, 'main_level_fan_mode:select_option=High');
fan('F4  Both, living fan changed by remote -> mirror + app setting (unchanged)', { [LR]: { attrs: { fan_mode: 'high' } } }, LR, 'dining_room:set_fan_mode=high | main_level_fan_mode:select_option=High');
fan('F5  Living only, dining fan event -> nothing', Object.assign({}, LO, { [DR]: { attrs: { fan_mode: 'high' } } }), DR, 'NOOP (fan/tilt already in sync)');
fan('F6  Both, dining fan event -> mirror to living, no app feedback (unchanged)', { [DR]: { attrs: { fan_mode: 'high' } } }, DR, 'living_room:set_fan_mode=high');
fan('F7  (future) Dining only, dining fan by remote -> app setting follows', Object.assign({}, DO, { [LR]: 'off', [DR]: { attrs: { fan_mode: 'high' } } }), DR, 'main_level_fan_mode:select_option=High');
fan('F8  Living only, restore lane just changed dining mode -> no feedback (recentModeChange)', Object.assign({}, LOoff, { [LR]: { attrs: { fan_mode: 'high' } } }), LR, 'NOOP (fan/tilt already in sync)', { lastModeChangeAt: { [LR]: clock - 5000 } });

console.log('\n=== Watchdog (minute ticks) ===');
function wdTicks(name, ticks, want) {
  const sd = {};
  const res = [];
  const start = clock;
  ticks.forEach((t, i) => {
    clock = start + i * 60000;
    const out = WD({ all: () => [] }, (n) => ({ all: () => house(t(clock)) }), () => sd);
    res.push(out.length ? out.map((o) => o.json.service + (o.json.attr1_value && o.json.domain !== 'rest_command' ? ':' + String(o.json.attr1_value).replace('climate.hp_', '').replace('input_boolean.', '') : '') + (o.json.domain === 'rest_command' ? '[' + o.json.attr1_value + ']' : '')).join(',') : '-');
  });
  clock = start;
  check(name, res.join(' / '), want);
}
const allOff = { [LR]: 'off', [DR]: 'off', 'climate.hp_anthony': 'off', 'climate.hp_autumn': 'off', 'climate.hp_bedroom': 'off', 'climate.hp_bedroom_2': 'off', 'climate.hp_basement': 'off', 'input_select.global_hvac_mode': 'off' };
const lc = (agoMs) => ({ last_changed: iso(clock - agoMs) });
wdTicks('W1  Living only, dining off, living fine -> nothing for 5 min', Array(5).fill(() => LOoff), '- / - / - / - / -');
wdTicks('W2  Living only, dining running -> turned off quietly on 2nd sighting, no override/Telegram after', [() => LO, () => LO, () => LOoff, () => LOoff, () => LOoff], '- / turn_off:dining_room / - / - / -');
wdTicks('W3  Just switched to Living only, dining still running -> left to the Controller for 3 min', [
  (c) => Object.assign({}, LO, { 'input_select.main_level_heads': Object.assign({ state: 'Living room only' }, lc(10000)) }),
  (c) => Object.assign({}, LO, { 'input_select.main_level_heads': Object.assign({ state: 'Living room only' }, lc(70000)) }),
  (c) => Object.assign({}, LOoff, { 'input_select.main_level_heads': Object.assign({ state: 'Living room only' }, lc(130000)) }),
  (c) => Object.assign({}, LOoff, { 'input_select.main_level_heads': Object.assign({ state: 'Living room only' }, lc(190000)) }),
], '- / - / - / -');
wdTicks('W4  Just switched to Both, dining coming back -> no override while settling', [
  (c) => ({ [DR]: 'off', 'input_select.main_level_heads': Object.assign({ state: 'Both' }, lc(5000)) }),
  (c) => ({ [DR]: 'off', 'input_select.main_level_heads': Object.assign({ state: 'Both' }, lc(65000)) }),
  (c) => ({ 'input_select.main_level_heads': Object.assign({ state: 'Both' }, lc(125000)) }),
  (c) => ({ 'input_select.main_level_heads': Object.assign({ state: 'Both' }, lc(185000)) }),
], '- / - / - / -');
wdTicks('W5  Both, dining failed to come back (still off after 3 min) -> override + Telegram (safety)', [
  (c) => ({ [DR]: 'off', 'input_select.main_level_heads': Object.assign({ state: 'Both' }, lc(170000)) }),
  (c) => ({ [DR]: 'off', 'input_select.main_level_heads': Object.assign({ state: 'Both' }, lc(230000)) }),
  (c) => ({ [DR]: 'off', 'input_select.main_level_heads': Object.assign({ state: 'Both' }, lc(290000)) }),
], '- / - / turn_on:override_main_level,n8n_hvac_notify[HVAC Override]');
wdTicks('W6  Living only, living off by remote -> override + Telegram as for any room', [() => Object.assign({}, LOoff, { [LR]: 'off' }), () => Object.assign({}, LOoff, { [LR]: 'off' })], '- / turn_on:override_main_level,n8n_hvac_notify[HVAC Override]');
wdTicks('W7  Living only, house Off, both off -> nothing', Array(3).fill(() => Object.assign({}, allOff, LO)), '- / - / -');
wdTicks('W8  Living only, living temp off by hand -> override (as today)', [() => Object.assign({}, LOoff, { [LR]: { attrs: { temperature: 73 } } }), () => Object.assign({}, LOoff, { [LR]: { attrs: { temperature: 73 } } })], '- / turn_on:override_main_level,n8n_hvac_notify[HVAC Override]');
wdTicks('W9  Both, dining off by hand (unchanged) -> override', [() => ({ [DR]: 'off' }), () => ({ [DR]: 'off' })], '- / turn_on:override_main_level,n8n_hvac_notify[HVAC Override]');
wdTicks('W10 Both, split heat vs cool (unchanged) -> split alert after ~2 min (override part is existing house-Off behaviour)', [() => ({ [DR]: 'cool' }), () => ({ [DR]: 'cool' }), () => ({ [DR]: 'cool' })].map((f) => () => Object.assign({}, allOff, { [LR]: 'heat' }, f())), '- / turn_on:override_main_level,n8n_hvac_notify[HVAC Override] / n8n_hvac_notify[HVAC main level split]');
wdTicks('W11 Living only + Override on, dining running -> still turned off', [() => Object.assign({}, LO, { 'input_boolean.override_main_level': 'on' }), () => Object.assign({}, LO, { 'input_boolean.override_main_level': 'on' })], '- / turn_on:override_main_level,n8n_hvac_notify[HVAC Override]'.replace(/.*/, '- / turn_off:dining_room'));
wdTicks('W12 Living only, dining unavailable -> no turn_off, unavailable alert only', [() => Object.assign({}, LO, { [DR]: { state: 'unavailable', last_changed: iso(clock - 120000) } })], 'n8n_hvac_notify[HVAC Head Unavailable]');

Date.now = realNow;
console.log('\n' + (fails ? fails + ' FAILED' : 'ALL PASSED'));
