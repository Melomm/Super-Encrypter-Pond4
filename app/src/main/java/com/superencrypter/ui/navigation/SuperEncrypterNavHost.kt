package com.superencrypter.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.superencrypter.data.repository.SuperEncrypterRepository
import com.superencrypter.ui.screens.createvault.CreateVaultScreen
import com.superencrypter.ui.screens.createvault.CreateVaultViewModel
import com.superencrypter.ui.screens.home.HomeScreen
import com.superencrypter.ui.screens.home.HomeViewModel
import com.superencrypter.ui.screens.manualscan.ManualScanScreen
import com.superencrypter.ui.screens.manualscan.ManualScanViewModel
import com.superencrypter.ui.screens.unlock.UnlockVaultScreen
import com.superencrypter.ui.screens.unlock.UnlockVaultViewModel
import com.superencrypter.ui.screens.vaultdetails.VaultDetailsScreen
import com.superencrypter.ui.screens.vaultdetails.VaultDetailsViewModel
import com.superencrypter.ui.screens.viewer.ViewerScreen
import com.superencrypter.ui.screens.viewer.ViewerViewModel

@Composable
fun SuperEncrypterNavHost(
    navController: NavHostController,
    repository: SuperEncrypterRepository
) {
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            val vm: HomeViewModel = viewModel(factory = factory { HomeViewModel(repository) })
            HomeScreen(
                viewModel = vm,
                onCreateVault = { navController.navigate(Routes.CREATE) },
                onOpenVault = { navController.navigate(Routes.details(it)) },
                onUnlockVault = { navController.navigate(Routes.unlock(it)) }
            )
        }

        composable(Routes.CREATE) {
            val vm: CreateVaultViewModel = viewModel(factory = factory { CreateVaultViewModel(repository) })
            CreateVaultScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onCreated = { vaultId ->
                    navController.navigate(Routes.details(vaultId)) {
                        popUpTo(Routes.HOME)
                    }
                }
            )
        }

        composable(Routes.SCAN) {
            val vm: ManualScanViewModel = viewModel(factory = factory { ManualScanViewModel(repository) })
            ManualScanScreen(viewModel = vm)
        }

        composable(
            route = Routes.DETAILS,
            arguments = listOf(navArgument("vaultId") { type = NavType.LongType })
        ) { entry ->
            val vaultId = entry.arguments?.getLong("vaultId") ?: return@composable
            val vm: VaultDetailsViewModel = viewModel(factory = factory { VaultDetailsViewModel(vaultId, repository) })
            VaultDetailsScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onUnlock = { navController.navigate(Routes.unlock(vaultId)) },
                onOpenFile = { fileId -> navController.navigate(Routes.viewer(vaultId, fileId)) }
            )
        }

        composable(
            route = Routes.UNLOCK,
            arguments = listOf(navArgument("vaultId") { type = NavType.LongType })
        ) { entry ->
            val vaultId = entry.arguments?.getLong("vaultId") ?: return@composable
            val vm: UnlockVaultViewModel = viewModel(factory = factory { UnlockVaultViewModel(vaultId, repository) })
            UnlockVaultScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onUnlocked = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.VIEWER,
            arguments = listOf(
                navArgument("vaultId") { type = NavType.LongType },
                navArgument("fileId") { type = NavType.LongType }
            )
        ) { entry ->
            val vaultId = entry.arguments?.getLong("vaultId") ?: return@composable
            val fileId = entry.arguments?.getLong("fileId") ?: return@composable
            val vm: ViewerViewModel = viewModel(factory = factory { ViewerViewModel(vaultId, fileId, repository) })
            ViewerScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }
    }
}

private inline fun <reified T : ViewModel> factory(crossinline creator: () -> T): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = creator() as VM
    }
