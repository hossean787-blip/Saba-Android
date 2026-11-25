const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const js=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/admin/manager.js'),'utf8');
const owner='550e8400-e29b-41d4-a716-446655440000',inputs={'selected-gift-units':{value:'7'},'selected-gift-reason':{value:'test only'},'modal-submit':{}},calls=[];
let fail=true, wallets=0;
const cx={window:{addEventListener:()=>{}},document:{getElementById:id=>inputs[id]},ADMIN_ROLE:'admin',CAN:{adjustWallet:()=>true},validUUID:x=>x===owner,html:String,fmtNum:String,toast:()=>{},openModal:()=>{},confirm:()=>true,newUUID:()=>owner,submitBusy:async fn=>fn(),parseError:String,adminFetch:async(route,b)=>{if(route==='wallet'){wallets++;return {credits:12}}calls.push(JSON.parse(JSON.stringify(b)));if(fail){fail=false;throw Error('simulated lost response')}return {receipt:'test-receipt'};}};
vm.createContext(cx);vm.runInContext(js,cx);vm.runInContext('openManagerUser=()=>{}',cx);
(async()=>{
 vm.runInContext("openSelectedGift('"+owner+"')",cx);
 await vm.runInContext("saveSelectedGift('"+owner+"')",cx);
 await vm.runInContext("saveSelectedGift('"+owner+"')",cx);
 assert.equal(wallets,1);assert.equal(calls.length,2);assert.deepEqual(calls[0],calls[1]);assert.equal(calls[0].balance,19);assert.equal(calls[0].expected,12);
 vm.runInContext("ADMIN_ROLE='viewer'",cx);await vm.runInContext("saveSelectedGift('"+owner+"')",cx);assert.equal(calls.length,2);
 console.log('PASS: mock gift addition, stable retry payload, viewer rejection');
})().catch(e=>{console.error(e);process.exit(1)});
