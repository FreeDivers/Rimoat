package org.qinyan.rimoat

internal fun AppScreen.motionKey(): String = when (this) {
    AppScreen.Rooms -> "rooms"
    AppScreen.Trash -> "trash"
    AppScreen.Settings -> "settings"
    AppScreen.About -> "about"
    AppScreen.OpenSourceLicenses -> "licenses"
    is AppScreen.LibraryLicense -> "licenses"
    AppScreen.RemoteList -> "list"
    AppScreen.AddRemote -> "add"
    is AppScreen.RemoteControl -> "control:$remoteId"
    is AppScreen.CustomRemoteWizard -> "wizard:$sessionId"
}

internal fun AppScreen.backDestination(wide: Boolean = false): AppScreen? = when (this) {
    AppScreen.Rooms, AppScreen.Trash, AppScreen.About -> AppScreen.Settings
    AppScreen.OpenSourceLicenses -> AppScreen.About
    is AppScreen.LibraryLicense -> if (wide) AppScreen.About else AppScreen.OpenSourceLicenses
    AppScreen.RemoteList -> null // Leave root back to Android's back-to-home animation.
    is AppScreen.CustomRemoteWizard -> AppScreen.AddRemote
    else -> AppScreen.RemoteList
}
