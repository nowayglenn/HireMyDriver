import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const root=path.dirname(fileURLToPath(import.meta.url));
http.createServer((req,res)=>{const url=new URL(req.url,'http://localhost');const name=url.pathname==='/'?'index.html':decodeURIComponent(url.pathname.slice(1));const file=path.resolve(root,name);if(!file.startsWith(root+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){res.writeHead(404);res.end('Not found');return}res.setHeader('Content-Type',file.endsWith('.css')?'text/css':file.endsWith('.js')?'text/javascript':'text/html; charset=utf-8');res.end(fs.readFileSync(file))}).listen(8765,'127.0.0.1',()=>console.log('Local: http://127.0.0.1:8765'));
