// Developer build helper: Node with TypeScript 5+ installed (not needed for APK install).
const fs=require('node:fs'),path=require('node:path'),ts=require('typescript');
const base=path.resolve(__dirname,"..");
const src=path.join(base,'sources'),dist=path.join(base,'app/src/main/assets/admin');
const options={allowJs:true,target:ts.ScriptTarget.ES2017,module:ts.ModuleKind.None,newLine:ts.NewLineKind.LineFeed};
function transpile(text,filename){
 const result=ts.transpileModule(text,{fileName:filename,compilerOptions:options,reportDiagnostics:true});
 if((result.diagnostics||[]).some(d=>d.category===ts.DiagnosticCategory.Error))throw new Error('TypeScript transpilation failed '+filename+JSON.stringify(result.diagnostics));
 return result.outputText;
}
let source=fs.readFileSync(path.join(src,'index.source.html'),'utf8');
const s=/<script>([\s\S]*?)<\/script>/g;
let count=0;
source=source.replace(s,(all,code)=>{count++;return '<script>\n'+transpile(code,'admin-panel.js')+'\n</script>';});
if(count!==1)throw new Error('Expected exactly one panel inline script, got '+count);
fs.writeFileSync(path.join(dist,'index.html'),source,'utf8');
fs.writeFileSync(path.join(dist,'reports.js'),transpile(fs.readFileSync(path.join(src,'reports.source.js'),'utf8'),'reports.js'),'utf8');
fs.writeFileSync(path.join(dist,'manager.js'),transpile(fs.readFileSync(path.join(src,'manager.source.js'),'utf8'),'manager.js'),'utf8');
console.log('Transpiled WebView scripts to ES2017 (works with older Android System WebView).');
