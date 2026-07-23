package com.glocalsaino.miwallet.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.view.View.GONE
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import com.glocalsaino.miwallet.startActivityFromClass
import com.glocalsaino.miwallet.alert
import com.glocalsaino.miwallet.R
import com.glocalsaino.miwallet.Tracker
import com.glocalsaino.miwallet.databinding.ActivityImportBinding
import com.glocalsaino.miwallet.functions.NotAPassUrlException
import com.glocalsaino.miwallet.functions.fromURI
import com.glocalsaino.miwallet.model.PassStore
import com.glocalsaino.miwallet.geofence.GeofenceManager
import com.glocalsaino.miwallet.passkit.PassUpdateManager

class PassImportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImportBinding
    val tracker: Tracker by inject()
    val passStore: PassStore by inject()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) doImport(true) else onExternalStorageDenied()
    }

    private fun doImportWithPermissionCheck(withPermission: Boolean) {
        if (!withPermission) {
            doImport(false)
        } else {
            val permission = Manifest.permission.READ_EXTERNAL_STORAGE
            AlertDialog.Builder(this)
                .setTitle(R.string.storage_permission_title)
                .setMessage(R.string.storage_permission_message)
                .setPositiveButton(R.string.location_disclosure_ok) { _, _ ->
                    requestPermissionLauncher.launch(permission)
                }
                .setNegativeButton(R.string.location_disclosure_cancel) { _, _ ->
                    onExternalStorageDenied()
                }
                .show()
        }
    }

    private fun importUri(): Uri? =
        intent.data ?: @Suppress("DEPRECATION") intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri

    private fun doImport(withPermission: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val uri = importUri() ?: run {
                    withContext(Dispatchers.Main) { finish() }
                    return@launch
                }
                val fromURI = fromURI(this@PassImportActivity, uri, tracker)

                withContext(Dispatchers.Main) {
                    binding.progressContainer.visibility = GONE

                    if (fromURI == null) {
                        finish()
                    } else {
                        if (isFinishing) {
                            val spec = UnzipPassController.InputStreamUnzipControllerSpec(fromURI, application, passStore, null, null)
                            UnzipPassController.processInputStream(spec)
                        } else {
                            UnzipPassDialog.show(fromURI, this@PassImportActivity, passStore) { path ->
                                val id = path.split("/".toRegex()).dropLastWhile(String::isEmpty).toTypedArray().last()
                                val passbookForId = passStore.getPassbookForId(id)
                                passStore.currentPass = passbookForId
                                passStore.classifier.moveToTopic(passbookForId!!, getString(R.string.topic_new))
                                PassUpdateManager.registerForUpdatesIfPossible(applicationContext, passbookForId)
                                GeofenceManager.register(applicationContext, passbookForId)
                                if (passbookForId.locations.isNotEmpty() && !GeofenceManager.hasLocationPermission(this@PassImportActivity)) {
                                    requestLocationPermissions {
                                        startActivityFromClass(PassViewActivity::class.java)
                                        finish()
                                    }
                                } else {
                                    startActivityFromClass(PassViewActivity::class.java)
                                    finish()
                                }
                            }
                        }
                    }
                }
            } catch (e: NotAPassUrlException) {
                withContext(Dispatchers.Main) {
                    binding.progressContainer.visibility = GONE
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(e.url)))
                    } catch (notFound: ActivityNotFoundException) {
                        tracker.trackException("No browser to open non-pass URL", notFound, false)
                    }
                    finish()
                }
            } catch (e: Exception) {
                val isPermissionError = e.message?.contains("Permission") == true
                // On Android 10+ content URIs from intents carry automatic read access —
                // requesting READ_EXTERNAL_STORAGE or READ_MEDIA_IMAGES won't help and
                // just confuses the user with an unrelated permission dialog.
                val canFixWithStorage = isPermissionError
                    && !withPermission
                    && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                if (canFixWithStorage) {
                    doImportWithPermissionCheck(true)
                } else {
                    tracker.trackException("Error in import", e, false)
                    withContext(Dispatchers.Main) {
                        binding.progressContainer.visibility = GONE
                        alert(R.string.pass_problem, R.string.problem) { finish() }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (importUri()?.scheme == null) {
            tracker.trackException("invalid_import_uri", false)
            finish()
            return
        }

        binding = ActivityImportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        doImportWithPermissionCheck(false)
    }

    private fun onExternalStorageDenied() {
        binding.progressContainer.visibility = GONE
        alert(R.string.storage_permission_denied_msg, R.string.error_no_permission_title, onOK = { finish() })
    }

    private var pendingNavigate: (() -> Unit)? = null

    private val requestBackgroundLocation = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) GeofenceManager.registerAll(applicationContext, passStore)
        pendingNavigate?.invoke()
        pendingNavigate = null
    }

    private val requestFineLocation = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                requestBackgroundLocation.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                return@registerForActivityResult  // navigate after background location result
            } else {
                GeofenceManager.registerAll(applicationContext, passStore)
            }
        }
        pendingNavigate?.invoke()
        pendingNavigate = null
    }

    private fun requestLocationPermissions(onDone: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(R.string.location_disclosure_title)
            .setMessage(R.string.location_disclosure_message)
            .setPositiveButton(R.string.location_disclosure_ok) { _, _ ->
                pendingNavigate = onDone
                requestFineLocation.launch(arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ))
            }
            .setNegativeButton(R.string.location_disclosure_cancel) { _, _ ->
                onDone()
            }
            .show()
    }
}
