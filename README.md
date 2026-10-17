# S26 Creative Studio v0.3.0: embedded YuE2 preview

Runs upstream yue2.cpp as a native ARM64 shared library inside Android. No separate Termux process.

## Usage
Download YuE2 Q5 backbone and VAE in Models, open Music and press **Start built-in YuE2**. The engine loads model weights locally and handles jobs using its own internal loopback 127.0.0.1:8087 server. Then choose style and lyrics and tap Generate.

## Status & limitations
- **Native engine integration is experimental**. A successful cloud build does not prove that 12GB Android RAM can complete YuE2 song generation.
- CPU only, no Snapdragon NPU backend. Expect slow processing, memory/thermal limitations, and possible model load failures.
- YuE2 Q5 and VAE require ~3.2GB storage, plus memory for weights/KV cache.
- YuE2 .gguf weights aren't bundled; download via Models. Open-source yue2.cpp pinned to 8af01740686824ffd4a1839a457954f42e2a8ce7.
- APK debug keys might differ across cloud runs, requiring uninstall before installing a different build; uninstalling may delete app-specific model downloads.
- Generative photo/video backends and image LoRA support still remain unimplemented.

## Build
GitHub Actions fetches the pinned yue2.cpp + GGML submodule, patches its CMake with scripts/prepare_native.py, builds arm64-v8a libyue2mobile.so with NDK27.2. Licence for yue2.cpp is MIT; model weights have CC BY-NC restrictions.
