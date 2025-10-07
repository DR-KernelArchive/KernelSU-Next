package com.rifsxd.ksunext.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.rememberNavController
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import com.ramcosta.composedestinations.DestinationsNavHost
import com.ramcosta.composedestinations.animations.NavHostAnimatedDestinationStyle
import com.ramcosta.composedestinations.generated.NavGraphs
import com.ramcosta.composedestinations.generated.destinations.FlashScreenDestination
import com.ramcosta.composedestinations.utils.rememberDestinationsNavigator
import com.rifsxd.ksunext.Natives
import com.rifsxd.ksunext.ui.screen.FlashIt
import com.rifsxd.ksunext.ui.theme.KernelSUTheme
import com.rifsxd.ksunext.ui.util.*
import com.rifsxd.ksunext.ui.viewmodel.ModuleViewModel
import com.rifsxd.ksunext.ui.viewmodel.SuperUserViewModel

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase?.let { LocaleHelper.applyLanguage(it) })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        super.onCreate(savedInstanceState)

        val isManager = Natives.becomeManager(packageName)
        if (isManager) install()

        val zipUri: ArrayList<Uri>? = if (intent.data != null) {
            arrayListOf(intent.data!!)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra("uris", Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra("uris")
            }
        }

        setContent {
            val prefs = getSharedPreferences("settings", MODE_PRIVATE)
            val amoledMode = prefs.getBoolean("enable_amoled", false)

            val moduleViewModel: ModuleViewModel = viewModel()
            val superUserViewModel: SuperUserViewModel = viewModel()

            KernelSUTheme(amoledMode = amoledMode) {
                val navController = rememberNavController()
                val snackBarHostState = remember { SnackbarHostState() }
                val currentDestination = navController.currentBackStackEntryAsState().value?.destination
                val bottomBarRoutes = remember {
                    BottomBarDestination.entries.map { it.direction.route }.toSet()
                }
                val navigator = navController.rememberDestinationsNavigator()

                LaunchedEffect(zipUri) {
                    if (!zipUri.isNullOrEmpty()) {
                        navigator.navigate(
                            FlashScreenDestination(
                                flashIt = FlashIt.FlashModules(zipUri),
                                finishIntent = true
                            )
                        )
                    }
                }

                LaunchedEffect(Unit) {
                    if (superUserViewModel.appList.isEmpty()) {
                        superUserViewModel.fetchAppList()
                    }
                    if (moduleViewModel.moduleList.isEmpty()) {
                        moduleViewModel.fetchModuleList()
                    }
                }

                Scaffold(
                    contentWindowInsets = WindowInsets(0, 0, 0, 0)
                ) { innerPadding ->
                    CompositionLocalProvider(
                        LocalSnackbarHost provides snackBarHostState,
                    ) {
                        DestinationsNavHost(
                            modifier = Modifier.padding(innerPadding).windowInsetsPadding(WindowInsets.navigationBars),
                            navGraph = NavGraphs.root,
                            navController = navController,
                            defaultTransitions = object : NavHostAnimatedDestinationStyle() {
                                override val enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
                                    if (targetState.destination.route in bottomBarRoutes && initialState.destination.route in bottomBarRoutes) {
                                        // Slide left/right between bottom bar destinations
                                        val fromIndex = BottomBarDestination.entries.indexOfFirst { it.direction.route == initialState.destination.route }
                                        val toIndex = BottomBarDestination.entries.indexOfFirst { it.direction.route == targetState.destination.route }
                                        if (toIndex > fromIndex) {
                                            slideInHorizontally(initialOffsetX = { it })
                                        } else {
                                            slideInHorizontally(initialOffsetX = { -it })
                                        }
                                    } else if (targetState.destination.route !in bottomBarRoutes) {
                                        // Slide in and fade in for detail screens
                                        slideInHorizontally(
                                            initialOffsetX = { it },
                                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                        ) + fadeIn(animationSpec = tween(400))
                                    } else {
                                        // Default fade in
                                        fadeIn(animationSpec = tween(340))
                                    }
                                }

                                override val exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
                                    if (initialState.destination.route in bottomBarRoutes && targetState.destination.route in bottomBarRoutes) {
                                        // Slide left/right between bottom bar destinations
                                        val fromIndex = BottomBarDestination.entries.indexOfFirst { it.direction.route == initialState.destination.route }
                                        val toIndex = BottomBarDestination.entries.indexOfFirst { it.direction.route == targetState.destination.route }
                                        if (toIndex > fromIndex) {
                                            slideOutHorizontally(targetOffsetX = { -it })
                                        } else {
                                            slideOutHorizontally(targetOffsetX = { it })
                                        }
                                    } else if (initialState.destination.route in bottomBarRoutes && targetState.destination.route !in bottomBarRoutes) {
                                        // Slide out and fade out for main->detail
                                        slideOutHorizontally(
                                            targetOffsetX = { -it / 2 },
                                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                        ) + fadeOut(animationSpec = tween(400))
                                    } else {
                                        // Default fade out
                                        fadeOut(animationSpec = tween(340))
                                    }
                                }

                                override val popEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
                                    if (targetState.destination.route in bottomBarRoutes) {
                                        // Slide in from left and fade in for pop to main
                                        slideInHorizontally(
                                            initialOffsetX = { -it / 2 },
                                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                        ) + fadeIn(animationSpec = tween(400))
                                    } else {
                                        // Pop between details: fade in
                                        fadeIn(animationSpec = tween(340))
                                    }
                                }

                                override val popExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
                                    if (initialState.destination.route !in bottomBarRoutes) {
                                        // Scale down and fade out for detail pop
                                        scaleOut(targetScale = 0.85f, animationSpec = spring(stiffness = Spring.StiffnessLow)) +
                                        fadeOut(animationSpec = tween(400))
                                    } else {
                                        // Tab pop: fade out
                                        fadeOut(animationSpec = tween(340))
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
