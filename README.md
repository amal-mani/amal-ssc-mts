# MTS Study (phone app)

SSC MTS study app: daily plan, Falcon course schedule, lesson tests, weekly tests,
current affairs, daily mock, shortcuts and SSC updates.

## Download for Android

**[Download MTS-Study.apk](https://github.com/amal-mani/amal-ssc-mts/releases/latest/download/MTS-Study.apk)**

1. Tap the link above on your phone.
2. Open the downloaded file and tap **Install**.
3. If Android asks, allow **Install unknown apps** for Chrome (or your file app).

The app works offline. When the phone is online it downloads the new daily
current affairs, mock, lesson tests and tricks (added every morning by 5 AM)
from the `data/` folder of this repository.

## Folders
- `android/` – the Android app (the study app files are in `app/src/main/assets/www/`)
- `data/` – daily updated files the app downloads
- `.github/workflows/build-apk.yml` – builds the APK on GitHub after every change
