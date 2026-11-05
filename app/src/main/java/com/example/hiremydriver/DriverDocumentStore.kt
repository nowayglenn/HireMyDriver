package com.example.hiremydriver

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

/** Local submitted documents, until driver registration has an authenticated backend. */
internal object DriverDocumentStore {
    const val PREFERENCES = "driver_documents_settings_local"
    val photoKeys = mapOf("licenseFront" to "front", "licenseBack" to "back", "policeImage" to "police")

    fun save(context: Context, draft: Bundle) {
        val folder = File(context.filesDir, "submitted_driver_documents/${UUID.randomUUID()}")
        check(folder.mkdirs()) { "Cannot save documents" }
        try {
            val saved = photoKeys.mapValues { (sourceKey, destinationKey) ->
                draft.getString(sourceKey)?.takeIf(String::isNotBlank)?.let { source ->
                    val file = File(folder, destinationKey)
                    context.contentResolver.openInputStream(Uri.parse(source)).use { input ->
                        checkNotNull(input) { "Document is unavailable" }
                        file.outputStream().use { output -> input.copyTo(output); output.fd.sync() }
                    }
                    check(file.length() > 0) { "Document is empty" }
                    Uri.fromFile(file).toString()
                }
            }
            val editor = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
                .putString("licenseNumber", draft.getString("licenseNumber", ""))
                .putString("licenseExpiry", draft.getString("licenseExpiration", ""))
                .remove("clearanceNumber")
                .putString("clearanceIssue", draft.getString("issueDate", ""))
                .putString("clearanceExpiry", draft.getString("validityDate", ""))
            saved.forEach { (sourceKey, value) -> editor.putString(photoKeys.getValue(sourceKey), value) }
            check(editor.commit()) { "Cannot save document information" }
        } catch (error: Exception) {
            folder.listFiles()?.forEach(File::delete)
            folder.delete()
            throw error
        }
    }
}

internal class DriverDocumentSubmissionModel(application: Application) : AndroidViewModel(application) {
    enum class State { IDLE, SAVING, SAVED, ERROR, ACCOUNT_EXISTS, RECOVER_PASSWORD }
    var errorMessage: String? = null
        private set
    private val mutableState = MutableLiveData(State.IDLE)
    val state: LiveData<State> = mutableState

    fun cancelConfirmation() {
        mutableState.value = State.IDLE
    }

    fun submit(draft: Bundle) {
        if (mutableState.value in listOf(State.SAVING, State.SAVED)) return
        val snapshot = Bundle(draft)
        errorMessage = null
        mutableState.value = State.SAVING
        DriverAuth.submit(getApplication(), snapshot).addOnCompleteListener { result ->
            snapshot.remove("password")
            snapshot.remove("confirmPassword")
            if (generateSequence(result.exception as Throwable?) { it.cause }.take(10)
                    .any { it is DriverRegistrationPasswordRequired }) {
                mutableState.value = State.RECOVER_PASSWORD
                return@addOnCompleteListener
            }
            if (CustomerAuth.isExistingAccount(result.exception)) {
                mutableState.value = State.ACCOUNT_EXISTS
                return@addOnCompleteListener
            }
            errorMessage = if (result.isSuccessful) null else DriverAuth.message(result.exception)
            mutableState.value = if (result.isSuccessful) State.SAVED else State.ERROR
        }
    }
}
