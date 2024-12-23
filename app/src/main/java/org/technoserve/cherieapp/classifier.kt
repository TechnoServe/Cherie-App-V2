package org.technoserve.cherieapp

import android.content.Context
import android.graphics.*
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.Tensor
import org.pytorch.torchvision.TensorImageUtils
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import kotlin.math.*
import kotlinx.coroutines.*
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min

import android.graphics.RectF
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.toLowerCase
import androidx.core.graphics.scale

data class DetectResult(
    val boundingBox: RectF,
    val classId: Int,
    val score: Float,
)

fun nms(x: Tensor, threshold: Float): List<DetectResult> {
    // x: [0:4] - box, [4] - score, [5] - class
    val data = x.dataAsFloatArray
    val numElem = x.shape()[0].toInt()
    val innerShape = x.shape()[1].toInt()
    val selected_indices = (0 until numElem).toMutableList()

    val scores =  data.sliceArray( (0 until numElem).flatMap { r->(r*innerShape)+4 until (r*innerShape)+5 } )
    val boxes = data.sliceArray( (0 until numElem).flatMap { r->(r*innerShape) until (r*innerShape)+4 } )
    val classes = data.sliceArray( (0 until numElem).flatMap { r->(r*innerShape)+5 until (r*innerShape)+6 } )

    for (i in 0 until numElem) {
        val current_class = classes[i].toInt()
        for (j in i+1 until numElem) {
            val box_i = boxes.sliceArray(i*4 until (i*4)+4)
            val box_j = boxes.sliceArray(j*4 until (j*4)+4)
            val iou = calculate_iou(box_i, box_j)
            if (iou > threshold && classes[j].toInt() == current_class) {
                if (scores[j] > scores[i]) {
                    selected_indices.remove(i)
                    break
                } else {
                    selected_indices.remove(j)
                }
            }
        }
    }


    val result = mutableListOf<DetectResult>()
    for (i in 0 until numElem) {

        if (selected_indices.contains(i)) {
            val box = boxes.slice((i*4) until (i*4)+4)
            val detection = DetectResult(boundingBox = RectF(box[0], box[1], box[2], box[3]), score = scores[i], classId = classes[i].toInt())
            result.add(detection)
        }
    }

    return result
}

fun calculate_iou(box1: FloatArray, box2: FloatArray): Float {
    val x1 = maxOf(box1[0], box2[0])
    val y1 = maxOf(box1[1], box2[1])
    val x2 = minOf(box1[2], box2[2])
    val y2 = minOf(box1[3], box2[3])

    val intersection = maxOf(0f, x2 - x1) * maxOf(0f, y2 - y1)
    val area1 = (box1[2] - box1[0]) * (box1[3] - box1[1])
    val area2 = (box2[2] - box2[0]) * (box2[3] - box2[1])
    val union = area1 + area2 - intersection

    return intersection / union
}

data class Detection(val box: RectF, var score: Float, val scale: Float)

fun mergeCloseDetections(detections: List<Detection>, overlapThreshold: Float = 0.4f, distanceThreshold: Float = 10f): List<Detection> {
    if (detections.size <= 1) return detections

    val sortedDetections = detections.sortedByDescending { it.score }
    val mergedDetections = mutableListOf<Detection>()
    for (detection in sortedDetections) {
        var shouldAdd = true
        for (mergedDetection in mergedDetections) {
            if (areDetectionsClose(detection, mergedDetection, overlapThreshold, distanceThreshold)) {
                shouldAdd = false
                break
            }
        }
        if (shouldAdd) {
            mergedDetections.add(detection)
        }
    }

    return mergedDetections
}

private fun areDetectionsClose(a: Detection, b: Detection, overlapThreshold: Float, distanceThreshold: Float): Boolean {
    val intersection = RectF(a.box)
    if (intersection.intersect(b.box)) {
        val intersectionArea = intersection.width() * intersection.height()
        val unionArea = a.box.width() * a.box.height() + b.box.width() * b.box.height() - intersectionArea
        val iou = intersectionArea / unionArea
        return iou > overlapThreshold
    }

    val centerA = PointF(a.box.centerX(), a.box.centerY())
    val centerB = PointF(b.box.centerX(), b.box.centerY())
    val distance = distanceBetween(centerA, centerB)
    return distance < distanceThreshold
}

private fun distanceBetween(a: PointF, b: PointF): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return kotlin.math.sqrt(dx * dx + dy * dy)
}

fun Bitmap.resizeToMultipleOf128(padOrCrop: Boolean = true): Bitmap {
    val targetWidth = (width + 255) / 256 * 256
    val targetHeight = (height + 255) / 256 * 256

    val resultBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(resultBitmap)

    // Fill the background with a color (e.g., white) if padding
    if (padOrCrop) {
        canvas.drawColor(Color.WHITE)
    }

    val left = (targetWidth - width) / 2
    val top = (targetHeight - height) / 2

    if (padOrCrop) {
        // Pad: draw the original bitmap centered
        canvas.drawBitmap(this, left.toFloat(), top.toFloat(), null)
    } else {
        // Crop: draw a centered portion of the original bitmap
        val srcLeft = max(0, -left)
        val srcTop = max(0, -top)
        val srcRight = min(width, targetWidth - left)
        val srcBottom = min(height, targetHeight - top)

        canvas.drawBitmap(
            this,
            android.graphics.Rect(srcLeft, srcTop, srcRight, srcBottom),
            android.graphics.Rect(
                max(0, left),
                max(0, top),
                min(targetWidth, left + width),
                min(targetHeight, top + height)
            ),
            null
        )
    }

    return resultBitmap
}
class BeanClassifier(
    private val context: Context,
    private val country: String,
//    classifierModelPath: String,
//    detectModelPath: String
) {
//    init {
////        OpenCVLoader.initDebug()
//    }

    private val yoloModel: Module
    private val classifierModel: Module
    private val imageSizeX = 1024
    private val imageSizeY = 1024
    private val classNames = listOf("Overripe", "Ripe", "Underripe", "err")
    private val classColors = listOf(
        Color.rgb(255, 50, 50),
        Color.rgb(50, 50, 255),
        Color.rgb(50, 200, 50),
        Color.rgb(0, 0, 0)
    )

    init {
        val modelDir = File(context.filesDir, "model")
        if (!modelDir.exists()) {
            modelDir.mkdirs()
        }
//        val destDir = File(modelDir, )
        val destDirClassifier = File(modelDir, country.lowercase()+"_"+"classifier.pt")
        val destDirDetect = File(modelDir, country.lowercase()+"_"+"mobile_model_b4_nms.ptl")
        yoloModel = Module.load(destDirDetect.absolutePath)
        classifierModel = Module.load(destDirClassifier.absolutePath)
    }

    suspend fun processImage(_bitmap_: Bitmap): Triple<Bitmap, List<Double>, Int> = withContext(Dispatchers.Default) {
//
////        val yoloPreProcessed = preProcessYoloInput(_bitmap)
//        return@withContext Pair(resizedBitmap, listOf(0.0,0.0,0.0))
        var adjustedX = 0;
        var adjustedY = 0;
        var adjuste = false;
        if((_bitmap_.width > 512) || (_bitmap_.height > 1280)){
            if (_bitmap_.width > _bitmap_.height){
                val ratio=  _bitmap_.height.toFloat()/_bitmap_.width.toFloat();

                adjustedX = 1280;
                adjustedY = (adjustedX * ratio).toInt();
            }else{
                val ratio=  (_bitmap_.width.toFloat())/(_bitmap_.height.toFloat());
                adjustedY = 1280;
                adjustedX = (adjustedY*ratio).toInt();
            }
            adjuste = true;
        }

        var _bitmap = if(adjuste) Bitmap.createScaledBitmap(_bitmap_, adjustedX, adjustedY, true) else _bitmap_
        _bitmap = _bitmap.resizeToMultipleOf128(false)
//        _bitmap = _bitmap.scale(1024, 1024, false)
        var x =efficientMultiScaleDetection(_bitmap)
        x= filterDetectionsBySize(x)
        x = mergeCloseDetections(x)
        x = x.filter { it ->
            (it.score > 0.05f)
                    && (it.box.width() > 3f)
                    && (it.box.height() > 3f)
        }//.subList(0,50)





        var timeBeforePreprocessing = System.currentTimeMillis()

        val preprocessedImages = x.map { det ->
//            Log.d("BeanClassifier", "Detected box: ${det.box.left}, ${det.box.top}, ${det.box.width()}, ${det.box.height()}")
            async {
                Triple(
                    preProcessClassifierInput(_bitmap, det.box, det.score),
                    det.box,
                    det.score
                )
            }
        }.awaitAll()

        var timeBeforeClassification = System.currentTimeMillis()






        val classificationResults = classifyBeans(preprocessedImages.map { it.first.second }, preprocessedImages.map { it.third })

        var timeBeforeDrawing = System.currentTimeMillis()

        val resultImage = drawDetections(_bitmap, preprocessedImages.zip(classificationResults))


        var processingTimeMs = timeBeforeClassification - timeBeforePreprocessing
        var classificationTimeMs = timeBeforeDrawing - timeBeforeClassification
        var drawingTimeMs = System.currentTimeMillis() - timeBeforeDrawing
        Log.d("BeanClassifier", "Processing time: $processingTimeMs ms")
        Log.d("BeanClassifier", "Classification time: $classificationTimeMs ms")
        Log.d("BeanClassifier", "Drawing time: $drawingTimeMs ms")
        val classCounts = classificationResults.groupingBy { it }.eachCount()
        val totalDetections = classificationResults.count { cr->cr != 3  }
        val classPercentages = classNames.map { className ->
            (classCounts[classNames.indexOf(className)] ?: 0).toDouble() / totalDetections * 100
        }

        Triple(resultImage, classPercentages,
            totalDetections.toInt()
            )
    }

    fun findPatchSizes(width: Int, height: Int, minPatchSize: Int = 100): MutableList<Pair<Int, Int>> {
        val gcd = generateSequence(width to height) { (a, b) -> b to a % b }
            .first { it.second == 0 }.first

        val factors = (minPatchSize..sqrt(gcd.toDouble()).toInt())
            .filter { gcd % it == 0 }
            .flatMap { listOf(it, gcd / it) }
            .distinct()
            .filter { it >= minPatchSize }
            .sortedDescending()

        println("Image dimensions: ${width}x$height")
        println("Greatest Common Divisor: $gcd")
        println("Minimum patch size: $minPatchSize")
        println("Potential square patch sizes:")

        var patch_size_nominees = mutableListOf<Pair<Int, Int>>()

        if (factors.isEmpty()) {
            println("No patch sizes >= $minPatchSize found that evenly divide both dimensions.")
            var closestSize = minPatchSize
            while (true) {
                if (closestSize >= width || closestSize >= height) break
                val wPatches = width / closestSize
                val hPatches = height / closestSize
                val wLeftover = width % closestSize
                val hLeftover = height % closestSize
                patch_size_nominees.add(Pair(closestSize, wLeftover*hLeftover))
                println("${closestSize}x$closestSize - Patches: ${wPatches}x$hPatches, Leftover: ${wLeftover}x$hLeftover")
                if (wLeftover == 0 && hLeftover == 0) break
                closestSize++
            }
        } else {
            factors.forEach { factor ->
                val wPatches = width / factor
                val hPatches = height / factor
                val wLeftover = width % factor
                val hLeftover = height % factor
                patch_size_nominees.add(Pair(factor, wLeftover*hLeftover))
                println("${factor}x$factor - Patches: ${wPatches}x$hPatches, Leftover: ${wLeftover}x$hLeftover")
            }
        }
        return patch_size_nominees
    }
    private suspend fun efficientMultiScaleDetection(image: Bitmap): List<Detection> = withContext(Dispatchers.Default) {
        val scaleFactors = listOf<Float>(1.0f)


        val patchSizes = listOf(
//            Triple(256, 256, 1),
            Triple(512, 512, 2),
//            Triple(512, 512, 4),
//            Triple(halfMin, halfMin, 1),
//           Pair(patch_sizes2, patch_sizes2)
        )

        val allDetections = mutableListOf<Detection>()

        scaleFactors.flatMap { scale ->
            val scaledImage = Bitmap.createScaledBitmap(
                image,
                (image.width * scale).toInt(),
                (image.height * scale).toInt(),
                true
            )

            val enhancedImages = listOf(scaledImage)

            enhancedImages.flatMap { img ->
                System.gc()
                var fullImageDetection = async {
                    detectOnImage(img, scale, 0, 0).map {
                        it->it.apply { it.score *= 0.2f }
                    }
//                    (listOf<Detection>())
                }
                val patchDetections = patchSizes.flatMap { (patchHeight, patchWidth, divisor) ->
                    (0 until img.height step (patchHeight/divisor)).flatMap { i ->
                        (0 until img.width step (patchWidth/divisor)).map { j ->
                            async {
                                var actualPatchHeight = patchHeight
                                var actualPatchWidth = patchWidth
                                if ((i + actualPatchHeight) > img.height || (j + actualPatchWidth) > img.width) {
                                    emptyList<Detection>()
                                } else {
                                    val patch =
                                        Bitmap.createBitmap(img, j, i, actualPatchWidth, actualPatchHeight)
                                    //                            Log.d("BeanClassifier", "Patch size: ${patch.width}, ${patch.height}")
                                    detectOnImage(patch, scale, j, i)
                                }
                            }

                        }
                    }
                }

                patchDetections + listOf(fullImageDetection)
            }
        }.awaitAll().flatten().also { allDetections.addAll(it) }

        allDetections
    }
    private val cpuThreadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
    private val coroutineScope = CoroutineScope(cpuThreadPool.asCoroutineDispatcher() + SupervisorJob())
    fun cleanup() {
        coroutineScope.cancel()
        cpuThreadPool.shutdown()
    }
    private suspend fun detectOnImage(image: Bitmap, scale: Float, offsetX: Int, offsetY: Int): List<Detection> {
        Log.d("Image size", "${image.width}, ${image.height}");
        val input = preProcessYoloInput(image)
        Log.d("BeanClassifier", "Input tensor shape: ${input.shape().contentToString()}")
        val ivv = IValue.from(input)
        Log.d("BeanClassifier", "Input tensor shape: ${ivv.toTensor().shape().contentToString()}")
        var yoloOutput = yoloModel.forward(ivv, IValue.from(0.03));
        if (yoloOutput.isNull) {
            return emptyList()
        }
        val (_output, boxes, scores) =  yoloOutput.toTuple()
        var output = _output.toTensor();
        var detResult = nms(output, 0.35f) // the 0.45 is IoU threshold


        var scaleX = imageSizeX /  image.width.toFloat()  // 2.0
        var scaleY =  imageSizeY / image.height.toFloat()  // 2.0
        var detections =  detResult.map { det ->
            Detection(
                RectF(
                    (((det.boundingBox.left ) / scale) / scaleX) + offsetX,
                    (((det.boundingBox.top ) / scale) / scaleY) + offsetY,
                    (((det.boundingBox.right ) / scale) / scaleX) + offsetX,
                    (((det.boundingBox.bottom ) / scale) / scaleY) + offsetY
                ),
                det.score,
                scale
            )
        }
        return detections;//nonMaxSuppression(, 0.4f)
    }


    private suspend fun preProcessYoloInput(bitmap: Bitmap): Tensor  = withContext(Dispatchers.Default)  {
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, imageSizeX, imageSizeY, true)
        // newer
        TensorImageUtils.bitmapToFloat32Tensor(
            resizedBitmap,
            floatArrayOf(0f, 0f, 0f),
            floatArrayOf(1f, 1f, 1f)
        )
    }

    private fun filterDetectionsBySize(detections: List<Detection>, stdThreshold: Float = 3f): List<Detection> {
        val areas = detections.map { abs(it.box.width() * it.box.height()) }
        val meanArea = areas.average().toFloat()
        val stdArea = sqrt(areas.map { (it - meanArea).pow(2) }.average()).toFloat()

        val minArea = 100f
        val maxSize = 20000f

        return detections.filter { det ->
            val area = abs(det.box.width() * det.box.height())
            var wHratio = max(det.box.width() / det.box.height(), det.box.height() / det.box.width())
              wHratio < 2.3 && area > minArea && area < maxSize &&det.box.left > 0 && det.box.top > 0
//                    && det.box.width() > 15
//                    && det.box.height() > 15
        }
    }

    private suspend fun preProcessClassifierInput(_bitmap: Bitmap, box: RectF, score: Float): Pair<Bitmap,Tensor> = withContext(Dispatchers.Default) {
        val left = max(0f, box.left).toInt()
        val top = max(0f, box.top).toInt()

        var right=left + box.width()
        var bottom= top+box.height()
        var _maxRight = min(_bitmap.width.toFloat(), right).toFloat()
        var _maxBottom = min(_bitmap.height.toFloat(), bottom).toFloat()

        val width = min(_bitmap.width.toFloat(), _maxRight - left).toInt()
        val height = min(_bitmap.height.toFloat(), _maxBottom - top).toInt()



        var cutimg = Bitmap.createBitmap(_bitmap, left, top, width, height)
        val resizedBitmap = cutimg.scale(64, 64, false)

         Pair(resizedBitmap,TensorImageUtils.bitmapToFloat32Tensor(
            resizedBitmap,
             floatArrayOf(0f, 0f, 0f),
             floatArrayOf(1f, 1f, 1f)
        ))
    }

//    private suspend fun classifyBatch(batch: List<Tensor>): Tensor = withContext(Dispatchers.Default){
//        var floatArrayList = batch.flatMap { it.dataAsFloatArray.toList() }.toFloatArray()
//        val inputTensor = Tensor.fromBlob(floatArrayList, longArrayOf(batch.size.toLong(), 3, 128, 128))
//
//    }
private suspend fun classifyBeans(inputs: List<Tensor>, scores: List<Float>): List<Int> = withContext(Dispatchers.Default) {
    // batch inference, merge them into batches of 24
    val results = mutableListOf<Int>()
    val _results = inputs.zip(scores).map {
        async {
            try {
                val result = Pair(classifierModel.forward(IValue.from(it.first)).toTensor(), it.second)
                print("Scanned an image")
                return@async result
            } catch (e: Exception) {
                // Log the error for debugging
                Log.e("BeanClassifier", "Error during classification: ${e.message}")

                // Optionally notify the user
                withContext(Dispatchers.Main) {
                    Toast.makeText( context,"An error occurred while classifying the image.", Toast.LENGTH_SHORT).show()
                }

                // Return null to indicate an error occurred
                return@async null
            }
        }
    }.awaitAll()

    // Filter out any null results if an error occurred
    val filteredResults = _results.filterNotNull()

    for ((output, scoreItm) in filteredResults) {
        val outputData = output.dataAsFloatArray
        for (bi in 0 until output.shape()[0].toInt()) {
            val outputStart = bi * 4
            val outputEnd = outputStart + 4
            val outputSlice = outputData.sliceArray(outputStart until outputEnd)
            val maxIndex = outputSlice.indices.maxByOrNull { outputSlice[it] } ?: 0

            if (maxIndex == 3) {
                if (scoreItm >= 0.2f) {
                    // find second max
                    val maxIndex2 = outputSlice.indices.sortedByDescending { outputSlice[it] }.get(1)
                    results.add(maxIndex2)
                } else {
                    results.add(maxIndex)
                }
            } else {
                if (outputSlice[3] > 0.2f && scoreItm < 0.10f) {
                    results.add(3)
                } else {
                    results.add(maxIndex)
                }
            }
        }
    }
    return@withContext results
}


    private fun drawDetections(bitmap: Bitmap, detections: List<Pair<Triple<Pair<Bitmap,Tensor>, RectF, Float>, Int>>): Bitmap {
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        val paint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }

        for ((detection, classIndex) in detections) {
            val (_, box, score) = detection
            paint.color = classColors[classIndex]
            canvas.drawRect(box, paint)
        }

        return mutableBitmap
    }

    private fun assetFilePath(context: Context, assetName: String): String {
        val file = File(context.filesDir, assetName)
        if (file.exists() && file.length() > 0) {
            return file.absolutePath
        }

        context.assets.open(assetName).use { inputStream ->
            FileOutputStream(file).use { outputStream ->
                val buffer = ByteArray(4 * 1024)
                var read: Int
                while (inputStream.read(buffer).also { read = it } != -1) {
                    outputStream.write(buffer, 0, read)
                }
                outputStream.flush()
            }
        }
        return file.absolutePath
    }


}