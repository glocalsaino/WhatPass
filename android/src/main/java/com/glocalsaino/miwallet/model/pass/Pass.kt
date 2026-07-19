package com.glocalsaino.miwallet.model.pass

import android.graphics.Bitmap
import androidx.annotation.StringDef
import com.glocalsaino.miwallet.model.PassBitmapDefinitions.*
import com.glocalsaino.miwallet.model.PassStore


interface Pass {

    @StringDef(BITMAP_ICON, BITMAP_THUMBNAIL, BITMAP_STRIP, BITMAP_LOGO, BITMAP_FOOTER, BITMAP_BACKGROUND)
    @Retention(AnnotationRetention.SOURCE)
    annotation class PassBitmap

    val description: String?

    var type: PassType

    val fields: List<PassField>

    val locations: List<PassLocation>

    val sharingProhibited: Boolean

    val beacons: List<PassBeacon>

    var accentColor: Int

    var foregroundColor: Int

    var labelColor: Int

    var transitType: String?

    var logoText: String?

    val id: String

    val creator: String?

    fun getSource(passStore: PassStore): String?

    var barCode: BarCode?

    val webServiceURL: String?

    val authToken: String?

    val serial: String?

    val passIdent: String?

    val app: String?

    val validTimespans: List<PassImpl.TimeSpan>?
    var calendarTimespan: PassImpl.TimeSpan?

    fun getBitmap(passStore: PassStore, passBitmap: String): Bitmap?

}
