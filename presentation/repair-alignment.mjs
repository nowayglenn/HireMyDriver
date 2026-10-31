import fs from 'node:fs';
const p=new URL('./android-renderer.js',import.meta.url);let s=fs.readFileSync(p,'utf8');
s=s.replace("tag:n.tag.split('.').at(-1)","tag:n.tag.split('.').at(-1).replace(/^AppCompat(?=ImageButton|ImageView|TextView|EditText)/,'')");
s=s.replace("const g=c.a.layout_gravity||'';", "const g=(autoW&&forceW===null||autoH&&forceH===null)?'':c.a.layout_gravity||'';");
s=s.replace("n.box={x:n.box?.x||0,y:n.box?.y||0,w:Math.max(0,w),h:Math.max(0,h)};return n.box", "n.box={x:n.box?.x||0,y:n.box?.y||0,w:Math.max(0,w),h:Math.max(0,h)};if(autoW&&forceW===null&&n.c.length)return nativeMeasure(n,maxW,maxH,n.box.w,autoH?n.box.h:forceH);return n.box");
fs.writeFileSync(p,s);
