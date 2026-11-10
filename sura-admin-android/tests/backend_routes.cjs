const assert=require('node:assert/strict'), fs=require('node:fs'), vm=require('node:vm');
const crypto=require('node:crypto');
const source=fs.readFileSync(require('node:path').resolve(__dirname,'../backend/sura-manager-api.ts'),'utf8');
const compiled=fs.readFileSync(require('node:path').resolve(__dirname,'../backend/sura-manager-api.compiled.js'),'utf8');
const digest=crypto.createHash('sha256').update(source).digest('hex');
assert(compiled.startsWith('// source-sha256:'+digest+'\n'),'backend TS & compiled JS are out of sync');
let handler;let actor='bb668800-e29b-41d4-a716-446655440111';let uid='550e8400-e29b-41d4-a716-446655440000';let role='admin';let upstream=[];
const ok=(value,headers={})=>new Response(JSON.stringify(value),{status:200,headers:{'content-type':'application/json',...headers}});
async function fetchMock(url,opts={}){
 const u=new URL(url);upstream.push({path:u.pathname,method:opts.method||'GET',query:u.searchParams,body:opts.body&&JSON.parse(opts.body)});
 if(u.pathname==='/auth/v1/user')return ok({id:actor,email:'admin@test.test',email_confirmed_at:'2026-11-09T08:00:00Z'});
 if(u.pathname==='/auth/v1/admin/users')return ok({users:[{id:uid,email:'client@example.com',email_confirmed_at:'2026-11-09T00:00:00Z',created_at:'2026-11-09T00:00:00Z'}]});
 if(u.pathname==='/auth/v1/admin/users/'+uid)return ok({id:uid,email:'client@example.com',email_confirmed_at:'2026-11-09T00:00:00Z'});
 if(u.pathname==='/rest/v1/sura_admin_roles'){
  return u.searchParams.has('user_id')?ok([{role}]):ok([{user_id:actor,role:'admin',updated_at:'2026-11-09T00:00:00Z'}]);
 }
 if(u.pathname==='/rest/v1/sura_credit_pricing')return ok([{price_per_invoice_rial:100000,currency:'IRR'}]);
 if(u.pathname==='/rest/v1/sura_owner_wallets')return ok([]);
 if(u.pathname==='/rest/v1/sura_taxpayer_wallets')return ok([]);
 if(u.pathname==='/rest/v1/sura_payment_orders')return ok([{id:'unit',state:'verified',amount_rial:100000}],{'content-range':'0-0/1'});
 if(u.pathname==='/rest/v1/sura_manager_role_audit')return ok([]);
 if(u.pathname==='/rest/v1/sura_manager_price_audit')return ok([]);
 if(u.pathname.startsWith('/rest/v1/rpc/')){
  if(u.pathname.endsWith('summary'))return ok({verified_orders:1,verified_revenue_rial:'100000'});
  return ok({status:'applied',receipt:'audit-1'});
 }
 throw Error('Unexpected upstream '+url);
}
vm.runInNewContext(compiled,{Deno:{env:{get:k=>({SUPABASE_URL:'https://sura.supabase.co',SUPABASE_ANON_KEY:'public-key',SUPABASE_SERVICE_ROLE_KEY:'secure-server-only'}[k])},serve:fn=>handler=fn},fetch:fetchMock,Response,Request,Headers,URL,URLSearchParams,AbortSignal,Date,Number,Array,JSON,Map,console,RegExp,Error,Promise,String,Object,Boolean,Set});
async function call(path,data={},token='valid'){
 const r=await handler(new Request('https://sura.supabase.co/functions/v1/sura-manager-api/'+path,{method:'POST',headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},body:JSON.stringify(data)}));
 return {status:r.status,payload:await r.json()};
}
(async()=>{
 assert.equal((await call('summary',{},null)).status,401);
 assert.equal((await call('summary',{})).payload.summary.verified_orders,1);
 assert.equal((await call('users',{page:0})).status,400);
 assert.equal((await call('users',{page:1,perPage:30})).payload.users[0].email,'client@example.com');
 assert.equal((await call('user',{id:uid})).payload.user.id,uid);
 assert.equal((await call('orders',{page:1,state:'verified'})).payload.total,1);
 assert.equal((await call('orders',{state:'DROP TABLE'})).status,400);
 assert.equal((await call('orders',{from:'INVALID'})).status,400);
 assert.equal((await call('health',{})).payload.status,'ok');
 assert.equal((await call('role-assign',{target:uid,role:'admin',expected:null,reason:'Test role assignment',request:uid,confirmEmail:'wrong@example.com'})).status,409);
 assert.equal((await call('role-assign',{target:uid,role:'viewer',expected:null,reason:'Test role assignment',request:uid,confirmEmail:'client@example.com'})).payload.status,'applied');
 assert.equal((await call('price-update',{price:120000,expected:100000,reason:'Pricing adjustment QA',request:uid})).payload.status,'applied');
 assert.equal((await call('audit',{kind:'role'})).payload.kind,'role');
 role='viewer';
 assert.equal((await call('role-list',{})).status,403);
 assert.equal((await call('role-assign',{target:uid,role:'admin',reason:'test role',request:uid,confirmEmail:'client@example.com'})).status,403);
 assert.equal((await call('users',{})).status,403);
 assert.equal((await call('summary',{})).status,200);
 assert.equal(upstream.filter(x=>x.path.includes('rpc/sura_manager_assign_role')).length,1);
 assert(!source.includes('eyJhbGci'), 'secret hardcoded');
 console.log('PASS: 17 authorization, validation, directory, page, price and audit API cases');
})().catch(e=>{console.error(e);process.exit(1)});
