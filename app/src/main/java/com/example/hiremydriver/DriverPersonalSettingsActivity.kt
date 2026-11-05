package com.example.hiremydriver

class DriverPersonalSettingsActivity : DriverProfileSettingsActivity() {
    override val screenLayout = R.layout.activity_driver_personal_settings
    override val preferenceName get() = DriverAuth.preferenceName(this, PREFERENCES)
    override val fields = listOf(
        Field(R.id.settingsName, R.id.settingsNameLayout, "name", "Full name", required = true),
        Field(R.id.settingsEmail, R.id.settingsEmailLayout, "email", "Email", required = true),
        Field(R.id.settingsMobile, R.id.settingsMobileLayout, "mobile", "Mobile number"),
        Field(R.id.settingsBirthDate, R.id.settingsBirthDateLayout, "birthDate", "Date of birth", date = true),
        Field(R.id.settingsAddress, R.id.settingsAddressLayout, "address", "Address"),
        Field(R.id.settingsBio, R.id.settingsBioLayout, "bio", "Short bio")
    )

    override fun initialValues() {
        if (!draft.containsKey("name")) draft.putString("name", intent.getStringExtra("PROFILE_NAME") ?: "")
        if (!draft.containsKey("email")) draft.putString("email", intent.getStringExtra("PROFILE_EMAIL") ?: "")
    }

    companion object { const val PREFERENCES = "driver_personal_settings_local" }
}
