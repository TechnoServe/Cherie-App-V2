package org.technoserve.cherieapp.ui.screens

import android.app.Activity
import android.app.Application
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.work.*
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract
import com.firebase.ui.auth.data.model.FirebaseAuthUIAuthenticationResult
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.technoserve.cherieapp.R
import org.technoserve.cherieapp.SavedPredictionActivity
import org.technoserve.cherieapp.database.Prediction
import org.technoserve.cherieapp.database.PredictionViewModel
import org.technoserve.cherieapp.database.PredictionViewModelFactory
import org.technoserve.cherieapp.helpers.ImageUtils
import org.technoserve.cherieapp.ui.navigation.NavigationItem
import org.technoserve.cherieapp.workers.UploadWorker
import org.technoserve.cherieapp.workers.WORKER_IMAGE_NAMES_KEY
import org.technoserve.cherieapp.workers.WORKER_IMAGE_URIS_KEY
import org.technoserve.cherieapp.workers.WORKER_PREDICTION_IDS_KEY
import java.util.*
import kotlin.concurrent.schedule
import androidx.compose.material.*

const val DELETED = 204

@ExperimentalMaterialApi
@ExperimentalFoundationApi
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun SavedPredictionsScreen(
    scaffoldState: ScaffoldState,
    homeScope: CoroutineScope,
    navController: NavController
) {

    val context = LocalContext.current
    val predictionViewModel: PredictionViewModel = viewModel(
        factory = PredictionViewModelFactory(context.applicationContext as Application)
    )
    val predDeletedMsg = stringResource(id = R.string.prediction_deleted)
    val predDeletedLab = stringResource(id = R.string.undo)
    val itemsDeletedMsg = stringResource(id = R.string.items_deleted)
    val queuedMsg = stringResource(id = R.string.image_has_been_queued_for_upload)
    val undoLab = stringResource(id = R.string.undo)
    val loginSuccessMsg = stringResource(id = R.string.login_successfull)
    val loginFailedMsg = stringResource(id = R.string.login_failed)
    val workManager: WorkManager = WorkManager.getInstance(context)
    // TODO: Remove listOf initial value so livedata starts out as null
    val listItems by predictionViewModel.readAllData.observeAsState(listOf())

    fun refreshListItems() {
        // TODO: update saved predictions list when db gets updated
        //  currently using a terrible makeshift solution
        navController.navigate(NavigationItem.Inference.route)
        navController.navigate(NavigationItem.Logs.route) {
            navController.graph.startDestinationRoute?.let { route ->
                popUpTo(route) {
                    saveState = true
                }
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val showDeleteDialog = remember { mutableStateOf(false) }
    val showSyncDialog = remember { mutableStateOf(false) }
    val showLoginDialog = remember { mutableStateOf(false) }

    val loading = remember { mutableStateOf(true) }
    val previewedPredictions: MutableList<Prediction> = mutableListOf()

    Timer().schedule(1200) {
        loading.value = false
    }

    val showDetails =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == DELETED) {
                val id = result.data?.getLongExtra("ID", 0L)
                homeScope.launch {
                    when (scaffoldState.snackbarHostState.showSnackbar(
                        predDeletedMsg,
                        predDeletedLab
                    )) {
                        SnackbarResult.Dismissed -> {
                            previewedPredictions.removeAll { it.id == id }
                        }
                        SnackbarResult.ActionPerformed -> {
                            // Restore Prediction
                            val previewedPrediction = previewedPredictions.find { it.id == id }
                            if (previewedPrediction != null) {
                                predictionViewModel.addPrediction(previewedPrediction)
                            }
                        }
                    }
                }
            } else {
                refreshListItems()
            }
        }


    val selectedIds = remember { mutableStateListOf<Long>() }

    fun selectOrDeselectAll() {
        if (selectedIds.isEmpty()) {
            val ids = listItems.map { it.id }
            selectedIds.addAll(ids)
        } else {
            selectedIds.removeAll(selectedIds)
        }
    }

    val proceedToPredictionScreen: (id: Long) -> Unit = {
        val previewedPrediction = listItems.find { it2 -> it2.id == it }
        if (previewedPrediction != null) {
            previewedPredictions.add(previewedPrediction)
        }
        val intent = SavedPredictionActivity.newIntent(context, it)
        showDetails.launch(intent)
    }

    val checkboxAction: (id: Long) -> Unit = {
        if (selectedIds.contains(it)) {
            selectedIds.remove(it)
        } else {
            selectedIds.add(it)
        }
    }

    val clickRowItem: (id: Long) -> Unit = {
        // No item selected
        if (selectedIds.isEmpty()) {
            proceedToPredictionScreen(it)
        } else {
            checkboxAction(it)
        }
    }

    val proceedWithDelete: () -> Unit = {
        val toDelete = mutableListOf<Long>()
        toDelete.addAll(selectedIds)
        previewedPredictions.removeAll(previewedPredictions)
        previewedPredictions.addAll(listItems.filter { toDelete.contains(it.id) })
        predictionViewModel.deleteList(toDelete)
        selectedIds.removeAll(selectedIds)
        showDeleteDialog.value = false

        homeScope.launch {
            when (scaffoldState.snackbarHostState.showSnackbar(
                "${previewedPredictions.size} ${itemsDeletedMsg}",
                undoLab
            )) {
                SnackbarResult.Dismissed -> {
                    previewedPredictions.removeAll(previewedPredictions)
                }
                SnackbarResult.ActionPerformed -> {
                    // Restore Predictions
                    previewedPredictions.forEach {
                        predictionViewModel.addPrediction(it)
                    }

                }
            }
        }
    }

    val proceedWithSync: () -> Unit = {
        val toSync = mutableListOf<Long>()
        toSync.addAll(selectedIds)
        selectedIds.removeAll(selectedIds)
        showSyncDialog.value = false

        predictionViewModel.updateSyncListStatus(toSync)

        val userId = FirebaseAuth.getInstance().currentUser?.uid
        val predictionsToSync = listItems.filter { toSync.contains(it.id) && !it.scheduledForSync }
        if(predictionsToSync.isNotEmpty()) {
            GlobalScope.launch {
                val fileNames = mutableListOf<String>()
                val imageUris = mutableListOf<String>()
                val predictionIds = mutableListOf<Long>()

                predictionsToSync.forEach {
                    val fileName = (userId) + "_" + it.id
                    val combinedBitmaps = ImageUtils.combineBitmaps(it.inputImage, it.mask)
                    val imageUri = ImageUtils.createTempBitmapUri(context, combinedBitmaps, fileName)
                    fileNames.add(fileName)
                    imageUris.add(imageUri.toString())
                    predictionIds.add(it.id)
                }

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
                    .addTag("MASS UPLOAD TAG")
                    .setConstraints(constraints)
                    .build()

                workManager.beginWith(uploadRequest).enqueue()
                Log.d("TAG", "Syncing ${predictionsToSync.size} items")
            }
        }
        homeScope.launch {
            scaffoldState.snackbarHostState.showSnackbar("${predictionsToSync.size} ${queuedMsg}")
        }
        refreshListItems()
    }

    fun onSignInResult(result: FirebaseAuthUIAuthenticationResult) {
        if (result.resultCode == Activity.RESULT_OK) {
            val newUser = FirebaseAuth.getInstance().currentUser
            if (newUser != null) {
                homeScope.launch {
                    scaffoldState.snackbarHostState.showSnackbar(loginSuccessMsg)
                }
            }
            // ...
        } else {
            // Sign in failed. If response is null the user canceled the
            // sign-in flow using the back button. Otherwise check
            // response.getError().getErrorCode() and handle the error.
            // ...
            homeScope.launch {
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
        showLoginDialog.value = false
    }

    fun onSyncClicked() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            showSyncDialog.value = true
        } else {
            showLoginDialog.value = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.saved_predictions_title),
                        color = Color.White,
                        fontSize = 18.sp,
                    )
                },
                backgroundColor = MaterialTheme.colors.primary,
                contentColor = Color.Black,
                actions = {
                    if (listItems.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                if (selectedIds.isNotEmpty()) {
                                    onSyncClicked()
                                }
                            },
                            enabled = (selectedIds.size > 0)
                        ) {
                            val toBeSynced = listItems.count { it.scheduledForSync && !it.synced }
                            if (selectedIds.size == 0 && toBeSynced > 0) {
                                BadgedBox(
                                    badge = {
                                        Text(
                                            toBeSynced.toString(),
                                            color = Color.Green
                                        )
                                    },
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.CloudUpload,
                                        contentDescription = null,
                                        tint = if (selectedIds.size > 0) Color.White else Color(
                                            0xAAFFFFFF
                                        )
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.CloudUpload,
                                    contentDescription = null,
                                    tint = if (selectedIds.size > 0) Color.White else Color(
                                        0xAAFFFFFF
                                    )
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                if (selectedIds.isNotEmpty()) showDeleteDialog.value = true
                            },
                            enabled = (selectedIds.size > 0)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = null,
                                tint = if (selectedIds.size > 0) Color.White else Color(0xAAFFFFFF)
                            )
                        }
                    }
                }
            )
        }
    ) {
        if (loading.value) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            )
            {
                LinearProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "${stringResource(id = R.string.loading_saved_predictions)}...",
                    textAlign = TextAlign.Center
                )
            }
        } else {
            if (listItems.isEmpty()) {
                NoSavedPredictions()
            } else {
                Box(modifier = Modifier.fillMaxWidth()) {

                    val listState = rememberLazyListState()
                    val scope = rememberCoroutineScope()

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colors.background),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp)
                    ) {

                        stickyHeader {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(end = 16.dp)
                                    .background(MaterialTheme.colors.background),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    modifier = Modifier.clickable(onClick = { selectOrDeselectAll() }),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceAround
                                ) {
                                    Row(
                                        modifier = Modifier.padding(
                                            PaddingValues(all = 16.dp)
                                        )
                                    ) {
                                        Checkbox(
                                            checked = listItems.size == selectedIds.size,
                                            onCheckedChange = { selectOrDeselectAll() }
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Text(text = stringResource(id = R.string.select_all))
                                    }
                                }
                                Row {
                                    if (selectedIds.size == 1) {
                                        Text(text = "${selectedIds.size} item selected")
                                    }
                                    if (selectedIds.size > 1) {
                                        Text(text = "${selectedIds.size} items selected")
                                    }
                                }
                            }
                        }

                        itemsIndexed(items = listItems) { index, item ->
                            PredictionCard(
                                prediction = item,
                                clickRowItem,
                                checkboxAction,
                                selectedIds.contains(item.id)
                            )
                            if (index < listItems.size - 1)
                                Divider(
                                    color = Color.LightGray,
                                    thickness = 1.dp,
                                    modifier = Modifier.padding(PaddingValues(horizontal = 52.dp))
                                )
                        }
                    }

                    val showButton = listState.firstVisibleItemIndex > 5

                    AnimatedVisibility(
                        visible = showButton,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp)
                    ) {
                        ScrollToTopButton(scrollToTop = {
                            scope.launch {
                                listState.scrollToItem(0)
                            }
                        })
                    }
                }
            }
        }


        if (showDeleteDialog.value) {
            DeleteAllDialogPresenter(showDeleteDialog, onProceedFn = proceedWithDelete)
        }

        if (showSyncDialog.value) {
            SyncAllDialogPresenter(showSyncDialog, onProceedFn = proceedWithSync)
        }

        if (showLoginDialog.value) {
            LoginRequiredDialogPresenter(showLoginDialog, onProceedFn = initLogin)
        }
    }
}

@Composable
fun ScrollToTopButton(scrollToTop: () -> Unit) {
    FloatingActionButton(
        contentColor = MaterialTheme.colors.onSurface,
        backgroundColor = Color.White,
        onClick = { scrollToTop() }
    ) {
        Icon(Icons.Outlined.ArrowUpward, "", tint = MaterialTheme.colors.primary)
    }
}

@Composable
fun NoSavedPredictions() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val cherryIconResource =
            if (isSystemInDarkTheme()) R.drawable.cherry_white else R.drawable.cherry
        Image(
            painter = painterResource(id = cherryIconResource),
            contentDescription = "",
            contentScale = ContentScale.Inside,
            modifier = Modifier
                .height(240.dp)
                .padding(top = 60.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(id = R.string.no_saved_predictions),
            color = MaterialTheme.colors.onSurface,
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(60.dp))
    }
}


@Composable
fun SyncAllDialogPresenter(
    showSyncDialog: MutableState<Boolean>,
    onProceedFn: () -> Unit
) {

    if (showSyncDialog.value) {
        AlertDialog(
            modifier = Modifier.padding(horizontal = 32.dp),
            onDismissRequest = { showSyncDialog.value = false },
            title = { Text(text = stringResource(id = R.string.sync_selected_items)) },
            text = {
                Column {
                    Text(stringResource(id = R.string.are_you_sure))
                    Text(stringResource(id = R.string.this_will_upload_all_selected_items))
                }
            },

            confirmButton = {
                TextButton(onClick = { onProceedFn() }) {
                    Text(text = stringResource(id = R.string.yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSyncDialog.value = false }) {
                    Text(text = stringResource(id = R.string.no))
                }
            }
        )
    }

}

@Composable
fun DeleteAllDialogPresenter(
    showDeleteDialog: MutableState<Boolean>,
    onProceedFn: () -> Unit
) {

    if (showDeleteDialog.value) {
        AlertDialog(
            modifier = Modifier.padding(horizontal = 32.dp),
            onDismissRequest = { showDeleteDialog.value = false },
            title = { Text(text = stringResource(id = R.string.delete_selected_items)) },
            text = {
                Column {
                    Text(stringResource(id = R.string.are_you_sure))
                    Text(stringResource(id = R.string.this_will_delete_all_selected_items))
                }
            },

            confirmButton = {
                TextButton(onClick = { onProceedFn() }) {
                    Text(text = stringResource(id = R.string.yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog.value = false }) {
                    Text(text = stringResource(id = R.string.no))
                }
            }
        )
    }
}

@Composable
fun LoginRequiredDialogPresenter(
    showLoginRequiredDialog: MutableState<Boolean>,
    onProceedFn: () -> Unit
) {

    if (showLoginRequiredDialog.value) {
        AlertDialog(
            modifier = Modifier.padding(horizontal = 32.dp),
            onDismissRequest = { showLoginRequiredDialog.value = false },
            title = { Text(text = stringResource(id = R.string.login_required)) },
            text = {
                Column {
                    Text(stringResource(id = R.string.you_need_to_have_an_account_to_upload_images))
                    Text(stringResource(id = R.string.do_you_want_to_login_now))
                }
            },

            confirmButton = {
                TextButton(onClick = { onProceedFn() }) {
                    Text(text = stringResource(id = R.string.yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLoginRequiredDialog.value = false }) {
                    Text(text = stringResource(id = R.string.no))
                }
            }
        )
    }
}