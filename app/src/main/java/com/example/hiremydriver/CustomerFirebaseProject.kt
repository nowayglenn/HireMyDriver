package com.example.hiremydriver

/** Prevent accidentally creating customer accounts in an unrelated Firebase project. */
internal object CustomerFirebaseProject {
    const val PROJECT_ID = "hiremydriver-e88f7"

    fun accepts(projectId: String?): Boolean = projectId == PROJECT_ID
}
