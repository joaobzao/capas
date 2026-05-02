package com.joaobzao.capas

import com.google.firebase.crashlytics.FirebaseCrashlytics

actual fun logBreadcrumb(message: String) {
    FirebaseCrashlytics.getInstance().log(message)
}
