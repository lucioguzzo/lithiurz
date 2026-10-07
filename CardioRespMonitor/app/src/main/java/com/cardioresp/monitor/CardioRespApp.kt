package com.cardioresp.monitor

import android.app.Application
import com.cardioresp.monitor.di.AppContainer
import com.cardioresp.monitor.service.AcquisitionNotification

class CardioRespApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        AcquisitionNotification.createChannel(this)
    }
}
