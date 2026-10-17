package com.bomber16693.s26creativestudio

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.view.View
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** On-device downloader: can store model files, but does not run neural inference. */
class ModelManager(private val activity: Activity, private val progress: (String) -> Unit) {
    companion object { const val EXPORT_FOLDER_CODE = 982; const val IMPORT_MODEL_CODE = 983 }
    private val teal = Color.rgb(105, 229, 222)
    private val white = Color.rgb(235, 241, 247)
    private val subdued = Color.rgb(178, 195, 207)
    private val executor = Executors.newSingleThreadExecutor()
    private val abort = AtomicBoolean(false)
    private val busy = AtomicBoolean(false)
    private val directory = File(activity.getExternalFilesDir(null) ?: activity.filesDir, "models").apply { mkdirs() }
    private var exportModel: String? = null
    private val models = listOf(
        Spec("Stable Diffusion 1.5 • Local images", "v1-5-pruned-emaonly.safetensors", 4_265_146_304L,
            "LOCAL image generation • 512x512 • img2img / mask editing / SD1.5 LoRAs. CreativeML Open RAIL-M. Runs CPU, may be slow.",
            "https://huggingface.co/stable-diffusion-v1-5/stable-diffusion-v1-5/resolve/main/v1-5-pruned-emaonly.safetensors"),
        Spec("YuE2 • Q5 backbone", "YuE2-3B-Q5_K_M.gguf", 2_620_000_000L,
            "Music • smaller 3.6B backbone • requires VAE + separate yue2.cpp engine",
            "https://huggingface.co/Serveurperso/YuE2-GGUF/resolve/main/YuE2-3B-Q5_K_M.gguf"),
        Spec("YuE2 • audio VAE", "YuE2-Vae-F32.gguf", 530_000_000L,
            "Music • decoder REQUIRED alongside the backbone",
            "https://huggingface.co/Serveurperso/YuE2-GGUF/resolve/main/YuE2-Vae-F32.gguf"),
        Spec("YuE2 • Q8 backbone", "YuE2-3B-Q8_0.gguf", 3_810_000_000L,
            "Music • higher precision alternative; only download one backbone",
            "https://huggingface.co/Serveurperso/YuE2-GGUF/resolve/main/YuE2-3B-Q8_0.gguf")
    )
    private data class Spec(val label: String, val file: String, val approxBytes: Long, val detail: String, val url: String)
    private fun dp(n: Int) = (n * activity.resources.displayMetrics.density + 0.5f).toInt()
    private fun text(value: String, size: Float = 14f): TextView = TextView(activity).apply {
        text = value
        textSize = size
        setTextColor(white)
        setPadding(dp(3), dp(7), dp(3), dp(7))
    }
    private fun button(title: String, action: () -> Unit): Button = Button(activity).apply {
        text = title
        isAllCaps = false
        background = GradientDrawable().apply { setColor(teal); cornerRadius = dp(12).toFloat() }
        setTextColor(Color.rgb(5, 14, 24))
        setOnClickListener { action() }
    }
    private fun add(column: LinearLayout, view: View) = column.addView(view, LinearLayout.LayoutParams(-1, -2).apply {
        bottomMargin = dp(8)
    })
    private fun human(bytes: Long) = String.format(java.util.Locale.US, "%.2f GB", bytes.toDouble() / 1_000_000_000.0)
    fun render(column: LinearLayout) {
        add(column, text("Download model files", 20f))
        add(column, text("Models download to private phone storage. YuE2 runs with the built-in music engine; SD1.5 powers the local image editor. LoRAs must match SD1.5.", 13f))
        add(column, text("Free phone storage: " + human(StatFs(directory.absolutePath).availableBytes), 13f))
        for (m in models) {
            val existing = File(directory, m.file)
            val partial = File(directory, m.file + ".part")
            add(column, text(m.label + " • " + human(m.approxBytes), 17f))
            add(column, text(m.detail + "\n" + when {
                existing.isFile -> "Downloaded: " + human(existing.length())
                partial.isFile -> "Partial: " + human(partial.length()) + " — can resume"
                else -> "Not downloaded"
            }, 13f))
            add(column, button("Download / resume " + m.file) { startDownload(m) })
            if (existing.isFile) {
                add(column, button("Check model file") { inspect(existing) })
                add(column, button("Export to selected folder") {
                    exportModel = m.file
                    activity.startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), EXPORT_FOLDER_CODE)
                })
                add(column, button("Delete " + m.file) {
                    if (busy.get()) { progress("Finish the active download first"); return@button }
                    if (existing.delete()) progress("Deleted " + m.file) else progress("Could not delete file")
                })
            }
        }
        add(column, text("Already downloaded a model? Import it from Downloads or Documents. Useful after reinstalling the app.", 13f))
        add(column, button("Import downloaded .GGUF / .safetensors from phone") {
            activity.startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }, IMPORT_MODEL_CODE)
        })
        add(column, button("Pause / stop downloading") { abort.set(true); progress("Pausing download; partial file kept") })
        add(column, text("Local image models", 18f))
        add(column, text("Recommended for S26 Ultra 12GB: SD1.5 checkpoint above. Models larger than SD1.5 are not validated. SD1.5 LoRAs are imported in Image Studio.", 13f))
        add(column, button("About SD1.5 image runtime") {
            activity.startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse("https://github.com/leejet/stable-diffusion.cpp")))
        })
        add(column, text("Custom model download (advanced)", 18f))
        add(column, text("Direct HTTPS model files only. Custom files require a compatible format; only SD1.5 single-file checkpoints are supported for local images.", 13f))
        val custom = EditText(activity).apply {
            hint = "https://huggingface.co/.../resolve/main/model.gguf"
            setSingleLine(false)
            maxLines = 3
            setTextColor(white)
            setHintTextColor(subdued)
        }
        add(column, custom)
        add(column, button("Download custom model URL") {
            val url = custom.text.toString().trim()
            val parsed = try { URL(url) } catch (_: Exception) { null }
            if (parsed == null || parsed.protocol != "https" || parsed.userInfo != null ||
                parsed.host.isBlank() || parsed.port != -1 && parsed.port != 443) {
                progress("Please use a direct HTTPS model file URL")
                return@button
            }
            val rawName = Uri.decode(parsed.path.substringAfterLast('/'))
            val name = rawName.take(100)
            if (!name.matches(Regex("[A-Za-z0-9_.-]{3,100}")) ||
                !(name.endsWith(".gguf", true) || name.endsWith(".safetensors", true) ||
                  name.endsWith(".onnx", true) || name.endsWith(".bin", true) || name.endsWith(".zip", true))) {
                progress("URL must end in a model filename (.gguf, .onnx, .safetensors, .bin or .zip)")
                return@button
            }
            startDownload(Spec("Custom model", name, 0, "Custom • no inference yet", url))
        })
        add(column, text("Note: the YuE2 weights are CC BY-NC 4.0; check licences before use.", 12f))
    }
    private fun startDownload(spec: Spec) {
        if (!busy.compareAndSet(false, true)) { progress("A download or export is already running"); return }
        abort.set(false)
        executor.execute {
            try { download(spec) }
            catch (e: Exception) { progress("Download stopped: " + (e.message ?: "unknown error")) }
            finally { busy.set(false) }
        }
    }
    private fun download(spec: Spec) {
        val target = File(directory, spec.file)
        if (target.exists()) { progress(spec.file + " is already downloaded"); return }
        val temp = File(directory, spec.file + ".part")
        var offset = temp.length()
        val free = StatFs(directory.absolutePath).availableBytes
        if (spec.approxBytes > 0 && free < ((spec.approxBytes - offset).coerceAtLeast(0) + 400_000_000L)) {
            error("Need around " + human((spec.approxBytes - offset).coerceAtLeast(0) + 400_000_000L) + " free")
        }
        progress("Connecting to model host...")
        val connection = URL(spec.url).openConnection() as HttpURLConnection
        connection.connectTimeout = 20000
        connection.readTimeout = 50000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "S26CreativeStudio/0.2 Android")
        if (offset > 0) connection.setRequestProperty("Range", "bytes=" + offset + "-")
        try {
            val code = connection.responseCode
            if (code != 200 && code != 206) error("Server returned HTTP " + code)
            if (offset > 0 && code == 200) { temp.delete(); offset = 0L }
            val ranged = connection.getHeaderField("Content-Range")
            if (code == 206 && offset > 0 && (ranged == null || !ranged.startsWith("bytes " + offset + "-"))) {
                error("Resume offset mismatch; partial download preserved")
            }
            val incoming = connection.contentLengthLong
            val expected = if (incoming >= 0) offset + incoming else spec.approxBytes
            if (expected > 0 && free < ((expected - offset).coerceAtLeast(0) + 100_000_000L)) {
                error("Not enough storage; require " + human(expected - offset + 100_000_000L))
            }
            var written = offset
            var tick = 0L
            connection.inputStream.use { source ->
                FileOutputStream(temp, offset > 0).buffered(128 * 1024).use { dest ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        if (abort.get()) { progress("Paused at " + human(written) + "; tap Download to resume"); return }
                        val n = source.read(buffer)
                        if (n < 0) break
                        dest.write(buffer, 0, n)
                        written += n
                        val now = System.currentTimeMillis()
                        if (now - tick > 1200) {
                            val total = if (expected > 0) " / " + human(expected) else ""
                            progress(spec.label + ": " + human(written) + total)
                            tick = now
                        }
                    }
                }
            }
            if (expected > 0 && written != expected) error("Incomplete model; " + human(written) + " saved to resume")
            if (written < 1024L) error("Model too small to be a genuine weight file")
            if (spec.file.endsWith(".gguf", true) && !hasGgufMagic(temp)) error("Not GGUF; server may have returned an error page")
            if (!temp.renameTo(target)) error("Failed to finalize model download")
            progress("Saved " + spec.file + " (" + human(written) + "). Export before using with Termux.")
        } finally { connection.disconnect() }
    }
    private fun hasGgufMagic(file: File): Boolean = try {
        FileInputStream(file).use {
            val magic = ByteArray(4)
            it.read(magic) == 4 && String(magic, Charsets.US_ASCII) == "GGUF"
        }
    } catch (_: Exception) { false }
    private fun inspect(file: File) {
        if (file.extension.equals("gguf", true)) {
            progress(if (hasGgufMagic(file)) "GGUF header verified; file size " + human(file.length()) +
                ". This does not validate all weights or run the model." else "Invalid GGUF header")
        } else progress("Saved " + file.name + " • " + human(file.length()) + " (not inference-tested)")
    }
    fun importModel(uri: Uri?) {
        if (uri == null) { progress("Import cancelled"); return }
        val cursor = activity.contentResolver.query(uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
        var name: String? = null
        var declaredSize: Long = -1L
        cursor?.use { c ->
            if (c.moveToFirst()) {
                name = c.getString(0)
                if (!c.isNull(1)) declaredSize = c.getLong(1)
            }
        }
        val filename = name ?: "unknown"
        if (!filename.matches(Regex("[A-Za-z0-9_.-]{3,140}")) || !(filename.endsWith(".gguf", true) || filename.endsWith(".safetensors", true))) {
            progress("Choose a YuE2 .gguf or an SD1.5 .safetensors checkpoint")
            return
        }
        if (!busy.compareAndSet(false, true)) { progress("Another model operation is running"); return }
        abort.set(false)
        executor.execute {
            val dest = File(directory, filename)
            val part = File(directory, filename + ".importing")
            try {
                if (dest.exists()) error("Model already exists. Delete it first if replacing.")
                val available = StatFs(directory.absolutePath).availableBytes
                if (declaredSize > 0 && available < declaredSize + 200_000_000L)
                    error("Not enough free storage to import " + human(declaredSize))
                var copied = 0L
                var tick = 0L
                activity.contentResolver.openInputStream(uri)?.use { source ->
                    FileOutputStream(part).use { target ->
                        val buffer = ByteArray(128 * 1024)
                        while (true) {
                            if (abort.get()) error("Import interrupted")
                            val n = source.read(buffer)
                            if (n < 0) break
                            target.write(buffer, 0, n)
                            copied += n
                            val now = System.currentTimeMillis()
                            if (now - tick > 1500) {
                                progress("Importing " + filename + ": " + human(copied))
                                tick = now
                            }
                        }
                    }
                } ?: error("Cannot open selected model file")
                if (declaredSize > 0 && copied != declaredSize)
                    error("Incomplete copy: " + human(copied))
                if (copied < 1024L || (filename.endsWith(".gguf",true) && !hasGgufMagic(part)))
                    error("Selected file is not a valid GGUF model")
                if (!part.renameTo(dest)) error("Could not finish import")
                progress("Imported " + filename + ". Return to Music or Images to use it.")
            } catch (e: Exception) {
                part.delete()
                progress("Import failed: " + (e.message ?: "unknown error"))
            } finally { busy.set(false) }
        }
    }

    fun onExportFolder(tree: Uri?) {
        val selected = exportModel
        exportModel = null
        if (tree == null || selected == null) { progress("Export cancelled"); return }
        val modelFile = File(directory, selected)
        if (!modelFile.isFile) { progress("Model not found; download it first"); return }
        if (!busy.compareAndSet(false, true)) { progress("Another operation is running"); return }
        abort.set(false)
        executor.execute {
            try {
                val root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
                val dest = DocumentsContract.createDocument(activity.contentResolver, root, "application/octet-stream", selected)
                    ?: error("Could not create the selected file")
                activity.contentResolver.openOutputStream(dest, "w")?.use { output ->
                    FileInputStream(modelFile).use { input ->
                        val buf = ByteArray(128 * 1024)
                        var copied = 0L
                        var lastTick = 0L
                        while (true) {
                            if (abort.get()) { progress("Export interrupted; delete incomplete copy from selected folder"); return@execute }
                            val count = input.read(buf)
                            if (count < 0) break
                            output.write(buf, 0, count)
                            copied += count
                            val now = System.currentTimeMillis()
                            if (now - lastTick > 1500) { progress("Exporting " + human(copied) + " / " + human(modelFile.length())); lastTick = now }
                        }
                    }
                } ?: error("Destination folder not writable")
                progress("Exported " + selected + " to chosen folder")
            } catch (e: Exception) { progress("Export failed: " + (e.message ?: "unknown error")) }
            finally { busy.set(false) }
        }
    }
    fun stop() { abort.set(true); executor.shutdown() }
}
