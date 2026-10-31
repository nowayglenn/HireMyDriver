// Read-only diagnosis. Never print account details, document IDs, or credentials.
import {createRequire} from 'node:module';
import {resolve} from 'node:path';
import {pathToFileURL} from 'node:url';
import {writeFileSync,existsSync} from 'node:fs';
const root=process.argv[2];
process.loadEnvFile(resolve(root,'.env.local'));
const require=createRequire(resolve(root,'package.json'));
const {initializeApp,cert,applicationDefault,deleteApp}=require('firebase-admin/app');
const {getFirestore}=require('firebase-admin/firestore');
const {firebaseServerConfig}=await import(pathToFileURL(resolve(root,'src/lib/firebase/server-config.ts')).href);
const config=firebaseServerConfig(process.env);
if(config.projectId!=='hiremydriver-e88f7') throw Error('Unexpected project');
const credential=config.serviceAccount?cert(config.serviceAccount):applicationDefault();
const app=initializeApp({projectId:config.projectId,credential});
const db=getFirestore(app);
try {
 const token=await credential.getAccessToken();
 const headers={Authorization:`Bearer ${token.access_token}`};
 const base='https://firebaserules.googleapis.com/v1/';
 const release=await fetch(base+`projects/${config.projectId}/releases/cloud.firestore`,{headers});
 if(release.ok){
  const r=await release.json();
  const response=await fetch(base+r.rulesetName,{headers});
  const rules=await response.json();
  const content=rules.source?.files?.map(f=>f.content).join('\n') || '';
  console.log(JSON.stringify({rulesReadable:response.ok,bookingContactRules:content.includes('/customer/profile'),privateProfileRules:content.includes('/settings/profile')}));
  const backup=new URL('../deployed-profile-rules-before.rules',import.meta.url);
  if(response.ok && !existsSync(backup)) writeFileSync(backup,content);
 } else console.log(JSON.stringify({rulesReadable:false,status:release.status}));
 const bookings=await db.collection('bookings').limit(100).get();
 let profiles=0, sources=0;
 const phases={};
 for(const booking of bookings.docs){
  const phase=booking.get('phase'); phases[phase]=(phases[phase]||0)+1;
  const contact=await booking.ref.collection('customer').doc('profile').get();
  if(contact.exists) profiles++;
  const uid=booking.get('customerUid');
  if(typeof uid==='string' && uid){
   const user=await db.collection('users').doc(uid).get();
   if(user.exists) sources++;
  }
 }
 console.log(JSON.stringify({bookingsChecked:bookings.size,sharedProfiles:profiles,registeredSources:sources,phases}));
} finally {await db.terminate();await deleteApp(app);}
