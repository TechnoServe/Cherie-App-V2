package org.technoserve.cherieapp.ui.screens

import CustomSpinner
import android.content.SharedPreferences
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.MaterialTheme.typography
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
import java.util.zip.ZipFile
import kotlin.system.measureTimeMillis


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
    val selectedCountry = readCountry(sharedPreferences)
    val modelDir = File(context.filesDir, "model")

    // Retrieve the saved download percentage on initial screen load
    downloadPercentage.value = getSavedDownloadPercentage(context)

    LaunchedEffect(Unit) {
        Log.d(TAG, selectedCountry)
        // Initiate your download and track progress
        // Call startDownload() here with proper parameters
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (downloadPercentage.value == 100) {

                // Check if a valid country is selected
                if (selectedCountry == "Guatemala" || selectedCountry == "Honduras" || selectedCountry == "Ethiopia") {
                    navController.navigate(NavigationItem.Inference.route)
                } else if (selectedCountry == "Rwanda") {
                    navController.navigate(NavigationItem.ChooseImage.route)
                }
            } else {
                // Show SelectCountry if the download is incomplete
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
//                    .height(screenHeight * 0.5f)
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
                    Text(
                        text = "${stringResource(id = R.string.Percentage_download_model)}... ${downloadPercentage.value}%",
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
                        if (selected.value.lowercase() == "ethiopia") {
                            downloading.value = true
                            val detectionModelRef =
                                Firebase.storage.reference.child("models/${selected.value.lowercase()}/mobile_model_b4_nms.ptl")
                            val classifierModelRef =
                                Firebase.storage.reference.child("models/${selected.value.lowercase()}/classifier.pt")

                            // Create directory for model files
                            val detectionModelFile = File(modelDir, "${selected.value.lowercase()}_mobile_model_b4_nms.ptl")
                            val classifierModelFile = File(modelDir, "${selected.value.lowercase()}_classifier.pt")
                            saveCountry(sharedPreferences, selected.value)

                            val modelPaths = listOf(
                                Pair(detectionModelRef, detectionModelFile),
                                Pair(classifierModelRef, classifierModelFile)
                            )

                            var loaded_items = 0

                            for (modelPath in modelPaths) {
                                val modelRef = modelPath.first
                                val modelFile = modelPath.second

                                modelRef.downloadUrl.addOnSuccessListener {
                                    modelRef.getFile(modelFile).addOnSuccessListener {
                                        Log.d("download test", "successful :-)")
                                        loaded_items += 1

                                        // Update the UI or state to show progress percentage
                                        updateDownloadPercentage(loaded_items, modelPaths.size,context)

                                        if (loaded_items == modelPaths.size) {
                                            downloading.value = false
                                            navController.navigate(NavigationItem.Inference.route)
                                        }
                                    }.addOnProgressListener { taskSnapshot ->
                                        val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount).toInt()
                                        Log.d("Download Progress", "File: ${modelFile.name} is $progress% downloaded")
                                        // Update the UI with the current download percentage
                                        // For example, you could store it in a state variable
                                        updateDownloadPercentage(taskSnapshot.bytesTransferred.toInt(), taskSnapshot.totalByteCount.toInt(), context)
                                    }.addOnFailureListener { exception ->
                                        downloading.value = false
                                        homeScope.launch {
                                            scaffoldState.snackbarHostState.showSnackbar(
                                                exception.message ?: "Unknown error... download failed"
                                            )
                                        }
                                        Log.d("download test", exception.message ?: "Unknown error... download failed")
                                    }
                                }.addOnFailureListener {
                                    navController.navigate(NavigationItem.ChooseImage.route)
                                }
                            }

                        }else{
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
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(id = R.string.collaboration_details),
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center,
                    color = Color.Black,
                    style = typography.caption,
                )

            }

        }
    }
}



private fun getSharedPreferences(context: Context): SharedPreferences {
    return context.getSharedPreferences("country_preference", Context.MODE_PRIVATE)
}
private val downloadPercentage = mutableStateOf(0)

private fun updateDownloadPercentage(current: Int, total: Int,context: Context) {
    val percentage = (current * 100) / total
    downloadPercentage.value = percentage
    saveDownloadPercentage(context, percentage) // Save the percentage
    Log.d("Download Percentage", "Download is at $percentage%")
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
private fun saveDownloadPercentage(context: Context, percentage: Int) {
    val sharedPreferences = context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
    sharedPreferences.edit().putInt("download_percentage", percentage).apply()
}

// Functionz to get the saved download percentage from SharedPreferences
private fun getSavedDownloadPercentage(context: Context): Int {
    val sharedPreferences = context.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
    return sharedPreferences.getInt("download_percentage", 0) // Default to 0 if not found
}