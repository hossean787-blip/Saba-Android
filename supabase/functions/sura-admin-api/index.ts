const BASE=Deno.env.get("SUPABASE_URL")||"";
const ANON=Deno.env.get("SUPABASE_ANON_KEY")||"";
const SERVICE=Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")||"";
const CORS={"access-control-allow-origin":"*","access-control-allow-methods":"GET,POST,OPTIONS","access-control-allow-headers":"authorization,apikey,content-type","cache-control":"no-store"};
const READ=new Set(["sura_owner_wallets","sura_taxpayer_wallets","sura_payment_orders","sura_credit_grants","sura_credit_transfers","sura_promo_redemptions","sura_promo_invoice_uses","sura_credit_pricing","sura_admin_credit_adjustments"]);
const UUID=/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const out=(data,status=200)=>new Response(JSON.stringify(data),{status,headers:{...CORS,"content-type":"application/json; charset=utf-8"}});
const relay=(content,status=200)=>new Response(content,{status,headers:{...CORS,"content-type":"application/json; charset=utf-8"}});
const head={apikey:SERVICE,authorization:"Bearer "+SERVICE};
const safeInt=(v,lo,hi)=>typeof v==="number"&&Number.isSafeInteger(v)&&v>=lo&&v<=hi;
async function identity(req){
 const h=req.headers.get("authorization")||"";
 if(!/^Bearer \S+$/.test(h))return null;
 const a=await fetch(BASE+"/auth/v1/user",{headers:{apikey:ANON,authorization:h},signal:AbortSignal.timeout(12000)}).catch(()=>null);
 if(!a?.ok)return null;
 const u=await a.json().catch(()=>null);
 if(!UUID.test(String(u?.id||""))||!u?.email_confirmed_at)return null;
 const r=await fetch(BASE+"/rest/v1/sura_admin_roles?user_id=eq."+u.id+"&select=role&limit=1",{headers:head,signal:AbortSignal.timeout(12000)}).catch(()=>null);
 if(!r?.ok)return null;
 const roles=await r.json().catch(()=>[]);
 const role=roles?.[0]?.role;
 if(!["admin","editor","viewer"].includes(role))return null;
 return {id:u.id,email:String(u.email||""),role};
}
async function rpc(name,params){
 const r=await fetch(BASE+"/rest/v1/rpc/"+name,{method:"POST",headers:{...head,"content-type":"application/json"},body:JSON.stringify(params),signal:AbortSignal.timeout(16000)});
 if(!r.ok)return out({error:"database_operation_failed"},502);
 return relay(await r.text());
}
async function ownerExists(owner){
 const r=await fetch(BASE+"/auth/v1/admin/users/"+owner,{headers:head,signal:AbortSignal.timeout(12000)});
 if(r.status===404)return false;
 if(!r.ok)throw new Error("owner_lookup_failed");
 return true;
}
Deno.serve(async(req)=>{
 try{
  if(req.method==="OPTIONS")return new Response(null,{status:204,headers:CORS});
  if(!BASE||!ANON||!SERVICE)return out({error:"server_not_configured"},503);
  const user=await identity(req);
  if(!user)return out({error:"unauthorized"},401);
  const path=new URL(req.url).pathname.split("/").pop();
  if(path==="me"&&req.method==="GET")return out(user);
  if(req.method!=="POST")return out({error:"method_not_allowed"},405);
  const body=await req.json().catch(()=>null);
  if(!body||Array.isArray(body)||typeof body!=="object")return out({error:"invalid_body"},400);
  if(path==="query"){
   const table=String(body.table||"");
   if(!READ.has(table))return out({error:"forbidden_table"},403);
   if(table==="sura_admin_credit_adjustments"&&user.role!=="admin")return out({error:"insufficient_role"},403);
   const q=new URLSearchParams(String(body.query||""));
   for(const field of q.keys())if(!["select","order","limit"].includes(field))return out({error:"forbidden_query"},400);
   const select=q.get("select")||"*",order=q.get("order")||"",limit=Number(q.get("limit")||100);
   if(!/^(\*|[a-z_][a-z_0-9]*(,[a-z_][a-z_0-9]*)*)$/.test(select)||
    (order&&!/^[a-z_][a-z_0-9]*\.(asc|desc)$/.test(order))||
    !safeInt(limit,1,1000))return out({error:"invalid_query"},400);
   const safe=new URLSearchParams({select,limit:String(limit)});
   if(order)safe.set("order",order);
   const r=await fetch(BASE+"/rest/v1/"+table+"?"+safe,{headers:{...head,accept:"application/json"},signal:AbortSignal.timeout(16000)});
   if(!r.ok)return out({error:"read_failed"},502);
   return relay(await r.text());
  }
  if(path==="wallet"){
   if(user.role!=="admin")return out({error:"insufficient_role"},403);
   if(!UUID.test(String(body.owner||"")))return out({error:"invalid_owner"},400);
   if(!await ownerExists(body.owner))return out({error:"owner_not_found"},404);
   const r=await fetch(BASE+"/rest/v1/sura_owner_wallets?owner_id=eq."+body.owner+"&select=credits&limit=1",{headers:{...head,accept:"application/json"},signal:AbortSignal.timeout(12000)});
   if(!r.ok)return out({error:"read_failed"},502);
   const rows=await r.json().catch(()=>[]);
   return out({credits:rows[0]?.credits??0});
  }
  if(path==="mutate"){
   if(user.role!=="admin")return out({error:"insufficient_role"},403);
   if(body.table!=="sura_credit_pricing"||body.method!=="PATCH"||body.filter!=="id=eq.1")return out({error:"forbidden_mutation"},403);
   const d=body.data;
   if(!d||Array.isArray(d)||typeof d!=="object"||!Object.keys(d).length||
    Object.keys(d).some(k=>!["price_per_invoice_rial","currency"].includes(k))||
    (d.price_per_invoice_rial!==undefined&&!safeInt(d.price_per_invoice_rial,1000,1000000000))||
    (d.currency!==undefined&&d.currency!=="IRR"))return out({error:"invalid_price"},400);
   const r=await fetch(BASE+"/rest/v1/sura_credit_pricing?id=eq.1",{method:"PATCH",headers:{...head,"content-type":"application/json",prefer:"return=representation"},body:JSON.stringify(d),signal:AbortSignal.timeout(16000)});
   if(!r.ok)return out({error:"price_update_failed"},502);
   const rows=await r.json().catch(()=>[]);
   return rows.length?out(rows):out({error:"price_not_found"},404);
  }
  if(path==="rpc"){
   if(user.role==="viewer")return out({error:"insufficient_role"},403);
   const p=body.params;
   if(body.fn!=="sura_transfer_owner_credit"||!p||Array.isArray(p)||typeof p!=="object"||
    Object.keys(p).sort().join(",")!=="p_credits,p_owner,p_request,p_taxpayer"||
    !UUID.test(String(p.p_owner))||!UUID.test(String(p.p_request))||
    !safeInt(p.p_taxpayer,1,Number.MAX_SAFE_INTEGER)||!safeInt(p.p_credits,1,1000000000))return out({error:"invalid_transfer"},400);
   return rpc("sura_transfer_owner_credit",p);
  }
  if(path==="adjust"){
   if(user.role!=="admin")return out({error:"insufficient_role"},403);
   const taxpayer=body.taxpayer===null?null:body.taxpayer;
   if(!UUID.test(String(body.owner||""))||!UUID.test(String(body.request||""))||
    (taxpayer!==null&&!safeInt(taxpayer,1,Number.MAX_SAFE_INTEGER))||
    !safeInt(body.expected,0,1000000000)||!safeInt(body.balance,0,1000000000)||
    typeof body.reason!=="string"||body.reason.trim().length<5||body.reason.trim().length>500)return out({error:"invalid_adjustment"},400);
   if(!await ownerExists(body.owner))return out({error:"owner_not_found"},404);
   return rpc("sura_admin_set_credit_balance",{
    p_owner:body.owner,p_taxpayer:taxpayer,p_new_balance:body.balance,
    p_expected_balance:body.expected,p_reason:body.reason.trim(),p_request:body.request,p_actor:user.id
   });
  }
  return out({error:"not_found"},404);
 }catch(_){return out({error:"internal_error"},500);}
});