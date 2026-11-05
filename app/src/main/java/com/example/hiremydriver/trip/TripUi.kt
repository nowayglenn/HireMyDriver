package com.example.hiremydriver.trip

import com.example.hiremydriver.maps.TripMapController
import java.text.DateFormat
import java.util.Date
import java.util.Locale

object TripUi {
    fun status(t:TripSession)=when(t.phase){
        TripPhase.REQUESTED->"Waiting for driver acceptance"
        TripPhase.ACCEPTED->"Accepted"
        TripPhase.ON_THE_WAY->"Driver on the way"
        TripPhase.ARRIVED->"Driver has arrived"
        TripPhase.IN_PROGRESS->"In Progress"
        TripPhase.COMPLETED->"Service completed"
        TripPhase.CANCELLED -> "Booking cancelled"; TripPhase.DECLINED->"Booking declined"
    }
    fun timer(t:TripSession,now:Long=System.currentTimeMillis()):String {
        if(t.phase==TripPhase.DECLINED)return "This booking did not start"
        val elapsed = t.elapsedMillis(now)
        if (elapsed == null) return if (t.phase == TripPhase.COMPLETED) "Service completed · Duration unavailable"
            else "${t.durationHours}-hour service · Timer starts after pickup"
        val seconds = elapsed / 1000
        return String.format(Locale.getDefault(), "%dh %02dm %02ds · %s", seconds/3600, (seconds%3600)/60, seconds%60,
            if (t.phase == TripPhase.COMPLETED) "Completed" else "Elapsed")
    }
    fun route(t:TripSession)= (listOf("Pickup: ${t.pickup?.label?:t.pickupLabel}")+if(t.destinations.isEmpty())listOf("Destination: ${t.destinationLabel}") else t.destinations.mapIndexed{i,p->"${i+1}. ${p.label}${if(i<t.reachedStops)" · Visited" else ""}"}).joinToString("\n")
    fun locationStatus(t:TripSession):String = t.locationAt?.let { "Last driver position: ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it))}" }?:"Driver location not available yet"
    fun pins(t:TripSession):List<TripMapController.Pin> = buildList {
        t.pickup?.let{add(TripMapController.Pin(it,"Customer pickup","P",TripMapController.ORANGE))}
        t.destinations.forEachIndexed{i,p->add(TripMapController.Pin(p,"Destination ${i+1}","${i+1}",TripMapController.ORANGE))}
        t.driverLocation?.let{add(TripMapController.Pin(it,"Last recorded driver position","D",TripMapController.NAVY))}
    }
}
