const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const src=fs.readFileSync(path.resolve(__dirname,'../../supabase/functions/sura-manager-support/index.ts'),'utf8');
let handler,actor='eb80ffa2-c2b4-4857-b23a-f7f61f6c1fdc',role='admin',calls=[];
const ticket='550e8400-e29b-41d4-a716-446655440000';
const ok=x=>new Response(JSON.stringify(x),{status:200});
async function mock(url,opts={}){
 calls.push({url,body:opts.body&&JSON.parse(opts.body)});
 if(url.includes('/auth/v1/user'))return ok({id:actor,email:'hossean787@gmail.com',email_confirmed_at:'yes'});
 if(url.includes('sura_admin_roles?'))return ok([{role}]);
 if(url.includes('rpc/sura_support_manager_list'))return ok([]);
 if(url.includes('rpc/sura_support_manager_act'))return ok({status:'applied',request_id:JSON.parse(opts.body).p_request});
 throw Error('Unexpected upstream');
}
vm.runInNewContext(src,{Deno:{env:{get:()=> 'https://test.invalid'},serve:f=>handler=f},fetch:mock,Request,Response,URL,AbortSignal,JSON,Number,Array,String,Error,RegExp});
async function call(route,body,auth=true){const r=await handler(new Request('https://test.invalid/'+route,{method:'POST',headers:auth?{authorization:'Bearer test'}:{},body:JSON.stringify(body)}));return r.status;}
(async()=>{
 assert.equal(await call('list',{},false),401);
 actor=ticket;assert.equal(await call('list',{}),403);actor='eb80ffa2-c2b4-4857-b23a-f7f61f6c1fdc';
 role='editor';assert.equal(await call('list',{}),403);role='admin';
 assert.equal(await call('list',{page:0}),400);
 assert.equal(await call('list',{status:'bad'}),400);
 assert.equal(await call('list',{}),200);
 assert.equal(await call('act',{ticket,expected:'open',status:'answered',reply:'test',request:ticket}),200);
 assert.equal(await call('act',{ticket,expected:'open',status:'answered',reply:'-----BEGIN PRIVATE KEY-----',request:ticket}),400);
 assert.equal(calls.filter(c=>c.url.includes('rpc/sura_support_manager_act')).length,1);
 console.log('PASS: 8 support admin API authorization and validation checks');
})().catch(e=>{console.error(e);process.exit(1)});
