package com.bomber16693.s26creativestudio

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.os.Build
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Offline-first prototype: YuE2 runs separately. Photo/video utilities use Android SDK. */
class MainActivity : Activity() {
    private val navy = Color.rgb(9, 14, 26)
    private val teal = Color.rgb(105, 229, 222)
    private val white = Color.rgb(236, 243, 248)
    private val faint = Color.rgb(157, 176, 192)
    private val workers = Executors.newSingleThreadExecutor()
    private val cancelled = AtomicBoolean(false)
    private val prefs by lazy { getSharedPreferences("s26", MODE_PRIVATE) }
    private lateinit var content: LinearLayout
    private lateinit var statusView: TextView
    private var selectedPhoto: Uri? = null
    private var mode = "Music"
    private var running = false
    private val pickerCode = 871
    private val modelManagerDelegate = lazy { ModelManager(this) { status(it) } }
    private val modelManager by modelManagerDelegate
    private val nativeYuEDelegate = lazy { NativeYuE(this) { status(it) } }
    private val nativeYuE by nativeYuEDelegate
    private fun modelManagerDelegateInitialized() = modelManagerDelegate.isInitialized()

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.statusBarColor = navy
        window.navigationBarColor = navy
        showScreen("Music")
    }
    private fun dp(n: Int) = (n * resources.displayMetrics.density + .5f).toInt()
    private fun bg(color: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(13).toFloat()
    }
    private fun label(s: String, size: Float = 15f): TextView = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(white)
        setPadding(0, dp(7), 0, dp(7))
    }
    private fun add(view: View) {
        content.addView(view, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
    }
    private fun button(s: String, doIt: () -> Unit): Button = Button(this).apply {
        text = s
        isAllCaps = false
        setTextColor(navy)
        background = bg(teal)
        setOnClickListener { doIt() }
    }
    private fun input(hint: String, initial: String = "", rows: Int = 1): EditText = EditText(this).apply {
        this.hint = hint
        setHintTextColor(faint)
        setTextColor(white)
        background = bg(Color.rgb(32, 43, 60))
        setText(initial)
        setPadding(dp(13), dp(13), dp(13), dp(13))
        if (rows > 1) {
            minLines = rows
            gravity = Gravity.TOP
        } else setSingleLine(true)
    }
    private fun status(s: String) = runOnUiThread { statusView.text = s.take(240) }
    private fun runJob(title: String, job: () -> Unit) {
        if (running) { status("Finish or cancel the current task first"); return }
        val info = ActivityManager.MemoryInfo()
        (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (info.lowMemory || power.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE) {
            status("Phone is hot or running low on memory")
            return
        }
        running = true
        cancelled.set(false)
        status(title + " started")
        workers.execute {
            try { job() }
            catch (e: Exception) { status(title + ": " + (e.message ?: "error")) }
            finally { runOnUiThread { running = false } }
        }
    }
    private fun showScreen(tab: String) {
        mode = tab
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundColor(navy)
        }
        setContentView(root)
        if (Build.VERSION.SDK_INT >= 30) {
            root.setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                v.setPadding(dp(16) + bars.left, dp(14) + bars.top, dp(16) + bars.right, dp(14) + bars.bottom)
                insets
            }
        }
        root.addView(label("S26 / CREATIVE STUDIO", 22f).apply { setTextColor(teal) })
        statusView = label("Offline-first • 12GB RAM profile", 12f)
        root.addView(statusView)
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (t in listOf("Music", "Photo", "Video", "Models", "Setup")) {
            tabs.addView(TextView(this).apply {
                text = t
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(if (t == tab) teal else faint)
                setPadding(0, dp(14), 0, dp(14))
                setOnClickListener { showScreen(t) }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(tabs)
        val scroll = ScrollView(this)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(15), 0, dp(20))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        when (tab) {
            "Music" -> showMusic()
            "Photo" -> showPhoto()
            "Video" -> showVideo()
            "Models" -> modelManager.render(content)
            else -> showSetup()
        }
    }
    private fun showMusic() {
        add(label("YuE2 • Built-in engine (experimental)", 19f))
        add(label("No Termux. The engine runs inside this app using the models you downloaded in Models.", 13f))
        add(button("Start built-in YuE2") { nativeYuE.start() })
        add(button("Stop built-in YuE2") { nativeYuE.stop() })
        val endpoint = input("YuE2 URL", prefs.getString("endpoint", "http://127.0.0.1:8087") ?: "")
        val style = input("Style / genre", prefs.getString("style", "Industrial techno, glitch hop") ?: "")
        val words = input("Lyrics (leave blank for instrumental)", prefs.getString("lyrics", "") ?: "", 6)
        add(endpoint); add(style); add(words)
        val instrumental = Switch(this).apply {
            text = "Instrumental (no singing)"
            setTextColor(white)
            isChecked = prefs.getBoolean("instrumental", false)
        }
        add(instrumental)
        add(button("Check YuE2 connection") {
            val base = endpoint.text.toString().trimEnd('/')
            if (!validUrl(base)) { status("Invalid server URL"); return@button }
            runJob("Connection") {
                val (code, data, _) = request(base + "/health")
                status(if (code == 200) "Server responds: " + data.toString(Charsets.UTF_8).take(110) else "Not ready (HTTP " + code + ")")
            }
        })
        add(button("Generate YuE2 music") {
            val base = endpoint.text.toString().trimEnd('/')
            if (!validUrl(base)) { status("Invalid server URL"); return@button }
            val genre = style.text.toString()
            val lyrics = words.text.toString()
            val noVocal = instrumental.isChecked
            if (genre.isBlank() && lyrics.isBlank()) { status("Add a style or lyrics"); return@button }
            prefs.edit().putString("endpoint", base).putString("style", genre)
                .putString("lyrics", lyrics).putBoolean("instrumental", noVocal).apply()
            runJob("YuE2 generation") {
                if (base == "http://127.0.0.1:8087") nativeYuE.ensureStarted()
                val body = JSONObject().put("style", genre).put("lyrics", if (noVocal) "" else lyrics)
                    .put("duration", 30.0).put("cot", "full").put("steps", 16)
                    .put("lm_batch_size", 1).put("synth_batch_size", 1)
                    .put("output_format", "mp3").put("mp3_bitrate", 128)
                val (code, data, _) = request(base + "/synth", body)
                if (code !in 200..299) error("Submit HTTP " + code + ": " + data.toString(Charsets.UTF_8).take(120))
                val obj = JSONObject(data.toString(Charsets.UTF_8))
                val id = obj.optString("id", obj.optString("job_id"))
                if (id.isEmpty()) error("Server response contains no job ID")
                status("YuE2 running: " + id.take(14))
                repeat(3600) {
                    if (cancelled.get()) error("Stopped polling; engine might still be rendering")
                    val query = "?id=" + URLEncoder.encode(id, "UTF-8")
                    val (c, json, _) = request(base + "/job" + query)
                    if (c !in 200..299) error("Job check HTTP " + c)
                    val state = JSONObject(json.toString(Charsets.UTF_8)).optString("status")
                    if (state == "done" || state == "completed") {
                        val (downloadCode, bytes, mime) = request(base + "/job" + query + "&result=1")
                        if (downloadCode !in 200..299) error("Result HTTP " + downloadCode)
                        val audio = if (mime.contains("multipart/")) extractMp3(bytes, mime) else bytes
                        if (audio.size < 100 || audio.size > 48 * 1024 * 1024) error("Unexpected audio size")
                        val uri = save(audio, "audio/mpeg", "Music", "yue2_" + System.currentTimeMillis() + ".mp3")
                        status("YuE2 song saved in Music")
                        open(uri, "audio/mpeg")
                        return@runJob
                    }
                    if (state == "failed" || state == "cancelled") error("YuE2 state: " + state)
                    Thread.sleep(2500)
                }
                error("Server timed out")
            }
        })
        add(button("Stop checking job") { cancelled.set(true); status("Polling cancellation requested") })
    }
    private fun validUrl(value: String): Boolean = try {
        val u = URL(value)
        (u.protocol == "http" || u.protocol == "https") && u.host.isNotBlank() && u.userInfo == null
    } catch (_: Exception) { false }

    private fun request(url: String, post: JSONObject? = null): Triple<Int, ByteArray, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10000
            c.readTimeout = 30000
            c.useCaches = false
            if (post != null) {
                c.requestMethod = "POST"
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(post.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            val data = ByteArrayOutputStream()
            val source = if (code in 200..299) c.inputStream else c.errorStream
            source?.use { s ->
                val b = ByteArray(8192)
                while (true) {
                    val n = s.read(b)
                    if (n < 0) break
                    if (data.size() + n > 48 * 1024 * 1024) error("Result exceeded maximum size")
                    data.write(b, 0, n)
                }
            }
            return Triple(code, data.toByteArray(), c.contentType ?: "")
        } finally { c.disconnect() }
    }
    private fun extractMp3(data: ByteArray, mime: String): ByteArray {
        val boundary=mime.substringAfter("boundary=","").trim().trim('"')
        if(boundary.isBlank())error("No multipart boundary in music response")
        fun index(bytes:ByteArray,from:Int):Int {
            if(bytes.isEmpty())return -1
            for(i in from..(data.size-bytes.size)){
                var found=true
                for(k in bytes.indices)if(data[i+k]!=bytes[k]){found=false;break}
                if(found)return i
            }
            return -1
        }
        val delimiter="\r\n\r\n".toByteArray(Charsets.US_ASCII)
        val next=("\r\n--"+boundary).toByteArray(Charsets.US_ASCII)
        var from=0
        while(from<data.size){
            val head=index(delimiter,from);if(head<0)break
            val meta=data.copyOfRange(from,head).toString(Charsets.ISO_8859_1)
            val begin=head+delimiter.size
            val end=index(next,begin);if(end<0)break
            if(meta.contains("Content-Type: audio/",true))return data.copyOfRange(begin,end)
            from=end+next.size
        }
        error("No MP3 audio part found in YuE2 response")
    }
    private fun showPhoto() {
        add(label("Photo enhancement • Offline", 19f))
        add(label("Enhances an existing photo. This is not AI image diffusion generation.", 13f))
        add(button("Choose photo") { choose() })
        add(label(if (selectedPhoto == null) "No photo selected" else "Photo selected"))
        add(button("Enhance and save PNG") {
            val uri = selectedPhoto
            if (uri == null) { status("Choose a photo first"); return@button }
            runJob("Enhance photo") {
                val src = decode(uri)
                try {
                    val dest = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
                    try {
                        Canvas(dest).drawBitmap(src, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG).apply {
                            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(1.18f) })
                        })
                        val out = ByteArrayOutputStream()
                        dest.compress(Bitmap.CompressFormat.PNG, 100, out)
                        val result = save(out.toByteArray(), "image/png", "Pictures",
                            "s26_photo_" + System.currentTimeMillis() + ".png")
                        status("Enhanced PNG saved")
                        open(result, "image/png")
                    } finally { dest.recycle() }
                } finally { src.recycle() }
            }
        })
        add(label("Local text-to-image model integration is planned; no model is bundled yet.", 13f))
    }
    private fun showVideo() {
        add(label("Photo-to-video • Offline", 19f))
        add(label("Create a 5-second H.264 MP4 zooming a photo. NOT neural video generation.", 13f))
        add(button("Choose starting photo") { choose() })
        add(label(if (selectedPhoto == null) "No photo selected" else "Photo selected"))
        add(button("Create 5-second MP4") {
            val uri = selectedPhoto
            if (uri == null) { status("Choose a photo first"); return@button }
            runJob("Encode MP4") {
                val bitmap = decode(uri)
                val mp4 = File(cacheDir, "s26_" + System.currentTimeMillis() + ".mp4")
                try {
                    makeVideo(bitmap, mp4)
                    val result = save(mp4.readBytes(), "video/mp4", "Movies", mp4.name)
                    status("MP4 saved to Movies")
                    open(result, "video/mp4")
                } finally { bitmap.recycle(); mp4.delete() }
            }
        })
        add(label("Neodragon/Wan generative video backends are not integrated yet.", 13f))
    }
    private fun showSetup() {
        add(label("S26 Ultra • Device status", 19f))
        add(label("Detected: " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL))
        val m = ActivityManager.MemoryInfo()
        (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(m)
        add(label("Available RAM: " + m.availMem / (1024 * 1024) + " MB"))
        add(label("Thermal status: " + (getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus))
        add(label("Only one task runs at once. Photos are downsampled to 1600 pixels. AI model files are NOT bundled.", 13f))
        add(button("YuE2 setup (GitHub)") {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ServeurpersoCom/yue2.cpp")))
        })
    }
    private fun choose() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }, pickerCode)
    }
    @Deprecated("Legacy activity result support for this prototype")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ModelManager.EXPORT_FOLDER_CODE && resultCode == RESULT_OK) {
            modelManager.onExportFolder(data?.data)
            return
        }
        if (requestCode == pickerCode && resultCode == RESULT_OK) {
            selectedPhoto = data?.data
            selectedPhoto?.let {
                try { contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                catch (_: SecurityException) {}
            }
            showScreen(mode)
            status("Photo selected")
        }
    }
    private fun decode(uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: error("Unable to decode image")
    }
    private fun save(data: ByteArray, mime: String, folder: String, filename: String): Uri {
        val collection = when (folder) {
            "Music" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            "Movies" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            else -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, folder + "/S26CreativeStudio")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(collection, values) ?: error("Storage not available")
        try {
            contentResolver.openOutputStream(uri)?.use { it.write(data) }
                ?: error("Failed to open output")
            contentResolver.update(uri, ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }, null, null)
            return uri
        } catch (e: Exception) {
            contentResolver.delete(uri, null, null)
            throw e
        }
    }
    private fun open(uri: Uri, mime: String) = runOnUiThread {
        try {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        } catch (_: Exception) {
            Toast.makeText(this, "Saved to Android " + mime.substringBefore('/'), Toast.LENGTH_LONG).show()
        }
    }
    private fun makeVideo(image: Bitmap, file: File) {
        val w = 640
        val h = 360
        val fps = 15
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        val config = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 1300000)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        codec.configure(config, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val surface = codec.createInputSurface()
        val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val info = MediaCodec.BufferInfo()
        var started = false
        var track = -1
        try {
            codec.start()
            fun drain(last: Boolean) {
                var empty = 0
                while (empty < (if (last) 150 else 2)) {
                    val index = codec.dequeueOutputBuffer(info, 10000L)
                    if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        track = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        started = true
                    } else if (index >= 0) {
                        if (info.size > 0 && started) {
                            val buffer = codec.getOutputBuffer(index) ?: error("Encoder buffer missing")
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            muxer.writeSampleData(track, buffer, info)
                        }
                        codec.releaseOutputBuffer(index, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return
                        empty = 0
                    } else empty++
                }
            }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val begin = System.nanoTime()
            for (i in 0 until 5 * fps) {
                val canvas = surface.lockHardwareCanvas()
                try {
                    canvas.drawColor(Color.BLACK)
                    val zoom = 1.03f + .08f * (i.toFloat() / (5 * fps).toFloat())
                    val scale = maxOf(w.toFloat() / image.width, h.toFloat() / image.height) * zoom
                    val drawW = image.width * scale
                    val drawH = image.height * scale
                    canvas.drawBitmap(image, null, RectF(
                        (w - drawW) / 2, (h - drawH) / 2,
                        (w + drawW) / 2, (h + drawH) / 2
                    ), paint)
                } finally { surface.unlockCanvasAndPost(canvas) }
                drain(false)
                val delay = (begin + (i + 1L) * 1000000000L / fps - System.nanoTime()) / 1000000L
                if (delay > 0) Thread.sleep(delay)
            }
            codec.signalEndOfInputStream()
            drain(true)
            if (!started) error("No H.264 data produced")
        } finally {
            try { codec.stop() } catch (_: Exception) {}
            codec.release()
            surface.release()
            try { if (started) muxer.stop() } catch (_: Exception) {}
            muxer.release()
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        cancelled.set(true)
        workers.shutdown()
        if (modelManagerDelegateInitialized()) modelManager.stop()
        if (nativeYuEDelegate.isInitialized()) nativeYuE.stop()
    }
}
