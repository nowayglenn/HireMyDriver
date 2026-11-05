package com.example.hiremydriver

/** Shared by the driver form, online gate and customer matching. */
object DrivingInformation {
    val vehicles = listOf("Sedan", "SUV", "Van", "Pickup", "Truck")
    val transmissions = listOf("Automatic", "Manual")
    val languages = listOf("Filipino", "English", "Cebuano/Bisaya")
    const val REQUIRED_MESSAGE = "Complete and save Driving Information first. Tap here to set up."

    fun error(data: Map<*, *>): String? {
        fun selected(key: String) = (data[key] as? List<*>)?.filterIsInstance<String>().orEmpty()
        return when {
            selected("vehicles").isEmpty() || selected("vehicles").any { it !in vehicles } -> "Select at least one vehicle type."
            selected("transmissions").isEmpty() || selected("transmissions").any { it !in transmissions } -> "Select Manual, Automatic, or both."
            !(data["experience"] as? String).orEmpty().matches(Regex("(?:[0-9]|[1-7][0-9]|80)")) -> "Enter years of driving experience from 0 to 80."
            selected("areas").isEmpty() || selected("areas").any { it.isBlank() || it == "Select service areas" } -> "Select at least one service area."
            selected("languages").isEmpty() || selected("languages").any { it !in languages } -> "Select at least one language you speak."
            (data["rate"] as? Number)?.let { it.toDouble() == it.toLong().toDouble() && it.toLong() in 0..100000 } != true -> "Enter an hourly rate from 0 to 100000."
            else -> null
        }
    }
    fun complete(data: Map<*, *>) = error(data) == null
}
