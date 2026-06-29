/*
 * Copyright (c) 2026 Wandi (soe1hom-arch). All rights reserved.
 */

package com.afft.app

import android.app.Application
import android.util.Log

class AFFTApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d("AFFT", "AFFT Application initialized")
    }

    companion object {
        lateinit var instance: AFFTApplication
            private set
    }
}
