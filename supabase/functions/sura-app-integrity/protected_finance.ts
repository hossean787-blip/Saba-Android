import { b64 } from "./encoding.ts";
import { gateHash } from "./protected_balance.ts";

export type FinanceOperation =
 {operation:"consume_invoice"|"consume_paid"; taxpayerId:number; invoiceKey:string} |
 {operation:"consume_gift"; invoiceKey:string} |
 {operation:"transfer"; taxpayerId:number; credits:number; requestId:string};
type FinanceDeps={
 issue:(scope:string,hash:string)=>Promise<boolean>;
 consume:(scope:string,hash:string)=>Promise<boolean>;
 verify:(chain:unknown,signature:unknown,nonce:Uint8Array)=>Promise<void>;
 execute:(operation:FinanceOperation)=>Promise<unknown>;
 random:()=>Uint8Array;
};
const reply=(data:unknown,status=200)=>new Response(JSON.stringify(data),{status,headers:{"content-type":"application/json","cache-control":"no-store"}});
const uuid=(v:unknown):v is string=>typeof v==="string"&&/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(v);
const positive=(v:unknown,max=Number.MAX_SAFE_INTEGER):v is number=>typeof v==="number"&&Number.isSafeInteger(v)&&v>0&&v<=max;
export function financialOperation(body:unknown):FinanceOperation|null{
 if(!body||typeof body!=="object"||Array.isArray(body))return null;
 const b=body as Record<string,unknown>;
 if(b.operation==="consume_gift"&&uuid(b.invoiceKey))return {operation:b.operation,invoiceKey:b.invoiceKey};
 if((b.operation==="consume_invoice"||b.operation==="consume_paid")&&positive(b.taxpayerId)&&uuid(b.invoiceKey))
  return {operation:b.operation,taxpayerId:b.taxpayerId,invoiceKey:b.invoiceKey};
 if(b.operation==="transfer"&&positive(b.taxpayerId)&&positive(b.credits,100000000)&&uuid(b.requestId))
  return {operation:b.operation,taxpayerId:b.taxpayerId,credits:b.credits,requestId:b.requestId};
 return null;
}
/** Server-owned route scope. Probe is never allowed to mutate; its proof cannot authorize execute. */
export async function protectedFinance(action:"challenge"|"execute",sessionHash:string,body:unknown,d:FinanceDeps,probe=false):Promise<Response>{
 const operation=financialOperation(body);
 if(!operation)return reply({error:"invalid_request"},400);
 const b=body as Record<string,unknown>;
 const scope=await gateHash(JSON.stringify(["sura-finance-v1",probe?"probe":"execute",sessionHash,operation]));
 try{
  if(action==="challenge"){
   const nonce=d.random();if(nonce.length!==32)throw Error("random_unavailable");
   if(!await d.issue(scope,await gateHash(nonce)))return reply({error:"rate_limited"},429);
   return reply({nonce:btoa(String.fromCharCode(...nonce)),expiresInSeconds:180,mode:"enforce",operation:operation.operation,probe});
  }
  let nonce:Uint8Array;
  try{nonce=b64(b.nonce,32);if(nonce.length!==32)throw Error();}catch{return reply({error:"integrity_required"},403);}
  if(!Array.isArray(b.chain)||typeof b.signature!=="string")return reply({error:"integrity_required"},403);
  if(!await d.consume(scope,await gateHash(nonce)))return reply({error:"challenge_invalid_or_used"},403);
  try{await d.verify(b.chain,b.signature,nonce);}
  catch(e){return e instanceof Error&&e.message==="trust_unavailable"?reply({error:"integrity_unavailable"},503):reply({error:"integrity_rejected"},403);}
  if(probe)return reply({status:"verified",probe:true,mutation:false});
  // Existing RPCs own transaction/idempotency. Never retry here or generate a replacement request ID.
  const result=await d.execute(operation);
  if(!result||typeof result!=="object"||Array.isArray(result))return reply({error:"invalid_response"},502);
  const out=result as Record<string,unknown>;
  return reply(out,typeof out.error==="string"?409:200);
 }catch{return reply({error:"integrity_unavailable"},503);}
}
