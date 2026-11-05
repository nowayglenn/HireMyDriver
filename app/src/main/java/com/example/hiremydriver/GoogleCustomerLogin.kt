package com.example.hiremydriver

import android.content.Intent
import android.os.CancellationSignal
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.credentials.*
import androidx.credentials.exceptions.*
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.example.hiremydriver.trip.BookingDialogs
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

/** Explicit Google button flow; Firebase verifies tokens and existing accounts keep their UID. */
class GoogleCustomerLogin(private val activity: AppCompatActivity, private val button: MaterialButton,
    private val otherButtons: List<android.view.View> = emptyList()) : DefaultLifecycleObserver {
    private var busy = false
    private var cancellation: CancellationSignal? = null
    init {
        activity.lifecycle.addObserver(this)
        button.setOnClickListener { start() }
    }
    private fun setBusy(value: Boolean) { busy = value; button.isEnabled = !value; otherButtons.forEach { it.isEnabled = !value } }
    private fun alive() = !activity.isFinishing && !activity.isDestroyed
    private fun error(message: String, retry: Boolean = true) {
        setBusy(false)
        if (alive()) BookingDialogs.confirm(activity, "Could not sign in", message,
            if (retry) "Try again" else "Use email login", "Cancel") { if (retry) start() }
    }
    private fun start() {
        if (busy || !alive()) return
        val services = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(activity)
        if (services != ConnectionResult.SUCCESS) {
            val message = when (services) {
                ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED ->
                    "Update Google Play services on this phone before using Google sign-in. You can also sign in with your email and password."
                ConnectionResult.SERVICE_DISABLED ->
                    "Google Play services is disabled. Enable it in your phone settings, or sign in with your email and password."
                ConnectionResult.SERVICE_UPDATING ->
                    "Google Play services is updating. Please wait until the update finishes, or sign in with your email and password."
                else ->
                    "Google sign-in needs Google Play services, which is not available on this phone. Please sign in or sign up with your email and password."
            }
            error(message, retry = false)
            return
        }
        if (!NetworkStatus.requireConnection(activity) { start() }) return
        val id = activity.resources.getIdentifier("default_web_client_id", "string", activity.packageName)
        if (id == 0) { error("Google sign-in is not configured yet. Please finish the Firebase Google provider setup."); return }
        setBusy(true)
        val request = GetCredentialRequest.Builder().addCredentialOption(
            GetSignInWithGoogleOption.Builder(activity.getString(id)).build()).build()
        cancellation = CancellationSignal()
        CredentialManager.create(activity).getCredentialAsync(activity, request, cancellation,
            ContextCompat.getMainExecutor(activity), object : CredentialManagerCallback<GetCredentialResponse, GetCredentialException> {
                override fun onError(e: GetCredentialException) {
                    if (e is GetCredentialCancellationException) setBusy(false)
                    else {
                        // Do not log provider messages, credentials, tokens, or account details.
                        android.util.Log.w("GoogleCustomerLogin", "Credential request failed: ${e.javaClass.simpleName}")
                        error(when (e) {
                            is NoCredentialException ->
                                "No Google account is available for sign-in. Add a Google account in your phone settings, then try again."
                            is GetCredentialProviderConfigurationException, is GetCredentialUnsupportedException ->
                                "Google sign-in is unavailable on this phone. Please sign in or sign up with your email and password."
                            else -> "Google sign-in could not finish. Please try again or sign in with your email and password."
                        }, retry = e !is GetCredentialProviderConfigurationException && e !is GetCredentialUnsupportedException)
                    }
                }
                override fun onResult(result: GetCredentialResponse) {
                    val token = runCatching { GoogleIdTokenCredential.createFrom(result.credential.data).idToken }.getOrNull()
                    if (token == null) { error("Google did not return a valid sign-in. Please try again."); return }
                    CustomerAuth.signInGoogle(activity.applicationContext, token).addOnCompleteListener { task ->
                        if (!alive()) return@addOnCompleteListener
                        if (!task.isSuccessful) { error(CustomerAuth.message(task.exception)); return@addOnCompleteListener }
                        val user = task.result
                        FirebaseFirestore.getInstance().collection("users").document(user.uid).get(Source.SERVER)
                            .addOnSuccessListener { profile ->
                                if (!alive()) return@addOnSuccessListener
                                if (profile.exists() && profile.getString("role") != "customer") {
                                    CustomerAuth.signOut(activity); error("This is a driver account. Please use I'm a Driver.")
                                } else if (profile.exists()) finishProfile(user, profile.getString("fullName").orEmpty(), profile.getString("phoneNumber").orEmpty())
                                else completeProfile(user)
                            }.addOnFailureListener { error("Could not load your profile. Check your connection and try again.") }
                    }
                }
            })
    }
    private fun completeProfile(user: com.google.firebase.auth.FirebaseUser) {
        val view = activity.layoutInflater.inflate(R.layout.dialog_google_profile, null)
        val name = view.findViewById<android.widget.EditText>(R.id.googleProfileName).apply { setText(user.displayName.orEmpty()) }
        val phone = view.findViewById<android.widget.EditText>(R.id.googleProfilePhone)
        val terms = view.findViewById<android.widget.CheckBox>(R.id.googleProfileTerms)
        val dialog = BookingDialogs.custom(activity, view)
        var completing = false
        dialog.setOnDismissListener { if (!completing) { CustomerAuth.signOut(activity); setBusy(false) } }
        view.findViewById<TextView>(R.id.googleProfileTermsLink).setOnClickListener { activity.startActivity(Intent(activity, TermsConditionsActivity::class.java)) }
        view.findViewById<android.view.View>(R.id.googleProfileCancel).setOnClickListener { dialog.dismiss() }
        view.findViewById<android.view.View>(R.id.googleProfileContinue).setOnClickListener {
            val fullName = name.text.toString().trim()
            val number = phone.text.toString().trim()
            if (fullName.isBlank()) { name.error = "Full name is required"; return@setOnClickListener }
            if (number.count(Char::isDigit) !in 10..15) { phone.error = "Enter a valid phone number"; return@setOnClickListener }
            if (!terms.isChecked) { terms.error = "Please agree to the terms"; return@setOnClickListener }
            completing = true; dialog.dismiss(); finishProfile(user, fullName, number)
        }
    }
    private fun finishProfile(user: com.google.firebase.auth.FirebaseUser, name: String, phone: String) {
        CustomerAuth.completeGoogleProfile(activity.applicationContext, user, name, phone).addOnCompleteListener { task ->
            if (!alive()) return@addOnCompleteListener
            if (!task.isSuccessful) { error("Could not save your customer profile. Check your connection and try again."); return@addOnCompleteListener }
            activity.startActivity(Intent(activity, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION))
            activity.finish()
        }
    }
    override fun onDestroy(owner: LifecycleOwner) { cancellation?.cancel() }
}
