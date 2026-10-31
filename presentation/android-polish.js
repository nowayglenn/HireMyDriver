/* Presentation-only behavior and alignment for the original Android layouts. */
Object.assign(androidScreens,{
  notifications:()=>state.role==='driver'?'activity_driver_notifications':'activity_notifications',
  terms:'activity_terms_conditions',privacy:'activity_privacy_policy','license-scan':'activity_license_camera',
  chat:'activity_chat_conversation',sos:'activity_emergency_alert_confirm','upcoming-list':'activity_driver_upcoming_bookings'
});
androidScreens.support='activity_driver_contact_support';
Object.defineProperty(androidScreens,'help',{get:()=>state.role==='customer'?'activity_customer_help_center':'activity_driver_help_center'});
const driverHelpQuestions=[{"title":"Where can I find my bookings?","answer":"Open Bookings from the bottom navigation. Choose a booking to see its trip details and available actions."},{"title":"How do I update my personal information?","answer":"Go to Profile → Account Settings → Personal Information. Edit your details, then tap Save Changes. These changes are saved on this phone."},{"title":"How do I change my document photos?","answer":"Go to Profile → Documents. Tap Add photo or Replace photo for your license or clearance, choose an image, then tap Save Changes. Saving here keeps the documents on this phone; it does not submit a new verification request."},{"title":"Where can I read customer reviews?","answer":"Go to Profile → Customer Reviews to view the ratings and feedback available from completed trips."},{"title":"How do I select the vehicles I can drive?","answer":"Go to Profile → Driving Information. Tap a vehicle type to select or unselect it. A check mark shows which types are selected."},{"title":"What should I include when asking for help?","answer":"Describe what happened, which screen you were using, and what you expected. Include the booking reference if the issue concerns a trip. Do not include passwords or verification codes."}];
function activeHelpQuestions(){return state.role==='driver'?driverHelpQuestions:customerHelpQuestions}
const customerHelpQuestions=[
  {
    "title": "Where can I find my bookings?",
    "answer": "Open Bookings from the bottom navigation. Select a booking to view its status, pickup, destinations, and available actions."
  },
  {
    "title": "How do I choose a pickup location?",
    "answer": "When booking a driver, open the pickup map and move the pin to your meeting point. Add a place or landmark, then confirm the pickup."
  },
  {
    "title": "What happens if I leave the map during a trip?",
    "answer": "Your saved trip and start time are kept on this phone. Open the same active booking to continue. Location updates pause when you leave the map or minimize the app."
  },
  {
    "title": "Why is my location not showing?",
    "answer": "Open Settings → Location Permissions to check access. Turn on Location in your phone settings. Precise access gives a more accurate position than approximate access."
  },
  {
    "title": "How do I manage notifications?",
    "answer": "Open Settings → Notifications. Save your booking and message preferences or use Manage phone notifications to open Android settings. Live booking and message alerts are not available yet."
  },
  {
    "title": "What should I include when asking for help?",
    "answer": "Choose a topic, describe what happened, and include the booking reference if available. Do not include passwords or verification codes. Contact Support lets you share the message through another app."
  }
];
function supportTopics(){return state.role==='customer'?['Booking or trip','Account and profile','Map and location','Messages and notifications','Trip review','Something else']:['Booking or trip','Account and profile','Documents','Map and location','Customer reviews','Something else']}

function ensureDemoContent(){
  if(state.polishSeeded)return;
  state.polishSeeded=true;state.preferences={customer:{},driver:{}};state.termsAccepted={customer:true,driver:true};
  const comments=['Arrived on time and made our family trip comfortable.','Careful driving and clear communication throughout the trip.','Very familiar with the roads around Bohol. Would book again.','Friendly and helpful with our pickup and stops.','A smooth trip. The pickup took a few extra minutes, but communication was good.'];
  state.reviews=people.flatMap((p,driver)=>comments.map((text,i)=>({driver,name:['Alex Santos','Jamie Cruz','Camille Reyes','Nico Garcia','Patricia Lim'][i],rating:i===4?4:5,text,photo:['laira','mika','deandra','reno','mic'][i],sample:true})));
  state.notifications.customer.push({title:'Trip completed',text:'Your trip to Chocolate Hills and Loboc River is complete. Share your experience.',id:'HMD-1020',read:true},{title:'Booking reminder',text:'Your upcoming driver booking is on October 25 at 9:00 AM.',id:'HMD-1025',read:false});
  state.notifications.driver.push({title:'Upcoming booking',text:'Your accepted booking with Sofia Reyes is ready to view.',id:'HMD-1025',read:false},{title:'New customer review',text:'A customer shared feedback about the completed trip.',id:'HMD-1020',read:true});
}
function matchingDrivers(){
  const q=(state.homeSearch||'').trim().toLowerCase(),f=state.filter;
  return people.map((p,i)=>({p,i})).filter(({p})=>{
    const area=(f.area||'All').replace(/\s*\(Bohol\)/,'');
    return (!q||(p.name+' '+p.area+' Bohol '+p.types.join(' ')).toLowerCase().includes(q))&&
      (f.vehicle==='All'||p.types.includes(f.vehicle))&&
      (area==='All'||p.area.toLowerCase()===area.toLowerCase()||area==='Bohol')&&
      (!f.rating||f.rating==='All'||Number(p.rating)>=Number(f.rating))&&
      (!f.day||f.day==='Any day'||state.schedule.some(s=>s.day===f.day&&s.enabled))&&
      (f.experience==='All'||f.experience==='1–3 years'&&p.exp<=3||f.experience==='3–5 years'&&p.exp>=3&&p.exp<=5||f.experience==='5+ years'&&p.exp>=5);
  });
}
function refreshNativeLayout(){const host=$('#native-screen'),root=host?.firstElementChild?._node;if(root){nativeMeasure(root,host.clientWidth,host.clientHeight,host.clientWidth,host.clientHeight);paintNative(root)}}
function updateHomeResults(){
  const matches=new Set(matchingDrivers().map(x=>String(x.i)));
  $$('#native-screen [data-action="driver"]').forEach(el=>{const visible=matches.has(el.dataset.value);el._node.a.visibility=visible?'visible':'gone';el.style.display=visible?'':'none'});
  const empty=$('[data-nid="searchEmpty"]');if(empty){empty._node.a.visibility=matches.size?'gone':'visible';empty.style.display=matches.size?'none':''}
  const clear=$('[data-nid="btnClearSearch"]');if(clear){clear._node.a.visibility=state.homeSearch?'visible':'gone';clear.style.display=state.homeSearch?'flex':'none'}
  refreshNativeLayout();
  refreshSearchHistory();
}
function driverReviews(index){return state.reviews.filter(r=>r.driver===index)}
const polishBinding=bindAndroidScreen;
bindAndroidScreen=function(root){
  ensureDemoContent();polishBinding(root);
  const drv=state.role==='driver',screen=state.screen,set=(id,a={},act)=>editNative(root,id,a,act);
  const parentOf=child=>{let found;walkNative(root,n=>{if(n.c.includes(child))found=n});return found};
  // Give selectors, badges and icon/text actions a common vertical center.
  ['containerDate','containerTime','containerDuration','containerVehicle','containerTransmission','btnServiceArea','btnAvailability','containerRelationship','layoutVerified'].forEach(id=>{const n=set(id,{gravity:'center_vertical'});if(n)n.a.minHeight=id==='layoutVerified'?'36dp':'52dp'});
  walkNative(root,n=>{
    if(n.a.tag==='screen_header_title')n.a.gravity='start|center_vertical';
    if(['btnTrackTrip','btnEmergencyAccepted'].includes(nativeId(n))){Object.assign(n.a,{gravity:'center',layout_height:'48dp',minHeight:'48dp',paddingVertical:'0dp'});n.c.forEach(c=>{if(c.tag==='TextView')Object.assign(c.a,{gravity:'center_vertical',minHeight:'0dp',layout_height:'wrap_content',layout_marginTop:'0dp',layout_marginBottom:'0dp'})})}
    if(drv&&screen==='bookings'&&nativeId(n)==='bookingCardRating')n.a.visibility='gone';
  });
  if(screen==='signup'||screen==='registration'){
    const check=set('chkTerms',{layout_width:'44dp',layout_height:'44dp',minWidth:'44dp',minHeight:'44dp',padding:'0dp',checked:String(state.termsAccepted[state.role]),contentDescription:'I agree to the Terms and Conditions'},['demo-terms']);
    const row=parentOf(check);Object.assign(row.a,{gravity:'center_vertical',layout_height:'wrap_content',minHeight:'48dp'});
    row.c.filter(n=>n.tag==='TextView').forEach(n=>Object.assign(n.a,{textSize:'12sp',gravity:'center_vertical',layout_height:'44dp',minHeight:'0dp',layout_marginStart:'0dp',padding:'0dp'}));
    const link=set('txtTermsLink',{layout_marginStart:'4dp'});link.action=['go','terms'];
    set(drv?'btnNext':'btnSignUp',{},['demo-signup',state.role]);
  }
  if(screen==='home'&&!drv){
    set('btnNotification',{},['go','notifications']);
    const section=findNative(root,'aiRecommendedSection');if(section){const empty=nativeText('No drivers match your search or filters.',14,{id:'@+id/searchEmpty',paddingVertical:'20dp',gravity:'center',visibility:'gone'});section.c.push(empty)}
    const matches=new Set(matchingDrivers().map(x=>String(x.i)));
    walkNative(root,n=>{if(n.action?.[0]==='driver')n.a.visibility=matches.has(String(n.action[1]))?'visible':'gone'});
    set('searchEmpty',{visibility:matches.size?'gone':'visible'});
    set('edtSearch',{hint:'Search driver name or location'});
  }
  if(screen==='filter'){
    ['All',...vehicles].forEach(v=>{
      const n=set('btnVehicle'+v,{background:v===state.filter.vehicle?'#FFF3E6':'@android:color/transparent',cornerRadius:'12dp',paddingVertical:'8dp',gravity:'center',minHeight:'84dp'},['filter-vehicle',v]);
      if(n)walkNative(n,c=>{if(c.tag==='TextView')c.a.textColor=v===state.filter.vehicle?'#FF7900':'#667085';if(c.tag==='ImageView')Object.assign(c.a,{background:v===state.filter.vehicle?'#FF7900':'#F8F9FB',tint:v===state.filter.vehicle?'#FFFFFF':'#667085',cornerRadius:'10dp'})});
    });
    set('txtServiceArea',{gravity:'start|center_vertical',minHeight:'0dp'});
    set('txtAvailability',{text:state.filter.day||'Select available day',gravity:'start|center_vertical',minHeight:'0dp'});
    set('btnApplyFilters',{},['demo-apply-filter']);
    ['All','4','3','2','1'].forEach(v=>set('chipRating'+v,{background:(state.filter.rating||'All')===v?'#FF7900':'#F3F4F6',textColor:(state.filter.rating||'All')===v?'#FFFFFF':'#667085'},['demo-rating-filter',v]));
  }
  if(['recommended','popular','results'].includes(screen)){
    const matches=new Set(matchingDrivers().map(x=>String(x.i)));
    walkNative(root,n=>{if(n.action?.[0]==='driver')n.a.visibility=matches.has(String(n.action[1]))?'visible':'gone'});
  }
  if(screen==='book'||screen==='best'){
    set('txtDate',{text:demoDate(state.form.date),layout_width:'0dp',layout_weight:'1'});
    set('txtTime',{text:demoTime(state.form.time),layout_width:'0dp',layout_weight:'1'});
    ['txtDate','txtTime','txtDuration','txtVehicle'].forEach(id=>set(id,{gravity:'start|center_vertical',minHeight:'0dp',layout_height:'wrap_content'}));
    ['edtPickup','edtDestination'].forEach(id=>{const n=set(id);if(n)n.readOnly=true});
  }
  if(screen==='profile'){
    set('imgProfilePhoto',{scaleType:'centerCrop',shapeAppearanceOverlay:'@style/CircleImage',padding:'0dp',paddingHorizontal:'0dp',paddingVertical:'0dp'});
  }
  if(screen==='emergency'){
    ['edtContactName','edtPhoneNumber'].forEach(id=>set(id,{layout_height:'52dp',minHeight:'52dp',gravity:'center_vertical',paddingHorizontal:'14dp',paddingVertical:'0dp'}));
    set('txtRelationship',{gravity:'start|center_vertical',minHeight:'0dp'});
  }
  if(['notification-settings','location-settings'].includes(screen)){
    const prefs=state.preferences[state.role];
    walkNative(root,n=>{if(/Switch/.test(n.tag)){const id=nativeId(n);n.a.checked=String(prefs[id]??(id==='switchPreciseLocation'?state.settings.location:true));n.action=['demo-switch',id];}});
    set('txtNotificationPermission',{text:'Notifications are enabled for this demo.'});
    set('txtPermissionStatus',{text:(prefs.switchPreciseLocation??state.settings.location)?'Allowed · Precise location':'Approximate location'});
    set('btnOpenSystemSettings',{},['demo-device-settings','location']);
    set('btnNotificationPhoneSettings',{},['demo-device-settings','notifications']);
  }
  if(screen==='language'){
    ['English','Tagalog','Ilocano'].forEach(v=>set('rb'+v,{checked:String((state.language||'English')===v),paddingStart:'20dp',paddingEnd:'20dp',gravity:'start|center_vertical'},['demo-language',v]));
    set('btnSaveLanguage',{},['demo-save-language']);
  }
  if(screen==='terms'||screen==='privacy')set('btnAccept',{},['back']);
  if(screen==='terms'||screen==='privacy'){
    const text=set(screen==='terms'?'txtTermsContent':'txtPrivacyContent');
    if(text)text.a.text=resource(text.a.text).split('\n').map(line=>line.trim()).join('\n').replace(/\n{3,}/g,'\n\n').trim();
  }
  if(screen==='notifications'){
    walkNative(root,n=>{if(n.tag==='Flow')n.a.visibility='gone'});
    set('notificationHeaderTitle',{layout_width:'0dp',layout_constraintStart_toEndOf:'@id/btnBack',layout_constraintEnd_toStartOf:'@id/btnMarkRead',layout_constraintTop_toTopOf:'parent',layout_constraintBottom_toBottomOf:'parent',layout_marginStart:'4dp',textSize:'20sp',gravity:'center_vertical'});
    set('btnMarkRead',{layout_width:'90dp',layout_height:'48dp',layout_constraintEnd_toEndOf:'parent',layout_constraintTop_toTopOf:'parent',layout_constraintBottom_toBottomOf:'parent',layout_marginEnd:'12dp',textSize:'11sp',gravity:'center'},['demo-mark-read']);
    let list;walkNative(root,n=>{if(/ScrollView/.test(n.tag))list=n.c[0]});
    list.c=[nativeText('TODAY',13,{textStyle:'bold',layout_marginStart:'20dp',layout_marginTop:'20dp',layout_marginBottom:'8dp',textColor:'#667085'}),...state.notifications[state.role].map((notice,i)=>{
      const row=resolveNative(ANDROID.layouts.item_notification);row.action=['notification',String(i)];
      Object.assign(row.a,{gravity:'top',padding:'20dp'});
      editNative(row,'txtNotificationTitle',{text:notice.title,layout_width:'match_parent',gravity:'start'});editNative(row,'txtNotificationDesc',{text:notice.text,gravity:'start',lineSpacingExtra:'2dp'});editNative(row,'txtNotificationTime',{text:notice.read?'Earlier · Read':'Just now',gravity:'start'});
      editNative(row,'viewUnreadDot',{visibility:notice.read?'gone':'visible'});return row;
    })];
  }
  if(screen==='driver'){
    const reviews=driverReviews(state.driver);set('txtReviewCount',{text:'('+reviews.length+' reviews)'},['demo-driver-reviews',String(state.driver)]);
  }
  if(screen==='sos'){
    const b=currentBooking();set('emergencyDestination',{text:'Destination: '+b.destinations.at(-1)});set('emergencyDriver',{text:'Driver: '+people[b.driver].name});set('emergencyTime',{text:'Alert time: 9:41 AM · Demo'});set('btnSendAlert',{},['emergency-preview']);set('btnCancel',{},['back']);
  }
  if(screen==='chat'){
    const b=currentBooking(),peer=drv?state.profile.name:people[b.driver].name,photo=drv?'mika':people[b.driver].photo;
    set('txtDriverName',{text:peer});set('txtChatStatus',{text:'Booking '+b.id+' · '+b.status});set('profileCircle',{src:'@drawable/'+photo,padding:'0dp'});
    const input=set('edtMessage',{layout_height:'48dp',minHeight:'48dp',contentDescription:'Message'});input.formId='chat-text';input.value='';set('btnSend',{},['demo-send-message']);
    const messages=set('messagesContainer');messages.c=state.messages.map((m,i)=>{
      const mine=m.role===state.role,body=nativeText(m.text,14,{layout_width:'wrap_content',maxWidth:'260dp',padding:'12dp',background:'@drawable/bg_chat_bubble_'+(mine?'customer':'driver'),textColor:mine?'#FFFFFF':'#344054'});
      const wrapper={tag:'LinearLayout',a:{layout_width:mine?'wrap_content':'0dp',layout_weight:mine?'0':'1',layout_height:'wrap_content',orientation:'vertical'},c:[body,nativeText('9:'+String(35+i).padStart(2,'0')+' AM'+(mine?' · Sent':''),11,{layout_width:'wrap_content',paddingTop:'4dp'})]};
      return {tag:'LinearLayout',a:{layout_width:'match_parent',layout_height:'wrap_content',orientation:'horizontal',gravity:mine?'end':'start',layout_marginBottom:'16dp'},c:[...(!mine?[{tag:'ShapeableImageView',a:{layout_width:'28dp',layout_height:'28dp',layout_marginEnd:'8dp',src:'@drawable/'+photo,scaleType:'centerCrop',shapeAppearanceOverlay:'@style/CircleImage'},c:[]}]:[]),wrapper]};
    });
  }
  if(screen==='help'){
    set('btnSupportBack',{},['back']);set('btnContactFromHelp',{},['go','support']);
    const search=set('helpSearch',{contentDescription:'Search help'});search.formId='demo-help-search';search.value=state.helpQuery||'';
    state.helpExpanded??=[];
    const list=set('helpQuestions');list.c=activeHelpQuestions().map((q,i)=>{
      const card=resolveNative(ANDROID.layouts.item_driver_help_question);card.a.id='@+id/demoHelpQuestion'+i;
      editNative(card,'helpQuestionTitle',{text:q.title});
      editNative(card,'helpQuestionAnswer',{text:q.answer,visibility:state.helpExpanded.includes(i)?'visible':'gone'});
      editNative(card,'helpQuestionIndicator',{text:state.helpExpanded.includes(i)?'−':'+'});
      editNative(card,'helpQuestionToggle',{contentDescription:q.title},['demo-help-toggle',String(i)]);
      card.a.visibility=(q.title+' '+q.answer).toLowerCase().includes((state.helpQuery||'').trim().toLowerCase())?'visible':'gone';return card;
    });set('helpNoResults',{visibility:list.c.some(n=>n.a.visibility!=='gone')?'gone':'visible'});
  }
  if(screen==='support'){
    if(!supportTopics().includes(state.supportTopic))state.supportTopic='Booking or trip';
    set('btnSupportBack',{},['back']);set('supportTopic',{text:state.supportTopic||'Booking or trip',strokeWidth:'1dp'},['demo-support-topic']);
    const reference=set('supportBooking'),message=set('supportMessage');reference.formId='support-booking';reference.value=state.supportBooking||currentBooking().id;message.formId='support-message';message.value=state.supportMessage||'';
    set('btnShareSupport',{},['demo-support-share']);
  }
  if(screen==='upcoming-list'){
    const list=findNative(root,'driverUpcomingScroll').c[0];
    list.c=state.bookings.filter(b=>['Accepted','On the way','Arrived'].includes(b.status)).map(b=>{
      const card=resolveNative(ANDROID.layouts.item_driver_upcoming_full),date=new Date(b.date+'T12:00:00');
      const vals={bookingCardMonth:date.toLocaleDateString('en-US',{month:'short'}).toUpperCase(),bookingCardDay:String(date.getDate()),bookingCardTime:demoTime(b.time),bookingCardName:state.profile.name,bookingCardRoute:b.pickup+' → '+b.destinations.at(-1),bookingCardPackage:'By the Hour ('+b.hours+' hours) · '+money(b.hours*people[b.driver].rate),bookingCardStatus:b.status};
      Object.entries(vals).forEach(([id,text])=>editNative(card,id,{text}));editNative(card,'bookingCardImage',{src:'@drawable/mika'});editNative(card,'btnStartTrip1',{},['demo-open-trip',b.id]);editNative(card,'btnMessage1',{},['demo-open-chat',b.id]);compactNativeButtons(card);return card;
    });
    if(!list.c.length)list.c=[nativeText('No upcoming bookings.',14,{gravity:'center',padding:'24dp'})];
  }
  if(screen==='registration-personal'){
    set('btnTakeSelfie',{layout_height:'78dp',minHeight:'78dp',paddingVertical:'12dp',gravity:'center',orientation:'vertical'});
  }
  if(screen==='registration-documents'){
    set('btnScanLicense',{},['demo-scan']);set('btnLicenseBack',{},['back']);set('btnLicenseBackBottom',{},['back']);
    const done=state.uploads.licenseDemo;
    set('licenseFrontOcrStatus',{visibility:done?'visible':'gone',text:'Demo license front and back added. No camera was opened.'});
    set('edtLicenseExpiration',{text:done?'October 22, 2028':'Select expiration date'});
    if(done){const n=set('edtLicenseNumber');n.value='DEMO-LICENSE-001'}
  }
  if(screen==='license-scan'){
    const back=state.scanSide==='back';
    set('licenseCameraTitle',{text:back?'Scan license back':'Scan license front'});
    set('licenseCameraHint',{text:'Align the license inside the frame. Presentation preview only — no camera is used.'});
    set('licenseCameraStatus',{text:state.scanCaptured?'Sample image captured.':'Ready for demo capture'});
    set('licenseAlignmentLabel',{text:'DEMO · '+(back?'BACK':'FRONT'),gravity:'center'});
    set('captureLicensePhoto',{visibility:state.scanCaptured?'gone':'visible',text:'Capture sample'},['demo-capture']);
    set('licensePhotoReviewActions',{visibility:state.scanCaptured?'visible':'gone'});
    set('retakeLicensePhoto',{},['demo-retake']);set('acceptLicensePhoto',{text:back?'Use sample license':'Continue to back'},['demo-use-license']);
    set('closeLicenseCamera',{},['go','registration-documents']);
    walkNative(root,n=>{if(n.tag==='LicenseCameraFrame'){n.tag='FrameLayout';n.a.background='#101C2D';n.a.cornerRadius='16dp'}if(n.tag==='LicenseGuideView'){n.tag='View';Object.assign(n.a,{layout_width:'match_parent',layout_height:'190dp',layout_gravity:'center',layout_marginHorizontal:'20dp',strokeColor:'#FF7900',strokeWidth:'2dp',cornerRadius:'12dp',background:'#1A2C43'})}});
  }
  if(screen==='registration-review'){
    const values={txtReviewFullName:state.registrationName||people[0].name,txtReviewEmail:state.registrationEmail||'mark.demo@example.com',txtReviewPhone:state.profile.phone,txtReviewBirthDate:'Date of Birth: June 15, 1995',txtReviewAddress:state.registrationAddress||state.profile.address,txtReviewExperience:'Driving experience: 6 years',txtReviewBio:'Safe, professional driving services around Bohol.',txtReviewLicenseNumber:'License: DEMO-LICENSE-001',txtReviewExpirationDate:'Expires: October 22, 2028',txtReviewIssueDate:'Police clearance issued: October 1, 2026',txtReviewValidityDate:'Valid until: April 1, 2027'};
    Object.entries(values).forEach(([id,text])=>set(id,{text}));set('imgReviewProfile',{src:'@drawable/laur'});
    ['imgReviewLicenseFront','imgReviewLicenseBack','imgReviewPoliceClearance'].forEach(id=>{const n=set(id,{layout_height:'88dp',background:'#F3F4F6',gravity:'center',text:'Demo document on file',textColor:'#667085'});n.tag='TextView'});
    set('btnSubmitVerification',{},['demo-submit-verification']);set('btnLicenseBack',{},['back']);set('btnReviewBack',{},['back']);
  }
  if(screen==='home'&&drv){
    walkNative(root,n=>{if(n.action?.[0]==='booking-tab'&&n.action?.[1]==='Upcoming')n.action=['go','upcoming-list']});
    ['cardStatistics','cardUpcomingBooking','cardBookingRequests'].forEach(id=>set(id,{layout_marginTop:'18dp'}));
    const verified=set('layoutVerified',{layout_height:'36dp',minHeight:'36dp',gravity:'center_vertical'});
    verified.c.forEach(n=>{if(n.tag==='TextView')Object.assign(n.a,{layout_height:'36dp',minHeight:'0dp',gravity:'center_vertical'})});
    const pending=state.verificationPending;
    if(pending){const badge=set('layoutVerified');walkNative(badge,n=>{if(n.tag==='TextView')n.a.text='Verification Pending';if(n.tag==='ImageView')n.a.tint='#98A2B3'});set('switchAvailability',{checked:'false'});set('txtAvailabilityStatus',{text:'Your documents are awaiting verification.'});set('cardUpcomingBooking',{visibility:'gone'});set('cardBookingRequests',{visibility:'gone'});set('txtPendingCount',{text:'0'});set('txtCompletedCount',{text:'0'});walkNative(root,n=>{if(n.a.text==='4.8')n.a.text='—'})}
  }
  ['txtUpcomingDay','bookingCardDay'].forEach(id=>{const day=findNative(root,id),body=day&&parentOf(day);if(body){Object.assign(body.a,{layout_height:'0dp',layout_weight:'1',gravity:'center',padding:'0dp',paddingBottom:'0dp'});body.c.forEach(c=>Object.assign(c.a,{gravity:'center',layout_width:'match_parent'}))}});
  if(screen==='home'&&!drv){
    const clear=set('btnClearSearch',{visibility:state.homeSearch?'visible':'gone',text:'×',textSize:'26sp',gravity:'center',contentDescription:'Clear search',background:'@android:color/transparent',padding:'0dp'},['demo-clear-search']);clear.tag='TextView';
    const result=findNative(root,'searchResultsSection'),parent=result&&parentOf(result);
    if(parent)parent.c.splice(parent.c.indexOf(result),0,{tag:'LinearLayout',a:{id:'@+id/searchHistory',layout_width:'match_parent',layout_height:'wrap_content',orientation:'vertical',paddingHorizontal:'18dp',paddingTop:'12dp',paddingBottom:'8dp',visibility:'gone'},c:[]});
  }
};

const polishPerform=perform;
perform=function(a,v='',el){
  ensureDemoContent();
  if(a==='demo-help-toggle'){const index=Number(v);state.helpExpanded??=[];state.helpExpanded=state.helpExpanded.includes(index)?state.helpExpanded.filter(i=>i!==index):[...state.helpExpanded,index];refreshCustomerHelp();return}
  if(a==='map-type'){showOriginalMapTypes();return}
  if(a==='demo-send-message'){const input=$('#chat-text'),text=input.value.trim();if(!text)return;state.messages.push({role:state.role,text});notify(state.role==='customer'?'driver':'customer','New message',text);render();$('#chat-text').focus();return}
  if(a==='demo-open-chat'||a==='demo-open-trip'){state.selected=v;go(a==='demo-open-chat'?'chat':'trip');return}
  if(a==='booking-decision'&&v==='decline'){const b=currentBooking();if(b.status==='Pending'){b.status='Declined';notify('customer','Booking declined','Your driver is unable to accept this request.')}closeModal();render();toast('Booking request declined.');return}
  if(a==='demo-support-topic'){state.supportMessage=$('#support-message').value;state.supportBooking=$('#support-booking').value;modal('Choose a topic',supportTopics().map(t=>`<button class="choice ${state.supportTopic===t?'active':''}" ${action('demo-support-choice',t)}>${esc(t)}</button>`).join(''),btn('Cancel','close','','outline'));return}
  if(a==='demo-support-choice'){state.supportTopic=v;closeModal();render();return}
  if(a==='demo-support-share'){state.supportMessage=$('#support-message').value;state.supportBooking=$('#support-booking').value;if(!state.supportMessage.trim()){toast('Please describe what you need help with.');return}modal('Share support message',`<p>Demo preview — no message will be sent.</p><p><b>${esc(state.supportTopic||'Booking or trip')}</b><br>${esc(state.supportBooking)}<br>${esc(state.supportMessage)}</p>`,btn('Done','close'));return}
  if(a==='demo-clear-search'){rememberSearch();state.homeSearch='';$('#native-search').value='';$('#native-search').focus();updateHomeResults();return}
  if(a==='demo-history-clear'){state.searchHistory=[];refreshSearchHistory();return}
  if(a==='demo-history-remove'){state.searchHistory=(state.searchHistory||[]).filter(q=>q!==v);refreshSearchHistory();return}
  if(a==='demo-history-use'){state.homeSearch=v;$('#native-search').value=v;$('#native-search').focus();rememberSearch();updateHomeResults();return}
  if(a==='availability'&&state.verificationPending){toast('Your documents are awaiting verification.');return}
  if(a==='demo-terms'){state.termsAccepted[state.role]=!state.termsAccepted[state.role];el.setAttribute('aria-checked',String(state.termsAccepted[state.role]));return}
  if(a==='demo-signup'){
    if(!state.termsAccepted[v])return toast('Please agree to the Terms & Conditions.');
    if(!$('#signup-name').value.trim()||!$('#signup-email').validity.valid||!$('#signup-email').value)return toast('Enter your name and a valid email address.');
    if($('#signup-password').value.length<6||$('#signup-password').value!==$('#signup-confirm').value)return toast('Use matching passwords with at least 6 characters.');
    if(v==='driver'){state.registrationName=$('#signup-name').value;state.registrationEmail=$('#signup-email').value;state.role='driver';go('registration-personal');return;}
    state.profile.name=$('#signup-name').value;state.profile.email=$('#signup-email').value;state.profile.phone=$('#signup-phone')?.value||state.profile.phone;state.role='customer';go('home');return;
  }
  if(a==='demo-apply-filter'){go('home');toast(matchingDrivers().length+' matching drivers');return}
  if(a==='demo-rating-filter'){state.filter.rating=v;render();return}
  if(a==='demo-switch'){const p=state.preferences[state.role];p[v]=el.getAttribute('aria-checked')!=='true';el.setAttribute('aria-checked',String(p[v]));if(v==='switchPreciseLocation'){state.settings.location=p[v];const status=$('[data-nid="txtPermissionStatus"]');if(status)status.textContent=p[v]?'Allowed · Precise location':'Approximate location'}return}
  if(a==='demo-language'){state.language=v;$$('[role="radio"]').forEach(r=>r.setAttribute('aria-checked',String(r.dataset.value===v)));return}
  if(a==='demo-save-language'){toast((state.language||'English')+' selected.');return}
  if(a==='demo-device-settings'){modal(v==='location'?'Location permissions':'Phone notifications','<p>On the phone, this opens Android settings. This HTML preview changes demo preferences only.</p>',btn('Done','close'));return}
  if(a==='demo-mark-read'){state.notifications[state.role].forEach(n=>n.read=true);render();return}
  if(a==='demo-driver-reviews'){state.reviewDriver=Number(v);state.reviewOwn=false;state.reviewFilter='See All';go('reviews');return}
  if(a==='go'&&v==='reviews'){state.reviewOwn=state.role==='customer';state.reviewDriver=state.role==='driver'?0:state.screen==='reviewed'?currentBooking().driver:state.driver}
  if(a==='submit-review'){const before=state.reviews.length;polishPerform(a,v,el);if(state.reviews.length>before){state.reviews[0].driver=currentBooking().driver;state.reviewDriver=currentBooking().driver;state.reviewOwn=true}return}
  if(a==='demo-scan'){state.scanSide='front';state.scanCaptured=false;go('license-scan');return}
  if(a==='demo-capture'){state.scanCaptured=true;render();return}
  if(a==='demo-retake'){state.scanCaptured=false;render();return}
  if(a==='demo-use-license'){if(state.scanSide==='front'){state.scanSide='back';state.scanCaptured=false;render()}else{state.uploads.licenseDemo=true;go('registration-documents')}return}
  if(a==='demo-submit-verification'){state.verificationPending=true;state.role='driver';state.locationPrompted??={};state.locationPrompted.driver=true;go('home');modal('Verification pending','<p>Your information has been submitted. Please wait for your documents to be reviewed before accepting booking requests.</p>',btn('Continue','close'));return}
  if(a==='go'&&v==='registration-documents'&&state.screen==='registration-personal')state.registrationAddress=$('#registration-address')?.value||state.profile.address;
  if(a==='native-select')saveBookingForm();
  if(a==='map-collapse'){setMapSheetHeight(mapCollapsed?$('.map-screen').clientHeight*.48:80);return}
  if(a==='notification'){const notice=state.notifications[state.role][Number(v)];if(notice?.title.toLowerCase().includes('review')){state.reviewDriver=0;state.reviewOwn=false}}
  return polishPerform(a,v,el);
};
reviewCards=function(){
  ensureDemoContent();const list=state.reviews.filter(r=>state.reviewOwn?!r.sample:r.driver===(state.reviewDriver??(state.role==='driver'?0:state.driver))).filter(r=>state.reviewFilter==='See All'||r.rating===parseInt(state.reviewFilter));
  return list.map(r=>`<div class="card"><div class="row">${img(r.photo)}<div><b>${esc(r.name)}</b><div class="rating" style="margin-top:6px">${'★'.repeat(r.rating)}${'☆'.repeat(5-r.rating)}</div></div></div><p class="small-text muted" style="margin:15px 0 0">${esc(r.text)}</p></div>`).join('')||'<p class="empty">No reviews for this rating yet.</p>';
};
document.addEventListener('input',e=>{if(e.target.id==='native-search'){state.homeSearch=e.target.value;updateHomeResults()}});

const polishRender=render;
render=function(){const map=['trip','picker','history','extension','destination'].includes(state.screen);$('.screen').classList.toggle('map-mode',map);polishRender();if(state.screen==='profile')$('#statusbar').classList.add('dark');if(map){$('#statusbar').classList.remove('dark');setupMapSheet()}if($('#native-pickup'))$('#native-pickup').readOnly=true;if($('#native-destination'))$('#native-destination').readOnly=true;const chat=$('[data-nid="chatScrollView"]');if(chat)chat.scrollTop=chat.scrollHeight;};
let sheetHeight=null;
function setMapSheetHeight(height){
  const map=$('.map-screen');if(!map)return;const max=Math.max(100,map.clientHeight-180);
  sheetHeight=Math.max(84,Math.min(max,height));mapCollapsed=sheetHeight<=90;
  map.style.setProperty('--sheet-height',sheetHeight+'px');$('.map-panel').classList.toggle('collapsed',mapCollapsed);
  const handle=$('.map-panel .handle');handle.setAttribute('aria-expanded',String(!mapCollapsed));
}
function setupMapSheet(){
  const panel=$('.map-panel'),map=$('.map-screen'),handle=panel?.querySelector('.handle');if(!handle)return;
  const content=document.createElement('div');content.className='map-sheet-content';while(handle.nextSibling)content.appendChild(handle.nextSibling);panel.appendChild(content);
  setMapSheetHeight(mapCollapsed?80:sheetHeight||map.clientHeight*(map.classList.contains('picker')?0.40:0.48));
  let drag=null,moved=false;
  handle.addEventListener('pointerdown',e=>{drag={y:e.clientY,height:panel.getBoundingClientRect().height/parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--phone-scale'))};moved=false;handle.setPointerCapture(e.pointerId);map.classList.add('sheet-dragging');e.preventDefault()});
  handle.addEventListener('pointermove',e=>{if(!drag)return;const dy=(e.clientY-drag.y)/parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--phone-scale'));if(Math.abs(dy)>3)moved=true;setMapSheetHeight(drag.height-dy)});
  const end=()=>{drag=null;map.classList.remove('sheet-dragging')};handle.addEventListener('pointerup',end);handle.addEventListener('pointercancel',end);
  handle.addEventListener('click',e=>{if(moved){e.preventDefault();e.stopPropagation();moved=false}});
  handle.addEventListener('keydown',e=>{if(e.key==='ArrowUp'||e.key==='ArrowDown'){e.preventDefault();setMapSheetHeight(sheetHeight+(e.key==='ArrowUp'?60:-60))}});
}
function showOriginalMapTypes(){
  const root=resolveNative(ANDROID.layouts.dialog_map_type),overlay=$('#overlay');overlay.className='';overlay.innerHTML='<section class="dialog native-map-dialog" role="dialog" aria-label="Map type" aria-modal="true" style="position:relative;padding:0"></section>';
  const host=overlay.firstChild;editNative(root,'closeMapTypes',{},['close']);
  ['Normal','Satellite'].forEach((type,i)=>editNative(root,'mapLayer'+type,{strokeColor:(state.mapType==='Satellite')===!!i?'#FF7900':'#E4E7EC'},['set-map-type',i?'Satellite':'Map']));
  mountNative(root,host);const close=findNative(root,'closeMapTypes').el;close.innerHTML='<span style="font-size:28px;line-height:1">×</span>';
  nativeMeasure(root,host.clientWidth,0,host.clientWidth,null);host.style.height=root.box.h+'px';paintNative(root);
}
function rememberSearch(){const q=(state.homeSearch||'').trim().slice(0,100);if(q)state.searchHistory=[q,...(state.searchHistory||[]).filter(x=>x.toLowerCase()!==q.toLowerCase())].slice(0,10)}
function refreshSearchHistory(){
  const host=$('[data-nid="searchHistory"]');if(!host)return;const node=host._node,items=state.searchHistory||[],visible=document.activeElement?.id==='native-search'&&!state.homeSearch&&items.length;
  node.a.visibility=visible?'visible':'gone';host.style.display=visible?'block':'none';node.c=[];host.innerHTML='';
  if(visible){
    const label=(text,size,attrs={})=>nativeText(text,size,{layout_width:'wrap_content',layout_height:'48dp',gravity:'center_vertical',...attrs});
    const clear=label('Clear all',13,{textColor:'#FF7900',paddingStart:'12dp'});clear.action=['demo-history-clear'];
    node.c.push({tag:'LinearLayout',a:{layout_width:'match_parent',layout_height:'48dp',orientation:'horizontal',gravity:'center_vertical'},c:[label('Recent searches',16,{layout_width:'0dp',layout_weight:'1',textStyle:'bold',textColor:'#273142'}),clear]});
    items.forEach(query=>{const title=label(query,14,{layout_width:'0dp',layout_weight:'1',maxLines:'1'});title.action=['demo-history-use',query];node.c.push({tag:'LinearLayout',a:{layout_width:'match_parent',layout_height:'48dp',orientation:'horizontal',gravity:'center_vertical'},c:[{tag:'ImageView',a:{layout_width:'20dp',layout_height:'20dp',src:'@drawable/ic_search',tint:'#667085',layout_marginEnd:'12dp'},c:[]},title,{tag:'ImageButton',a:{layout_width:'48dp',layout_height:'48dp',padding:'14dp',src:'@drawable/ic_delete',tint:'#9CA3AF',contentDescription:'Remove search: '+query},action:['demo-history-remove',query],c:[]}]})});
    node.c.forEach(child=>mountNative(child,host));
  }
  refreshNativeLayout();
}
document.addEventListener('focusin',e=>{if(e.target.id==='native-search')refreshSearchHistory()});
document.addEventListener('focusout',e=>{if(e.target.id==='native-search'){rememberSearch();refreshSearchHistory()}});
document.addEventListener('pointerdown',e=>{if(e.target.closest('[data-nid="searchHistory"]')||e.target.closest('[data-action="demo-clear-search"]'))e.preventDefault()});
document.addEventListener('keydown',e=>{if(e.target.id==='native-search'&&e.key==='Enter'){e.preventDefault();rememberSearch();e.target.blur()}if(e.target.id==='chat-text'&&e.key==='Enter'&&!e.shiftKey){e.preventDefault();perform('demo-send-message')}});
render();

function refreshCustomerHelp(){
  const query=(state.helpQuery||'').trim().toLowerCase();let visible=0;
  activeHelpQuestions().forEach((q,i)=>{const el=$('[data-nid="demoHelpQuestion'+i+'"]');if(!el)return;const match=(q.title+' '+q.answer).toLowerCase().includes(query);if(match)visible++;el._node.a.visibility=match?'visible':'gone';el.style.display=match?'':'none';const open=(state.helpExpanded||[]).includes(i),answer=el.querySelector('[data-nid="helpQuestionAnswer"]'),indicator=el.querySelector('[data-nid="helpQuestionIndicator"]');answer._node.a.visibility=open?'visible':'gone';answer.style.display=open?'':'none';indicator.textContent=open?'−':'+';indicator._node.a.text=indicator.textContent;el.querySelector('[data-nid="helpQuestionToggle"]').setAttribute('aria-expanded',String(open));});
  const empty=$('[data-nid="helpNoResults"]');if(empty){empty._node.a.visibility=visible?'gone':'visible';empty.style.display=visible?'none':''}refreshNativeLayout();
}
document.addEventListener('input',e=>{if(e.target.id==='demo-help-search'){state.helpQuery=e.target.value;refreshCustomerHelp()}});
