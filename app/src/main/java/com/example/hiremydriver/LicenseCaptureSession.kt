package com.example.hiremydriver

/** Keeps confirmed sides separate from the shot currently being reviewed. */
internal class LicenseCaptureSession(val singleSide: String? = null) {
    val photos = linkedMapOf<String, String>()
    var side = singleSide ?: FRONT
        private set
    val complete get() = if (singleSide != null) photos.containsKey(singleSide)
        else photos.containsKey(FRONT) && photos.containsKey(BACK)

    fun confirm(fileName: String): String? {
        val replaced = photos.put(side, fileName)
        if (!complete) side = if (photos.containsKey(FRONT)) BACK else FRONT
        return replaced
    }
    fun retake(side: String) {
        require(side == FRONT || side == BACK)
        require(singleSide == null || singleSide == side)
        this.side = side
    }
    companion object { const val FRONT = "licenseFront"; const val BACK = "licenseBack" }
}
