package com.joaobzao.capas

import android.os.Bundle
import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.view.WindowCompat
import com.google.firebase.messaging.FirebaseMessaging
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.util.Log
import com.joaobzao.capas.navigation.CapasNavHost
import com.joaobzao.capas.analytics.CapasAnalytics
import com.joaobzao.capas.rating.RatingManager
import com.google.android.play.core.review.ReviewManagerFactory
import org.koin.java.KoinJavaComponent.get

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        askNotificationPermission()
        logToken()
        subscribeToUpdates()
        maybeRequestReview()

        setContent {
            val darkTheme = isSystemInDarkTheme()
            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as Activity).window
                    WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
                }
            }

            MaterialTheme {
                CapasNavHost()
            }
        }
    }

    /**
     * On the launch that crosses the engagement threshold, ask Google Play to
     * show the in-app review dialog. Play decides whether to actually display it
     * (and throttles heavily), so this is fire-and-forget and never blocks the UI.
     */
    private fun maybeRequestReview() {
        val ratingManager = get<RatingManager>(RatingManager::class.java)
        if (!ratingManager.registerAppOpenAndCheck()) return

        val reviewManager = ReviewManagerFactory.create(this)
        reviewManager.requestReviewFlow().addOnCompleteListener { request ->
            if (!request.isSuccessful) {
                logBreadcrumb("review: requestReviewFlow failed")
                return@addOnCompleteListener
            }
            reviewManager.launchReviewFlow(this, request.result).addOnCompleteListener {
                CapasAnalytics.trackRatePromptShown()
                logBreadcrumb("review: launchReviewFlow completed")
            }
        }
    }

    private fun subscribeToUpdates() {
        FirebaseMessaging.getInstance().subscribeToTopic("updates")
            .addOnCompleteListener { task ->
                var msg = "Subscribed to updates"
                if (!task.isSuccessful) {
                    msg = "Subscribe failed"
                }
                Log.d("MainActivity", msg)
                logBreadcrumb("fcm: $msg")
            }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // FCM SDK (and your app) can post notifications.
        } else {
            // TODO: Inform user that that your app will not show notifications.
        }
    }

    private fun askNotificationPermission() {
        // This is only necessary for API level >= 33 (TIRAMISU)
        if (Build.VERSION.SDK_INT >= 33) { // Build.VERSION_CODES.TIRAMISU
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                // FCM SDK (and your app) can post notifications.
            } else if (shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS)) {
                // TODO: Display an educational UI explaining to the user
                // https://developer.android.com/training/permissions/requesting#explain
                // For now, simple request:
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
                // Directly ask for the permission
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }


    private fun logToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("MainActivity", "Fetching FCM registration token failed", task.exception)
                logBreadcrumb("fcm: token fetch failed")
                return@addOnCompleteListener
            }

            // Get new FCM registration token
            val token = task.result
            Log.d("MainActivity", "FCM Token: $token")
            logBreadcrumb("fcm: token fetched")
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    CapasNavHost()
}