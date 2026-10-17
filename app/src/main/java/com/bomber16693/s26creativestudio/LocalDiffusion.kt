package com.bomber16693.s26creativestudio

/** Stable Diffusion 1.5 runs in-process using an ARM64 shared library and downloaded checkpoint. */
class LocalDiffusion {
    companion object {
        val supported: Boolean by lazy {
            try { System.loadLibrary("s26sd"); true } catch (_: Throwable) { false }
        }
    }
    private external fun generateNative(
        model: String, prompt: String, negative: String,
        initRgb: ByteArray?, mask: ByteArray?,
        strength: Float, steps: Int, seed: Long,
        loraPath: String?, loraStrength: Float
    ): IntArray?
    private external fun progressNative(): Int
    private external fun cancelNative()
    fun generate(
        model: String, prompt: String, negative: String,
        initRgb: ByteArray?, mask: ByteArray?,
        strength: Float, steps: Int, seed: Long,
        loraPath: String?, loraStrength: Float
    ): IntArray {
        check(supported) { "Native Stable Diffusion engine is not available in this APK" }
        return generateNative(model, prompt, negative, initRgb, mask, strength, steps, seed, loraPath, loraStrength)
            ?: error("The local model returned no image")
    }
    fun progress(): Int = if (supported) progressNative() else 0
    fun cancel() { if (supported) cancelNative() }
}
