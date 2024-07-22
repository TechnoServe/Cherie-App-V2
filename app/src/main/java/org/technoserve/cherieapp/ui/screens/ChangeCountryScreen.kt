package org.technoserve.cherieapp.ui.screens

import CustomSpinner
import android.content.SharedPreferences
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.CoroutineScope
import org.technoserve.cherieapp.ui.navigation.NavigationItem
import org.technoserve.cherieapp.workers.TAG


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChangeCountryScreen(
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

    val selected = remember {
        mutableStateOf(selected_country)
    }



    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Change Country",
                style = MaterialTheme.typography.h4,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 100.dp)
                    .fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(100.dp))
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
                    saveCountry(
                        sharedPreferences,
                        selected.value
                    );
                    navController.navigate(NavigationItem.Profile.route)
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
                    text = "Save",
                    modifier = Modifier.padding(12.dp, 4.dp, 12.dp, 4.dp),
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
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