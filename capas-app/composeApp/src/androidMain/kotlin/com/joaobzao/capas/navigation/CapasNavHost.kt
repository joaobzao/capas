package com.joaobzao.capas.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.joaobzao.capas.CapaDetailScreen
import com.joaobzao.capas.CapasScreen
import com.joaobzao.capas.capas.Capa
import com.joaobzao.capas.capas.CapasViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun CapasNavHost(
    navController: NavHostController = rememberNavController(),
    viewModel: CapasViewModel = koinViewModel()
) {
    NavHost(
        navController = navController,
        startDestination = "capas"
    ) {
        composable("capas") {
            CapasScreen(
                viewModel = viewModel,
                onCapaClick = { capa ->
                    navController.navigate(
                        "detail/${Uri.encode(capa.url)}/${Uri.encode(capa.nome)}"
                    )
                }
            )
        }

        composable(
            route = "detail/{url}/{nome}",
            arguments = listOf(
                navArgument("url") { type = NavType.StringType },
                navArgument("nome") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val url = backStackEntry.arguments?.getString("url")!!
            val nome = backStackEntry.arguments?.getString("nome")!!
            CapaDetailScreen(
                capa = Capa(nome, url),
                onBack = { navController.popBackStack() }
            )
        }
    }
}
