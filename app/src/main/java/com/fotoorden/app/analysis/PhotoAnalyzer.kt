package com.fotoorden.app.analysis

import android.content.Context
import android.graphics.Bitmap
import android.util.Size
import com.fotoorden.app.data.Category
import com.fotoorden.app.data.Photo
import com.fotoorden.app.data.PhotoInfo
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

/**
 * Analiza una foto con ML Kit (todo se hace dentro del móvil):
 *  - detección de caras  -> Personas
 *  - reconocimiento de texto -> Documentos
 *  - etiquetas de imagen -> Paisajes, Comida, Mascotas...
 * y además calcula nitidez y hash para borrosas / repetidas.
 */
class PhotoAnalyzer(private val context: Context) {

    private val labeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder().setConfidenceThreshold(0.5f).build()
    )
    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setMinFaceSize(0.08f)
            .build()
    )
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun analyze(photo: Photo): PhotoInfo {
        val bmp = loadBitmap(photo) ?: return PhotoInfo.FAILED
        try {
            val hash = ImageMath.dHash(bmp)
            val sharpness = ImageMath.sharpness(bmp)

            if (Classifier.isScreenshot(photo)) {
                return PhotoInfo(Category.CAPTURAS, sharpness, hash, 0)
            }

            val image = InputImage.fromBitmap(bmp, 0)
            val area = bmp.width.toFloat() * bmp.height

            val faces = faceDetector.process(image).await()
            val faceRatio = faces.maxOfOrNull {
                it.boundingBox.width().toFloat() * it.boundingBox.height() / area
            } ?: 0f

            val labels = labeler.process(image).await().map { it.text.lowercase() }

            var textChars = 0
            var textRatio = 0f
            if (faceRatio < 0.08f) {
                val text = textRecognizer.process(image).await()
                textChars = text.text.count { it.isLetterOrDigit() }
                textRatio = text.textBlocks.sumOf { b ->
                    val r = b.boundingBox
                    if (r == null) 0.0 else (r.width().toDouble() * r.height())
                }.toFloat() / area
            }

            val category = Classifier.decide(
                faceCount = faces.size,
                faceRatio = faceRatio,
                labels = labels,
                textChars = textChars,
                textRatio = textRatio,
            )
            return PhotoInfo(category, sharpness, hash, faces.size)
        } finally {
            bmp.recycle()
        }
    }

    private fun loadBitmap(photo: Photo): Bitmap? = try {
        val b = context.contentResolver.loadThumbnail(photo.uri, Size(640, 640), null)
        if (b.config == Bitmap.Config.HARDWARE) {
            b.copy(Bitmap.Config.ARGB_8888, false).also { b.recycle() }
        } else b
    } catch (e: Exception) {
        null
    }
}

/** Reglas para decidir la categoría a partir de lo que ha visto la IA. */
object Classifier {

    private val landscape = setOf(
        "sky", "cloud", "mountain", "beach", "sea", "lake", "river", "sunset", "waterfall",
        "desert", "snow", "rainbow", "forest", "field", "flower", "plant", "tree", "garden",
        "park", "grass", "rock", "canyon", "cliff", "sand", "lighthouse", "skyline",
        "leisure", "pier", "bridge", "tower", "monument", "castle", "storm",
    )
    private val food = setOf(
        "food", "cuisine", "dessert", "cake", "pizza", "bread", "fruit", "vegetable",
        "drink", "coffee", "cookie", "sushi", "pasta", "salad", "meal", "dish", "breakfast",
        "lunch", "dinner", "wine", "beer", "juice", "icing", "cupcake", "sandwich",
        "hamburger", "hot dog", "fast food", "soup", "cheese", "egg", "chocolate",
    )
    private val pets = setOf(
        "dog", "cat", "pet", "bird", "horse", "rabbit", "hamster", "fish", "turtle", "duck",
    )
    private val people = setOf(
        "selfie", "smile", "person", "crowd", "team", "bride", "baby", "child", "dude",
        "beard", "eyelash", "skin", "hand", "groom",
    )
    private val docs = setOf(
        "paper", "receipt", "document", "poster", "menu", "newspaper", "book",
        "handwriting", "text", "font", "comics", "whiteboard", "screenshot",
    )

    fun isScreenshot(p: Photo): Boolean {
        val s = (p.relativePath + "/" + p.name).lowercase()
        return "screenshot" in s || "captura" in s || "screen_" in s || "screenrecord" in s
    }

    fun decide(
        faceCount: Int,
        faceRatio: Float,
        labels: List<String>,
        textChars: Int,
        textRatio: Float,
    ): Category {
        val docLabel = labels.any { it in docs }
        val textHeavy = textChars >= 120 ||
            (textChars >= 40 && textRatio >= 0.12f) ||
            (docLabel && textChars >= 20)

        // Una cara pequeña dentro de mucho texto (DNI, carné...) cuenta como documento
        if (faceCount > 0 && faceRatio >= 0.015f && !(textHeavy && faceRatio < 0.06f)) {
            return Category.PERSONAS
        }
        if (textHeavy) return Category.DOCUMENTOS
        if (labels.any { it in pets }) return Category.MASCOTAS
        if (labels.any { it in food }) return Category.COMIDA
        if (labels.any { it in people }) return Category.PERSONAS
        if (labels.any { it in landscape }) return Category.PAISAJES
        return Category.OTRAS
    }
}
