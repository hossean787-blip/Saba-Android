#!/usr/bin/env python3
"""Extract a small public bank from the verified SURA database, never fabricate IDs.
Requires explicit source as-of date. Fails closed if a group cannot meet its quota.
"""
import argparse, hashlib, json, pathlib, sqlite3, re, datetime, decimal
QUOTAS={'accounting':25,'computer':30,'technical':30,'auto':40,'other':25}
KNOWN={'02530f58da28ab0d7846473b8e460cfe9a13c016468e8cffc07d2b3e7bc06fd1','58c3c63064508892333bfdfcf7e262bf489318070920f1762e51010bf6659fc8','567fbba0e54a863879c790a848866a7e16d48ad272c66e286172f87b67fd0e9f'}
KEYS={'accounting':['حسابداری','حسابرسی','مشاوره مالیاتی','تنظیم اظهارنامه','حقوق و دستمزد','خدمات مالی'],
'computer':['رایانه','کامپیوتر','لپ تاپ','صفحه کلید','چاپگر','ماوس','مانیتور','پردازنده','حافظه','تجهیزات شبکه'],
'technical':['مشاوره','تعمیر','نگهداری','نصب','مهندسی','طراحی','پشتیبانی نرم افزار'],
'auto':['خودرو','اتومبیل','لنت ترمز','فیلتر روغن','قطعات یدکی','شمع موتور','کمک فنر','دیسک ترمز','لاستیک'],
'other':['خدمات','حمل و نقل','آموزش','نظافت','چاپ','بسته بندی','تبلیغات']}
def normalize(s):return str(s or '').replace('ي','ی').replace('ك','ک').replace('\u200c',' ')
def choose(db):
 groups={k:[] for k in QUOTAS};seen=set()
 for g,terms in KEYS.items():
  selected=groups[g]
  # Separate candidate buckets to provide variety; only public entries and exact source fields.
  candidates=[]
  for term in terms:
   rows=db.execute("select c.id,c.description,c.vat,c.taxable,c.kind,c.expiration_date,s.code from catalog c join sources s on s.source_id=c.source_id where c.kind like '%عموم%' and replace(replace(c.description,'ي','ی'),'ك','ک') like ? order by length(c.description),c.id limit 250",('%'+term+'%',)).fetchall()
   candidates.append(iter(rows))
  while len(selected)<QUOTAS[g]:
   advanced=False
   for it in candidates:
    if len(selected)>=QUOTAS[g]:break
    for row in it:
     id,description,vat,taxable,kind,expiry,source=map(lambda v:str(v or ''),row)
     if id in seen or not re.fullmatch(r'[0-9]{13}',id) or not description.strip():continue
     if ('خدمت' in kind)!=(g in ('accounting','technical','other')):continue
     vat=vat.strip().rstrip('%').strip()
     try:
      if vat and (not re.fullmatch(r'[0-9]{1,3}(\.[0-9]{1,4})?',vat) or not 0<=decimal.Decimal(vat)<=100):continue
     except decimal.InvalidOperation:continue
     # Expiration is retained, not guessed. Exclude rows with any expiration until manually checked.
     if expiry.strip():continue
     selected.append(dict(id=id,description=description,vat=vat,taxable=taxable,kind=kind,expiration_date=expiry,source=source,group=g))
     seen.add(id);advanced=True;break
   if not advanced:break
  if len(selected)!=QUOTAS[g]:raise SystemExit(f'{g}: only {len(selected)} eligible records; no partial bank written')
 return [e for rows in groups.values() for e in rows]
def main():
 p=argparse.ArgumentParser();p.add_argument('db',type=pathlib.Path);p.add_argument('--as-of',required=True);p.add_argument('--out',type=pathlib.Path,default=pathlib.Path('app/src/main/assets/starter/catalog.json'));p.add_argument('--version',type=int,default=1);a=p.parse_args()
 assert 1<=a.version<=1000000000 and datetime.date.fromisoformat(a.as_of)<=datetime.date.today()
 with a.db.open('rb') as f:h=hashlib.file_digest(f,'sha256').hexdigest()
 if h not in KNOWN:raise SystemExit('Source database hash not in the controlled SURA archive')
 with sqlite3.connect(a.db.resolve().as_uri()+'?mode=ro',uri=True) as db:entries=choose(db)
 result=dict(version=a.version,source_hash=h,as_of=a.as_of,entries=entries)
 a.out.parent.mkdir(parents=True,exist_ok=True);tmp=a.out.with_suffix('.partial');tmp.write_text(json.dumps(result,ensure_ascii=False,separators=(',',':')),encoding='utf8');tmp.replace(a.out)
 print('Prepared',len(entries),'unique source records; official validity and group relevance must be reviewed before publishing.')
if __name__=='__main__':main()
