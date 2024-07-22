package org.technoserve.cherieapp.ui.screens


import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.SharedPreferences
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color.rgb
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import android.util.Log

import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.joda.time.Instant
import org.pytorch.IValue
import org.pytorch.torchvision.TensorImageUtils
import org.technoserve.cherieapp.Preferences
import org.technoserve.cherieapp.database.Prediction
import org.technoserve.cherieapp.database.PredictionViewModel
import org.technoserve.cherieapp.database.PredictionViewModelFactory
import org.technoserve.cherieapp.ui.components.ButtonPrimary
import kotlin.math.pow
import android.content.Intent
import android.view.MotionEvent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavController
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.amazonaws.AmazonClientException
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.mobile.client.AWSMobileClient
import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.PutObjectRequest
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.technoserve.cherieapp.R
import org.technoserve.cherieapp.hasLocationPermission
import org.technoserve.cherieapp.helpers.ImageUtils
import org.technoserve.cherieapp.workers.UploadWorker
import org.technoserve.cherieapp.workers.WORKER_IMAGE_NAMES_KEY
import org.technoserve.cherieapp.workers.WORKER_IMAGE_URIS_KEY
import org.technoserve.cherieapp.workers.WORKER_PREDICTION_IDS_KEY
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Date
import java.util.concurrent.TimeUnit






@SuppressLint("MissingPermission")
@Composable
fun UploadScreen(imageAsByteArray: ByteArray) {
    val bitmap: Bitmap = BitmapFactory.decodeByteArray(imageAsByteArray, 0, imageAsByteArray.size)
    val context_ = LocalContext.current
    val sharedPreferences = remember { getSharedPreferences(context_) }
    val selected_country = readCountry(sharedPreferences)
    val context = LocalContext.current as Activity
    var user = FirebaseAuth.getInstance().currentUser

    var newId by remember { mutableStateOf(0) }
    val workManager: WorkManager = WorkManager.getInstance(context)
    val hasBeenScheduledForUpload = remember { mutableStateOf(false) }

    var uploading = remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val predictionViewModel: PredictionViewModel = viewModel(
        factory = PredictionViewModelFactory(context.applicationContext as Application)
    )


    val now = Date()
    val objectKey = "countries/${selected_country}/images/${now}.jpg"

    val scaffoldState = rememberScaffoldState()




    Scaffold(
        scaffoldState = scaffoldState,
        topBar = { Navbar() }
    ) { _ ->
        val prediction =
            predictionViewModel.getLastPrediction().observeAsState(listOf()).value
        if (prediction.isNotEmpty()) {
            val item = prediction[0]
            Log.d("petit",item.id.toString())
            newId = item.id.toInt() + 1
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .wrapContentSize(Alignment.TopCenter)
                .padding(top = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f),
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Input Image",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = 32.dp, end = 32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))


                }
                Spacer(modifier = Modifier.height(32.dp))


                Spacer(modifier = Modifier.height(20.dp))



                Spacer(modifier = Modifier.height(20.dp))

                Button(onClick = {
                    val returnIntent = Intent()
                    context.setResult(Activity.RESULT_CANCELED, returnIntent)
                    context.finish()
//                    uploading.value = true
//                    val storageRef = Firebase.storage.reference
//                    val imageRef = storageRef.child(objectKey)
//                    val uploadTask = imageRef.putBytes(imageAsByteArray)
//                    uploadTask.addOnSuccessListener { taskSnapshot ->
//                        // Image uploaded successfully, now you can save the image URL to Firestore
//
//                        taskSnapshot.storage.downloadUrl.addOnSuccessListener { uri ->
//
//                            // Now, save the image URL to Firestore using Firestore's collection/document structure
//                            val db = Firebase.firestore
//                            val imageDocRef = db.collection("countries/${selected_country}/images")
//
//                            imageDocRef.add(hashMapOf(
//                                "imageUrl" to uri.toString(),
//                                "country" to selected_country,
//                                "annotated" to false
//                            ))
//                                .addOnSuccessListener {
//                                    uploading.value = false
//                                    scope.launch {
//                                        scaffoldState.snackbarHostState.showSnackbar("Uploaded successfully")
//                                    }
//
//                                }
//                                .addOnFailureListener { e ->
//                                    uploading.value = false
//                                    scope.launch {
//                                        scaffoldState.snackbarHostState.showSnackbar("${e.message}")
//                                    }
//                                    Log.d("upload test","${e.message}")
//                                }
//                        }
//
//                    }



                },
                    modifier = Modifier.requiredWidth(160.dp),
                    shape = RoundedCornerShape(20.dp),
                    enabled = true,
                    elevation = ButtonDefaults.elevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 4.dp,
                        disabledElevation = 0.dp
                    )
                ){
                    if(uploading.value){
                        CircularProgressIndicator(
                            color=Color.White
                        )
                    }else{
                        Text(
                            text = "Upload",
                            modifier = Modifier.padding(12.dp, 4.dp, 12.dp, 4.dp),
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
                Spacer(modifier = Modifier.height(48.dp))
            }
    }
}

@Composable
fun Navbar() {
    val context = LocalContext.current as Activity
    TopAppBar(
        title = {
            Text(
                text = "Upload screen",
                color = Color.White,
                fontSize = 18.sp,
            )
        },
        backgroundColor = MaterialTheme.colors.primary,
        contentColor = Color.Black,
        navigationIcon = {
            IconButton(onClick = {
                val returnIntent = Intent()
                context.setResult(Activity.RESULT_CANCELED, returnIntent)
                context.finish()
            }) {
                Icon(
                    imageVector = Icons.Outlined.ArrowBack,
                    contentDescription = null,
                    tint = Color.White
                )
            }
        }
    )
}




private fun getSharedPreferences(context: Context): SharedPreferences {
    return context.getSharedPreferences("country_preference", Context.MODE_PRIVATE)
}


private fun readCountry(sharedPreferences: SharedPreferences): String {
    return sharedPreferences.getString("country", "") ?: ""
}
