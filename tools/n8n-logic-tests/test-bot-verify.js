const fs=require('fs');
const src=fs.readFileSync(__dirname+'/bot-check-verify.js','utf8');
const BOT=new Function('$input','$','$runIndex',src);
const LR='climate.hp_living_room', DR='climate.hp_dining_room';
function states(o){const h=(s,t)=>({state:s,attributes:{temperature:t}});const s={'input_select.global_hvac_mode':{state:'heat'},'input_select.house_schedule_state':{state:'Day'},'input_select.main_level_heads':{state:'Both'},[LR]:h('heat',69),[DR]:h('heat',69),'climate.hp_anthony':h('heat',68),'climate.hp_autumn':h('heat',65),'climate.hp_bedroom':h('heat',70),'climate.hp_bedroom_2':h('heat',66),'climate.hp_basement':h('heat',65)};
for(const z of['main_level','anthony','autumn','bedroom_1','bedroom_2','basement']){s['input_boolean.zone_enable_'+z]={state:'on'};s['input_boolean.override_'+z]={state:'off'};}
const P={main_level:69,anthony:68,autumn:65,bedroom_1:70,bedroom_2:66,basement:65};for(const z in P)s['input_number.'+z+'_day_temp']={state:String(P[z])};
for(let i=0;i<20;i++)s['pad.'+i]={state:'x'};
Object.assign(s,o);return Object.entries(s).map(([entity_id,v])=>({json:Object.assign({entity_id},v)}));}
function run(o){const r=BOT({all:()=>states(o)},()=>({first:()=>({json:{verify:{mode:'heat',started_at:Date.now()},chat_id:1,sent_id:2}})}),0);return /confirmed on 6 of 6/.test(r[0].json.text)?'confirmed':(r[0].json.text.match(/not confirmed on: [^(]*/)||['?'])[0];}
let f=0;const c=(n,g,w)=>{const ok=g===w;if(!ok)f++;console.log((ok?'PASS ':'FAIL ')+n+'  -> '+g);};
c('B1 Both, all heating -> confirmed',run({}),'confirmed');
c('B2 Living only, dining off -> confirmed (was the false failure)',run({'input_select.main_level_heads':{state:'Living room only'},[DR]:{state:'off',attributes:{}}}),'confirmed');
c('B3 Both, dining off -> still reported (unchanged)',run({[DR]:{state:'off',attributes:{}}}),'not confirmed on: Main Level ');
c('B4 Living only, living off -> reported',run({'input_select.main_level_heads':{state:'Living room only'},[DR]:{state:'off',attributes:{}},[LR]:{state:'off',attributes:{}}}),'not confirmed on: Main Level ');
c('B5 Kids away, kids at Away 64/62 -> confirmed',run({'input_boolean.hvac_kids_away':{state:'on'},'input_number.anthony_away_temp':{state:'64'},'input_number.autumn_away_temp':{state:'62'},'climate.hp_anthony':{state:'heat',attributes:{temperature:64}},'climate.hp_autumn':{state:'heat',attributes:{temperature:62}}}),'confirmed');
c('B6 Kids away, kids still at Day -> reported',run({'input_boolean.hvac_kids_away':{state:'on'},'input_number.anthony_away_temp':{state:'64'},'input_number.autumn_away_temp':{state:'62'}}),'not confirmed on: Anthony, Autumn ');
c('B7 Boost, Main Level at 72 -> confirmed',run({'timer.hvac_boost_main_level':{state:'active'},[LR]:{state:'heat',attributes:{temperature:72}},[DR]:{state:'heat',attributes:{temperature:72}}}),'confirmed');
c('B8 Boost, Main Level still 69 -> reported',run({'timer.hvac_boost_main_level':{state:'active'}}),'not confirmed on: Main Level ');
console.log(f?f+' FAILED':'ALL PASSED');
