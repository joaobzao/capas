package com.joaobzao.capas

import android.app.Application
import android.content.Context
import android.util.Log
import org.koin.dsl.module

class MainApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin(
            module {
                single<Context> { this@MainApp }
                single {
                    { Log.i("Startup", "Hello from Android/Kotlin!") }
                }
            }
        )

    }
}