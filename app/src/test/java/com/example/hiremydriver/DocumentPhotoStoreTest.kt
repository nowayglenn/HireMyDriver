package com.example.hiremydriver

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class DocumentPhotoStoreTest {
    @get:Rule val folder = TemporaryFolder()
    private fun store() = DocumentPhotoStore(File(folder.root, "cache"), File(folder.root, "files"))

    @Test fun acceptedPhotoSurvivesRetakeAndCancel() {
        val store = store()
        val first = store.newShot().apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val accepted = store.accept(first)
        store.discard(first)
        val retake = store.newShot().apply { writeBytes(byteArrayOf(4, 5)) }
        store.discard(retake)
        assertArrayEquals(byteArrayOf(1, 2, 3), accepted.readBytes())
        assertFalse(first.exists())
        assertFalse(retake.exists())
    }

    @Test fun reviewCanRestoreAfterRecreationAndAcceptanceDoesNotDestroyItsSource() {
        val store = store()
        val pending = store.newShot().apply { writeBytes(byteArrayOf(1, 2)) }
        val restored = store().restore(pending.name)
        assertEquals(pending, restored)
        val accepted = store.accept(pending)
        assertArrayEquals(pending.readBytes(), accepted.readBytes())
        assertNotEquals(pending, accepted)
    }

    @Test fun invalidRestorePathsAndEmptyPhotosAreRejected() {
        val store = store()
        assertNull(store.restore("../../outside.jpg"))
        assertNull(store.restore(null))
        val empty = store.newShot().apply { createNewFile() }
        assertNull(store.restore(empty.name))
        assertThrows(IOException::class.java) { store.accept(empty) }
    }

    @Test fun discardAndAcceptCannotTouchUnrelatedFiles() {
        val store = store()
        val other = folder.newFile("unrelated.jpg").apply { writeText("keep") }
        store.discard(other)
        assertEquals("keep", other.readText())
        assertThrows(IOException::class.java) { store.accept(other) }
    }

    @Test fun frontAndBackRemainSeparateAndAcceptanceDoesNotOverwrite() {
        val store = store()
        val front = store.newShot().apply { writeText("front") }
        val back = store.newShot().apply { writeText("back") }
        val savedFront = store.accept(front)
        val savedBack = store.accept(back)
        assertNotEquals(savedFront, savedBack)
        assertEquals("front", savedFront.readText())
        assertEquals("back", savedBack.readText())
    }

    @Test fun savingPairRollsBackIfSecondPhotoIsMissing() {
        val store = store()
        val front = store.newShot().apply { writeText("front") }
        val missing = store.newShot()
        assertThrows(IOException::class.java) { store.acceptAll(linkedMapOf("front" to front, "back" to missing)) }
        assertEquals("front", front.readText())
        assertTrue(File(folder.root, "files/registration_documents").listFiles().orEmpty().isEmpty())
    }
}
