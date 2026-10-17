# S26 Creative Studio — Local Images + YuE2 Music (v0.5 experimental)

Samsung Galaxy S26 Ultra (12GB RAM), Android 16.

## Images — fully offline after model download

**Recommended model:** Stable Diffusion 1.5, official `v1-5-pruned-emaonly.safetensors` single-file checkpoint (4,265,146,304 bytes, CreativeML Open RAIL-M). Available in **Models → Stable Diffusion 1.5**. This is a conservative memory-first recommendation for this phone; it is **not** necessarily the highest-quality model the hardware could run, but its 512×512 architecture, image-to-image and LoRA compatibility make it a good first supported option.

- Open Images after downloading checkpoint.
- Text prompt and optional negative prompt → generate 512×512 image locally (no photos/prompts sent to cloud).
- Choose photo and use edit strength → img2img restyle.
- Paint white over photo in mask panel → selective inpainting (results depend on the base checkpoint; dedicated inpainting checkpoints can improve quality).
- Import **SD1.5-compatible** LoRA `.safetensors` on Images screen; choose from installed list, set strength.
- Save result to Pictures/S26CreativeStudio.
- Inference backend: pinned [stable-diffusion.cpp](https://github.com/leejet/stable-diffusion.cpp), compiled for Android ARM64 CPU. No GPU/NPU acceleration in this version. Jobs may be slow; test on the physical S26 before expecting dependable performance.

### Resources
- 4.27GB checkpoint download, allow additional storage overhead.
- Only one image task at a time; close other heavy apps and do not run YuE2 at the same time.
- Stable Diffusion 1.5 is not a modern instruction-following image edit model. It performs denoising/inpainting, not exact semantic object edits.
- Importing an unrelated SDXL or FLUX model in Models does **not** make it compatible with the SD1.5-specific UI and LoRA presets.

## Music — experimental in-app YuE2
Existing [yue2.cpp](https://github.com/ServeurpersoCom/yue2.cpp) native ARM64 CPU integration is preserved. Download Q5 GGUF and VAE through Models; use **Start built-in YuE2** on Music. No Termux application required. Running entire songs on 12GB RAM has not been tested on the physical device and may be slow or unstable.

## APK builds from GitHub
GitHub Actions builds both native backends and the Android app, then attaches an APK ZIP artifact. This is a debug build; the signing key can change between runs. **Export models before uninstalling an older app**, because Android deletes private app model files when uninstalled. Update installs may fail if signatures differ.

## Security and privacy
Image neural inference is local. The app needs Internet only for downloading model weights and any optional YuE2 API connections. No cloud AI image generation is launched from the Images tab. Models may have license restrictions; the official SD1.5 checkpoint uses CreativeML Open RAIL-M. YuE2 weights typically have non-commercial restrictions.

## Development status
Compilation of native libraries in CI does not establish successful on-phone inference. Verify small generations first and report errors with model filename and memory/thermal status.
