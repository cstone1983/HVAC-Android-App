// The Bot's Handle node (copy in bot-handle.js): its zone view and +/- buttons must use the block
// the zone is really on (Kids away, Night early), as the Controller does.
const fs = require('fs');
const H = new Function('$input', '$', fs.readFileSync(__dirname + '/bot-handle.js', 'utf8'));
function states(o) {
  const s = { 'input_select.global_hvac_mode': { state: 'heat' }, 'input_select.house_schedule_state': { state: 'Day' } };
  for (const z of ['main_level', 'anthony', 'autumn', 'bedroom_1', 'bedroom_2', 'basement']) {
    s['input_boolean.zone_enable_' + z] = { state: 'on' }; s['input_boolean.override_' + z] = { state: 'off' };
    s['input_number.' + z + '_day_temp'] = { state: '68', attributes: { min: 60, max: 80, step: 1 } };
    s['input_number.' + z + '_night_temp'] = { state: '66', attributes: { min: 60, max: 80, step: 1 } };
    s['input_number.' + z + '_away_temp'] = { state: '64', attributes: { min: 60, max: 80, step: 1 } };
  }
  for (let i = 0; i < 20; i++) s['pad.' + i] = { state: 'x' };
  Object.assign(s, o);
  return Object.entries(s).map(([entity_id, v]) => ({ json: Object.assign({ entity_id }, v) }));
}
function run(o, data) {
  return H({ all: () => states(o) }, () => ({ first: () => ({ json: { kind: 'cb', chat_id: 1, message_id: 2, data } }) }))[0].json;
}
let f = 0;
const c = (n, g, w) => { const ok = g === w; if (!ok) f++; console.log((ok ? 'PASS ' : 'FAIL ') + n + '  -> ' + g); };
const KA = { 'input_boolean.hvac_kids_away': { state: 'on' } };
c('H1 Kids away: Anthony view shows the Away block', /Setpoint \(away, heat\): 64/.test(run(KA, 'z:anthony').text) ? 'away 64' : run(KA, 'z:anthony').text, 'away 64');
c('H2 Kids away: Anthony + writes his Away helper', run(KA, 'zs:anthony:65').ha.a1v, 'input_number.anthony_away_temp');
c('H3 Kids away: Bedroom + still writes Day', run(KA, 'zs:bedroom_1:69').ha.a1v, 'input_number.bedroom_1_day_temp');
c('H4 Night early in Day: Bedroom + writes Night', run({ 'input_boolean.hvac_early_night': { state: 'on' } }, 'zs:bedroom_1:67').ha.a1v, 'input_number.bedroom_1_night_temp');
c('H5 Nothing on: unchanged (Day)', run({}, 'zs:anthony:69').ha.a1v, 'input_number.anthony_day_temp');
console.log(f ? f + ' FAILED' : 'ALL PASSED');
