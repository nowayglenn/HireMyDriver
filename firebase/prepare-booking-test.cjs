const fs = require('fs');
const path = require('path');
const rules = fs.readFileSync(path.join(__dirname, 'booking-workflow.rules'), 'utf8');
fs.writeFileSync(path.join(__dirname, 'booking-emulator.firestore.rules'),
    "rules_version = '2';\nservice cloud.firestore { match /databases/{database}/documents {\n" + rules + '\n}}\n');
