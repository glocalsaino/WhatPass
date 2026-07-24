package com.glocalsaino.miwallet.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import org.json.JSONException
import org.json.JSONObject
import com.glocalsaino.miwallet.parseColor
import com.glocalsaino.miwallet.R
import com.glocalsaino.miwallet.Tracker
import com.glocalsaino.miwallet.functions.getHumanCategoryString
import com.glocalsaino.miwallet.functions.readJSONSafely
import com.glocalsaino.miwallet.model.ApplePassbookQuirkCorrector
import com.glocalsaino.miwallet.model.AppleStylePassTranslation
import com.glocalsaino.miwallet.model.PassBitmapDefinitions
import com.glocalsaino.miwallet.model.PassDefinitions
import com.glocalsaino.miwallet.model.pass.*
import org.threeten.bp.DateTimeException
import org.threeten.bp.ZonedDateTime
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.util.*

object AppleStylePassReader {

    fun read(passFile: File, language: String, context: Context, tracker: Tracker): Pass? {

        val translation = AppleStylePassTranslation()

        val pass = PassImpl(passFile.name)

        var passJSON: JSONObject? = null

        val localizedPath = findLocalizedPath(passFile, language, tracker)

        if (localizedPath != null) {
            val file = File(localizedPath, "pass.strings")
            translation.loadFromFile(file)
        }

        copyBitmapFile(passFile, localizedPath, PassBitmapDefinitions.BITMAP_ICON)
        copyBitmapFile(passFile, localizedPath, PassBitmapDefinitions.BITMAP_LOGO)
        copyBitmapFile(passFile, localizedPath, PassBitmapDefinitions.BITMAP_STRIP)
        copyBitmapFile(passFile, localizedPath, PassBitmapDefinitions.BITMAP_THUMBNAIL)
        copyBitmapFile(passFile, localizedPath, PassBitmapDefinitions.BITMAP_FOOTER)

        val file = File(passFile, "pass.json")

        try {
            val plainJsonString = AppleStylePassTranslation.readFileAsStringGuessEncoding(file)
            passJSON = readJSONSafely(plainJsonString)
        } catch (e: Exception) {
            Timber.i("PassParse Exception: $e")
        }

        if (passJSON == null) {
            // I had got a strange passbook with UCS-2 which could not be parsed before
            // was searching for a auto-detection, but could not find one with support for this encoding
            // and the right license

            for (charset in Charset.availableCharsets().values) {
                try {
                    val json = file.bufferedReader(charset).readText()
                    passJSON = readJSONSafely(json)
                } catch (ignored: Exception) {
                    // we try with next charset
                }

                if (passJSON != null) {
                    break
                }
            }
        }

        if (passJSON == null) {
            Timber.w("could not load pass.json from passcode ")
            tracker.trackEvent("problem_event", "pass", "without_pass_json", null)
            return null
        }

        try {
            val barcodeJSON = passJSON.getBarcodeJson()
            if (barcodeJSON != null) {
                val barcodeFormatString = barcodeJSON.getString("format")

                tracker.trackEvent("measure_event", "barcode_format", barcodeFormatString, 0L)
                val barcodeFormat = BarCode.getFormatFromString(barcodeFormatString)
                val barCode = BarCode(barcodeFormat, barcodeJSON.getString("message"))
                pass.barCode = barCode

                if (barcodeJSON.has("altText")) {
                    pass.barCode!!.alternativeText = barcodeJSON.getString("altText")
                }
            }
            // TODO should check a bit more with barcode here - this can be dangerous
        } catch (ignored: Exception) {
        }

        if (passJSON.has("relevantDate")) {
            try {
                pass.calendarTimespan = PassImpl.TimeSpan(from = ZonedDateTime.parse(passJSON.getString("relevantDate")))
            } catch (e: JSONException) {
                // be robust when it comes to bad dates - had a RL crash with "2013-12-25T00:00-57:00" here
                // OK then we just have no date here
                tracker.trackException("problem parsing relevant date", e, false)
            } catch (e: DateTimeException) {
                tracker.trackException("problem parsing relevant date", e, false)
            }

        }

        if (passJSON.has("expirationDate")) {
            try {
                pass.validTimespans = listOf(PassImpl.TimeSpan(to = ZonedDateTime.parse(passJSON.getString("expirationDate"))))
            } catch (e: JSONException) {
                // be robust when it comes to bad dates - had a RL crash with "2013-12-25T00:00-57:00" here
                // OK then we just have no date here
                tracker.trackException("problem parsing expiration date", e, false)
            } catch (e: DateTimeException) {
                tracker.trackException("problem parsing expiration date", e, false)
            }

        }

        pass.serial = readJsonSafeAsOptional(passJSON, "serialNumber")
        pass.authToken = readJsonSafeAsOptional(passJSON, "authenticationToken")
        pass.webServiceURL = readJsonSafeAsOptional(passJSON, "webServiceURL")
        pass.passIdent = readJsonSafeAsOptional(passJSON, "passTypeIdentifier")

        val locations = ArrayList<PassLocation>()
        try {

            val locationsJSON = passJSON.getJSONArray("locations")
            for (i in 0 until locationsJSON.length()) {
                val obj = locationsJSON.getJSONObject(i)

                val location = PassLocation()
                location.lat = obj.getDouble("latitude")
                location.lon = obj.getDouble("longitude")

                if (obj.has("relevantText")) {
                    location.name = translation.translate(obj.getString("relevantText"))
                }

                locations.add(location)
            }

        } catch (ignored: JSONException) {
        }

        pass.locations = locations

        if (passJSON.has("sharingProhibited")) {
            pass.sharingProhibited = passJSON.getBoolean("sharingProhibited")
        }

        val beacons = ArrayList<com.glocalsaino.miwallet.model.pass.PassBeacon>()
        try {
            val beaconsJSON = passJSON.getJSONArray("beacons")
            for (i in 0 until beaconsJSON.length()) {
                val obj = beaconsJSON.getJSONObject(i)
                val beacon = com.glocalsaino.miwallet.model.pass.PassBeacon()
                beacon.proximityUUID = obj.getString("proximityUUID")
                if (obj.has("major")) beacon.major = obj.getInt("major")
                if (obj.has("minor")) beacon.minor = obj.getInt("minor")
                if (obj.has("relevantText")) beacon.relevantText = translation.translate(obj.getString("relevantText"))
                beacons.add(beacon)
            }
        } catch (ignored: JSONException) {
        }
        pass.beacons = beacons

        readJsonSafe(passJSON, "backgroundColor", object : JsonStringReadCallback {
            override fun onString(string: String) {
                pass.accentColor = string.parseColor(Color.BLACK)
            }
        })

        readJsonSafe(passJSON, "foregroundColor", object : JsonStringReadCallback {
            override fun onString(string: String) {
                pass.foregroundColor = string.parseColor(Color.WHITE)
            }
        })

        readJsonSafe(passJSON, "labelColor", object : JsonStringReadCallback {
            override fun onString(string: String) {
                pass.labelColor = string.parseColor(Color.WHITE)
            }
        })

        readJsonSafe(passJSON, "description", object : JsonStringReadCallback {
            override fun onString(string: String) {
                pass.description = translation.translate(string)
            }
        })

        readJsonSafe(passJSON, "logoText", object : JsonStringReadCallback {
            override fun onString(string: String) {
                pass.logoText = translation.translate(string)
            }
        })


        // try to find in a predefined set of tickets

        PassDefinitions.TYPE_TO_NAME.forEach {
            if (passJSON.has(it.value)) {
                pass.type = it.key
            }
        }

        try {
            val type = PassDefinitions.TYPE_TO_NAME[pass.type]
            val typeJSON = passJSON.getJSONObject(type)
            if (typeJSON != null) {
                val fieldList: ArrayList<PassField> = ArrayList()

                addFields(fieldList, typeJSON, "primaryFields", translation)
                addFields(fieldList, typeJSON, "headerFields", translation)
                addFields(fieldList, typeJSON, "secondaryFields", translation)
                addFields(fieldList, typeJSON, "auxiliaryFields", translation)
                addFields(fieldList, typeJSON, "backFields", translation, hide = true)

                if (fieldList.none { !it.hide }) {
                    fieldList.add(PassField("", context.getString(R.string.type), context.getString(getHumanCategoryString(pass.type)), false))
                }
                pass.fields = fieldList

                pass.transitType = readJsonSafeAsOptional(typeJSON, "transitType")
            }

        } catch (ignored: JSONException) {
        }


        try {
            pass.creator = passJSON.getString("organizationName")
            tracker.trackEvent("measure_event", "organisation_parse", pass.creator, 1L)
        } catch (ignored: JSONException) {
            // ok - we have no organisation - big deal ..-)
        }

        ApplePassbookQuirkCorrector(tracker).correctQuirks(pass)

        return pass
    }

    private fun getField(jsonObject: JSONObject, key: String, translation: AppleStylePassTranslation): String? {
        if (jsonObject.has(key)) {
            try {
                return translation.translate(jsonObject.getString(key))
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
        return null
    }

    private fun addFields(list: ArrayList<PassField>, type_json: JSONObject, fieldsName: String, translation: AppleStylePassTranslation, hide: Boolean = false) {
        try {
            val jsonArray = type_json.getJSONArray(fieldsName)
            for (i in 0 until jsonArray.length()) {
                try {
                    val jsonObject = jsonArray.getJSONObject(i)
                    // attributedValue contains HTML with <a href> links per Apple's spec;
                    // fall back to value (plain text) when attributedValue is absent.
                    var value = getField(jsonObject, "attributedValue", translation)
                        ?: getField(jsonObject, "value", translation)

                    if (jsonObject.has("dateStyle") && value != null) {
                        value = formatPassDateValue(jsonObject, value) ?: value
                    }

                    val field = PassField(key = getField(jsonObject, "key", translation),
                            label = getField(jsonObject, "label", translation),
                            value = value,
                            hide = hide,
                            hint = fieldsName,
                            changeMessage = getField(jsonObject, "changeMessage", translation))
                    list.add(field)

                } catch (e: JSONException) {
                    Timber.w("could not process PassField from JSON for $fieldsName cause: $e")
                }

            }
        } catch (e: JSONException) {
            Timber.w("could not process PassFields $fieldsName from JSON: $e")
        }

    }

    private fun formatPassDateValue(jsonObject: JSONObject, rawValue: String): String? {
        val date = try {
            ZonedDateTime.parse(rawValue)
        } catch (e: DateTimeException) {
            return null
        }

        val millis = date.toInstant().toEpochMilli()

        if (jsonObject.optBoolean("isRelative", false)) {
            return android.text.format.DateUtils.getRelativeTimeSpanString(
                    millis, System.currentTimeMillis(), android.text.format.DateUtils.DAY_IN_MILLIS
            ).toString()
        }

        val dateStyle = applePkStyleToJavaStyle(jsonObject.optString("dateStyle", "PKDateStyleNone"))
        val timeStyle = applePkStyleToJavaStyle(jsonObject.optString("timeStyle", "PKDateStyleNone"))
        val legacyDate = Date(millis)

        return when {
            dateStyle != null && timeStyle != null ->
                java.text.DateFormat.getDateTimeInstance(dateStyle, timeStyle, Locale.getDefault()).format(legacyDate)
            dateStyle != null ->
                java.text.DateFormat.getDateInstance(dateStyle, Locale.getDefault()).format(legacyDate)
            timeStyle != null ->
                java.text.DateFormat.getTimeInstance(timeStyle, Locale.getDefault()).format(legacyDate)
            else -> null
        }
    }

    private fun applePkStyleToJavaStyle(pkStyle: String): Int? = when (pkStyle) {
        "PKDateStyleShort" -> java.text.DateFormat.SHORT
        "PKDateStyleMedium" -> java.text.DateFormat.MEDIUM
        "PKDateStyleLong" -> java.text.DateFormat.LONG
        "PKDateStyleFull" -> java.text.DateFormat.FULL
        else -> null
    }

    private fun findLocalizedPath(path: File, language: String, tracker: Tracker): String? {
        val localized = File(path, "$language.lproj")

        if (localized.exists() && localized.isDirectory) {
            tracker.trackEvent("measure_event", "pass", language + "_native_lproj", null)
            return localized.path
        }

        val fallback = File(path, "en.lproj")

        if (fallback.exists() && fallback.isDirectory) {
            tracker.trackEvent("measure_event", "pass", "en_lproj", null)
            return fallback.path
        }

        return null
    }

    internal interface JsonStringReadCallback {
        fun onString(string: String)
    }

    private fun readJsonSafeAsOptional(json: JSONObject, key: String): String? {
        if (json.has(key)) {
            try {
                return json.getString(key)
            } catch (e: JSONException) {
                // some passes just do not have the field
            }

        }
        return null
    }

    private fun readJsonSafe(json: JSONObject, key: String, callback: JsonStringReadCallback) {
        if (json.has(key)) {
            try {
                callback.onString(json.getString(key))
            } catch (e: JSONException) {
                // some passes just do not have the field
            }
        }
    }

    private fun copyBitmapFile(path: File, localizedPath: String?, bitmapString: String) {
        val bitmap = findBitmap(path, localizedPath, bitmapString)
        if (bitmap != null) {
            try {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, FileOutputStream(File(path, bitmapString + PassImpl.FILETYPE_IMAGES)))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun findBitmap(path: File, localizedPath: String?, bitmap: String): Bitmap? {

        val searchList = ArrayList<File>()
        if (localizedPath != null) {
            searchList.add(File(localizedPath, "$bitmap@2x.png"))
            searchList.add(File(localizedPath, "$bitmap.png"))
        }

        searchList.add((File(path, "$bitmap@2x.png")))
        searchList.add((File(path, "$bitmap@2x.png")))

        for (current in searchList) {

            var res: Bitmap? = null

            try {
                res = BitmapFactory.decodeFile(current.absolutePath)
            } catch (e: OutOfMemoryError) {
                System.gc()
                try {
                    res = BitmapFactory.decodeFile(current.absolutePath)
                } catch (e: OutOfMemoryError) {
                }
            }

            if (res != null) {
                return res
            }
        }
        return null
    }
}
