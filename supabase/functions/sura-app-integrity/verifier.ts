import * as asn1 from "npm:asn1js@3.0.10";
import { Certificate, CertificateChainValidationEngine } from "npm:pkijs@3.4.0";
import { X509Certificate, verify as signatureVerify } from "node:crypto";
const ATTEST="1.3.6.1.4.1.11129.2.1.17",PROVISION="1.3.6.1.4.1.11129.2.1.30";
const PIN="55a5c9f677f899630bc792330606161560a8dea94b7fe0eabe11d47a013d7b0b";
const hex=(b:Uint8Array)=>Array.from(b,x=>x.toString(16).padStart(2,"0")).join("");
function need(x:unknown):asserts x{if(!x)throw new Error("invalid_attestation");}
export function b64(s:unknown,max:number):Uint8Array{
 need(typeof s==="string"&&s.length>0&&s.length<=Math.ceil(max/3)*4&&s.length%4===0&&/^[A-Za-z0-9+/]+={0,2}$/.test(s));
 const b=Uint8Array.from(atob(s),c=>c.charCodeAt(0));need(b.length<=max&&btoa(String.fromCharCode(...b))===s);return b;
}
function parse(b:Uint8Array):any{const r=asn1.fromBER(b);need(r.offset===b.length);return r.result;}
function kids(n:any,t:number):any[]{need(n?.idBlock?.tagClass===1&&n.idBlock.tagNumber===t&&n.idBlock.isConstructed&&Array.isArray(n.valueBlock.value));return n.valueBlock.value;}
function num(n:any,t:number):number{need(n?.idBlock?.tagClass===1&&n.idBlock.tagNumber===t&&!n.valueBlock.isHexOnly&&Number.isSafeInteger(n.valueBlock.valueDec)&&n.valueBlock.valueDec>=0);return n.valueBlock.valueDec;}
function oct(n:any):Uint8Array{need(n?.idBlock?.tagClass===1&&n.idBlock.tagNumber===4&&!n.idBlock.isConstructed);return n.valueBlock.valueHexView;}
function tags(a:any[],t:number){return a.filter(n=>n.idBlock.tagClass===3&&n.idBlock.tagNumber===t);}
function explicit(a:any[],t:number):any{const f=tags(a,t);need(f.length===1&&f[0].idBlock.isConstructed&&f[0].valueBlock.value.length===1);return f[0].valueBlock.value[0];}
export function checkDescription(raw:Uint8Array,nonce:Uint8Array){
 const k=kids(parse(raw),16);need(k.length===8&&[1,2].includes(num(k[1],10))&&[1,2].includes(num(k[3],10))&&hex(oct(k[4]))===hex(nonce));
 const sw=kids(k[6],16),hw=kids(k[7],16);need(!tags(hw,709).length&&!tags(sw,704).length);
 const app=kids(parse(oct(explicit(sw,709))),16);need(app.length===2);
 const ps=kids(app[0],17),ds=kids(app[1],17);need(ps.length===1&&ds.length===1);
 const p=kids(ps[0],16);need(p.length===2&&new TextDecoder("utf-8",{fatal:true}).decode(oct(p[0]))==="ir.saba.invoice"&&num(p[1],2)>=127&&hex(oct(ds[0]))===PIN);
 const boot=kids(explicit(hw,704),16);need((boot.length===3||boot.length===4)&&boot[1].idBlock.tagClass===1&&boot[1].idBlock.tagNumber===1&&boot[1].valueBlock.value===true&&num(boot[2],10)===0);
 need(num(explicit(hw,702),2)===0&&num(explicit(hw,2),2)===3&&kids(explicit(hw,1),17).some(x=>num(x,2)===2)&&kids(explicit(hw,5),17).some(x=>num(x,2)===4));
}
async function json(url:string){try{const r=await fetch(url,{redirect:"error",signal:AbortSignal.timeout(8000)});if(!r.ok)throw Error();return await r.json();}catch{throw new Error("trust_unavailable");}}
let cache:{until:number,roots:X509Certificate[],revoked:Record<string,unknown>}|undefined;
async function trust(){
 if(cache&&Date.now()<cache.until)return cache;
 const [roots,rev]=await Promise.all([json("https://android.googleapis.com/attestation/root"),json("https://android.googleapis.com/attestation/status")]);
 if(!Array.isArray(roots)||!roots.length||roots.length>16||!rev.entries||typeof rev.entries!=="object"||Array.isArray(rev.entries))throw Error("trust_unavailable");
 cache={until:Date.now()+60000,roots:roots.map((s:string)=>new X509Certificate(s)),revoked:rev.entries};return cache;
}
function extension(c:Certificate,oid:string){const f=(c.extensions??[]).filter(x=>x.extnID===oid);need(f.length<=1);return f[0];}
export async function verifyAttestation(chain:unknown,signature:unknown,nonce:Uint8Array){
 need(Array.isArray(chain)&&chain.length>=2&&chain.length<=8);
 const xs=chain.map(x=>new X509Certificate(b64(x,8192)));
 need(!xs[0].ca&&xs[0].publicKey.asymmetricKeyType==="ec"&&signatureVerify("sha256",nonce,xs[0].publicKey,b64(signature,256)));
 const t=await trust(),last=xs.at(-1)!;
 const root=t.roots.find(r=>r.raw.equals(last.raw)||(last.checkIssued(r)&&last.verify(r.publicKey)));need(root);
 if(!last.raw.equals(root.raw))xs.push(root);
 need(new Set(xs.map(c=>c.fingerprint256)).size===xs.length);
 for(let i=0;i<xs.length;i++){
  const c=xs[i],serial=c.serialNumber.toLowerCase().replace(/^0+/,"")||"0";
  need(Date.parse(c.validFrom)<=Date.now()&&Date.parse(c.validTo)>=Date.now()&&!Object.hasOwn(t.revoked,serial));
  if(i>0)need(c.ca);
  if(i+1<xs.length)need(c.checkIssued(xs[i+1])&&c.verify(xs[i+1].publicKey));
 }
 const certs=xs.map(c=>Certificate.fromBER(Uint8Array.from(c.raw)));
 const result=await new CertificateChainValidationEngine({trustedCerts:[Certificate.fromBER(Uint8Array.from(root.raw))],certs:certs.slice(0,-1).reverse(),checkDate:new Date()}).verify();
 need(result.result===true&&certs.slice(1).every(c=>!extension(c,ATTEST)));
 const provision=certs.map((c,i)=>extension(c,PROVISION)?i:-1).filter(i=>i>=0);need(!provision.length||(provision.length===1&&provision[0]===1));
 const a=extension(certs[0],ATTEST);need(a);checkDescription(a.extnValue.valueBlock.valueHexView,nonce);
}
