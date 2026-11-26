import {proofSignature,verifyAttestation} from "./verifier.ts";
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
