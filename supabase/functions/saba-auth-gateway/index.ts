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
 if(path.endsWith("/v1/profile/mobile")) {
   const token=String(body.accessToken??"");
   if(!token) return respond({error:"missing_token"},400);
   const authHeaders={"apikey":anon,"authorization":"Bearer "+token};
   const who=await fetch(root+"/auth/v1/user",{headers:authHeaders});
   if(!who.ok) return respond({error:"unauthorized"},401);
   const current=await who.json();
   const metadata=current.user_metadata&&typeof current.user_metadata==="object"?current.user_metadata:{};
   if(body.mobile===undefined) return respond({mobile:String(metadata.mobile||metadata.phone||"")});
   const mobile=String(body.mobile??"").replace(/[٠-٩]/g,d=>String("٠١٢٣٤٥٦٧٨٩".indexOf(d))).replace(/[۰-۹]/g,d=>String("۰۱۲۳۴۵۶۷۸۹".indexOf(d)));
   if(!/^09\d{9}$/.test(mobile)) return respond({error:"invalid_mobile"},400);
   const updated=await fetch(root+"/auth/v1/user",{method:"PUT",headers:{...authHeaders,"content-type":"application/json"},body:JSON.stringify({data:{...metadata,mobile}})});
   if(!updated.ok) return respond({error:"profile_update_failed"},502);
   return respond({mobile});
 }
 const endpoint=path.endsWith("/v1/auth/registration/start")?"signup":path.endsWith("/v1/auth/login/password")?"token?grant_type=password":path.endsWith("/v1/auth/logout")?"logout":null;
 if(!endpoint) return respond({error:"unsupported_auth_operation"},404);
 if(endpoint==="signup"){
   if(body.acceptedTerms!==true||body.legalVersion!=="2026-11-25.1") return respond({error:"legal_consent_required"},422);
   const email=String(body.email??"").trim().toLowerCase();
   const password=String(body.password??"");
   if(!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)||password.length<8) return respond({error:"invalid_input"},400);
   const upstream=await fetch(root+"/auth/v1/signup",{method:"POST",headers:{"apikey":anon,"content-type":"application/json"},body:JSON.stringify({email,password,data:{first_name:String(body.firstName??"").slice(0,100),last_name:String(body.lastName??"").slice(0,100),sura_legal_accepted:true,sura_legal_version:"2026-11-25.1"}})});
   if(!upstream.ok){
     const err=await upstream.json().catch(()=>({}));
     const code=err.error_code??err.code;
     if(upstream.status===429)return respond({error:"rate_limited"},429);
     if(code==="user_already_exists"||code==="email_exists")return respond({error:"user_already_exists"},409);
     if(code==="weak_password")return respond({error:"weak_password"},400);
     if(upstream.status>=500)return respond({error:"service_unavailable"},503);
     return respond({error:"registration_failed"},400);
   }
   const data=await upstream.json();
   if(!data.access_token) return respond({status:"email_confirmation_required"},202);
   return respond({accessToken:data.access_token,refreshToken:data.refresh_token});
 }
 if(endpoint.startsWith("token")){
   const identifier=String(body.identifier??"").trim();
   if(!identifier.includes("@")) return respond({error:"email_required_for_password_login"},400);
   const upstream=await fetch(root+"/auth/v1/"+endpoint,{method:"POST",headers:{"apikey":anon,"content-type":"application/json"},body:JSON.stringify({email:identifier,password:String(body.password??"")})});
   if(!upstream.ok){
     const err=await upstream.json().catch(()=>({}));
     if(upstream.status===429)return respond({error:"rate_limited"},429);
     if(upstream.status>=500)return respond({error:"service_unavailable"},503);
     if((err.error_code??err.code)==="email_not_confirmed")return respond({error:"email_not_confirmed"},401);
     return respond({error:"invalid_credentials"},401);
   }
   const data=await upstream.json();
   return respond({accessToken:data.access_token,refreshToken:data.refresh_token});
 }
 const token=String(body.accessToken??"");
 if(!token) return respond({error:"missing_token"},400);
 const upstream=await fetch(root+"/auth/v1/logout",{method:"POST",headers:{"apikey":anon,"authorization":"Bearer "+token}});
 return respond({success:upstream.ok},upstream.ok?200:401);
});