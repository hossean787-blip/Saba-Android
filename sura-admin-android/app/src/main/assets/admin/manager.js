/* SURA Manager v1: account directory, role approvals, audit, health, paged orders. */
const MANAGER_ENDPOINT = 'sura-manager-api';
const MANAGER_SUPERADMIN_ID = 'eb80ffa2-c2b4-4857-b23a-f7f61f6c1fdc';
let directoryPage = 1, auditPage = 1, auditKind = 'credit', orderPage = 1, orderState = '';
let orderOwner='',orderFrom='',orderTo='';
let lastAuditRows=[],lastAuditKind='';
let directoryRows = [];
const MANAGER_STATE_LABEL = { created: 'ثبت اولیه', requested: 'منتظر پرداخت', verified: 'پرداخت تأییدشده', failed: 'ناموفق' };
const MANAGER_ROLE_LABEL = { admin: 'مدیر ارشد', editor: 'ویرایشگر', viewer: 'ناظر' };
async function managerFetch(endpoint, body = {}) {
    if (!USER_JWT)
        throw new Error('ابتدا وارد حساب مدیر شوید.');
    await ensureFreshToken();
    const res = await fetch(`${SB_URL}/functions/v1/${MANAGER_ENDPOINT}/${endpoint}`, {
        method: 'POST', headers: { apikey: SB_ANON, authorization: `Bearer ${USER_JWT}`, 'content-type': 'application/json' }, body: JSON.stringify(body)
    });
    if (res.status === 401) {
        logout();
        throw new Error('نشست منقضی شده است؛ دوباره وارد شوید.');
    }
    const data = await res.json().catch(() => ({ error: 'invalid_server_reply' }));
    if (!res.ok || data.error)
        throw new Error(data.error || `HTTP ${res.status}`);
    return data;
}
function managerRoles() {
    document.querySelectorAll('[data-min-role]').forEach(el => {
        const role = el.getAttribute('data-min-role');
        el.style.display = ((role === 'admin' && ADMIN_ROLE !== 'admin') || (role === 'staff' && ADMIN_ROLE === 'viewer')) ? 'none' : '';
    });
}
function safeJSString(value) { return html(JSON.stringify(String(value))); }
function managerUserLink(id) { return `<button class="action-btn" onclick="openManagerUser('${html(id)}')">جزئیات حساب</button>`; }
function managerPill(label, variant = 'blue') { return `<span class="badge ${variant}">${html(label)}</span>`; }
function managerGuid(id) { return `<span class="technical-id" dir="ltr" title="${html(id)}">${html(String(id).slice(0, 12))}…</span>`; }
function safeTime(value) { return value ? fmtDate(value) : '—'; }
async function renderDirectory(c) {
    if (ADMIN_ROLE === 'viewer') {
        c.innerHTML = '<div class="config-notice">فهرست کاربران فقط برای مدیر و ویرایشگر قابل مشاهده است.</div>';
        return;
    }
    const payload = await managerFetch('users', { page: directoryPage, perPage: 30 });
    directoryRows = payload.users || [];
    c.innerHTML = `<div class="section-hero"><span class="eyebrow">ACCOUNT DIRECTORY</span><h2>حساب‌های ثبت‌نام‌شده</h2>
 <p>فهرست واقعی کاربران از سرویس احراز هویت. حساب‌هایی که هنوز کیف‌پول ندارند نیز نمایش داده می‌شوند.</p></div>
 <div class="panel"><div class="panel-head"><h3>مدیریت حساب‌ها</h3>${managerPill('صفحه ' + fmtNum(directoryPage))}</div>
 <div class="filters"><input id="directory-search" class="search-input" placeholder="جست‌وجوی ایمیل یا شناسه در همین صفحه" oninput="filterManagerDirectory(this.value)" aria-label="جست‌وجوی کاربران">
 <button class="action-btn" onclick="openManagerUserLookup()">بازکردن با شناسه UUID</button></div>
 <div class="tbl-wrap"><table id="directory-table"><thead><tr><th>ایمیل حساب</th><th>تأیید</th><th>نقش مدیریت</th><th>ثبت‌نام</th><th>آخرین ورود</th><th>عملیات</th></tr></thead><tbody>
 ${directoryRows.map(u => `<tr data-q="${html((u.email || '') + ' ' + u.id).toLowerCase()}"><td><strong>${html(u.email || 'بدون ایمیل')}</strong><div class="technical-id" dir="ltr">${html(u.id)}</div></td>
 <td>${managerPill(u.verified ? 'تأییدشده' : 'تأییدنشده', u.verified ? 'green' : 'yellow')}</td>
 <td>${managerPill(MANAGER_ROLE_LABEL[u.role] || 'کاربر عادی', u.role === 'admin' ? 'red' : 'gray')}</td>
 <td>${safeTime(u.created_at)}</td><td>${safeTime(u.last_sign_in_at)}</td><td>${managerUserLink(u.id)}</td></tr>`).join('') || '<tr><td colspan="6" class="empty-note">کاربری در این صفحه ثبت نشده است.</td></tr>'}
 </tbody></table></div><div class="pagination manager-pagination">
 <span>جست‌وجو در همین صفحه انجام می‌شود؛ برای مشاهده سایر حساب‌ها صفحه را تغییر دهید.</span>
 <button class="action-btn" ${directoryPage === 1 ? 'disabled' : ''} onclick="setManagerDirectory(${directoryPage - 1})">قبلی</button>
 <strong>${fmtNum(directoryPage)}</strong><button class="action-btn" ${!payload.nextPage ? 'disabled' : ''} onclick="setManagerDirectory(${directoryPage + 1})">بعدی</button></div></div>`;
}
function filterManagerDirectory(v) { document.querySelectorAll('#directory-table tbody tr').forEach(tr => { tr.style.display = (tr.dataset.q || '').includes(String(v || '').toLowerCase()) ? '' : 'none'; }); }
function setManagerDirectory(v) { directoryPage = Math.max(1, v); showPage('directory'); }
function openManagerUserLookup() {
    openModal('مشاهده حساب با شناسه', `<div class="field-row"><label>شناسه کامل UUID</label><input dir="ltr" id="lookup-uuid" placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"></div>`, `<button class="btn btn-ghost" onclick="closeModal()">انصراف</button><button class="btn btn-accent" onclick="lookupManagerUser()">مشاهده</button>`);
}
function lookupManagerUser() { var _a; const id = (((_a = document.getElementById('lookup-uuid')) === null || _a === void 0 ? void 0 : _a.value) || '').trim(); if (!validUUID(id))
    return toast('شناسه UUID معتبر نیست', 'error'); openManagerUser(id); }
async function openManagerUser(id) {
    var _a;
    if (!validUUID(id))
        return toast('شناسه کاربر نامعتبر است', 'error');
    openModal('اطلاعات حساب', '<div role="status" class="empty-note">در حال دریافت اطلاعات از سرور…</div>', '<button class="btn btn-ghost" onclick="closeModal()">بستن</button>');
    try {
        const d = await managerFetch('user', { id });
        const u = d.user;
        const emailsafe = html(u.email || '—');
        const role = MANAGER_ROLE_LABEL[u.role] || 'کاربر عادی';
        document.getElementById('modal-body').innerHTML = `<div class="detail-person"><strong>${emailsafe}</strong><small dir="ltr">${html(u.id)}</small>
  <span>${managerPill(role, u.role === 'admin' ? 'red' : 'blue')} ${managerPill(u.verified ? 'ایمیل تأییدشده' : 'ایمیل تأییدنشده', u.verified ? 'green' : 'yellow')}</span></div>
  <div class="manager-facts"><div><small>ثبت‌نام</small><strong>${safeTime(u.created_at)}</strong></div><div><small>آخرین ورود</small><strong>${safeTime(u.last_sign_in_at)}</strong></div>
  <div><small>اعتبار مالک</small><strong>${fmtNum(((_a = d.wallet) === null || _a === void 0 ? void 0 : _a.credits) || 0)} واحد</strong></div><div><small>مودیان دارای اعتبار</small><strong>${fmtNum((d.taxpayers || []).length)}</strong></div></div>
${ADMIN_ROLE==='admin'?'<button class="action-btn" onclick="openSelectedGift(\''+html(u.id)+'\')">اعطای اعتبار رایگان</button>':''}
${ADMIN_ROLE==='admin'?'<button class="action-btn" onclick="openManagerConsent(\''+html(u.id)+'\')">سابقه پذیرش قوانین</button>':''}
  <h4>اعتبار مؤدیان</h4><div class="tbl-wrap"><table><thead><tr><th>شناسه محلی مؤدی</th><th>موجودی</th></tr></thead><tbody>
  ${(d.taxpayers || []).map(t => `<tr><td>${fmtNum(t.local_taxpayer_id)}</td><td>${fmtNum(t.credits)}</td></tr>`).join('') || '<tr><td colspan="2">مودی دارای موجودی ثبت نشده است.</td></tr>'}</tbody></table></div>
  ${d.taxpayers_truncated ? '<div class="empty-note">فهرست مؤدیان تا ۱۰۰ مورد نمایش داده شده است.</div>' : ''}
  <h4>آخرین تراکنش‌ها</h4><div class="tbl-wrap"><table><thead><tr><th>مبلغ</th><th>وضعیت</th><th>تاریخ</th></tr></thead><tbody>
  ${(d.orders || []).map(o => `<tr><td>${fmtRial(o.amount_rial)}</td><td>${html(MANAGER_STATE_LABEL[o.state] || o.state)}</td><td>${safeTime(o.created_at)}</td></tr>`).join('') || '<tr><td colspan="3">تراکنشی ثبت نشده است.</td></tr>'}</tbody></table></div>`;
        document.getElementById('modal-foot').innerHTML = `${ADMIN_ROLE === 'admin' ? `<button class="btn btn-accent" onclick="openManagerRoleModal('${u.id}','${u.role || ''}',${safeJSString(u.email || '')})">تعیین نقش</button>` : ''}
  <button class="btn btn-ghost" onclick="closeModal()">بستن</button>`;
    }
    catch (e) {
        document.getElementById('modal-body').textContent = 'خواندن اطلاعات حساب ناموفق: ' + parseError(e);
    }
}
async function renderRoles(c) {
    if (ADMIN_ROLE !== 'admin') {
        c.innerHTML = '<div class="config-notice">این بخش مختص مدیر ارشد است.</div>';
        return;
    }
    const d = await managerFetch('role-list', {});
    c.innerHTML = `<div class="section-hero"><span class="eyebrow">ACCESS CONTROL</span><h2>مدیران و سطح دسترسی</h2><p>مدیرکل منحصراً hossean787@gmail.com است. نقش ناظر و ویرایشگر جداگانه مدیریت می‌شوند.</p></div>
 <div class="panel"><div class="panel-head"><h3>اعضای تیم مدیریت</h3><button class="action-btn" onclick="openManagerRoleModal('','','')">+ افزودن ناظر یا ویرایشگر</button></div>
 <div class="tbl-wrap"><table><thead><tr><th>ایمیل</th><th>سطح</th><th>آخرین به‌روزرسانی</th><th>عملیات</th></tr></thead><tbody>
 ${(d.roles || []).map(r => `<tr><td>${html(r.email || '—')}<small class="technical-id" dir="ltr">${html(r.user_id)}</small></td>
 <td>${managerPill(MANAGER_ROLE_LABEL[r.role], r.role === 'admin' ? 'red' : 'blue')}</td><td>${safeTime(r.updated_at)}</td>
 <td><button class="action-btn" ${r.user_id===MANAGER_SUPERADMIN_ID?'disabled aria-label="مدیرکل ثابت"':''} onclick="openManagerRoleModal('${r.user_id}','${r.role}',${safeJSString(r.email || '')})">تغییر نقش</button></td></tr>`).join('') || '<tr><td colspan="4">مدیری تعریف نشده است.</td></tr>'}
 </tbody></table></div>${d.role_list_truncated ? '<p class="empty-note">فهرست به ۱۰۰ نقش محدود است.</p>' : ''}</div>
 <div class="panel"><div class="panel-head"><h3>آخرین تغییرات نقش</h3></div><div class="tbl-wrap"><table><thead><tr><th>هدف</th><th>از</th><th>به</th><th>دلیل</th><th>زمان</th></tr></thead><tbody>
 ${(d.history || []).slice(0, 20).map(v => `<tr><td>${managerGuid(v.target_id)}</td><td>${html(MANAGER_ROLE_LABEL[v.role_before] || 'بدون نقش')}</td><td>${html(MANAGER_ROLE_LABEL[v.role_after] || 'حذف دسترسی')}</td><td>${html(v.reason)}</td><td>${safeTime(v.created_at)}</td></tr>`).join('') || '<tr><td colspan="5">تغییری ثبت نشده است.</td></tr>'}</tbody></table></div></div>`;
}
function openManagerRoleModal(id = '', expected = '', email = '') {
    if (ADMIN_ROLE !== 'admin')
        return toast('تنها مدیر ارشد دسترسی دارد', 'error');
    if(id===MANAGER_SUPERADMIN_ID)return toast('حساب مدیرکل ثابت است','error');
    openModal('تغییر دسترسی مدیریتی', `<div class="empty-note">مدیرکل ثابت: hossean787@gmail.com. برای سایر حساب‌ها فقط نقش ناظر و ویرایشگر قابل تنظیم است.</div>
 <div class="field-row"><label>شناسه UUID کاربر</label><input dir="ltr" id="manager-role-id" value="${html(id)}" ${id ? 'readonly' : ''}></div>
 <div class="field-row"><label>ایمیل هدف (برای تأیید انسانی)</label><input id="manager-role-email" value="${html(email)}" placeholder="ایمیل حساب مقصد"></div>
 <div class="field-row"><label>نقش جدید</label><select id="manager-role-choice"><option value="viewer">ناظر ـ فقط مشاهده</option><option value="editor">ویرایشگر ـ بدون تغییر موجودی</option><option value="none">لغو نقش مدیریتی</option></select></div>
 <div class="field-row"><label>دلیل تغییر (حداقل ۵ حرف)</label><textarea id="manager-role-reason" maxlength="500"></textarea></div>`, `<button class="btn btn-ghost" onclick="closeModal()">انصراف</button><button id="modal-submit" class="btn btn-danger" onclick="submitManagerRole()">ثبت تغییر با ممیزی</button>`);
    document.getElementById('manager-role-choice').value = expected || 'viewer';
    document.getElementById('modal').dataset.expected = expected;
    document.getElementById('modal').dataset.request = newUUID();
}
async function submitManagerRole() {
    return submitBusy(async () => {
        const target = document.getElementById('manager-role-id').value.trim(), expected = document.getElementById('modal').dataset.expected || null, role = document.getElementById('manager-role-choice').value, reason = document.getElementById('manager-role-reason').value.trim(), email = document.getElementById('manager-role-email').value.trim();
        if (!validUUID(target) || reason.length < 5 || !email.includes('@'))
            return toast('شناسه، ایمیل و دلیل تغییر را بررسی کنید', 'error');
        if(target===MANAGER_SUPERADMIN_ID||role==='admin')return toast('نقش مدیرکل فقط برای حساب ثابت است','error');
        if (expected === role || (!expected && role === 'none'))
            return toast('نقش انتخابی تغییری ایجاد نمی‌کند', 'error');
        if (!confirm('تغییر دسترسی کاربر ' + email + ' به «' + (MANAGER_ROLE_LABEL[role] || 'حذف نقش') + '» را تأیید می‌کنید؟'))
            return;
        try {
            const r = await managerFetch('role-assign', { target, expected, role, reason, confirmEmail: email, request: document.getElementById('modal').dataset.request });
            toast('تغییر نقش با رسید ' + r.receipt + ' ثبت شد');
            closeModal();
            showPage('roles');
        }
        catch (e) {
            toast('خطا: ' + parseError(e), 'error');
        }
    });
}
async function appendManagerPriceHistory(c) {
    if (ADMIN_ROLE !== 'admin')
        return;
    const d = await managerFetch('price-history', {});
    const el = document.createElement('div');
    el.className = 'panel';
    el.innerHTML = `<div class="panel-head"><h3>تاریخچه رسمی تغییر قیمت</h3></div><div class="tbl-wrap"><table>
 <thead><tr><th>قیمت قبلی</th><th>قیمت جدید</th><th>دلیل</th><th>تاریخ</th><th>رسید</th></tr></thead><tbody>
 ${(d.history || []).map(x => `<tr><td>${fmtRial(x.old_price_rial)}</td><td>${fmtRial(x.new_price_rial)}</td><td>${html(x.reason)}</td><td>${safeTime(x.created_at)}</td><td>${managerGuid(x.request_id)}</td></tr>`).join('') || '<tr><td colspan="5">تغییری در قیمت ثبت نشده است.</td></tr>'}</tbody></table></div></div>`;
    c.appendChild(el);
}
async function renderManagerOrders(c) {
    const filters={page:orderPage,perPage:40};if(orderState)filters.state=orderState;if(orderOwner)filters.owner=orderOwner;if(orderFrom)filters.from=orderFrom;if(orderTo)filters.to=orderTo;
 const payload=await managerFetch('orders',filters);const orders=payload.orders||[];
    c.innerHTML = `<div class="section-hero"><span class="eyebrow">PAYMENT LEDGER</span><h2>دفتر پرداخت‌ها</h2><p>تراکنش‌ها از سرور با شمارش دقیق و صفحه‌بندی دریافت می‌شوند. هیچ وضعیت پرداختی به‌صورت دستی تغییر نمی‌کند.</p></div>
 <div class="panel"><div class="panel-head"><h3>پرداخت‌های زرین‌پال</h3><span>${payload.total === null ? 'تعداد نامشخص' : fmtNum(payload.total) + ' سفارش'}</span></div>
 <div class="filters"><select id="order-filter" class="filter-select" onchange="setManagerOrderState(this.value)">
 <option value="">تمام وضعیت‌ها</option><option value="verified" ${orderState === 'verified' ? 'selected' : ''}>تأییدشده</option><option value="requested" ${orderState === 'requested' ? 'selected' : ''}>درگاه</option><option value="created" ${orderState === 'created' ? 'selected' : ''}>جدید</option><option value="failed" ${orderState === 'failed' ? 'selected' : ''}>ناموفق</option>
 </select><input id="order-search" class="search-input" placeholder="جست‌وجو در همین صفحه" oninput="filterManagerOrders(this.value)" aria-label="جست‌وجوی صفحه"></div>
 <div class="manager-filterbar" role="group" aria-label="فیلتر سروری تراکنش‌ها">
 <label>شناسه مالک<input id="order-owner" dir="ltr" maxlength="36" autocomplete="off" placeholder="UUID مالک" value="${html(orderOwner)}"></label>
 <label>از تاریخ میلادی<input type="date" id="order-from" value="${html(orderFrom)}"></label>
 <label>تا تاریخ میلادی<input type="date" id="order-to" value="${html(orderTo)}"></label>
 <button class="action-btn manager-filter-primary" type="button" onclick="applyManagerOrderFilters()">اعمال روی همه سفارش‌ها</button>
 <button class="action-btn" type="button" onclick="clearManagerOrderFilters()">حذف فیلتر</button>
 </div><p class="manager-filter-note">فیلتر مالک و بازه تاریخ روی کل سوابق سرور اعمال می‌شود؛ جست‌وجوی بالایی مخصوص صفحه جاری است.</p>
 <div class="tbl-wrap"><table id="manager-orders"><thead><tr><th>سفارش</th><th>مالک</th><th>مبلغ (ریال)</th><th>اعتبار</th><th>وضعیت</th><th>مرجع درگاه</th><th>تاریخ</th></tr></thead><tbody>
 ${orders.map(o => `<tr data-q="${html((o.id + ' ' + o.owner_user_id + ' ' + (o.authority || '') + ' ' + (o.ref_id || '')).toLowerCase())}">
 <td>${managerGuid(o.id)}</td><td>${managerGuid(o.owner_user_id)}</td><td>${fmtRial(o.amount_rial)}</td><td>${fmtNum(o.credit_units)}</td>
 <td>${managerPill(MANAGER_STATE_LABEL[o.state] || o.state, o.state === 'verified' ? 'green' : o.state === 'failed' ? 'red' : 'yellow')}</td><td>${html(o.ref_id || o.authority || '—')}</td><td>${safeTime(o.created_at)}</td></tr>`).join('') || '<tr><td colspan="7">سفارشی در این صفحه وجود ندارد.</td></tr>'}
 </tbody></table></div><div class="pagination manager-pagination"><button class="action-btn" ${orderPage === 1 ? 'disabled' : ''} onclick="setManagerOrders(${orderPage - 1})">قبلی</button>
 <strong>صفحه ${fmtNum(orderPage)}</strong><button class="action-btn" ${payload.total !== null ? orderPage * 40 >= payload.total : orders.length < 40 ? 'disabled' : ''} onclick="setManagerOrders(${orderPage + 1})">بعدی</button></div></div>`;
}
function filterManagerOrders(q) { document.querySelectorAll('#manager-orders tbody tr').forEach(tr => { tr.style.display = (tr.dataset.q || '').includes((q || '').toLowerCase()) ? '' : 'none'; }); }
function setManagerOrderState(v) { orderPage = 1; orderState = v; showPage('payments'); }
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
function setManagerOrders(v) { orderPage = Math.max(1, v); showPage('payments'); }
async function renderAuditLog(c) {
    if (ADMIN_ROLE !== 'admin') {
        c.innerHTML = '<div class="config-notice">گزارش ممیزی فقط برای مدیر ارشد در دسترس است.</div>';
        return;
    }
    const d = await managerFetch('audit', { kind: auditKind, page: auditPage });
    lastAuditRows=Array.isArray(d.rows)?d.rows:[];lastAuditKind=auditKind;
  const labels = { credit: 'اصلاح اعتبار', price: 'تغییر قیمت', role: 'تغییر نقش' };
    c.innerHTML = `<div class="section-hero"><span class="eyebrow">AUDIT TRAIL</span><h2>دفتر ممیزی غیرقابل ویرایش</h2><p>تمام اصلاحات مدیریتی با شناسه درخواست، زمان، عامل و دلیل در پایگاه داده ثبت می‌شوند.</p></div>
 <div class="panel"><div class="panel-head"><h3>${labels[auditKind]}</h3><button class="action-btn manager-audit-export" type="button" onclick="exportManagerAuditPage()">خروجی CSV همین صفحه</button><span>${d.total === null ? '' : fmtNum(d.total) + ' مورد'}</span></div>
 <div class="filters"><select class="filter-select" id="audit-kind" onchange="setManagerAudit(this.value)">
 <option value="credit" ${auditKind === 'credit' ? 'selected' : ''}>اصلاح اعتبار</option><option value="price" ${auditKind === 'price' ? 'selected' : ''}>تغییر قیمت</option><option value="role" ${auditKind === 'role' ? 'selected' : ''}>تغییر دسترسی</option></select></div>
 <div class="tbl-wrap"><table><thead><tr><th>زمان</th><th>رسید</th><th>عامل</th><th>شرح تغییر</th><th>دلیل</th></tr></thead><tbody>
 ${(d.rows || []).map(a => `<tr><td>${safeTime(a.created_at)}</td><td>${managerGuid(a.request_id)}</td>
 <td>${managerGuid(a.actor_user_id || a.actor_id)}</td>
 <td>${auditKind === 'credit' ? 'تغییر موجودی ' + fmtNum(a.before_credits) + ' به ' + fmtNum(a.after_credits) : auditKind === 'price' ? fmtRial(a.old_price_rial) + ' به ' + fmtRial(a.new_price_rial) : html(MANAGER_ROLE_LABEL[a.role_before] || 'کاربر عادی') + ' ← ' + html(MANAGER_ROLE_LABEL[a.role_after] || 'لغو نقش')}</td>
 <td>${html(a.reason)}</td></tr>`).join('') || '<tr><td colspan="5">سابقه‌ای موجود نیست.</td></tr>'}</tbody></table></div>
 <div class="pagination manager-pagination"><button class="action-btn" ${auditPage === 1 ? 'disabled' : ''} onclick="setManagerAuditPage(${auditPage - 1})">قبلی</button>
 <strong>${fmtNum(auditPage)}</strong><button class="action-btn" ${(d.total !== null ? auditPage * 50 >= d.total : (d.rows || []).length < 50) ? 'disabled' : ''} onclick="setManagerAuditPage(${auditPage + 1})">بعدی</button></div></div>`;
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
function setManagerAudit(k) { auditKind = k; auditPage = 1; showPage('audit'); }
function setManagerAuditPage(p) { auditPage = Math.max(1, p); showPage('audit'); }
async function renderHealth(c) {
    var _a;
    const h = await managerFetch('health', {});
    c.innerHTML = `<div class="section-hero"><span class="eyebrow">SYSTEM STATUS</span><h2>وضعیت سامانه</h2><p>تست اتصال واقعی و مجوز ورود به سرویس مرکزی. وضعیت درگاه پرداخت و پذیرش مالیاتی جداگانه نیازمند آزمون عملی است.</p></div>
 <div class="stat-grid three"><div class="stat-card green"><div class="stat-label">سرویس مدیریت</div><div class="stat-val small">فعال</div><div class="stat-delta">پاسخ سرور تأیید شد</div></div>
 <div class="stat-card blue"><div class="stat-label">پایگاه داده</div><div class="stat-val small">متصل</div><div class="stat-delta">خواندن قیمت و کیف‌پول</div></div>
 <div class="stat-card green"><div class="stat-label">احراز هویت</div><div class="stat-val small">معتبر</div><div class="stat-delta">${html(MANAGER_ROLE_LABEL[h.role] || h.role)}</div></div></div>
 <div class="panel"><div class="panel-head"><h3>اطلاعات محیط عملیاتی</h3></div><div class="manager-facts health-facts">
 <div><small>زمان پاسخ سرور</small><strong>${safeTime(h.server_time)}</strong></div><div><small>واحد پول</small><strong>ریال ایران</strong></div>
 <div><small>قیمت پایه جاری</small><strong>${fmtRial((_a = h.price) === null || _a === void 0 ? void 0 : _a.price_per_invoice_rial)}</strong></div>
 <div><small>سرور</small><strong class="technical-id">${html(SB_URL)}</strong></div></div></div>
 <div class="config-notice">تأیید اتصال پایگاه داده به معنای تأیید پرداخت واقعی، ارسال صورتحساب به سامانه مؤدیان، مالیات محاسبه‌شده یا پایداری بلندمدت سرویس نیست.</div>`;
}
function managerSummaryCards(summary) {
    return `<div class="manager-facts kpi-trail"><div><small>سفارش‌های تأییدشده</small><strong>${fmtNum(summary.verified_orders)}</strong></div>
 <div><small>سفارش‌های در جریان</small><strong>${fmtNum(summary.requested_orders)}</strong></div>
 <div><small>موجودی مؤدیان</small><strong>${fmtNum(summary.taxpayer_credit_balance)} واحد</strong></div>
 <div><small>مصرف ثبت‌شده اعتبار پولی</small><strong>${fmtNum(summary.paid_invoice_uses)}</strong></div></div>
 <p class="empty-note">آمار فوق از تجمیع کل پایگاه داده به‌دست آمده است؛ صرفاً بر پایه فهرست ۱۰۰۰ ردیف آخر نیست. آمار مصرف صورتحساب معادل ارسال موفق مالیاتی نیست.</p>`;
}
function startManagerIdleGuard() {
    let last = Date.now();
    const mark = () => { last = Date.now(); };
    ['pointerdown', 'keydown', 'touchstart'].forEach(k => document.addEventListener(k, mark, { passive: true }));
    const guard = () => { if (USER_JWT && Date.now() - last > 20 * 60 * 1000) {
        logout();
        toast('به‌علت عدم فعالیت، نشست مدیریتی بسته شد.', 'error');
    } };
    setInterval(guard, 60 * 1000);
    document.addEventListener('visibilitychange', () => { if (!document.hidden)
        guard(); });
}
window.addEventListener('DOMContentLoaded', startManagerIdleGuard);

/* Manager support workspace — strictly admin-only on server and UI. */
let supportPage=1,supportFilter='',supportOwner='',supportTicket='',supportThreadPage=1,supportPending=null;
const SUPPORT_STATES={open:'ثبت‌شده',in_progress:'در حال بررسی',answered:'پاسخ داده‌شده',closed:'بسته'};
async function supportFetch(route,body){
 if(ADMIN_ROLE!=='admin'||!USER_JWT)throw Error('دسترسی مدیرکل لازم است.');
 await ensureFreshToken();
 const res=await fetch(SB_URL+'/functions/v1/sura-manager-support/'+route,{method:'POST',headers:{apikey:SB_ANON,authorization:'Bearer '+USER_JWT,'content-type':'application/json'},body:JSON.stringify(body)});
 const data=await res.json().catch(()=>({error:'invalid_response'}));
 if(res.status===401){logout();throw Error('نشست منقضی شد.');}
 if(!res.ok||data.error)throw Error(data.error||'support_unavailable');
 return data;
}
function supportPill(status){return managerPill(SUPPORT_STATES[status]||'نامشخص',status==='answered'?'green':status==='closed'?'gray':'blue');}
function supportFilterApply(){
 const owner=document.getElementById('support-owner').value.trim();
 if(owner&&!validUUID(owner))return toast('شناسه حساب معتبر نیست','error');
 supportOwner=owner;supportFilter=document.getElementById('support-state').value;supportPage=1;supportTicket='';showPage('support');
}
function supportGo(p){supportPage=Math.max(1,p);showPage('support');}
function supportOpen(id){if(!validUUID(id))return;supportTicket=id;supportThreadPage=1;showPage('support');}
function supportBack(){supportTicket='';supportThreadPage=1;showPage('support');}
function supportThreadGo(n){supportThreadPage=Math.max(1,n);showPage('support');}
function supportMessage(label,body,date){return '<div class="panel" style="padding:14px;margin:8px 0"><b>'+html(label)+'</b><div style="white-space:pre-wrap;overflow-wrap:anywhere;margin-top:8px">'+html(body)+'</div><small>'+html(safeTime(date))+'</small></div>';}
async function renderSupport(c){
 if(ADMIN_ROLE!=='admin'){c.innerHTML='<div class="config-notice">فقط مدیرکل مجاز است.</div>';return;}
 if(!supportTicket){
  const q={page:supportPage};if(supportFilter)q.status=supportFilter;if(supportOwner)q.owner=supportOwner;
  const data=await supportFetch('list',q);const rows=data.tickets||[];
  c.innerHTML='<div class="section-hero"><span class="eyebrow">SUPPORT CENTER</span><h2>مرکز پشتیبانی کاربران</h2><p>پیگیری درخواست‌ها بر اساس آخرین پیام؛ پاسخ و تغییر وضعیت با ثبت ممیزی.</p></div>'+
   '<div class="panel" style="padding:16px"><div class="field-row"><label>وضعیت</label><select id="support-state"><option value="">همه</option>'+
   Object.keys(SUPPORT_STATES).map(k=>'<option value="'+k+'" '+(supportFilter===k?'selected':'')+'>'+SUPPORT_STATES[k]+'</option>').join('')+
   '</select></div><div class="field-row"><label>شناسه حساب (اختیاری)</label><input id="support-owner" dir="ltr" value="'+html(supportOwner)+'"></div><button class="action-btn" onclick="supportFilterApply()">اعمال فیلتر</button></div>'+
   (rows.length?rows.map(t=>'<div class="panel" style="padding:15px"><div class="panel-head"><h3>'+html(t.category)+'</h3>'+supportPill(t.status)+'</div><p style="overflow-wrap:anywhere">'+html(String(t.message).slice(0,180))+'</p><div>مالک: '+managerGuid(t.owner_id)+'</div><small>آخرین پیام: '+html(t.last_sender==='customer'?'کاربر':'پشتیبانی')+' | '+html(safeTime(t.activity_at))+'</small><div><button class="action-btn" onclick="supportOpen(\''+html(t.id)+'\')">مشاهده گفتگو</button></div></div>').join(''):'<div class="empty-note">درخواستی یافت نشد.</div>')+
   '<div class="manager-pagination"><button class="action-btn" '+(supportPage===1?'disabled':'')+' onclick="supportGo('+(supportPage-1)+')">قبلی</button><span>صفحه '+fmtNum(supportPage)+'</span><button class="action-btn" '+(rows.length<30?'disabled':'')+' onclick="supportGo('+(supportPage+1)+')">بعدی</button></div>';
  return;
 }
 const data=await supportFetch('thread',{ticket:supportTicket,page:supportThreadPage});const t=data.ticket,rows=data.messages||[];
 const pending=supportPending&&supportPending.ticket===t.id?supportPending:null;
 c.innerHTML='<div class="section-hero"><h2>'+html(t.category)+'</h2><p dir="ltr">'+html(t.id)+'</p>'+supportPill(t.status)+'<button class="action-btn" onclick="supportBack()">بازگشت به فهرست</button></div>'+
 '<div class="panel" style="padding:14px">مالک حساب: '+managerGuid(t.owner_id)+' '+managerUserLink(t.owner_id)+'</div>'+
 supportMessage('درخواست اولیه کاربر',t.message,t.created_at)+(t.response?supportMessage('پاسخ اولیه',t.response,''):'')+
 rows.slice().reverse().map(m=>supportMessage(m.sender==='customer'?'کاربر':'پشتیبانی',m.body,m.created_at)).join('')+
 '<div class="manager-pagination"><button class="action-btn" '+(supportThreadPage===1?'disabled':'')+' onclick="supportThreadGo('+(supportThreadPage-1)+')">جدیدتر</button><button class="action-btn" '+(rows.length<30?'disabled':'')+' onclick="supportThreadGo('+(supportThreadPage+1)+')">قدیمی‌تر</button></div>'+
 '<div class="panel" style="padding:16px"><h3>پاسخ و تغییر وضعیت</h3><div class="field-row"><label>وضعیت جدید</label><select id="support-next" '+(pending?'disabled':'')+'>'+
 Object.keys(SUPPORT_STATES).map(k=>'<option value="'+k+'" '+((pending?pending.status:t.status)===k?'selected':'')+'>'+SUPPORT_STATES[k]+'</option>').join('')+
 '</select></div><div class="field-row"><label>متن پاسخ (اختیاری برای تغییر وضعیت)</label><textarea id="support-reply" maxlength="4000" rows="5" '+(pending?'disabled':'')+'>'+html(pending?pending.reply:'')+'</textarea></div>'+
 '<button class="action-btn" id="support-send" onclick="supportSubmit(\''+html(t.id)+'\',\''+html(t.status)+'\')">'+(pending?'بررسی همان عملیات':'تأیید پاسخ و وضعیت')+'</button>'+
 (pending?'<button class="action-btn" onclick="supportAbandon()">انصراف از تلاش قبلی</button>':'')+'</div>'+
 '<div class="panel" style="padding:16px"><h3>آخرین تغییرات ثبت‌شده</h3>'+((data.audit||[]).map(a=>'<p>'+html(SUPPORT_STATES[a.status_before])+' ← '+html(SUPPORT_STATES[a.status_after])+' | '+html(safeTime(a.created_at))+'</p>').join('')||'بدون تغییر مدیریتی')+'</div>';
}
async function supportSubmit(ticket,expected){
 const btn=document.getElementById('support-send');if(!btn||btn.disabled)return;
 if(supportPending&&supportPending.ticket!==ticket)return toast('ابتدا عملیات قبلی را تعیین تکلیف کنید','error');
 if(!supportPending){
  const status=document.getElementById('support-next').value,reply=document.getElementById('support-reply').value.trim();
  if(!reply&&status===expected)return toast('پاسخ یا وضعیت جدید لازم است','error');
  if(/-----BEGIN (?:RSA |EC |ENCRYPTED )?PRIVATE KEY-----/i.test(reply))return toast('ارسال کلید خصوصی ممنوع است','error');
  if(!confirm('پاسخ و وضعیت برای کاربر ثبت شود؟'))return;
  const bytes=new Uint8Array(16);crypto.getRandomValues(bytes);bytes[6]=(bytes[6]&15)|64;bytes[8]=(bytes[8]&63)|128;
  const hex=Array.from(bytes,x=>x.toString(16).padStart(2,'0')).join('');
  const request=hex.slice(0,8)+'-'+hex.slice(8,12)+'-'+hex.slice(12,16)+'-'+hex.slice(16,20)+'-'+hex.slice(20);
  supportPending={ticket,expected,status,reply,request};
 }
 btn.disabled=true;
 try{
  const result=await supportFetch('act',supportPending);
  if(result.status!=='applied'||result.request_id!==supportPending.request)throw Error('unconfirmed');
  supportPending=null;toast('عملیات ثبت شد');supportThreadPage=1;showPage('support');
 }catch(e){toast('عملیات تأیید نشد؛ همان تلاش را بررسی کنید. '+String(e.message),'error');showPage('support');}
 finally{btn.disabled=false;}
}
function supportAbandon(){if(confirm('ممکن است عملیات قبلی ثبت شده باشد. انصراف و تازه‌سازی؟')){supportPending=null;showPage('support');}}

async function openManagerConsent(owner){
 if(ADMIN_ROLE!=='admin'||!validUUID(owner))return toast('دسترسی مدیرکل لازم است','error');
 openModal('سابقه پذیرش قوانین','<div class="empty-note">در حال استعلام…</div>','<button class="btn btn-ghost" onclick="closeModal()">بستن</button>');
 try{
  const d=await supportFetch('consent',{owner});
  document.getElementById('modal-body').innerHTML=(d.acceptances||[]).map(a=>'<div class="panel" style="padding:12px"><b>نسخه '+html(a.legal_version)+'</b><p>'+html(safeTime(a.accepted_at))+'</p></div>').join('')||'<div class="empty-note">سابقه پذیرش ثبت نشده است (ممکن است حساب قدیمی باشد).</div>';
 }catch(e){document.getElementById('modal-body').textContent='استعلام ناموفق: '+parseError(e);}
}

let starterExpected=0,starterDraft=null;
async function renderStarter(c){
 if(ADMIN_ROLE!=='admin')throw Error('دسترسی مدیرکل لازم است');
 const d=await supportFetch('catalog-info',{}),v=d.catalog;
 starterExpected=v?Number(v.version):0;starterDraft=null;
 c.innerHTML='<div class="section-hero"><h2>بانک عمومی منتخب</h2><p>فقط فایل استخراج‌شده از بانک کنترل‌شده؛ انتشار به همه کاربران. شناسه اختصاصی مؤدیان تغییر نمی‌کند.</p></div>'+
 '<div class="panel" style="padding:16px"><h3>نسخه جاری: '+html(String(starterExpected))+'</h3><p>'+html(v?String(v.entries.length)+' شناسه | تاریخ منبع '+v.as_of:'هنوز داده تأییدشده منتشر نشده است')+'</p>'+
 '<label for="starter-json">محتوای JSON خروجی ابزار استخراج</label><textarea id="starter-json" rows="6" dir="ltr" style="width:100%" maxlength="900000" oninput="starterInvalidate()"></textarea><button class="action-btn" onclick="starterLoad()">کنترل فایل</button><p id="starter-preview"></p><div id="starter-rows" style="max-height:280px;overflow:auto"></div><button class="action-btn" id="starter-publish" disabled onclick="starterPublish()">انتشار پس از تأیید منبع</button></div>';
}
function starterInvalidate(){
 starterDraft=null;document.getElementById('starter-publish').disabled=true;
 document.getElementById('starter-preview').textContent='متن تغییر کرد؛ دوباره کنترل کنید.';
 document.getElementById('starter-rows').textContent='';
}
function starterLoad(){
 starterInvalidate();const b=document.getElementById('starter-publish'),p=document.getElementById('starter-preview');
 try{
  const text=document.getElementById('starter-json').value;
  if(!text||text.length>900000)throw Error('حجم نامعتبر');
  const d=JSON.parse(text);if(!Array.isArray(d.entries)||d.entries.length<150||d.entries.length>300||!/^[a-f0-9]{64}$/.test(d.source_hash)||!/^\d{4}-\d{2}-\d{2}$/.test(d.as_of))throw Error('ساختار منبع نامعتبر');
  const quotas={accounting:25,computer:30,technical:30,auto:40,other:25};
  const seen=new Set();for(const e of d.entries){if(!/^\d{13}$/.test(e.id)||seen.has(e.id)||!Object.prototype.hasOwnProperty.call(quotas,e.group)||!String(e.kind).includes('عموم'))throw Error('ردیف یا شناسه نامعتبر');seen.add(e.id);quotas[e.group]--;}
  if(Object.values(quotas).some(n=>n>0))throw Error('تعداد یکی از گروه‌ها کافی نیست');
  starterDraft={expected:starterExpected,source_hash:d.source_hash,as_of:d.as_of,entries:d.entries};
  p.textContent=d.entries.length+' شناسه یکتا؛ تاریخ منبع '+d.as_of+'؛ بررسی اعتبار و نرخ رسمی بر عهده مدیر پیش از انتشار است.';
  document.getElementById('starter-rows').innerHTML=d.entries.map(e=>'<p>'+html(e.id)+' | '+html(e.description)+' | '+html(e.vat||'نیازمند بررسی')+'</p>').join('');
  b.disabled=false;
 }catch(e){p.textContent='فایل رد شد: '+String(e.message);}
}
async function starterPublish(){
 if(!starterDraft||!confirm('منبع رسمی و نرخ‌ها را بررسی کرده‌اید و انتشار عمومی نسخه جدید را تأیید می‌کنید؟'))return;
 const b=document.getElementById('starter-publish');b.disabled=true;
 try{const d=await supportFetch('catalog-publish',starterDraft);if(d.status!=='published')throw Error('انتشار تأیید نشد');toast('نسخه '+d.version+' منتشر شد');showPage('starter');}
 catch(e){toast('انتشار تأیید نشد؛ نسخه جاری را دوباره بررسی کنید. '+String(e.message),'error');showPage('starter');}
}


/* Card receipts: admin-only; receipt is evidence, not proof of bank credit. */
let cardPage=1,cardSelected="";
async function cardFetch(route,body){
 const response=await fetch(SB_URL+'/functions/v1/sura-card-transfer/'+route,{method:'POST',headers:{apikey:SB_ANON,authorization:'Bearer '+USER_JWT,'content-type':'application/json'},body:JSON.stringify(body)});
 const data=await response.json();if(!response.ok||data.error)throw Error(data.error||'card_unavailable');return data;
}
function cardOpen(id){if(validUUID(id)){cardSelected=id;showPage('card-transfer');}}
function cardBack(){cardSelected="";showPage('card-transfer');}
function cardGo(page){cardPage=Math.max(1,page);cardBack();}
async function renderCardTransfers(c){
 if(!cardSelected){
  const d=await cardFetch('admin-list',{page:cardPage}),rows=d.requests||[];
  c.innerHTML='<div class="section-hero"><h2>رسیدهای کارت‌به‌کارت</h2><p>تنها پس از مشاهده واریز در گردش حساب بانکی تأیید کنید.</p></div>'+
   rows.map(r=>'<div class="panel"><b>'+html(String(r.amount_rial))+' ریال</b><p>'+html(r.kind==='monthly'?'اشتراک ماهانه':'اعتبار تکی')+' | '+html(r.tracking)+'</p><p>'+html(r.owner_id)+'</p><button class="action-btn" onclick="cardOpen(\''+html(r.id)+'\')">بررسی رسید</button></div>').join('')+
   (rows.length?'':'<div class="empty-note">درخواست در انتظار وجود ندارد.</div>')+
   '<button class="action-btn" '+(cardPage===1?'disabled':'')+' onclick="cardGo('+(cardPage-1)+')">قبلی</button> <button class="action-btn" '+(rows.length<30?'disabled':'')+' onclick="cardGo('+(cardPage+1)+')">بعدی</button>';
  return;
 }
 const d=await cardFetch('admin-detail',{id:cardSelected}),r=d.request;
 c.innerHTML='<div class="section-hero"><h2>تطبیق بانکی</h2><button class="action-btn" onclick="cardBack()">بازگشت</button></div><div class="panel"><p>مبلغ سفارش: '+html(String(r.amount_rial))+' ریال</p><p>شماره پیگیری اعلامی: '+html(r.tracking)+'</p><p>تاریخ: '+html(r.transfer_date)+'</p><p>کارت مقصد: '+'<bdi dir="ltr">'+html(String(r.destination_card).replace(/(.{4})(?=.)/g,'$1-'))+'</bdi>'+'</p>'+
  (typeof r.receipt==='string'&&/^[A-Za-z0-9+/=]+$/.test(r.receipt)?'<img alt="تصویر رسید" style="width:100%;max-width:600px" src="data:image/jpeg;base64,'+r.receipt+'">':'<p>تصویر قابل نمایش نیست.</p>')+
  (r.state==='pending'?'<div class="field-row"><label>مبلغ واقعی واریز (ریال)</label><input id="card-amount" inputmode="numeric"></div><div class="field-row"><label>شماره مرجع واقعی گردش حساب</label><input id="card-ref" inputmode="numeric" maxlength="40"></div><div class="field-row"><label>دلیل و توضیح بررسی</label><textarea id="card-reason" maxlength="500"></textarea></div><label><input type="checkbox" id="card-checked"> واریز واقعی را در حساب بانک بررسی کردم.</label><div><button class="action-btn" id="card-approve" onclick="cardReview(true)">تأیید و شارژ</button> <button class="action-btn" id="card-reject" onclick="cardReview(false)">رد رسید</button></div>':'<p>نتیجه: '+html(r.state)+'</p>')+'</div>';
}
async function cardReview(approve){
 const reason=document.getElementById('card-reason').value.trim();
 const amount=Number(document.getElementById('card-amount').value);
 const reference=document.getElementById('card-ref').value.trim();
 const checked=document.getElementById('card-checked').checked;
 if(reason.length<3||approve&&(!checked||!Number.isSafeInteger(amount)||amount<=0||!/^\d{4,40}$/.test(reference)))return toast('مشخصات تطبیق بانکی یا دلیل کامل نیست','error');
 if(!confirm(approve?'واریز واقعی بررسی شده و اعتبار شارژ شود؟':'درخواست رد شود؟'))return;
 document.getElementById('card-approve').disabled=true;document.getElementById('card-reject').disabled=true;
 try{await cardFetch('admin-review',{id:cardSelected,decision:approve?'approved':'rejected',amount:approve?amount:null,reference:approve?reference:null,bankChecked:checked,reason:reason});toast('نتیجه ثبت شد');cardBack();}
 catch(e){toast('نتیجه تأیید نشد: '+parseError(e)+'؛ مجدداً استعلام کنید','error');showPage('card-transfer');}
}


/* Monthly pricing: price changes never enable sales or mutate prior orders. */
let monthlyPriceSnapshot=null;
async function appendMonthlyPrice(c){
 if(ADMIN_ROLE!=='admin')return;
 try{
  const d=await cardFetch('admin-monthly-price',{}),p=d.plan;
  monthlyPriceSnapshot=p;
  const price=p.price_rial===null?null:Number(p.price_rial);
  const box=document.createElement('div');box.className='panel';
  box.innerHTML='<div class="panel-head"><h3>اشتراک ماهانه | ۲۰ صورتحساب در ۳۰ روز</h3></div><div style="padding:16px"><p>قیمت پایه: '+(price===null?'تعیین نشده':html(fmtRial(price)))+'</p><p>ارزش افزوده: '+(price===null?'—':html(fmtRial(Math.floor((price*10+50)/100))))+'</p><p>مبلغ نهایی: '+(price===null?'—':html(fmtRial(price+Math.floor((price*10+50)/100))))+'</p><p>وضعیت فروش: '+(p.enabled?'فعال':'غیرفعال')+'</p><button class="btn btn-accent" onclick="openMonthlyPrice()">تعیین یا تغییر قیمت اشتراک</button><p>تغییر قیمت فقط سفارش‌های جدید را متأثر می‌کند؛ این دکمه فروش را فعال نمی‌کند.</p><h4>تاریخچه تغییرات</h4>'+ (d.history||[]).map(h=>'<p>'+html(h.old_price===null?'تعیین اولیه':fmtRial(h.old_price))+' ← '+html(fmtRial(h.new_price))+' | '+html(safeTime(h.created_at))+' | '+html(h.reason)+'</p>').join('')+'</div>';
  c.appendChild(box);
 }catch(e){const box=document.createElement('div');box.className='panel';box.textContent='استعلام قیمت اشتراک: '+parseError(e);c.appendChild(box);}
}
function openMonthlyPrice(){
 if(ADMIN_ROLE!=='admin'||!monthlyPriceSnapshot)return;
 const p=monthlyPriceSnapshot;
 openModal('قیمت اشتراک ماهانه','<div class="field-row"><label>قیمت پایه به ریال</label><input type="number" id="monthly-price" min="1" max="10000000000" step="1" value="'+(p.price_rial===null?'':Number(p.price_rial))+'"></div><div class="field-row"><label>دلیل تغییر</label><textarea id="monthly-reason" maxlength="500"></textarea></div><p>مالیات ۱۰ درصد طبق تنظیم فعلی سرور محاسبه می‌شود. ظرفیت و مدت ثابت هستند.</p>','<button class="btn btn-ghost" onclick="closeModal()">انصراف</button><button id="modal-submit" class="btn btn-accent" onclick="saveMonthlyPrice()">ذخیره قیمت</button>');
 const m=document.getElementById('modal');m.dataset.monthlyRevision=String(p.revision);m.dataset.monthlyRequest=newUUID();
}
async function saveMonthlyPrice(){return submitBusy(async()=>{
 const m=document.getElementById('modal'),price=Number(document.getElementById('monthly-price').value),reason=document.getElementById('monthly-reason').value.trim();
 if(!Number.isSafeInteger(price)||price<1||price>10000000000||reason.length<5)return toast('قیمت یا دلیل معتبر نیست','error');
 if(!confirm('قیمت پایه '+fmtRial(price)+' و مبلغ نهایی '+fmtRial(price+Math.floor((price*10+50)/100))+' ثبت شود؟'))return;
 try{await cardFetch('admin-monthly-save',{request:m.dataset.monthlyRequest,expected:Number(m.dataset.monthlyRevision),price,reason});toast('قیمت اشتراک ثبت شد','success');closeModal();showPage('packages');}
 catch(e){toast('ذخیره تأیید نشد: '+parseError(e),'error');}
});}


/* Direct gift entry from selected account. Freeze the confirmed request across transport retries. */
let selectedGift=null;
function openSelectedGift(owner){
 if(ADMIN_ROLE!=='admin'||!CAN.adjustWallet()||!validUUID(owner))return toast('دسترسی مدیرکل لازم است','error');
 selectedGift=null;
 openModal('هدیه اعتبار به کاربر','<div class="empty-note">اعتبار رایگان به کیف‌پول مالک اضافه می‌شود؛ هیچ سفارش پرداختی ساخته نمی‌شود.</div><p dir="ltr">'+html(owner)+'</p><div class="field-row"><label>تعداد اعتبار هدیه</label><input type="number" id="selected-gift-units" min="1" max="1000000000" step="1"></div><div class="field-row"><label>دلیل هدیه</label><textarea id="selected-gift-reason" maxlength="490"></textarea></div>','<button class="btn btn-ghost" onclick="closeModal()">بستن</button><button class="btn btn-accent" id="modal-submit" onclick="saveSelectedGift(\''+html(owner)+'\')">ثبت هدیه</button>');
}
async function saveSelectedGift(owner){return submitBusy(async()=>{
 if(ADMIN_ROLE!=='admin'||!CAN.adjustWallet()||!validUUID(owner))return;
 try{
  if(!selectedGift){
   const units=Number(document.getElementById('selected-gift-units').value),reason=document.getElementById('selected-gift-reason').value.trim();
   if(!Number.isSafeInteger(units)||units<1||units>1000000000||reason.length<5||reason.length>490)return toast('تعداد یا دلیل هدیه معتبر نیست','error');
   const w=await adminFetch('wallet',{owner}),expected=Number(w.credits),balance=expected+units;
   if(!Number.isSafeInteger(expected)||expected<0||!Number.isSafeInteger(balance)||balance>1000000000)throw Error('invalid_balance');
   if(!confirm('اعطای '+fmtNum(units)+' اعتبار رایگان؛ موجودی جدید '+fmtNum(balance)+' واحد. تأیید می‌کنید؟'))return;
   selectedGift={owner,taxpayer:null,expected,balance,reason:'هدیه: '+reason,request:newUUID()};
   document.getElementById('selected-gift-units').disabled=true;
   document.getElementById('selected-gift-reason').disabled=true;
   document.getElementById('modal-submit').textContent='بررسی همان درخواست';
  }
  if(selectedGift.owner!==owner)throw Error('owner_changed');
  const r=await adminFetch('adjust',selectedGift);
  if(r.error||!r.receipt)throw Error(r.error||'missing_receipt');
  selectedGift=null;toast('اعتبار هدیه ثبت شد: '+r.receipt,'success');openManagerUser(owner);
 }catch(e){toast('نتیجه تأیید نشد؛ همان درخواست را بررسی کنید. '+parseError(e),'error');}
});}
