/* Original Android list pages and presentation-only state. */
Object.assign(androidScreens,{'requests-list':'activity_driver_booking_requests','upcoming-schedule':'activity_driver_upcoming_schedule'});
Object.defineProperty(androidScreens,'reviews',{get:()=>state.role==='customer'&&state.reviewOwn?'activity_customer_reviews':undefined});

function seedOwnReviews(){
  if(state.ownReviewsSeeded)return;state.ownReviewsSeeded=true;
  state.reviews.push(
    {own:true,driver:0,name:state.profile.name,rating:5,text:'Professional, punctual, and a very comfortable trip.',photo:'mika',date:'2026-10-20',bookingId:'HMD-1020',pickup:'Sikatuna Municipal Hall',destination:'Loboc River',hours:3},
    {own:true,driver:1,name:state.profile.name,rating:4,text:'A safe drive and helpful service during our city trip.',photo:'mika',date:'2026-10-18',bookingId:'HMD-1018',pickup:'Tagbilaran City',destination:'Panglao',hours:2}
  );
}
for(const entry of [
 ['requests-list','driver','Booking','New booking requests','All pending requests with Accept and Decline actions.'],
 ['upcoming-list','driver','Booking','Upcoming bookings','Accepted bookings in their own list.'],
 ['upcoming-schedule','driver','Account','Upcoming schedule','Accepted trips arranged by date and time.']
]){if(!screens.some(s=>s[0]===entry[0]))screens.push(entry);}
const followupBind=bindAndroidScreen;
bindAndroidScreen=function(root){
  followupBind(root);seedOwnReviews();
  const set=(id,a={},act)=>editNative(root,id,a,act),parentOf=child=>{let p;walkNative(root,n=>{if(n.c.includes(child))p=n});return p};
  if(state.screen==='welcome'){set('btnDriver',{layout_marginBottom:'32dp'});}
  if(state.screen==='settings'){
    const languageKey={tl:'lang_tagalog',ilo:'lang_ilocano'}[state.languageCode]||'lang_english';
    set('driverSettingsLanguageSummary',{text:'@string/'+languageKey});
    const languageRow=findNative(root,'menuLanguage');
    if(languageRow)walkNative(languageRow,n=>{if(n.a.text==='@string/lang_english')n.a.text='@string/'+languageKey});
  }
  if(state.screen==='language'){
    ['English','Tagalog','Ilocano'].forEach(name=>set('rb'+name,{layout_height:'wrap_content',minHeight:'64dp',paddingVertical:'16dp'}));
  }
  if(state.role==='driver'&&['home','bookings','messages','profile'].includes(state.screen)){
    const labels={Home:'nav_home',Booking:'nav_bookings',Message:'nav_messages',Profile:'nav_profile'};
    const nav=findNative(root,'bottomNavigation');
    if(nav)walkNative(nav,n=>{if(labels[n.a.text])n.a.text='@string/'+labels[n.a.text]});
  }
  if(state.screen==='home'&&state.role==='driver'){
    const labels={'Good Morning,':'driver_greeting','Verified Driver':'driver_verified_label',Availability:'availability',Pending:'tab_pending',Completed:'tab_completed',Rating:'driver_rating_label','Upcoming Booking':'driver_upcoming_label','New Booking Requests':'driver_requests_label','View All':'see_all',Accept:'driver_accept_action',Decline:'driver_decline_action','No new booking requests.':'driver_no_requests'};
    walkNative(root,n=>{if(labels[n.a.text])n.a.text='@string/'+labels[n.a.text]});
    set('txtAvailabilityStatus',{text:state.verificationPending?'Your documents are awaiting verification.':state.available?'@string/driver_available_message':'@string/driver_offline_message'});
    set('dotAvailability',{visibility:'gone'});
  }
  if(state.screen==='home'&&state.role==='driver')set('txtViewAllRequests',{},['go','requests-list']);
  if(state.screen==='schedule')set('rowUpcomingSchedule',{},['go','upcoming-schedule']);
  if(state.screen==='driving'){
    state.spokenLanguages??=['Filipino','English','Cebuano/Bisaya'];
    [['chkFilipino','Filipino'],['chkEnglish','English'],['chkCebuano','Cebuano/Bisaya']].forEach(([id,label])=>set(id,{checked:String(state.spokenLanguages.includes(label)),contentDescription:label},['spoken-language',label]));
  }
  if(state.screen==='requests-list'){
    const list=set('requestsContainer');list.c=state.bookings.filter(b=>b.status==='Pending').map(b=>{
      const card=resolveNative(ANDROID.layouts.item_driver_request_full);
      const vals={bookingCardName:state.profile.name,bookingCardSchedule:demoDate(b.date)+' · '+demoTime(b.time),bookingCardRoute:b.pickup+' → '+b.destinations.at(-1),bookingCardPackage:'By the Hour ('+b.hours+' hours) · Offered: '+money(b.hours*people[b.driver].rate)};
      Object.entries(vals).forEach(([id,text])=>editNative(card,id,{text}));
      editNative(card,'bookingCardImage',{src:'@drawable/mika'});card.action=['details',b.id];
      editNative(card,'bookingCardAccept',{layout_height:'48dp'},['accept',b.id]);editNative(card,'bookingCardDecline',{layout_height:'48dp'},['decline',b.id]);return card;
    });if(!list.c.length)list.c=[nativeText('No new booking requests.',14,{padding:'20dp',gravity:'center'})];
  }
  if(state.screen==='upcoming-schedule'){
    const card=findNative(root,'cardSchedule1'),container=parentOf(card),template=structuredClone(card);
    container.c=state.bookings.filter(b=>['Accepted','On the way','Arrived'].includes(b.status)).sort((a,b)=>(a.date+a.time).localeCompare(b.date+b.time)).map(b=>{
      const n=structuredClone(template);n.action=['details',b.id];const texts=[];walkNative(n,c=>{if(c.tag==='TextView')texts.push(c)});
      const date=new Date(b.date+'T12:00:00'),end=new Date(b.date+'T'+b.time);end.setHours(end.getHours()+b.hours);
      const values=[date.toLocaleDateString('en-US',{month:'short'}).toUpperCase(),String(date.getDate()),demoTime(b.time)+' – '+end.toLocaleTimeString('en-US',{hour:'numeric',minute:'2-digit'}),state.profile.name,b.pickup+' → '+b.destinations.at(-1),'By the Hour ('+b.hours+' hours)',b.status,'›'];
      texts.forEach((t,i)=>{t.a.text=values[i]||t.a.text});const day=texts[1],body=parentOfIn(n,day);if(body)Object.assign(body.a,{layout_height:'0dp',layout_weight:'1',gravity:'center'});return n;
    });if(!container.c.length)container.c=[nativeText('No upcoming trips.',14,{padding:'20dp',gravity:'center'})];
  }
  if(state.screen==='reviews'&&state.role==='customer'&&state.reviewOwn){
    set('txtReviewsTitle',{text:'My Reviews'});set('txtReviewsIntro',{text:"Here are the drivers you've reviewed."});set('btnBack',{contentDescription:'Back'});
    const intro=findNative(root,'txtReviewsIntro'),container=parentOf(intro);
    const reviews=state.reviews.filter(r=>!r.sample).sort((a,b)=>(b.date||'').localeCompare(a.date||''));
    container.c=[intro,...reviews.map(r=>{
      const card=resolveNative(ANDROID.layouts.item_customer_review),p=people[r.driver??0];
      const values={txtReviewDriverName:p.name,txtReviewDate:demoDate(r.date||'2026-10-24'),txtReviewRatingNum:r.rating.toFixed(1),txtReviewComment:r.text,txtReviewRoute:(r.pickup||'Sikatuna')+' → '+(r.destination||'Bohol'),txtReviewType:'By the hour · '+(r.hours||3)+' hours'};
      Object.entries(values).forEach(([id,text])=>editNative(card,id,{text}));editNative(card,'imgDriverThumb',{src:'@drawable/'+p.photo});editNative(card,'btnMoreReview',{contentDescription:'Review options for '+p.name},['own-review-options',String(state.reviews.indexOf(r))]);
      const fifth=findNative(card,'imgStar5'),stars=parentOfIn(card,fifth);let star=0;stars.c.forEach(c=>{if(c.tag==='ImageView')c.a.tint=++star<=r.rating?'#FF8A00':'#D0D5DD'});return card;
    })];
    if(!reviews.length)container.c.push(nativeText('No reviews yet. You can rate a driver after the service is completed.',14,{padding:'16dp'}));
    set('bottomNavigation',{visibility:'gone'});
    walkNative(root,n=>{if(/ScrollView/.test(n.tag)){delete n.a.layout_constraintBottom_toTopOf;n.a.layout_constraintBottom_toBottomOf='parent'}});
  }
  // Use geometric circles instead of font glyphs; align the marker rail to the first text line.
  walkNative(root,n=>{
    if(n.a.text==='●'&&n.a.textColor==='#FF7900'){n.tag='View';Object.assign(n.a,{text:'',layout_width:'10dp',layout_height:'10dp',layout_marginTop:'5dp',layout_marginStart:'5dp',layout_marginEnd:'5dp',background:'@drawable/bg_orange_dot'});}
    if(nativeId(n)==='routeDot'){const rail=parentOf(n);rail.tag='ConstraintLayout';Object.assign(n.a,{layout_constraintStart_toStartOf:'parent',layout_constraintEnd_toEndOf:'parent',layout_constraintTop_toTopOf:'parent'});const line=findNative(rail,'routeConnector');if(line)Object.assign(line.a,{layout_width:'2dp',layout_height:'0dp',layout_marginTop:'10dp',layout_constraintStart_toStartOf:'parent',layout_constraintEnd_toEndOf:'parent',layout_constraintTop_toTopOf:'parent',layout_constraintBottom_toBottomOf:'parent'});}
  });
};
function parentOfIn(root,child){let p;walkNative(root,n=>{if(n.c.includes(child))p=n});return p}
const followupPerform=perform;
perform=function(a,v='',el){
  if((a==='go'||a==='jump')&&v==='language')state.language={tl:'Tagalog',ilo:'Ilocano'}[state.languageCode]||'English';
  if(a==='demo-save-language'){
    state.languageCode={Tagalog:'tl',Ilocano:'ilo'}[state.language]||'en';
    history=[];go('home',false);return;
  }
  if((a==='go'||a==='jump')&&v==='reviews'&&state.role==='customer'){state.reviewOwn=true;state.hideMyReviewNav=true;}
  if(a==='spoken-language'){state.spokenLanguages??=[];const selected=!state.spokenLanguages.includes(v);state.spokenLanguages=selected?[...state.spokenLanguages,v]:state.spokenLanguages.filter(x=>x!==v);el.setAttribute('aria-checked',String(selected));el._node.a.checked=String(selected);return}
  if(a==='own-review-options'){modal('Review options',btn('Edit Review','own-review-edit',v,'outline'),btn('Cancel','close','','outline'));return}
  if(a==='own-review-edit'){const r=state.reviews[Number(v)];modal('Edit Review',`<label class="field"><span>Rating</span><select id="own-review-rating">${[5,4,3,2,1].map(n=>`<option value="${n}" ${n===r.rating?'selected':''}>${n} stars</option>`).join('')}</select></label><label class="field"><span>Your review</span><textarea id="own-review-text" maxlength="500">${esc(r.text)}</textarea></label>`,btn('Cancel','close','','outline')+btn('Save Changes','own-review-save',v));return}
  if(a==='own-review-save'){const text=$('#own-review-text').value.trim();if(!text)return toast('Please share a short review.');Object.assign(state.reviews[Number(v)],{text,rating:Number($('#own-review-rating').value)});closeModal();render();return}
  if(a==='submit-review'){
    const booking=currentBooking(),old=state.reviews.find(r=>!r.sample&&r.bookingId===booking.id),before=state.reviews.length;
    const result=followupPerform(a,v,el);if(state.reviews.length>before){Object.assign(state.reviews[0],{own:true,bookingId:booking.id,driver:booking.driver,date:'2026-10-24',pickup:booking.pickup,destination:booking.destinations.at(-1),hours:booking.hours});if(old)state.reviews.splice(state.reviews.indexOf(old),1);}return result;
  }
  return followupPerform(a,v,el);
};
const followupRender=render;
render=function(){document.querySelector('.screen').classList.toggle('welcome-full',state.screen==='welcome');followupRender();};
render();

// Keep the presentation's destination flow and map interactions aligned with the app.
(() => {
  const previousPerform = perform, previousRender = render, previousSheet = tripSheetBody, previousMapArt = mapArt;
  const previousBind=bindAndroidScreen;
  bindAndroidScreen=function(root){previousBind(root);if(state.role==='driver'&&state.verificationPending){['cardOngoingTrip','cardUpcomingBooking','cardBookingRequests'].forEach(id=>{const n=findNative(root,id);if(n)n.a.visibility='gone';});}};
  let requestedPlace = '', zoom = 1;
  perform = function(a,v='',el){
    if(state.screen==='picker' && $('#pin-name')) pickerName=$('#pin-name').value;
    if(a==='confirm-pin' && pickerTarget==='extra'){
      requestedPlace=pickerName.trim();
      if(!requestedPlace)return toast('Enter the place name or landmark.');
      go('trip');
      modal('Request another destination?',`<p>${esc(requestedPlace)}</p><p class="small-text">Your current destinations stay unchanged until the driver accepts. The service timer continues.</p>`,btn('Cancel','close','','outline')+btn('Request destination','confirm-extra-stop'));
      return;
    }
    if(a==='confirm-extra-stop'){
      const b=currentBooking();if(b.extra||b.status!=='In Progress')return closeModal();
      b.extra=requestedPlace;notify('driver','New destination request','The customer requested another stop: '+b.extra);
      closeModal();render();toast('Destination request sent.');return;
    }
    if(a==='review-extra-stop'){go('destination');return;}
    if(a==='extra-accept'||a==='extra-decline'){
      if(!currentBooking().extra)return;
      previousPerform(a,v,el);go('trip',false);toast(a==='extra-accept'?'Destination accepted':'Destination declined');return;
    }
    if(a==='locate'){pin={x:0,y:0};zoom=1;render();return;}
    return previousPerform(a,v,el);
  };
  tripSheetBody=function(b,driverView){
    if(driverView&&state.screen==='destination'&&b.extra)return `<p class="trip-state">Customer requested a new destination</p><h2 class="trip-heading">One more stop.</h2><p class="trip-caption">Your customer would like to add a destination.</p><h3 class="requested-place">${esc(b.extra)}</h3><p class="trip-caption">After ${esc(b.destinations.at(-1))}</p><div class="destination-service"><div><small>REMAINING SERVICE</small><b>${b.hours}h 00m</b></div><div><small>BOOKED DURATION</small><b>${b.hours} hours</b></div></div><p class="trip-time">Check the new stop before accepting. Your current service timer continues.</p><div class="trip-actions">${btn('Accept destination','extra-accept')}${btn('Decline change','extra-decline','','outline')}</div>`;
    return previousSheet(b,driverView);
  };
  // A connected demo route grows when the driver accepts another destination.
  mapArt=function(showRoute=true){
    if(!showRoute)return previousMapArt(false);
    const b=currentBooking(),points=[[350,360],[230,535],[98,725],[310,620],[380,480],[280,260]];
    const count=b.destinations.length+1;
    while(points.length<count)points.push([150+(points.length%3)*80,300+(points.length%4)*90]);
    const routePoints=points.slice(0,count),names=[b.pickup,...b.destinations];
    const line=routePoints.map(p=>p.join(',')).join(' ');
    return previousMapArt(false).replace('</svg>',`<polyline points="${line}" fill="none" stroke="white" stroke-width="10" stroke-linejoin="round"/><polyline points="${line}" fill="none" stroke="#3d89db" stroke-width="5" stroke-linejoin="round"/>${routePoints.map(([x,y],i)=>`<g><title>${esc(names[i])}</title><circle cx="${x}" cy="${y}" r="12" fill="${i?'#ff7900':'#061b41'}" stroke="white" stroke-width="3"/><text x="${x}" y="${y+4}" text-anchor="middle" fill="white" font-size="11">${i||'P'}</text></g>`).join('')}<circle cx="302" cy="355" r="9" fill="#1682fa" stroke="white" stroke-width="4"/></svg>`);
  };
  bindMap=function(){
    const canvas=$('#map-canvas');if(!canvas)return;
    let drag=null;const scale=()=>parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--phone-scale'))||1;
    const paint=()=>{canvas.querySelector('.map-art').style.transform=`translate(${pin.x}px,${pin.y}px) scale(${zoom})`;};paint();
    canvas.addEventListener('pointerdown',e=>{drag={x:e.clientX,y:e.clientY,px:pin.x,py:pin.y};canvas.setPointerCapture(e.pointerId);e.preventDefault();});
    canvas.addEventListener('pointermove',e=>{if(!drag)return;pin.x=Math.max(-240,Math.min(240,drag.px+(e.clientX-drag.x)/scale()));pin.y=Math.max(-320,Math.min(320,drag.py+(e.clientY-drag.y)/scale()));paint();});
    const end=()=>drag=null;canvas.addEventListener('pointerup',end);canvas.addEventListener('pointercancel',end);
    canvas.addEventListener('wheel',e=>{e.preventDefault();zoom=Math.max(1,Math.min(2.5,zoom-e.deltaY*.002));paint();},{passive:false});
  };
  function alignReviewRoute(){
    const rows=Array.from(document.querySelectorAll('[data-nid="demoReviewRoutePoint"]'));
    rows.forEach((row,i)=>{
      const dot=row.querySelector('[data-nid="routeDot"]'),next=rows[i+1]?.querySelector('[data-nid="routeDot"]');
      if(!dot)return;const r=row.getBoundingClientRect(),d=dot.getBoundingClientRect(),scale=r.width/row.offsetWidth;
      row.style.setProperty('--connector-x',((d.left+d.width/2-r.left)/scale-1)+'px');
      row.style.setProperty('--connector-y',((d.top+d.height/2-r.top)/scale)+'px');
      if(next){const n=next.getBoundingClientRect();row.style.setProperty('--connector-height',((n.top+n.height/2-d.top-d.height/2)/scale)+'px');}
    });
  }
  render=function(){
    previousRender();
    const map=$('.map-screen');
    if(map){
      if(state.screen==='picker'&&pickerTarget==='extra')$('.map-title').textContent='Add destination';
      if(state.role==='driver'&&state.screen==='destination'&&currentBooking().extra)$('.map-title').innerHTML='<small>DRIVER WORKSPACE</small>Destination change';
      if(currentBooking().extra){
        const card=Array.from(map.querySelectorAll('.card')).find(c=>c.querySelector('h4')?.textContent==='Destination request');
        if(card&&state.role==='driver')card.innerHTML=`<h4>New destination request</h4><p>${esc(currentBooking().extra)}</p>${btn('Review destination on map','review-extra-stop','','outline small')}`;
        const add=map.querySelector('[data-action="new-destination"]');if(add)add.disabled=true;
      }
    }
    requestAnimationFrame(alignReviewRoute);
  };
  window.addEventListener('resize',()=>requestAnimationFrame(alignReviewRoute));
  render();
})();
