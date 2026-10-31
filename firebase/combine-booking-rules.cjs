const fs = require('fs');
const path = require('path');
const original = fs.readFileSync(path.join(__dirname, 'driver-registration.firestore.rules'), 'utf8');
const booking = fs.readFileSync(path.join(__dirname, 'booking-workflow.rules'), 'utf8');
const ending = /  }\r?\n}\s*$/;
if (!ending.test(original)) throw Error('Unexpected original rules structure');
const inserted = '\n    // Booking workflow; existing registration permissions above are unchanged.\n' +
    booking.split(/\r?\n/).slice(2).map(line => '    ' + line).join('\n') + '\n';
const at = original.search(ending);
const messaging = fs.readFileSync(path.join(__dirname, 'messaging/firestore.rules'), 'utf8');
const messageBody = messaging.slice(messaging.indexOf('    function member'), messaging.lastIndexOf('  }'));
const features = fs.readFileSync(path.join(__dirname, 'customer-features.rules'), 'utf8');
const additions = inserted + '\n' + messageBody + '\n' + features + '\n';
const combined = original.slice(0, at) + additions + original.slice(at);
if (combined.replace(additions, '') !== original) throw Error('Original rules changed');
fs.writeFileSync(path.join(__dirname, 'firestore-combined.rules'), combined);
fs.writeFileSync(path.join(__dirname, 'firestore-combined.txt'), combined);
console.log('Combined file created; original registration rules preserved exactly.');
