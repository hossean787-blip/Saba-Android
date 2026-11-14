import { acceptedGatewayAuthority, recoverableOrderId, validAuthority, validCreditQuote, validMerchantId, validOrderId, verifiedGatewayReference } from "./payment_rules.ts";

const base = Deno.env.get("SUPABASE_URL") ?? "";
const anon = Deno.env.get("SUPABASE_ANON_KEY") ?? "";
const secret = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
const merchant = Deno.env.get("ZARINPAL_MERCHANT_ID") ?? "";
const callback = base + "/functions/v1/sura-zarinpal/callback";
// Current ZarinPal v4 production host. The former api.zarinpal.com host is obsolete.
const gatewayBase = "https://payment.zarinpal.com/pg/v4/payment/";
const egressUrl = Deno.env.get("SURA_PAYMENT_EGRESS_URL") ?? "";
const egressSecret = Deno.env.get("SURA_PAYMENT_EGRESS_SECRET") ?? "";

function proxyBase():string|null {
  if (!egressUrl && !egressSecret) return null;
  if (!egressUrl || egressSecret.length < 32) throw Error("payment_egress_not_configured");
  const parsed=new URL(egressUrl);
  if (parsed.protocol!=="https:" || parsed.username || parsed.password ||
      parsed.search || parsed.hash || parsed.pathname!=="/")
    throw Error("payment_egress_not_configured");
  return parsed.origin;
}

async function proxyHeaders(kind:"request"|"verify",body:string):Promise<Record<string,string>> {
  const time=Date.now().toString(),nonce=crypto.randomUUID();
  const key=await crypto.subtle.importKey("raw",new TextEncoder().encode(egressSecret),
    {name:"HMAC",hash:"SHA-256"},false,["sign"]);
  const bytes=new Uint8Array(await crypto.subtle.sign("HMAC",key,
    new TextEncoder().encode(time+"."+nonce+"."+kind+"."+body)));
  const digest=Array.from(bytes,b=>b.toString(16).padStart(2,"0")).join("");
  return {"x-sura-time":time,"x-sura-nonce":nonce,"x-sura-signature":digest};
}
const ready = validMerchantId(merchant);
const staticEgressReady = !!egressUrl && egressSecret.length >= 32;
const checkoutReady = ready && staticEgressReady &&
  Deno.env.get("SURA_PAYMENTS_LIVE_ENABLED") === "true";
const jsonHeaders = {"content-type":"application/json; charset=utf-8","cache-control":"no-store"};
const reply = (value:unknown,status=200) =>
  new Response(JSON.stringify(value),{status,headers:jsonHeaders});

type PaymentOrder = {
  id:string; owner_user_id:string; authority:string|null;
  amount_rial:number; credit_units:number; state:string; ref_id:string|null
};

async function db(path:string,method="GET",body?:unknown):Promise<any> {
  // Service role is server-only. Client-controlled fields never enter the target URL.
  const response = await fetch(base+"/rest/v1/"+path,{
    method,
    headers:{apikey:secret,authorization:"Bearer "+secret,
      "content-type":"application/json",prefer:"return=representation"},
    body:body===undefined?undefined:JSON.stringify(body),
    signal:AbortSignal.timeout(12000)
  });
  if(!response.ok)throw Error("database_unavailable");
  const data = await response.text();
  if(data.length>1000000)throw Error("database_response_too_large");
  return JSON.parse(data);
}

/** Never leave an unissued checkout order indefinitely in "created" after gateway failure. */
async function failCreatedOrder(orderId:string):Promise<void> {
  try {
    await db("sura_payment_orders?id=eq."+encodeURIComponent(orderId)+
      "&state=eq.created&authority=is.null","PATCH",{state:"failed"});
  } catch {
    // Keep the original payment error; do not claim cleanup succeeded.
    console.error("sura_zarinpal_created_order_cleanup_failed");
  }
}

async function verifiedUser(req:Request):Promise<{id:string}|null> {
  const token=req.headers.get("authorization")??"";
  if(!token.startsWith("Bearer ")||token.length>20000)return null;
  const response=await fetch(base+"/auth/v1/user",{
    headers:{apikey:anon,authorization:token},
    signal:AbortSignal.timeout(10000)
  });
  if(!response.ok)return null;
  const user=await response.json();
  return validOrderId(user?.id)&&user?.email_confirmed_at?{id:user.id}:null;
}

async function gateway(endpoint:"request.json"|"verify.json",body:unknown):Promise<any> {
  if(!ready)throw Error("merchant_not_configured");
  let response: Response;
  try {
    const kind=endpoint==="request.json"?"request":"verify";
    const serialized=JSON.stringify(body);
    const egress=proxyBase();
    const headers:Record<string,string>={"content-type":"application/json","accept":"application/json"};
    if (egress) Object.assign(headers,await proxyHeaders(kind,serialized));
    response=await fetch(egress ? egress+"/gateway/"+kind : gatewayBase+endpoint,{
      method:"POST",redirect:"error",headers,
      body:serialized,signal:AbortSignal.timeout(15000)
    });
  }catch(error){
    console.error("sura_zarinpal_gateway_network",error instanceof Error ? error.name : "unknown");
    throw Error("gateway_network_unavailable");
  }
  const text=await response.text();
  if(text.length>100000)throw Error("gateway_response_too_large");
  let payload: any;
  try { payload = JSON.parse(text); }
  catch { throw Error("gateway_invalid_response"); }
  if(!response.ok){
    // Do not log or disclose the full gateway response, credentials or card data.
    const rawCode=payload?.errors?.code;
    const gatewayCode=typeof rawCode==="number" && Number.isSafeInteger(rawCode)
      ? rawCode : null;
    console.error("sura_zarinpal_gateway_http",JSON.stringify({
      endpoint,httpStatus:response.status,gatewayCode
    }));
    throw Error("gateway_http_"+response.status);
  }
  return payload;
}

async function getOrderByAuthority(authority:string):Promise<PaymentOrder|null> {
  const list=await db("sura_payment_orders?select=id,owner_user_id,authority,amount_rial,credit_units,state,ref_id&authority=eq."+
    encodeURIComponent(authority)+"&limit=1");
  return list[0]??null;
}

async function getOwnerOrder(userId:string,orderId:string):Promise<PaymentOrder|null> {
  const list=await db("sura_payment_orders?select=id,owner_user_id,authority,amount_rial,credit_units,state,ref_id&owner_user_id=eq."+
    encodeURIComponent(userId)+"&id=eq."+encodeURIComponent(orderId)+"&limit=1");
  return list[0]??null;
}

/** Only ZarinPal's server-to-server verify response can fund credits.
 * Code 101 means the gateway already verified this same authority: this is
 * essential to recover if code 100 succeeded but DB settlement was interrupted.
 * The DB transaction enforces unique authority/ref_id and one grant per order. */
async function getLatestRecoverableOrder(userId:string):Promise<string|null> {
  const rows=await db("sura_payment_orders?select=id,state,authority"+
    "&owner_user_id=eq."+encodeURIComponent(userId)+
    "&state=eq.requested&authority=not.is.null"+
    "&order=created_at.desc&limit=1");
  return recoverableOrderId(rows[0]);
}

async function reconcile(order:PaymentOrder):Promise<boolean> {
  if(order.state==="verified")return true;
  if(order.state!=="requested"||!validAuthority(order.authority)||
    !Number.isSafeInteger(order.amount_rial)||order.amount_rial<=0||!ready)return false;
  const verification=await gateway("verify.json",{
    merchant_id:merchant,amount:order.amount_rial,authority:order.authority
  });
  const reference=verifiedGatewayReference(verification);
  if(reference===null)return false;
  const settled=await db("rpc/sura_settle_verified_payment","POST",{
    p_order_id:order.id,p_authority:order.authority,p_ref_id:reference
  });
  if(settled!==true&&settled!==false)throw Error("unexpected_settlement_result");
  return true;
}

function callbackPage(message:string):Response {
  // Message is always one of our fixed strings, never user input.
  const html='<!doctype html><html lang="fa" dir="rtl"><meta charset="utf-8">'+
    '<meta name="viewport" content="width=device-width"><title>پرداخت سورا</title>'+
    '<body style="font-family:sans-serif;text-align:center;padding:48px;background:#f0f7ff">'+
    '<h2>'+message+'</h2><p>برای دیدن وضعیت قطعی، به برنامه سورا برگردید و استعلام پرداخت را بزنید.</p></body></html>';
  return new Response(html,{headers:{
    "content-type":"text/html; charset=utf-8","cache-control":"no-store",
    "x-content-type-options":"nosniff",
    "content-security-policy":"default-src 'none'; style-src 'unsafe-inline'"
  }});
}

Deno.serve(async (req:Request)=>{
  const url=new URL(req.url),route=url.pathname.split("/").pop();
  if(!base||!anon||!secret)return reply({error:"server_not_configured"},503);
  // Safe, read-only operational diagnosis; never expose the merchant identifier.
  if(route==="health" && req.method==="GET")
    return reply({service:"sura-zarinpal",merchant_configured:ready,
      static_egress_configured:staticEgressReady,checkout_enabled:checkoutReady});
  if(route==="callback"){
    if(req.method!=="GET")return reply({error:"method_not_allowed"},405);
    const authority=url.searchParams.get("Authority");
    if(!validAuthority(authority))return callbackPage("شناسه پرداخت نامعتبر است.");
    try{
      const order=await getOrderByAuthority(authority);
      if(!order)return callbackPage("سفارش یافت نشد.");
      if(order.state==="verified")return callbackPage("پرداخت قبلاً با موفقیت ثبت شده است.");
      // Browser query parameters are not proof of payment; only direct verify is.
      if(url.searchParams.get("Status")!=="OK")
        return callbackPage("پرداخت تأیید نشده است؛ وضعیت را در سورا بررسی کنید.");
      const settled=await reconcile(order);
      return callbackPage(settled
        ?"پرداخت تأیید شد و اعتبار در حساب ثبت گردید."
        :"وضعیت پرداخت هنوز قطعی نیست؛ پیش از پرداخت مجدد استعلام کنید.");
    }catch{
      return callbackPage("نتیجه پرداخت در حال بررسی است؛ دوباره پرداخت نکنید.");
    }
  }
  if(req.method!=="POST")return reply({error:"method_not_allowed"},405);
  let user:{id:string}|null=null;
  try{user=await verifiedUser(req);}catch{return reply({error:"auth_unavailable"},503);}
  if(!user)return reply({error:"unauthorized"},401);
  try{
    if(Number(req.headers.get("content-length")??0)>8192)
      return reply({error:"request_too_large"},413);
    const payload=await req.json();
    if(route==="pending") {
      return reply({orderId:await getLatestRecoverableOrder(user.id)});
    }
    if(route==="status"){
      const id=payload?.orderId;
      if(!validOrderId(id))return reply({error:"invalid_order"},400);
      const order=await getOwnerOrder(user.id,id);
      if(!order)return reply({error:"not_found"},404);
      if(order.state==="requested"&&ready){
        // Callback may never arrive (browser closed/network error). An authenticated
        // status check can safely reconcile the original server-owned order.
        try{await reconcile(order);}catch{/* No credit issued without a gateway confirmation. */}
      }
      const latest=await getOwnerOrder(user.id,id);
      return latest?reply({
        orderId:latest.id,state:latest.state,credit_units:latest.credit_units,
        amount_rial:latest.amount_rial,ref_id:latest.ref_id
      }):reply({error:"not_found"},404);
    }
    if(route!=="create")return reply({error:"not_found"},404);
    if(!checkoutReady)return reply({error:"checkout_not_enabled"},503);
    const quantity=payload?.quantity;
    if(!Number.isSafeInteger(quantity)||quantity<1||quantity>999999)
      return reply({error:"invalid_quantity"},400);
    const quote=await db("rpc/sura_quote_credits","POST",{p_quantity:quantity});
    if(!validCreditQuote(quote,quantity))return reply({error:"price_unavailable"},503);
    const amount=quote.total_rial as number;
    const orders=await db("sura_payment_orders","POST",{
      owner_user_id:user.id,amount_rial:amount,credit_units:quantity,state:"created"
    });
    const order=orders[0];
    if(!validOrderId(order?.id))throw Error("order_creation_failed");
    let payment:any;
    try {
      payment=await gateway("request.json",{
        merchant_id:merchant,amount,currency:"IRR",callback_url:callback,
        description:"SURA invoice credits - "+quantity
      });
    } catch(error) {
      await failCreatedOrder(order.id);
      throw error;
    }
    const authority=acceptedGatewayAuthority(payment);
    if(authority===null) {
      await failCreatedOrder(order.id);
      return reply({error:"gateway_unavailable",orderId:order.id},502);
    }
    await db("sura_payment_orders?id=eq."+encodeURIComponent(order.id),"PATCH",{
      authority,state:"requested"
    });
    return reply({orderId:order.id,amountRial:amount,credits:quantity,
      paymentUrl:"https://www.zarinpal.com/pg/StartPay/"+authority});
  }catch(error){
    const kind=error instanceof Error ? error.message : "";
    if(kind.startsWith("gateway_http_"))
      return reply({error:"gateway_http_error",httpStatus:Number(kind.slice(13))||502},502);
    if(kind==="gateway_network_unavailable")
      return reply({error:"gateway_network_unavailable"},503);
    if(kind==="gateway_unavailable" || kind==="gateway_invalid_response" ||
       kind==="gateway_response_too_large")
      return reply({error:"gateway_unavailable"},502);
    if(kind==="database_unavailable" || kind==="order_creation_failed")
      return reply({error:"order_service_unavailable"},503);
    return reply({error:"payment_service_unavailable"},503);
  }
});
