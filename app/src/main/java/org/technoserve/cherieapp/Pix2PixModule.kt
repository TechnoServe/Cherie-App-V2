package org.technoserve.cherieapp

import android.content.Context
import android.util.Log
import org.pytorch.IValue
import org.pytorch.Module
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*
import kotlin.system.measureTimeMillis


@Throws(IOException::class)
fun assetFilePath(context: Context, assetName: String): String? {
    val file = File(context.filesDir, assetName)
    if (file.exists() && file.length() > 0) {
        return file.absolutePath
    }
    context.assets.open(assetName).use { `is` ->
        FileOutputStream(file).use { os ->
            val buffer = ByteArray(4 * 1024)
            var read: Int
            while (`is`.read(buffer).also { read = it } != -1) {
                os.write(buffer, 0, read)
            }
            os.flush()
        }
        return file.absolutePath
    }
}


object Pix2PixModule {
    var mModule: Module? = null

    fun loadModel(context: Context,country:String) {
        val time = measureTimeMillis {
            if(mModule == null){
                try {
                    mModule = Module.load(country)
                } catch (e: IOException) {
                    Log.e("Cherie", "Error reading assets", e)
                }
            }
        }
        Log.d("MODEL LOADED", time.toString())
    }

}