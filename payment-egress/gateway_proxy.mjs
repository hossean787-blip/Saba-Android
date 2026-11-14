import http from "node:http";
import {createHmac,timingSafeEqual} from "node:crypto";

const maxBody=8192,maxResponse=100_000,timeWindow=60_000;
const merchantRe=/^[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}$/i;
const authorityRe=/^[A-Za-z0-9_-]{20,64}$/;
const nonceRe=/^[a-f0-9-]{36}$/i;

export function signature(secret,time,nonce,operation,body) {
  return createHmac("sha256",secret).update(time+"."+nonce+"."+operation+"."+body).digest("hex");
}

export function createProxyHandler({secret,merchantId,callbackUrl,fetcher=fetch,now=Date.now}) {
  if(typeof secret!=="string"||secret.length<32) throw Error("invalid_secret");
  if(!merchantRe.test(merchantId)) throw Error("invalid_merchant");
  if(typeof callbackUrl!=="string"||!callbackUrl.startsWith("https://")) throw Error("invalid_callback");
  const seen=new Map();
  function reply(res,status,error) {
    res.writeHead(status,{"content-type":"application/json","cache-control":"no-store"});
    res.end(JSON.stringify({error}));
  }
  return async(req,res)=>{
    const operation=req.url==="/gateway/request"?"request":req.url==="/gateway/verify"?"verify":null;
    if(req.method!=="POST"||!operation) return reply(res,404,"not_found");
    const time=req.headers["x-sura-time"],nonce=req.headers["x-sura-nonce"];
    const mac=req.headers["x-sura-signature"];
    if(typeof time!=="string"||!/^\d{13}$/.test(time)||Math.abs(now()-Number(time))>timeWindow||
       typeof nonce!=="string"||!nonceRe.test(nonce)||
       typeof mac!=="string"||!/^[a-f0-9]{64}$/i.test(mac))
       return reply(res,401,"unauthorized");
    const chunks=[];let length=0;
    try {
      for await(const chunk of req) {
        length+=chunk.length;
        if(length>maxBody)return reply(res,413,"too_large");
        chunks.push(chunk);
      }
    } catch {return reply(res,400,"invalid_body");}
    const raw=Buffer.concat(chunks).toString("utf8");
    const expected=signature(secret,time,nonce,operation,raw);
    if(!timingSafeEqual(Buffer.from(mac,"hex"),Buffer.from(expected,"hex")))
      return reply(res,401,"unauthorized");
    for(const [key,when] of seen) if(now()-when>timeWindow*2)seen.delete(key);
    if(seen.has(nonce)||seen.size>5000)return reply(res,409,"replay_rejected");
    seen.set(nonce,now());
    let body;
    try{body=JSON.parse(raw);}catch{return reply(res,400,"invalid_json");}
    if(!body||typeof body!=="object"||Array.isArray(body)||body.merchant_id!==merchantId||
       !Number.isSafeInteger(body.amount)||body.amount<=0)
      return reply(res,400,"invalid_payment");
    if(operation==="request") {
      if(body.currency!=="IRR"||body.callback_url!==callbackUrl||
         typeof body.description!=="string"||body.description.length>160)
        return reply(res,400,"invalid_request");
    } else if(!authorityRe.test(body.authority??""))return reply(res,400,"invalid_authority");
    try {
      const url="https://payment.zarinpal.com/pg/v4/payment/"+operation+".json";
      const upstream=await fetcher(url,{method:"POST",redirect:"error",
        headers:{"content-type":"application/json","accept":"application/json"},
        body:raw,signal:AbortSignal.timeout(15000)});
      const declared=Number(upstream.headers?.get?.("content-length")||0);
      if(declared>maxResponse)throw Error("large_response");
      const payload=await upstream.text();
      if(payload.length>maxResponse)throw Error("large_response");
      JSON.parse(payload);
      res.writeHead(upstream.status,{"content-type":"application/json","cache-control":"no-store"});
      res.end(payload);
    }catch{return reply(res,502,"gateway_unavailable");}
  };
}

if(process.argv[1]&&import.meta.url===new URL("file://"+process.argv[1]).href) {
  const handler=createProxyHandler({secret:process.env.SURA_EGRESS_SECRET,
    merchantId:process.env.ZARINPAL_MERCHANT_ID,
    callbackUrl:process.env.SURA_ALLOWED_CALLBACK});
  const port=Number(process.env.SURA_EGRESS_PORT||8787);
  if(!Number.isSafeInteger(port)||port<1||port>65535)throw Error("invalid_port");
  http.createServer(handler).listen(port,"127.0.0.1",
    ()=>console.log("SURA payment egress bound to loopback"));
}
