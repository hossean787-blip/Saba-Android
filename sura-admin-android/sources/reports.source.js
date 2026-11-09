/* SURA Manager, self-contained XLSX export with zero remote script dependencies. */
(function(root) {
'use strict';
const utf8 = value => new TextEncoder().encode(String(value));
const xml = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&apos;'}[c]));
function colName(n) { let result=''; for(n++;n>0;n=Math.floor((n-1)/26)) result=String.fromCharCode(65+(n-1)%26)+result; return result; }
function sheetXml(rows) {
  const values = [Object.keys(rows[0]||{}), ...rows.map(r=>Object.values(r))];
  const table = values.map((row,i) => `<row r="${i+1}">` + row.map((value,j) => {
    const addr = colName(j)+(i+1);
    if (typeof value === 'number' && Number.isFinite(value)) return `<c r="${addr}"><v>${value}</v></c>`;
    return `<c r="${addr}" t="inlineStr"><is><t xml:space="preserve">${xml(value)}</t></is></c>`;
  }).join('') + '</row>').join('');
  return `<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>${table}</sheetData></worksheet>`;
}
const crcTable = new Uint32Array(256);
for (let n=0;n<256;n++) {let c=n;for(let j=0;j<8;j++)c=(c&1)?0xedb88320^(c>>>1):c>>>1;crcTable[n]=c>>>0;}
function crc32(bytes) {let c=0xffffffff;for(const b of bytes)c=crcTable[(c^b)&255]^(c>>>8);return(c^0xffffffff)>>>0;}
function zipFiles(entries) {
  let offset=0; const pieces=[],central=[];const put=b=>{pieces.push(b);offset+=b.length;};
  for(const [path,body] of entries){
    const file=utf8(path), bytes=utf8(body), crc=crc32(bytes), localOffset=offset;
    const local=new Uint8Array(30+file.length), v=new DataView(local.buffer);
    v.setUint32(0,0x04034b50,true);v.setUint16(4,20,true);v.setUint16(6,0x0800,true);
    v.setUint32(14,crc,true);v.setUint32(18,bytes.length,true);v.setUint32(22,bytes.length,true);v.setUint16(26,file.length,true);
    local.set(file,30);put(local);put(bytes);
    const center=new Uint8Array(46+file.length),cv=new DataView(center.buffer);
    cv.setUint32(0,0x02014b50,true);cv.setUint16(4,20,true);cv.setUint16(6,20,true);cv.setUint16(8,0x0800,true);
    cv.setUint32(16,crc,true);cv.setUint32(20,bytes.length,true);cv.setUint32(24,bytes.length,true);
    cv.setUint16(28,file.length,true);cv.setUint32(42,localOffset,true);center.set(file,46);central.push(center);
  }
  const centralOffset=offset;for(const c of central)put(c);
  const footer=new Uint8Array(22),fv=new DataView(footer.buffer);
  fv.setUint32(0,0x06054b50,true);fv.setUint16(8,central.length,true);fv.setUint16(10,central.length,true);
  fv.setUint32(12,offset-centralOffset,true);fv.setUint32(16,centralOffset,true);put(footer);
  const result=new Uint8Array(offset);let n=0;for(const part of pieces){result.set(part,n);n+=part.length;}
  return result;
}
function makeXlsx(sheets) {
  if (!Array.isArray(sheets) || sheets.length===0 || sheets.length>5)throw Error('Invalid sheets');
  const rels='<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>';
  const wsLinks=sheets.map((sheet,i)=>`<Relationship Id="rId${i+1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${i+1}.xml"/>`).join('');
  const ws=sheets.map((sheet,i)=>`<sheet name="${xml(sheet.name).slice(0,31)}" sheetId="${i+1}" r:id="rId${i+1}"/>`).join('');
  const files=[
    ['[Content_Types].xml',`<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>${sheets.map((s,i)=>`<Override PartName="/xl/worksheets/sheet${i+1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>`).join('')}</Types>`],
    ['_rels/.rels',rels],
    ['xl/workbook.xml',`<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>${ws}</sheets></workbook>`],
    ['xl/_rels/workbook.xml.rels',`<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">${wsLinks}</Relationships>`],
    ...sheets.map((sheet,i)=>[`xl/worksheets/sheet${i+1}.xml`,sheetXml(sheet.rows||[])])
  ];
  return new Blob([zipFiles(files)],{type:'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'});
}
function download(blob,name){
  if(root.SuraNative && typeof root.SuraNative.saveFile==='function'){
    const reader=new FileReader();
    reader.onload=()=>{
      const base64=String(reader.result||'').split(',')[1]||'';
      root.SuraNative.saveFile(name,blob.type.split(';')[0],base64);
    };
    reader.onerror=()=>{ if(root.toast)root.toast('ذخیره گزارش انجام نشد','error'); };
    reader.readAsDataURL(blob);
    return;
  }
  const u=URL.createObjectURL(blob),a=document.createElement('a');
  a.download=name;a.href=u;document.body.appendChild(a);a.click();a.remove();setTimeout(()=>URL.revokeObjectURL(u),1500);
}
root.SuraReports={makeXlsx,download};
if(typeof module!=='undefined'&&module.exports)module.exports={makeXlsx,zipFiles,xml,colName,download};
})(typeof window!=='undefined'?window:globalThis);
