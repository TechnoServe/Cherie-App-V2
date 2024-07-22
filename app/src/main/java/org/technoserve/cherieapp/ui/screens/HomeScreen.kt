package org.technoserve.cherieapp.ui.screens

import android.os.Parcelable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navArgument
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.CoroutineScope
import org.technoserve.cherieapp.ui.navigation.BottomNavigationBar
import org.technoserve.cherieapp.ui.navigation.NavigationItem

@ExperimentalFoundationApi
@Composable
fun HomeScreen() {
    val navController = rememberNavController()
    val scaffoldState = rememberScaffoldState()
    val scope = rememberCoroutineScope()
    Scaffold(
        scaffoldState = scaffoldState,
        modifier = Modifier.fillMaxSize(),
        bottomBar = { BottomNavigationBar(navController) },
    ) {
        innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()){
            Navigation(navController, scaffoldState, scope)
        }
    }
}

data class ImageModel(val imageAsByteArray: ByteArray)

@OptIn(ExperimentalMaterialApi::class)
@ExperimentalFoundationApi
@Composable
fun Navigation(
    navController: NavHostController,
    scaffoldState: ScaffoldState,
    homeScope: CoroutineScope
) {
    NavHost(navController, startDestination = NavigationItem.Initial.route) {
        composable(NavigationItem.Inference.route) {
            InferenceScreen(
                navController = navController,
                scaffoldState = scaffoldState,
                homeScope = homeScope
            )
        }
        composable(NavigationItem.Initial.route) {
            InitialScreen(
                navController = navController,
                scaffoldState = scaffoldState,
                homeScope = homeScope
            )
        }

        composable(NavigationItem.Logs.route) {
            SavedPredictionsScreen(
                scaffoldState = scaffoldState,
                homeScope = homeScope,
                navController = navController
            )
        }
        composable(NavigationItem.Profile.route) {
            ProfileScreen(
                scaffoldState = scaffoldState,
                homeScope = homeScope,
                navController = navController
            )
        }
        composable(NavigationItem.SelectCountry.route) {
            SelectCountry(
                scaffoldState = scaffoldState,
                homeScope = homeScope,
                navController = navController
            )
        }
        composable(NavigationItem.ChangeCountry.route) {
            ChangeCountryScreen(
                scaffoldState = scaffoldState,
                homeScope = homeScope,
                navController = navController
            )
        }
        composable(NavigationItem.ChooseImage.route) {
            ChooseImageScreen(
                scaffoldState = scaffoldState,
                homeScope = homeScope,
                navController = navController
            )
        }
    }
}
