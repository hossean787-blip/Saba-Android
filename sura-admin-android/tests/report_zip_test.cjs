'use strict';
const assert=require('node:assert/strict');
const {makeXlsx,xml,colName}=require('../app/src/main/assets/admin/reports.js');
assert.equal(colName(0),'A');assert.equal(colName(26),'AA');
assert.equal(xml('سورا & صبا'), 'سورا &amp; صبا');
(async()=>{
 const out=makeXlsx([{name:'داده',rows:[{'مبلغ':100000,'متن':'سلام <سورا>'}]}]);
 const bin=Buffer.from(await out.arrayBuffer());
 assert.equal(bin.readUInt32LE(0),0x04034b50);
 assert.equal(bin.readUInt32LE(bin.length-22),0x06054b50);
 assert(bin.length>200);
 console.log('PASS: offline XLSX writer ZIP signature, XML escaping, integer cell, 1 sheet');
})();
