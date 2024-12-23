package org.technoserve.cherieapp.ui.navigation

import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import org.technoserve.cherieapp.R

@Composable
fun BottomNavigationBar(navController: NavController) {
    val items = listOf(
        NavigationItem.Inference,
        NavigationItem.Logs,
        NavigationItem.Profile
    )
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    if (currentRoute != "initial" && currentRoute != "choose_image"){
        BottomNavigation(
            backgroundColor =  MaterialTheme.colors.primary,
            contentColor = MaterialTheme.colors.onSurface,
        ) {

            items.forEach { item ->
                BottomNavigationItem(
                    icon = { Icon(painterResource(id = item.icon), contentDescription = item.title) },
                    label = { Text(if(item.title=="Home") stringResource(id = R.string.home)
                    else (if(item.title=="Saved Predictions") stringResource(id = R.string.saved_predictions)
                    else(if(item.title=="Profile") stringResource(id = R.string.profile)
                    else "")),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis) /* optional */
                    },
                    selectedContentColor = Color.White,
                    unselectedContentColor = Color.White.copy(0.4f),
                    alwaysShowLabel = true,
                    selected = currentRoute == item.route,
                    onClick = {
                        if (item.route == NavigationItem.Inference.route && currentRoute == item.route) {
                            // Do nothing special here, the back press logic is handled by the BackHandler
                        } else {
                            navController.navigate(item.route) {
                                launchSingleTop = true
                                restoreState = true
                            }

                        }
                    }
                )
            }
        }
    }
}