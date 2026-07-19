package com.glocalsaino.miwallet.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import okio.buffer
import okio.source
import org.json.JSONObject
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import com.glocalsaino.miwallet.Tracker
import com.glocalsaino.miwallet.functions.createPassForImageImport
import com.glocalsaino.miwallet.functions.createPassForPDFImport
import com.glocalsaino.miwallet.functions.readJSONSafely
import com.glocalsaino.miwallet.model.InputStreamWithSource
import com.glocalsaino.miwallet.model.PassStore
import com.glocalsaino.miwallet.model.Settings
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.*

object UnzipPassController : KoinComponent {

    val tracker :Tracker by inject()
    val settings : Settings by inject()

    interface SuccessCallback {
        fun call(uuid: String)
    }

    interface FailCallback {
        fun fail(reason: String)
    }

    fun processInputStream(spec: InputStreamUnzipControllerSpec) {
        try {
            spec.inputStreamWithSource.inputStream.use {
                val tempFile = File.createTempFile("ins", "pass")
                it.copyTo(FileOutputStream(tempFile))
                processFile(FileUnzipControllerSpec(tempFile.absolutePath, spec))
                tempFile.delete()
            }
        } catch (e: Exception) {
            tracker.trackException("problem processing InputStream", e, false)
            spec.failCallback?.fail("problem with temp file: $e")
        }

    }

    // Public so callers that need to look up an already-installed pass by its real-world
    // identity (e.g. PassUpdateManager, comparing old vs. new field values across a push
    // update) can compute the same id without re-parsing pass.json.
    fun stableId(passTypeId: String, serial: String): String =
        "$passTypeId:$serial".replace(Regex("[^A-Za-z0-9:._-]"), "_")

    private fun stableIdFromPassJson(passJsonFile: File): String? {
        if (!passJsonFile.exists()) return null
        return try {
            val json = readJSONSafely(passJsonFile.bufferedReader().readText()) ?: return null
            val passTypeId = json.optString("passTypeIdentifier").takeIf { it.isNotBlank() } ?: return null
            val serial = json.optString("serialNumber").takeIf { it.isNotBlank() } ?: return null
            stableId(passTypeId, serial)
        } catch (e: Exception) {
            null
        }
    }

    private fun processFile(spec: FileUnzipControllerSpec) {

        var uuid = UUID.randomUUID().toString()
        val path = File(spec.context.cacheDir, "temp/$uuid")

        path.mkdirs()

        if (!path.exists()) {
            spec.failCallback?.fail("Problem creating the temp dir: $path")
            return
        }

        File(path, "source.obj").bufferedWriter().write(spec.source)

        try {
            val zipFile = ZipFile(spec.zipFileString)
            zipFile.extractAll(path.absolutePath)
        } catch (e: ZipException) {
            e.printStackTrace()
        }


        val manifestFile = File(path, "manifest.json")
        val espassFile = File(path, "main.json")
        val manifestJSON: JSONObject

        when {
            manifestFile.exists() -> try {
                val readToString = manifestFile.bufferedReader().readText()
                manifestJSON = readJSONSafely(readToString)!!
                // Prefer the pass's real-world identity (passTypeIdentifier + serialNumber,
                // per Apple's PassKit spec) over the manifest's content hash. The hash changes
                // on every edit, which made every update to a pass install as a brand new,
                // duplicate pass instead of refreshing the one already on the device.
                uuid = stableIdFromPassJson(File(path, "pass.json")) ?: manifestJSON.getString("pass.json")
            } catch (e: Exception) {
                spec.failCallback?.fail("Problem with manifest.json: $e")
                return
            }
            espassFile.exists() -> try {
                val readToString = espassFile.bufferedReader().readText()
                manifestJSON = readJSONSafely(readToString)!!
                uuid = manifestJSON.getString("id")
            } catch (e: Exception) {
                spec.failCallback?.fail("Problem with manifest.json: $e")
                return
            }
            else -> {
                val bitmap = BitmapFactory.decodeFile(spec.zipFileString)
                val resources = spec.context.resources

                if (bitmap != null) {
                    val imagePass = createPassForImageImport(resources)
                    val pathForID = spec.passStore.getPathForID(imagePass.id)
                    pathForID.mkdirs()

                    File(spec.zipFileString).copyTo(File(pathForID, "strip.png"))

                    spec.passStore.save(imagePass)
                    spec.passStore.classifier.moveToTopic(imagePass, "new")
                    spec.onSuccessCallback?.call(imagePass.id)
                    return
                }

                if (Build.VERSION.SDK_INT >= 21) {
                    try {
                        val file = File(spec.zipFileString)
                        val readUtf8 = file.source().buffer().readUtf8(4)
                        if (readUtf8 == "%PDF") {
                            val open = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                            val pdfRenderer = PdfRenderer(open)

                            val page = pdfRenderer.openPage(0)
                            val ratio = page.height.toFloat() / page.width

                            val widthPixels = resources.displayMetrics.widthPixels
                            val createBitmap = Bitmap.createBitmap(widthPixels, (widthPixels * ratio).toInt(), Bitmap.Config.ARGB_8888)
                            page.render(createBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                            val imagePass = createPassForPDFImport(resources)
                            val pathForID = spec.passStore.getPathForID(imagePass.id)
                            pathForID.mkdirs()

                            createBitmap.compress(Bitmap.CompressFormat.PNG, 100, FileOutputStream(File(pathForID, "strip.png")))

                            spec.passStore.save(imagePass)
                            spec.passStore.classifier.moveToTopic(imagePass, "new")
                            spec.onSuccessCallback?.call(imagePass.id)
                            return
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                spec.failCallback?.fail("Pass is not espass or pkpass format :-(")
                return
            }
        }

        spec.targetPath.mkdirs()
        val renamedFile = File(spec.targetPath, uuid)

        if (spec.overwrite && renamedFile.exists()) {
            renamedFile.deleteRecursively()
            // The old copy's parsed Pass is cached in memory - without dropping it here,
            // getPassbookForId() would keep serving the stale version we just deleted.
            spec.passStore.invalidate(uuid)
        }

        if (!renamedFile.exists()) {
            path.renameTo(renamedFile)
        } else {
            Timber.i("Pass with same ID exists")
        }

        // zip entries carry a DOS timestamp with no timezone info, taken as-is from
        // whatever server/timezone produced the pass - so pass.json's extracted mtime
        // can be hours off from the device's clock. The "last updated" label should
        // reflect when WhatPass actually fetched it, so stamp it with the real time now.
        File(renamedFile, "pass.json").setLastModified(System.currentTimeMillis())

        spec.onSuccessCallback?.call(uuid)
    }

    class InputStreamUnzipControllerSpec(internal val inputStreamWithSource: InputStreamWithSource, context: Context, passStore: PassStore,
                                         onSuccessCallback: SuccessCallback?, failCallback: FailCallback?) : UnzipControllerSpec(context, passStore, onSuccessCallback, failCallback, settings)

}
