const assert = require('node:assert/strict');
const host = process.env.FIRESTORE_EMULATOR_HOST;
if (!/^(127\.0\.0\.1|localhost):\d+$/.test(host || '')) throw Error('Local emulator required');
const project='demo-hiremydriver-booking', root=`projects/${project}/databases/(default)/documents`, base=`http://${host}/v1/${root}`;
const token = uid => {
 const b=x=>Buffer.from(JSON.stringify(x)).toString('base64url'), now=Math.floor(Date.now()/1000);
 return `${b({alg:'none',typ:'JWT'})}.${b({iss:`https://securetoken.google.com/${project}`,aud:project,sub:uid,user_id:uid,email:`${uid}@example.com`,iat:now,exp:now+3600,firebase:{sign_in_provider:'password'}})}.`;
};
const field=x=>x===null?{nullValue:null}:Array.isArray(x)?{arrayValue:{values:x.map(field)}}:typeof x==='object'?{mapValue:{fields:fields(x)}}:typeof x==='string'?{stringValue:x}:typeof x==='boolean'?{booleanValue:x}:{integerValue:String(x)};
const fields=x=>Object.fromEntries(Object.entries(x).map(([k,v])=>[k,field(v)]));
const write=(id,data,stamps=[],partial=false)=>({update:{name:`${root}/${id}`,fields:fields(data)},updateTransforms:stamps.map(fieldPath=>({fieldPath,setToServerValue:'REQUEST_TIME'})),...(partial?{updateMask:{fieldPaths:Object.keys(data)}}:{})});
const commit=(uid,writes)=>fetch(`${base}:commit`,{method:'POST',headers:{Authorization:`Bearer ${uid==='owner'?'owner':token(uid)}`,'Content-Type':'application/json'},body:JSON.stringify({writes})});
const get=(uid,id)=>fetch(`${base}/${id}`,{headers:{Authorization:`Bearer ${token(uid)}`}});
let count=0;
async function check(response,ok,label){const text=await response.text();assert.equal(response.ok,ok,`${label}: ${response.status} ${text}`);count++;console.log('PASS '+label);}
async function main(){
 const booking={customerUid:'customer-f',driverUid:'driver-f',customer:'Customer',driver:'Driver',phase:'COMPLETED'};
 await check(await commit('owner',[write('bookings/features',booking)]),true,'seed completed booking');
 await check(await commit('owner',[write('users/customer-f',{fullName:'Customer',email:'private@example.com'})]),true,'seed private customer name');
 await check(await commit('customer-f',[write('users/customer-f/public/profile',{name:'Customer'})]),true,'publish registered customer display name');
 await check(await commit('customer-f',[write('users/customer-f/public/profile',{name:'Someone else'})]),false,'reject invented public customer name');
 await check(await get('driver-f','users/customer-f/public/profile'),true,'driver can read public display name');
 await check(await get('driver-f','users/customer-f'),false,'driver still cannot read private customer document');
 const contact={name:'Trusted person',phone:'+639171234567',relationship:'Mother',tripDetails:true};
 const contactPath='users/customer-f/settings/emergencyContact';
 await check(await commit('customer-f',[write(contactPath,contact,['updatedAt'])]),true,'save contact');
 await check(await get('customer-f',contactPath),true,'owner retrieves contact');
 await check(await get('driver-f',contactPath),false,'driver cannot see private emergency contact');
 await check(await commit('stranger',[write(contactPath,contact,['updatedAt'])]),false,'stranger cannot replace contact');
 await check(await commit('customer-f',[write(contactPath,{...contact,phone:'123'},['updatedAt'])]),false,'reject invalid mobile');
 const thread={customerId:'customer-f',driverId:'driver-f',customerName:'Customer',driverName:'Driver',active:true,lastMessageId:'',lastText:'',lastSenderId:'',readAt:{}};
 const missing=await get('customer-f','conversations/features');assert.equal(missing.status,404);count++;
 await check(await commit('customer-f',[write('conversations/features',thread,['updatedAt'])]),true,'participant creates booking conversation');
 await check(await get('driver-f','conversations/features'),true,'driver reads conversation');
 await check(await get('stranger','conversations/features'),false,'stranger cannot read conversation');
 await check(await commit('driver-f',[write('conversations/features',{...thread,customerId:'stranger'},['updatedAt'])]),false,'cannot replace participants');
 const message=(uid,id,text)=>[
   write(`conversations/features/messages/${id}`,{senderId:uid,text},['createdAt']),
   write('conversations/features',{lastMessageId:id,lastText:text,lastSenderId:uid},['updatedAt'],true)
 ];
 await check(await commit('customer-f',message('customer-f','m1','Hello')),true,'customer sends text');
 await check(await commit('driver-f',message('driver-f','m2','Hello back')),true,'driver replies');
 await check(await get('customer-f','conversations/features/messages/m2'),true,'customer reads saved reply');
 for(const [uid,fieldPath] of [['customer-f','customerId'],['driver-f','driverId']]) {
   const response=await fetch(`${base}:runQuery`,{method:'POST',headers:{Authorization:`Bearer ${token(uid)}`,'Content-Type':'application/json'},body:JSON.stringify({structuredQuery:{from:[{collectionId:'conversations'}],where:{fieldFilter:{field:{fieldPath},op:'EQUAL',value:field(uid)}}}})});
   const rows=await response.json(); assert.equal(response.ok,true,JSON.stringify(rows));
   assert(rows.some(row=>row.document?.name.endsWith('/conversations/features')));count++;console.log('PASS '+fieldPath+' inbox loads existing history');
 }
 await check(await commit('driver-f',message('driver-f','m2','Changed')),false,'message IDs cannot be overwritten');
 await check(await commit('stranger',message('stranger','m3','Intrusion')),false,'stranger cannot send');
 const photo=message('customer-f','photo','image');photo[0].update.fields.imageUrl=field('https://example.com/image');
 await check(await commit('customer-f',photo),false,'attachments rejected');
 const review={customerUid:'customer-f',driverUid:'driver-f',rating:5,comment:'Helpful',tags:['Friendly']};
 await check(await commit('customer-f',[write('reviews/features',review,['createdAt','updatedAt'])]),true,'completed booking review');
 await check(await commit('customer-f',[write('reviews/features',{rating:4,comment:'Updated',tags:[]},['updatedAt'],true)]),true,'existing review edit preserves one document');
 await check(await commit('customer-f',[write('reviews/features',{customerName:'Customer'},['updatedAt'],true)]),true,'review exposes only verified booking display name');
 await check(await commit('customer-f',[write('reviews/features',{customerName:'Impersonated'},['updatedAt'],true)]),false,'reviewer cannot invent another display name');
 await check(await commit('driver-f',[write('reviews/features',{rating:5},['updatedAt'],true)]),false,'driver cannot rate own booking');
 await check(await commit('customer-f',[write('reviews/features',{rating:6},['updatedAt'],true)]),false,'reject invalid rating');
 await check(await commit('customer-f',[write('reviews/features',{tags:['Friendly','Friendly']},['updatedAt'],true)]),false,'reject duplicate review tags');
 await check(await commit('owner',[write('bookings/not-complete',{...booking,phase:'IN_PROGRESS'})]),true,'seed active trip');
 await check(await commit('customer-f',[write('reviews/not-complete',review,['createdAt','updatedAt'])]),false,'reject review before completion');
 await check(await commit('customer-f',[write('reviews/duplicate',review,['createdAt','updatedAt'])]),false,'no duplicate under arbitrary booking ID');
 await check(await commit('customer-f',[write('emergencyAlerts/features',{customerUid:'customer-f',status:'sent'})]),false,'client cannot invent SMS success');
 await check(await commit('owner',[write('users/customer-f',{role:'customer'},[],true)]),true,'seed role for notification device');
 await check(await commit('customer-f',[write('users/customer-f/devices/phone',{token:'device-token',role:'customer'},['updatedAt'])]),true,'owner registers notification token');
 await check(await get('driver-f','users/customer-f/devices/phone'),false,'peer cannot read device token');
 await check(await commit('customer-f',[write('users/customer-f/devices/phone',{token:'device-token',role:'driver'},['updatedAt'])]),false,'customer cannot register as driver');
 await check(await commit('customer-f',[write('users/customer-f/notifications/fake',{title:'Fabricated'})]),false,'client cannot invent notification');
 await check(await commit('owner',[write('users/customer-f/notifications/real',{title:'Booking accepted',read:false})]),true,'server creates event');
 await check(await get('driver-f','users/customer-f/notifications/real'),false,'notifications stay recipient-private');
 await check(await commit('customer-f',[write('users/customer-f/notifications/real',{read:true},[],true)]),true,'recipient marks notification read');
 await check(await commit('customer-f',[write('users/customer-f/notifications/real',{title:'Changed'},[],true)]),false,'recipient cannot change notification text');
 const privateProfile='users/customer-f/settings/profile';
 const sharedProfile='bookings/features/customer/profile';
 const profile={name:'Customer Updated',phone:'09171234567',address:'Barangay, Municipality, Province'};
 await check(await commit('customer-f',[write(privateProfile,profile)]),true,'customer saves private editable profile');
 await check(await get('driver-f',privateProfile),false,'driver cannot browse private editable profile');
 await check(await commit('stranger',[write(privateProfile,profile)]),false,'other users cannot edit private profile');
 const shared={...profile,email:'customer-f@example.com'};
 await check(await commit('customer-f',[write(sharedProfile,shared,['updatedAt'])]),true,'customer shares details with assigned booking driver');
 await check(await get('driver-f',sharedProfile),true,'assigned driver reads booking customer profile');
 await check(await get('customer-f',sharedProfile),true,'customer reads own shared profile');
 await check(await get('stranger',sharedProfile),false,'unrelated user cannot read contact details');
 await check(await commit('driver-f',[write(sharedProfile,shared,['updatedAt'])]),false,'driver cannot alter customer contact information');
 await check(await commit('customer-f',[write(sharedProfile,{...shared,email:'forged@example.com'},['updatedAt'])]),false,'cannot share an unverified login email');
 await check(await commit('customer-f',[write(sharedProfile,{...shared,secret:'private'},['updatedAt'])]),false,'shared profile rejects unrelated personal fields');
 console.log(`${count} customer feature rule checks passed.`);
}
main().catch(e=>{console.error(e.message);process.exitCode=1});
