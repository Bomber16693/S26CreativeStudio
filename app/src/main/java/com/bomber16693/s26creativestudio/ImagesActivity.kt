package com.bomber16693.s26creativestudio

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Working image studio: edits a photo offline, or submits generation/img2img/inpainting
 * to the free community AI Horde. Cloud workers receive submitted prompts/images.
 * This is NOT on-device diffusion.
 */
class ImagesActivity : Activity() {
    private val bg = Color.rgb(9, 14, 26)
    private val accent = Color.rgb(105, 229, 222)
    private val pale = Color.rgb(236, 243, 248)
    private val executor = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)
    private val cancelled = AtomicBoolean(false)
    private val store by lazy { getSharedPreferences("image_studio", MODE_PRIVATE) }
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var preview: ImageView
    private var original: Bitmap? = null
    private var mask: MaskPainter? = null
    @Volatile private var activeJob: String? = null

    private fun dp(n: Int) = (n * resources.displayMetrics.density + .5f).toInt()
    private fun text(s: String, size: Float = 14f) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(pale)
        setPadding(0, dp(7), 0, dp(7))
    }
    private fun add(v: View) { root.addView(v, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }) }
    private fun btn(s: String, action: () -> Unit): Button = Button(this).apply {
        text = s
        isAllCaps = false
        setOnClickListener { action() }
    }
    private fun field(hint: String, value: String = "", multi: Boolean = false): EditText =
        EditText(this).apply {
            this.hint = hint
            setHintTextColor(Color.LTGRAY)
            setTextColor(pale)
            setText(value)
            if (multi) { minLines = 3 } else setSingleLine(true)
        }
    private fun inform(s: String) = runOnUiThread { status.text = s.take(300) }
    private fun job(title: String, task: () -> Unit) {
        if (!busy.compareAndSet(false, true)) { inform("Another image job is active"); return }
        cancelled.set(false)
        inform(title + " started")
        executor.execute {
            try { task() }
            catch (e: Exception) { inform(title + ": " + (e.message ?: "failed")) }
            finally { activeJob = null; busy.set(false) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        val scroller = ScrollView(this).apply { setBackgroundColor(bg); isFillViewport = true }
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(20)) }
        if (android.os.Build.VERSION.SDK_INT >= 30) root.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            view.setPadding(dp(16) + bars.left, dp(12) + bars.top, dp(16) + bars.right, dp(20) + bars.bottom)
            insets
        }
        scroller.addView(root)
        setContentView(scroller)
        add(text("IMAGE STUDIO", 23f).apply { setTextColor(accent) })
        status = text("Edit offline, or generate using free community cloud workers.", 13f)
        add(status)
        add(text("Offline photo editing", 19f))
        add(btn("Choose a photo from gallery") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "image/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }, 501)
        })
        preview = ImageView(this).apply {
            setBackgroundColor(Color.rgb(20, 28, 39))
            scaleType = ImageView.ScaleType.FIT_CENTER
            minimumHeight = dp(200)
            adjustViewBounds = true
        }
        add(preview)
        add(text("Local photo adjustments (no internet or model needed)"))
        val saturation = SeekBar(this).apply { max = 200; progress = 115 }
        add(text("Saturation: 0–200%"))
        add(saturation)
        val brightness = SeekBar(this).apply { max = 200; progress = 100 }
        add(text("Brightness"))
        add(brightness)
        add(btn("Enhance photo offline and save PNG") {
            val originalPhoto = original ?: run { inform("Choose a photo first"); return@btn }
            val sat = saturation.progress / 100f
            val light = brightness.progress - 100f
            job("Local editing") {
                val transformed = Bitmap.createBitmap(originalPhoto.width, originalPhoto.height, Bitmap.Config.ARGB_8888)
                try {
                    val satFilter = ColorMatrix().apply { setSaturation(sat) }
                    satFilter.postConcat(ColorMatrix(floatArrayOf(
                        1f,0f,0f,0f,light, 0f,1f,0f,0f,light,
                        0f,0f,1f,0f,light, 0f,0f,0f,1f,0f
                    )))
                    Canvas(transformed).drawBitmap(originalPhoto, 0f, 0f,
                        Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(satFilter) })
                    val saved = savePng(transformed)
                    inform("Saved edited image in Pictures / S26CreativeStudio")
                    runOnUiThread { preview.setImageURI(saved) }
                } finally { transformed.recycle() }
            }
        })

        add(text("AI image generation (FREE COMMUNITY CLOUD)", 19f))
        add(text("Uses volunteer AI Horde computers, not your phone's GPU. Generation times and model availability vary. Prompts are sent to the service; anonymous jobs can be shared with its network.", 13f))
        val prompt = field("Describe the image or edit you want", store.getString("prompt", "") ?: "", true)
        add(prompt)
        val negative = field("Negative prompt (optional)", store.getString("negative", "") ?: "", true)
        add(negative)
        val model = field("Model name (blank = any available)", store.getString("model", "") ?: "")
        add(model)
        val lora = field("CivitAI LoRA version/name (optional; compatible models only)", store.getString("lora", "") ?: "")
        add(lora)
        val loraWeight = field("LoRA weight (e.g. 0.8)", store.getString("loraWeight", "0.8") ?: "0.8")
        add(loraWeight)
        add(text("For cloud LoRAs, use a public CivitAI ID/version supported by the selected worker. This is not local LoRA inference.", 12f))
        val apiKey = field("AI Horde API key (optional, anonymous by default)", store.getString("key", "") ?: "")
        add(apiKey)
        val allowPhotoUpload = Switch(this).apply {
            text = "I consent to sending my selected photo to AI Horde volunteers"
            setTextColor(pale)
            isChecked = false
        }
        add(allowPhotoUpload)
        val strength = SeekBar(this).apply { max = 90; progress = 65 }
        add(text("Edit strength / denoise (10%–90%)"))
        add(strength)
        add(text("Paint a WHITE mask on the area you want AI to replace. Black areas are retained. Image resized to 512×512."))
        val painter = MaskPainter(this)
        mask = painter
        add(painter)
        add(btn("Clear edit mask") { painter.clear() })
        fun values(): ImageParams {
            val idea = prompt.text.toString().trim()
            if (idea.isEmpty()) error("Enter a prompt")
            store.edit().putString("prompt", idea)
                .putString("negative", negative.text.toString())
                .putString("model", model.text.toString())
                .putString("lora", lora.text.toString())
                .putString("loraWeight", loraWeight.text.toString())
                .putString("key", apiKey.text.toString()).apply()
            return ImageParams(
                idea, negative.text.toString(), model.text.toString().trim(),
                lora.text.toString().trim(), loraWeight.text.toString().toDoubleOrNull() ?: 0.8,
                apiKey.text.toString().ifBlank { "0000000000" }, strength.progress / 100f
            )
        }
        add(btn("AI generate new image (cloud)") {
            try { val p = values(); job("AI image") { generate(p, "txt2img", null, null) } }
            catch (e: Exception) { inform(e.message ?: "Invalid settings") }
        })
        add(btn("AI restyle selected photo (cloud)") {
            val src = original
            if (src == null) { inform("Choose your photo first"); return@btn }
            if (!allowPhotoUpload.isChecked) { inform("Consent to sharing the photo with cloud workers first"); return@btn }
            try { val p = values(); job("Image-to-image") { generate(p, "img2img", src, null) } }
            catch (e: Exception) { inform(e.message ?: "Invalid settings") }
        })
        add(btn("AI inpaint WHITE masked area (cloud)") {
            val src = original
            if (src == null) { inform("Choose a photo first"); return@btn }
            if (!painter.hasMask) { inform("Paint the area to replace first"); return@btn }
            if (!allowPhotoUpload.isChecked) { inform("Consent to sharing photo and mask with cloud workers"); return@btn }
            val maskCopy = painter.snapshot()
            try {
                val p = values()
                job("Inpainting") { try { generate(p, "inpainting", src, maskCopy) } finally { maskCopy.recycle() } }
            } catch (e: Exception) { maskCopy.recycle(); inform(e.message ?: "Invalid settings") }
        })
        add(btn("Cancel current AI job") {
            cancelled.set(true)
            val id = activeJob
            if (id != null) executor.execute { try { req("DELETE", "https://aihorde.net/api/v2/generate/status/" + id, apiKey.text.toString().ifBlank { "0000000000" }) } catch (_: Exception) {} }
            inform("Cancellation requested")
        })
        add(text("Future local AI: Qualcomm-compatible diffusion runtime is not bundled in this version. YuE2 music is a separate native feature on the main screen.", 12f))
    }
    @Deprecated("Legacy gallery picker")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 501 || resultCode != RESULT_OK || data?.data == null) return
        try {
            val uri = data.data!!
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
            var n = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / n > 1500) n *= 2
            val bmp = contentResolver.openInputStream(uri)!!.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = n })
            } ?: error("Failed to load photo")
            original?.recycle()
            original = bmp
            preview.setImageBitmap(bmp)
            mask?.setPhoto(bmp)
            inform("Photo loaded. Local editing and optional AI edits are ready.")
        } catch (e: Exception) { inform("Photo load: " + e.message) }
    }
    private data class ImageParams(val prompt: String, val negative: String, val model: String,
        val lora: String, val loraWeight: Double, val key: String, val strength: Float)
    private fun base64(bitmap: Bitmap): String {
        val s = ByteArrayOutputStream()
        val b = Bitmap.createScaledBitmap(bitmap, 512, 512, true)
        try { b.compress(Bitmap.CompressFormat.WEBP_LOSSY, 88, s) }
        finally { if (b != bitmap) b.recycle() }
        return Base64.encodeToString(s.toByteArray(), Base64.NO_WRAP)
    }
    private fun generate(p: ImageParams, mode: String, input: Bitmap?, painted: Bitmap?) {
        val body = JSONObject()
        body.put("prompt", p.prompt + if (p.negative.isBlank()) "" else "###" + p.negative)
        body.put("nsfw", false)
        body.put("censor_nsfw", true)
        body.put("trusted_workers", false)
        body.put("shared", false)
        body.put("r2", true)
        val params = JSONObject().put("width", 512).put("height", 512)
            .put("steps", 20).put("n", 1).put("cfg_scale", 7.0)
            .put("sampler_name", "k_euler_a")
        if (p.lora.isNotBlank()) {
            params.put("loras", JSONArray().put(JSONObject().put("name", p.lora)
                .put("model", p.loraWeight.coerceIn(0.1, 1.5))))
        }
        if (p.model.isNotBlank()) body.put("models", JSONArray().put(p.model))
        if (input != null) {
            body.put("source_image", base64(input))
            body.put("source_processing", mode)
            params.put("denoising_strength", p.strength.coerceIn(0.1f, 0.9f).toDouble())
            if (painted != null) body.put("source_mask", base64(painted))
        }
        body.put("params", params)
        val create = req("POST", "https://aihorde.net/api/v2/generate/async", p.key, body)
        val id = create.optString("id")
        if (id.isBlank()) error("Horde returned no job ID: " + create.toString().take(170))
        activeJob = id
        inform("Queued on AI Horde: " + id.take(12))
        for (i in 0..150) {
            if (cancelled.get()) { req("DELETE", "https://aihorde.net/api/v2/generate/status/" + id, p.key); error("Cancelled") }
            Thread.sleep(3500)
            val info = req("GET", "https://aihorde.net/api/v2/generate/status/" + id, p.key)
            if (info.optBoolean("faulted", false)) error("Cloud worker failed; try again")
            if (info.optBoolean("done", false)) {
                val gens = info.optJSONArray("generations") ?: error("No returned images")
                if (gens.length() == 0) error("The request completed without an image")
                val link = gens.getJSONObject(0).optString("img")
                val imageBytes = if (link.startsWith("https://")) downloadImage(link)
                    else Base64.decode(link.substringAfter(","), Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    ?: error("Could not decode generated image")
                try {
                    val uri = savePng(bitmap)
                    inform("Image saved to Pictures / S26CreativeStudio")
                    runOnUiThread { preview.setImageURI(uri) }
                } finally { bitmap.recycle() }
                return
            }
            if (i % 3 == 0) inform("AI Horde: queued " + info.optInt("wait_time") +
                "s; position " + info.optInt("queue_position") +
                "; processing " + info.optInt("processing"))
        }
        error("The community queue did not finish in this session")
    }
    private fun req(method: String, url: String, key: String, payload: JSONObject? = null): JSONObject {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 12000
        c.readTimeout = 25000
        c.requestMethod = method
        c.setRequestProperty("apikey", key)
        c.setRequestProperty("Client-Agent", "S26CreativeStudio:0.4:mobile")
        c.setRequestProperty("Accept", "application/json")
        try {
            if (payload != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val bytes = ByteArrayOutputStream()
            stream?.use { it.copyTo(bytes) }
            val raw = bytes.toString("UTF-8")
            if (code !in 200..299) error("HTTP " + code + ": " + raw.take(260))
            return if (raw.isBlank()) JSONObject() else JSONObject(raw)
        } finally { c.disconnect() }
    }
    private fun downloadImage(link: String): ByteArray {
        val url = URL(link)
        if (url.protocol != "https") error("Refusing non-HTTPS image URL")
        val c = url.openConnection() as HttpURLConnection
        c.connectTimeout = 12000
        c.readTimeout = 30000
        try {
            if (c.responseCode !in 200..299) error("Image link unavailable")
            val out = ByteArrayOutputStream()
            c.inputStream.use { input ->
                val chunk = ByteArray(8192)
                while (true) {
                    val n = input.read(chunk)
                    if (n < 0) break
                    if (out.size() + n > 20 * 1024 * 1024) error("Cloud image too large")
                    out.write(chunk, 0, n)
                }
            }
            return out.toByteArray()
        } finally { c.disconnect() }
    }
    private fun savePng(bitmap: Bitmap): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "s26_image_" + System.currentTimeMillis() + ".png")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/S26CreativeStudio")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val target = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("Cannot save image")
        try {
            contentResolver.openOutputStream(target)?.use {
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) error("PNG compression failed")
            } ?: error("Cannot write image")
            contentResolver.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            return target
        } catch (e: Exception) { contentResolver.delete(target, null, null); throw e }
    }
    override fun onDestroy() {
        super.onDestroy()
        cancelled.set(true)
        executor.shutdown()
    }
}

/** White painted pixels are replaced during inpainting; black pixels are preserved. */
private class MaskPainter(activity: Activity) : View(activity) {
    private val brush = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; strokeWidth = 26f
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        style = Paint.Style.STROKE
    }
    private var original: Bitmap? = null
    private val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
    private val board = Canvas(bitmap)
    private var stroke = Path()
    var hasMask = false
        private set
    init {
        minimumHeight = (300 * resources.displayMetrics.density).toInt()
        clear()
    }
    fun setPhoto(b: Bitmap) { original = b; clear() }
    fun clear() { board.drawColor(Color.BLACK); hasMask = false; stroke.reset(); invalidate() }
    fun snapshot(): Bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
    override fun onMeasure(w: Int, h: Int) {
        val width = MeasureSpec.getSize(w)
        setMeasuredDimension(width, (width.coerceAtMost((320 * resources.displayMetrics.density).toInt())).coerceAtLeast(1))
    }
    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        c.drawColor(Color.rgb(28, 37, 49))
        original?.let {
            c.drawBitmap(it, null, Rect(0,0,width,height), Paint(Paint.FILTER_BITMAP_FLAG))
        }
        val over = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(90,255,255,255); strokeWidth = 2f }
        val overlay = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = 75 }
        // Only white mask pixels should be visible; black becomes transparent for display.
        val shown = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val px = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(px, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (i in px.indices) px[i] = if ((px[i] and 0x00ffffff) == 0) 0 else Color.argb(140, 255, 255, 255)
        shown.setPixels(px, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        c.drawBitmap(shown, null, Rect(0,0,width,height), overlay)
        shown.recycle()
    }
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (original == null) return false
        val x = e.x.coerceIn(0f, width.toFloat()) * (512f / width.coerceAtLeast(1))
        val y = e.y.coerceIn(0f, height.toFloat()) * (512f / height.coerceAtLeast(1))
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { stroke = Path(); stroke.moveTo(x,y); board.drawCircle(x,y,13f,Paint(Paint.ANTI_ALIAS_FLAG).apply {color=Color.WHITE}); hasMask = true }
            MotionEvent.ACTION_MOVE -> { stroke.lineTo(x,y); board.drawPath(stroke,brush) }
            MotionEvent.ACTION_UP -> { stroke.lineTo(x,y); board.drawPath(stroke,brush); stroke.reset() }
            else -> return false
        }
        invalidate()
        return true
    }
}
