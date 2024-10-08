package org.technoserve.cherieapp.ui.screens


import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.joda.time.DateTime
import org.technoserve.cherieapp.database.Prediction
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter
import org.technoserve.cherieapp.R

var fmt: DateTimeFormatter = DateTimeFormat.forPattern("yyyy/MM/dd HH:mm")

@Composable
fun PredictionCard(
    prediction: Prediction,
    proceedToPredictionScreen: (predictionId: Long) -> Unit,
    setChecked: (predictionId: Long) -> Unit,
    isChecked: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClick = { proceedToPredictionScreen(prediction.id) },
            )
    ){
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.weight(3f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isChecked,
                    onCheckedChange = { setChecked(prediction.id) },
                )
                Spacer(modifier = Modifier.width(16.dp))
                Box(modifier = Modifier.weight(1f)){
                    Image(
                        bitmap = prediction.inputImage.asImageBitmap(),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        contentDescription = null
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f)){
                    Image(
                        bitmap = prediction.mask.asImageBitmap(),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        contentDescription = null
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                modifier = Modifier
                    .weight(2f)
                    .align(Alignment.CenterVertically)
            ) {
                Text(
                    text = "${stringResource(id = R.string.ripeness)}: ${prediction.ripe.toInt()}%",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = DateTime(prediction.createdAt).toString(fmt),
                    fontSize = 12.sp
                )
//                Text(
//                    text = "${prediction.id}",
//                    fontSize = 12.sp
//                )
                if(prediction.synced){
                    Icon(
                        Icons.Outlined.CheckCircleOutline,
                        "Uploaded",
                        tint = Color.Green,
                        modifier = Modifier.size(16.dp).padding(top=2.dp)
                    )
                } else if(prediction.scheduledForSync){
                    Icon(
                        Icons.Outlined.CloudUpload,
                        "Uploaded",
                        tint = Color.Gray,
                        modifier = Modifier.size(16.dp).padding(top=2.dp)
                    )
                }
            }
        }
    }
}