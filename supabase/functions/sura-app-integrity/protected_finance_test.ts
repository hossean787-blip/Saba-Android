import {protectedFinance,financialOperation,type FinanceOperation} from "./protected_finance.ts";
function check(v:unknown){if(!v)throw Error("assertion failed");}
const session="a".repeat(64),key="123e4567-e89b-42d3-a456-426614174000",other="123e4567-e89b-42d3-a456-426614174001";
const base={operation:"consume_invoice",taxpayerId:3,invoiceKey:key};
function fixture(){
 let current:{scope:string,hash:string,used:boolean}|undefined,nonceNumber=0,valid=true,unavailable=false,loseResponse=false;
 let calls=0,debits=0;
 const seen=new Set<string>();
 const deps={
  issue:async(scope:string,hash:string)=>{current={scope,hash,used:false};return true;},
  consume:async(scope:string,hash:string)=>{if(!current||current.used||current.scope!==scope||current.hash!==hash)return false;current.used=true;return true;},
  verify:async()=>{if(unavailable)throw Error("trust_unavailable");if(!valid)throw Error("invalid_attestation");},
  execute:async(op:FinanceOperation)=>{
   calls++;const identity=JSON.stringify(op),prior=seen.has(identity);
   if(!prior){debits++;seen.add(identity);}
   if(loseResponse){loseResponse=false;throw Error("response_lost");}
   return {consumed:!prior,already_consumed:prior,remaining:5-debits};
  },
  random:()=>new Uint8Array(32).fill(++nonceNumber)
 };
 return {deps,counts:()=>[calls,debits],reject:()=>{valid=false;},outage:()=>{unavailable=true;},lose:()=>{loseResponse=true;}};
}
async function proof(f:ReturnType<typeof fixture>,operation:unknown=base,probe=false){
 const r=await protectedFinance("challenge",session,operation,f.deps,probe);check(r.status===200);
 return {...(operation as object),nonce:(await r.json()).nonce,chain:["synthetic"],signature:"synthetic"};
}
Deno.test("protected consumption executes once; proof replay never reaches RPC",async()=>{
 const f=fixture(),p=await proof(f);check((await protectedFinance("execute",session,p,f.deps)).status===200);
 check((await protectedFinance("execute",session,p,f.deps)).status===403);check(f.counts().join(",")==="1,1");
});
Deno.test("fresh proof with same invoice after lost response preserves idempotent RPC contract",async()=>{
 const f=fixture();f.lose();const a=await proof(f);
 check((await protectedFinance("execute",session,a,f.deps)).status===503);
 const b=await proof(f),r=await protectedFinance("execute",session,b,f.deps);
 check(r.status===200&&(await r.json()).already_consumed===true);check(f.counts().join(",")==="2,1");
});
Deno.test("changed session, taxpayer, invoice or operation never authorizes a mutation",async()=>{
 const f=fixture(),p=await proof(f);
 for(const changed of [{...p,taxpayerId:4},{...p,invoiceKey:other},{...p,operation:"consume_paid"},{...p,operation:"consume_gift"}])
  check((await protectedFinance("execute",session,changed,f.deps)).status===403);
 check((await protectedFinance("execute","b".repeat(64),p,f.deps)).status===403);
 check(f.counts().join(",")==="0,0");
});
Deno.test("transfer amount and request ID are bound; malformed operations rejected",async()=>{
 const f=fixture(),op={operation:"transfer",taxpayerId:3,credits:2,requestId:key},p=await proof(f,op);
 for(const changed of [{...p,credits:3},{...p,requestId:other}])check((await protectedFinance("execute",session,changed,f.deps)).status===403);
 check(financialOperation({...op,credits:0})===null&&financialOperation({...op,credits:1.5})===null&&financialOperation({...op,credits:100000001})===null);
 check(f.counts().join(",")==="0,0");
});
Deno.test("read-only probe cannot be reused for real mutation and never calls RPC",async()=>{
 const f=fixture(),p=await proof(f,base,true);
 check((await protectedFinance("execute",session,p,f.deps,false)).status===403);
 const r=await protectedFinance("execute",session,p,f.deps,true);
 check(r.status===200&&(await r.json()).mutation===false);check(f.counts().join(",")==="0,0");
});
Deno.test("missing, rejected, or unavailable evidence fails closed",async()=>{
 const f=fixture();
 check((await protectedFinance("execute",session,{...base,verified:true},f.deps)).status===403);
 f.reject();check((await protectedFinance("execute",session,await proof(f),f.deps)).status===403);
 f.outage();check((await protectedFinance("execute",session,await proof(f),f.deps)).status===503);
 check(f.counts().join(",")==="0,0");
});
