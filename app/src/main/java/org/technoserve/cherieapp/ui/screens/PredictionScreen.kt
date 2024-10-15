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
import android.widget.Toast
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import org.technoserve.cherieapp.BeanClassifier
import org.technoserve.cherieapp.Pix2PixModule
import org.technoserve.cherieapp.R
import org.technoserve.cherieapp.hasLocationPermission
import org.technoserve.cherieapp.helpers.ImageUtils
import org.technoserve.cherieapp.workers.UploadWorker
import org.technoserve.cherieapp.workers.WORKER_IMAGE_NAMES_KEY
import org.technoserve.cherieapp.workers.WORKER_IMAGE_URIS_KEY
import org.technoserve.cherieapp.workers.WORKER_PREDICTION_IDS_KEY
import java.io.File
import java.util.concurrent.TimeUnit


fun distance(col1: IntArray, col2: IntArray): Double {
    val (r1, g1, b1) = col1
    val (r2, g2, b2) = col2
    return (r1 - r2 + 0.0).pow(2.0) + (g1 - g2 + 0.0).pow(2.0) + (b1 - b2 + 0.0).pow(2.0)
}

val refColors: Array<IntArray> = arrayOf(
    intArrayOf(255, 0, 0),        // red
    intArrayOf(0, 255, 0),        // green
    intArrayOf(0, 0, 255),        // blue
    intArrayOf(0, 0, 0),          // black
    intArrayOf(255, 255, 255),    // white
)

enum class ScoreType {
    RIPE, UNDERRIPE, OVERRIPE
}

fun nearestPixel(col1: IntArray): Int {
    var idxClosest = 0
    var minDistance = distance(col1, refColors[idxClosest])

    for (i in 1 until refColors.size) {
        val currentDistance = distance(col1, refColors[i])
        if (currentDistance < minDistance) {
            minDistance = currentDistance
            idxClosest = i
        }
    }
    val closestColor = refColors[idxClosest]
    return rgb(closestColor[0], closestColor[1], closestColor[2])
}


@SuppressLint("MissingPermission")
@Composable
fun PredictionScreen(imageAsByteArray: ByteArray,country:String) {
    val bitmap: Bitmap = BitmapFactory.decodeByteArray(imageAsByteArray, 0, imageAsByteArray.size)
    val mask: Bitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
    val context_ = LocalContext.current
    val sharedPreferences = remember { getSharedPreferences(context_) }
    val selected_country = readCountry(sharedPreferences)
    val context = LocalContext.current as Activity
    var user = FirebaseAuth.getInstance().currentUser
    val complete = remember { mutableStateOf(false) }
    val mylocation = remember { mutableStateOf("") }
    var newId by remember { mutableStateOf(0) }
    val workManager: WorkManager = WorkManager.getInstance(context)
    val hasBeenScheduledForUpload = remember { mutableStateOf(false) }
    val beanClassifier = remember {
        if(selected_country.lowercase() == "ethiopia") BeanClassifier(context,country) else null
    }
    var ratingState by remember {
        mutableStateOf(0)
    }
    val predictionViewModel: PredictionViewModel = viewModel(
        factory = PredictionViewModelFactory(context.applicationContext as Application)
    )

    val sharedPrefs by remember { mutableStateOf(Preferences(context)) }

    val scaffoldState = rememberScaffoldState()
    val scope = rememberCoroutineScope()

    var redCount = 0
    var blueCount = 0
    var greenCount = 0

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }






    var classPercentages by remember { mutableStateOf(listOf<Double>()) }
    var totalDetections by remember { mutableStateOf(0) }
    var showMask by remember { mutableStateOf(true) }
    var resultImage by remember { mutableStateOf<Bitmap?>(null) }



    fun runModel() {
        val NORM_MEAN_RGB = floatArrayOf(0.5f, 0.5f, 0.5f)
        val NORM_STD_RGB = floatArrayOf(0.5f, 0.5f, 0.5f)
        val inputTensor = TensorImageUtils.bitmapToFloat32Tensor(
            bitmap,
            NORM_MEAN_RGB, NORM_STD_RGB
        )

        val startTime = SystemClock.elapsedRealtime()
        val outputTensor = Pix2PixModule.mModule!!.forward(IValue.from(inputTensor)).toTensor()
        val inferenceTime = SystemClock.elapsedRealtime() - startTime
        Log.d("ImageSegmentation", "inference time (ms): $inferenceTime")

        val scores = outputTensor.dataAsFloatArray

        val width: Int = bitmap.width
        val height: Int = bitmap.height

        var max = 0f
        var min = 999999f

        for (f in scores) {
            if (f > max) {
                max = f
            }
            if (f < min) {
                min = f
            }
        }

        val delta = (max - min).toInt()
        val pixels = IntArray(width * height * 4)

        for (i in 0 until width * height) {
            val r = ((scores[i] - min) / delta * 255.0f).toInt()
            val g = ((scores[i + width * height] - min) / delta * 255.0f).toInt()
            val b = ((scores[i + width * height * 2] - min) / delta * 255.0f).toInt()

            pixels[i] = nearestPixel(intArrayOf(r, g, b))

            when (pixels[i]) {
                rgb(255, 0, 0) -> redCount++
                rgb(0, 255, 0) -> greenCount++
                rgb(0, 0, 255) -> blueCount++
            }
        }
//        val bitmap = BitmapFactory.decodeByteArray(imageAsByteArray, 0, imageAsByteArray.size)
        var _resultImage = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        _resultImage.setPixels(pixels, 0, width, 0, 0, width, height)
        resultImage = _resultImage
        totalDetections = redCount + greenCount + blueCount
        classPercentages = listOf(
            (redCount + 0.0) / totalDetections * 100,
            (greenCount + 0.0) / totalDetections * 100,
            (blueCount + 0.0) / totalDetections * 100
        )
        complete.value = true
        sharedPrefs.generatedPredictions++
    }

    LaunchedEffect(imageAsByteArray) {
        if (selected_country.lowercase() == "ethiopia"){
            val bitmap = BitmapFactory.decodeByteArray(imageAsByteArray, 0, imageAsByteArray.size)
            val (processedImage, percentages, detections) = beanClassifier!!.processImage(bitmap)
            // if detections < 1, show a toast with "No cherries detected"
            if (detections < 1){
                resultImage = processedImage
                classPercentages = listOf<Double>(0.0,0.0,0.0)
                complete.value = true
                Toast.makeText(context, "No cherries detected", Toast.LENGTH_SHORT).show();
            }else{
                resultImage = processedImage
                classPercentages = percentages
                totalDetections = detections
                complete.value = true
            }
        }else{
            if (context.hasLocationPermission()) {
                Log.d("hihihi","aha kbsa")
                fusedLocationClient.lastLocation.addOnSuccessListener { locationResult ->
                    locationResult?.let { lastLocation ->
                        mylocation.value = "${lastLocation.latitude}, ${lastLocation.longitude}"
                        Log.d("mytest5",mylocation.value)
                    }
                }
            }
            Log.d("Selected country",selected_country)
            GlobalScope.launch {
                val modelDir = File(context.filesDir, "model")
                val modelFile = File(modelDir, "${selected_country.lowercase()}.ptl")
                val modelPath = modelFile.absolutePath
                Log.d("prediction path test",modelPath)
                val startTime = System.nanoTime()
                Pix2PixModule.loadModel(context,modelPath)
                Log.i("SELECTED COUNTRY 2",country)
                Log.d(
                    "Model Task",
                    "Loading model took: " + ((System.nanoTime() - startTime) / 1000000) + "mS\n"
                )
                runModel()
                Log.d(
                    "Model Task",
                    "Running inference took: " + ((System.nanoTime() - startTime) / 1000000) + "mS\n"
                )
            }
        }


    }

    fun showStillRunningMessage() {
//        scope.launch {
//            scaffoldState.snackbarHostState.showSnackbar("Prediction currently running")
//        }
    }


    val onRetry = {
        if (complete.value) {
            complete.value = false
//            runModel()
        } else {
            showStillRunningMessage()
        }
    }

    fun upload(item: Prediction) {
        Log.d("PETIT",newId.toString())
        val fileName = (user?.uid) + "_" + newId
        val combinedBitmaps = ImageUtils.combineBitmaps(item.inputImage, item.mask)
        val imageUri = ImageUtils.createTempBitmapUri(context, combinedBitmaps, fileName)

        val fileNames = mutableListOf(fileName)
        val imageUris = mutableListOf(imageUri.toString())
        val predictionIds = mutableListOf(newId.toLong())

        predictionViewModel.updateSyncListStatus(predictionIds)

        val workdata = workDataOf(
            WORKER_IMAGE_NAMES_KEY to fileNames.toTypedArray(),
            WORKER_IMAGE_URIS_KEY to imageUris.toTypedArray(),
            WORKER_PREDICTION_IDS_KEY to predictionIds.toTypedArray()
        )

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresBatteryNotLow(true)
            .build()

        val uploadRequest = OneTimeWorkRequestBuilder<UploadWorker>()
            .setInputData(workdata)
            .addTag("SINGLE UPLOAD TAG " + newId)
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                3,
                TimeUnit.MINUTES
            )
            .build()

        workManager.beginWith(uploadRequest).enqueue()
        hasBeenScheduledForUpload.value = true
//        scope.launch {
//            scaffoldState.snackbarHostState.showSnackbar("Image has been queued for upload")
//        }
    }

    fun calculateRipenessScore(scoreType: ScoreType = ScoreType.RIPE): Float {
        return when (scoreType) {
            ScoreType.RIPE -> ((redCount + 0f) / (redCount + greenCount + blueCount + 0f)) * 100
            ScoreType.UNDERRIPE -> ((greenCount + 0f) / (redCount + greenCount + blueCount + 0f)) * 100
            ScoreType.OVERRIPE -> ((blueCount + 0f) / (redCount + greenCount + blueCount + 0f)) * 100
        }
    }

    Scaffold(
        scaffoldState = scaffoldState,
        topBar = { Nav(onRetry = onRetry) }
    ) {
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
            if (complete.value) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row (
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,

                    ) {
                        Text(stringResource(id = R.string.show_detection), color = Color.Black)
                        showMask.let {
                            Checkbox(
                                checked = it,
                                onCheckedChange = {
                                    showMask = it
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = MaterialTheme.colors.primary,
                                    uncheckedColor = MaterialTheme.colors.secondary
                                )
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f),
                    ) {
                        showMask.let { shouldShowMask->

                            resultImage?.let { image ->
                                Image(
                                    bitmap = if (shouldShowMask) image.asImageBitmap() else bitmap.asImageBitmap(),
                                    contentDescription = "Processed Image",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(start = 32.dp, end = 32.dp)
                                )
                            }

                        }


                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = stringResource(id = R.string.total_detection)+" $totalDetections",
                    color = MaterialTheme.colors.primary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    classPercentages.let {
                        CategoryIndicators(it)
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))

                // Add the recommendation here
                RipenessRecommendation(classPercentages)

//                Text(
//                    text = stringResource(id = R.string.rate_this_prediction),
//                    color = Color.Black,
//                    textAlign = TextAlign.Center,
//                    fontSize = 14.sp,
//                    fontWeight = FontWeight.Bold
//                )
//
//                Spacer(modifier = Modifier.height(20.dp))
//
//                RatingBar(ratingState = ratingState, onStateChange = { newValue ->
//                    ratingState = newValue.toInt()
//                })
//
                Spacer(modifier = Modifier.height(20.dp))

                ButtonPrimary(onClick = {
                    resultImage?.let { image ->
                        val item = addPrediction(
                            predictionViewModel,
                            bitmap,
                            image,
                            classPercentages,
                            mylocation.value,
                            selected_country,
                            ratingState
                        )
                        val returnIntent = Intent()
                        context.setResult(Activity.RESULT_OK, returnIntent)
                        context.finish()
                        upload(item)
                    }
                }, label = stringResource(id = R.string.save))
                Spacer(modifier = Modifier.height(48.dp))
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    LinearProgressIndicator()
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        text = "${stringResource(id = R.string.predicting_ripeness)}...",
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}


@Composable
fun CategoryIndicators(classPercentages: List<Double>, white: Boolean = false) {

// white bg box
    Box(
        Modifier.padding(15.dp).background(Color.White, shape = RoundedCornerShape(8.dp)).fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround,
            modifier = Modifier
                .height(48.dp)
                .fillMaxWidth()
//                            .padding(start = 60.dp)
        ) {
            Text(
                text = "${stringResource(id = R.string.underripe)}: ${classPercentages[2].toInt()}%",
                color = Color.Green,
                textAlign = TextAlign.Center,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "${stringResource(id = R.string.ripe)}: ${classPercentages[1].toInt()}%",
                color = Color.Blue,
                textAlign = TextAlign.Center,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "${stringResource(id = R.string.overripe)}: ${classPercentages[0].toInt()}%",
                color = Color.Red,
//                            modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun RipenessRecommendation(classPercentages: List<Double>, white: Boolean = false) {
    val ripePercentage = classPercentages[1]
    val unripePercentage = classPercentages[2]

    val message = when {
//        unripePercentage > 15 -> stringResource(id = R.string.alert_msg_3)
        ripePercentage < 80 -> stringResource(id = R.string.alert_msg_1)
        ripePercentage > 95 -> stringResource(id = R.string.alert_msg_2)

        else -> return  // No recommendation needed
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .background(Color.Yellow.copy(alpha = 0.2f), shape = RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.body1,
            color = if (white) Color.White else Color.Black,
        )
    }
}

@Composable
fun Nav(onRetry: () -> Unit) {
    val context = LocalContext.current as Activity
    TopAppBar(
        title = {
            Text(
                text = stringResource(id = R.string.prediction_screen),
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
        },
        actions = {
            IconButton(onClick = {
                GlobalScope.launch {
                    onRetry()
                }
            }) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = null,
                    tint = Color.White
                )
            }
        }
    )
}

fun addPrediction(
    predictionViewModel: PredictionViewModel,
    inputImage: Bitmap,
    resultImage: Bitmap,
    classPercentages: List<Double>,
    coordinates: String,
    region: String,
    rating: Int
): Prediction {
    val prediction = Prediction(
        inputImage,
        resultImage,
        classPercentages[1].toFloat(), // Ripe
        classPercentages[0].toFloat(), // Overripe
        classPercentages[2].toFloat(), // Underripe
        coordinates,
        region,
        rating,
        createdAt = Instant.now().millis
    )
    predictionViewModel.addPrediction(prediction)
    return prediction
}


@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun RatingBar(
    modifier: Modifier = Modifier,
    ratingState: Int,
    onStateChange: (String) -> Unit
) {


    var selected by remember {
        mutableStateOf(false)
    }
    val size by animateDpAsState(
        targetValue = if (selected) 72.dp else 64.dp,
        spring(Spring.DampingRatioMediumBouncy)
    )

    Row(
        modifier = Modifier.fillMaxWidth()


        ,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        for (i in 1..5) {
            Icon(
                painter = painterResource(id = R.drawable.ic_star),
                contentDescription = "star",
                modifier = modifier
                    .width(size)
                    .height(size)
                    .pointerInteropFilter {
                        when (it.action) {
                            MotionEvent.ACTION_DOWN -> {
                                selected = true
                                onStateChange(i.toString())
                            }

                            MotionEvent.ACTION_UP -> {
                                selected = false
                            }
                        }
                        true
                    },
                tint = if (i <= ratingState) Color(0xFFFFD700) else Color(0xFFA2ADB1)
            )
        }
    }
}

private fun getSharedPreferences(context: Context): SharedPreferences {
    return context.getSharedPreferences("country_preference", Context.MODE_PRIVATE)
}
private fun readCountry(sharedPreferences: SharedPreferences): String {
    return sharedPreferences.getString("country", "") ?: ""
}

private fun saveCountry(sharedPreferences: SharedPreferences, country: String) {
    with(sharedPreferences.edit()) {
        putString("country", country)
        apply()
    }
}

