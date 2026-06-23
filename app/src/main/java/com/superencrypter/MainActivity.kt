package com.superencrypter

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.superencrypter.ui.navigation.Routes
import com.superencrypter.ui.navigation.SuperEncrypterNavHost
import com.superencrypter.ui.theme.SuperEncrypterTheme
import com.superencrypter.ui.theme.VaultBackground
import com.superencrypter.ui.theme.VaultSurface
import com.superencrypter.util.AppVisibilityTracker

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onStart() {
        super.onStart()
        AppVisibilityTracker.activityStarted()
    }

    override fun onStop() {
        AppVisibilityTracker.activityStopped()
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val repository = (application as SuperEncrypterApplication).container.repository
        setContent {
            SuperEncrypterTheme {
                val navController = rememberNavController()
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = backStackEntry?.destination?.route
                val showBottomBar = currentRoute == Routes.HOME || currentRoute == Routes.SCAN

                Scaffold(
                    containerColor = VaultBackground,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    bottomBar = {
                        if (showBottomBar) {
                            NavigationBar(
                                containerColor = VaultSurface,
                                tonalElevation = NavigationBarDefaults.Elevation
                            ) {
                                NavigationBarItem(
                                    selected = currentRoute == Routes.HOME,
                                    onClick = {
                                        navController.navigate(Routes.HOME) {
                                            popUpTo(Routes.HOME) { inclusive = false }
                                            launchSingleTop = true
                                        }
                                    },
                                    icon = { Icon(Icons.Default.Folder, contentDescription = null) },
                                    label = { Text("Pastas") }
                                )
                                NavigationBarItem(
                                    selected = currentRoute == Routes.SCAN,
                                    onClick = {
                                        navController.navigate(Routes.SCAN) {
                                            popUpTo(Routes.HOME) { inclusive = false }
                                            launchSingleTop = true
                                        }
                                    },
                                    icon = { Icon(Icons.Default.Security, contentDescription = null) },
                                    label = { Text("Scan") }
                                )
                            }
                        }
                    }
                ) { padding ->
                    Box(Modifier.padding(padding)) {
                        SuperEncrypterNavHost(
                            navController = navController,
                            repository = repository
                        )
                    }
                }
            }
        }
    }
}
