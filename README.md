# AI PHONE DR

A web-based Android app packaged with a native Android WebView shell.

## Stack
- HTML, CSS and JavaScript UI
- Android WebView wrapper (Java)
- Gradle / Android Gradle Plugin
- GitHub Actions builds a debug APK

## Build
1. Open the **Actions** tab.
2. Select **Build AI PHONE DR APK**.
3. Select **Run workflow** on the main branch.
4. Open the completed workflow run and download the `AI-PHONE-DR-debug-apk` artifact.
5. Extract the ZIP and install `app-debug.apk` on an Android device. Allow installation from that source if Android asks.

A push to main that changes the web UI or Android project also triggers a build.

## Security and capabilities
The WebView loads a bundled local HTML asset. No login, password, API key or access token is embedded in the app. Android controls privileged operations. The initial dashboard provides informational UI and does not claim to perform hidden cleaning, RAM boosting or battery manipulation.
