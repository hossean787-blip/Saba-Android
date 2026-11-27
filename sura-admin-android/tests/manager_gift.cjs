const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const js=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/admin/manager.js'),'utf8');
const owner='550e8400-e29b-41d4-a716-446655440000',inputs={'selected-gift-units':{value:'7'},'selected-gift-reason':{value:'test only'},'modal-submit':{},'gift-owner-query':{value:'client'},'gift-owner-results':{innerHTML:''}},calls=[];
let fail=true, wallets=0;
const cx={window:{addEventListener:()=>{}},document:{getElementById:id=>inputs[id]},ADMIN_ROLE:'admin',CAN:{adjustWallet:()=>true},validUUID:x=>x===owner,html:String,fmtNum:String,toast:()=>{},openModal:()=>{},confirm:()=>true,newUUID:()=>owner,submitBusy:async fn=>fn(),parseError:String,managerFetch:async(route,b)=>{assert.equal(route,'owner-search');assert.equal(b.query,'client');return {matches:[{id:owner,name:'علی رضایی',email:'client@example.com'}]}},adminFetch:async(route,b)=>{if(route==='wallet'){wallets++;return {credits:12}}calls.push(JSON.parse(JSON.stringify(b)));if(fail){fail=false;throw Error('simulated lost response')}return {receipt:'test-receipt'};}};
vm.createContext(cx);vm.runInContext(js,cx);vm.runInContext('openManagerUser=()=>{}',cx);
(async()=>{
 vm.runInContext('openSelectedGift()',cx);await vm.runInContext('searchGiftOwners()',cx);assert.match(inputs['gift-owner-results'].innerHTML,/client@example.com/);vm.runInContext("selectGiftOwner('"+owner+"')",cx);assert.equal(vm.runInContext('selectedGiftOwner.name',cx),'علی رضایی');
 vm.runInContext("openSelectedGift('"+owner+"','علی رضایی','client@example.com')",cx);
 await vm.runInContext("saveSelectedGift('"+owner+"')",cx);
 await vm.runInContext("saveSelectedGift('"+owner+"')",cx);
 assert.equal(wallets,1);assert.equal(calls.length,2);assert.deepEqual(calls[0],calls[1]);assert.equal(calls[0].balance,19);assert.equal(calls[0].expected,12);
 vm.runInContext("ADMIN_ROLE='viewer'",cx);await vm.runInContext("saveSelectedGift('"+owner+"')",cx);assert.equal(calls.length,2);
 console.log('PASS: owner lookup by name/email, mock wallet gift, stable retry payload and viewer rejection');
})().catch(e=>{console.error(e);process.exit(1)});
