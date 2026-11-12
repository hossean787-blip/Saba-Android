/* SURA Manager v1: account directory, role approvals, audit, health, paged orders. */
const MANAGER_ENDPOINT = 'sura-manager-api';
let directoryPage=1, auditPage=1, auditKind='credit', orderPage=1, orderState='';
let orderOwner='',orderFrom='',orderTo='';
let lastAuditRows=[],lastAuditKind='';
let directoryRows=[];
const MANAGER_STATE_LABEL={created:'ثبت اولیه',requested:'منتظر پرداخت',verified:'پرداخت تأییدشده',failed:'ناموفق'};
const MANAGER_ROLE_LABEL={admin:'مدیر ارشد',editor:'ویرایشگر',viewer:'ناظر'};

async function managerFetch(endpoint,body={}) {
 if(!USER_JWT)throw new Error('ابتدا وارد حساب مدیر شوید.');
 await ensureFreshToken();
 const res=await fetch(`${SB_URL}/functions/v1/${MANAGER_ENDPOINT}/${endpoint}`,{
  method:'POST',headers:{apikey:SB_ANON,authorization:`Bearer ${USER_JWT}`,'content-type':'application/json'},body:JSON.stringify(body)
 });
 if(res.status===401){logout();throw new Error('نشست منقضی شده است؛ دوباره وارد شوید.');}
 const data=await res.json().catch(()=>({error:'invalid_server_reply'}));
 if(!res.ok||data.error)throw new Error(data.error||`HTTP ${res.status}`);
 return data;
}
function managerRoles(){
 document.querySelectorAll('[data-min-role]').forEach(el=>{
  const role=el.getAttribute('data-min-role');
  el.style.display=((role==='admin'&&ADMIN_ROLE!=='admin')||(role==='staff'&&ADMIN_ROLE==='viewer'))?'none':'';
 });
}
function safeJSString(value){return html(JSON.stringify(String(value)));}
function managerUserLink(id){return `<button class="action-btn" onclick="openManagerUser('${html(id)}')">جزئیات حساب</button>`;}
function managerPill(label,variant='blue'){return `<span class="badge ${variant}">${html(label)}</span>`;}
function managerGuid(id){return `<span class="technical-id" dir="ltr" title="${html(id)}">${html(String(id).slice(0,12))}…</span>`;}
function safeTime(value){return value?fmtDate(value):'—';}

async function renderDirectory(c){
 if(ADMIN_ROLE==='viewer'){c.innerHTML='<div class="config-notice">فهرست کاربران فقط برای مدیر و ویرایشگر قابل مشاهده است.</div>';return;}
 const payload=await managerFetch('users',{page:directoryPage,perPage:30});
 directoryRows=payload.users||[];
 c.innerHTML=`<div class="section-hero"><span class="eyebrow">ACCOUNT DIRECTORY</span><h2>حساب‌های ثبت‌نام‌شده</h2>
 <p>فهرست واقعی کاربران از سرویس احراز هویت. حساب‌هایی که هنوز کیف‌پول ندارند نیز نمایش داده می‌شوند.</p></div>
 <div class="panel"><div class="panel-head"><h3>مدیریت حساب‌ها</h3>${managerPill('صفحه '+fmtNum(directoryPage))}</div>
 <div class="filters"><input id="directory-search" class="search-input" placeholder="جست‌وجوی ایمیل یا شناسه در همین صفحه" oninput="filterManagerDirectory(this.value)" aria-label="جست‌وجوی کاربران">
 <button class="action-btn" onclick="openManagerUserLookup()">بازکردن با شناسه UUID</button></div>
 <div class="tbl-wrap"><table id="directory-table"><thead><tr><th>ایمیل حساب</th><th>تأیید</th><th>نقش مدیریت</th><th>ثبت‌نام</th><th>آخرین ورود</th><th>عملیات</th></tr></thead><tbody>
 ${directoryRows.map(u=>`<tr data-q="${html((u.email||'')+' '+u.id).toLowerCase()}"><td><strong>${html(u.email||'بدون ایمیل')}</strong><div class="technical-id" dir="ltr">${html(u.id)}</div></td>
 <td>${managerPill(u.verified?'تأییدشده':'تأییدنشده',u.verified?'green':'yellow')}</td>
 <td>${managerPill(MANAGER_ROLE_LABEL[u.role]||'کاربر عادی',u.role==='admin'?'red':'gray')}</td>
 <td>${safeTime(u.created_at)}</td><td>${safeTime(u.last_sign_in_at)}</td><td>${managerUserLink(u.id)}</td></tr>`).join('')||'<tr><td colspan="6" class="empty-note">کاربری در این صفحه ثبت نشده است.</td></tr>'}
 </tbody></table></div><div class="pagination manager-pagination">
 <span>جست‌وجو در همین صفحه انجام می‌شود؛ برای مشاهده سایر حساب‌ها صفحه را تغییر دهید.</span>
 <button class="action-btn" ${directoryPage===1?'disabled':''} onclick="setManagerDirectory(${directoryPage-1})">قبلی</button>
 <strong>${fmtNum(directoryPage)}</strong><button class="action-btn" ${!payload.nextPage?'disabled':''} onclick="setManagerDirectory(${directoryPage+1})">بعدی</button></div></div>`;
}
function filterManagerDirectory(v){document.querySelectorAll('#directory-table tbody tr').forEach(tr=>{tr.style.display=(tr.dataset.q||'').includes(String(v||'').toLowerCase())?'':'none';});}
function setManagerDirectory(v){directoryPage=Math.max(1,v);showPage('directory');}
function openManagerUserLookup(){openModal('مشاهده حساب با شناسه',`<div class="field-row"><label>شناسه کامل UUID</label><input dir="ltr" id="lookup-uuid" placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"></div>`,
 `<button class="btn btn-ghost" onclick="closeModal()">انصراف</button><button class="btn btn-accent" onclick="lookupManagerUser()">مشاهده</button>`);}
function lookupManagerUser(){const id=(document.getElementById('lookup-uuid')?.value||'').trim();if(!validUUID(id))return toast('شناسه UUID معتبر نیست','error');openManagerUser(id);}
async function openManagerUser(id){
 if(!validUUID(id))return toast('شناسه کاربر نامعتبر است','error');
 openModal('اطلاعات حساب','<div role="status" class="empty-note">در حال دریافت اطلاعات از سرور…</div>','<button class="btn btn-ghost" onclick="closeModal()">بستن</button>');
 try{
  const d=await managerFetch('user',{id}); const u=d.user;
  const emailsafe=html(u.email||'—'); const role=MANAGER_ROLE_LABEL[u.role]||'کاربر عادی';
  document.getElementById('modal-body').innerHTML=`<div class="detail-person"><strong>${emailsafe}</strong><small dir="ltr">${html(u.id)}</small>
  <span>${managerPill(role,u.role==='admin'?'red':'blue')} ${managerPill(u.verified?'ایمیل تأییدشده':'ایمیل تأییدنشده',u.verified?'green':'yellow')}</span></div>
  <div class="manager-facts"><div><small>ثبت‌نام</small><strong>${safeTime(u.created_at)}</strong></div><div><small>آخرین ورود</small><strong>${safeTime(u.last_sign_in_at)}</strong></div>
  <div><small>اعتبار مالک</small><strong>${fmtNum(d.wallet?.credits||0)} واحد</strong></div><div><small>مودیان دارای اعتبار</small><strong>${fmtNum((d.taxpayers||[]).length)}</strong></div></div>
  <h4>اعتبار مؤدیان</h4><div class="tbl-wrap"><table><thead><tr><th>شناسه محلی مؤدی</th><th>موجودی</th></tr></thead><tbody>
  ${(d.taxpayers||[]).map(t=>`<tr><td>${fmtNum(t.local_taxpayer_id)}</td><td>${fmtNum(t.credits)}</td></tr>`).join('')||'<tr><td colspan="2">مودی دارای موجودی ثبت نشده است.</td></tr>'}</tbody></table></div>
  ${d.taxpayers_truncated?'<div class="empty-note">فهرست مؤدیان تا ۱۰۰ مورد نمایش داده شده است.</div>':''}
  <h4>آخرین تراکنش‌ها</h4><div class="tbl-wrap"><table><thead><tr><th>مبلغ</th><th>وضعیت</th><th>تاریخ</th></tr></thead><tbody>
  ${(d.orders||[]).map(o=>`<tr><td>${fmtRial(o.amount_rial)}</td><td>${html(MANAGER_STATE_LABEL[o.state]||o.state)}</td><td>${safeTime(o.created_at)}</td></tr>`).join('')||'<tr><td colspan="3">تراکنشی ثبت نشده است.</td></tr>'}</tbody></table></div>`;
  document.getElementById('modal-foot').innerHTML=`${ADMIN_ROLE==='admin'?`<button class="btn btn-accent" onclick="openManagerRoleModal('${u.id}','${u.role||''}',${safeJSString(u.email||'')})">تعیین نقش</button>`:''}
  <button class="btn btn-ghost" onclick="closeModal()">بستن</button>`;
 }catch(e){document.getElementById('modal-body').textContent='خواندن اطلاعات حساب ناموفق: '+parseError(e);}
}

async function renderRoles(c){
 if(ADMIN_ROLE!=='admin'){c.innerHTML='<div class="config-notice">این بخش مختص مدیر ارشد است.</div>';return;}
 const d=await managerFetch('role-list',{});
 c.innerHTML=`<div class="section-hero"><span class="eyebrow">ACCESS CONTROL</span><h2>مدیران و سطح دسترسی</h2><p>سطح دسترسی فقط در سرور نگهداری می‌شود. هر تغییر با شناسه پیگیری و دلیل ثبت خواهد شد.</p></div>
 <div class="panel"><div class="panel-head"><h3>اعضای تیم مدیریت</h3><button class="action-btn" onclick="openManagerRoleModal('','','')">+ افزودن مدیر یا ناظر</button></div>
 <div class="tbl-wrap"><table><thead><tr><th>ایمیل</th><th>سطح</th><th>آخرین به‌روزرسانی</th><th>عملیات</th></tr></thead><tbody>
 ${(d.roles||[]).map(r=>`<tr><td>${html(r.email||'—')}<small class="technical-id" dir="ltr">${html(r.user_id)}</small></td>
 <td>${managerPill(MANAGER_ROLE_LABEL[r.role],r.role==='admin'?'red':'blue')}</td><td>${safeTime(r.updated_at)}</td>
 <td><button class="action-btn" onclick="openManagerRoleModal('${r.user_id}','${r.role}',${safeJSString(r.email||'')})">تغییر نقش</button></td></tr>`).join('')||'<tr><td colspan="4">مدیری تعریف نشده است.</td></tr>'}
 </tbody></table></div>${d.role_list_truncated?'<p class="empty-note">فهرست به ۱۰۰ نقش محدود است.</p>':''}</div>
 <div class="panel"><div class="panel-head"><h3>آخرین تغییرات نقش</h3></div><div class="tbl-wrap"><table><thead><tr><th>هدف</th><th>از</th><th>به</th><th>دلیل</th><th>زمان</th></tr></thead><tbody>
 ${(d.history||[]).slice(0,20).map(v=>`<tr><td>${managerGuid(v.target_id)}</td><td>${html(MANAGER_ROLE_LABEL[v.role_before]||'بدون نقش')}</td><td>${html(MANAGER_ROLE_LABEL[v.role_after]||'حذف دسترسی')}</td><td>${html(v.reason)}</td><td>${safeTime(v.created_at)}</td></tr>`).join('')||'<tr><td colspan="5">تغییری ثبت نشده است.</td></tr>'}</tbody></table></div></div>`;
}
function openManagerRoleModal(id='',expected='',email=''){
 if(ADMIN_ROLE!=='admin')return toast('تنها مدیر ارشد دسترسی دارد','error');
 openModal('تغییر دسترسی مدیریتی',`<div class="empty-note">افزودن مدیر یا تغییر نقش تنها با حساب ایمیل تأییدشده مجاز است. نقش مدیر ارشد را فقط به شخص مورد اعتماد واگذار کنید.</div>
 <div class="field-row"><label>شناسه UUID کاربر</label><input dir="ltr" id="manager-role-id" value="${html(id)}" ${id?'readonly':''}></div>
 <div class="field-row"><label>ایمیل هدف (برای تأیید انسانی)</label><input id="manager-role-email" value="${html(email)}" placeholder="ایمیل حساب مقصد"></div>
 <div class="field-row"><label>نقش جدید</label><select id="manager-role-choice"><option value="viewer">ناظر ـ فقط مشاهده</option><option value="editor">ویرایشگر ـ بدون تغییر موجودی</option><option value="admin">مدیر ارشد ـ دسترسی کامل</option><option value="none">لغو نقش مدیریتی</option></select></div>
 <div class="field-row"><label>دلیل تغییر (حداقل ۵ حرف)</label><textarea id="manager-role-reason" maxlength="500"></textarea></div>`,
 `<button class="btn btn-ghost" onclick="closeModal()">انصراف</button><button id="modal-submit" class="btn btn-danger" onclick="submitManagerRole()">ثبت تغییر با ممیزی</button>`);
 document.getElementById('manager-role-choice').value=expected||'viewer';
 document.getElementById('modal').dataset.expected=expected;
 document.getElementById('modal').dataset.request=newUUID();
}
async function submitManagerRole(){return submitBusy(async()=>{
 const target=document.getElementById('manager-role-id').value.trim(),expected=document.getElementById('modal').dataset.expected||null,
 role=document.getElementById('manager-role-choice').value,reason=document.getElementById('manager-role-reason').value.trim(),
 email=document.getElementById('manager-role-email').value.trim();
 if(!validUUID(target)||reason.length<5||!email.includes('@'))return toast('شناسه، ایمیل و دلیل تغییر را بررسی کنید','error');
 if(expected===role||(!expected&&role==='none'))return toast('نقش انتخابی تغییری ایجاد نمی‌کند','error');
 if(!confirm('تغییر دسترسی کاربر '+email+' به «'+(MANAGER_ROLE_LABEL[role]||'حذف نقش')+'» را تأیید می‌کنید؟'))return;
 try{const r=await managerFetch('role-assign',{target,expected,role,reason,confirmEmail:email,request:document.getElementById('modal').dataset.request});
  toast('تغییر نقش با رسید '+r.receipt+' ثبت شد');closeModal();showPage('roles');}
 catch(e){toast('خطا: '+parseError(e),'error');}
});}

async function appendManagerPriceHistory(c){
 if(ADMIN_ROLE!=='admin')return;
 const d=await managerFetch('price-history',{});
 const el=document.createElement('div');el.className='panel';
 el.innerHTML=`<div class="panel-head"><h3>تاریخچه رسمی تغییر قیمت</h3></div><div class="tbl-wrap"><table>
 <thead><tr><th>قیمت قبلی</th><th>قیمت جدید</th><th>دلیل</th><th>تاریخ</th><th>رسید</th></tr></thead><tbody>
 ${(d.history||[]).map(x=>`<tr><td>${fmtRial(x.old_price_rial)}</td><td>${fmtRial(x.new_price_rial)}</td><td>${html(x.reason)}</td><td>${safeTime(x.created_at)}</td><td>${managerGuid(x.request_id)}</td></tr>`).join('')||'<tr><td colspan="5">تغییری در قیمت ثبت نشده است.</td></tr>'}</tbody></table></div></div>`;
 c.appendChild(el);
}

async function renderManagerOrders(c){
 const filters={page:orderPage,perPage:40};if(orderState)filters.state=orderState;if(orderOwner)filters.owner=orderOwner;if(orderFrom)filters.from=orderFrom;if(orderTo)filters.to=orderTo;
 const payload=await managerFetch('orders',filters);const orders=payload.orders||[];
 c.innerHTML=`<div class="section-hero"><span class="eyebrow">PAYMENT LEDGER</span><h2>دفتر پرداخت‌ها</h2><p>تراکنش‌ها از سرور با شمارش دقیق و صفحه‌بندی دریافت می‌شوند. هیچ وضعیت پرداختی به‌صورت دستی تغییر نمی‌کند.</p></div>
 <div class="panel"><div class="panel-head"><h3>پرداخت‌های زرین‌پال</h3><span>${payload.total===null?'تعداد نامشخص':fmtNum(payload.total)+' سفارش'}</span></div>
 <div class="filters"><select id="order-filter" class="filter-select" onchange="setManagerOrderState(this.value)">
 <option value="">تمام وضعیت‌ها</option><option value="verified" ${orderState==='verified'?'selected':''}>تأییدشده</option><option value="requested" ${orderState==='requested'?'selected':''}>درگاه</option><option value="created" ${orderState==='created'?'selected':''}>جدید</option><option value="failed" ${orderState==='failed'?'selected':''}>ناموفق</option>
 </select><input id="order-search" class="search-input" placeholder="جست‌وجو در همین صفحه" oninput="filterManagerOrders(this.value)" aria-label="جست‌وجوی صفحه"></div>
 <div class="manager-filterbar" role="group" aria-label="فیلتر سروری تراکنش‌ها">
 <label>شناسه مالک<input id="order-owner" dir="ltr" maxlength="36" autocomplete="off" placeholder="UUID مالک" value="${html(orderOwner)}"></label>
 <label>از تاریخ میلادی<input type="date" id="order-from" value="${html(orderFrom)}"></label>
 <label>تا تاریخ میلادی<input type="date" id="order-to" value="${html(orderTo)}"></label>
 <button class="action-btn manager-filter-primary" type="button" onclick="applyManagerOrderFilters()">اعمال روی همه سفارش‌ها</button>
 <button class="action-btn" type="button" onclick="clearManagerOrderFilters()">حذف فیلتر</button>
 </div><p class="manager-filter-note">فیلتر مالک و بازه تاریخ روی کل سوابق سرور اعمال می‌شود؛ جست‌وجوی بالایی مخصوص صفحه جاری است.</p>
 <div class="tbl-wrap"><table id="manager-orders"><thead><tr><th>سفارش</th><th>مالک</th><th>مبلغ (ریال)</th><th>اعتبار</th><th>وضعیت</th><th>مرجع درگاه</th><th>تاریخ</th></tr></thead><tbody>
 ${orders.map(o=>`<tr data-q="${html((o.id+' '+o.owner_user_id+' '+(o.authority||'')+' '+(o.ref_id||'')).toLowerCase())}">
 <td>${managerGuid(o.id)}</td><td>${managerGuid(o.owner_user_id)}</td><td>${fmtRial(o.amount_rial)}</td><td>${fmtNum(o.credit_units)}</td>
 <td>${managerPill(MANAGER_STATE_LABEL[o.state]||o.state,o.state==='verified'?'green':o.state==='failed'?'red':'yellow')}</td><td>${html(o.ref_id||o.authority||'—')}</td><td>${safeTime(o.created_at)}</td></tr>`).join('')||'<tr><td colspan="7">سفارشی در این صفحه وجود ندارد.</td></tr>'}
 </tbody></table></div><div class="pagination manager-pagination"><button class="action-btn" ${orderPage===1?'disabled':''} onclick="setManagerOrders(${orderPage-1})">قبلی</button>
 <strong>صفحه ${fmtNum(orderPage)}</strong><button class="action-btn" ${payload.total!==null?orderPage*40>=payload.total:orders.length<40?'disabled':''} onclick="setManagerOrders(${orderPage+1})">بعدی</button></div></div>`;
}
function filterManagerOrders(q){document.querySelectorAll('#manager-orders tbody tr').forEach(tr=>{tr.style.display=(tr.dataset.q||'').includes((q||'').toLowerCase())?'':'none';});}
function setManagerOrderState(v){orderPage=1;orderState=v;showPage('payments');}
function applyManagerOrderFilters(){
 const owner=document.getElementById('order-owner').value.trim();
 const from=document.getElementById('order-from').value;
 const to=document.getElementById('order-to').value;
 if(owner&&!validUUID(owner))return toast('شناسه مالک باید UUID معتبر باشد','error');
 if((from&&!/^\d{4}-\d{2}-\d{2}$/.test(from))||(to&&!/^\d{4}-\d{2}-\d{2}$/.test(to)))return toast('تاریخ نامعتبر است','error');
 if(from&&to&&from>to)return toast('تاریخ شروع نباید بعد از پایان باشد','error');
 orderOwner=owner;orderFrom=from;orderTo=to;orderPage=1;showPage('payments');
}
function clearManagerOrderFilters(){orderOwner='';orderFrom='';orderTo='';orderState='';orderPage=1;showPage('payments');}
function setManagerOrders(v){orderPage=Math.max(1,v);showPage('payments');}

async function renderAuditLog(c){
 if(ADMIN_ROLE!=='admin'){c.innerHTML='<div class="config-notice">گزارش ممیزی فقط برای مدیر ارشد در دسترس است.</div>';return;}
 const d=await managerFetch('audit',{kind:auditKind,page:auditPage});
 lastAuditRows=Array.isArray(d.rows)?d.rows:[];lastAuditKind=auditKind;
  const labels={credit:'اصلاح اعتبار',price:'تغییر قیمت',role:'تغییر نقش'};
 c.innerHTML=`<div class="section-hero"><span class="eyebrow">AUDIT TRAIL</span><h2>دفتر ممیزی غیرقابل ویرایش</h2><p>تمام اصلاحات مدیریتی با شناسه درخواست، زمان، عامل و دلیل در پایگاه داده ثبت می‌شوند.</p></div>
 <div class="panel"><div class="panel-head"><h3>${labels[auditKind]}</h3><button class="action-btn manager-audit-export" type="button" onclick="exportManagerAuditPage()">خروجی CSV همین صفحه</button><span>${d.total===null?'':fmtNum(d.total)+' مورد'}</span></div>
 <div class="filters"><select class="filter-select" id="audit-kind" onchange="setManagerAudit(this.value)">
 <option value="credit" ${auditKind==='credit'?'selected':''}>اصلاح اعتبار</option><option value="price" ${auditKind==='price'?'selected':''}>تغییر قیمت</option><option value="role" ${auditKind==='role'?'selected':''}>تغییر دسترسی</option></select></div>
 <div class="tbl-wrap"><table><thead><tr><th>زمان</th><th>رسید</th><th>عامل</th><th>شرح تغییر</th><th>دلیل</th></tr></thead><tbody>
 ${(d.rows||[]).map(a=>`<tr><td>${safeTime(a.created_at)}</td><td>${managerGuid(a.request_id)}</td>
 <td>${managerGuid(a.actor_user_id||a.actor_id)}</td>
 <td>${auditKind==='credit'?'تغییر موجودی '+fmtNum(a.before_credits)+' به '+fmtNum(a.after_credits):auditKind==='price'?fmtRial(a.old_price_rial)+' به '+fmtRial(a.new_price_rial):html(MANAGER_ROLE_LABEL[a.role_before]||'کاربر عادی')+' ← '+html(MANAGER_ROLE_LABEL[a.role_after]||'لغو نقش')}</td>
 <td>${html(a.reason)}</td></tr>`).join('')||'<tr><td colspan="5">سابقه‌ای موجود نیست.</td></tr>'}</tbody></table></div>
 <div class="pagination manager-pagination"><button class="action-btn" ${auditPage===1?'disabled':''} onclick="setManagerAuditPage(${auditPage-1})">قبلی</button>
 <strong>${fmtNum(auditPage)}</strong><button class="action-btn" ${(d.total!==null?auditPage*50>=d.total:(d.rows||[]).length<50)?'disabled':''} onclick="setManagerAuditPage(${auditPage+1})">بعدی</button></div></div>`;
}
function exportManagerAuditPage(){
 if(ADMIN_ROLE!=='admin'||lastAuditKind!==auditKind)return toast('دسترسی خروجی مجاز نیست','error');
 if(!lastAuditRows.length)return toast('در این صفحه سابقه‌ای وجود ندارد','error');
 const rows=lastAuditRows.map(a=>[a.created_at||'',a.request_id||'',a.actor_user_id||a.actor_id||'',auditKind==='credit'?a.owner_id||'':auditKind==='role'?a.target_id||'':'',auditKind==='credit'?a.before_credits+' به '+a.after_credits:auditKind==='price'?a.old_price_rial+' به '+a.new_price_rial:(a.role_before||'none')+' به '+(a.role_after||'none'),a.reason||'']);
 const head=['زمان','رسید','عامل','هدف','تغییر','دلیل'];
 const csv=[head,...rows].map(row=>row.map(csvValue).join(',')).join('\r\n');
 SuraReports.download(new Blob(['\ufeff'+csv],{type:'text/csv;charset=utf-8'}),'sura-report-'+new Date().toISOString().slice(0,10)+'.csv');
 toast('خروجی '+lastAuditRows.length+' رکورد از همین صفحه آماده شد','success');
}
function setManagerAudit(k){auditKind=k;auditPage=1;showPage('audit');}
function setManagerAuditPage(p){auditPage=Math.max(1,p);showPage('audit');}

async function renderHealth(c){
 const h=await managerFetch('health',{});
 c.innerHTML=`<div class="section-hero"><span class="eyebrow">SYSTEM STATUS</span><h2>وضعیت سامانه</h2><p>تست اتصال واقعی و مجوز ورود به سرویس مرکزی. وضعیت درگاه پرداخت و پذیرش مالیاتی جداگانه نیازمند آزمون عملی است.</p></div>
 <div class="stat-grid three"><div class="stat-card green"><div class="stat-label">سرویس مدیریت</div><div class="stat-val small">فعال</div><div class="stat-delta">پاسخ سرور تأیید شد</div></div>
 <div class="stat-card blue"><div class="stat-label">پایگاه داده</div><div class="stat-val small">متصل</div><div class="stat-delta">خواندن قیمت و کیف‌پول</div></div>
 <div class="stat-card green"><div class="stat-label">احراز هویت</div><div class="stat-val small">معتبر</div><div class="stat-delta">${html(MANAGER_ROLE_LABEL[h.role]||h.role)}</div></div></div>
 <div class="panel"><div class="panel-head"><h3>اطلاعات محیط عملیاتی</h3></div><div class="manager-facts health-facts">
 <div><small>زمان پاسخ سرور</small><strong>${safeTime(h.server_time)}</strong></div><div><small>واحد پول</small><strong>ریال ایران</strong></div>
 <div><small>قیمت پایه جاری</small><strong>${fmtRial(h.price?.price_per_invoice_rial)}</strong></div>
 <div><small>سرور</small><strong class="technical-id">${html(SB_URL)}</strong></div></div></div>
 <div class="config-notice">تأیید اتصال پایگاه داده به معنای تأیید پرداخت واقعی، ارسال صورتحساب به سامانه مؤدیان، مالیات محاسبه‌شده یا پایداری بلندمدت سرویس نیست.</div>`;
}

function managerSummaryCards(summary){
 return `<div class="manager-facts kpi-trail"><div><small>سفارش‌های تأییدشده</small><strong>${fmtNum(summary.verified_orders)}</strong></div>
 <div><small>سفارش‌های در جریان</small><strong>${fmtNum(summary.requested_orders)}</strong></div>
 <div><small>موجودی مؤدیان</small><strong>${fmtNum(summary.taxpayer_credit_balance)} واحد</strong></div>
 <div><small>مصرف ثبت‌شده اعتبار پولی</small><strong>${fmtNum(summary.paid_invoice_uses)}</strong></div></div>
 <p class="empty-note">آمار فوق از تجمیع کل پایگاه داده به‌دست آمده است؛ صرفاً بر پایه فهرست ۱۰۰۰ ردیف آخر نیست. آمار مصرف صورتحساب معادل ارسال موفق مالیاتی نیست.</p>`;
}

function startManagerIdleGuard(){
 let last=Date.now();
 const mark=()=>{last=Date.now();};
 ['pointerdown','keydown','touchstart'].forEach(k=>document.addEventListener(k,mark,{passive:true}));
 const guard=()=>{if(USER_JWT&&Date.now()-last>20*60*1000){logout();toast('به‌علت عدم فعالیت، نشست مدیریتی بسته شد.','error');}};
 setInterval(guard,60*1000);
 document.addEventListener('visibilitychange',()=>{if(!document.hidden)guard();});
}
window.addEventListener('DOMContentLoaded',startManagerIdleGuard);
