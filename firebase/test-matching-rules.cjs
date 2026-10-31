const assert=require('node:assert/strict');
const host=process.env.FIRESTORE_EMULATOR_HOST;
if(!/^(localhost|127\.0\.0\.1):\d+$/.test(host||''))throw Error('Local emulator required');
const project='demo-hiremydriver-booking',root=`projects/${project}/databases/(default)/documents`,base=`http://${host}/v1/${root}`;
const field=x=>x===null?{nullValue:null}:Array.isArray(x)?{arrayValue:{values:x.map(field)}}:typeof x==='object'?{mapValue:{fields:fields(x)}}:typeof x==='string'?{stringValue:x}:typeof x==='boolean'?{booleanValue:x}:{integerValue:String(x)};
const fields=x=>Object.fromEntries(Object.entries(x).map(([k,v])=>[k,field(v)]));
const token=uid=>{const b=x=>Buffer.from(JSON.stringify(x)).toString('base64url'),now=Math.floor(Date.now()/1000);return `${b({alg:'none'})}.${b({iss:`https://securetoken.google.com/${project}`,aud:project,sub:uid,user_id:uid,iat:now,exp:now+3600,firebase:{sign_in_provider:'password'}})}.`;};
const write=(path,data,mask=false,stamp=false)=>({update:{name:`${root}/${path}`,fields:fields(data)},...(mask?{updateMask:{fieldPaths:Object.keys(data)}}:{}),...(stamp?{updateTransforms:[{fieldPath:'updatedAt',setToServerValue:'REQUEST_TIME'}]}:{})});
const commit=(uid,writes)=>fetch(`${base}:commit`,{method:'POST',headers:{Authorization:`Bearer ${uid==='owner'?'owner':token(uid)}`,'Content-Type':'application/json'},body:JSON.stringify({writes})});
async function check(response,ok,label){const body=await response.text();assert.equal(response.ok,ok,`${label}: ${body}`);console.log('PASS '+label);}
(async()=>{
 const id='matching-driver',p={fullName:'Driver',experience:'3',bio:'Bio',status:'pending'},path=`driverProfiles/${id}`;
 await check(await commit('owner',[write(`driverApplications/${id}`,p)]),true,'seed private application');
 await check(await commit(id,[write(path,p)]),true,'existing profile create');
 const matching={areas:['Bohol'],vehicles:['SUV'],transmissions:['Automatic'],languages:['English'],rate:500,experience:'3',accepting:true,
  weekly:Array.from({length:7},()=>({enabled:true,start:480,end:1080}))};
 await check(await commit(id,[write(path,{matching,busySlots:{},availabilityReady:false},true)]),false,'pending driver cannot turn on availability');
 await check(await commit(id,[write(path,{matching:{...matching,accepting:false},busySlots:{},availabilityReady:false},true)]),true,'pending driver can save offline setup');
 await check(await fetch(base+'/'+path,{headers:{Authorization:'Bearer '+token('matching-customer')}}),false,'customer cannot see pending profile');
 await check(await fetch(base+'/'+path,{headers:{Authorization:'Bearer '+token(id)}}),true,'pending owner can read own profile');
 await check(await commit(id,[write(path,{status:'approved'},true)]),false,'driver cannot self approve');
 await check(await commit('owner',[write('driverApplications/'+id,{status:'approved'},true),write(path,{status:'approved'},true)]),true,'trusted admin approves application and profile');
 await check(await commit(id,[write(path,{matching,busySlots:{},availabilityReady:false},true)]),true,'approved driver with complete setup can go online');
 await check(await commit(id,[write(path,{matching:{...matching,languages:['Invalid']}},true)]),false,'reject unsupported languages');
 await check(await commit('stranger',[write(path,{matching},true)]),false,'stranger cannot change settings');
 const selectedAreas=['Sikatuna (Bohol)','Balilihan (Bohol)','City of Tagbilaran (Bohol)'];
 await check(await commit(id,[write(path,{matching:{...matching,areas:selectedAreas}},true)]),true,'owner saves chosen municipality areas in existing matching field');
 const publicRead=await fetch(`${base}/${path}`,{headers:{Authorization:`Bearer ${token('matching-customer')}`}});
 assert.equal(publicRead.ok,true);
 assert.deepEqual((await publicRead.json()).fields.matching.mapValue.fields.areas.arrayValue.values.map(x=>x.stringValue),selectedAreas);
 console.log('PASS customer reads exactly the selected areas');
 await check(await commit('stranger',[write(path,{matching:{...matching,areas:['City of Cebu (Cebu)']}},true)]),false,'customer cannot change driver service areas');
 await check(await commit(id,[write(path,{matching:{...matching,areas:Array.from({length:31},(_,i)=>'Area '+i)}},true)]),false,'existing thirty-area limit enforced');
 for(const field of ['vehicles','transmissions','areas','rate','experience','languages']) {
  const incomplete={...matching,[field]: field==='rate'?null:field==='experience'?'':[]};
  await check(await commit(id,[write(path,{matching:incomplete},true)]),false,'cannot go online without '+field);
  await check(await commit(id,[write(path,{matching:{...incomplete,accepting:false}},true)]),true,'incomplete '+field+' may stay offline');
 }
 await check(await commit(id,[write(path,{matching:{...matching,weekly:matching.weekly.map((d,i)=>({...d,enabled:i===2,configured:i===2||i===4}))}},true)]),true,'explicit Wednesday working and Friday off preserved; other days unset');
 await check(await commit(id,[write(path,{matching:{...matching,weekly:matching.weekly.map(d=>({...d,configured:false}))}},true)]),false,'unset days cannot be enabled');
 await check(await commit(id,[write(path,{matching},true)]),true,'restore complete online profile');

 await check(await commit(id,[write(path,{matching:{...matching,rate:750,experience:'8'}},true)]),true,'owner publishes updated rate and experience');
 await check(await commit(id,[write(path,{matching:{...matching,licenseNumber:'private'}},true)]),false,'public driving settings reject private documents');
 await check(await commit(id,[write(path,{matching:{...matching,weekly:[{}]}},true)]),false,'invalid schedule rejected');
 await check(await commit(id,[write(path,{savedLocation:{latitude:10,longitude:123,capturedAt:Date.now(),source:'gps'}},true)]),true,'owner saves measured location');
 await check(await commit(id,[write(path,{matching:{...matching,weekly:matching.weekly.map(d=>({...d,configured:true,start:555,end:1005}))}},true)]),true,'seven configured days save alongside existing measured location');
 await check(await commit(id,[write(path,{savedLocation:{latitude:91,longitude:123,capturedAt:Date.now(),source:'gps'}},true)]),false,'invalid coordinate rejected');
 await check(await commit('stranger',[write(path,{savedLocation:{latitude:10,longitude:123,capturedAt:Date.now(),source:'gps'}},true)]),false,'peer cannot move driver location');
 const b={driverUid:id,customerUid:'matching-customer',date:'Oct 19, 2026',time:'09:00 AM',hours:4,phase:'REQUESTED'};
 await check(await commit('owner',[write('bookings/matching-booking',b)]),true,'seed private booking');
 await check(await commit(id,[write('bookings/matching-booking',{phase:'ACCEPTED'},true,true)]),false,'acceptance without busy slot rejected');
 const slot={date:b.date,time:b.time,hours:4,phase:'ACCEPTED',started:0,extensionMinutes:0};
 const projection={busySlots:{'matching-booking':slot},busyBookingId:'matching-booking'};
 await check(await commit(id,[write('bookings/matching-booking',{phase:'ACCEPTED'},true,true),write(path,projection,true)]),true,'booking and anonymous slot saved atomically');
 await check(await commit(id,[write(path,{busySlots:{},busyBookingId:'matching-booking'},true)]),false,'cannot erase active booking slot');
 await check(await commit(id,[write(path,{...projection,busySlots:{'matching-booking':{...slot,customerName:'private'}}},true)]),false,'private fields rejected');
 await check(await commit(id,[write(path,{...projection,busySlots:{'matching-booking':{...slot,hours:1}}},true)]),false,'cannot invent free hours');
 await check(await commit(id,[write(path,{availabilityReady:true},true)]),true,'completed refresh marks availability ready');
 await check(await commit(id,[write(path,{thumbnail:'photo'},true)]),true,'profile photo edits preserve busy times');
 await check(await commit(id,[write('bookings/matching-booking',{phase:'ON_THE_WAY'},true,true),write(path,{busySlots:{'matching-booking':{...slot,phase:'ON_THE_WAY'}},busyBookingId:'matching-booking'},true)]),true,'departure remains atomic');
 await check(await commit('owner',[write('bookings/matching-booking',{...b,phase:'COMPLETED'})]),true,'seed completed legacy booking');
 await check(await commit(id,[write(path,{busySlots:{},busyBookingId:'matching-booking'},true)]),true,'completed slot removed');
 await check(await commit('owner',[write('bookings/cancel-reserved',{...b,phase:'ACCEPTED'}),write(path,{busySlots:{'cancel-reserved':slot}},true)]),true,'seed reserved cancellation');
 await check(await commit('matching-customer',[write('bookings/cancel-reserved',{phase:'CANCELLED',request:'Cancelled: Plans',cancellationReason:'Plans'},true,true)]),false,'cancellation must release reservation atomically');
 await check(await commit('matching-customer',[write('bookings/cancel-reserved',{phase:'CANCELLED',request:'Cancelled: Plans',cancellationReason:'Plans'},true,true),write(path,{busySlots:{},busyBookingId:'cancel-reserved'},true)]),true,'customer cancellation atomically releases only own reservation');
 await check(await commit('owner',[write('bookings/extend-reserved',{...b,phase:'IN_PROGRESS',started:1000}),write(path,{busySlots:{'extend-reserved':{...slot,phase:'IN_PROGRESS',started:1000}}},true)]),true,'seed reserved extension');
 const requestExtension=()=>({
  ...write('bookings/extend-reserved',{requestedMinutes:30,extensionStatus:'PENDING',extensionSequence:1},true,true),
  updateTransforms:[{fieldPath:'updatedAt',setToServerValue:'REQUEST_TIME'},
   {fieldPath:'extensionEvents.EXTENSION_1_PENDING',setToServerValue:'REQUEST_TIME'}]
 });
 await check(await commit('matching-customer',[requestExtension(),write(path,{
  busySlots:{'extend-reserved':{...slot,phase:'IN_PROGRESS',started:1000}},busyBookingId:'extend-reserved'},true)]),
  false,'regression: customer request cannot rewrite driver availability');
 await check(await commit('stranger',[requestExtension()]),false,'unrelated customer cannot request extension');
 await check(await commit('matching-customer',[requestExtension()]),true,'customer extension with receipt succeeds without availability write');
 await check(await commit('matching-customer',[requestExtension()]),false,'duplicate pending request rejected');
 await check(await commit(id,[write('bookings/extend-reserved',{extensionStatus:'APPROVED',extensionMinutes:30},true,true)]),false,'extension approval must update reserved time');
 await check(await commit(id,[write('bookings/extend-reserved',{extensionStatus:'APPROVED',extensionMinutes:30},true,true),write(path,{busySlots:{'extend-reserved':{...slot,phase:'IN_PROGRESS',started:1000,extensionMinutes:30}},busyBookingId:'extend-reserved'},true)]),true,'extension and reserved time update atomically');
})().catch(e=>{console.error(e);process.exitCode=1});
