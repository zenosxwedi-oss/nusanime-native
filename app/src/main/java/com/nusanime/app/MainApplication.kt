package com.nusanime.app

import android.app.Application

class MainApplication : Application() {
    companion object {
        @Volatile lateinit var instance: MainApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
