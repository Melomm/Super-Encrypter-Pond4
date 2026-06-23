package com.superencrypter

import android.app.Application

class SuperEncrypterApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.initialize()
    }
}
