import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const here=path.dirname(fileURLToPath(import.meta.url));
const root=path.resolve(here,'..');
const assets={};
for(const dir of ['drawable','drawable-nodpi'])for(const file of fs.readdirSync(path.join(root,'app/src/main/res',dir))){
  if(!/\.(png|jpg)$/.test(file))continue;
  assets[path.parse(file).name]=`data:image/${file.endsWith('.jpg')?'jpeg':'png'};base64,`+fs.readFileSync(path.join(root,'app/src/main/res',dir,file)).toString('base64');
}
const bohol=JSON.parse(fs.readFileSync(path.join(root,'app/src/main/assets/service_areas.json'),'utf8')).filter(x=>x.province==='Bohol').map(x=>x.name.replace(/^City of /,'')).sort();
const assetScript='const ASSETS='+JSON.stringify(assets)+';\nconst BOHOL='+JSON.stringify(bohol)+';';
fs.writeFileSync(path.join(here,'assets.js'),assetScript);
let html=fs.readFileSync(path.join(here,'index.html'),'utf8');
html=html.replace('<link rel="stylesheet" href="presentation.css">',()=>'<style>'+fs.readFileSync(path.join(here,'presentation.css'),'utf8')+'</style>');
html=html.replace('<script src="assets.js"></script>',()=>'<script>'+assetScript+'</script>');
html=html.replace('<script src="app.js"></script>',()=>'<script>'+fs.readFileSync(path.join(here,'app.js'),'utf8').replace(/<\/script/gi,'<\\/script')+'</script>');
for(const file of ['android-resources.js','android-renderer.js','android-screens.js','android-flow.js','android-polish.js','android-followup.js'])html=html.replace(`<script src="${file}"></script>`,()=>'<script>'+fs.readFileSync(path.join(here,file),'utf8').replace(/<\/script/gi,'<\\/script')+'</script>');
fs.writeFileSync(path.join(here,'HireMyDriver-Presentation.html'),html);
console.log(`Built standalone HTML: ${Object.keys(assets).length} original assets, ${bohol.length} Bohol areas, ${(Buffer.byteLength(html)/1024/1024).toFixed(1)} MB.`);
