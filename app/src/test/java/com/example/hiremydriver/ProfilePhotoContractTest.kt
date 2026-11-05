package com.example.hiremydriver

import org.junit.Assert.*
import org.junit.Test

class ProfilePhotoContractTest {
    @Test fun uploadsAndReplacementResolveToOneObjectPerAccount() {
        assertEquals("customer_123-abc/profile.jpg", ProfilePhotoContract.objectPath("customer_123-abc"))
        assertNotEquals(ProfilePhotoContract.objectPath("alice"), ProfilePhotoContract.objectPath("bob"))
    }

    @Test fun rejectsUidPathTraversalAndUnexpectedCharacters() {
        for (uid in listOf("", ".", "..", "../bob", "alice/bob", "alice\\bob", "a?b", "a#b", "a%b", " a", "a".repeat(129))) {
            assertTrue("Rejected $uid", runCatching { ProfilePhotoContract.objectPath(uid) }.isFailure)
        }
    }

    @Test fun accessFailuresAreNotTreatedAsAnAccountWithoutAPhoto() {
        assertTrue(ProfilePhotoContract.isMissingPhoto(404, "NoSuchKey", "Not found"))
        assertTrue(ProfilePhotoContract.isMissingPhoto(400, "", "Object not found"))
        assertFalse(ProfilePhotoContract.isMissingPhoto(404, "NoSuchBucket", "Bucket not found"))
        assertFalse(ProfilePhotoContract.isMissingPhoto(403, "AccessDenied", "Object not found"))
        assertFalse(ProfilePhotoContract.isMissingPhoto(401, "InvalidJWT", "Invalid JWT"))
        assertFalse(ProfilePhotoContract.isMissingPhoto(500, "", "Object not found"))
    }
}
