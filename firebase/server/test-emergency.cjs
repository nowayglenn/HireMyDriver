// Isolated backend tests: mocked Firebase and provider, NEVER sends an SMS.
const fs=require('fs'), vm=require('vm'), assert=require('node:assert/strict');
const ts=require('C:/WebProjects/hiremydriver-admin/node_modules/typescript');
const source=fs.readFileSync(__dirname+'/emergency-route.ts','utf8');
const code=ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText;
function setup({enabled=true,phase='IN_PROGRESS',contact=true,providerFailure=false}={}) {
 const data=new Map([['bookings/trip',{customerUid:'customer',customer:'Ana',driver:'Driver',phase,pickupLabel:'Pickup place',destinations:[{label:'Destination one'},{label:'Destination two'}],location:{lat:88,lon:99}}]]);
 if(contact)data.set('users/customer/settings/emergencyContact',{name:'Mother',phone:'+639171234567',tripDetails:true});
 const ref=path=>({path,collection:name=>({doc:id=>ref(path+'/'+name+'/'+id)}),update:async patch=>data.set(path,{...data.get(path),...patch})});
 const db={collection:name=>({doc:id=>ref(name+'/'+id)}),runTransaction:async callback=>callback({get:async r=>({exists:data.has(r.path),data:()=>data.get(r.path)}),create:(r,x)=>{assert(!data.has(r.path));data.set(r.path,x);}})};
 const calls=[];const exports={};
 const context={exports,Response,Request,URLSearchParams,AbortSignal,Date,Intl,process:{env:enabled?{EMERGENCY_SMS_ENABLED:'true',SEMAPHORE_API_KEY:'mock-only',SEMAPHORE_SENDERNAME:'Mock'}:{}},
 require:name=>name.includes('firebase/admin')?{getFirebaseAdminAuth:()=>({verifyIdToken:async token=>{if(token!=='valid')throw Error('invalid');return {uid:'customer',firebase:{sign_in_provider:'password'}};}})}:name.includes('admin/drivers')?{driverDatabase:()=>db}:{FieldValue:{serverTimestamp:()=>123}},
 fetch:async(url,options)=>{calls.push({url,options});if(providerFailure)throw Error('timeout');return Response.json([{message_id:123,status:options.method==='POST'?'Queued':'Sent'}]);}};
 vm.runInNewContext(code,context);
 const request=(body={bookingId:'trip',consent:true},token='valid')=>exports.POST(new Request('https://localhost/api/emergency-alert',{method:'POST',headers:{authorization:'Bearer '+token},body:JSON.stringify(body)}));
 return {request,calls,data};
}
(async()=>{
 let s=setup({enabled:false});assert.equal((await s.request()).status,503);assert.equal(s.calls.length,0);
 s=setup();assert.equal((await s.request(undefined,'invalid')).status,401);assert.equal(s.calls.length,0);
 assert.equal((await s.request({bookingId:'trip',consent:false})).status,400);
 s=setup({phase:'COMPLETED'});assert.equal((await s.request()).status,503);assert.equal(s.calls.length,0);
 s=setup({contact:false});assert.equal((await s.request()).status,503);assert.equal(s.calls.length,0);
 s=setup();let result=await(await s.request({bookingId:'trip',consent:true,location:{lat:9.5,lon:123.5,time:Date.now()}})).json();assert.equal(result.status,'queued');
 let message=s.calls[0].options.body.get('message');assert(message.includes('9.500000, 123.500000'));assert(!message.includes('88'));assert(message.includes('Destination one -> Destination two'));assert(message.includes('Philippine Standard Time'));assert(message.includes('Customer: Ana'));
 result=await(await s.request()).json();assert.equal(result.status,'sent');assert.equal(s.calls.filter(c=>c.options.method==='POST').length,1);
 s=setup();await s.request({bookingId:'trip',consent:true,location:{lat:9,lon:123,time:Date.now()-300000}});message=s.calls[0].options.body.get('message');assert(message.includes('could not be determined'));assert(!message.includes('maps.google.com'));
 s=setup({providerFailure:true});assert.equal((await(await s.request()).json()).status,'unknown');await s.request();assert.equal(s.calls.length,1);
 console.log('PASS SMS setup, authentication, consent, active trip, contact, customer GPS, stale GPS, PH time, destinations, provider status and duplicate/timeout protection (mock provider only).');
})().catch(e=>{console.error(e);process.exitCode=1;});
