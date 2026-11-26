import {proofSignature,verifyAttestation,checkDescription} from "./verifier.ts";
import * as asn1 from "npm:asn1js@4.0.0";
function check(ok:boolean){if(!ok)throw Error("assertion failed");}
Deno.test("WebCrypto P-256 DER proof conversion and negative signature",async()=>{
 const key=await crypto.subtle.generateKey({name:"ECDSA",namedCurve:"P-256"},true,["sign","verify"]);
 const nonce=crypto.getRandomValues(new Uint8Array(32));
 const sig=new Uint8Array(await crypto.subtle.sign({name:"ECDSA",hash:"SHA-256"},key.privateKey,nonce));
 function integer(raw:Uint8Array){let b=raw;while(b.length>1&&b[0]===0)b=b.slice(1);if(b[0]&128)b=Uint8Array.of(0,...b);return new asn1.Integer({valueHex:b});}
 const der=new Uint8Array(new asn1.Sequence({value:[integer(sig.slice(0,32)),integer(sig.slice(32))]}).toBER(false));
 const normalized=proofSignature(der);
 check(normalized.length===64&&normalized.every((v,i)=>v===sig[i]));
 check(await crypto.subtle.verify({name:"ECDSA",hash:"SHA-256"},key.publicKey,normalized,nonce));
 nonce[0]^=1;
 check(!await crypto.subtle.verify({name:"ECDSA",hash:"SHA-256"},key.publicKey,normalized,nonce));
});
Deno.test("invalid chain rejected without trust-network access",async()=>{
 let rejected=false;try{await verifyAttestation([], "AA==",new Uint8Array(32));}catch{rejected=true;}
 check(rejected);
});

/** Synthetic metadata ONLY: no trusted certificate or production proof is created here.
 * A valid description is necessary but not sufficient for server verification. */
function syntheticDescription(opts: {
  signer?:Uint8Array; packageName?:string; version?:number; nonce?:Uint8Array;
  hardware?:number; locked?:boolean; boot?:number; duplicateApp?:boolean;
} = {}):Uint8Array {
 const seq=(...value:any[])=>new asn1.Sequence({value});
 const set=(...value:any[])=>new asn1.Set({value});
 const integer=(value:number)=>new asn1.Integer({value});
 const enumeration=(value:number)=>new asn1.Enumerated({value});
 const oct=(valueHex:Uint8Array)=>new asn1.OctetString({valueHex});
 const tag=(tagNumber:number,value:any)=>new asn1.Constructed({idBlock:{tagClass:3,tagNumber},value:[value]});
 const signer=opts.signer??Uint8Array.from("55a5c9f677f899630bc792330606161560a8dea94b7fe0eabe11d47a013d7b0b".match(/../g)!,x=>parseInt(x,16));
 const app=seq(set(seq(oct(new TextEncoder().encode(opts.packageName??"ir.saba.invoice")),integer(opts.version??139))),set(oct(signer)));
 const appTag=tag(709,oct(new Uint8Array(app.toBER(false))));
 const root=seq(oct(new Uint8Array(32)),new asn1.Boolean({value:opts.locked??true}),enumeration(opts.boot??0),oct(new Uint8Array(32)));
 return new Uint8Array(seq(integer(3),enumeration(opts.hardware??1),integer(4),enumeration(opts.hardware??1),
  oct(opts.nonce??new Uint8Array(32)),oct(new Uint8Array()),seq(appTag,...(opts.duplicateApp?[appTag]:[])),
  seq(tag(704,root),tag(702,integer(0)),tag(2,integer(3)),tag(1,set(integer(2))),tag(5,set(integer(4)))).
  toBER(false));
}
Deno.test("synthetic metadata control: correct expected fields pass parser only",()=>{
 checkDescription(syntheticDescription(),new Uint8Array(32));
});
Deno.test("changed application identity is rejected independently of local client decision",()=>{
 const cases=[
  {signer:new Uint8Array(32).fill(9)},
  {packageName:"ir.synthetic.invoice"},
  {version:126},
  {duplicateApp:true}
 ];
 for(const test of cases){
  let rejected=false;
  try{checkDescription(syntheticDescription(test),new Uint8Array(32));}catch{rejected=true;}
  check(rejected);
 }
});
Deno.test("replayed nonce and non-hardware or unverified boot metadata are rejected",()=>{
 const cases=[
  {nonce:new Uint8Array(32).fill(1)},
  {hardware:0},
  {locked:false},
  {boot:2}
 ];
 for(const test of cases){
  let rejected=false;
  try{checkDescription(syntheticDescription(test),new Uint8Array(32));}catch{rejected=true;}
  check(rejected);
 }
});
