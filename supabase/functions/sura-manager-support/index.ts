/* Isolated support administration endpoint. Existing financial APIs remain untouched. */
const BASE=Deno.env.get("SUPABASE_URL")||"";
const ANON=Deno.env.get("SUPABASE_ANON_KEY")||"";
const SERVICE=Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")||"";
const ADMIN="eb80ffa2-c2b4-4857-b23a-f7f61f6c1fdc";
const EMAIL="hossean787@gmail.com";
const states=["open","in_progress","answered","closed"];
const cors={"access-control-allow-origin":"https://appassets.androidplatform.net","access-control-allow-methods":"POST,OPTIONS","access-control-allow-headers":"authorization,apikey,content-type","cache-control":"no-store","vary":"Origin","x-content-type-options":"nosniff"};
const id=v=>typeof v==="string"&&/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(v);
const send=(v,status=200)=>new Response(JSON.stringify(v),{status,headers:{...cors,"content-type":"application/json; charset=utf-8"}});
const serviceHeaders={apikey:SERVICE,authorization:"Bearer "+SERVICE};
async function read(path,headers=serviceHeaders) {
 const r=await fetch(BASE+path,{headers,signal:AbortSignal.timeout(12000)});
 if(!r.ok)throw Error("upstream_failed");
 return r.json();
}
async function rpc(name,body) {
 const r=await fetch(BASE+"/rest/v1/rpc/"+name,{method:"POST",headers:{...serviceHeaders,"content-type":"application/json"},body:JSON.stringify(body),signal:AbortSignal.timeout(15000)});
 if(!r.ok)throw Error("database_failed");return r.json();
}
async function handler(req) {
 if(req.method==="OPTIONS")return new Response(null,{status:204,headers:cors});
 if(req.method!=="POST")return send({error:"method_not_allowed"},405);
 if(!BASE||!ANON||!SERVICE)return send({error:"not_configured"},503);
 const auth=req.headers.get("authorization")||"";
 if(!/^Bearer \S+$/.test(auth))return send({error:"unauthorized"},401);
 const u=await read("/auth/v1/user",{apikey:ANON,authorization:auth}).catch(()=>null);
 if(!u||u.id!==ADMIN||String(u.email||"").toLowerCase()!==EMAIL||!u.email_confirmed_at)return send({error:"forbidden"},403);
 const role=await read("/rest/v1/sura_admin_roles?select=role&user_id=eq."+ADMIN+"&limit=1");
 if(!Array.isArray(role)||role.length!==1||role[0].role!=="admin")return send({error:"forbidden"},403);
 const route=new URL(req.url).pathname.split("/").filter(Boolean).pop();
 const raw=await req.text();
 if(raw.length>(route==="catalog-publish"?900000:20000))return send({error:"too_large"},413);
 let b;try{b=JSON.parse(raw)}catch{return send({error:"invalid_request"},400)}
 if(!b||typeof b!=="object"||Array.isArray(b))return send({error:"invalid_request"},400);
 const page=b.page===undefined?1:b.page;
 if(!Number.isSafeInteger(page)||page<1||page>10000)return send({error:"invalid_page"},400);
 if(route==="list"){
  if(b.status!==undefined&&!states.includes(b.status))return send({error:"invalid_status"},400);
  if(b.owner!==undefined&&!id(b.owner))return send({error:"invalid_owner"},400);
  const rows=await rpc("sura_support_manager_list",{p_status:b.status||null,p_owner:b.owner||null,p_page:page});
  return send({tickets:rows,page});
 }
 if(route==="consent"){
  if(!id(b.owner))return send({error:"invalid_owner"},400);
  const rows=await read("/rest/v1/sura_legal_acceptances?select=legal_version,accepted_at&owner_id=eq."+b.owner+"&order=accepted_at.desc&limit=50");
  return send({acceptances:rows});
 }
 if(route==="catalog-info"){
  const rows=await read("/rest/v1/sura_starter_catalog?select=version,source_hash,as_of,entries&order=version.desc&limit=1");
  return send({catalog:rows[0]||null});
 }
 if(route==="catalog-publish"){
  if(!Number.isSafeInteger(b.expected)||b.expected<0||!Array.isArray(b.entries)||typeof b.source_hash!=="string"||typeof b.as_of!=="string")return send({error:"invalid_source"},400);
  const result=await rpc("sura_publish_starter",{p_actor:ADMIN,p_expected:b.expected,p_hash:b.source_hash,p_as_of:b.as_of,p_entries:b.entries});
  return send(result,result.error?409:200);
 }
 if(!id(b.ticket))return send({error:"invalid_ticket"},400);
 if(route==="thread"){
  const tickets=await read("/rest/v1/sura_support_tickets?select=id,owner_id,category,status,message,response,created_at&id=eq."+b.ticket+"&limit=1");
  if(!tickets.length)return send({error:"ticket_not_found"},404);
  const messages=await read("/rest/v1/sura_support_messages?select=id,sender,body,created_at&ticket_id=eq."+b.ticket+"&order=created_at.desc,id.desc&limit=30&offset="+((page-1)*30));
  const audit=await read("/rest/v1/sura_support_manager_audit?select=request_id,status_before,status_after,created_at&ticket_id=eq."+b.ticket+"&order=created_at.desc&limit=30");
  return send({ticket:tickets[0],messages,audit,page});
 }
 if(route==="act"){
  if(!id(b.request)||!states.includes(b.expected)||!states.includes(b.status)||typeof b.reply!=="string"||b.reply.trim().length>4000||/-----BEGIN (?:RSA |EC |ENCRYPTED )?PRIVATE KEY-----/i.test(b.reply))return send({error:"invalid_action"},400);
  const result=await rpc("sura_support_manager_act",{p_actor:ADMIN,p_ticket:b.ticket,p_request:b.request,p_expected:b.expected,p_status:b.status,p_reply:b.reply.trim()});
  return send(result,result.error?409:200);
 }
 return send({error:"not_found"},404);
}
Deno.serve(async req=>{try{return await handler(req)}catch{return send({error:"service_unavailable"},502)}});
