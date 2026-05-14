package com.tools.vspace

import android.app.Application
import android.util.Log

class VSpaceApp : Application() {

    companion object {
        private const val TAG = "VSpaceApp"
        lateinit var instance: VSpaceApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "GuardianSpace Application started")
    }
}
