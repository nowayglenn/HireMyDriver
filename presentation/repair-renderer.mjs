import fs from 'node:fs';
const path=new URL('./android-renderer.js',import.meta.url);
let s=fs.readFileSync(path,'utf8');
s=s.replace("return value}\n", "return String(value).replace(/\\\\n/g,'\\n').replace(/\\\\'/g,\"'\")}\n");
// CardView clips its child background to its rounded outline.
s=s.replace("if(a.cardElevation)el.style.boxShadow", "if(/CardView/.test(t)){el.style.backgroundColor=color(a.cardBackgroundColor||'#FFFFFF');el.style.overflow='hidden'}if(a.cardElevation)el.style.boxShadow");
s=s.replace("if(!a.background&&!a.backgroundTint){", "if(!a.background&&!a.backgroundTint&&!/TextButton|OutlinedButton/.test(a.style||'')){");
const from=s.indexOf("else if(t==='LinearLayout'||t==='RadioGroup')");
const to=s.indexOf("else if(t==='ConstraintLayout')",from);
s=s.slice(0,from)+`else if(t==='LinearLayout'||t==='RadioGroup'){
  const vertical=a.orientation==='vertical';
  const visible=n.c.filter(c=>c.a.visibility!=='gone');
  let used=0,totalWeight=0;
  for(const c of visible){
    const cm=edges(c.a,'layout_margin'),weight=Number(c.a.layout_weight||0);
    totalWeight+=weight;
    const zeroWidth=!vertical&&weight>0&&c.a.layout_width==='0dp';
    const zeroHeight=vertical&&weight>0&&c.a.layout_height==='0dp';
    nativeMeasure(c,innerW,innerH,zeroWidth?0:null,zeroHeight?0:null);
    used+=vertical?c.box.h+cm.t+cm.b:c.box.w+cm.l+cm.r;
  }
  const available=vertical?(autoH&&forceH===null?used:innerH):(autoW&&forceW===null?used:innerW);
  if(totalWeight)for(const c of visible){
    const weight=Number(c.a.layout_weight||0);
    if(!weight)continue;
    const extra=(available-used)*weight/totalWeight;
    nativeMeasure(c,innerW,innerH,vertical?null:Math.max(0,c.box.w+extra),vertical?Math.max(0,c.box.h+extra):null);
  }
  const extent=visible.reduce((sum,c)=>{const cm=edges(c.a,'layout_margin');return sum+(vertical?c.box.h+cm.t+cm.b:c.box.w+cm.l+cm.r)},0);
  let cursor=0;
  const gravity=a.gravity||'';
  if(!vertical&&!autoW){if(/end|right/.test(gravity))cursor=Math.max(0,innerW-extent);else if(/center_horizontal|^center$/.test(gravity))cursor=Math.max(0,(innerW-extent)/2)}
  for(const c of visible){
    const cm=edges(c.a,'layout_margin'),cg=c.a.layout_gravity||gravity;
    c.box.x=p.l+(vertical?cm.l+(/center_horizontal|^center$/.test(cg)?Math.max(0,(innerW-c.box.w-cm.l-cm.r)/2):/right|end/.test(cg)?Math.max(0,innerW-c.box.w-cm.l-cm.r):0):cursor+cm.l);
    c.box.y=p.t+(vertical?cursor+cm.t:cm.t+(/center_vertical|^center$/.test(cg)?Math.max(0,(innerH-c.box.h-cm.t-cm.b)/2):0));
    cursor+=vertical?c.box.h+cm.t+cm.b:c.box.w+cm.l+cm.r;
    contentW=Math.max(contentW,c.box.x+c.box.w+cm.r-p.l);
    contentH=Math.max(contentH,c.box.y+c.box.h+cm.b-p.t);
  }
  if(!vertical&&autoH&&forceH===null)for(const c of visible){const cg=c.a.layout_gravity||gravity;if(/center_vertical|^center$/.test(cg))c.box.y=p.t+Math.max(0,(contentH-c.box.h)/2)}
}
`+s.slice(to);
s=s.replace("w=Math.min(maxW,contentW+p.l+p.r+m.l+m.r)","w=Math.min(Math.max(0,maxW-m.l-m.r),contentW+p.l+p.r)");
// Preserve the allocated dimensions when measuring weighted nested containers.
s=s.replace("const autoW=declaredW==='wrap_content',autoH=declaredH==='wrap_content'", "const autoW=declaredW==='wrap_content',autoH=declaredH==='wrap_content'");
fs.writeFileSync(path,s);
