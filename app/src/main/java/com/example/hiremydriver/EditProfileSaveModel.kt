package com.example.hiremydriver

import android.content.Context
import android.net.Uri
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.UserProfileChangeRequest

internal data class EditProfileSaveState(val saving: Boolean = false, val saved: Boolean = false, val error: String? = null)

/** Retains the save across rotation and prevents duplicate uploads from repeated taps. */
internal class EditProfileSaveModel : ViewModel() {
    val state = MutableLiveData(EditProfileSaveState())
    private var cleared = false

    fun save(context: Context, uid: String, photo: Uri?, name: String, phone: String, address: String, birthDate: String) {
        if (state.value?.saving == true || state.value?.saved == true) return
        if (name.length !in 1..200 || phone.length > 32 || address.length > 1000) {
            state.value = EditProfileSaveState(error = "Check your name, phone number and address lengths.")
            return
        }
        val app = context.applicationContext
        val user = CustomerAuth.currentUser(app)
        if (user == null || user.uid != uid) {
            state.value = EditProfileSaveState(error = "Your account changed. Please sign in again.")
            return
        }
        state.value = EditProfileSaveState(saving = true)
        CustomerProfilePhoto.save(app, uid, photo).continueWithTask { upload ->
            if (!upload.isSuccessful) throw upload.exception ?: IllegalStateException()
            if (CustomerAuth.currentUser(app)?.uid != uid) throw PhotoStorageFailure("Your account changed. Please sign in again.")
            user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name).build())
        }.continueWithTask { profile ->
            if (!profile.isSuccessful) throw profile.exception ?: IllegalStateException()
            if (CustomerAuth.currentUser(app)?.uid != uid) throw PhotoStorageFailure("Your account changed. Please sign in again.")
            com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users").document(uid)
                .collection("settings").document("profile").set(mapOf("name" to name, "phone" to phone, "address" to address))
        }.addOnCompleteListener { result ->
            if (CustomerAuth.currentUser(app)?.uid != uid) {
                if (!cleared) state.value = EditProfileSaveState(error = "Your account changed. Please sign in again.")
                return@addOnCompleteListener
            }
            if (result.isSuccessful) {
                app.getSharedPreferences("customer_profile_$uid", Context.MODE_PRIVATE).edit()
                    .putString("name", name).putString("phone", phone).putString("address", address)
                    .putString("birthDate", birthDate).apply()
                com.example.hiremydriver.trip.CustomerBookingProfile.sync(app, com.example.hiremydriver.trip.LocalTripStore(app).all())
            }
            if (!cleared) {
                val photoError = generateSequence<Throwable>(result.exception) { it.cause }
                    .filterIsInstance<PhotoStorageFailure>().firstOrNull()?.message
                state.value = if (result.isSuccessful) EditProfileSaveState(saved = true)
                    else EditProfileSaveState(error = photoError ?: CustomerAuth.message(result.exception))
            }
        }
    }

    fun consumeError() { state.value = state.value?.copy(error = null) }
    override fun onCleared() { cleared = true }
}
