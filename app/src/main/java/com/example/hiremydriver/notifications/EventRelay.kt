package com.example.hiremydriver.notifications

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import com.example.hiremydriver.CustomerAuth
import com.example.hiremydriver.DriverAuth
import com.google.android.gms.tasks.Tasks
import org.json.JSONObject
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection

/** Durable delivery hints only. The server obtains the event, recipients and text from Firestore. */
object EventRelay {
    private const val JOB = 7482
    private val worker = Executors.newSingleThreadExecutor()
    private var running = false
    private fun prefs(c: Context) = c.getSharedPreferences("notification_outbox", Context.MODE_PRIVATE)
    fun enqueue(c: Context, uid: String, driver: Boolean, kind: String, bookingId: String, event: String) {
        val payload = JSONObject().put("uid", uid).put("driver", driver).put("kind", kind).put("bookingId", bookingId)
            .put(if (kind == "message") "messageId" else "phase", event)
        prefs(c).edit().putString("$uid:$kind:$bookingId:$event", payload.toString()).commit()
        schedule(c); flush(c.applicationContext) {}
    }
    fun schedule(c: Context) {
        if (prefs(c).all.isEmpty()) return
        c.getSystemService(JobScheduler::class.java).schedule(JobInfo.Builder(JOB, ComponentName(c, NotificationRelayJob::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true)
            .setBackoffCriteria(30000, JobInfo.BACKOFF_POLICY_EXPONENTIAL).build())
    }
    @Synchronized fun flush(c: Context, done: (Boolean) -> Unit) {
        if (running) { done(true); return }
        running = true
        worker.execute {
            try {
                prefs(c).all.forEach { (key, raw) ->
                    val p = runCatching { JSONObject(raw as String) }.getOrNull() ?: return@forEach
                    val user = if (p.optBoolean("driver")) DriverAuth.currentUser(c) else CustomerAuth.currentUser(c)
                    if (user?.uid != p.optString("uid")) { prefs(c).edit().remove(key).commit(); return@forEach }
                    runCatching {
                        val token = Tasks.await(user.getIdToken(false), 20, TimeUnit.SECONDS).token ?: error("No session")
                        val connection = URL("https://hiremydriver-dashboard.vercel.app/api/notifications/relay").openConnection() as HttpsURLConnection
                        try {
                            connection.requestMethod = "POST"; connection.connectTimeout = 15000; connection.readTimeout = 20000
                            connection.setRequestProperty("Authorization", "Bearer $token")
                            connection.setRequestProperty("Content-Type", "application/json"); connection.doOutput = true
                            connection.outputStream.use { it.write(p.toString().toByteArray(Charsets.UTF_8)) }
                            val code = connection.responseCode
                            if (code in 200..299 || code == 400 || code == 403) prefs(c).edit().remove(key).commit()
                        } finally { connection.disconnect() }
                    }
                }
            } finally { synchronized(this) { running = false }; done(prefs(c).all.isNotEmpty()) }
        }
    }
}
class NotificationRelayJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean { EventRelay.flush(applicationContext) { retry -> jobFinished(params, retry) }; return true }
    override fun onStopJob(params: JobParameters) = true
}
