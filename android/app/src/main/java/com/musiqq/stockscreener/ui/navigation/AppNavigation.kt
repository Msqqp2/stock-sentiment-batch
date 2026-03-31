package com.musiqq.stockscreener.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.musiqq.stockscreener.ui.detail.DetailScreen
import com.musiqq.stockscreener.ui.heatmap.HeatMapScreen
import com.musiqq.stockscreener.ui.screener.ScreenerScreen
import com.musiqq.stockscreener.ui.search.SearchScreen
import com.musiqq.stockscreener.ui.settings.SettingsScreen
import com.musiqq.stockscreener.ui.watchlist.WatchlistScreen

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Search : Screen("search", "종목검색", Icons.Default.Search)
    data object Screener : Screen("screener", "스크리너", Icons.Default.BarChart)
    data object HeatMap : Screen("heatmap", "히트맵", Icons.Default.GridView)
    data object Watchlist : Screen("watchlist", "관심종목", Icons.Default.Star)
    data object Settings : Screen("settings", "설정", Icons.Default.Settings)
}

private val bottomTabs = listOf(Screen.Search, Screen.Screener, Screen.HeatMap, Screen.Watchlist, Screen.Settings)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = bottomTabs.any { screen ->
        currentDestination?.hierarchy?.any { it.route == screen.route } == true
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomTabs.forEach { screen ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.route == screen.route
                        } == true
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.label) },
                            label = { Text(screen.label) },
                            selected = selected,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Search.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Screen.Search.route) {
                SearchScreen(
                    onNavigateToDetail = { symbol ->
                        navController.navigate("detail/$symbol")
                    },
                )
            }
            composable(Screen.Screener.route) {
                ScreenerScreen(
                    onNavigateToDetail = { symbol ->
                        navController.navigate("detail/$symbol")
                    },
                )
            }
            composable(Screen.HeatMap.route) {
                HeatMapScreen(
                    onNavigateToDetail = { symbol ->
                        navController.navigate("detail/$symbol")
                    },
                )
            }
            composable(Screen.Watchlist.route) {
                WatchlistScreen(
                    onNavigateToDetail = { symbol ->
                        navController.navigate("detail/$symbol")
                    },
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen()
            }
            composable(
                route = "detail/{symbol}",
                arguments = listOf(navArgument("symbol") { type = NavType.StringType }),
            ) { backStackEntry ->
                val symbol = backStackEntry.arguments?.getString("symbol") ?: return@composable
                DetailScreen(
                    symbol = symbol,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
