package com.joaobzao.capas.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.joaobzao.capas.CapaDetailScreen
import com.joaobzao.capas.WelcomeScreen
import com.joaobzao.capas.capas.CapasViewModel
import org.koin.androidx.compose.koinViewModel
import androidx.compose.runtime.collectAsState
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import com.google.firebase.analytics.logEvent
import com.joaobzao.capas.CapasScreen

@Composable
fun CapasNavHost(
    navController: NavHostController = rememberNavController(),
    viewModel: CapasViewModel = koinViewModel()
) {
    val startDestination = if (viewModel.isOnboardingCompleted()) "capas" else "welcome"

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Welcome
        composable("welcome") {
            WelcomeScreen(
                onFinish = {
                    viewModel.completeOnboarding()
                    navController.navigate("capas") {
                        popUpTo("welcome") { inclusive = true }
                    }
                }
            )
        }

        // Lista de capas
        composable("capas") {
            CapasScreen(
                viewModel = viewModel,
                onCapaClick = { capa ->
                    Firebase.analytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT) {
                        param(FirebaseAnalytics.Param.ITEM_ID, capa.id)
                        param(FirebaseAnalytics.Param.ITEM_NAME, capa.nome)
                        param(FirebaseAnalytics.Param.CONTENT_TYPE, "capa")
                    }
                    navController.navigate("detail/${capa.id}")
                }
            )
        }

        // Detalhe
        composable(
            route = "detail/{id}",
            arguments = listOf(
                navArgument("id") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id")!!

            // Procurar a capa no estado do ViewModel
            val allCapas = viewModel.capasState.collectAsState().value.capas?.let { capas ->
                (capas.mainNewspapers + capas.sportNewspapers + capas.economyNewspapers + capas.regionalNewspapers)
            } ?: emptyList()

            val initialPage = allCapas.indexOfFirst { it.id == id }

            if (initialPage != -1) {
                CapaDetailScreen(
                    capas = allCapas,
                    initialPage = initialPage,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
