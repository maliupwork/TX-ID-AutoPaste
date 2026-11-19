package com.example

import android.app.Application
import com.example.di.AppContainer
import com.example.service.TxIdNotificationHelper

class TxIdApplication : Application() {

  lateinit var container: AppContainer
    private set

  override fun onCreate() {
    super.onCreate()
    container = AppContainer(this)
    TxIdNotificationHelper.ensureChannel(this)
  }
}
