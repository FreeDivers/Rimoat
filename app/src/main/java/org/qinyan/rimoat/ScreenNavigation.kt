package org.qinyan.rimoat

internal fun AppScreen.motionKey(): String = when (this) {
    AppScreen.Rooms -> "rooms"
    AppScreen.Trash -> "trash"
    AppScreen.Settings -> "settings"
    AppScreen.About -> "about"
    AppScreen.OpenSourceLicenses -> "licenses"
    is AppScreen.LibraryLicense -> "license:$libraryId"
    AppScreen.RemoteList -> "list"
    AppScreen.AddRemote -> "add"
    is AppScreen.RemoteControl -> "control:$remoteId"
    is AppScreen.CustomRemoteWizard -> "wizard:$sessionId"
}

internal fun AppScreen.backDestination(): AppScreen? = when (this) {
    AppScreen.Rooms, AppScreen.Trash, AppScreen.About -> AppScreen.Settings
    AppScreen.OpenSourceLicenses -> AppScreen.About
    is AppScreen.LibraryLicense -> AppScreen.OpenSourceLicenses
    AppScreen.RemoteList -> null // Leave root back to Android's back-to-home animation.
    is AppScreen.CustomRemoteWizard -> AppScreen.AddRemote
    else -> AppScreen.RemoteList
}

internal fun isBackNavigation(from: AppScreen, to: AppScreen): Boolean =
    to == from.backDestination()
