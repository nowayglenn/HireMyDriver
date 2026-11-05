package com.example.hiremydriver

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Source

object PhilippineMobile {
    fun normalize(value: String): String? {
        val clean = value.trim().replace(Regex("[\\s()-]"), "")
        return when {
            clean.matches(Regex("09[0-9]{9}")) -> "+63" + clean.drop(1)
            clean.matches(Regex("639[0-9]{9}")) -> "+$clean"
            clean.matches(Regex("\\+639[0-9]{9}")) -> clean
            else -> null
        }
    }
}

data class EmergencyContact(val name: String, val phone: String, val relationship: String, val tripDetails: Boolean)

object EmergencyContactStore {
    private fun ref(uid: String) = FirebaseFirestore.getInstance().collection("users").document(uid)
        .collection("settings").document("emergencyContact")

    fun load(context: Context, preferCached: Boolean = false, done: (Result<EmergencyContact?>) -> Unit) {
        val uid = CustomerAuth.currentUser(context)?.uid ?: run { done(Result.failure(Exception("Please sign in."))); return }
        if (preferCached) {
            ref(uid).get(Source.CACHE).addOnCompleteListener { task ->
                if (CustomerAuth.currentUser(context)?.uid != uid) return@addOnCompleteListener
                val doc = if (task.isSuccessful) task.result else null
                if (doc != null && doc.exists() && !doc.metadata.hasPendingWrites()) {
                    done(Result.success(EmergencyContact(doc.getString("name").orEmpty(), doc.getString("phone").orEmpty(),
                        doc.getString("relationship").orEmpty(), doc.getBoolean("tripDetails") ?: true)))
                } else load(context, done = done)
            }
            return
        }
        ref(uid).get(Source.SERVER).addOnCompleteListener { task ->
            if (CustomerAuth.currentUser(context)?.uid != uid) return@addOnCompleteListener
            if (!task.isSuccessful) done(Result.failure(task.exception ?: Exception("Unable to load contact")))
            else {
                val doc = task.result
                done(Result.success(if (!doc.exists()) null else EmergencyContact(doc.getString("name").orEmpty(),
                    doc.getString("phone").orEmpty(), doc.getString("relationship").orEmpty(), doc.getBoolean("tripDetails") ?: true)))
            }
        }
    }

    fun save(context: Context, contact: EmergencyContact, done: (Boolean) -> Unit) {
        val uid = CustomerAuth.currentUser(context)?.uid ?: run { done(false); return }
        val phone = PhilippineMobile.normalize(contact.phone) ?: run { done(false); return }
        ref(uid).set(mapOf("name" to contact.name, "phone" to phone, "relationship" to contact.relationship,
            "tripDetails" to contact.tripDetails, "updatedAt" to FieldValue.serverTimestamp()))
            .addOnCompleteListener { done(it.isSuccessful && CustomerAuth.currentUser(context)?.uid == uid) }
    }
}
