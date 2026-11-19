# WhatPass

App Android para ver pases Apple Wallet (.pkpass). Publicada en Google Play por Glocal Saino. **Repo público.**

- Antes se llamaba **MiWallet**; se renombró a WhatPass porque ese nombre ya lo usaban otros. Por eso el paquete sigue siendo `com.glocalsaino.miwallet` (no cambiarlo: Google Play lo trata como app distinta) y hay clases como `MiWalletFirebaseService`.
- Basada en PassAndroid (ligi). Integración con PassSource: push FCM + cliente PassKit Web Service (`PassUpdateManager`, `PassReRegisterWorker`); importación por URL y QR (`QrImportActivity`).

- Kotlin, paquete `com.glocalsaino.miwallet`, código en `android/src/main`. Backend en `backend/` (Firebase Functions).
- Claves de firma en `~/keystores/` (nunca en el repo).
- Renderizado: `PassViewActivity` usa `PassViewPKFragment` para tarjetas de embarque y `PassViewFragment` para el resto. Layouts: `main_field_item` (primaryFields), `secondary_field_item`, `auxiliary_field_item`, `strip_overlay_field_item` (primaryFields sobre la tira en coupon/storeCard/event).
- Pases genéricos: etiqueta de primaryFields ENCIMA del valor; storeCard mantiene el valor encima.
- 1.1.12 (versionCode 27): arreglado "Camera is being used after Camera.release()" en `QrImportActivity` / `PassReaderActivity` (cámara se abre en onResume y se libera en onPause).
- Compilar: `./gradlew :android:compileDebugKotlin --console=plain`. Pruebas en móvil real con adb.
