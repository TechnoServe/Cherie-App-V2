package org.technoserve.cherieapp.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import org.technoserve.cherieapp.R
import java.io.File
import java.io.IOException
import android.graphics.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageView
import com.canhub.cropper.options
import com.google.android.gms.location.LocationServices
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.technoserve.cherieapp.PredictionActivity
import org.technoserve.cherieapp.UploadActivity
import org.technoserve.cherieapp.hasLocationPermission
import org.technoserve.cherieapp.ui.navigation.NavigationItem
import java.io.ByteArrayOutputStream
import androidx.compose.runtime.remember as remember1


@SuppressLint("MissingPermission")
@Composable
fun ChooseImageScreen(
    navController: NavController,
    scaffoldState: ScaffoldState,
    homeScope: CoroutineScope
) {
    val imageUri = remember1 { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current
    val title = stringResource(id = R.string.resize_image)
    val errorMsg = stringResource(id = R.string.something_went_wrong)
    val savedMsg = stringResource(id =  R.string.saved_successfully)
    val sharedPreferences = remember { getSharedPreferences(context) }
    val selected_country = readCountry(sharedPreferences)
    val bitmap = remember1 { mutableStateOf<Bitmap?>(null) }
    val currentPhotoPath = remember1 { mutableStateOf("") }
    val dialogIsVisible = remember1 { mutableStateOf(false) }
    val showHelpDialog = remember1 { mutableStateOf(false) }
    val location = remember { mutableStateOf("Location(0.0, 0.0)") }


    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    LaunchedEffect(Unit) {

        if (context.hasLocationPermission()) {
            fusedLocationClient.lastLocation.addOnSuccessListener { locationResult ->
                locationResult?.let { lastLocation ->
                    location.value = "${lastLocation.latitude}, ${lastLocation.longitude}, ${lastLocation.toString()}"
                    Log.d("mytest",location.value)
                }
            }
        }
    }
    @Throws(IOException::class)
    fun createImageFile(): File {
        // Create an image file name
        // val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File? = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_TEMP_", /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
        ).apply {
            // Save a file: path for use with ACTION_VIEW intents
            currentPhotoPath.value = absolutePath
        }
    }

    val cropImage = rememberLauncherForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            val uriContent: Uri? = result.uriContent
            bitmap.value = BitmapFactory.decodeStream(imageUri.value?.let {
                uriContent?.let { it1 -> context.contentResolver?.openInputStream(it1) }
            })
            dialogIsVisible.value = true
        } else {
            val exception = result.error
            homeScope.launch {
                scaffoldState.snackbarHostState.showSnackbar(errorMsg)
            }
            Log.d("CHERIE@CROP", "Error : ${exception?.localizedMessage}")
        }
    }

    fun startCrop() {
        cropImage.launch(
            options(uri = imageUri.value) {
                setGuidelines(CropImageView.Guidelines.ON)
                setFixAspectRatio(true)
                setAspectRatio(1, 1)
                setInitialCropWindowPaddingRatio(0f)
                setActivityTitle(title)
                setRequestedSize(512, 512)
                setMinCropResultSize(512, 512)
                setOutputCompressQuality(80)
                setOutputCompressFormat(Bitmap.CompressFormat.JPEG)
                setCropMenuCropButtonIcon(R.drawable.done)
            }
        )
    }

    val selectImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            imageUri.value = uri
            startCrop()
        }
    }

    val loadFromGallery: () -> Unit = {
        selectImageLauncher.launch("image/*")
    }

    val takePicture =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                try {
                    startCrop()
                } catch (error: Exception) {
                    Log.d("CHERIE@CAMERA", "Error : ${error.localizedMessage}")
                }
            }
        }

    val launchCamera: () -> Unit = {
        val photoURI: Uri? = context.let {
            createImageFile().let { it1 ->
                FileProvider.getUriForFile(
                    it,
                    context.applicationContext.packageName.toString() + ".provider",
                    it1
                )
            }
        }
        photoURI?.let {
            imageUri.value = it

            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.putExtra("android.intent.extra.quickCapture", true)
            intent.putExtra(MediaStore.EXTRA_OUTPUT, it)
            takePicture.launch(intent)
        }
    }

    val dismissDialog: () -> Unit = {
        val backToCrop = false
        if (backToCrop) {
            // Alternate flow - Take user back to Crop Modal
            startCrop()
            dialogIsVisible.value = false
        } else {
            // Wipe state
            imageUri.value = null
            bitmap.value = null
            currentPhotoPath.value = ""

            dialogIsVisible.value = false
        }
    }

    val runUpload =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                homeScope.launch {
                    scaffoldState.snackbarHostState.showSnackbar(savedMsg)
                }
                navController.navigate(NavigationItem.Logs.route) {
                    navController.graph.startDestinationRoute?.let { route ->
                        popUpTo(route) {
                            saveState = true
                        }
                    }
                    launchSingleTop = true
                    restoreState = true
                }
                Log.d("TAG", "Got result OK - Saving Prediction")
            } else {
                Log.d("TAG", "Back button was pressed - Save Cancelled")
            }
        }

    val proceedToPredictionScreen: (country:String) -> Unit = {country->
        Log.i("SELECTED COUNTRY",country)
        val stream = ByteArrayOutputStream()
        bitmap.value?.compress(Bitmap.CompressFormat.JPEG, 100, stream)
        val imgAsByteArray: ByteArray = stream.toByteArray()

        val intent = UploadActivity.newIntent(context, imgAsByteArray)
        intent.putExtra("country",country)
        runUpload.launch(intent)

        dismissDialog()
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .background(color = MaterialTheme.colors.primary)
    ) {
        Box(modifier = Modifier.weight(1f)) {
            HeaderWithIcon(showHelpDialog)
        }
        Box(modifier = Modifier.weight(1f)) {
            ChooseImageLayout(loadFromGallery, launchCamera)
        }
        bitmap.value?.let {
            FullScreenDialog(dialogIsVisible.value, it, dismissDialog, proceedToPredictionScreen)
        }
        if (showHelpDialog.value) {
            HelpDialog(showHelpDialog)
        }
    }
}

@Composable
fun ChooseImageLayout(loadFromGallery: () -> Unit, launchCamera: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .clip(RoundedCornerShape(24.dp, 24.dp, 0.dp, 0.dp))
            .background(color = MaterialTheme.colors.background),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Text(
            text = ("Upload coffee cherry images for training"),
            color = MaterialTheme.colors.onSurface,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(start = 32.dp, top = 48.dp, end = 32.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.Center
        ) {
            val haptic = LocalHapticFeedback.current
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                FloatingActionButton(
                    contentColor = MaterialTheme.colors.onSurface,
                    backgroundColor = Color.White,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        launchCamera()
                    }
                ) {
                    Icon(Icons.Outlined.PhotoCamera, "", tint = MaterialTheme.colors.primary)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(id = R.string.take_picture),
                    color = MaterialTheme.colors.onSurface
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                FloatingActionButton(
                    contentColor = MaterialTheme.colors.onSurface,
                    backgroundColor = Color.White,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        loadFromGallery()
                    }
                ) {
                    Icon(Icons.Outlined.Image, "", tint = Color.Blue)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = stringResource(id = R.string.load_from_galery), color = MaterialTheme.colors.onSurface)
            }

        }
    }
}



private fun getSharedPreferences(context: Context): SharedPreferences {
    return context.getSharedPreferences("country_preference", Context.MODE_PRIVATE)
}



private fun readCountry(sharedPreferences: SharedPreferences): String {
    return sharedPreferences.getString("country", "") ?: ""
}