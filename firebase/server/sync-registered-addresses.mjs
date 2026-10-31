// Copies only missing public addresses from approved registrations. No registration changes.
// Dry run by default. Credentials remain in the existing admin environment.
import {createRequire} from 'node:module';
import {resolve} from 'node:path';
import {pathToFileURL} from 'node:url';
const root=process.argv[2];
if (!root) throw Error('Admin project directory required');
process.loadEnvFile(resolve(root,'.env.local'));
const require=createRequire(resolve(root,'package.json'));
const {initializeApp,cert,applicationDefault,deleteApp}=require('firebase-admin/app');
const {getFirestore}=require('firebase-admin/firestore');
const {firebaseServerConfig}=await import(pathToFileURL(resolve(root,'src/lib/firebase/server-config.ts')).href);
const config=firebaseServerConfig(process.env);
if(config.projectId!=='hiremydriver-e88f7') throw Error('Unexpected Firebase project');
const app=initializeApp({projectId:config.projectId,credential:config.serviceAccount?cert(config.serviceAccount):applicationDefault()});
const db=getFirestore(app);
try {
 const profiles=await db.collection('driverProfiles').where('status','==','approved').get();
 let eligible=0,updated=0;
 for(const profile of profiles.docs) {
  if(typeof profile.get('address')==='string' && profile.get('address').trim()) continue;
  const ref=db.collection('driverApplications').doc(profile.id);
  const source=await ref.get();
  if(source.get('status')!=='approved' || typeof source.get('address')!=='string' || !source.get('address').trim()) continue;
  eligible++;
  if(!process.argv.includes('--apply')) continue;
  const changed=await db.runTransaction(async tx=>{
   const fresh=await tx.get(ref),current=await tx.get(profile.ref);
   if(fresh.get('status')!=='approved' || current.get('status')!=='approved' ||
      (typeof current.get('address')==='string' && current.get('address').trim()) ||
      typeof fresh.get('address')!=='string' || !fresh.get('address').trim()) return false;
   tx.update(profile.ref,{address:fresh.get('address')});
   return true;
  });
  if(changed)updated++;
 }
 console.log(JSON.stringify({mode:process.argv.includes('--apply')?'apply':'dry-run',approvedProfiles:profiles.size,missingAddressesWithSavedSource:eligible,updated}));
} finally {await db.terminate(); await deleteApp(app);}
