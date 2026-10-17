# S26 Creative Studio — 0.2.0

Offline-first experimental Android app targeting Galaxy S26 Ultra (12GB RAM).

## What works in this prototype
- **Models:** downloads genuine YuE2 GGUF weights from Hugging Face to private app files, with progress, pause/resume, basic GGUF header/size checks, storage checks, deletion, and an option to export to a chosen Android folder so Termux can access them. Also supports custom HTTPS model file downloads. AI model inference is not built in.
- **Music:** prompt and lyrics forms, HTTP connectivity to a separately running yue2.cpp service, job polling. Its response decoder is experimental and may require fixing for multipart replies.
- **Photo:** enhance saturation of an existing image and export PNG.
- **Video:** encode a 5-second zoomed MP4 from an existing photo.
- **Setup:** reports available RAM/thermal status.

## Known limitations
- **No built-in neural video or photo generator.** Downloading those model weights cannot make them run without an NPU-compatible runtime and pipeline.
- **YuE2 needs yue2.cpp running independently,** e.g. in Termux. This APK only downloads and exports files. Q5 requires a supported yue2.cpp build and a VAE; download both. On-device speed and compatibility have not been tested.
- Large downloads occupy storage and may stop when the app is backgrounded. Reopening lets you resume partial files. Keep app in foreground while downloading.
- Some model licences prohibit commercial usage. Consult each model card.
- Debug builds are test APKs, not Play Store releases, and may show unrecognised-developer warnings.

## Phone-only installation
Open GitHub Actions and download the APK artifact from the newest green **Build Android APK** run, unzip, then install app-debug.apk using Samsung My Files. This build targets Android 16 (API 36), rather than the obsolete Android 12L target that caused Play Protect's old-target warning.

## Security
The model downloader restricts downloads to HTTPS, disallows embedded URL passwords, and stores files in app-specific storage until you explicitly export them. Its YuE2 localhost HTTP server should remain bound to loopback. No cloud account credentials included.

### Model sources
- [YuE2 GGUF](https://huggingface.co/Serveurperso/YuE2-GGUF) (CC BY-NC 4.0)
- [yue2.cpp](https://github.com/ServeurpersoCom/yue2.cpp) (engine source)
- [Nightmare Mobile](https://github.com/AbrahamPaulJ/nightmare-mobile) (separate image/video engine, **not** integrated into this app).
