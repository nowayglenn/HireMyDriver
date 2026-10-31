import fs from 'node:fs';
const p=new URL('./app.js',import.meta.url);let s=fs.readFileSync(p,'utf8');
s=s.replace("available:true,tab:'Pending'", "available:true,tab:'All'").replaceAll("role==='driver'?'Requests':'Pending'", "role==='driver'?'Requests':'All'");
s=s.replace("['Requests','Upcoming','In Progress','Completed','Cancelled']:['Pending','Upcoming','In Progress','Completed','Cancelled']", "['Requests','Upcoming','Completed']:['All','Pending','Accepted','Completed','Cancelled']");
s=s.replace("const match={Requests:", "const match={All:['Pending','Accepted','On the way','Arrived','In Progress','Completed','Cancelled','Declined'],Accepted:['Accepted','On the way','Arrived','In Progress'],Requests:");
fs.writeFileSync(p,s);
const r=new URL('./android-renderer.js',import.meta.url);s=fs.readFileSync(r,'utf8');
s=s.replace("if((a.shapeAppearanceOverlay||'').includes('Circle')){el.style.borderRadius='50%';el.style.overflow='hidden'}", "if(a.shapeAppearanceOverlay){const shape=styleAttrs(a.shapeAppearanceOverlay),radius=shape.cornerSize||((a.shapeAppearanceOverlay||'').includes('Circle')?'50%':'12dp');el.style.borderRadius=radius.includes('%')?radius:dp(radius)+'px';el.style.overflow='hidden'}");
fs.writeFileSync(r,s);
