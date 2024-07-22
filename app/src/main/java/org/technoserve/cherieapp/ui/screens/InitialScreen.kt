package org.technoserve.cherieapp.ui.screens

import CustomSpinner
import android.content.SharedPreferences
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.technoserve.cherieapp.R
import org.technoserve.cherieapp.ui.navigation.NavigationItem
import org.technoserve.cherieapp.workers.TAG
import java.io.File


fun downloadModel(){

}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InitialScreen(
    scaffoldState: ScaffoldState,
    homeScope: CoroutineScope,
    navController: NavController
) {
    val context = LocalContext.current
    val sharedPreferences = remember { getSharedPreferences(context) }
    val selected_country = readCountry(sharedPreferences)
    LaunchedEffect(Unit) {
        Log.d(TAG,selected_country)
    }

    // UI code that depends on the fetched data
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if(selected_country=="Guatemala" || selected_country=="Honduras" || selected_country=="Ethiopia"  || selected_country=="Rwanda" ){
                if(selected_country=="Guatemala" || selected_country=="Honduras" || selected_country=="Ethiopia"){
                    navController.navigate(NavigationItem.Inference.route)
                }else{
                    navController.navigate(NavigationItem.ChooseImage.route)
                }

            }
            else{
                SelectCountry(scaffoldState = scaffoldState, homeScope = homeScope, navController = navController)
            }
        }
    }
}


@Composable
fun SelectCountry(
    scaffoldState: ScaffoldState,
    homeScope: CoroutineScope,
    navController: NavController
){
    val selected = remember {
        mutableStateOf("Ethiopia")
    }
    val isDarkMode = isSystemInDarkTheme()
    val downloading = remember { mutableStateOf(false) }
    val context = LocalContext.current
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val sharedPreferences = remember { getSharedPreferences(context) }
    val modelDir = File(context.filesDir, "model")
    modelDir.mkdirs()
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier= Modifier
                .width(screenWidth * 0.8f)
                .shadow(
                    if (isDarkMode) Color.Black else Color(0xFFced4da),
                    borderRadius = 7.dp,
                    offsetX = 2.dp,
                    offsetY = 2.dp,
                    spread = 3.dp,
                    blurRadius = 20.dp
                )
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(screenHeight * 0.4f)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {

                if (downloading.value) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        LinearProgressIndicator()
                        Spacer(modifier = Modifier.height(32.dp))
                        Text(
                            text = "${stringResource(id = R.string.downloading_model)}...",
                            color = Color.Black,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                } else {
                    Text(
                        text = stringResource(id = R.string.select_country),
                        style = MaterialTheme.typography.h4,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(50.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CustomSpinner(
                            availableQuantities = options,
                            selectedItem = selected.value,
                            onItemSelected = { s ->
                                selected.value = s;
                            })
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = {
                            downloading.value = true
                            val modelRef =
                                Firebase.storage.reference.child("models/${selected.value.lowercase()}.ptl")
                            val modelFile = File(modelDir, "${selected.value.lowercase()}.ptl")
                            saveCountry(
                                sharedPreferences,
                                selected.value
                            );
                            modelRef.downloadUrl.addOnSuccessListener {
                                modelRef.getFile(modelFile).addOnSuccessListener {

                                    Log.d("download test", "successfull :-)")
                                    val modelPath = modelFile.absolutePath
                                    Log.d("retrieve test", modelPath)
                                    downloading.value = false
                                    navController.navigate(NavigationItem.Inference.route)
                                }.addOnFailureListener { exception ->
                                    downloading.value = false
                                    homeScope.launch {
                                        scaffoldState.snackbarHostState.showSnackbar(
                                            exception.message ?: "Unkown error... download failed"
                                        )
                                    }
                                    Log.d(
                                        "download test",
                                        exception.message ?: "Unkown error... download failed"
                                    )
                                }

                            }.addOnFailureListener {
                                navController.navigate(NavigationItem.ChooseImage.route)
                            }

                        },
                        modifier = Modifier.requiredWidth(240.dp),
                        shape = RoundedCornerShape(20.dp),
                        elevation = ButtonDefaults.elevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 4.dp,
                            disabledElevation = 0.dp
                        )
                    ) {
                        Text(
                            text = stringResource(id = R.string.save),
                            modifier = Modifier.padding(12.dp, 4.dp, 12.dp, 4.dp),
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

            }
        }
    }



private fun getSharedPreferences(context: Context): SharedPreferences {
    return context.getSharedPreferences("country_preference", Context.MODE_PRIVATE)
}

private fun saveCountry(sharedPreferences: SharedPreferences, country: String) {
    with(sharedPreferences.edit()) {
        putString("country", country)
        apply()
    }
}

private fun readCountry(sharedPreferences: SharedPreferences): String {
    return sharedPreferences.getString("country", "") ?: ""
}