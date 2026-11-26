import { b64 } from "./encoding.ts";
import { protectedBalance } from "./protected_balance.ts";
import { protectedFinance, type FinanceOperation } from "./protected_finance.ts";
const root=Deno.env.get("SUPABASE_URL"),anon=Deno.env.get("SUPABASE_ANON_KEY"),service=Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
const reply=(data:unknown,status=200)=>new Response(JSON.stringify(data),{status,headers:{"content-type":"application/json","cache-control":"no-store"}});
async function hash(x:string|Uint8Array){const b=typeof x==="string"?new TextEncoder().encode(x):x;return Array.from(new Uint8Array(await crypto.subtle.digest("SHA-256",b)),n=>n.toString(16).padStart(2,"0")).join("");}
async function rpc(name:string,body:unknown){
 const r=await fetch(root+"/rest/v1/rpc/"+name,{method:"POST",redirect:"error",signal:AbortSignal.timeout(7000),headers:{apikey:service!,authorization:"Bearer "+service,"content-type":"application/json"},body:JSON.stringify(body)});
 if(!r.ok)throw Error("database_unavailable");return (await r.json())===true;
}
async function read(req:Request){
 const reader=req.body?.getReader();if(!reader)throw Error();let n=0;const chunks:Uint8Array[]=[];
 try{while(true){const {done,value}=await reader.read();if(done)break;n+=value.length;if(n>96000)throw Error();chunks.push(value);}}
 finally{await reader.cancel().catch(()=>{});}
 const all=new Uint8Array(n);let p=0;for(const c of chunks){all.set(c,p);p+=c.length;}
 const d=JSON.parse(new TextDecoder("utf-8",{fatal:true}).decode(all));if(!d||typeof d!=="object"||Array.isArray(d))throw Error();return d;
}
Deno.serve(async(req)=>{
 if(req.method!=="POST")return reply({error:"method_not_allowed"},405);
 const path=new URL(req.url).pathname;
 const protectedAction=path.endsWith("/protected-challenge")?"challenge":path.endsWith("/protected-balance")?"balance":null;
 const financePath=path.split("/").at(-1);
 const financeAction=financePath==="finance-challenge"||financePath==="finance-probe-challenge"?"challenge":financePath==="finance-execute"||financePath==="finance-probe"?"execute":null;
 const financeProbe=financePath==="finance-probe-challenge"||financePath==="finance-probe";
 if(!protectedAction&&!financeAction&&!path.endsWith("/challenge")&&!path.endsWith("/verify"))return reply({error:"not_found"},404);
 if(!root||!anon||!service)return reply({error:"server_not_configured"},503);
 const auth=req.headers.get("authorization")??"";if(!/^Bearer [A-Za-z0-9._-]{20,16000}$/.test(auth))return reply({error:"unauthorized"},401);
 try{
  const r=await fetch(root+"/auth/v1/user",{redirect:"error",signal:AbortSignal.timeout(7000),headers:{apikey:anon,authorization:auth}});
  if(!r.ok)return reply({error:"unauthorized"},401);
  const user=await r.json();if(typeof user.id!=="string"||!/^[0-9a-f-]{36}$/i.test(user.id))return reply({error:"unauthorized"},401);
  const session=await hash(auth);
  if(financeAction){
   if(!user.email_confirmed_at)return reply({error:"unverified_account"},403);
   // Live debit/transfer remains disabled until the owner-test client and rollout gate are verified.
   if(!financeProbe&&Deno.env.get("SURA_FINANCE_INTEGRITY_EXECUTE")!=="enabled")return reply({error:"integrity_pilot_only"},503);
   let body:unknown;try{body=await read(req);}catch{return reply({error:"invalid_request"},400);}
   return await protectedFinance(financeAction,session,body,{
    issue:(scope,nonceHash)=>rpc("sura_integrity_issue",{p_owner:user.id,p_session:scope,p_nonce:nonceHash}),
    consume:(scope,nonceHash)=>rpc("sura_integrity_consume",{p_owner:user.id,p_session:scope,p_nonce:nonceHash}),
    verify:async(chain,signature,nonce)=>{
     let verifier:typeof import("./verifier.ts");
     try{verifier=await import("./verifier.ts");}catch{throw Error("trust_unavailable");}
     await verifier.verifyAttestation(chain,signature,nonce);
    },
    execute:async(op:FinanceOperation)=>{
     let name:string,params:Record<string,unknown>;
     switch(op.operation){
      case "consume_gift":name="sura_consume_promo_credit";params={p_account:user.id,p_invoice_key:op.invoiceKey};break;
      case "transfer":name="sura_transfer_owner_credit";params={p_owner:user.id,p_taxpayer:op.taxpayerId,p_credits:op.credits,p_request:op.requestId};break;
      case "consume_paid":case "consume_invoice":name=op.operation==="consume_paid"?"sura_consume_taxpayer_credit":"sura_consume_invoice_entitlement";params={p_owner:user.id,p_taxpayer:op.taxpayerId,p_invoice_key:op.invoiceKey};break;
     }
     const result=await fetch(root+"/rest/v1/rpc/"+name,{method:"POST",redirect:"error",signal:AbortSignal.timeout(7000),headers:{apikey:service!,authorization:"Bearer "+service,"content-type":"application/json"},body:JSON.stringify(params)});
     if(!result.ok)throw Error("financial_result_unknown");
     return await result.json();
    },
    random:()=>crypto.getRandomValues(new Uint8Array(32))
   },financeProbe);
  }
  if(protectedAction){
   if(!user.email_confirmed_at)return reply({error:"unverified_account"},403);
   let body:unknown;try{body=await read(req);}catch{return reply({error:"invalid_request"},400);}
   return await protectedBalance(protectedAction,session,body,{
    issue:(scope,nonceHash)=>rpc("sura_integrity_issue",{p_owner:user.id,p_session:scope,p_nonce:nonceHash}),
    consume:(scope,nonceHash)=>rpc("sura_integrity_consume",{p_owner:user.id,p_session:scope,p_nonce:nonceHash}),
    verify:async(chain,signature,nonce)=>{
     let verifier:typeof import("./verifier.ts");
     try{verifier=await import("./verifier.ts");}catch{throw Error("trust_unavailable");}
     await verifier.verifyAttestation(chain,signature,nonce);
    },
    balance:async(taxpayer)=>{
     const result=await fetch(root+"/rest/v1/rpc/sura_invoice_entitlement_balance",{method:"POST",redirect:"error",signal:AbortSignal.timeout(7000),headers:{apikey:service!,authorization:"Bearer "+service,"content-type":"application/json"},body:JSON.stringify({p_owner:user.id,p_taxpayer:taxpayer})});
     if(!result.ok)throw Error("balance_unavailable");
     return await result.json();
    },
    random:()=>crypto.getRandomValues(new Uint8Array(32))
   });
  }
  if(path.endsWith("/challenge")){
   const nonce=crypto.getRandomValues(new Uint8Array(32));
   if(!await rpc("sura_integrity_issue",{p_owner:user.id,p_session:session,p_nonce:await hash(nonce)}))return reply({error:"rate_limited"},429);
   return reply({nonce:btoa(String.fromCharCode(...nonce)),expiresInSeconds:180,mode:"observe"});
  }
  let d:any,n:Uint8Array;try{d=await read(req);n=b64(d.nonce,32);if(n.length!==32)throw Error();}catch{return reply({error:"invalid_request"},400);}
  if(!await rpc("sura_integrity_consume",{p_owner:user.id,p_session:session,p_nonce:await hash(n)}))return reply({error:"challenge_invalid_or_used"},403);
  // Dependency initialization failure is service unavailability, never a device rejection.
  let verifyAttestation:typeof import("./verifier.ts").verifyAttestation;
  try { ({verifyAttestation}=await import("./verifier.ts")); }
  catch { console.error("sura_integrity_verifier_init_failed");return reply({status:"unavailable",mode:"observe"},503); }
  try{await verifyAttestation(d.chain,d.signature,n);}catch(e){return e instanceof Error&&e.message==="trust_unavailable"?reply({status:"unavailable",mode:"observe"},503):reply({status:"not_verified",mode:"observe"},422);}
  return reply({status:"verified",mode:"observe"});
 }catch{return reply({error:"integrity_unavailable"},503);}
});
