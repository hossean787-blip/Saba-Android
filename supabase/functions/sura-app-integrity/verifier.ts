import * as asn1 from "npm:asn1js@4.0.0";
import { Certificate, BasicConstraints, CertificateChainValidationEngine } from "npm:pkijs@4.0.1";
import { b64 } from "./encoding.ts";
const ATTEST="1.3.6.1.4.1.11129.2.1.17",PROVISION="1.3.6.1.4.1.11129.2.1.30";
const PIN="55a5c9f677f899630bc792330606161560a8dea94b7fe0eabe11d47a013d7b0b";
const hex=(b:Uint8Array)=>Array.from(b,x=>x.toString(16).padStart(2,"0")).join("");
function need(x:unknown):asserts x{if(!x)throw new Error("invalid_attestation");}
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
type ParsedCert={der:Uint8Array,cert:Certificate};
function certificate(der:Uint8Array):ParsedCert {
 const schema=parse(der);
 return {der,cert:new Certificate({schema})};
}
function fromPem(pem:unknown):ParsedCert {
 need(typeof pem==="string"&&pem.length<=15000);
 const match=pem.trim().match(/^-----BEGIN CERTIFICATE-----\s+([A-Za-z0-9+/=\s]+)-----END CERTIFICATE-----$/);
 need(match);
 return certificate(b64(match[1].replace(/\s/g,""),8192));
}
let cache:{until:number,roots:ParsedCert[],revoked:Record<string,unknown>}|undefined;
async function trust(){
 if(cache&&Date.now()<cache.until)return cache;
 try {
  const [roots,rev]=await Promise.all([json("https://android.googleapis.com/attestation/root"),json("https://android.googleapis.com/attestation/status")]);
  if(!Array.isArray(roots)||!roots.length||roots.length>16||!rev.entries||typeof rev.entries!=="object"||Array.isArray(rev.entries))throw Error();
  cache={until:Date.now()+60000,roots:roots.map(fromPem),revoked:rev.entries};return cache;
 }catch{throw Error("trust_unavailable");}
}
function extension(c:Certificate,oid:string){const f=(c.extensions??[]).filter(x=>x.extnID===oid);need(f.length<=1);return f[0];}
function isCA(c:Certificate):boolean{
 const e=extension(c,"2.5.29.19");
 return !!e&&BasicConstraints.fromBER(e.extnValue.valueBlock.valueHexView).cA===true;
}
/** Android ECDSA returns DER, whereas WebCrypto expects a fixed 64-byte r||s P-256 signature. */
export function proofSignature(der:Uint8Array):Uint8Array{
 const parts=kids(parse(der),16);need(parts.length===2);
 const out=new Uint8Array(64);
 for(let i=0;i<2;i++){
  const n=parts[i];need(n?.idBlock?.tagClass===1&&n.idBlock.tagNumber===2&&!n.idBlock.isConstructed);
  let b:Uint8Array=n.valueBlock.valueHexView;
  need(b.length>=1&&b.length<=33&&(b[0]&128)===0);
  if(b.length>1&&b[0]===0){need((b[1]&128)!==0);b=b.slice(1);}
  need(b.length<=32&&b.some(x=>x!==0));
  out.set(b,32*(i+1)-b.length);
 }
 return out;
}
async function issued(child:Certificate,parent:Certificate){
 return child.issuer.isEqual(parent.subject)&&await child.verify(parent);
}
export async function verifyAttestation(chain:unknown,signature:unknown,nonce:Uint8Array){
 need(Array.isArray(chain)&&chain.length>=2&&chain.length<=8&&nonce.length===32);
 const xs=chain.map(x=>certificate(b64(x,8192)));
 const leaf=xs[0].cert,spki=leaf.subjectPublicKeyInfo;
 need(!isCA(leaf)&&spki.algorithm.algorithmId==="1.2.840.10045.2.1");
 const key=await crypto.subtle.importKey("spki",spki.toSchema().toBER(false),{name:"ECDSA",namedCurve:"P-256"},false,["verify"]);
 need(await crypto.subtle.verify({name:"ECDSA",hash:"SHA-256"},key,proofSignature(b64(signature,256)),nonce));
 const t=await trust(),last=xs.at(-1)!;
 let root=t.roots.find(r=>hex(r.der)===hex(last.der));
 if(!root)for(const r of t.roots){if(await issued(last.cert,r.cert)){root=r;break;}}
 need(root);
 if(hex(last.der)!==hex(root.der))xs.push(root);
 need(new Set(xs.map(c=>hex(c.der))).size===xs.length);
 for(let i=0;i<xs.length;i++){
  const c=xs[i].cert,serial=hex(c.serialNumber.valueBlock.valueHexView).replace(/^0+/,"")||"0";
  need(c.notBefore.value.getTime()<=Date.now()&&c.notAfter.value.getTime()>=Date.now()&&!Object.hasOwn(t.revoked,serial));
  if(i>0)need(isCA(c));
  if(i+1<xs.length)need(await issued(c,xs[i+1].cert));
 }
 const certs=xs.map(c=>c.cert);
 const result=await new CertificateChainValidationEngine({trustedCerts:[root.cert],certs:certs.slice(0,-1).reverse(),checkDate:new Date()}).verify();
 need(result.result===true&&certs.slice(1).every(c=>!extension(c,ATTEST)));
 const provision=certs.map((c,i)=>extension(c,PROVISION)?i:-1).filter(i=>i>=0);need(!provision.length||(provision.length===1&&provision[0]===1));
 const a=extension(certs[0],ATTEST);need(a);checkDescription(a.extnValue.valueBlock.valueHexView,nonce);
}
