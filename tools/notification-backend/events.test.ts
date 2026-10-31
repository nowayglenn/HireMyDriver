import assert from 'node:assert/strict';
import { bookingEvents, confirmedBookingPhase, messageEvent, reviewEvent, approvalEvent } from './events.ts';
const b={customerUid:'customer',driverUid:'driver',phase:'REQUESTED'};
assert.equal(confirmedBookingPhase({...b,phase:'COMPLETED',statusEvents:{ACCEPTED:{toMillis:()=>123}}},'ACCEPTED'),'ACCEPTED');
assert.equal(confirmedBookingPhase({...b,phase:'COMPLETED'},'ACCEPTED'),null);
assert.equal(confirmedBookingPhase({...b,phase:'COMPLETED',statusEvents:{ACCEPTED:'fake'}},'ACCEPTED'),null);
assert.equal(confirmedBookingPhase(b,'REQUESTED'),'REQUESTED');
assert.throws(()=>bookingEvents('booking',b,'stranger'),/ACCESS/);
for(const phase of ['REQUESTED','ACCEPTED','ON_THE_WAY','ARRIVED','IN_PROGRESS','COMPLETED','CANCELLED','DECLINED']) {
 const events=bookingEvents('booking',{...b,phase},'driver');
 assert.deepEqual(events.map(e=>e.uid),['customer','driver']);
 assert.equal(new Set(events.map(e=>e.eventId)).size,1);
}
assert.equal(bookingEvents('booking',b,'customer')[1].title,'New booking request');
assert.throws(()=>bookingEvents('booking',{...b,phase:'fabricated'},'customer'),/EVENT/);
const thread={customerId:'customer',driverId:'driver',customerName:'Customer',driverName:'Driver'};
for(const [sender,recipient] of [['customer','driver'],['driver','customer']]) {
 const event=messageEvent('booking','message',thread,{senderId:sender,text:'hello\nworld'},sender);
 assert.equal(event.uid,recipient);assert.equal(event.body,'hello world');
 assert.equal(event.eventId,'message_booking_message');
}
assert.throws(()=>messageEvent('b','m',thread,{senderId:'customer'},'driver'),/ACCESS/);
assert.throws(()=>messageEvent('b','m',thread,{senderId:'stranger'},'stranger'),/ACCESS/);
console.log('PASS notification events: all booking phases, correct recipient, idempotent IDs, sender and membership authorization');

const completed={...b,phase:'COMPLETED'};
const review={customerUid:'customer',driverUid:'driver',rating:5};
const alert=reviewEvent('booking',completed,review,'customer');
assert.equal(alert.uid,'driver'); assert.equal(alert.kind,'review');
assert.equal(alert.eventId,reviewEvent('booking',completed,{...review,rating:4},'customer').eventId);
assert.throws(()=>reviewEvent('booking',completed,review,'stranger'),/ACCESS/);
assert.throws(()=>reviewEvent('booking',completed,review,'driver'),/ACCESS/);
assert.throws(()=>reviewEvent('booking',completed,{...review,driverUid:'other'},'customer'),/ACCESS/);
assert.throws(()=>reviewEvent('booking',b,review,'customer'),/EVENT/);
for(const rating of [0,6,4.5,'5',null]) assert.throws(()=>reviewEvent('booking',completed,{...review,rating},'customer'),/EVENT/);
const approved=approvalEvent('driver','decision');
assert.equal(approved.uid,'driver'); assert.equal(approved.kind,'approval'); assert.equal(approved.role,'driver');
assert.equal(approved.bookingId,''); assert.equal(approved.eventId,'approval_decision');
console.log('PASS review authorization, completed-booking requirement, rating validation, duplicate-review suppression and approval recipient');
