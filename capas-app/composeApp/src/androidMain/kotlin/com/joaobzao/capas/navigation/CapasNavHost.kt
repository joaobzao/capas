package com.joaobzao.capas.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.joaobzao.capas.AboutScreen
import com.joaobzao.capas.CapaDetailScreen
import com.joaobzao.capas.MyCoversScreen
import com.joaobzao.capas.R
import com.joaobzao.capas.WelcomeScreen
import com.joaobzao.capas.capas.CapasViewModel
import com.joaobzao.capas.logBreadcrumb
import org.koin.androidx.compose.koinViewModel
import androidx.compose.runtime.collectAsState
import com.joaobzao.capas.CapasScreen
import com.joaobzao.capas.analytics.CapasAnalytics

private enum class BottomTab(
    val route: String,
    val labelResId: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    CAPAS("capas", R.string.nav_capas, Icons.Filled.Home, Icons.Filled.Home),
    MY_COVERS("favorites", R.string.nav_my_covers, Icons.Filled.Star, Icons.Filled.Star),
    ABOUT("about", R.string.nav_about, Icons.Filled.Info, Icons.Filled.Info)
}

@Composable
fun CapasNavHost(
    navController: NavHostController = rememberNavController(),
    viewModel: CapasViewModel = koinViewModel()
) {
    val startDestination = if (viewModel.isOnboardingCompleted()) "capas" else "welcome"

    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    var isDraggingCapa by remember { mutableStateOf(false) }
    val showBottomBar = BottomTab.entries.any { it.route == currentRoute } && !isDraggingCapa

    // Um único observador de rota garante um screen_view por navegação
    // (os blocos composable podem compor mais do que uma vez durante a transição).
    LaunchedEffect(currentRoute) {
        val screenName = when (currentRoute) {
            "welcome", "capas", "favorites", "about" -> currentRoute
            "detail/{id}?source={source}" -> "capa_detail"
            else -> null
        }
        screenName?.let { CapasAnalytics.trackScreenView(it) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.fillMaxSize()
        ) {
            // Welcome
            composable("welcome") {
                logBreadcrumb("nav: welcome")
                WelcomeScreen(
                    onFinish = {
                        CapasAnalytics.trackOnboardingCompleted()
                        viewModel.completeOnboarding()
                        navController.navigate("capas") {
                            popUpTo("welcome") { inclusive = true }
                        }
                    }
                )
            }

            // Lista de capas
            composable("capas") {
                logBreadcrumb("nav: capas")
                CapasScreen(
                    viewModel = viewModel,
                    onCapaClick = { capa -> logCapaSelection(capa.id, capa.nome, DETAIL_SOURCE_ALL, navController) },
                    onDraggingChange = { isDraggingCapa = it }
                )
            }

            // As minhas capas (favoritos)
            composable("favorites") {
                logBreadcrumb("nav: favorites")
                MyCoversScreen(
                    viewModel = viewModel,
                    onCapaClick = { capa -> logCapaSelection(capa.id, capa.nome, DETAIL_SOURCE_FAVORITES, navController) },
                    onDraggingChange = { isDraggingCapa = it }
                )
            }

            // Sobre
            composable("about") {
                logBreadcrumb("nav: about")
                AboutScreen(viewModel = viewModel)
            }

            // Detalhe
            composable(
                route = "detail/{id}?source={source}",
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType },
                    navArgument("source") {
                        type = NavType.StringType
                        defaultValue = DETAIL_SOURCE_ALL
                    }
                )
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getString("id")!!
                val source = backStackEntry.arguments?.getString("source") ?: DETAIL_SOURCE_ALL
                logBreadcrumb("nav: detail/$id?source=$source")

                val state = viewModel.capasState.collectAsState().value

                // O conjunto a percorrer depende do ecrã de origem.
                val capasToShow = if (source == DETAIL_SOURCE_FAVORITES) {
                    state.favorites
                } else {
                    state.capas?.let { capas ->
                        (capas.mainNewspapers + capas.sportNewspapers + capas.economyNewspapers + capas.regionalNewspapers + capas.internationalNewspapers)
                    } ?: emptyList()
                }

                val initialPage = capasToShow.indexOfFirst { it.id == id }

                if (initialPage != -1) {
                    CapaDetailScreen(
                        capas = capasToShow,
                        initialPage = initialPage,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }

        if (showBottomBar) {
            FloatingNavBar(
                currentRoute = currentRoute,
                onTabSelected = { tab ->
                    navController.navigate(tab.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun FloatingNavBar(
    currentRoute: String?,
    onTabSelected: (BottomTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 32.dp)
            .padding(bottom = 16.dp, top = 8.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 12.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomTab.entries.forEach { tab ->
                val selected = currentRoute == tab.route
                FloatingNavItem(
                    icon = if (selected) tab.selectedIcon else tab.unselectedIcon,
                    label = stringResource(tab.labelResId),
                    selected = selected,
                    onClick = { if (!selected) onTabSelected(tab) }
                )
            }
        }
    }
}

@Composable
private fun FloatingNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val contentColor =
        if (selected) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = label,
            color = contentColor,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            ),
            maxLines = 1
        )
    }
}

private const val DETAIL_SOURCE_ALL = "all"
private const val DETAIL_SOURCE_FAVORITES = "favorites"

private fun logCapaSelection(id: String, nome: String, source: String, navController: NavHostController) {
    CapasAnalytics.trackCapaOpened(id, nome, source)
    navController.navigate("detail/$id?source=$source")
}
