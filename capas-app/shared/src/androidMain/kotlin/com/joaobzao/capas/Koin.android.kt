package com.joaobzao.capas

import android.content.Context
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<Settings> {
        SharedPreferencesSettings(
            get<Context>().getSharedPreferences("capas_prefs", Context.MODE_PRIVATE)
        )
    }

    single {
        OkHttp.create()
    }
}
