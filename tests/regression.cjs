const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const path=require('node:path');
(async()=>{
 const server=require('node:http').createServer((req,res)=>{res.setHeader('Content-Type','text/html; charset=utf-8');res.end(require('node:fs').readFileSync(path.resolve(__dirname,'../Saba_Offline_App.html')))});await new Promise(r=>server.listen(0,'127.0.0.1',r));
 const browser=await chromium.launch({executablePath:process.env.SABA_CHROMIUM||'/usr/bin/chromium',headless:true,args:['--no-sandbox']});
 const page=await browser.newPage();
 const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.goto('http://127.0.0.1:'+server.address().port);
 const results=[];
 async function test(name,fn){try{await fn();results.push({name,pass:true});}catch(e){results.push({name,pass:false,error:e.message});}}
 await test('preserves selected customer on refresh',async()=>{
  assert.equal(await page.evaluate(()=>{state=fresh();state.companies=[{id:'a',name:'A',national:'1'}];state.customers=[{id:'u',companyId:'a',name:'U',created:''},{id:'v',companyId:'a',name:'V',created:''}];render();document.getElementById('invoiceCustomer').value='v';render();return document.getElementById('invoiceCustomer').value;}),'v');
 });
 await test('rejects malformed backup without replacing data',async()=>{
  await page.setInputFiles('#importFile',{name:'bad.json',mimeType:'application/json',buffer:Buffer.from(JSON.stringify({companies:[null],customers:[],invoices:[],master:0}))});
  await page.waitForTimeout(100);
  assert.equal(await page.evaluate(()=>state.companies[0]?.id),'a');
 });
 await test('rejects inconsistent financial backup',async()=>{
  await page.evaluate(()=>{state=fresh();state.companies=[{id:'a',name:'A',national:'1'}];render()});
  await page.setInputFiles('#importFile',{name:'bad.json',mimeType:'application/json',buffer:Buffer.from(JSON.stringify({companies:[],customers:[],invoices:[],master:-1}))});
  await page.waitForTimeout(100);assert.equal(await page.evaluate(()=>state.master),0);
 });
 await test('quota purchase rejects integer overflow',async()=>{
  await page.evaluate(()=>{state=fresh();state.purchased=Number.MAX_SAFE_INTEGER;state.master=Number.MAX_SAFE_INTEGER;document.getElementById('purchaseCount').value='1';document.getElementById('purchaseForm').dispatchEvent(new Event('submit',{cancelable:true}));});
  assert.equal(await page.evaluate(()=>state.master),Number.MAX_SAFE_INTEGER);
 });
 await test('valid invoice workflow, isolation and idempotent finalization',async()=>{
  const result=await page.evaluate(()=>{
   state=fresh();state.companies=[{id:'a',name:'A',national:'1'},{id:'b',name:'B',national:'2'}];state.customers=[{id:'u',companyId:'a',name:'U',created:''}];render();
   function submit(id,values){for(const [key,value] of Object.entries(values))document.getElementById(key).value=value;document.getElementById(id).dispatchEvent(new Event('submit',{cancelable:true}));}
   submit('purchaseForm',{purchaseCount:'10'});submit('transferForm',{transferCompany:'a',transferCount:'5'});
   submit('invoiceForm',{invoiceNumber:'INV-1',invoiceDate:'1405/08/05',invoiceCustomer:'u',itemDescription:'Service',itemQuantity:'2',itemPrice:'1000',itemTax:'10'});
   const inv=state.invoices[0];finalizeInvoice(inv.id);finalizeInvoice(inv.id);
   const result={total:inv.total,used:state.used.a,master:state.master};
   selectCompany('b');result.isolated=!document.getElementById('invoicesRows').textContent.includes('INV-1');
   result.valid=!!validateState(state);save();return result;
  });assert.deepEqual(result,{total:2200,used:1,master:5,isolated:true,valid:true});
 });
 await test('valid backup can be cancelled or restored',async()=>{
  const backup=await page.evaluate(()=>JSON.stringify(state));
  let accept=false;const dialog=d=>accept?d.accept():d.dismiss();page.on('dialog',dialog);
  await page.evaluate(()=>{state=fresh();save()});
  const file={name:'backup.json',mimeType:'application/json',buffer:Buffer.from(backup)};
  await page.setInputFiles('#importFile',file);await page.waitForTimeout(100);assert.equal(await page.evaluate(()=>state.companies.length),0);
  accept=true;await page.setInputFiles('#importFile',file);await page.waitForTimeout(100);assert.equal(await page.evaluate(()=>state.invoices[0].total),2200);page.off('dialog',dialog);
 });
 await test('rejects injected identifiers, foreign customers, invalid statuses and totals',async()=>{
  assert.equal(await page.evaluate(()=>{
   const original=JSON.stringify(state);const cases=[x=>x.companies[0].id="a');alert(1);//",x=>x.invoices[0].customerId='missing',x=>x.invoices[0].status='<img src=x onerror=alert(1)>',x=>x.invoices[0].total=1];
   return cases.every(change=>{let x=JSON.parse(original);change(x);try{validateState(x);return false}catch{return true}});
  }),true);
 });
 await test('storage failure rolls back data without success message',async()=>{
  assert.equal(await page.evaluate(()=>{
   const before=JSON.stringify(state),original=Storage.prototype.setItem;
   Storage.prototype.setItem=function(){throw new Error('full')};state.master+=1;state.purchased+=1;
   try{save()}catch{}finally{Storage.prototype.setItem=original}
   return JSON.stringify(state)===before&&document.getElementById('notice').textContent.includes('ذخیره انجام نشد');
  }),true);
 });
 await test('persists invoice after browser reload',async()=>{
  await page.reload();assert.equal(await page.evaluate(()=>state.invoices[0].total),2200);
 });
 await test('preserves transfer target and isolates product catalog',async()=>{
  assert.equal(await page.evaluate(()=>{
   document.getElementById('transferCompany').value='b';render();const selected=document.getElementById('transferCompany').value==='b';
   selectCompany('a');document.getElementById('productName').value='Product A';document.getElementById('productForm').dispatchEvent(new Event('submit',{cancelable:true}));selectCompany('b');return selected&&!document.getElementById('productsRows').textContent.includes('Product A');
  }),true);
 });
 await test('all navigation panels open without page errors',async()=>{
  for(const name of ['dashboard','companies','customers','invoices','quota','settings','products','tracking','printing','integration','owner']){
   await page.evaluate(name=>go(name),name);assert.equal(await page.locator('.panel.active').getAttribute('id'),name);
  }
 });
 await test('prints a zero-value invoice without losing its total',async()=>{
  await page.evaluate(()=>{selectCompany('a');const i=state.invoices[0];i.p=0;i.net=0;i.tax=0;i.total=0;save();go('printing')});
  const popupEvent=page.waitForEvent('popup');await page.click('#printPreview');const popup=await popupEvent;
  await popup.waitForLoadState();assert.equal(await popup.locator('tr').last().locator('td').textContent(),'0');await popup.close();
 });
 await test('corrupt storage is preserved until explicit restore or reset',async()=>{
  await page.evaluate(()=>localStorage.setItem(KEY,'not-json'));await page.reload();
  const result=await page.evaluate(()=>{state.companies.push({id:'new',name:'New',national:'1'});try{save()}catch{}return localStorage.getItem(KEY)});
  assert.equal(result,'not-json');
 });
 const report={results,pageErrors:errors};require('node:fs').writeFileSync(path.resolve(__dirname,'results.json'),JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify(report,null,2));await browser.close();server.close();if(results.some(x=>!x.pass)||errors.length)process.exitCode=1;
})().catch(e=>{console.error(e);process.exitCode=1});
