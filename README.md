# S26 Creative Studio 0.4 — Image & Music MVP

**Samsung Galaxy S26 Ultra (12 GB RAM).**

## Music (on-device, experimental)
- The app packages an ARM64 yue2.cpp native library (from pinned upstream Git commit).
- Download/import YuE2 Q5 backbone + 530MB VAE via **Models**. Press **Start built-in YuE2** under Music; you don't need Termux.
- Music requests run through the local loopback service hosted **inside the app process**. CPU only; successful music generation on S26 Ultra is **not verified** and could be slow or fail due to memory.
- Old YuE2 backend supports both instrumental/lyrics and MP3 exports. Builds are verified by CI only, not on physical device.

## Images
- **Offline**: select a photo; adjust brightness and saturation, then export PNG.
- **Optional free online AI image generation**: text-to-image, image-to-image and masked inpainting through [AI Horde](https://aihorde.net/). This is **NOT** an offline diffusion engine.
- **Cloud LoRA**: supply a compatible CivitAI LoRA name/version in the image studio, along with model name and LoRA weight. The remote worker must support and be able to fetch it.
- Anonymous API key: ten zeros. Free shared volunteer workers may have long queues; give a registered key for priority.
- **Photo privacy**: photos used for AI edits are transmitted to volunteers only after selecting the explicit source photo consent switch. Anonymous requests may be shared by AI Horde regardless of the app's sharing preference.
- The app has no installed Stable Diffusion NPU/Vulkan runtime yet, so local generative image models and local LoRA inference are **not supported**. Existing model downloads are file management only.

## Install
GitHub Actions downloads the pinned upstream YuE2/GGML source, cross-compiles an arm64 shared library with the NDK, then builds `app-debug.apk`.
Open the newest successful **Build Android APK** run in GitHub Actions and download its artifact ZIP. No PC required.

### IMPORTANT: signed APK updates
GitHub Actions debug signing keys can differ between builds. Before uninstalling any older version, **export your downloaded GGUF model files** from the Models tab to Documents/Downloads. Uninstalling an app deletes its private model data. After installing, re-import the files. 
This is a testing APK, not a signed release-track installer.
