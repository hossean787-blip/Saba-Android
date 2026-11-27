// Regenerate the deployable Deno API bundle from its TypeScript source.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),ts=require('typescript');
const root=path.resolve(__dirname,'..'),sourcePath=path.join(root,'backend/sura-manager-api.ts'),outputPath=path.join(root,'backend/sura-manager-api.compiled.js');
const source=fs.readFileSync(sourcePath,'utf8');
const digest=crypto.createHash('sha256').update(source).digest('hex');
const result=ts.transpileModule(source,{fileName:'sura-manager-api.ts',compilerOptions:{target:ts.ScriptTarget.ES2017,module:ts.ModuleKind.None,newLine:ts.NewLineKind.LineFeed,removeComments:false},reportDiagnostics:true});
const errors=(result.diagnostics||[]).filter(d=>d.category===ts.DiagnosticCategory.Error);
if(errors.length)throw new Error('SURA Manager API transpilation failed: '+errors.map(d=>ts.flattenDiagnosticMessageText(d.messageText,' ')).join('; '));
fs.writeFileSync(outputPath,'// source-sha256:'+digest+'\n'+result.outputText,'utf8');
console.log('Compiled SURA Manager API; source SHA-256 '+digest);
