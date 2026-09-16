# Emami Coin Calculator — Android 14+

Persian/RTL Android app for calculating the intrinsic value and premium/bubble of the Iranian Emami gold coin and the USD/Toman exchange rate implied by the coin price.

## Features
- Android 14+ (`minSdk 34`), target/compile SDK 35
- Kotlin + Jetpack Compose + Material 3
- MVVM-style ViewModel state
- Room calculation history and cached last-good snapshot
- Retrofit/OkHttp provider layer
- Live XAU/USD from `api.gold-api.com`
- Best-effort live USD/IRR, Emami price and reported Emami premium from TGJU public profile pages
- Manual USD/Toman and Emami price entry always available
- Internal IRR arithmetic; Toman at the UI boundary
- Offline/cache fallback

## Calculations
- Emami nominal weight: 8.133 g
- Fineness: 0.900
- Pure gold: 7.3197 g
- Troy ounce: 31.1034768 g

`intrinsicUSD = XAUUSD / 31.1034768 * 7.3197`

`theoreticalIRR = intrinsicUSD * marketUsdIRR`

`premiumIRR = coinMarketIRR - theoreticalIRR`

`premiumPct = premiumIRR / theoreticalIRR * 100`

`impliedUsdIRR = coinMarketIRR / intrinsicUSD`

## Data provider note
TGJU's official OpenAPI is the preferred production integration when licensed credentials are available. The included TGJU reader is isolated in `data/remote/TgjuPublicProvider`, reads public profile pages at a low request rate, and falls back to cache/manual input if parsing or networking fails. Replace that single provider with an authorized TGJU/Bonbast API implementation for commercial distribution.

## Build
Open with a current Android Studio, or use Gradle 8.9 and Android SDK 35:

```bash
gradle test assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

The included `.github/workflows/android.yml` workflow installs Gradle 8.9, runs tests, builds the debug APK, and uploads it as a CI artifact.
