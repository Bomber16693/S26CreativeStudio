#include <jni.h>
#include <atomic>
#include <algorithm>
#include <cstdint>
#include <string>
#include <vector>
#include <mutex>
#include "stable-diffusion.h"

// Uses stable-diffusion.cpp CPU backend. Keeps all neural inference inside app.
namespace {
std::atomic<sd_ctx_t*> current{nullptr};
std::atomic<int> completed{0};
std::atomic<int> total{0};
std::mutex generation_lock;

void progress_callback(int step, int steps, float, void*) {
    completed.store(step);
    total.store(steps);
}
void failure(JNIEnv* env, const char* message) {
    jclass clazz = env->FindClass("java/lang/IllegalStateException");
    if (clazz) env->ThrowNew(clazz, message);
}
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_bomber16693_s26creativestudio_LocalDiffusion_generateNative(
        JNIEnv* env, jobject,
        jstring model, jstring prompt, jstring negative,
        jbyteArray rgbInput, jbyteArray monoMask,
        jfloat strength, jint steps, jlong seed,
        jstring loraPath, jfloat loraStrength) {
    std::lock_guard<std::mutex> guard(generation_lock);
    completed.store(0);
    total.store(0);
    if (!model || !prompt || !negative) { failure(env, "Missing required model or prompt"); return nullptr; }
    const char* modelChars = env->GetStringUTFChars(model, nullptr);
    const char* promptChars = env->GetStringUTFChars(prompt, nullptr);
    const char* negChars = env->GetStringUTFChars(negative, nullptr);
    std::string modelFile(modelChars), positive(promptChars), exclude(negChars);
    env->ReleaseStringUTFChars(model, modelChars);
    env->ReleaseStringUTFChars(prompt, promptChars);
    env->ReleaseStringUTFChars(negative, negChars);
    std::string loraFile;
    if (loraPath) {
        const char* chars = env->GetStringUTFChars(loraPath, nullptr);
        loraFile = chars;
        env->ReleaseStringUTFChars(loraPath, chars);
    }
    std::vector<uint8_t> initPixels;
    std::vector<uint8_t> maskPixels;
    if (rgbInput) {
        const jsize n = env->GetArrayLength(rgbInput);
        if (n != 512*512*3) { failure(env, "Input must be 512x512 RGB"); return nullptr; }
        initPixels.resize(static_cast<size_t>(n));
        env->GetByteArrayRegion(rgbInput, 0, n, reinterpret_cast<jbyte*>(initPixels.data()));
    }
    if (monoMask) {
        const jsize n = env->GetArrayLength(monoMask);
        if (initPixels.empty() || n != 512*512) {
            failure(env, "Mask requires 512x512 grayscale and an input image"); return nullptr;
        }
        maskPixels.resize(static_cast<size_t>(n));
        env->GetByteArrayRegion(monoMask, 0, n, reinterpret_cast<jbyte*>(maskPixels.data()));
    }

    sd_ctx_params_t options{};
    sd_ctx_params_init(&options);
    options.model_path = modelFile.c_str();
    options.n_threads = 4;
    options.backend = "CPU";
    options.enable_mmap = true;
    options.flash_attn = true;
    sd_set_progress_callback(progress_callback, nullptr);
    sd_ctx_t* ctx = new_sd_ctx(&options);
    if (!ctx) { failure(env, "Could not load SD 1.5 model (file, RAM, or format)"); return nullptr; }
    current.store(ctx);
    sd_img_gen_params_t params{};
    sd_img_gen_params_init(&params);
    params.prompt = positive.c_str();
    params.negative_prompt = exclude.c_str();
    params.width = 512;
    params.height = 512;
    params.batch_count = 1;
    params.seed = static_cast<int64_t>(seed);
    params.sample_params.sample_steps = std::clamp(static_cast<int>(steps), 4, 25);
    params.sample_params.sample_method = EULER_A_SAMPLE_METHOD;
    params.sample_params.guidance.txt_cfg = 7.0f;
    params.strength = std::clamp(strength, 0.1f, 1.0f);
    params.vae_tiling_params.enabled = true;
    params.vae_tiling_params.tile_size_w = 256;
    params.vae_tiling_params.tile_size_h = 256;
    if (!initPixels.empty()) {
        params.init_image = {512u, 512u, 3u, initPixels.data()};
    }
    if (!maskPixels.empty()) {
        params.mask_image = {512u, 512u, 1u, maskPixels.data()};
    }
    sd_lora_t lora{};
    if (!loraFile.empty()) {
        lora.path = loraFile.c_str();
        lora.multiplier = std::clamp(loraStrength, 0.0f, 2.0f);
        params.loras = &lora;
        params.lora_count = 1;
    }
    sd_image_t* images = nullptr;
    int number = 0;
    const bool ok = generate_image(ctx, &params, &images, &number);
    current.store(nullptr);
    free_sd_ctx(ctx);
    if (!ok || !images || number == 0 || !images[0].data) {
        if (images) free_sd_images(images, number);
        failure(env, "Local image generation failed (model or memory)");
        return nullptr;
    }
    sd_image_t& pic = images[0];
    if (pic.channel < 3 || pic.width != 512 || pic.height != 512) {
        free_sd_images(images, number);
        failure(env, "Unexpected diffusion output dimensions");
        return nullptr;
    }
    const int pixels = static_cast<int>(pic.width * pic.height);
    jintArray output = env->NewIntArray(pixels);
    if (!output) { free_sd_images(images, number); return nullptr; }
    std::vector<jint> argb(static_cast<size_t>(pixels));
    for (int i=0;i<pixels;i++) {
        const uint8_t* p = pic.data + static_cast<size_t>(i)*pic.channel;
        argb[static_cast<size_t>(i)] = static_cast<jint>(
            0xff000000u | (static_cast<uint32_t>(p[0])<<16) |
            (static_cast<uint32_t>(p[1])<<8) | static_cast<uint32_t>(p[2]));
    }
    env->SetIntArrayRegion(output, 0, pixels, argb.data());
    free_sd_images(images, number);
    return output;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_bomber16693_s26creativestudio_LocalDiffusion_progressNative(JNIEnv*, jobject) {
    const int max = total.load();
    return max <= 0 ? 0 : completed.load()*100/max;
}
extern "C" JNIEXPORT void JNICALL
Java_com_bomber16693_s26creativestudio_LocalDiffusion_cancelNative(JNIEnv*, jobject) {
    sd_ctx_t* ctx = current.load();
    if (ctx) sd_cancel_generation(ctx, SD_CANCEL_ALL);
}
