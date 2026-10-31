const base=Deno.env.get("SUPABASE_URL")??"";
const anon=Deno.env.get("SUPABASE_ANON_KEY")??"";
const service=Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")??"";
const pepper=Deno.env.get("SURA_PROMO_PEPPER")??service;
const headers={"content-type":"application/json","cache-control":"no-store"};
const reply=(obj:unknown,status=200)=>new Response(JSON.stringify(obj),{status,headers});
async function digest(input:string) {
 const key=await crypto.subtle.importKey("raw",new TextEncoder().encode(pepper),"HMAC",false,["sign"]);
 const bytes=new Uint8Array(await crypto.subtle.sign("HMAC",key,new TextEncoder().encode(input)));
 return Array.from(bytes,x=>x.toString(16).padStart(2,"0")).join("");
}
Deno.serve(async req=>{
 if(req.method!=="POST")return reply({error:"method_not_allowed"},405);
 if(!base||!anon||!service||pepper.length<32)return reply({error:"not_configured"},503);
 const authorization=req.headers.get("authorization")??"";
 if(!authorization.startsWith("Bearer "))return reply({error:"unauthorized"},401);
 const token=authorization.substring(7);
 const userResponse=await fetch(base+"/auth/v1/user",{headers:{apikey:anon,authorization:"Bearer "+token}});
 if(!userResponse.ok)return reply({error:"unauthorized"},401);
 const user=await userResponse.json();
 if(!user?.id||!user?.email_confirmed_at)return reply({error:"unverified_account"},403);
 let body:any;try{body=await req.json()}catch{return reply({error:"invalid_json"},400)}
 const code=String(body?.code??"").trim();
 const deviceId=String(body?.deviceId??"").trim();
 if(code.length<1||code.length>128)return reply({error:"invalid_code"},400);
 const check=await fetch(base+"/rest/v1/rpc/sura_validate_promo",{
 method:"POST",headers:{"apikey":service,"authorization":"Bearer "+service,"content-type":"application/json"},
 body:JSON.stringify({p_code:code})
 });
 if(!check.ok)return reply({error:"promo_configuration_error"},503);
 if(await check.json()!==true)return reply({error:"invalid_code"},400);
 if(!/^[a-f0-9]{16}$/i.test(deviceId))return reply({error:"invalid_device"},400);
 const ip=(req.headers.get("cf-connecting-ip")??req.headers.get("x-forwarded-for")?.split(",")[0]??"").trim();
 const device=await digest("device:"+deviceId.toLowerCase());
 const ipHash=ip?await digest("ip:"+ip):null;
 const response=await fetch(base+"/rest/v1/rpc/sura_redeem_promo",{
 method:"POST",
 headers:{"apikey":service,"authorization":"Bearer "+service,"content-type":"application/json"},
 body:JSON.stringify({p_account:user.id,p_device:device,p_ip:ipHash})
 });
 if(!response.ok)return reply({error:"redeem_failed"},503);
 const result=await response.json();
 if(result?.error==="already_redeemed")return reply({error:"already_redeemed",granted:0},409);
 return reply({granted:3,remaining:3,note:"server_promo_balance_not_invoice_spend"});
});