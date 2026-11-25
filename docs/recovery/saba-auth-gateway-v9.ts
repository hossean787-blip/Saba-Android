const root = Deno.env.get("SUPABASE_URL");
const anon = Deno.env.get("SUPABASE_ANON_KEY");
const headers = {"content-type":"application/json; charset=utf-8","cache-control":"no-store"};
const respond=(data:unknown,status=200)=>new Response(JSON.stringify(data),{status,headers});
Deno.serve(async(req)=>{
 if(req.method!=="POST") return respond({error:"method_not_allowed"},405);
 const path=new URL(req.url).pathname;
 if(!root||!anon) return respond({error:"server_not_configured"},503);
 let body:Record<string,unknown>;
 try { body=await req.json(); } catch { return respond({error:"invalid_json"},400); }
 const endpoint=path.endsWith("/v1/auth/registration/start")?"signup":path.endsWith("/v1/auth/login/password")?"token?grant_type=password":path.endsWith("/v1/auth/logout")?"logout":null;
 if(!endpoint) return respond({error:"unsupported_auth_operation"},404);
 if(endpoint==="signup"){
   const email=String(body.email??"").trim().toLowerCase();
   const password=String(body.password??"");
   if(!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)||password.length<8) return respond({error:"invalid_input"},400);
   const upstream=await fetch(root+"/auth/v1/signup",{method:"POST",headers:{"apikey":anon,"content-type":"application/json"},body:JSON.stringify({email,password,data:{first_name:String(body.firstName??"").slice(0,100),last_name:String(body.lastName??"").slice(0,100)}})});
   if(!upstream.ok) return respond({error:"registration_failed"},upstream.status===429?429:400);
   const data=await upstream.json();
   if(!data.access_token) return respond({status:"email_confirmation_required"},202);
   return respond({accessToken:data.access_token,refreshToken:data.refresh_token});
 }
 if(endpoint.startsWith("token")){
   const identifier=String(body.identifier??"").trim();
   if(!identifier.includes("@")) return respond({error:"email_required_for_password_login"},400);
   const upstream=await fetch(root+"/auth/v1/"+endpoint,{method:"POST",headers:{"apikey":anon,"content-type":"application/json"},body:JSON.stringify({email:identifier,password:String(body.password??"")})});
   if(!upstream.ok) return respond({error:"invalid_credentials"},401);
   const data=await upstream.json();
   return respond({accessToken:data.access_token,refreshToken:data.refresh_token});
 }
 const token=String(body.accessToken??"");
 if(!token) return respond({error:"missing_token"},400);
 const upstream=await fetch(root+"/auth/v1/logout",{method:"POST",headers:{"apikey":anon,"authorization":"Bearer "+token}});
 return respond({success:upstream.ok},upstream.ok?200:401);
});