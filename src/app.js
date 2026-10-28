const KEY='saba_offline_v1';
let storageBlocked=false;
let state=load();
let activePage='dashboard';
// Default schema, shared by startup, reset and backup validation.
function fresh() {
  return {
    companies: [],
    customers: [],
    invoices: [],
    products: [],
    purchased: 0,
    master: 0,
    allocations: {},
    used: {},
    fontScale: 1,
    printPrefs: {}
  };
}
// Validate persisted and imported data before it reaches the UI.
function validateState(input) {
  const fail = () => {
    throw new Error('ساختار یا مقادیر پشتیبان معتبر نیست.');
  };
  const record = value => value !== null && typeof value === 'object' && !Array.isArray(value);
  const text = value => typeof value === 'string';
  const integer = value => Number.isSafeInteger(value) && value >= 0;
  const id = value => text(value) && /^[a-zA-Z0-9_-]{1,100}$/.test(value);
  if (!record(input)) fail();
  const x = {
    ...fresh(), ...input
  };
  for (const key of ['companies', 'customers', 'invoices', 'products']) {
    if (!Array.isArray(x[key])) fail();
    const seen = new Set();
    for (const row of x[key]) {
      if (!record(row) || !id(row.id) || seen.has(row.id)) fail();
      seen.add(row.id);
    }
  }
  const companies = new Set(x.companies.map(row => row.id));
  const nationals = new Set();
  for (const row of x.companies) {
    if (!text(row.name) || !row.name.trim() || !text(row.national) || !row.national.trim() || nationals.has(row.national)) fail();
    nationals.add(row.national);
  }
  for (const key of ['customers', 'products']) {
    for (const row of x[key]) {
      if (!companies.has(row.companyId) || !text(row.name) || !row.name.trim()) fail();
      for (const field of key === 'customers' ? ['national', 'created'] : ['code', 'kind', 'unit']) {
        if (row[field] !== undefined && !text(row[field])) fail();
      }
    }
  }
  const productCodes = new Set();
  for (const row of x.products) {
    if (row.code) {
      const key = JSON.stringify([row.companyId, row.code]);
      if (productCodes.has(key)) fail();
      productCodes.add(key);
    }
  }
  const numbers = new Set();
  for (const row of x.invoices) {
    if (!companies.has(row.companyId) || !x.customers.some(c => c.id === row.customerId && c.companyId === row.companyId)) fail();
    for (const field of ['number', 'date', 'desc', 'customerName']) if (!text(row[field]) || !row[field].trim()) fail();
    if (!['پیش‌نویس', 'نهایی‌شده'].includes(row.status)) fail();
    if (!integer(row.q) || row.q < 1 || !integer(row.p) || !Number.isFinite(row.t) || row.t < 0 || row.t > 100) fail();
    for (const field of ['net', 'tax', 'total', 'created']) if (!integer(row[field])) fail();
    if (row.net !== row.q * row.p || row.tax !== Math.round(row.net * row.t / 100) || row.total !== row.net + row.tax) fail();
    const key = JSON.stringify([row.companyId, row.number]);
    if (numbers.has(key)) fail();
    numbers.add(key);
  }
  if (!integer(x.purchased) || !integer(x.master)) fail();
  for (const key of ['allocations', 'used']) {
    if (!record(x[key])) fail();
    for (const [companyId, count] of Object.entries(x[key])) if (!companies.has(companyId) || !integer(count)) fail();
  }
  let allocated = 0;
  for (const companyId of companies) {
    const allocation = x.allocations[companyId] || 0;
    const used = x.used[companyId] || 0;
    if (used > allocation || used !== x.invoices.filter(row => row.companyId === companyId && row.status === 'نهایی‌شده').length) fail();
    allocated += allocation;
    if (!integer(allocated)) fail();
  }
  if (!integer(x.master + allocated) || x.purchased !== x.master + allocated) fail();
  if (![0.85, 1, 1.15, 1.3].includes(x.fontScale) || !record(x.printPrefs)) fail();
  for (const key of ['title', 'footer']) if (x.printPrefs[key] !== undefined && !text(x.printPrefs[key])) fail();
  for (const key of ['seller', 'buyer', 'signature', 'stamp']) if (x.printPrefs[key] !== undefined && typeof x.printPrefs[key] !== 'boolean') fail();
  // Discard unknown top-level fields; retain compatible v19 records.
  return Object.fromEntries(Object.keys(fresh()).map(key => [key, x[key]]));
}
function load() {
  try {
    const raw = localStorage.getItem(KEY);
    storageBlocked = false;
    return raw === null ? fresh() : validateState(JSON.parse(raw));
  }
  catch {
    storageBlocked = true;
    // Keep the original stored bytes intact for recovery; do not overwrite on startup.
    queueMicrotask(() => notify('خواندن داده‌های محلی ممکن نشد. پیش از ثبت اطلاعات، پشتیبان معتبر را بازیابی کنید.'));
    return fresh();
  }
}
function save() {
  try {
    if (storageBlocked) throw new Error("ابتدا داده‌های محلی را بازیابی یا بازنشانی کنید.");
    localStorage.setItem(KEY, JSON.stringify(validateState(state)));
  }
  catch (error) {
    state = load();
    render();
    notify('ذخیره انجام نشد؛ فضای مرورگر و اعتبار داده‌ها را بررسی کنید.');
    throw error;
  }
  render();
}
function esc(s){
  return String(s??'').replace(/[&<>"']/g,c=>({
    '&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'
  }
  [c]))
}
function fa(n){
  return Number(n||0).toLocaleString('fa-IR')
}
function money(n){
  return fa(n)+' ریال'
}
function company(){
  return state.companies.find(c=>c.id===document.getElementById('activeCompany').value)||state.companies[0]||null
}
function cid(){
  return company()?.id||''
}
function notify(t){
  let n=document.getElementById('notice');
  n.textContent=t;
  n.classList.add('show');
  setTimeout(()=>n.classList.remove('show'),3500)
}
function go(page){
  activePage=page;
  document.querySelectorAll('.panel').forEach(p=>p.classList.toggle('active',p.id===page));
  document.querySelectorAll('.nav button').forEach(b=>b.classList.toggle('active',b.dataset.page===page));
  document.getElementById('pageTitle').textContent=({
    dashboard:'داشبورد',companies:'مدیریت شرکت‌ها',customers:'مدیریت مشتریان',invoices:'فاکتورها و صدور',quota:'سهمیه و مجوز',settings:'تنظیمات',products:'کالا و خدمات',tracking:'پیگیری ارسال',printing:'تنظیمات چاپ',integration:'اتصال و امضا',owner:'حساب مالک'
  }
  )[page];
  render()
}
document.querySelectorAll('.nav button').forEach(b=>b.addEventListener('click',()=>go(b.dataset.page)));
document.getElementById('activeCompany').addEventListener('change',render);
function initSelects(){
  let a=document.getElementById('activeCompany'),old=a.value;
  a.innerHTML=state.companies.map(c=>`<option value="${esc(c.id)}">${esc(c.name)}</option>`).join('');
  if(state.companies.some(c=>c.id===old))a.value=old;
  let customers=state.customers.filter(c=>c.companyId===cid());
  const customerSelect=document.getElementById('invoiceCustomer'),previousCustomer=customerSelect.value;
  document.getElementById('invoiceCustomer').innerHTML=customers.map(c=>`<option value="${esc(c.id)}">${esc(c.name)}</option>`).join('');
  if(customers.some(c=>c.id===previousCustomer))customerSelect.value=previousCustomer;
  const transferSelect=document.getElementById('transferCompany'),previousTransfer=transferSelect.value;
  document.getElementById('transferCompany').innerHTML=state.companies.map(c=>`<option value="${esc(c.id)}">${esc(c.name)}</option>`).join('');
  if(state.companies.some(c=>c.id===previousTransfer))transferSelect.value=previousTransfer
}
function render(){
  initSelects();
  let c=company(),cs=state.customers.filter(x=>x.companyId===cid()),ins=state.invoices.filter(x=>x.companyId===cid());
  let alloc=state.allocations[cid()]||0,used=state.used[cid()]||0,remaining=Math.max(0,alloc-used);
  document.getElementById('mCompanies').textContent=fa(state.companies.length);
  document.getElementById('mCustomers').textContent=fa(cs.length);
  document.getElementById('mInvoices').textContent=fa(ins.length);
  document.getElementById('mQuota').textContent=fa(remaining);
  document.getElementById('quotaUsed').textContent=fa(used)+' از '+fa(alloc);
  document.getElementById('quotaBar').style.width=(alloc?Math.min(100,used/alloc*100):0)+'%';
  document.getElementById('recent').innerHTML=ins.slice().reverse().slice(0,5).map(i=>`<div class="activity-row"><div><strong>${esc(i.number)}</strong><div class="footer-note">${esc(i.customerName)} · ${esc(i.date)}</div></div><div style="text-align:left"><strong>${money(i.total)}</strong><div><span class="status ${i.status==='نهایی‌شده'?'ok':''}">${esc(i.status)}</span></div></div></div>`).join('')||'<div class="empty">هنوز فاکتوری ثبت نشده است.</div>';
  document.getElementById('companiesRows').innerHTML=state.companies.map(x=>`<tr><td>${esc(x.name)}</td><td>${esc(x.national)}</td><td>${fa(state.allocations[x.id]||0)}</td><td><button class="secondary" onclick="selectCompany('${esc(x.id)}')">انتخاب</button></td></tr>`).join('')||'<tr><td colspan="4" class="empty">شرکتی ثبت نشده است.</td></tr>';
  document.getElementById('customersRows').innerHTML=cs.map(x=>`<tr><td>${esc(x.name)}</td><td>${esc(x.national||'—')}</td><td>${esc(x.created)}</td></tr>`).join('')||'<tr><td colspan="3" class="empty">مشتری ثبت نشده است.</td></tr>';
  document.getElementById('invoicesRows').innerHTML=ins.slice().reverse().map(i=>`<tr><td>${esc(i.number)}</td><td>${esc(i.date)}</td><td>${esc(i.customerName)}</td><td>${money(i.total)}</td><td><span class="status ${i.status==='نهایی‌شده'?'ok':''}">${esc(i.status)}</span></td><td>${i.status==='پیش‌نویس'?`<button onclick="finalizeInvoice('${esc(i.id)}')" ${remaining<1?'disabled':''}>نهایی‌سازی محلی</button>`:'<span class="tag">ثبت شد</span>'}</td></tr>`).join('')||'<tr><td colspan="6" class="empty">فاکتوری ثبت نشده است.</td></tr>';
  document.getElementById('qPurchased').textContent=fa(state.purchased);
  document.getElementById('qMaster').textContent=fa(state.master);
  document.getElementById('qAllocated').textContent=fa(Object.values(state.allocations).reduce((a,b)=>a+b,0));
  document.getElementById('qUsed').textContent=fa(Object.values(state.used).reduce((a,b)=>a+b,0));
  document.getElementById('quotaRows').innerHTML=state.companies.map(x=>{
    let a=state.allocations[x.id]||0,u=state.used[x.id]||0;
    return `<tr><td>${esc(x.name)}</td><td>${fa(a)}</td><td>${fa(u)}</td><td>${fa(Math.max(0,a-u))}</td></tr>`
  }
  ).join('')||'<tr><td colspan="4" class="empty">شرکتی ثبت نشده است.</td></tr>';
  document.getElementById('fontScale').value=String(state.fontScale||1);
  document.documentElement.style.fontSize=(15*(state.fontScale||1))+'px';
  renderExtensions()
}
function selectCompany(id){
  document.getElementById('activeCompany').value=id;
  render();
  notify('شرکت فعال تغییر کرد.')
}
document.getElementById('companyForm').addEventListener('submit',e=>{
  e.preventDefault();
  let name=document.getElementById('companyName').value.trim(),national=document.getElementById('companyNational').value.trim().replace(/[۰-۹]/g,d=>'۰۱۲۳۴۵۶۷۸۹'.indexOf(d));
  if(!name||!national)return notify('نام و شناسه ملی الزامی است.');
  if(state.companies.some(c=>c.national===national))return notify('این شناسه ملی قبلاً ثبت شده است.');
  let id=crypto.randomUUID();
  state.companies.push({
    id,name,national
  });
  state.allocations[id]=0;
  state.used[id]=0;
  document.getElementById('companyForm').reset();
  save();
  document.getElementById('activeCompany').value=id;
  render();
  notify('شرکت ثبت شد.')
});
document.getElementById('customerForm').addEventListener('submit',e=>{
  e.preventDefault();
  if(!company())return notify('ابتدا شرکت ثبت کنید.');
  let name=document.getElementById('customerName').value.trim();
  if(!name)return notify('نام مشتری الزامی است.');
  state.customers.push({
    id:crypto.randomUUID(),companyId:cid(),name,national:document.getElementById('customerNational').value.trim(),created:new Date().toLocaleDateString('fa-IR')
  });
  e.target.reset();
  save();
  notify('مشتری ذخیره شد.')
});
function previewInvoice(){
  let q=Number(document.getElementById('itemQuantity').value),p=Number(document.getElementById('itemPrice').value),t=Number(document.getElementById('itemTax').value);
  if(!Number.isInteger(q)||q<1||!Number.isFinite(p)||p<0||!Number.isFinite(t)||t<0||t>100)return notify('تعداد، قیمت یا مالیات معتبر نیست.');
  let net=q*p,tax=Math.round(net*t/100);
  document.getElementById('invoicePreview').textContent=money(net+tax)
}
['itemQuantity','itemPrice','itemTax'].forEach(id=>document.getElementById(id).addEventListener('input',previewInvoice));
document.getElementById('invoiceForm').addEventListener('submit',e=>{
  e.preventDefault();
  if(!company())return notify('ابتدا شرکت ثبت کنید.');
  let customer=state.customers.find(c=>c.id===document.getElementById('invoiceCustomer').value&&c.companyId===cid());
  if(!customer)return notify('برای این شرکت مشتری ثبت کنید.');
  let number=document.getElementById('invoiceNumber').value.trim(),date=document.getElementById('invoiceDate').value.trim(),desc=document.getElementById('itemDescription').value.trim(),q=Number(document.getElementById('itemQuantity').value),p=Number(document.getElementById('itemPrice').value),t=Number(document.getElementById('itemTax').value);
  if(state.invoices.some(i=>i.companyId===cid()&&i.number===number))return notify('شماره فاکتور تکراری است.');
  if(!number||!date||!desc||!Number.isInteger(q)||q<1||!Number.isSafeInteger(p)||p<0||!Number.isFinite(t)||t<0||t>100)return notify('اطلاعات فاکتور معتبر نیست.');
  let net=q*p;
  if(!Number.isSafeInteger(net)||net>Number.MAX_SAFE_INTEGER)return notify('مبلغ از محدوده مجاز خارج است.');
  let tax=Math.round(net*t/100),total=net+tax;
  if(!Number.isSafeInteger(total))return notify('مبلغ نهایی معتبر نیست.');
  state.invoices.push({
    id:crypto.randomUUID(),companyId:cid(),number,date,customerId:customer.id,customerName:customer.name,desc,q,p,t,net,tax,total,status:'پیش‌نویس',created:Date.now()
  });
  e.target.reset();
  document.getElementById('itemQuantity').value='1';
  document.getElementById('itemTax').value='10';
  document.getElementById('invoicePreview').textContent='۰ ریال';
  save();
  notify('پیش‌نویس فاکتور ذخیره شد.')
});
function finalizeInvoice(id){
  let inv=state.invoices.find(i=>i.id===id&&i.companyId===cid());
  if(!inv||inv.status!=='پیش‌نویس')return notify('این فاکتور قابل نهایی‌سازی نیست.');
  let remaining=(state.allocations[cid()]||0)-(state.used[cid()]||0);
  if(remaining<1)return notify('سهمیه کافی نیست؛ ابتدا سهمیه تخصیص دهید.');
  inv.status='نهایی‌شده';
  inv.finalizedAt=Date.now();
  state.used[cid()]=(state.used[cid()]||0)+1;
  save();
  notify('فاکتور نهایی و یک سهمیه مصرف شد. این ثبت محلی است، ارسال دولتی انجام نشد.')
}
document.getElementById('purchaseForm').addEventListener('submit',e=>{
  e.preventDefault();
  let n=Number(document.getElementById('purchaseCount').value);
  if(!Number.isSafeInteger(n)||n<1||!Number.isSafeInteger(state.purchased+n)||!Number.isSafeInteger(state.master+n))return notify('تعداد سهمیه از محدوده مجاز خارج است.');
  state.purchased+=n;
  state.master+=n;
  save();
  notify('سهمیه آزمایشی به کیف خریدار اصلی افزوده شد.')
});
document.getElementById('transferForm').addEventListener('submit',e=>{
  e.preventDefault();
  let id=document.getElementById('transferCompany').value,n=Number(document.getElementById('transferCount').value);
  if(!state.companies.some(c=>c.id===id))return notify('ابتدا شرکت مقصد ثبت کنید.');
  if(!Number.isSafeInteger(n)||n<1)return notify('تعداد باید عدد مثبت باشد.');
  if(n>state.master)return notify('سهمیه خریدار اصلی کافی نیست.');
  state.master-=n;
  state.allocations[id]=(state.allocations[id]||0)+n;
  save();
  notify('سهمیه به شرکت انتخاب‌شده تخصیص یافت.')
});
document.getElementById('saveSettings').addEventListener('click',()=>{
  state.fontScale=Number(document.getElementById('fontScale').value)||1;
  save();
  notify('تنظیمات ذخیره شد.')
});
function exportData(){
  let blob=new Blob([JSON.stringify(state,null,2)],{
    type:'application/json'
  }
  ),a=document.createElement('a');
  a.href=URL.createObjectURL(blob);
  a.download='saba-backup.json';
  a.click();
  URL.revokeObjectURL(a.href);
  notify('فایل پشتیبان آماده شد.')
}
document.getElementById('importFile').addEventListener('change',async e=>{
  let f=e.target.files[0];
  if(!f)return;
  try{
    let x=JSON.parse(await f.text());
    const validated=validateState(x);
    if(!confirm('بازیابی، داده‌های فعلی را جایگزین می‌کند. ادامه می‌دهید؟'))return;
    storageBlocked=false;
    state=validated;
    save();
    notify('پشتیبان بازیابی شد.')
  }
  catch{
    notify('فایل پشتیبان معتبر نیست.')
  }
  e.target.value=''
});
function resetData(){
  if(confirm('همه شرکت‌ها، مشتریان، فاکتورها و سهمیه‌های محلی پاک شوند؟')){
    storageBlocked=false;
    state=fresh();
    save();
    go('dashboard');
    notify('داده‌های محلی پاک شد.')
  }
}
// Product catalog, tracking, and print preferences
// Extension v19: company-scoped product catalog, local status view, print preferences.
function extendedState(){
  if(!Array.isArray(state.products))state.products=[];
  if(!state.printPrefs||typeof state.printPrefs!=='object')state.printPrefs={
  };
}
function renderExtensions(){
  extendedState();
  const current=cid();
  const products=state.products.filter(x=>x.companyId===current);
  document.getElementById('productsRows').innerHTML=products.map(x=>`<tr><td>${esc(x.name)}</td><td>${esc(x.code||'—')}</td><td>${esc(x.kind)}</td><td>${esc(x.unit||'—')}</td></tr>`).join('')||'<tr><td colspan="4" class="empty">کالا یا خدمتی ثبت نشده است.</td></tr>';
  const search=document.getElementById('trackingSearch').value.trim();
  document.getElementById('trackingRows').innerHTML=state.invoices.filter(x=>x.companyId===current&&(!search||String(x.number).includes(search))).slice().reverse().map(x=>`<tr><td>${esc(x.number)}</td><td>${esc(x.subject||'اصلی')}</td><td>${esc(x.pattern||'الگوی اول')}</td><td>${esc(x.status)}</td><td><span class="status">ارسال نشده</span></td></tr>`).join('')||'<tr><td colspan="5" class="empty">موردی یافت نشد.</td></tr>';
  const p=state.printPrefs;
  document.getElementById('printTitle').value=p.title||'';
  document.getElementById('printFooter').value=p.footer||'';
  for(const k of ['Seller','Buyer','Signature','Stamp'])document.getElementById('print'+k).checked=!!p[k.toLowerCase()];
}
document.getElementById('productForm').addEventListener('submit',e=>{
  e.preventDefault();
  if(!company())return notify('ابتدا شرکت ثبت کنید.');
  extendedState();
  const name=document.getElementById('productName').value.trim(),code=document.getElementById('productCode').value.trim();
  if(!name)return notify('نام کالا یا خدمت الزامی است.');
  if(code&&state.products.some(x=>x.companyId===cid()&&x.code===code))return notify('شناسه کالا در این شرکت تکراری است.');
  state.products.push({
    id:crypto.randomUUID(),companyId:cid(),name,code,kind:document.getElementById('productKind').value,unit:document.getElementById('productUnit').value.trim()
  });
  e.target.reset();
  save();
  notify('کالا یا خدمت ذخیره شد.')
});
document.getElementById('trackingSearch').addEventListener('input',renderExtensions);
document.getElementById('savePrint').addEventListener('click',()=>{
  state.printPrefs={
    title:document.getElementById('printTitle').value.trim(),footer:document.getElementById('printFooter').value.trim(),seller:document.getElementById('printSeller').checked,buyer:document.getElementById('printBuyer').checked,signature:document.getElementById('printSignature').checked,stamp:document.getElementById('printStamp').checked
  };
  save();
  notify('تنظیمات چاپ ذخیره شد.')
});
document.getElementById('printPreview').addEventListener('click',()=>{
  const inv=state.invoices.filter(x=>x.companyId===cid()).slice(-1)[0];
  if(!inv)return notify('ابتدا یک فاکتور ثبت کنید.');
  const p=state.printPrefs||{
  };
  const w=window.open('','_blank');
  if(!w)return notify('مرورگر پنجره چاپ را مسدود کرد.');
  const safe=x=>esc(String(x??''));
  w.document.write('<!doctype html><html lang="fa" dir="rtl"><meta charset="utf-8"><title>چاپ صورتحساب</title><style>body{font:16px Tahoma;padding:35px;line-height:2}table{width:100%;border-collapse:collapse}td,th{border:1px solid #bbb;padding:10px}</style><h2>'+safe(p.title||'صورتحساب فروش')+'</h2><table><tr><th>شماره</th><td>'+safe(inv.number)+'</td></tr><tr><th>تاریخ</th><td>'+safe(inv.date)+'</td></tr>'+(p.seller?'<tr><th>فروشنده</th><td>'+safe(company()?.name)+'</td></tr>':'')+(p.buyer?'<tr><th>خریدار</th><td>'+safe(inv.customerName)+'</td></tr>':'')+'<tr><th>شرح</th><td>'+safe(inv.desc)+'</td></tr><tr><th>مبلغ کل (ریال)</th><td>'+safe(inv.total)+'</td></tr></table><p>'+safe(p.footer)+'</p>'+(p.signature?'<p>محل امضا: ....................</p>':'')+(p.stamp?'<p>محل مهر: ....................</p>':'')+'<p>نمونه محلی ـ ارسال مالیاتی انجام نشده است</p></html>');
  w.document.close();
  w.focus();
  w.print()
});
render();
