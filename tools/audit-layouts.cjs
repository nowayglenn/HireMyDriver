// Read-only resource audit; run with node tools/audit-layouts.cjs.
const fs = require('node:fs');
const path = require('node:path');
function parse(source) {
  const nodes = [], stack = [];
  for (const match of source.matchAll(/<!--[\s\S]*?-->|<[^>]+>/g)) {
    const text = match[0];
    if (/^<[!?]/.test(text)) continue;
    if (text.startsWith('</')) { const n = stack.pop(); if(n) n.end = match.index + text.length; continue; }
    const node = { text, start: match.index, original: text, tag: text.match(/^<([^\s/>]+)/)[1], parent: stack.at(-1), children: [] };
    node.parent?.children.push(node); nodes.push(node);
    if (!text.endsWith('/>')) stack.push(node); else node.end = match.index + text.length;
  }
  return nodes;
}
const get = (n, key) => n.text.match(new RegExp('\\b' + key + '="([^"]*)"'))?.[1];
const id = n => get(n, 'android:id')?.split('/')[1];
function layouts() {
  return fs.readdirSync('app/src/main').filter(n => n.startsWith('res')).flatMap(n => {
    const dir = `app/src/main/${n}/layout`;
    return fs.existsSync(dir) ? fs.readdirSync(dir).filter(f => f.endsWith('.xml')).map(f => path.join(dir,f)) : [];
  });
}
module.exports = {parse,get,id,layouts};
if(require.main === module) {
  for (const file of layouts()) {
    for (const node of parse(fs.readFileSync(file,'utf8'))) {
      if(node.tag === 'TextView' && /^[1-9]\d*dp$/.test(get(node,'android:layout_height') || ''))
        console.log(path.basename(file),id(node),get(node,'android:layout_height'),get(node,'android:text'));
    }
  }
}
