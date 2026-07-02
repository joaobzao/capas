package com.joaobzao.capas

import android.app.Application
import android.content.Context
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.joaobzao.capas.capas.CapasViewModel
import okhttp3.OkHttpClient
import org.koin.core.module.dsl.viewModel
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module

class MainApp : Application(), ImageLoaderFactory {
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

    // frontpages.com serves the high-res international covers but returns HTTP 403
    // for Coil's default "okhttp/x.x" User-Agent (Vary: User-Agent), leaving those
    // covers blank. Any non-okhttp UA is accepted, so override it globally.
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient {
                OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        val request = chain.request().newBuilder()
                            .header("User-Agent", "Capas-Android")
                            .build()
                        chain.proceed(request)
                    }
                    .build()
            }
            .build()
    }
}