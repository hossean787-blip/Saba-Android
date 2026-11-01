const base=Deno.env.get("SUPABASE_URL")??"";
const anon=Deno.env.get("SUPABASE_ANON_KEY")??"";
const service=Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")??"";
const json=(v:unknown,status=200)=>new Response(JSON.stringify(v),{status,headers:{"content-type":"application/json","cache-control":"no-store"}});
Deno.serve(async(req)=>{
 if(req.method!=="POST")return json({error:"method_not_allowed"},405);
 if(!base||!anon||!service)return json({error:"not_configured"},503);
 const authorization=req.headers.get("authorization")??"";
 if(!authorization.startsWith("Bearer "))return json({error:"unauthorized"},401);
 try{
  const auth=await fetch(base+"/auth/v1/user",{headers:{apikey:anon,authorization}});
  if(!auth.ok)return json({error:"unauthorized"},401);
  const user=await auth.json();
  if(!user?.id||!user?.email_confirmed_at)return json({error:"unverified_account"},403);
  const body=await req.json();
  const quantity=Number(body?.quantity);
  if(!Number.isSafeInteger(quantity)||quantity<1||quantity>999999)return json({error:"invalid_quantity"},400);
  const response=await fetch(base+"/rest/v1/rpc/sura_quote_credits",{
   method:"POST",headers:{apikey:service,authorization:"Bearer "+service,"content-type":"application/json"},
   body:JSON.stringify({p_quantity:quantity})
  });
  if(!response.ok)return json({error:"price_unavailable"},503);
  const quote=await response.json();
  return json({...quote,payment_enabled:/^[0-9a-f-]{36}$/i.test(Deno.env.get("ZARINPAL_MERCHANT_ID")??"")});
 }catch{return json({error:"service_unavailable"},503);}
});