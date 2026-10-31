/* Runtime content for the exported Android layouts. All actions use local demo data. */
Object.assign(androidScreens,{bookings:()=>state.role==='driver'?'activity_driver_booking':'activity_customer_booking',confirm:'activity_confirm_booking',details:()=>state.role==='driver'?'activity_driver_accepted_booking_details':'activity_booking_details','trip-details':()=>state.role==='driver'?'activity_driver_accepted_booking_details':'activity_booking_details',messages:()=>state.role==='driver'?'activity_driver_message':'activity_customer_messages'});
const bindBaseAndroid=bindAndroidScreen;
function editNative(root,id,attrs={},actionValue){const n=findNative(root,id);if(n){Object.assign(n.a,attrs);if(actionValue)n.action=actionValue}return n}
const demoDate=value=>new Date(value+'T12:00:00').toLocaleDateString('en-US',{month:'short',day:'numeric',year:'numeric'});
const demoTime=value=>{const [h,m]=value.split(':').map(Number);return (h%12||12)+':'+String(m).padStart(2,'0')+' '+(h>=12?'PM':'AM')};
function nativeRouteRows(pickup,destinations,review=false){return [pickup,...destinations].map((place,i)=>{
  const row=resolveNative(ANDROID.layouts.item_booking_route_point);
  editNative(row,'routePointName',{text:place,...(review?{textSize:'14sp'}:{})});
  editNative(row,'routePointCaption',{text:i?'Destination '+i:'Pickup'});
  if(review){row.a.id='@+id/demoReviewRoutePoint';editNative(row,'routeConnector',{visibility:'gone'});}
  else if(i===destinations.length)editNative(row,'routeConnector',{visibility:'invisible'});
  return row;
})}
function nativeBookingCard(b,kind,drv){
  const n=resolveNative(ANDROID.layouts['item_'+(drv?'driver':'customer')+'_booking_'+kind]);
  const p=people[b.driver];
  const texts={bookingCardName:drv?state.profile.name:p.name,bookingCardSchedule:demoDate(b.date)+' · '+demoTime(b.time),bookingCardRoute:b.pickup+' → '+b.destinations.at(-1),bookingCardPackage:'By the Hour ('+b.hours+' hours)',bookingCardFee:money(b.hours*p.rate),bookingCardStatus:b.status,bookingCardRating:drv?'★★★★★':'★ '+p.rating};
  Object.entries(texts).forEach(([id,text])=>editNative(n,id,{text}));
  editNative(n,'bookingCardImage',{src:'@drawable/'+(drv?'mika':p.photo)});
  n.action=['details',b.id];
  [['bookingCardDetails','details'],['bookingCardAccept','accept'],['bookingCardDecline','decline'],['bookingCardCancel','cancel'],['btnTrackTrip','native-track'],['btnEmergencyAccepted','native-sos']].forEach(([id,a])=>editNative(n,id,{},[a,b.id]));
  editNative(n,'btnEmergencyAccepted',{visibility:b.status==='In Progress'?'visible':'gone'});
  return n;
}
function standardNativeNav(root){
  if(!['home','bookings','messages','profile'].includes(state.screen))return;
  const old=findNative(root,'bottomNavigation');if(!old)return;
  const source=resolveNative(ANDROID.layouts[state.role==='driver'?'activity_driver_home':'activity_customer_home']);
  const bar=findNative(source,'bottomNavigation');
  // Use one hierarchy for every primary screen; only the selected item changes.
  old.c=bar.c;old.a={...old.a,layout_height:'72dp',paddingTop:'8dp',paddingBottom:'8dp',background:'#FFFFFF'};
  const targets=['home','bookings','messages','profile'];
  old.c.forEach((item,i)=>{
    item.action=['go',targets[i]];
    Object.assign(item.a,{layout_width:'0dp',layout_weight:'1',layout_height:'match_parent',orientation:'vertical',gravity:'center',padding:'0dp'});
    walkNative(item,n=>{
      if(n.tag==='ImageView')Object.assign(n.a,{layout_width:'56dp',layout_height:'32dp',paddingHorizontal:'16dp',paddingVertical:'4dp',tint:state.screen===targets[i]?'#FF7900':'#667085',background:'@android:color/transparent'});
      if(n.tag==='TextView')Object.assign(n.a,{textColor:state.screen===targets[i]?'#FF7900':'#667085',textSize:'11sp',textStyle:state.screen===targets[i]?'bold':'normal',layout_marginTop:'4dp'});
    });
  });
}
function compactNativeButtons(root){walkNative(root,n=>{
  if(!['MaterialButton','Button'].includes(n.tag))return;
  const textStyle=/TextButton/.test(n.a.style||'');
  const height=textStyle?36:n.a.layout_width==='match_parent'?48:44;
  Object.assign(n.a,{layout_height:height+'dp',minHeight:height+'dp',paddingTop:'0dp',paddingBottom:'0dp',paddingVertical:'0dp',textSize:Math.min(dp(n.a.textSize)||14,15)+'sp'});
  if(n.a.minWidth&&dp(n.a.minWidth)>84)n.a.minWidth='84dp';
})}
bindAndroidScreen=function(root){
  bindBaseAndroid(root);
  const drv=state.role==='driver',set=(id,a={},actionValue)=>editNative(root,id,a,actionValue);
  if(state.screen==='login'){
    set('dividerLeft',{layout_constraintStart_toStartOf:'parent',layout_marginStart:'32dp'});
    set('dividerRight',{layout_constraintEnd_toEndOf:'parent',layout_marginEnd:'32dp'});
  }
  if(state.screen==='bookings'){
    const groups=drv?{Requests:['Pending'],Upcoming:['Accepted','On the way','Arrived'],Completed:['Completed']}:{Pending:['Pending'],Accepted:['Accepted','On the way','Arrived','In Progress'],Completed:['Completed'],Cancelled:['Cancelled','Declined']};
    if(!(state.tab in groups)&&!(state.tab==='All'&&!drv))state.tab=drv?'Requests':'All';
    const tabs=drv?Object.keys(groups):['All',...Object.keys(groups)];
    for(const tab of tabs){
      set('tab'+tab,{},['booking-tab',tab]);
      if(drv){set('txtTab'+tab,{textColor:state.tab===tab?'#FF7900':'#667085'});set('lineTab'+tab,{visibility:state.tab===tab?'visible':'invisible'});}
      else set('tab'+tab,{background:state.tab===tab?'#FF7900':'#F3F4F6',textColor:state.tab===tab?'#FFFFFF':'#667085',cornerRadius:'18dp'});
    }
    for(const [tab,statuses] of Object.entries(groups)){
      const group=findNative(root,(drv?'content':'section')+tab);if(!group)continue;
      const list=drv&&state.verificationPending?[]:state.bookings.filter(b=>statuses.includes(b.status)).sort((a,b)=>b.date.localeCompare(a.date));
      group.a.visibility=state.tab===tab||state.tab==='All'?'visible':'gone';
      const kind=drv?{Requests:'request',Upcoming:'upcoming',Completed:'completed'}[tab]:tab.toLowerCase();
      const cards=list.map(b=>nativeBookingCard(b,kind,drv));
      if(!cards.length)cards.push(nativeText('No '+tab.toLowerCase()+' bookings.',14,{paddingVertical:'24dp',gravity:'center',textColor:'#98A2B3'}));
      if(drv)group.c=cards;else{const heading=group.c[0];group.c=[heading,...cards];set('txt'+tab+'Count',{text:String(list.length)})}
    }
    const ongoing=drv&&state.verificationPending?null:state.bookings.find(b=>b.status==='In Progress');
    set('cardOngoingTrip',{visibility:ongoing?'visible':'gone'});
    if(ongoing){set('txtOngoingCustomerName',{text:state.profile.name});set('imgOngoingCustomer',{src:'@drawable/mika'});set('txtOngoingRoute',{text:ongoing.pickup+' → '+ongoing.destinations.at(-1)});set('txtOngoingStartedTime',{text:'Started: '+demoTime(ongoing.time)});set('btnViewCurrentTrip',{},['native-track',ongoing.id])}
  }
  if(state.screen==='home'&&drv){
    set('txtViewAllRequests',{layout_width:'wrap_content',layout_weight:'0'});
    const pending=state.bookings.filter(b=>b.status==='Pending').slice(0,10);
    const requests=findNative(root,'cardBookingRequests')?.c[0];
    if(requests){const heading=requests.c[0],template=findNative(root,'requestContainer1');requests.c=[heading];for(const [i,b] of pending.entries()){
      const item=structuredClone(template);item.action=['details',b.id];
      walkNative(item,n=>{const id=nativeId(n);if(id==='imgCustomer1')n.a.src='@drawable/mika';if(id==='txtCustomer1')n.a.text=state.profile.name;if(id==='txtRequestDate1')n.a.text=demoDate(b.date)+' · '+demoTime(b.time);if(id==='txtRequestRoute1')n.a.text=b.pickup+' → '+b.destinations.at(-1);if(id==='btnAccept1')n.action=['accept',b.id];if(id==='btnDecline1')n.action=['decline',b.id];});requests.c.push(item);
    }if(!pending.length)requests.c.push(nativeText('No new booking requests.',14,{paddingVertical:'20dp'}))}
    const upcoming=state.bookings.find(b=>b.status==='Accepted');
    set('cardUpcomingBooking',{visibility:upcoming?'visible':'gone'});
    if(upcoming){const date=new Date(upcoming.date+'T12:00:00');set('txtUpcomingMonth',{text:date.toLocaleDateString('en-US',{month:'short'}).toUpperCase()});set('txtUpcomingDay',{text:String(date.getDate())});set('txtUpcomingTime',{text:demoTime(upcoming.time)});set('txtUpcomingCustomer',{text:state.profile.name});set('imgUpcomingCustomer',{src:'@drawable/mika'});set('txtUpcomingRoute',{text:upcoming.pickup+' → '+upcoming.destinations.at(-1)});set('txtBookingType',{text:'By the Hour ('+upcoming.hours+' hours)'});set('bookingInfo',{},['details',upcoming.id])}
  }
  if(state.screen==='confirm'){
    const f=state.form,p=driver();
    const values={txtDate:demoDate(f.date),txtTime:demoTime(f.time),txtDuration:f.hours+' hours',txtVehicle:f.vehicle,txtConfirmNotes:f.notes||'No additional notes',txtDriverName:p.name,txtPriceHeader:money(p.rate)+' / hour',txtRating:'★ '+p.rating,txtSubtotal:money(f.hours*p.rate),txtCalculation:money(p.rate)+' × '+f.hours+' hours',txtTotalAmount:money(f.hours*p.rate)};
    Object.entries(values).forEach(([id,text])=>set(id,{text}));set('imgDriverSmall',{src:'@drawable/'+p.photo});set('btnConfirmAndBook',{},['submit-booking']);
    const timeline=findNative(root,'reviewRouteTimeline');timeline.c=nativeRouteRows(f.pickup,f.destinations,true);
  }
  if(state.screen==='details'||state.screen==='trip-details'){
    const b=currentBooking(),p=people[b.driver],done=b.status==='Completed',closed=['Completed','Cancelled','Declined'].includes(b.status),readOnly=state.screen==='trip-details';
    const values={txtTitle:readOnly?'Trip Details':'Booking Details',txtBookingId:'Booking ID: '+b.id,txtBookingStatus:b.status,txtUpcomingMonth:new Date(b.date+'T12:00:00').toLocaleDateString('en-US',{month:'short'}).toUpperCase(),txtUpcomingDay:String(Number(b.date.slice(-2))),txtUpcomingTime:demoTime(b.time),txtCustomerName:state.profile.name,txtCustomerPhone:state.profile.phone,txtCustomerEmail:state.profile.email,txtCustomerAddress:state.profile.address,txtBookingType:'By the Hour ('+b.hours+' hours)',txtDateTime:demoDate(b.date)+' · '+demoTime(b.time),txtTotalAmount:money(b.hours*p.rate),txtDriverName:p.name,txtRating:'★ '+p.rating,txtExperience:p.exp+' years experience',txtVehicle:p.types.join(', '),txtTripDate:demoDate(b.date),txtTripTime:demoTime(b.time),txtTripDuration:b.hours+' hours',txtTripPickup:b.pickup,txtTripDestination:b.destinations.join(' → '),txtTripVehicle:b.vehicle,savedBookingVehicle:b.vehicle,savedBookingNotes:b.notes||'No additional notes',txtStatusTitle:done?'Booking Completed':b.status==='Pending'?'Booking request sent':'Booking '+b.status,txtStatusSubtitle:done?'This trip was completed on '+demoDate(b.date)+'.':b.status==='Pending'?'Waiting for the driver to respond.':b.status==='Cancelled'?'This booking has been cancelled.':'View your booking and route information.'};
    Object.entries(values).forEach(([id,text])=>set(id,{text}));set('imgDriverThumb',{src:'@drawable/'+p.photo});set('imgCustomerProfile',{src:'@drawable/mika'});
    if(drv){set('legacyRouteGraphic',{visibility:'gone'});set('legacyRouteText',{visibility:'gone'});const rows=set('savedRouteTimeline',{visibility:'visible'});rows.c=nativeRouteRows(b.pickup,b.destinations);set('btnViewMap',{},['go',done?'history':'trip']);set('cardTripInProgress',{visibility:b.status==='In Progress'?'visible':'gone'});set('cardTripCompleted',{visibility:done?'visible':'gone'});
      set('btnMessageCustomer',{},['go','chat']);set('btnViewReview',{visibility:done?'visible':'gone'},['go','reviews']);
      if(b.status==='Pending'){set('btnStartService',{text:'Accept Booking'},['accept',b.id]);set('btnMessageCustomer',{text:'Decline'},['decline',b.id]);}
      else set('btnStartService',{text:b.status==='In Progress'?'Open Trip Map':'Open Pickup Map',visibility:closed?'gone':'visible'},['go','trip']);
      if(readOnly||b.status==='Cancelled'||b.status==='Declined')set('bottomActionArea',{visibility:'gone'});
      if(b.extension){const summary=nativeText('Extension request: '+b.extension.status+'\nAdditional time: '+b.extension.minutes+' minutes',14,{textColor:'#344054',layout_marginTop:'16dp'});const amount=findNative(root,'txtTotalAmount');let parent;walkNative(root,n=>{if(n.c.some(c=>c.c?.includes(amount)))parent=n});if(parent){parent.c.push(summary);if(b.extension.status==='Pending'){const button=resolveNative(ANDROID.layouts.view_review_extension_button);button.action=['go','extension'];parent.c.push(button)}}}
    }else{
      set('btnLeft',{text:done?'Rebook Driver':'View Map'},[done?'rebook':'go',done?b.id:'trip']);set('btnRight',{text:done?'Rate Driver':'Message Driver'},[done?'rate-booking':'go',done?b.id:'chat']);
      if(done){set('completedTripDetailsCard',{visibility:'visible'});const rows=findNative(root,'completedRouteTimeline');rows.c=nativeRouteRows(b.pickup,b.destinations);set('btnViewMapHistory',{},['go','history'])}
      if(readOnly||b.status==='Cancelled'||b.status==='Declined')set('bottomButtons',{visibility:'gone'});
    }
  }
  if(state.screen==='edit'&&drv){const mobile=set('settingsMobile');if(mobile){mobile.formId='profile-phone';mobile.value=state.profile.phone}const birth=set('settingsBirthDate');if(birth)birth.value='June 15, 1995';const bio=set('settingsBio');if(bio)bio.value=people[0].bio;}
  if(state.screen==='weekly-schedule'){
    ['Monday','Tuesday','Wednesday','Thursday','Friday','Saturday','Sunday'].forEach((day,i)=>{const schedule=state.schedule.find(s=>s.day===day)||{day,start:'06:00',end:'18:00',enabled:false};if(!state.schedule.some(s=>s.day===day))state.schedule.push(schedule);set('scheduleDay'+(i+1)+'Part1',{checked:String(schedule.enabled)},['native-day',day]);set('scheduleDay'+(i+1)+'Part2',{text:demoTime(schedule.start)},['native-schedule-time',day+'|start']);set('scheduleDay'+(i+1)+'Part4',{text:demoTime(schedule.end)},['native-schedule-time',day+'|end']);});set('btnSaveSchedule',{},['native-save-schedule']);
  }
  if(state.screen==='filter'){
    const pickup=findNative(root,'btnVehiclePickup');let holder;walkNative(root,n=>{if(n.c.includes(pickup))holder=n});if(holder){const truck=structuredClone(pickup);truck.a.id='@+id/btnVehicleTruck';truck.action=['filter-vehicle','Truck'];walkNative(truck,n=>{if(n.a.text==='Pickup')n.a.text='Truck'});holder.c.push(truck)}
  }
  if(state.screen==='messages'){
    set('tabMain',{},['native-message-tab','all']);set('tabUnread',{},['native-message-tab','unread']);
    set('txtMain',{textColor:state.messageTab==='unread'?'#667085':'#FF7900'});set('txtUnread',{textColor:state.messageTab==='unread'?'#FF7900':'#667085'});set('mainIndicator',{visibility:state.messageTab==='unread'?'invisible':'visible'});set('unreadIndicator',{visibility:state.messageTab==='unread'?'visible':'invisible'});
    const list=findNative(root,'chatListContainer');
    list.c=state.messageTab==='unread'?[nativeText('No unread conversations.',14,{gravity:'center',paddingVertical:'36dp'})]:[{tag:'LinearLayout',a:{layout_width:'match_parent',layout_height:'wrap_content',orientation:'horizontal',padding:'18dp',gravity:'center_vertical'},action:['go','chat'],c:[{tag:'ShapeableImageView',a:{layout_width:'56dp',layout_height:'56dp',src:'@drawable/'+(drv?'mika':driver().photo),scaleType:'centerCrop',shapeAppearanceOverlay:'@style/CircleImage'},c:[]},{tag:'LinearLayout',a:{layout_width:'0dp',layout_weight:'1',layout_height:'wrap_content',layout_marginStart:'14dp',orientation:'vertical'},c:[nativeText(drv?state.profile.name:driver().name,16,{textColor:'#061B41',textStyle:'bold'}),nativeText(state.messages.at(-1)?.text||'Start a conversation',13,{layout_marginTop:'6dp',maxLines:'2'})]}]}];
  }
  walkNative(root,n=>{if(n.tag==='TextView'&&resource(n.a.text).trim()==='›'&&n.a.layout_weight){n.a.layout_weight='0';n.a.layout_width='24dp'}});
  standardNativeNav(root);compactNativeButtons(root);
};
document.addEventListener('click',e=>{const el=e.target.closest('[data-action]');if(!el)return;const a=el.dataset.action,v=el.dataset.value;if(a==='native-track'||a==='native-sos'){state.selected=v;go(a==='native-track'?'trip':'sos')}if(a==='native-message-tab'){state.messageTab=v;render()}});
document.addEventListener('click',e=>{const el=e.target.closest('[data-action]');if(!el)return;const a=el.dataset.action,v=el.dataset.value;
  if(a==='native-day'){const day=state.schedule.find(s=>s.day===v);day.enabled=!day.enabled;render()}
  if(a==='native-schedule-time'){const [day,key]=v.split('|');modal('Select '+(key==='start'?'start':'end')+' time',field('Time','schedule-choice',state.schedule.find(s=>s.day===day)[key],'time'),btn('Cancel','close','','outline')+btn('Apply','native-save-time',v))}
  if(a==='native-save-time'){const [day,key]=v.split('|'),value=$('#schedule-choice').value;if(!value)return;state.schedule.find(s=>s.day===day)[key]=value;closeModal();render()}
  if(a==='native-save-schedule'){if(state.schedule.some(s=>s.enabled&&s.start>=s.end)){toast('End time must be later than start time.');return}go('schedule');toast('Weekly schedule saved.')}
});
render();
