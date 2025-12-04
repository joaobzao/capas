package com.joaobzao.capas

import com.joaobzao.capas.capas.CapasViewModel
import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module
import platform.Foundation.NSUserDefaults

class CapasViewModelHelper : KoinComponent {
    val viewModel: CapasViewModel by inject()
}

fun doInitKoin() {
    initKoin(
        module {
            single {
                { println("Hello from iOS/Kotlin!") }
            }
            single<Settings> {
                NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults)
            }
            factory { CapasViewModel(get { parametersOf("CapasViewModel") }, get()) }
        }
    )
}
