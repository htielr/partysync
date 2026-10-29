package com.karthick.partysync.ui.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Browse : Screen("browse")
    data object Settings : Screen("settings")

    data object AddEditMapping : Screen("mapping/{mappingId}") {
        const val ARG_MAPPING_ID = "mappingId"
        const val NEW_MAPPING_ID = -1L

        fun routeFor(mappingId: Long = NEW_MAPPING_ID) = "mapping/$mappingId"
    }

    data object MappingDetail : Screen("mapping/{mappingId}/detail") {
        const val ARG_MAPPING_ID = "mappingId"

        fun routeFor(mappingId: Long) = "mapping/$mappingId/detail"
    }

    data object Servers : Screen("servers")

    data object AddEditServer : Screen("server/{serverId}") {
        const val ARG_SERVER_ID = "serverId"
        const val NEW_SERVER_ID = -1L

        fun routeFor(serverId: Long = NEW_SERVER_ID) = "server/$serverId"
    }
}
