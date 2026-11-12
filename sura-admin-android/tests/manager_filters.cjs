const assert=require('node:assert/strict');const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const js=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/admin/manager.js'),'utf8');
const calls=[],alerts=[];const inputs={'order-owner':{value:'550e8400-e29b-41d4-a716-446655440000'},'order-from':{value:'2026-11-01'},'order-to':{value:'2026-11-12'}};
const cx={window:{addEventListener:()=>{}},document:{getElementById:id=>inputs[id],querySelectorAll:()=>[]},USER_JWT:'test',SB_ANON:'pub',SB_URL:'https://example.supabase.co',ADMIN_ROLE:'admin',ensureFreshToken:async()=>{},toast:(m,k)=>alerts.push([m,k]),showPage:n=>calls.push(['page',n]),validUUID:s=>/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(s),html:String,fmtNum:String,fmtRial:String,csvValue:v=>JSON.stringify(String(v)),SuraReports:{download:(blob,name)=>calls.push(['export',blob,name])},Blob,Date,fetch:async(u,o)=>{calls.push(['fetch',JSON.parse(o.body)]);return {ok:true,status:200,json:async()=>({orders:[],total:0})}}};
vm.createContext(cx);vm.runInContext(js,cx);
(async()=>{
 vm.runInContext('applyManagerOrderFilters()',cx);assert.deepEqual(calls.pop(),['page','payments']);
 await vm.runInContext("renderManagerOrders({innerHTML:''})",cx);const sent=calls.find(x=>x[0]==='fetch')[1];assert.equal(sent.owner,inputs['order-owner'].value);assert.equal(sent.from,'2026-11-01');assert.equal(sent.to,'2026-11-12');
 inputs['order-from'].value='2026-11-20';vm.runInContext('applyManagerOrderFilters()',cx);assert.equal(alerts.pop()[1],'error');
 inputs['order-owner'].value='invalid';inputs['order-from'].value='';vm.runInContext('applyManagerOrderFilters()',cx);assert.equal(alerts.pop()[1],'error');
 vm.runInContext('clearManagerOrderFilters()',cx);assert.equal(vm.runInContext('orderOwner',cx),'');
 vm.runInContext("auditKind='price';lastAuditKind='price';lastAuditRows=[{created_at:'2026-11-12',request_id:'ID',actor_id:'ADMIN',old_price_rial:100000,new_price_rial:110000,reason:'Verified change'}]",cx);
 vm.runInContext('exportManagerAuditPage()',cx);const e=calls.find(x=>x[0]==='export');assert.ok(e&&e[2].endsWith('.csv'));assert.match(await e[1].text(),/110000/);
 vm.runInContext("ADMIN_ROLE='viewer'",cx);vm.runInContext('exportManagerAuditPage()',cx);assert.equal(alerts.pop()[1],'error');
 console.log('PASS RC2 filters, validation, audit export and role guard');
})().catch(e=>{console.error(e);process.exit(1)});
