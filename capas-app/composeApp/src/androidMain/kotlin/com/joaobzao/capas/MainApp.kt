package com.joaobzao.capas

import android.app.Application
import android.content.Context
import android.util.Log
import com.joaobzao.capas.capas.CapasViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.parameter.parametersOf
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

                viewModel { CapasViewModel(get { parametersOf("CapasViewModel") }, get()) }
            }

        )

    }
}