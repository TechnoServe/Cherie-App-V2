package org.technoserve.cherieapp.ui.screens

import android.app.Activity
import android.app.Activity.RESULT_OK
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.graphics.BlurMaskFilter
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract
import com.firebase.ui.auth.data.model.FirebaseAuthUIAuthenticationResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter
import org.technoserve.cherieapp.Preferences
import org.technoserve.cherieapp.R
import org.technoserve.cherieapp.database.Prediction
import org.technoserve.cherieapp.database.PredictionViewModel
import org.technoserve.cherieapp.database.PredictionViewModelFactory
import org.technoserve.cherieapp.ui.components.ButtonPrimary
import org.technoserve.cherieapp.ui.navigation.NavigationItem

@Composable
fun ProfileScreen(
    scaffoldState: ScaffoldState,
    homeScope: CoroutineScope,
    navController: NavController
) {

    val context = LocalContext.current as Activity
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val predictionViewModel: PredictionViewModel = viewModel(
        factory = PredictionViewModelFactory(context.applicationContext as Application)
    )

    val listItems = predictionViewModel.readAllData.observeAsState(listOf()).value
    val sharedPreferences = remember { getSharedPreferences(context) }
    val selected_country = readCountry(sharedPreferences)
    var user by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser) }
    val loginSuccessMsg = stringResource(id = R.string.login_successfull)
    val loginFailedMsg = stringResource(id = R.string.login_failed)
    val sharedPrefs by remember { mutableStateOf(Preferences(context)) }

    // Choose authentication providers
    val providers = arrayListOf(
        AuthUI.IdpConfig.PhoneBuilder().build(),
        AuthUI.IdpConfig.GoogleBuilder().build(),
    )

    fun onSignInResult(result: FirebaseAuthUIAuthenticationResult) {
        val response = result.idpResponse
        if (result.resultCode == RESULT_OK) {
            // Successfully signed in
            val newUser = FirebaseAuth.getInstance().currentUser
            if (newUser != null) {
                user = newUser
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
            Log.d("Firebase log",result.toString())
        }
    }

    // See: https://developer.android.com/training/basics/intents/result
    val signInLauncher =
        rememberLauncherForActivityResult(FirebaseAuthUIActivityResultContract()) { res ->
            onSignInResult(res)
        }
    val isDarkMode = isSystemInDarkTheme()
    fun startAuthFlow() {
        val signInIntent = AuthUI.getInstance()
            .createSignInIntentBuilder()
            .setAvailableProviders(providers)
            .setLogo(R.drawable.cherie)
            .setTheme(R.style.LoginTheme)
            .build()
        signInLauncher.launch(signInIntent)
    }

    fun logout() {
        AuthUI.getInstance()
            .signOut(context)
            .addOnCompleteListener {
                val message = if (it.isSuccessful) loginSuccessMsg else loginFailedMsg
                user = null
                homeScope.launch {
                    scaffoldState.snackbarHostState.showSnackbar(message)
                }
            }
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text =  stringResource(id = R.string.profile),
                        color = Color.White,
                        fontSize = 18.sp,
                    )
                },
                backgroundColor = MaterialTheme.colors.primary,
                contentColor = Color.Black,
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 32.dp)
                .background(MaterialTheme.colors.background),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {

            if (user == null) {
                ButtonPrimary(onClick = { startAuthFlow() }, label = stringResource(id = R.string.login))
                Spacer(modifier = Modifier.height(50.dp))
                Box(
                    contentAlignment = Alignment.Center,
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
                    Spacer(modifier = Modifier.height(18.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "${stringResource(id = R.string.country)}: ${selected_country}" ,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.W600,
                            color = Color.Black
                        )

                    }
                    Spacer(modifier = Modifier.height(18.dp))
                }
            }

            user?.let { UserInfo(it,navController) }

            Stats(listItems, sharedPrefs,navController)

            if (user != null) {
                Spacer(modifier = Modifier.weight(1f))
                ButtonPrimary(onClick = { logout() }, label = stringResource(id = R.string.log_out))
                Spacer(modifier = Modifier.height(64.dp))
            }

        }
    }
}

@Preview
@Composable
fun ProfileScreenPreview() {
    val scaffoldState = rememberScaffoldState()
    val navController = rememberNavController()
    ProfileScreen(homeScope = GlobalScope, scaffoldState = scaffoldState, navController = navController)
}

@Composable
fun UserInfo(user: FirebaseUser, navController: NavController) {
    val context = LocalContext.current
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val isDarkMode = isSystemInDarkTheme()
    val sharedPreferences = remember { getSharedPreferences(context) }
    val selected_country = readCountry(sharedPreferences)
    val fmt: DateTimeFormatter = DateTimeFormat.forPattern("yyyy/MM/dd HH:mm:ss")
    Box(
        contentAlignment = Alignment.Center,
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
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.Start
        ) {
            if (user.email != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "${stringResource(id = R.string.email)}: " + user.email, fontWeight = FontWeight.W600,color = Color.Black)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "${stringResource(id = R.string.name)}: " + user.displayName, fontWeight = FontWeight.W600,color = Color.Black)
            }
            if (user.phoneNumber != null && user.phoneNumber != "") {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "${stringResource(id = R.string.phone)}: " + user.phoneNumber, fontWeight = FontWeight.W600,color = Color.Black)
            }
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "${stringResource(id = R.string.country)}: ${selected_country}" ,
                fontSize = 12.sp,
                fontWeight = FontWeight.W600,
                color = Color.Black
            )



            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${stringResource(id = R.string.last_sign_in)}: " + DateTime(user.metadata?.lastSignInTimestamp).toString(fmt),
                fontSize = 12.sp,
                fontWeight = FontWeight.W600,
                color = Color.Black
            )
            Spacer(modifier = Modifier.height(18.dp))
        }
    }
}

@Composable
fun Stats(listItems: List<Prediction>, sharedPrefs: Preferences, navController: NavController) {
    val context = LocalContext.current
    val sharedPreferences = remember { getSharedPreferences(context) }
    val selected_country = readCountry(sharedPreferences)
    var averageRipe = 0f
    val isDarkMode = isSystemInDarkTheme()
    var averageUnderripe = 0f
    var averageOverripe = 0f
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    if(listItems.isNotEmpty()){
        for(item in listItems){
            averageRipe += item.ripe
            averageUnderripe += item.underripe
            averageOverripe += item.overripe
        }
        averageRipe /= listItems.size
        averageUnderripe /= listItems.size
        averageOverripe /= listItems.size
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Spacer(modifier = Modifier.height(18.dp))
        Text(text = stringResource(id = R.string.average_ripeness_stats), color = MaterialTheme.colors.primary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(32.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            Column(
                modifier= Modifier
                    .width(screenWidth * 0.25f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Box(
                    contentAlignment = Alignment.Center,
                    modifier= Modifier
                        .width(screenWidth * 0.25f)
                        .height(80.dp)
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
                ){
                    Stat(title = stringResource(id = R.string.ripe), value = "${String.format("%.0f", averageRipe)}%", color= MaterialTheme.colors.primary)
                }
                Spacer(modifier = Modifier.height(18.dp))
                Text(text = stringResource(id = R.string.ripe), fontWeight = FontWeight.W600)
            }

            StatDivider()

            Column(
                modifier= Modifier
                    .width(screenWidth * 0.25f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .width(screenWidth * 0.25f)
                        .height(80.dp)
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
                    Stat(title = stringResource(id = R.string.underripe), value = "${String.format("%.0f", averageUnderripe)}%",color = MaterialTheme.colors.secondary)
                }
                Spacer(modifier = Modifier.height(18.dp))
                Text(text = stringResource(id = R.string.underripe), fontWeight = FontWeight.W600)
            }
            StatDivider()
            Column(
                modifier= Modifier
                    .width(screenWidth * 0.25f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .width(screenWidth * 0.25f)
                        .height(80.dp)
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
                    Stat(title = stringResource(id = R.string.overripe), value = "${String.format("%.0f", averageOverripe)}%",color = Color(0xFF3700B3))
                }
                Spacer(modifier = Modifier.height(18.dp))
                Text(text = stringResource(id = R.string.overripe), fontWeight = FontWeight.W600)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(text = stringResource(id = R.string.usage_stats), color = MaterialTheme.colors.primary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(32.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            Column(
                modifier= Modifier
                    .width(screenWidth * 0.25f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .width(screenWidth * 0.25f)
                        .height(80.dp)
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
                    Stat(
                        title = "${if (sharedPrefs.generatedPredictions == 1) "${stringResource(id = R.string.generated_prediction)}" else "${stringResource(id = R.string.generated_predictions)}"}",
                        value = sharedPrefs.generatedPredictions.toString(),
                        color = Color.Black
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
                Text(text = "${stringResource(id = R.string.generated_prediction)}${if (sharedPrefs.generatedPredictions == 1) "" else "s"}", fontWeight = FontWeight.W600)
            }
            StatDivider()
            Column(
                modifier= Modifier
                    .width(screenWidth * 0.25f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .width(screenWidth * 0.25f)
                        .height(80.dp)
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
                    Stat(
                        title = "${stringResource(id = R.string.saved_prediction)}${if (listItems.size == 1) "" else "s"}",
                        value = listItems.size.toString(),
                        color = Color.Black
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
                Text(text = "${if (listItems.size == 1) "${stringResource(id = R.string.saved_prediction)}" else "${stringResource(id = R.string.saved_predictions)}"}", fontWeight = FontWeight.W600)
            }
            StatDivider()
            Column(
                modifier= Modifier
                    .width(screenWidth * 0.25f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .width(screenWidth * 0.25f)
                        .height(80.dp)
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
                    Stat(
                        title = "${stringResource(id = R.string.upload)}${if (sharedPrefs.uploadedPredictions == 1) "" else "s"}\n",
                        value = sharedPrefs.uploadedPredictions.toString(),
                        color = Color.Black
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
                Text(text = "${if (sharedPrefs.uploadedPredictions == 1) "${stringResource(id = R.string.upload)}"  else "${stringResource(id = R.string.uploads)}"}\n", fontWeight = FontWeight.W600)
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
//        Text(text = "Country", fontSize = 20.sp, fontWeight = FontWeight.Bold)
//        Row(
//            modifier = Modifier.fillMaxWidth(),
//            horizontalArrangement = Arrangement.Start,
//            verticalAlignment = Alignment.CenterVertically
//        ) {
//            Box(modifier=Modifier.weight(1f)){
//                Column {
//                    Text(
//                        modifier = Modifier.padding(top = 16.dp),
//                        text = selected_country,
//                        style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold)
//                    )
//                    ClickableText(
//                        text = buildAnnotatedString {
//                            withStyle(style = SpanStyle(color = Color.Blue,textDecoration = TextDecoration.Underline)) {
//                                append("change")
//                            }
//                        },
//                        onClick = {
//                            navController.navigate(NavigationItem.ChangeCountry.route)
//                        }
//                    )
//
//                }
//            }
//
//        }
    }
}

@Composable
fun Stat(title: String, value: String, color: Color = MaterialTheme.colors.onSurface) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text = value, fontSize = 36.sp, fontWeight = FontWeight.Black, color = color)
    }
}

@Composable
fun StatDivider() {
    Spacer(modifier = Modifier.width(16.dp))

}

fun Modifier.shadow(
    color: Color = Color.Black,
    borderRadius: Dp = 0.dp,
    blurRadius: Dp = 0.dp,
    offsetY: Dp = 0.dp,
    offsetX: Dp = 0.dp,
    spread: Dp = 0f.dp,
    modifier: Modifier = Modifier
) = this.then(
    modifier.drawBehind {
        this.drawIntoCanvas {
            val paint = Paint()
            val frameworkPaint = paint.asFrameworkPaint()
            val spreadPixel = spread.toPx()
            val leftPixel = (0f - spreadPixel) + offsetX.toPx()
            val topPixel = (0f - spreadPixel) + offsetY.toPx()
            val rightPixel = (this.size.width + spreadPixel)
            val bottomPixel = (this.size.height + spreadPixel)

            if (blurRadius != 0.dp) {
                frameworkPaint.maskFilter =
                    (BlurMaskFilter(blurRadius.toPx(), BlurMaskFilter.Blur.NORMAL))
            }

            frameworkPaint.color = color.toArgb()
            it.drawRoundRect(
                left = leftPixel,
                top = topPixel,
                right = rightPixel,
                bottom = bottomPixel,
                radiusX = borderRadius.toPx(),
                radiusY = borderRadius.toPx(),
                paint
            )
        }
    }
)

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