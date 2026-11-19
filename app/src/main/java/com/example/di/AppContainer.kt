package com.example.di

import android.content.Context
import com.example.data.InstalledAppsRepository
import com.example.data.SettingsRepository
import com.example.domain.AutoPasteManager
import com.example.domain.PrivacyManager
import com.example.domain.TransactionIdentifierExtractor
import com.example.ocr.OnDeviceOcrProcessor
import com.example.permissions.PermissionManager

class AppContainer(context: Context) {
  val appContext: Context = context.applicationContext
  val settingsRepository = SettingsRepository(appContext)
  val installedAppsRepository = InstalledAppsRepository(appContext)
  val extractor = TransactionIdentifierExtractor()
  val privacyManager = PrivacyManager()
  val ocrProcessor = OnDeviceOcrProcessor(appContext)
  val autoPasteManager = AutoPasteManager(appContext, privacyManager)
  val permissionManager = PermissionManager(appContext)
}
