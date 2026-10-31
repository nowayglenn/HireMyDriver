import fs from 'node:fs';import vm from 'node:vm';
const ctx={};vm.createContext(ctx);vm.runInContext(fs.readFileSync(new URL('./android-resources.js',import.meta.url),'utf8')+';globalThis.resources=ANDROID;',ctx);
for(const name of process.argv.slice(2)){console.log('\n'+name);const walk=(n,level=0)=>{console.log(' '.repeat(level)+n.tag.split('.').at(-1)+' '+(n.a.id||'')+' '+(n.a.text||'')+' ['+n.a.layout_width+','+n.a.layout_height+'] '+(n.a.visibility||'')+' '+(n.a.layout_weight||''));n.c.forEach(c=>walk(c,level+1))};walk(ctx.resources.layouts[name]);}
