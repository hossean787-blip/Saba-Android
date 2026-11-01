const base=Deno.env.get("SUPABASE_URL")??"";
const anon=Deno.env.get("SUPABASE_ANON_KEY")??"";
const secret=Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")??"";
const merchant=Deno.env.get("ZARINPAL_MERCHANT_ID")??"";
const callback=base+"/functions/v1/sura-zarinpal/callback";
const api="https://api.zarinpal.com/pg/v4/payment/";
const hdr={"content-type":"application/json","cache-control":"no-store"};
const output=(v:unknown,status=200)=>new Response(JSON.stringify(v),{status,headers:hdr});
async function db(path:string,method="GET",body?:unknown){
 const r=await fetch(base+"/rest/v1/"+path,{method,headers:{apikey:secret,authorization:"Bearer "+secret,"content-type":"application/json",prefer:"return=representation"},body:body===undefined?undefined:JSON.stringify(body)});
 if(!r.ok)throw Error("db_error_"+r.status);
 return r.json();
}
async function verifiedUser(req:Request){
 const auth=req.headers.get("authorization")??"";
 if(!auth.startsWith("Bearer "))return null;
 const r=await fetch(base+"/auth/v1/user",{headers:{apikey:anon,authorization:auth}});
 if(!r.ok)return null;
 const u=await r.json();
 return u?.id&&u?.email_confirmed_at?u:null;
}
async function gateway(endpoint:string,data:unknown){
 const r=await fetch(api+endpoint,{method:"POST",headers:{"content-type":"application/json"},body:JSON.stringify(data)});
 if(!r.ok)throw Error("gateway_http");
 return r.json();
}
Deno.serve(async(req)=>{
 const url=new URL(req.url), route=url.pathname.split("/").pop();
 if(!base||!anon||!secret)return output({error:"not_configured"},503);
 if(route==="callback"){
  const authority=url.searchParams.get("Authority")??"";
  const paid=url.searchParams.get("Status")==="OK";
  const finish=(message:string)=>new Response('<!doctype html><html lang="fa" dir="rtl"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>پرداخت سورا</title><body style="font-family:sans-serif;text-align:center;padding:48px;background:#f0f7ff"><h2>'+message+'</h2><p>برای مشاهده نتیجه نهایی به برنامه سورا برگردید.</p></body></html>',{headers:{"content-type":"text/html; charset=utf-8","cache-control":"no-store"}});
  if(!/^[a-zA-Z0-9_-]{20,64}$/.test(authority))return finish("شناسه پرداخت نامعتبر است.");
  try{
   const orders=await db("sura_payment_orders?select=id,amount_rial,state,ref_id&authority=eq."+encodeURIComponent(authority)+"&limit=1");
   const order=orders[0];
   if(!order)return finish("سفارش یافت نشد.");
   if(order.state==="verified")return finish("این پرداخت قبلاً ثبت شده است.");
   if(!paid)return finish("پرداخت تأیید نشده است.");
   if(!merchant)return finish("درگاه هنوز پیکربندی نشده است.");
   const v=await gateway("verify.json",{merchant_id:merchant,amount:order.amount_rial,authority});
   if(v?.data?.code!==100 && v?.data?.code!==101)return finish("تأیید پرداخت ناموفق بود.");
   if(!v?.data?.ref_id)return finish("شماره پیگیری معتبر دریافت نشد.");
   const result=await db("rpc/sura_settle_verified_payment","POST",{p_order_id:order.id,p_authority:authority,p_ref_id:String(v.data.ref_id)});
   return finish(result===true||result===false?"پرداخت در سرور بررسی و ثبت شد.":"وضعیت پرداخت نیازمند بررسی است.");
  }catch{return finish("نتیجه پرداخت در حال بررسی است؛ مجدداً پرداخت نکنید.");}
 }
 if(req.method!=="POST")return output({error:"method_not_allowed"},405);
 const user=await verifiedUser(req);
 if(!user)return output({error:"unauthorized"},401);
 try{
  const body=await req.json();
  if(route==="status"){
   const orderId=String(body?.orderId??"");
   if(!/^[0-9a-f-]{36}$/i.test(orderId))return output({error:"invalid_order"},400);
   const orders=await db("sura_payment_orders?select=id,state,credit_units,amount_rial,ref_id&owner_user_id=eq."+user.id+"&id=eq."+orderId);
   return orders[0]?output(orders[0]):output({error:"not_found"},404);
  }
  if(route!=="create")return output({error:"not_found"},404);
  if(!merchant||!/^[0-9a-f-]{36}$/i.test(merchant))return output({error:"merchant_not_configured"},503);
  const quantity=Number(body?.quantity);
  if(!Number.isSafeInteger(quantity)||quantity<1||quantity>999999)return output({error:"invalid_quantity"},400);
  const quote=await db("rpc/sura_quote_credits","POST",{p_quantity:quantity});
  if(quote?.error||quote?.currency!=="IRR"||quote.total_rial<=0)return output({error:"price_unavailable"},503);
  const created=await db("sura_payment_orders","POST",{owner_user_id:user.id,amount_rial:quote.total_rial,credit_units:quantity,state:"created"});
  const order=created[0]; if(!order?.id)throw Error("order_not_created");
  const payment=await gateway("request.json",{merchant_id:merchant,amount:quote.total_rial,callback_url:callback,description:"SURA invoice credits - "+quantity});
  const authority=String(payment?.data?.authority??"");
  if(payment?.data?.code!==100||!/^[a-zA-Z0-9_-]{20,64}$/.test(authority))return output({error:"gateway_unavailable",orderId:order.id},502);
  await db("sura_payment_orders?id=eq."+order.id,"PATCH",{authority,state:"requested"});
  return output({orderId:order.id,amountRial:quote.total_rial,credits:quantity,paymentUrl:"https://www.zarinpal.com/pg/StartPay/"+authority});
 }catch{return output({error:"payment_service_unavailable"},503);}
});