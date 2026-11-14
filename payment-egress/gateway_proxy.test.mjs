import test from "node:test";
import assert from "node:assert/strict";
import http from "node:http";
import {randomUUID} from "node:crypto";
import {createProxyHandler,signature} from "./gateway_proxy.mjs";

const merchant="12345678-1234-1234-1234-1234567890ab";
const callback="https://fiiitzfbdjzpmepzjsjw.supabase.co/functions/v1/sura-zarinpal/callback";
const secret="dummy-only-not-for-production-test-secret-0123456789";
const now=Date.now();
const calls=[];
const server=http.createServer(createProxyHandler({secret,merchantId:merchant,callbackUrl:callback,now:()=>now,
  fetcher:async(url,options)=>{calls.push({url,options});return new Response(JSON.stringify({
    data:{code:100,authority:"A000000000000000000000000000000000"}
  }),{status:200,headers:{"content-type":"application/json"}});}}));
await new Promise(resolve=>server.listen(0,"127.0.0.1",resolve));
const base="http://127.0.0.1:"+server.address().port;
const sample={merchant_id:merchant,amount:110000,currency:"IRR",callback_url:callback,
  description:"SURA invoice credits - 1"};
async function send(type,data,auth=true,nonce=randomUUID(),time=now) {
  const body=JSON.stringify(data);
  return fetch(base+"/gateway/"+type,{method:"POST",body,headers:auth?{
    "x-sura-time":String(time),"x-sura-nonce":nonce,
    "x-sura-signature":signature(secret,time,nonce,type,body)}:{}});
}
test("reject unsigned and outdated payment traffic",async()=>{
  assert.equal((await send("request",sample,false)).status,401);
  assert.equal((await send("request",sample,true,randomUUID(),now-120000)).status,401);
});
test("forward only signed requests and reject reuse of the same nonce",async()=>{
  const nonce=randomUUID();
  assert.equal((await send("request",sample,true,nonce)).status,200);
  assert.equal(calls.at(-1).url,"https://payment.zarinpal.com/pg/v4/payment/request.json");
  assert.equal((await send("request",sample,true,nonce)).status,409);
});
test("reject changed merchant, callback, amounts and arbitrary routes",async()=>{
  assert.equal((await send("request",{...sample,merchant_id:randomUUID()})).status,400);
  assert.equal((await send("request",{...sample,callback_url:"https://example.com"})).status,400);
  assert.equal((await send("request",{...sample,amount:-1})).status,400);
  assert.equal((await send("unsafe",sample)).status,404);
});
test("verify only a valid authority using an exact trusted gateway URL",async()=>{
  assert.equal((await send("verify",{merchant_id:merchant,amount:110000,authority:"bad"})).status,400);
  assert.equal((await send("verify",{merchant_id:merchant,amount:110000,
    authority:"A000000000000000000000000000000000"})).status,200);
  assert.equal(calls.at(-1).url,"https://payment.zarinpal.com/pg/v4/payment/verify.json");
});
test.after(()=>server.close());
