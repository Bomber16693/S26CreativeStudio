package com.bomber16693.s26creativestudio

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.os.PowerManager
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Embeds upstream yue2.cpp directly into Android via JNI; no Termux app or external service. */
class NativeYuE(private val activity: Activity,private val status:(String)->Unit){
    private val worker=Executors.newSingleThreadExecutor()
    private val running=AtomicBoolean(false)
    private val loaded by lazy {
        try{System.loadLibrary("yue2mobile");true}
        catch(e:Throwable){status("Embedded YuE2 library load failed: "+e.message);false}
    }
    private external fun runServer(model:String,vae:String):Int
    private external fun stopServer()
    private val models by lazy { File(activity.getExternalFilesDir(null)?:activity.filesDir,"models") }
    private fun modelPaths():Pair<File,File>{
        val model=listOf("YuE2-3B-Q5_K_M.gguf","YuE2-3B-Q6_K.gguf","YuE2-3B-Q8_0.gguf")
            .map{File(models,it)}.firstOrNull{it.isFile&&it.length()>2_000_000_000L}
            ?:error("Download the YuE2 Q5 or Q8 backbone in Models")
        val vae=File(models,"YuE2-Vae-F32.gguf")
        if(!vae.isFile||vae.length()<400_000_000L)error("Download the YuE2 VAE model in Models")
        return model to vae
    }
    fun start(){
        if(!loaded)return
        if(!running.compareAndSet(false,true)){status("YuE2 is already starting or running");return}
        val pair=try{
            val p=modelPaths()
            val mi=ActivityManager.MemoryInfo()
            (activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
            if(mi.lowMemory||mi.availMem<3_500_000_000L)error("Insufficient free memory to load YuE2")
            val therm=(activity.getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus
            if(therm>=PowerManager.THERMAL_STATUS_SEVERE)error("Phone is too hot to start YuE2")
            p
        }catch(e:Exception){running.set(false);status(e.message?:"Cannot start YuE2");return}
        status("Loading YuE2 inside this app (CPU mode)…")
        worker.execute {
            try {status("YuE2 engine exited: "+runServer(pair.first.absolutePath,pair.second.absolutePath))}
            catch(e:Throwable){status("YuE2 native error: "+e.message)}
            finally{running.set(false)}
        }
    }
    fun ready():Boolean=try{
        val c=URL("http://127.0.0.1:8087/health").openConnection() as HttpURLConnection
        try{c.connectTimeout=1000;c.readTimeout=1000;c.responseCode==200}
        finally{c.disconnect()}
    }catch(_:Exception){false}
    fun ensureStarted(){
        if(ready())return
        start()
        if(!running.get())error("Unable to start embedded YuE2")
        repeat(180){
            if(ready())return
            if(!running.get())error("YuE2 native engine exited before it became ready")
            Thread.sleep(1000)
        }
        error("YuE2 engine startup timed out")
    }
    fun stop(){if(loaded&&running.get())try{stopServer()}catch(_:Throwable){}}
}
