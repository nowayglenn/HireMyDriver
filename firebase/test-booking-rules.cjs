// Local emulator only. No production credentials or production writes.
const fs = require('fs');
const assert = require('node:assert/strict');
const host = process.env.FIRESTORE_EMULATOR_HOST;
if (!host || !/^(127\.0\.0\.1|localhost):\d+$/.test(host)) throw Error('Local emulator required');
const project = 'demo-hiremydriver-booking';
const base = `http://${host}/v1/projects/${project}/databases/(default)/documents`;
function token(uid) {
 const now = Math.floor(Date.now()/1000);
 const b = v => Buffer.from(JSON.stringify(v)).toString('base64url');
 return `${b({alg:'none',typ:'JWT'})}.${b({iss:`https://securetoken.google.com/${project}`,aud:project,sub:uid,user_id:uid,iat:now,exp:now+3600,firebase:{sign_in_provider:'password'}})}.`;
}
function field(v) {
 if(v===null)return {nullValue:null};
 if(Array.isArray(v))return {arrayValue:{values:v.map(field)}};
 if(typeof v==='object')return {mapValue:{fields:fields(v)}};
 if(typeof v==='string')return {stringValue:v};
 if(typeof v==='boolean')return {booleanValue:v};
 return Number.isInteger(v)?{integerValue:String(v)}:{doubleValue:v};
}
function fields(o) { return Object.fromEntries(Object.entries(o).filter(([,v])=>v!==undefined).map(([k,v])=>[k,field(v)])); }
const place=(label,lat,lon)=>({label,lat,lon});
async function write(uid,id,data,create=false,admin=false) {
 const stamp=[{fieldPath:'updatedAt',setToServerValue:'REQUEST_TIME'}];
 if(create)stamp.push({fieldPath:'createdAt',setToServerValue:'REQUEST_TIME'});
 const body={writes:[{update:{name:`projects/${project}/databases/(default)/documents/${id}`,fields:fields(data)},
  updateTransforms:admin?[]:stamp,...(!create?{updateMask:{fieldPaths:Object.keys(data)}}:{})}]};
 return fetch(`${base}:commit`,{method:'POST',headers:{Authorization:`Bearer ${admin?'owner':token(uid)}`,'Content-Type':'application/json'},body:JSON.stringify(body)});
}
async function check(response,ok,label) {
 const text=await response.text(); assert.equal(response.ok,ok,`${label}: ${response.status} ${text}`); console.log(`PASS ${label}`);
}
async function main(){
 const matching={areas:['Bohol'],vehicles:['SUV'],transmissions:['Automatic'],languages:['English'],rate:500,experience:'3',accepting:true};
 await check(await write('', 'driverApplications/driver-a',{status:'approved'},true,true),true,'seed approved application');
 await check(await write('', 'driverProfiles/driver-a',{fullName:'Driver',status:'approved',matching},true,true),true,'seed approved ready driver');
 const data={id:'booking-a',driver:'Driver',customer:'Customer',customerUid:'customer-a',driverUid:'driver-a',
 pickupLabel:'Pickup',destinationLabel:'Destination',pickup:place('Pickup',9.7,123.9),destinations:[place('Destination',9.8,124)],
 hours:4,vehicle:'SUV',notes:'',fee:'',date:'Oct 15, 2026',time:'10:00 AM',phase:'REQUESTED',request:'',reached:0,local:true,
 driverImage:0,driverRating:'',synced:true};
 await check(await write('', 'driverProfiles/driver-a',{status:'pending'},false,true),true,'set pending status');
 await check(await write('customer-a','bookings/pending-driver',{...data,id:'pending-driver'},true),false,'pending drivers cannot receive bookings');
 await check(await write('', 'driverProfiles/driver-a',{status:'approved',matching:{...matching,languages:[]}},false,true),true,'seed incomplete approved profile');
 await check(await write('customer-a','bookings/incomplete-driver',{...data,id:'incomplete-driver'},true),false,'incomplete approved drivers cannot receive bookings');
 await check(await write('', 'driverProfiles/driver-a',{matching:{...matching,accepting:false}},false,true),true,'set offline status');
 await check(await write('customer-a','bookings/offline-driver',{...data,id:'offline-driver'},true),false,'offline drivers cannot receive bookings');
 await check(await write('', 'driverProfiles/driver-a',{matching},false,true),true,'restore eligible driver');
 await check(await write('customer-a','bookings/booking-a',data,true),true,'customer creates request');
 const missing=await fetch(`${base}/bookings/not-created-yet`,{headers:{Authorization:`Bearer ${token('customer-a')}`}});
 assert.equal(missing.status,404); console.log('PASS creation transaction can read missing document');
 await check(await write('customer-a','bookings/forged',{...data,id:'forged',customerUid:'stranger'},true),false,'cannot impersonate another customer');
 const get=(uid)=>fetch(`${base}/bookings/booking-a`,{headers:{Authorization:`Bearer ${token(uid)}`}});
 await check(await get('customer-a'),true,'customer reads own booking');
 await check(await get('driver-a'),true,'assigned driver reads booking');
 await check(await get('stranger'),false,'unrelated user cannot read booking');
 await check(await write('stranger','bookings/booking-a',{phase:'ACCEPTED'}),false,'unrelated user cannot accept');
 await check(await write('customer-a','bookings/booking-a',{phase:'ACCEPTED'}),false,'customer cannot set driver status');
 await check(await write('driver-a','bookings/booking-a',{phase:'IN_PROGRESS',started:1000}),false,'cannot skip acceptance');
 await check(await write('', 'driverApplications/driver-a',{status:'pending'},false,true),true,'approval revoked before acceptance');
 await check(await write('driver-a','bookings/booking-a',{phase:'ACCEPTED'}),false,'pending driver cannot accept existing request');
 await check(await write('', 'driverApplications/driver-a',{status:'approved'},false,true),true,'restore trusted approval');
 await check(await write('', 'driverProfiles/driver-a',{matching:{...matching,languages:[]}},false,true),true,'make setup incomplete before acceptance');
 await check(await write('driver-a','bookings/booking-a',{phase:'ACCEPTED'}),false,'incomplete driver cannot accept existing request');
 await check(await write('', 'driverProfiles/driver-a',{matching},false,true),true,'restore setup before acceptance');
 await check(await write('driver-a','bookings/booking-a',{phase:'ACCEPTED'}),true,'driver accepts');
 await check(await get('driver-a'),true,'map preview reads without writing');
 const preview=await (await get('customer-a')).json(); assert.equal(preview.fields.phase.stringValue,'ACCEPTED');
 await check(await write('driver-a','bookings/booking-a',{phase:'ARRIVED'}),false,'cannot skip departure');
 await check(await write('driver-a','bookings/booking-a',{location:place('Driver',9.6,123.8),locationAt:1000}),false,'preview cannot share driver location');
 await check(await write('driver-a','bookings/booking-a',{phase:'ON_THE_WAY'}),true,'confirmed departure');
 await check(await write('driver-a','bookings/booking-a',{phase:'ACCEPTED'}),false,'cannot reverse status');
 await check(await write('driver-a','bookings/booking-a',{location:place('Driver',9.6,123.8),locationAt:1000}),true,'driver shares on-way location');
 await check(await write('driver-a','bookings/booking-a',{phase:'ARRIVED'}),true,'driver arrives');
 await check(await write('driver-a','bookings/booking-a',{phase:'IN_PROGRESS',started:1000}),true,'driver starts service');
 await check(await write('driver-a','bookings/booking-a',{started:9999}),false,'timer cannot restart');
 await check(await write('driver-a','bookings/booking-a',{driverUid:'stranger'}),false,'driver cannot reassign booking');
 await check(await write('driver-a','bookings/booking-a',{fee:'altered'}),false,'trip information is immutable');
 const customerView=await (await get('customer-a')).json(); assert.equal(customerView.fields.phase.stringValue,'IN_PROGRESS');
 console.log('PASS customer reads persisted In Progress');
 await check(await write('customer-a','bookings/booking-a',{pending:place('Extra',9.9,124.1),request:'Waiting for driver confirmation'}),true,'existing stop request supported');
 await check(await write('driver-a','bookings/booking-a',{pending:undefined,destinations:[...data.destinations,place('Extra',9.9,124.1)],request:'Destination accepted'}),true,'driver confirms requested destination');
 await check(await write('driver-a','bookings/booking-a',{reached:1}),true,'driver marks destination reached');
 await check(await write('driver-a','bookings/booking-a',{reached:3}),false,'cannot skip destinations');
 await check(await write('driver-a','bookings/booking-a',{phase:'COMPLETED',completed:2000}),true,'existing completion supported');
 await check(await write('driver-a','bookings/booking-a',{phase:'IN_PROGRESS',started:3000}),false,'cannot reopen completed service');
 const receiptWrite=(uid,phase,extra={},receiptPhase=phase)=>fetch(`${base}:commit`,{method:'POST',headers:{Authorization:`Bearer ${token(uid)}`,'Content-Type':'application/json'},body:JSON.stringify({writes:[{
  update:{name:`projects/${project}/databases/(default)/documents/bookings/event-booking`,fields:fields({phase,...extra})},
  updateMask:{fieldPaths:['phase',...Object.keys(extra)]},
  updateTransforms:[{fieldPath:'updatedAt',setToServerValue:'REQUEST_TIME'},{fieldPath:`statusEvents.${receiptPhase}`,setToServerValue:'REQUEST_TIME'}]
 }]})});
 await check(await write('customer-a','bookings/event-booking',{...data,id:'event-booking'},true),true,'create booking for durable event checks');
 await check(await receiptWrite('driver-a','ACCEPTED',{},'COMPLETED'),false,'cannot invent a completion receipt while accepting');
 await check(await receiptWrite('customer-a','ACCEPTED'),false,'customer cannot forge driver event');
 await check(await receiptWrite('driver-a','ACCEPTED'),true,'acceptance records server-confirmed event');
 await check(await receiptWrite('driver-a','ON_THE_WAY'),true,'departure retains previous event');
 const eventDoc=await (await fetch(`${base}/bookings/event-booking`,{headers:{Authorization:`Bearer ${token('customer-a')}`}})).json();
 assert(eventDoc.fields.statusEvents.mapValue.fields.ACCEPTED.timestampValue);
 assert(eventDoc.fields.statusEvents.mapValue.fields.ON_THE_WAY.timestampValue);
 console.log('PASS delayed relay can read both real transition timestamps');
 await check(await receiptWrite('driver-a','ON_THE_WAY'),false,'same-phase writes cannot recreate an event');
 await check(await write('driver-a','bookings/event-booking',{statusEvents:{}}),false,'cannot erase transition history');
 await check(await write('customer-a','bookings/cancel-test',{...data,id:'cancel-test'},true),true,'create cancellation test');
 await check(await write('customer-a','bookings/cancel-test',{phase:'CANCELLED',cancellationReason:'',request:'Cancelled: '}),false,'blank cancellation reason rejected');
 await check(await write('stranger','bookings/cancel-test',{phase:'CANCELLED',cancellationReason:'Plans',request:'Cancelled: Plans'}),false,'other customer cannot cancel');
 await check(await write('customer-a','bookings/cancel-test',{phase:'CANCELLED',cancellationReason:'Plans',request:'Cancelled: Plans'}),true,'customer cancellation saved');
 await check(await write('customer-a','bookings/cancel-test',{phase:'REQUESTED'}),false,'cancelled booking cannot reopen');
 await check(await write('', 'bookings/extension-test',{...data,id:'extension-test',phase:'IN_PROGRESS',started:1000,hourlyRate:500},true,true),true,'seed active extension booking');
 await check(await write('customer-a','bookings/extension-test',{phase:'CANCELLED',cancellationReason:'Plans',request:'Cancelled: Plans'}),false,'cannot cancel service in progress');
 await check(await write('customer-a','bookings/extension-test',{requestedMinutes:30,extensionStatus:'PENDING',extensionSequence:1}),true,'customer requests thirty minutes');
 await check(await write('customer-a','bookings/extension-test',{requestedMinutes:60,extensionStatus:'PENDING',extensionSequence:2}),false,'duplicate pending extension rejected');
 await check(await write('customer-a','bookings/extension-test',{extensionStatus:'APPROVED',extensionMinutes:30}),false,'customer cannot approve own extension');
 await check(await write('driver-a','bookings/extension-test',{extensionStatus:'APPROVED',extensionMinutes:60}),false,'driver cannot invent additional duration');
 await check(await write('driver-a','bookings/extension-test',{extensionStatus:'APPROVED',extensionMinutes:30}),true,'driver approves requested minutes');
 await check(await write('customer-a','bookings/extension-test',{requestedMinutes:60,extensionStatus:'PENDING',extensionSequence:2}),true,'new request after resolution allowed');
 await check(await write('driver-a','bookings/extension-test',{extensionStatus:'DECLINED'}),true,'driver declines without changing duration');
 await check(await write('customer-a','bookings/extension-test',{requestedMinutes:1440,extensionStatus:'PENDING',extensionSequence:3}),false,'oversized extension rejected');
 const extensionReceipt=(uid,values,event)=>fetch(`${base}:commit`,{method:'POST',headers:{Authorization:`Bearer ${token(uid)}`,'Content-Type':'application/json'},body:JSON.stringify({writes:[{update:{name:`projects/${project}/databases/(default)/documents/bookings/extension-test`,fields:fields(values)},updateMask:{fieldPaths:Object.keys(values)},updateTransforms:[{fieldPath:'updatedAt',setToServerValue:'REQUEST_TIME'},{fieldPath:`extensionEvents.${event}`,setToServerValue:'REQUEST_TIME'}]}]})});
 await check(await extensionReceipt('customer-a',{requestedMinutes:30,extensionStatus:'PENDING',extensionSequence:3},'EXTENSION_3_APPROVED'),false,'cannot forge approval notification receipt');
 await check(await extensionReceipt('customer-a',{requestedMinutes:30,extensionStatus:'PENDING',extensionSequence:3},'EXTENSION_3_PENDING'),true,'extension request records confirmed notification receipt');
 await check(await extensionReceipt('driver-a',{extensionStatus:'APPROVED',extensionMinutes:60},'EXTENSION_3_APPROVED'),true,'approved extension records confirmed notification receipt');
 console.log('Booking permission and transition checks passed.');
}
main().catch(e=>{console.error(e.message);process.exitCode=1});
