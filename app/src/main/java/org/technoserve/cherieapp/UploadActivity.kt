package org.technoserve.cherieapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.technoserve.cherieapp.ui.screens.UploadScreen
import org.technoserve.cherieapp.ui.theme.CherieTheme

class UploadActivity : ComponentActivity() {
    private val imageAsByteArray by lazy {
        intent.getByteArrayExtra(IMAGE) as ByteArray
    }

    private val country by lazy {
        intent.getStringExtra("country") as String
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        setContent {
            CherieTheme {
                UploadScreen(imageAsByteArray=imageAsByteArray)
            }
        }
    }

    companion object {
        const val IMAGE = "image"

        fun newIntent(context: Context, imageAsByteArray: ByteArray) =
            Intent(context, UploadActivity::class.java).apply {
                putExtra(IMAGE, imageAsByteArray)
            }
    }
}