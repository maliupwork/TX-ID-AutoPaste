package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data class ApplicationSelectionRoute(val initialTab: Int = 0)

@Serializable
data object SettingsRoute
