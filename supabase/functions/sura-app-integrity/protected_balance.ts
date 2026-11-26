import { b64 } from "./encoding.ts";

type GateDeps = {
 issue: (scope:string,nonceHash:string)=>Promise<boolean>;
 consume: (scope:string,nonceHash:string)=>Promise<boolean>;
 verify: (chain:unknown,signature:unknown,nonce:Uint8Array)=>Promise<void>;
 balance: (taxpayer:number)=>Promise<unknown>;
 random: ()=>Uint8Array;
};
const reply=(data:unknown,status=200)=>new Response(JSON.stringify(data),{status,headers:{"content-type":"application/json","cache-control":"no-store"}});
export async function gateHash(x:string|Uint8Array):Promise<string>{
 const data=typeof x==="string"?new TextEncoder().encode(x):x;
 return Array.from(new Uint8Array(await crypto.subtle.digest("SHA-256",data)),b=>b.toString(16).padStart(2,"0")).join("");
}
/** A proof for one owner/session/taxpayer/operation cannot authorize another.
 * No client assertion, cached success or header is accepted as evidence. */
export async function protectedBalance(action:"challenge"|"balance",sessionHash:string,body:unknown,d:GateDeps):Promise<Response>{
 if(!body||typeof body!=="object"||Array.isArray(body))return reply({error:"invalid_request"},400);
 const b=body as Record<string,unknown>;
 const taxpayer=b.taxpayerId;
 if(typeof taxpayer!=="number"||!Number.isSafeInteger(taxpayer)||taxpayer<=0)return reply({error:"invalid_taxpayer"},400);
 const scope=await gateHash(JSON.stringify(["sura-protected-balance-v1",sessionHash,taxpayer]));
 try{
  if(action==="challenge"){
   const nonce=d.random();
   if(nonce.length!==32)throw Error("random_unavailable");
   if(!await d.issue(scope,await gateHash(nonce)))return reply({error:"rate_limited"},429);
   return reply({nonce:btoa(String.fromCharCode(...nonce)),expiresInSeconds:180,mode:"enforce",operation:"invoice_balance"});
  }
  let nonce:Uint8Array;
  try{nonce=b64(b.nonce,32);if(nonce.length!==32)throw Error();}catch{return reply({error:"integrity_required"},403);}
  if(!Array.isArray(b.chain)||typeof b.signature!=="string")return reply({error:"integrity_required"},403);
  if(!await d.consume(scope,await gateHash(nonce)))return reply({error:"challenge_invalid_or_used"},403);
  try{await d.verify(b.chain,b.signature,nonce);}
  catch(e){return e instanceof Error&&e.message==="trust_unavailable"?reply({error:"integrity_unavailable"},503):reply({error:"integrity_rejected"},403);}
  const remaining=await d.balance(taxpayer);
  if(typeof remaining!=="number"||!Number.isSafeInteger(remaining)||remaining<0)return reply({error:"invalid_response"},502);
  return reply({remaining,mode:"enforce"});
 }catch{return reply({error:"integrity_unavailable"},503);}
}
