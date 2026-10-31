// Read-only: save the live rules for local regression testing; never print credentials.
import {createRequire} from 'node:module';
import {resolve} from 'node:path';
import {pathToFileURL} from 'node:url';
import {writeFileSync} from 'node:fs';
const root=process.argv[2];
process.loadEnvFile(resolve(root,'.env.local'));
const require=createRequire(resolve(root,'package.json'));
const {cert,applicationDefault}=require('firebase-admin/app');
const {firebaseServerConfig}=await import(pathToFileURL(resolve(root,'src/lib/firebase/server-config.ts')).href);
const config=firebaseServerConfig(process.env);
if(config.projectId!=='hiremydriver-e88f7')throw Error('Unexpected project');
const credential=config.serviceAccount?cert(config.serviceAccount):applicationDefault();
const token=await credential.getAccessToken();
async function read(path){
 const response=await fetch('https://firebaserules.googleapis.com/v1/'+path,{headers:{Authorization:`Bearer ${token.access_token}`}});
 if(!response.ok)throw Error(`Rules read failed (${response.status})`);
 return response.json();
}
const release=await read(`projects/${config.projectId}/releases/cloud.firestore`);
const rules=await read(release.rulesetName);
const content=rules.source.files.map(f=>f.content).join('\n');
writeFileSync(new URL('../deployed-extension-rules.rules',import.meta.url),content);
console.log(JSON.stringify({snapshotSaved:true,extensionRequests:content.includes("changes(['requestedMinutes', 'extensionStatus', 'extensionSequence', 'updatedAt'])"),
 extensionReceipts:content.includes('extensionEvents[extensionKey] == request.time')}));
