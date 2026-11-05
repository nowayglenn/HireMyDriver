package com.example.hiremydriver

import java.io.File
import java.io.IOException
import java.util.UUID

/** Temporary shots are separate from accepted photos, so Retake cannot erase a draft. */
internal class DocumentPhotoStore(cacheRoot: File, filesRoot: File) {
    private val pending = File(cacheRoot, "registration_captures").apply { mkdirs() }
    private val accepted = File(filesRoot, "registration_documents").apply { mkdirs() }
    fun newShot() = File(pending, "${UUID.randomUUID()}.jpg")
    fun restore(name: String?): File? = name?.takeIf {
        Regex("[a-f0-9-]{36}\\.jpg").matches(it)
    }?.let { File(pending, it) }?.takeIf { it.isFile && it.length() > 0 }

    fun discard(file: File) {
        if (file.canonicalFile.parentFile == pending.canonicalFile) file.delete()
    }

    fun accept(file: File): File {
        if (file.canonicalFile.parentFile != pending.canonicalFile || !file.isFile || file.length() == 0L) {
            throw IOException("No captured photo")
        }
        val destination = File(accepted, "${UUID.randomUUID()}.jpg")
        try { file.copyTo(destination) }
        catch (error: IOException) { destination.delete(); throw error }
        return destination
    }

    fun acceptAll(files: Map<String, File>): Map<String, File> {
        val copies = linkedMapOf<String, File>()
        try { files.forEach { (side, file) -> copies[side] = accept(file) } }
        catch (error: Exception) { copies.values.forEach { it.delete() }; throw error }
        return copies
    }
}
