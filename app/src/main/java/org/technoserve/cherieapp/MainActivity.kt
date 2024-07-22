package org.technoserve.cherieapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.technoserve.cherieapp.ui.theme.CherieTheme
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.*
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import org.technoserve.cherieapp.helpers.getPermissionsText
import org.technoserve.cherieapp.ui.navigation.BottomNavigationBar
import org.technoserve.cherieapp.ui.screens.Navigation

@ExperimentalFoundationApi
@OptIn(ExperimentalPermissionsApi::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_Cherie_NoActionBar)
//        Pix2PixModule.loadModel(this)
        setContent {
            CherieTheme {
                val navController = rememberNavController()
                val scaffoldState = rememberScaffoldState()
                val scope = rememberCoroutineScope()
                val multiplePermissionsState = rememberMultiplePermissionsState(
                    listOf(
                        android.Manifest.permission.CAMERA,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION,
                        android.Manifest.permission.ACCESS_FINE_LOCATION
                    )
                )

                LaunchedEffect(true) {

                    multiplePermissionsState.launchMultiplePermissionRequest()


                }

                Surface(color = MaterialTheme.colors.background) {
                    PermissionsWrapper(
                        multiplePermissionsState,
                        navigateToSettingsScreen = {
                            startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", packageName, null)
                                )
                            )
                        }
                    ) {
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
                }
            }
        }
    }
}




@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun PermissionsWrapper(
    multiplePermissionsState: MultiplePermissionsState,
    navigateToSettingsScreen: () -> Unit,
    content: @Composable () -> Unit
) {
    when {
        // If all permissions are granted, then show screen with the feature enabled
        multiplePermissionsState.allPermissionsGranted -> {
            content()
        }
        // If the user denied any permission but a rationale should be shown, or the user sees
        // the permissions for the first time, explain why the feature is needed by the app and
        // allow the user decide if they don't want to see the rationale any more.


        multiplePermissionsState.shouldShowRationale ||
                !multiplePermissionsState.permissionRequested -> {
            Column(
                modifier = Modifier
                    .padding(32.dp)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val revokedPermissionsText = getPermissionsText(
                    multiplePermissionsState.revokedPermissions
                )
                Text(
                    "Permissions Required",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))
                Text("$revokedPermissionsText important.", textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Please grant all of them for the app to function properly.",
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 36.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Button(
                        onClick = {
                            multiplePermissionsState.launchMultiplePermissionRequest()
                        },
                        modifier = Modifier.requiredWidth(240.dp),
                        shape = RoundedCornerShape(0),
                        elevation = ButtonDefaults.elevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 4.dp,
                            disabledElevation = 0.dp
                        )
                    ) {
                        Text(
                            text = "Request permissions",
                            modifier = Modifier.padding(12.dp, 4.dp, 12.dp, 4.dp),
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
        // If the criteria above hasn't been met, the user denied some permission. Let's present
        // the user with a FAQ in case they want to know more and send them to the Settings screen
        // to enable them the future there if they want to.
        else -> {
            Column(
                modifier = Modifier
                    .padding(32.dp)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val revokedPermissionsText = getPermissionsText(
                    multiplePermissionsState.revokedPermissions
                )

                Text(
                    "Permissions Required",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))
                Text("$revokedPermissionsText denied.", textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Please grant access on the Settings screen for the app to function properly.",
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 36.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Button(
                        onClick = navigateToSettingsScreen,
                        modifier = Modifier.requiredWidth(240.dp),
                        shape = RoundedCornerShape(0),
                        elevation = ButtonDefaults.elevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 4.dp,
                            disabledElevation = 0.dp
                        )
                    ) {
                        Text(
                            text = "Open Settings",
                            modifier = Modifier.padding(12.dp, 4.dp, 12.dp, 4.dp),
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }


            }
        }
    }
}



