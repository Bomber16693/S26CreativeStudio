package com.bomber16693.s26creativestudio

import android.app.Activity
import android.app.ActivityManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.widget.*
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class LocalImagesActivity : Activity() {
    private val background = Color.rgb(9, 14, 26)
    private val mint = Color.rgb(112, 229, 222)
    private val paper = Color.rgb(233, 242, 249)
    private val worker = Executors.newSingleThreadExecutor()
    private val running = AtomicBoolean(false)
    private val engine = LocalDiffusion()
    private lateinit var body: LinearLayout
    private lateinit var status: TextView
    private lateinit var output: ImageView
    private lateinit var painter: PaintMaskView
    private lateinit var modelSpinner: Spinner
    private lateinit var loraSpinner: Spinner
    private lateinit var originalToggle: CheckBox
    private lateinit var prompt: EditText
    private lateinit var negative: EditText
    private lateinit var strength: SeekBar
    private lateinit var steps: SeekBar
    private lateinit var weight: SeekBar
    private var source: Bitmap? = null
    private val models by lazy { File(getExternalFilesDir(null) ?: filesDir, "models").apply { mkdirs() } }
    private val loras by lazy { File(models, "loras").apply { mkdirs() } }
    private var checkpoints = emptyList<File>()
    private var adapters = emptyList<File>()
    private val ticker = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            if (running.get()) {
                val n = engine.progress()
                if (n > 0) status.text = "Rendering locally: $n% — keep this screen open"
                ticker.postDelayed(this, 1100)
            }
        }
    }
    private fun dp(n: Int) = (n * resources.displayMetrics.density + .5f).toInt()
    private fun text(s: String, size: Float = 15f) = TextView(this).apply {
        text=s; textSize=size; setTextColor(paper); setPadding(0,dp(8),0,dp(8))
    }
    private fun add(v: View) { body.addView(v,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(7) }) }
    private fun button(s: String, action: ()->Unit) = Button(this).apply {
        text=s; isAllCaps=false; setOnClickListener { action() }
    }
    private fun message(s: String) = runOnUiThread { status.text=s.take(400) }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.statusBarColor=background; window.navigationBarColor=background
        val scroll=ScrollView(this).apply { setBackgroundColor(background); isFillViewport=true }
        body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(16),dp(12),dp(16),dp(30)) }
        if (android.os.Build.VERSION.SDK_INT>=30) body.setOnApplyWindowInsetsListener { view,insets->
            val bars=insets.getInsets(WindowInsets.Type.systemBars())
            view.setPadding(dp(16)+bars.left,dp(12)+bars.top,dp(16)+bars.right,dp(30)+bars.bottom)
            insets
        }
        scroll.addView(body);setContentView(scroll)
        add(text("LOCAL IMAGE STUDIO",23f).apply { setTextColor(mint) })
        status=text("Everything on this screen runs on your phone. No cloud photo uploads.",13f);add(status)
        add(text("Model: Stable Diffusion 1.5 • 512×512 • CPU",15f))
        add(text("Choose your downloaded SD1.5 checkpoint in Models. 12GB RAM profile; rendering can be slow and warm the phone.",12f))
        modelSpinner=Spinner(this);add(modelSpinner)
        add(button("Refresh installed models") { refreshFiles() })
        add(button("Open Models tab") { finish() })
        add(text("Prompt",17f))
        prompt=EditText(this).apply {hint="Describe the photo you want or the edit"; setTextColor(paper);setHintTextColor(Color.LTGRAY);minLines=3}
        add(prompt)
        negative=EditText(this).apply {hint="Negative prompt (optional)";setTextColor(paper);setHintTextColor(Color.LTGRAY)}
        add(negative)
        add(text("Select a photo for restyling or painting a replacement mask",13f))
        add(button("Choose image from Gallery") { openDocument(700, "image/*") })
        output=ImageView(this).apply {
            minimumHeight=dp(180); setBackgroundColor(Color.rgb(33,44,59)); adjustViewBounds=true
        }
        add(output)
        originalToggle=CheckBox(this).apply {text="Use selected photo as AI input";setTextColor(paper);isChecked=true}
        add(originalToggle)
        add(text("Mask (white = replace this area; black = preserve)",13f))
        painter=PaintMaskView(this);add(painter)
        add(button("Clear mask") { painter.clear() })
        add(text("Denoise/edit strength: low keeps original, high changes more",12f))
        strength=SeekBar(this).apply {max=95;progress=60}
        add(strength)
        add(text("Steps: 4–25 (default 12, more steps take longer)",12f))
        steps=SeekBar(this).apply {max=21;progress=8}
        add(steps)
        add(text("Local SD1.5 LoRA (optional)",17f))
        loraSpinner=Spinner(this);add(loraSpinner)
        add(button("Import SD1.5 LoRA (.safetensors)") { openDocument(701,"*/*") })
        add(text("LoRA strength",12f))
        weight=SeekBar(this).apply {max=200;progress=75}
        add(weight)
        add(button("Generate 512×512 locally") { generate() })
        add(button("Cancel AI generation") { engine.cancel(); message("Stop requested") })
        add(text("Offline brightness/saturation adjustments remain available in Samsung Gallery. This AI engine operates wholly offline after model download.",12f))
        refreshFiles()
    }
    private fun openDocument(code: Int, type: String) {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            this.type=type;addCategory(Intent.CATEGORY_OPENABLE)
        },code)
    }
    private fun refreshFiles() {
        checkpoints=models.listFiles()?.filter {
            it.isFile && it.name.endsWith(".safetensors",true) && it.length()>1_000_000_000L
        }?.sortedBy { it.name } ?: emptyList()
        adapters=loras.listFiles()?.filter { it.isFile && it.name.endsWith(".safetensors",true) }?.sortedBy { it.name } ?: emptyList()
        modelSpinner.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,
            if(checkpoints.isEmpty()) listOf("No SD1.5 model installed") else checkpoints.map { it.name })
        loraSpinner.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,
            listOf("No LoRA")+adapters.map { it.name })
        if(checkpoints.isEmpty())message("Download SD1.5 model in Models tab first")
    }
    private fun rgb(bmp: Bitmap): ByteArray {
        val small=Bitmap.createScaledBitmap(bmp,512,512,true)
        val pixels=IntArray(512*512)
        small.getPixels(pixels,0,512,0,0,512,512)
        val bytes=ByteArray(512*512*3)
        for(i in pixels.indices) {
            bytes[i*3]=(pixels[i] shr 16).toByte()
            bytes[i*3+1]=(pixels[i] shr 8).toByte()
            bytes[i*3+2]=pixels[i].toByte()
        }
        if(small!==bmp)small.recycle()
        return bytes
    }
    private fun generate() {
        if (running.get()) { message("Generation already running");return }
        val model=checkpoints.getOrNull(modelSpinner.selectedItemPosition)
        if(model==null){message("No compatible checkpoint installed. Download Stable Diffusion 1.5 in Models.");return}
        if (!LocalDiffusion.supported) {message("This APK does not contain the compiled SD inference library");return}
        if(prompt.text.toString().isBlank()) {message("Please enter a prompt");return}
        val ram=ActivityManager.MemoryInfo()
        (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(ram)
        val thermal=(getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus
        if (ram.lowMemory || ram.availMem<2_500_000_000L || thermal>=PowerManager.THERMAL_STATUS_SEVERE) {
            message("Insufficient free memory or phone is hot; try again later");return
        }
        val lora=adapters.getOrNull(loraSpinner.selectedItemPosition-1)
        val src=if(originalToggle.isChecked)source else null
        if(painter.masked && src==null){message("Select a photo before inpainting");return}
        val maskBytes=if (painter.masked) painter.mono() else null
        val rgbBytes=src?.let(::rgb)
        val idea=prompt.text.toString()
        val neg=negative.text.toString()
        val power=(strength.progress.coerceAtLeast(10))/100f
        val count=steps.progress+4
        val strengthLora=weight.progress/100f
        if(!running.compareAndSet(false,true))return
        message("Loading local SD model — phone CPU only...")
        ticker.post(tick)
        worker.execute {
            try{
                val pixels=engine.generate(model.absolutePath,idea,neg,rgbBytes,maskBytes,
                    power,count,System.currentTimeMillis(),lora?.absolutePath,strengthLora)
                val result=Bitmap.createBitmap(pixels,512,512,Bitmap.Config.ARGB_8888)
                try {
                    val values=ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME,"s26_local_"+System.currentTimeMillis()+".png")
                        put(MediaStore.MediaColumns.MIME_TYPE,"image/png")
                        put(MediaStore.MediaColumns.RELATIVE_PATH,"Pictures/S26CreativeStudio")
                        put(MediaStore.MediaColumns.IS_PENDING,1)
                    }
                    val uri=contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)
                        ?:error("Could not create gallery output")
                    try {
                        contentResolver.openOutputStream(uri)?.use { itStream->
                            if(!result.compress(Bitmap.CompressFormat.PNG,100,itStream))error("Failed PNG encoding")
                        } ?:error("Cannot write image")
                        contentResolver.update(uri,ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING,0) },null,null)
                        runOnUiThread { output.setImageURI(uri) }
                        message("Saved entirely locally to Gallery / Pictures/S26CreativeStudio")
                    }catch(e:Exception){contentResolver.delete(uri,null,null);throw e}
                }finally{result.recycle()}
            }catch(e:Throwable){message("Local generation: "+(e.message?:"native error"))}
            finally{ running.set(false);ticker.removeCallbacks(tick)}
        }
    }
    @Deprecated("Gallery picker")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){
        super.onActivityResult(requestCode,resultCode,data)
        if(resultCode!=RESULT_OK||data?.data==null)return
        val uri=data.data!!
        if(requestCode==700) {
            try {
                val options=BitmapFactory.Options().apply { inJustDecodeBounds=true }
                contentResolver.openInputStream(uri)?.use {BitmapFactory.decodeStream(it,null,options)}
                var sample=1
                while(maxOf(options.outWidth,options.outHeight)/sample>1600)sample*=2
                val bmp=contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply {inSampleSize=sample})
                }?:error("Unable to read image")
                source?.recycle();source=bmp
                output.setImageBitmap(bmp);painter.setPhoto(bmp)
                message("Photo loaded. Use strength for restyle, paint white for selective editing.")
            }catch(e:Exception){message("Photo load failed: "+e.message)}
        }else if(requestCode==701) {
            val cursor=contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)
            var name="";cursor?.use {if(it.moveToFirst())name=it.getString(0)}
            if(!name.matches(Regex("[A-Za-z0-9._-]{4,120}"))||!name.endsWith(".safetensors",true)) {
                message("Select a compatible SD1.5 .safetensors LoRA");return
            }
            if(running.get()){message("Finish rendering first");return}
            worker.execute {
                try {
                    val target=File(loras,name)
                    if (target.exists())error("LoRA already imported")
                    contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(target).use { output ->
                            val buffer=ByteArray(128*1024);var total=0L
                            while(true) {
                                val count=input.read(buffer);if(count<0)break
                                total+=count
                                if(total>1_500_000_000L)error("LoRA file too large")
                                output.write(buffer,0,count)
                            }
                        }
                    }?:error("Unable to open LoRA")
                    message("Imported LoRA: $name")
                    runOnUiThread{refreshFiles()}
                }catch(e:Exception){File(loras,name).delete();message("LoRA import error: "+e.message)}
            }
        }
    }
    override fun onDestroy() {
        engine.cancel();ticker.removeCallbacks(tick)
        source?.recycle(); worker.shutdown()
        super.onDestroy()
    }
}

/** Editable white-on-black mask, matching stable-diffusion.cpp 1-channel mask input. */
private class PaintMaskView(activity:Activity):View(activity) {
    private var photograph:Bitmap?=null
    private val image=Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888)
    private val board=Canvas(image)
    private val brush=Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color=Color.WHITE;strokeWidth=24f;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND;style=Paint.Style.STROKE
    }
    private var path=Path()
    var masked=false;private set
    init{clear()}
    fun clear(){board.drawColor(Color.BLACK);masked=false;path.reset();invalidate()}
    fun setPhoto(b:Bitmap){photograph=b;clear()}
    fun mono():ByteArray{
        val array=IntArray(512*512);image.getPixels(array,0,512,0,0,512,512)
        return ByteArray(array.size) { if ((array[it] and 0xff)>127) 255.toByte() else 0 }
    }
    override fun onMeasure(w:Int,h:Int){
        val width=MeasureSpec.getSize(w);setMeasuredDimension(width,width.coerceAtMost((310*resources.displayMetrics.density).toInt()))
    }
    override fun onDraw(canvas:Canvas){
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(32,43,61))
        photograph?.let { canvas.drawBitmap(it,null,Rect(0,0,width,height),Paint(Paint.FILTER_BITMAP_FLAG)) }
        val px=IntArray(512*512);image.getPixels(px,0,512,0,0,512,512)
        for(i in px.indices)px[i]=if((px[i] and 0xff)>127)Color.argb(150,255,255,255) else 0
        val overlay=Bitmap.createBitmap(px,512,512,Bitmap.Config.ARGB_8888)
        canvas.drawBitmap(overlay,null,Rect(0,0,width,height),Paint(Paint.FILTER_BITMAP_FLAG))
        overlay.recycle()
    }
    override fun onTouchEvent(e:MotionEvent):Boolean{
        if(photograph==null)return false
        val x=e.x.coerceIn(0f,width.toFloat())*512f/width.coerceAtLeast(1)
        val y=e.y.coerceIn(0f,height.toFloat())*512f/height.coerceAtLeast(1)
        when(e.actionMasked){
            MotionEvent.ACTION_DOWN -> {path=Path();path.moveTo(x,y);board.drawCircle(x,y,12f,Paint().apply {color=Color.WHITE;isAntiAlias=true});masked=true}
            MotionEvent.ACTION_MOVE -> {path.lineTo(x,y);board.drawPath(path,brush)}
            MotionEvent.ACTION_UP -> {path.lineTo(x,y);board.drawPath(path,brush);path.reset()}
            else -> return false
        }
        invalidate();return true
    }
}
