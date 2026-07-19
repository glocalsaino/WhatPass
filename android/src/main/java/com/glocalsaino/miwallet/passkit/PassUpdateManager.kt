package com.glocalsaino.miwallet.passkit

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.glocalsaino.miwallet.model.InputStreamWithSource
import com.glocalsaino.miwallet.model.PassStore
import com.glocalsaino.miwallet.model.pass.Pass
import com.glocalsaino.miwallet.ui.UnzipPassController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.util.UUID

object PassUpdateManager : KoinComponent {

    private val passStore: PassStore by inject()

    private const val TAG = "PassUpdateManager"
    private const val PREFS_NAME = "miwallet_passkit"
    private const val KEY_FCM_TOKEN = "fcm_token"
    private const val KEY_DEVICE_ID = "device_library_id"

    // Our Cloud Function relay (see /backend in the repo) - PassSource POSTs here
    // instead of pushing to APNs directly, since our push token is an FCM token,
    // not an APNs one. Identifies this app to web services that support multiple
    // non-Apple PassKit clients (e.g. PassSource).
    private const val PASS_CLIENT_NAME = "WhatPass"
    private const val PUSH_SERVICE_URL = "https://pushpassupdate-5xsk5gao2q-uc.a.run.app"

    private val httpClient = OkHttpClient()

    // Permite invocar el registro/desregistro como funciones "fire-and-forget"
    // desde callers que no tienen lifecycleScope propio (p.ej. SearchSuccessCallback).
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── Persistencia de tokens ──────────────────────────────────────────────

    fun saveFcmToken(context: Context, token: String) {
        prefs(context).edit().putString(KEY_FCM_TOKEN, token).apply()
    }

    fun getFcmToken(context: Context): String? =
        prefs(context).getString(KEY_FCM_TOKEN, null)

    fun getDeviceId(context: Context): String {
        val prefs = prefs(context)
        return prefs.getString(KEY_DEVICE_ID, null) ?: run {
            val newId = UUID.randomUUID().toString().replace("-", "")
            prefs.edit().putString(KEY_DEVICE_ID, newId).apply()
            newId
        }
    }

    // ── Registro de un pase en el web service de passsource ────────────────

    suspend fun registerPass(
        context: Context,
        webServiceUrl: String,
        authToken: String,
        passTypeId: String,
        serial: String
    ) = withContext(Dispatchers.IO) {
        val deviceId = getDeviceId(context)
        val fcmToken = getFcmToken(context) ?: return@withContext

        // TEMPORARY (PassSource integration debugging): redacted record of exactly
        // what we register with, to compare against their side's timestamps.
        val redactedToken = if (fcmToken.length > 16) "${fcmToken.take(10)}...${fcmToken.takeLast(6)} (len ${fcmToken.length})" else fcmToken
        Log.i(TAG, "Registering passTypeIdentifier=$passTypeId serialNumber=$serial pushToken=$redactedToken")

        val url = "$webServiceUrl/v1/devices/$deviceId/registrations/$passTypeId/$serial"
        val body = JSONObject().apply {
            put("pushToken", fcmToken)
            put("pushServiceUrl", PUSH_SERVICE_URL)
        }.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "ApplePass $authToken")
            .addHeader("X-Pass-Client", PASS_CLIENT_NAME)
            .post(body)
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                Log.d(TAG, "Register pass $serial → HTTP ${response.code}")
                // 201 = registered, 200 = already registered
                if (!response.isSuccessful && response.code != 200) {
                    Log.w(TAG, "Register failed: ${response.body?.string()}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error registering pass $serial", e)
        }
    }

    // ── Des-registro al borrar un pase ─────────────────────────────────────

    suspend fun unregisterPass(
        context: Context,
        webServiceUrl: String,
        authToken: String,
        passTypeId: String,
        serial: String
    ) = withContext(Dispatchers.IO) {
        val deviceId = getDeviceId(context)
        val url = "$webServiceUrl/v1/devices/$deviceId/registrations/$passTypeId/$serial"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "ApplePass $authToken")
            .addHeader("X-Pass-Client", PASS_CLIENT_NAME)
            .delete()
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                Log.d(TAG, "Unregister pass $serial → HTTP ${response.code}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering pass $serial", e)
        }
    }

    // ── Descarga pase actualizado desde el web service ─────────────────────

    data class PassDownloadResult(val updated: Boolean, val changeMessage: String? = null, val organizationName: String? = null)

    suspend fun downloadUpdatedPass(
        context: Context,
        passTypeId: String,
        serial: String
    ): PassDownloadResult = withContext(Dispatchers.IO) {
        val prefs = prefs(context)
        val webServiceUrl = prefs.getString("webservice_${passTypeId}_${serial}", null)
            ?: return@withContext PassDownloadResult(false)
        val authToken = prefs.getString("authtoken_${passTypeId}_${serial}", null)
            ?: return@withContext PassDownloadResult(false)

        val url = "$webServiceUrl/v1/passes/$passTypeId/$serial"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "ApplePass $authToken")
            .get()
            .build()

        return@withContext try {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bytes = response.body?.bytes() ?: return@withContext PassDownloadResult(false)
                    // Snapshot the fields before the overwrite, so we can diff against the
                    // freshly-installed version below and surface the Apple PassKit
                    // "changeMessage" text for whichever field actually changed - matching
                    // what Apple Wallet shows in its own push banner, instead of a generic
                    // "your pass was updated".
                    val oldPass = passStore.getPassbookForId(UnzipPassController.stableId(passTypeId, serial))
                    val installed = installUpdatedPass(context, passStore, bytes)
                    val newPass = if (installed) {
                        passStore.getPassbookForId(UnzipPassController.stableId(passTypeId, serial))
                    } else null
                    val changeMessage = newPass?.let { buildChangeMessage(oldPass, it) }
                    PassDownloadResult(installed, changeMessage, newPass?.creator)
                } else {
                    Log.w(TAG, "Download failed HTTP ${response.code}")
                    PassDownloadResult(false)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading pass $serial", e)
            PassDownloadResult(false)
        }
    }

    private fun buildChangeMessage(oldPass: Pass?, newPass: Pass): String? {
        if (oldPass == null) return null
        val oldValues = oldPass.fields.associateBy { it.hint to it.key }
        for (field in newPass.fields) {
            val changeMessage = field.changeMessage ?: continue
            val newValue = field.value ?: continue
            val oldValue = oldValues[field.hint to field.key]?.value
            if (newValue != oldValue) {
                return changeMessage.replace("%@", newValue)
            }
        }
        return null
    }

    // ── Re-registro cuando cambia el token FCM ─────────────────────────────

    fun reRegisterAllPasses(context: Context, newToken: String) {
        // Delegado a WorkManager para ejecutarse en background
        PassReRegisterWorker.enqueue(context, newToken)
    }

    // ── Almacena la URL del web service para un pase registrado ───────────

    fun savePassRegistration(
        context: Context,
        passTypeId: String,
        serial: String,
        webServiceUrl: String,
        authToken: String
    ) {
        prefs(context).edit()
            .putString("webservice_${passTypeId}_${serial}", webServiceUrl)
            .putString("authtoken_${passTypeId}_${serial}", authToken)
            .apply()
    }

    fun clearPassRegistration(context: Context, passTypeId: String, serial: String) {
        prefs(context).edit()
            .remove("webservice_${passTypeId}_${serial}")
            .remove("authtoken_${passTypeId}_${serial}")
            .apply()
    }

    // ── Entry points desde import/actualización/borrado de pases ───────────

    fun registerForUpdatesIfPossible(context: Context, pass: Pass) {
        val webServiceUrl = pass.webServiceURL
        val authToken = pass.authToken
        val passTypeId = pass.passIdent
        val serial = pass.serial
        if (webServiceUrl == null || authToken == null || passTypeId == null || serial == null) {
            return
        }
        managerScope.launch {
            savePassRegistration(context, passTypeId, serial, webServiceUrl, authToken)
            registerPass(context, webServiceUrl, authToken, passTypeId, serial)
        }
    }

    fun unregisterIfPossible(context: Context, pass: Pass) {
        val webServiceUrl = pass.webServiceURL
        val authToken = pass.authToken
        val passTypeId = pass.passIdent
        val serial = pass.serial
        if (webServiceUrl == null || authToken == null || passTypeId == null || serial == null) {
            return
        }
        managerScope.launch {
            unregisterPass(context, webServiceUrl, authToken, passTypeId, serial)
            clearPassRegistration(context, passTypeId, serial)
        }
    }

    // ── Internals ──────────────────────────────────────────────────────────

    // Installs the freshly-downloaded pass through the same path a manual re-import
    // uses (UnzipPassController), so it lands in - and overwrites - the exact same
    // pass the user already has installed, instead of an unrelated raw file that
    // nothing else in the app ever reads back.
    private fun installUpdatedPass(context: Context, passStore: PassStore, bytes: ByteArray): Boolean {
        val tempFile = File.createTempFile("push_update", ".pkpass")
        var success = false
        try {
            tempFile.writeBytes(bytes)
            val inputStreamWithSource = InputStreamWithSource(tempFile.toURI().toString(), tempFile.inputStream())
            val spec = UnzipPassController.InputStreamUnzipControllerSpec(
                inputStreamWithSource, context, passStore,
                object : UnzipPassController.SuccessCallback {
                    override fun call(uuid: String) {
                        success = true
                    }
                },
                object : UnzipPassController.FailCallback {
                    override fun fail(reason: String) {
                        Log.w(TAG, "Error installing updated pass: $reason")
                    }
                }
            )
            spec.overwrite = true
            UnzipPassController.processInputStream(spec)
        } finally {
            tempFile.delete()
        }
        return success
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
