// Publish only the reviewed contact-rule additions and fill missing booking contacts.
// Existing profiles and unrelated deployed rules are preserved. No personal data is printed.
import {createRequire} from 'node:module';
import {resolve} from 'node:path';
import {pathToFileURL} from 'node:url';
import {readFileSync,writeFileSync} from 'node:fs';
const root=process.argv[2];
process.loadEnvFile(resolve(root,'.env.local'));
const require=createRequire(resolve(root,'package.json'));
const {initializeApp,cert,applicationDefault,deleteApp}=require('firebase-admin/app');
const {getFirestore,FieldValue}=require('firebase-admin/firestore');
const {firebaseServerConfig}=await import(pathToFileURL(resolve(root,'src/lib/firebase/server-config.ts')).href);
const config=firebaseServerConfig(process.env);
if(config.projectId!=='hiremydriver-e88f7') throw Error('Unexpected project');
const credential=config.serviceAccount?cert(config.serviceAccount):applicationDefault();
const app=initializeApp({projectId:config.projectId,credential});
const db=getFirestore(app);
const before=readFileSync(new URL('../deployed-profile-rules-before.rules',import.meta.url),'utf8');
const after=readFileSync(new URL('../deployed-profile-rules-after.rules',import.meta.url),'utf8');
const additions=readFileSync(new URL('../customer-features.rules',import.meta.url),'utf8').split('match /users/{uid}/public/profile')[0];
const marker='match /databases/{database}/documents {';
if(after!==before.replace(marker,marker+'\n'+additions))throw Error('Unexpected rules changes');
try {
 const token=await credential.getAccessToken();
 const headers={Authorization:`Bearer ${token.access_token}`,'Content-Type':'application/json'};
 async function api(path,method='GET',body){
  const response=await fetch('https://firebaserules.googleapis.com/v1/'+path,{method,headers,body:body?JSON.stringify(body):undefined});
  if(!response.ok)throw Error(`Rules request failed (${response.status})`);
  return response.json();
 }
 const releasePath=`projects/${config.projectId}/releases/cloud.firestore`;
 const release=await api(releasePath);
 const current=await api(release.rulesetName);
 const content=current.source.files.map(f=>f.content).join('\n');
 if(content!==before && content!==after)throw Error('Deployed rules changed; review again before publishing');
 if(content!==after){
  writeFileSync(new URL('../profile-rules-rollback.json',import.meta.url),JSON.stringify({rulesetName:release.rulesetName},null,2));
  const created=await api(`projects/${config.projectId}/rulesets`,'POST',{source:{files:[{name:'firestore.rules',content:after}]}});
  // Recheck immediately before publishing so another admin's edits are not replaced.
  if((await api(releasePath)).rulesetName!==release.rulesetName)throw Error('Release changed; publish aborted');
  await api(releasePath,'PATCH',{release:{name:releasePath,rulesetName:created.name},updateMask:'rulesetName'});
 }
 const verified=await api((await api(releasePath)).rulesetName);
 if(verified.source.files.map(f=>f.content).join('\n')!==after)throw Error('Rules verification failed');
 let created=0,skipped=0;
 const bookings=await db.collection('bookings').get();
 for(const row of bookings.docs){
  const changed=await db.runTransaction(async tx=>{
   const booking=await tx.get(row.ref);
   const uid=booking.get('customerUid'),driver=booking.get('driverUid');
   if(typeof uid!=='string'||!uid||typeof driver!=='string'||!driver)return false;
   const contactRef=row.ref.collection('customer').doc('profile');
   const contact=await tx.get(contactRef);
   if(contact.exists)return false;
   const userRef=db.collection('users').doc(uid);
   const user=await tx.get(userRef),profile=await tx.get(userRef.collection('settings').doc('profile'));
   if(!user.exists||user.get('role')!=='customer')return false;
   const value=(key,original)=>{
    const data=profile.get(key)??user.get(original);
    return typeof data==='string'?data:'';
   };
   const email=user.get('email');
   tx.create(contactRef,{name:value('name','fullName'),email:typeof email==='string'?email:'',
    phone:value('phone','phoneNumber'),address:value('address','address'),updatedAt:FieldValue.serverTimestamp()});
   return true;
  });
  if(changed)created++;else skipped++;
 }
 console.log(JSON.stringify({rulesVerified:true,bookingProfilesCreated:created,existingOrUnavailable:skipped}));
} finally {await db.terminate();await deleteApp(app);}
