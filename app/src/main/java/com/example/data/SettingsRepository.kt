package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.AppSettings
import com.example.domain.RetentionMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by
  preferencesDataStore(name = "tx_id_autopaste_settings")

/**
 * Stores strictly non-sensitive user preferences (selected app packages, toggles).
 * Never stores extracted Transaction IDs or receipt content.
 */
class SettingsRepository(private val context: Context) {

  private object Keys {
    val MASTER_ENABLED = booleanPreferencesKey("master_enabled")
    val SOURCE_PACKAGES = stringSetPreferencesKey("source_packages")
    val DESTINATION_PACKAGE = stringPreferencesKey("destination_package")
    val RETENTION_MODE = stringPreferencesKey("retention_mode")
    val CLIPBOARD_FALLBACK = booleanPreferencesKey("clipboard_fallback")
    val MIN_CONFIDENCE = floatPreferencesKey("min_confidence")
    val MEDIA_PROJECTION_FALLBACK = booleanPreferencesKey("media_projection_fallback")
  }

  val settingsFlow: Flow<AppSettings> =
    context.settingsDataStore.data.map { prefs ->
      val retentionName = prefs[Keys.RETENTION_MODE]
      val retentionMode =
        RetentionMode.entries.firstOrNull { it.name == retentionName }
          ?: RetentionMode.AUTO_3_MINUTES

      AppSettings(
        isMasterEnabled = prefs[Keys.MASTER_ENABLED] ?: false,
        selectedSourcePackages = prefs[Keys.SOURCE_PACKAGES] ?: emptySet(),
        destinationPackage = prefs[Keys.DESTINATION_PACKAGE],
        retentionMode = retentionMode,
        allowClipboardFallback = prefs[Keys.CLIPBOARD_FALLBACK] ?: true,
        minConfidenceThreshold = prefs[Keys.MIN_CONFIDENCE] ?: 0.75f,
        mediaProjectionFallbackEnabled = prefs[Keys.MEDIA_PROJECTION_FALLBACK] ?: true,
      )
    }

  suspend fun setMasterEnabled(enabled: Boolean) {
    context.settingsDataStore.edit { it[Keys.MASTER_ENABLED] = enabled }
  }

  suspend fun toggleSourcePackage(packageName: String) {
    context.settingsDataStore.edit { prefs ->
      val current = prefs[Keys.SOURCE_PACKAGES] ?: emptySet()
      prefs[Keys.SOURCE_PACKAGES] =
        if (packageName in current) current - packageName else current + packageName
    }
  }

  suspend fun setSourcePackages(packages: Set<String>) {
    context.settingsDataStore.edit { it[Keys.SOURCE_PACKAGES] = packages }
  }

  suspend fun setDestinationPackage(packageName: String?) {
    context.settingsDataStore.edit { prefs ->
      if (packageName == null) {
        prefs.remove(Keys.DESTINATION_PACKAGE)
      } else {
        prefs[Keys.DESTINATION_PACKAGE] = packageName
      }
    }
  }

  suspend fun setRetentionMode(mode: RetentionMode) {
    context.settingsDataStore.edit { it[Keys.RETENTION_MODE] = mode.name }
  }

  suspend fun setClipboardFallbackEnabled(enabled: Boolean) {
    context.settingsDataStore.edit { it[Keys.CLIPBOARD_FALLBACK] = enabled }
  }

  suspend fun setMinConfidenceThreshold(threshold: Float) {
    context.settingsDataStore.edit { it[Keys.MIN_CONFIDENCE] = threshold.coerceIn(0.60f, 0.95f) }
  }

  suspend fun setMediaProjectionFallbackEnabled(enabled: Boolean) {
    context.settingsDataStore.edit { it[Keys.MEDIA_PROJECTION_FALLBACK] = enabled }
  }
}
