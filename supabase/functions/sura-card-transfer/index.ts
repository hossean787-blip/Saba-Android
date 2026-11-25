// Bank transfer receipt workflow. No public settlement endpoint.
const base=Deno.env.get("SUPABASE_URL")??"",anon=Deno.env.get("SUPABASE_ANON_KEY")??"",service=Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")??"";
const admin="eb80ffa2-c2b4-4857-b23a-f7f61f6c1fdc",email="hossean787@gmail.com";
const cors={"access-control-allow-origin":"https://appassets.androidplatform.net","access-control-allow-methods":"POST,OPTIONS","access-control-allow-headers":"authorization,apikey,content-type","cache-control":"no-store","x-content-type-options":"nosniff"};
const reply=(v:unknown,status=200)=>new Response(JSON.stringify(v),{status,headers:{...cors,"content-type":"application/json"}});
const uuid=(v:unknown):v is string=>typeof v==="string"&&/^[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}$/.test(v);
const safeNum=(v:unknown):v is number=>typeof v==="number"&&Number.isSafeInteger(v)&&v>0;
const sh={apikey:service,authorization:"Bearer "+service,"content-type":"application/json"};
async function rest(path:string,method="GET",body?:unknown){const r=await fetch(base+"/rest/v1/"+path,{method,headers:sh,body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(15000)});if(!r.ok)throw Error("database_failed");return r.json();}
async function rpc(name:string,body:unknown){return rest("rpc/"+name,"POST",body);}
const fields="id,owner_id,kind,units,amount_rial,state,tracking,transfer_date,created_at,submitted_at,review_reason,reviewed_at";
async function bounded(req:Request){const reader=req.body?.getReader();if(!reader)throw Error("invalid_request");let total=0;const dec=new TextDecoder();let raw="";try{while(true){const r=await reader.read();if(r.done)break;total+=r.value.byteLength;if(total>680000){await reader.cancel();throw Error("too_large");}raw+=dec.decode(r.value,{stream:true});}return JSON.parse(raw+dec.decode());}finally{reader.releaseLock();}}
async function handler(req:Request){
 if(req.method==="OPTIONS")return new Response(null,{status:204,headers:cors});
 if(req.method!=="POST")return reply({error:"method_not_allowed"},405);
 if(!base||!anon||!service)return reply({error:"not_configured"},503);
 const authorization=req.headers.get("authorization")??"";
 if(!/^Bearer \S+$/.test(authorization)||authorization.length>20000)return reply({error:"unauthorized"},401);
 const a=await fetch(base+"/auth/v1/user",{headers:{apikey:anon,authorization},signal:AbortSignal.timeout(10000)});
 if(!a.ok)return reply({error:"unauthorized"},401);
 const u=await a.json();if(!uuid(u?.id)||!u.email_confirmed_at)return reply({error:"unauthorized"},401);
 let b:any;try{b=await bounded(req);}catch{return reply({error:"invalid_request"},400);}
 if(!b||typeof b!=="object"||Array.isArray(b))return reply({error:"invalid_request"},400);
 const route=new URL(req.url).pathname.split("/").pop();
 if(route==="quote"||route==="reserve"){
  if(!["single","monthly"].includes(b.kind)||!safeNum(b.units)||b.units>999999)return reply({error:"invalid_plan"},400);
  if(route==="reserve"&&!safeNum(b.total))return reply({error:"invalid_total"},400);
  const r=route==="quote"?await rpc("sura_card_quote",{p_kind:b.kind,p_units:b.units}):await rpc("sura_card_reserve",{p_owner:u.id,p_kind:b.kind,p_units:b.units,p_total:b.total});
  return reply(r,r.error?409:200);
 }
 if(route==="mine"){
  const rows=await rest("sura_card_requests?select="+fields+",destination_card,holder&owner_id=eq."+u.id+"&order=created_at.desc&limit=30");
  return reply({requests:rows});
 }
 if(route==="submit"){
  if(!uuid(b.id)||typeof b.tracking!=="string"||typeof b.date!=="string"||typeof b.receipt!=="string")return reply({error:"invalid_receipt"},400);
  const r=await rpc("sura_card_submit",{p_owner:u.id,p_id:b.id,p_tracking:b.tracking,p_date:b.date,p_receipt:b.receipt});
  return reply(r,r.error?409:200);
 }
 if(!["admin-list","admin-detail","admin-review","admin-monthly-price","admin-monthly-save"].includes(route??""))return reply({error:"not_found"},404);
 if(u.id!==admin||String(u.email).toLowerCase()!==email)return reply({error:"forbidden"},403);
 const roles=await rest("sura_admin_roles?select=role&user_id=eq."+admin+"&limit=1");
 if(roles[0]?.role!=="admin")return reply({error:"forbidden"},403);
 if(route==="admin-monthly-price"){
  const rows=await rest("sura_monthly_plan?select=price_rial,enabled,revision&id=eq.1&limit=1");
  const history=await rest("sura_monthly_price_audit?select=request_id,old_price,new_price,created_at,reason&order=new_revision.desc&limit=10");
  if(!rows[0])return reply({error:"plan_missing"},503);
  return reply({plan:rows[0],history});
 }
 if(route==="admin-monthly-save"){
  if(!uuid(b.request)||!Number.isSafeInteger(b.expected)||b.expected<0||!safeNum(b.price)||b.price>10000000000||typeof b.reason!=="string"||b.reason.trim().length<5||b.reason.length>500)return reply({error:"invalid_request"},400);
  const result=await rpc("sura_manager_monthly_price",{p_actor:u.id,p_request:b.request,p_expected:b.expected,p_price:b.price,p_reason:b.reason.trim()});
  return reply(result,result.error?409:200);
 }
 if(route==="admin-list"){
  const page=b.page??1;if(!safeNum(page)||page>10000)return reply({error:"invalid_page"},400);
  return reply({requests:await rest("sura_card_requests?select="+fields+"&state=eq.pending&order=submitted_at.asc&limit=30&offset="+((page-1)*30))});
 }
 if(!uuid(b.id))return reply({error:"invalid_id"},400);
 if(route==="admin-detail"){
  const rows=await rest("sura_card_requests?select="+fields+",destination_card,holder,receipt_jpeg,bank_reference&id=eq."+b.id+"&limit=1");
  if(!rows[0])return reply({error:"not_found"},404);
  const r=rows[0],hex=r.receipt_jpeg;delete r.receipt_jpeg;
  if(typeof hex==="string"&&hex.startsWith("\\x")&&hex.length<=1000002){
   const bytes=new Uint8Array((hex.length-2)/2);for(let i=0;i<bytes.length;i++)bytes[i]=parseInt(hex.slice(2+i*2,4+i*2),16);
   let binary="";for(let i=0;i<bytes.length;i+=8192)binary+=String.fromCharCode(...bytes.subarray(i,i+8192));
   r.receipt=btoa(binary);
  }
  return reply({request:r});
 }
 if(!["approved","rejected"].includes(b.decision)||typeof b.reason!=="string"||b.reason.length>500)return reply({error:"invalid_review"},400);
 if(b.decision==="approved"&&(!safeNum(b.amount)||typeof b.reference!=="string"||b.bankChecked!==true))return reply({error:"bank_confirmation_required"},400);
 const r=await rpc("sura_card_review",{p_actor:u.id,p_id:b.id,p_decision:b.decision,p_bank_reference:b.reference??null,p_amount:b.amount??null,p_reason:b.reason});
 return reply(r,r.error?409:200);
}
Deno.serve(async req=>{try{return await handler(req)}catch{return reply({error:"service_unavailable"},503)}});
