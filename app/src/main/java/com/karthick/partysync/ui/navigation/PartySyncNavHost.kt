package com.karthick.partysync.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.karthick.partysync.ui.addmapping.AddEditMappingScreen
import com.karthick.partysync.ui.home.HomeScreen
import com.karthick.partysync.ui.mappingdetail.MappingDetailScreen
import com.karthick.partysync.ui.servers.AddEditServerScreen
import com.karthick.partysync.ui.servers.ServersScreen
import com.karthick.partysync.ui.settings.SettingsScreen

@Composable
fun PartySyncNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) {
            HomeScreen(
                onAddMapping = { navController.navigate(Screen.AddEditMapping.routeFor()) },
                onEditMapping = { id -> navController.navigate(Screen.AddEditMapping.routeFor(id)) },
                onViewMappingDetail = { id -> navController.navigate(Screen.MappingDetail.routeFor(id)) },
                onOpenSettings = { navController.navigate(Screen.Settings.route) },
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onManageServers = { navController.navigate(Screen.Servers.route) },
            )
        }
        composable(
            route = Screen.AddEditMapping.route,
            arguments = listOf(navArgument(Screen.AddEditMapping.ARG_MAPPING_ID) { type = NavType.StringType }),
        ) {
            AddEditMappingScreen(
                onDone = { navController.popBackStack() },
                onAddServer = { navController.navigate(Screen.AddEditServer.routeFor()) },
            )
        }
        composable(
            route = Screen.MappingDetail.route,
            arguments = listOf(navArgument(Screen.MappingDetail.ARG_MAPPING_ID) { type = NavType.StringType }),
        ) {
            MappingDetailScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Servers.route) {
            ServersScreen(
                onBack = { navController.popBackStack() },
                onAddServer = { navController.navigate(Screen.AddEditServer.routeFor()) },
                onEditServer = { id -> navController.navigate(Screen.AddEditServer.routeFor(id)) },
            )
        }
        composable(
            route = Screen.AddEditServer.route,
            arguments = listOf(navArgument(Screen.AddEditServer.ARG_SERVER_ID) { type = NavType.StringType }),
        ) {
            AddEditServerScreen(onDone = { navController.popBackStack() })
        }
    }
}
