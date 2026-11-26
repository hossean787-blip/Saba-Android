function need(x:unknown):asserts x{if(!x)throw new Error("invalid_attestation");}
export function b64(s:unknown,max:number):Uint8Array{
 need(typeof s==="string"&&s.length>0&&s.length<=Math.ceil(max/3)*4&&s.length%4===0&&/^[A-Za-z0-9+/]+={0,2}$/.test(s));
 const b=Uint8Array.from(atob(s),c=>c.charCodeAt(0));need(b.length<=max&&btoa(String.fromCharCode(...b))===s);return b;
}
