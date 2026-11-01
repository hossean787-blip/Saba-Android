const base=Deno.env.get("SUPABASE_URL")??"";
const anon=Deno.env.get("SUPABASE_ANON_KEY")??"";
const service=Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")??"";
const headers={"content-type":"application/json","cache-control":"no-store"};
function reply(data:unknown,status=200){return new Response(JSON.stringify(data),{status,headers});}
async function rest(path:string,method="GET",data?:unknown){
 const r=await fetch(base+"/rest/v1/"+path,{method,headers:{apikey:service,authorization:"Bearer "+service,"content-type":"application/json"},body:data===undefined?undefined:JSON.stringify(data)});
 if(!r.ok)throw new Error("database_unavailable");
 return r.json();
}
Deno.serve(async(req)=>{
 if(req.method!=="POST")return reply({error:"method_not_allowed"},405);
 if(!base||!anon||!service)return reply({error:"not_configured"},503);
 const auth=req.headers.get("authorization")??"";
 if(!auth.startsWith("Bearer "))return reply({error:"unauthorized"},401);
 try{
  const a=await fetch(base+"/auth/v1/user",{headers:{apikey:anon,authorization:auth}});
  if(!a.ok)return reply({error:"unauthorized"},401);
  const user=await a.json();
  if(!user?.id||!user?.email_confirmed_at)return reply({error:"unverified_account"},403);
  const body=await req.json();
  const taxpayer=Number(body?.taxpayerId);
  if(!Number.isSafeInteger(taxpayer)||taxpayer<=0)return reply({error:"invalid_taxpayer"},400);
  const operation=String(body?.operation??"balance");
  if(operation==="balance"){
   const owner=await rest("sura_owner_wallets?select=credits&owner_id=eq."+encodeURIComponent(user.id));
   const target=await rest("sura_taxpayer_wallets?select=credits&owner_id=eq."+encodeURIComponent(user.id)+"&local_taxpayer_id=eq."+taxpayer);
   const history=await rest("sura_credit_transfers?select=id,credits,created_at&owner_id=eq."+encodeURIComponent(user.id)+"&local_taxpayer_id=eq."+taxpayer+"&order=created_at.desc&limit=10");
   return reply({ownerBalance:owner[0]?.credits??0,taxpayerBalance:target[0]?.credits??0,history});
  }
  if(operation!=="transfer")return reply({error:"unknown_operation"},400);
  const amount=Number(body?.credits);
  const requestId=String(body?.requestId??"");
  if(!Number.isSafeInteger(amount)||amount<=0||amount>100000000||!/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(requestId))
   return reply({error:"invalid_transfer"},400);
  const result=await rest("rpc/sura_transfer_owner_credit","POST",{p_owner:user.id,p_taxpayer:taxpayer,p_credits:amount,p_request:requestId});
  return reply(result,result?.error?409:200);
 }catch{return reply({error:"service_unavailable"},503);}
});
