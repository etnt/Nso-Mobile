package se.kruskakli.nso.domain

/*
    In the MVI (Model-View-Intent) architecture, the View should
    not directly call methods on the ViewModel. Instead, it should
    emit Intents, which the ViewModel listens to and then performs
    the appropriate actions.
 */
sealed class MainIntent {
    object ShowPackages : MainIntent()
    object ShowDevices : MainIntent()
    object ShowAlarms : MainIntent()
    data class SaveSettings(val settingsData: SettingsData) : MainIntent()
}

data class SettingsData(
    val name: String,
    val ip: String,
    val port: String,
    val user: String,
    val passwd: String,
    val refresh: Boolean
)