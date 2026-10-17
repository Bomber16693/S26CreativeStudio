# S26 Creative Studio

Native Android experimental creative app targeted at Samsung Galaxy S26 Ultra (12GB).

## Included in the APK
- **Music:** style and lyrics inputs, YuE2 (yue2.cpp) local HTTP server connectivity, job submission, polling, download of completed MP3 to Android Music.
- **Photo:** select a photo, enhance saturation, save a PNG to Android Pictures.
- **Video:** select a photo, encode a 5-second H.264 MP4 with gentle zoom/pan to Android Movies.
- **Setup:** available memory and temperature status.

## Not included yet
- Local AI image diffusion, Neodragon/Wan AI video generation, bundled YuE2 model weights or automatic Termux installation. AI music requires an independently installed and running compatible yue2.cpp server (default http://127.0.0.1:8087). Performance on this phone is not yet validated.
- First APK is a **prototype**, not production-ready. It has not been tested on a physical S26 Ultra.

## Phone-only build and install
1. Open this repository's **Actions** tab and select **Build Android APK**.
2. Open the latest successful run (or manually choose Run workflow).
3. Download the **S26CreativeStudio-debug-apk** artifact, unzip it, install `app-debug.apk` on your phone.
4. Check warning screens and grant permission to install only from the file manager if you trust this source.

## Privacy
No cloud AI providers, analytic trackers or stored API keys. YuE2 uses a local port over HTTP; keep it bound to loopback 127.0.0.1 rather than exposed to the network. Third-party model weight licences apply. This is unsigned release / debug signed APK for testing.
