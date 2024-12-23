package org.technoserve.cherieapp.ui.screens

import android.app.Activity
import android.app.Application
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.*
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract
import com.firebase.ui.auth.data.model.FirebaseAuthUIAuthenticationResult
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import org.technoserve.cherieapp.R
import org.technoserve.cherieapp.database.Prediction
import org.technoserve.cherieapp.database.PredictionViewModel
import org.technoserve.cherieapp.database.PredictionViewModelFactory
import org.technoserve.cherieapp.helpers.ImageUtils
import org.technoserve.cherieapp.ui.components.ButtonPrimary
import org.technoserve.cherieapp.ui.components.ButtonSecondary
import org.technoserve.cherieapp.workers.*
import java.util.concurrent.TimeUnit


@Composable
fun SavedPredictionScreen(predictionId: Long) {

    val context = LocalContext.current as Activity
    val predictionViewModel: PredictionViewModel = viewModel(
        factory = PredictionViewModelFactory(context.applicationContext as Application)
    )

    val prediction =
        predictionViewModel.getSinglePrediction(predictionId).observeAsState(listOf()).value

    val workManager: WorkManager = WorkManager.getInstance(context)
    val showDeleteDialog = remember { mutableStateOf(false) }
    val showUploadDialog = remember { mutableStateOf(false) }
    val hasBeenScheduledForUpload = remember { mutableStateOf(false) }
    val loginSuccessMsg = stringResource(id = R.string.login_successfull)
    val loginFailedMsg = stringResource(id = R.string.login_failed)
    var user = FirebaseAuth.getInstance().currentUser
    val imageQueuedMsg = stringResource(id = R.string.image_has_been_queued_for_upload)
    val accountNeededMsg = stringResource(id = R.string.you_need_to_have_an_account_to_upload_images)
    val loginMsg = stringResource(id = R.string.login)
    val scaffoldState = rememberScaffoldState()
    val scope = rememberCoroutineScope()

    fun onSignInResult(result: FirebaseAuthUIAuthenticationResult) {
        if (result.resultCode == Activity.RESULT_OK) {
            val newUser = FirebaseAuth.getInstance().currentUser
            user = newUser
            if (newUser != null) {
                scope.launch {
                    scaffoldState.snackbarHostState.showSnackbar(loginSuccessMsg)
                }
            }
            // ...
        } else {
            // Sign in failed. If response is null the user canceled the
            // sign-in flow using the back button. Otherwise check
            // response.getError().getErrorCode() and handle the error.
            // ...
            scope.launch {
                scaffoldState.snackbarHostState.showSnackbar(loginFailedMsg)
            }
        }
    }

    val signInLauncher =
        rememberLauncherForActivityResult(FirebaseAuthUIActivityResultContract()) { res ->
            onSignInResult(res)
        }

    val initLogin: () -> Unit = {
        val providers = arrayListOf(
            AuthUI.IdpConfig.PhoneBuilder().build(),
            AuthUI.IdpConfig.GoogleBuilder().build(),
        )
        val signInIntent = AuthUI.getInstance()
            .createSignInIntentBuilder()
            .setAvailableProviders(providers)
            .setLogo(R.drawable.cherie)
            .setTheme(R.style.LoginTheme)
            .build()
        signInLauncher.launch(signInIntent)
    }

    fun upload(item: Prediction) {
        val fileName = (user?.uid) + "_" + item.id
        val combinedBitmaps = ImageUtils.combineBitmaps(item.inputImage, item.mask)
        val imageUri = ImageUtils.createTempBitmapUri(context, combinedBitmaps, fileName)

        val fileNames = mutableListOf(fileName)
        val imageUris = mutableListOf(imageUri.toString())
        val predictionIds = mutableListOf(item.id)

        predictionViewModel.updateSyncListStatus(predictionIds)

        val workdata = workDataOf(
            WORKER_IMAGE_NAMES_KEY to fileNames.toTypedArray(),
            WORKER_IMAGE_URIS_KEY to imageUris.toTypedArray(),
            WORKER_PREDICTION_IDS_KEY to predictionIds.toTypedArray()
        )

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val uploadRequest = OneTimeWorkRequestBuilder<UploadWorker>()
            .setInputData(workdata)
            .addTag("SINGLE UPLOAD TAG " + item.id)
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                3,
                TimeUnit.MINUTES
            )
            .build()

        workManager.beginWith(uploadRequest).enqueue()
        hasBeenScheduledForUpload.value = true
        scope.launch {
            scaffoldState.snackbarHostState.showSnackbar(imageQueuedMsg)
        }
    }

    val proceedWithSync: (item: Prediction) -> Unit = {
        upload(it)
        showUploadDialog.value = false
    }

    val onClickUpload: () -> Unit = {
        if (user == null) {
            scope.launch {
                when (scaffoldState.snackbarHostState.showSnackbar(
                    accountNeededMsg,
                   loginMsg,
                )) {
                    SnackbarResult.Dismissed -> {

                    }
                    SnackbarResult.ActionPerformed -> {
                        initLogin()
                    }
                }
            }
        } else {
            showUploadDialog.value = true
        }
    }

    Scaffold(
        scaffoldState = scaffoldState,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text =  stringResource(id = R.string.saved_prediction_title) ,
                        color = Color.White,
                        fontSize = 18.sp,
                    )
                },
                backgroundColor = MaterialTheme.colors.primary,
                contentColor = Color.White,
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
            )
        }
    ) {
        if (prediction.isNotEmpty()) {
            val item = prediction[0]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colors.background)
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
                            bitmap = item.inputImage.asImageBitmap(),
                            contentDescription = "Input Image",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = 32.dp, end = 32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Box(
                        modifier = Modifier
                            .weight(1f),
                    ) {
                        Image(
                            bitmap = item.mask.asImageBitmap(),
                            contentDescription = "Mask",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = 32.dp, end = 32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
                CategoryIndicators(
                    classPercentages = listOf(
                        item.overripe.toDouble(),
                        item.ripe.toDouble(),
                        item.underripe.toDouble()
                    )
                )
//                Row(
//                    verticalAlignment = Alignment.CenterVertically,
//                    horizontalArrangement = Arrangement.SpaceAround,
//                    modifier = Modifier
//                        .height(48.dp)
//                        .fillMaxWidth()
//                ){
//                    Text(
//                        text = "${stringResource(id = R.string.underripe)}: ${item.underripe.toInt()}%",
//                        color = MaterialTheme.colors.onSurface,
////                        modifier = Modifier.fillMaxWidth(),
//                        textAlign = TextAlign.Center,
//                        fontSize = 18.sp
//                    )
//                    Text(
//                        text = "${stringResource(id = R.string.ripe)}: ${item.ripe.toInt()}%",
//                        color = MaterialTheme.colors.onSurface,
////                        modifier = Modifier.fillMaxWidth(),
//                        textAlign = TextAlign.Center,
//                        fontSize = 18.sp
//                    )
//                    Text(
//                        text = "${stringResource(id = R.string.overripe)}: ${item.overripe.toInt()}%",
//                        color = MaterialTheme.colors.onSurface,
////                        modifier = Modifier.fillMaxWidth(),
//                        textAlign = TextAlign.Center,
//                        fontSize = 18.sp
//                    )
//
//                }

                Spacer(modifier = Modifier.height(16.dp))

                RipenessRecommendation(classPercentages = listOf(
                    item.overripe.toDouble(),
                    item.ripe.toDouble(),
                    item.underripe.toDouble()
                ), white=false)

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .padding(bottom = 48.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (!item.scheduledForSync) {
                        ButtonSecondary(
                            onClick = {
                                onClickUpload()
                            },
                            label = stringResource(id = R.string.upload),
                            enabled = !hasBeenScheduledForUpload.value
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp, 4.dp, 12.dp, 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.CloudUpload,
                                    "",
                                    tint = MaterialTheme.colors.primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Upload",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colors.primary
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    ButtonPrimary(onClick = { showDeleteDialog.value = true }, label = "Delete") {
                        Row(
                            modifier = Modifier.padding(12.dp, 4.dp, 12.dp, 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.Delete, "", tint = Color.White)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(id = R.string.delete),
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
                if (showUploadDialog.value) UploadDialogPresenter(
                    showUploadDialog,
                    item,
                    proceedWithSync
                )
                if (showDeleteDialog.value) DeleteDialogPresenter(
                    showDeleteDialog,
                    item,
                    predictionViewModel
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
//                LinearProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Loading...",
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun UploadDialogPresenter(
    showDialog: MutableState<Boolean>,
    item: Prediction,
    onProceedFn: (item: Prediction) -> Unit
) {
    if (showDialog.value) {
        AlertDialog(
            modifier = Modifier.padding(horizontal = 32.dp),
            onDismissRequest = { showDialog.value = false },
            title = { Text(text = stringResource(id = R.string.upload_prediction)) },
            text = {
                Column {
                    Text(stringResource(id = R.string.are_you_sure))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog.value = false }) {
                    Text(text = stringResource(id = R.string.no))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onProceedFn(item)
                }) {
                    Text(text = stringResource(id = R.string.yes))
                }
            },
        )
    }
}

@Composable
fun DeleteDialogPresenter(
    showDialog: MutableState<Boolean>,
    prediction: Prediction,
    predictionViewModel: PredictionViewModel
) {
    val context = LocalContext.current as Activity
    if (showDialog.value) {
        AlertDialog(
            modifier = Modifier.padding(horizontal = 32.dp),
            onDismissRequest = { showDialog.value = false },
            title = { Text(text = stringResource(id = R.string.delete_prediction)) },
            text = {
                Column {
                    Text(stringResource(id = R.string.are_you_sure))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog.value = false }) {
                    Text(text = stringResource(id = R.string.no))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val returnIntent = Intent()
                    returnIntent.putExtra("ID", prediction.id)
                    predictionViewModel.deletePrediction(prediction)
                    showDialog.value = false
                    context.setResult(DELETED, returnIntent)
                    context.finish()
                }) { Text(text = stringResource(id = R.string.yes)) }
            },
        )
    }
}
