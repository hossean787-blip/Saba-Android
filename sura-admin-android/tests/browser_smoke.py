from playwright.sync_api import sync_playwright
from pathlib import Path
import json
from urllib.parse import urlsplit
from http.server import ThreadingHTTPServer, SimpleHTTPRequestHandler
from threading import Thread
from functools import partial

ROOT=Path(__file__).resolve().parents[1]
owner='550e8400-e29b-41d4-a716-446655440000'; actor='bb668800-e29b-41d4-a716-446655440111'
requests=[]
errors=[]

TABLES={
 'sura_payment_orders':[{'id':'240f8400-e29b-41d4-a716-446655440000','owner_user_id':owner,'state':'verified','amount_rial':200000,'credit_units':2,'created_at':'2026-11-09T10:00:00Z','gateway':'zarinpal','authority':'A000123','ref_id':'REF-01'}],
 'sura_owner_wallets':[{'owner_id':owner,'credits':10,'updated_at':'2026-11-09T10:00:00Z'}],
 'sura_taxpayer_wallets':[{'owner_id':owner,'local_taxpayer_id':2,'credits':3,'updated_at':'2026-11-09T10:00:00Z'}],
 'sura_credit_grants':[{'id':'99cf8400-e29b-41d4-a716-446655440000','order_id':'240f8400-e29b-41d4-a716-446655440000','owner_user_id':owner,'units':2,'created_at':'2026-11-09T10:00:00Z'}],
 'sura_credit_transfers':[],
 'sura_promo_redemptions':[{'id':1,'account_id':owner,'credits_granted':3,'credits_remaining':2,'redeemed_at':'2026-11-09T10:00:00Z'}],
 'sura_promo_invoice_uses':[],
 'sura_credit_pricing':[{'id':1,'price_per_invoice_rial':100000,'currency':'IRR','updated_at':'2026-11-09T10:00:00Z'}],
 'sura_admin_credit_adjustments':[{'request_id':'310f8400-e29b-41d4-a716-446655440000','actor_user_id':actor,'owner_id':owner,'local_taxpayer_id':None,'before_credits':8,'after_credits':10,'delta':2,'reason':'اعتبار هدیه تست','created_at':'2026-11-09T10:00:00Z'}],
}

def handler(route):
 url=route.request.url
 if 'appassets.androidplatform.net' in url:
  local=ROOT/'app/src/main/assets/admin'
  if url.endswith('index.html'):route.fulfill(status=200,content_type='text/html',body=(local/'index.html').read_bytes());return
  if url.endswith('reports.js'):route.fulfill(status=200,content_type='application/javascript',body=(local/'reports.js').read_bytes());return
  if url.endswith('manager.js'):route.fulfill(status=200,content_type='application/javascript',body=(local/'manager.js').read_bytes());return
  route.fulfill(status=403,body='');return
 if 'googleapis' in url or 'gstatic' in url or 'cdnjs' in url:
  route.abort();return
 if '.supabase.co' not in url:
  route.continue_();return
 path=urlsplit(url).path
 if path.startswith('/auth/v1/token'):
  route.fulfill(headers={'access-control-allow-origin':'*'},json={'access_token':'mock-jwt','refresh_token':'mock-refresh','expires_in':3600},status=200);return
 if path.endswith('/me'):
  route.fulfill(headers={'access-control-allow-origin':'*'},json={'role':'admin','email':'admin@test.example','id':actor});return
 body=route.request.post_data_json or {}
 if '/sura-manager-api/' in path:
  endpoint=path.split('/')[-1]
  manager={
   'summary':{'summary':{'verified_revenue_rial':'200000','verified_orders':1,'requested_orders':2,'failed_orders':0,'credits_sold':'2','owner_wallets':1,'taxpayer_wallets':1,'promo_remaining':'2','promo_redemptions':1,'taxpayer_credit_balance':'3','paid_invoice_uses':1}},
   'users':{'users':[{'id':owner,'email':'customer@example.com','verified':True,'created_at':'2026-11-08T10:00:00Z','last_sign_in_at':'2026-11-09T10:00:00Z','role':None}], 'page':1,'nextPage':None},
   'user':{'user':{'id':owner,'email':'customer@example.com','verified':True,'created_at':'2026-11-08T10:00:00Z','role':None},'wallet':{'credits':10},'taxpayers':[{'local_taxpayer_id':2,'credits':3}],'orders':TABLES['sura_payment_orders']},
   'role-list':{'roles':[{'user_id':actor,'email':'admin@test.example','role':'admin','updated_at':'2026-11-08T10:00:00Z'}],'history':[]},
   'role-assign':{'status':'applied','receipt':'receipt-role-1'},
   'orders':{'orders':TABLES['sura_payment_orders'],'page':1,'perPage':40,'total':1},
   'health':{'status':'ok','database':'ok','role':'admin','server_time':'2026-11-10T00:00:00Z','price':{'price_per_invoice_rial':100000}},
   'audit':{'rows':TABLES['sura_admin_credit_adjustments'],'total':1,'page':1,'kind':body.get('kind','credit')},
   'price-history':{'history':[]},
   'price-update':{'status':'applied','receipt':'receipt-price-1'},
  }
  if endpoint in ('role-assign','price-update'): requests.append((endpoint,body))
  route.fulfill(headers={'access-control-allow-origin':'*'},json=manager.get(endpoint,{'error':'not_found'}),status=200 if endpoint in manager else 404);return
 if path.endswith('/query'):
  route.fulfill(headers={'access-control-allow-origin':'*'},json=TABLES.get(body.get('table'), []));return
 if path.endswith('/wallet'):
  route.fulfill(headers={'access-control-allow-origin':'*'},json={'credits':10});return
 if path.endswith('/adjust'):
  requests.append(('adjust',body))
  route.fulfill(headers={'access-control-allow-origin':'*'},json={'status':'applied','balance':body.get('balance',0),'receipt':body.get('request')});return
 if path.endswith('/rpc'):
  requests.append(('rpc',body))
  route.fulfill(headers={'access-control-allow-origin':'*'},json={'status':'transferred','receipt':'310f8400-e29b-41d4-a716-446655440000'});return
 if path.endswith('/mutate'):
  requests.append(('mutate',body))
  route.fulfill(headers={'access-control-allow-origin':'*'},json=[{'id':1}]);return
 route.fulfill(status=404,json={'error':'missing_route'})

with sync_playwright() as p:
 browser=p.chromium.launch(headless=True,executable_path="/usr/bin/chromium",args=["--no-sandbox"])
 page=browser.new_page(viewport={'width':1440,'height':900}, locale='fa-IR',accept_downloads=True)
 page.route('**/*',handler)
 page.on('pageerror', lambda err: errors.append(str(err)))
 page.on('dialog', lambda d: d.accept())
 html_text=(ROOT/'app/src/main/assets/admin/index.html').read_text()
 report_js=(ROOT/'app/src/main/assets/admin/reports.js').read_text()
 manager_js=(ROOT/'app/src/main/assets/admin/manager.js').read_text()
 assert '<script src="/assets/admin/reports.js"></script>' in html_text
 assert '<script src="/assets/admin/manager.js"></script>' in html_text
 html_text=html_text.replace('<script src="/assets/admin/reports.js"></script>','<script>'+report_js+'</script>')
 html_text=html_text.replace('<script src="/assets/admin/manager.js"></script>','<script>'+manager_js+'</script>')
 page.set_content(html_text,wait_until='domcontentloaded')
 assert page.locator('#inp-url').count()==1
 assert page.evaluate('typeof SuraReports.makeXlsx') == 'function'
 assert page.evaluate("fmtRial('9007199254740993')")=='۹٬۰۰۷٬۱۹۹٬۲۵۴٬۷۴۰٬۹۹۳ ریال'
 assert page.locator('#inp-anon').input_value().startswith('sb_publishable_')
 page.locator('#inp-email').fill('admin@test.example')
 page.locator('#inp-password').fill('Pass123456!')
 page.get_by_role('button',name='ورود به پنل').click()
 page.locator('#app').wait_for(state='visible')
 page.locator('.stat-card').first.wait_for()
 page.screenshot(path=str(ROOT/'tests/desktop.png'),full_page=True)
 assert page.locator('html').get_attribute('data-theme')=='dark'
 page.locator('#theme-toggle').click()
 assert page.locator('html').get_attribute('data-theme')=='light'
 page.locator('#theme-toggle').click()
 assert page.get_by_text('۲۰۰٬۰۰۰',exact=False).count()>0
 page.evaluate("showPage('directory')")
 page.get_by_text('customer@example.com').wait_for()
 page.get_by_role('button',name='جزئیات حساب').click()
 page.get_by_text('اعتبار مؤدیان').last.wait_for()
 page.locator('#modal-overlay').locator('.modal-close').click()
 page.evaluate("showPage('roles')")
 page.get_by_text('مدیران و سطح دسترسی').wait_for()
 page.get_by_role('button',name='تغییر نقش').click()
 page.locator('#manager-role-email').fill('admin@test.example')
 page.locator('#manager-role-choice').select_option('editor')
 page.locator('#manager-role-reason').fill('تغییر سطح مدیریت برای آزمون')
 page.locator('#modal-submit').click()
 page.locator('#modal-overlay').wait_for(state='hidden')
 assert any(x[0]=='role-assign' and x[1].get('role')=='editor' and x[1].get('confirmEmail')=='admin@test.example' for x in requests)
 page.evaluate("showPage('health')")
 page.get_by_text('وضعیت سامانه').last.wait_for()
 page.get_by_text('پایگاه داده',exact=True).wait_for()
 page.evaluate("showPage('audit')")
 page.get_by_text('دفتر ممیزی غیرقابل ویرایش').wait_for()
 page.evaluate("showPage('payments')")
 page.get_by_text('دفتر پرداخت‌ها').wait_for()
 page.evaluate("showPage('users')")
 page.get_by_text('کیف‌پول کاربران').wait_for()
 page.get_by_role('button',name='اصلاح').first.click()
 page.locator('#adj-new').fill('12')
 page.locator('#adj-reason').fill('اصلاح حساب نمونه')
 page.get_by_role('button',name='ثبت اصلاح').click()
 page.get_by_text('کیف‌پول کاربران').wait_for()
 assert any(x[0]=='adjust' and x[1].get('balance')==12 for x in requests)
 page.locator('#modal-overlay').wait_for(state='hidden')
 page.evaluate("showPage('users')")
 page.get_by_role('button',name='انتقال').first.click()
 page.locator('#m-taxpayer').fill('2');page.locator('#m-amount').fill('2')
 page.get_by_role('button',name='تأیید انتقال').click()
 assert any(x[0]=='rpc' for x in requests)
 page.locator('#modal-overlay').wait_for(state='hidden')
 page.evaluate("showPage('packages')")
 page.get_by_role('button',name='ویرایش قیمت پایه').click()
 page.locator('#pkg-price').fill('120000')
 page.locator('#pkg-reason').fill('به‌روزرسانی قیمت پس از بررسی رسمی')
 page.get_by_role('button',name='ذخیره قیمت').click()
 page.locator('#modal-overlay').wait_for(state='hidden')
 assert any(x[0]=='price-update' and x[1].get('price')==120000 for x in requests)
 page.evaluate("showPage('credits')")
 page.get_by_text('دفتر ممیزی اصلاحات مدیرکل').wait_for()
 assert page.get_by_text('اعتبار هدیه تست').count()==1
 page.evaluate("showPage('reports')")
 page.get_by_text('گردش مالی ماهانه').wait_for()
 assert 'مالیات ۱۰٪' not in page.locator('#content').inner_text()
 with page.expect_download() as d_csv:
  page.locator('button').filter(has_text='CSV').click()
 d_csv.value.save_as(str(ROOT/'tests/sample.csv'))
 with page.expect_download() as d_xlsx:
  page.locator('button').filter(has_text='اکسل').click()
 d_xlsx.value.save_as(str(ROOT/'tests/sample.xlsx'))
 page.screenshot(path=str(ROOT/'tests/reports.png'),full_page=True)
 page.set_viewport_size({'width':390,'height':844})
 page.evaluate("showPage('dashboard')")
 page.locator('#mobile-menu').click()
 page.wait_for_timeout(350)
 assert page.locator('body').evaluate("el=>el.classList.contains('drawer-open')")
 page.screenshot(path=str(ROOT/'tests/mobile-menu.png'),full_page=True)
 page.get_by_text('کاربران و مودیان').first.click()
 page.wait_for_timeout(350)
 page.get_by_text('کیف‌پول کاربران').wait_for()
 assert not page.locator('body').evaluate("el=>el.classList.contains('drawer-open')")
 page.screenshot(path=str(ROOT/'tests/mobile-users.png'),full_page=True)
 page.evaluate("ADMIN_ROLE='viewer';managerRoles();showPage('users')")
 page.get_by_text('کیف‌پول کاربران').wait_for()
 assert page.locator('#owner-table button').count()==0
 assert page.locator('[data-min-role="admin"]').first.evaluate("el=>el.style.display")=='none'
 assert page.locator('[data-min-role="staff"]').first.evaluate("el=>el.style.display")=='none'
 assert not errors, errors
 print('PASS: login, directory, user details, roles, health, audit, paged payments, price audit, wallet, CSV/XLSX, mobile')
 print('Requests:',[(a,b.get('table',b.get('owner'))) for a,b in requests])
 browser.close()

