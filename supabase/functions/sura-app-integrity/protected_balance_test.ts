import {protectedBalance} from "./protected_balance.ts";
function check(x:unknown){if(!x)throw Error("assertion failed");}
function fixture(){
 let issued:{scope:string,hash:string,used:boolean}|undefined;
 let balanceCalls=0,verifyCalls=0,valid=true,unavailable=false;
 const nonce=new Uint8Array(32).fill(7);
 const deps={
  issue:async(scope:string,hash:string)=>{issued={scope,hash,used:false};return true;},
  consume:async(scope:string,hash:string)=>{if(!issued||issued.used||issued.scope!==scope||issued.hash!==hash)return false;issued.used=true;return true;},
  verify:async()=>{verifyCalls++;if(unavailable)throw Error("trust_unavailable");if(!valid)throw Error("invalid_attestation");},
  balance:async()=>{balanceCalls++;return 4;},
  random:()=>nonce
 };
 return {deps,counts:()=>[verifyCalls,balanceCalls],reject:()=>{valid=false;},outage:()=>{unavailable=true;}};
}
const session="a".repeat(64),other="b".repeat(64);
async function challenge(f:ReturnType<typeof fixture>){
 const r=await protectedBalance("challenge",session,{taxpayerId:3},f.deps);
 check(r.status===200);return (await r.json()).nonce;
}
Deno.test("enforced read-only balance requires valid proof and consumes once",async()=>{
 const f=fixture(),nonce=await challenge(f);
 const proof={taxpayerId:3,nonce,chain:["synthetic"],signature:"synthetic"};
 const r=await protectedBalance("balance",session,proof,f.deps);
 check(r.status===200&&(await r.json()).remaining===4);
 check((await protectedBalance("balance",session,proof,f.deps)).status===403);
 check(f.counts().join(",")==="1,1");
});
Deno.test("client success assertion or no proof cannot access protected balance",async()=>{
 const f=fixture();
 check((await protectedBalance("balance",session,{taxpayerId:3,verified:true},f.deps)).status===403);
 check(f.counts().join(",")==="0,0");
});
Deno.test("scope rejects wrong taxpayer or session without reading balance",async()=>{
 const f=fixture(),nonce=await challenge(f),proof={taxpayerId:3,nonce,chain:["synthetic"],signature:"synthetic"};
 check((await protectedBalance("balance",other,proof,f.deps)).status===403);
 check((await protectedBalance("balance",session,{...proof,taxpayerId:4},f.deps)).status===403);
 check(f.counts().join(",")==="0,0");
 check((await protectedBalance("balance",session,proof,f.deps)).status===200);
});
Deno.test("invalid proof and trust outage fail closed; no financial read",async()=>{
 for(const outage of [false,true]){
  const f=fixture(),nonce=await challenge(f);
  if(outage)f.outage();else f.reject();
  const r=await protectedBalance("balance",session,{taxpayerId:3,nonce,chain:["synthetic"],signature:"synthetic"},f.deps);
  check(r.status===(outage?503:403));check(f.counts().join(",")==="1,0");
 }
});
Deno.test("invalid taxpayer rejected before challenge or data access",async()=>{
 const f=fixture();
 for(const taxpayerId of [0,-1,1.5,"3",Number.MAX_SAFE_INTEGER+1]){
  check((await protectedBalance("challenge",session,{taxpayerId},f.deps)).status===400);
 }
 check(f.counts().join(",")==="0,0");
});
