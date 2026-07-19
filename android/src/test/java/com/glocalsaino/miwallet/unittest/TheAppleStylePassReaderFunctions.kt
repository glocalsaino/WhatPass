package com.glocalsaino.miwallet.unittest

import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.Test
import com.glocalsaino.miwallet.reader.getBarcodeJson

class TheAppleStylePassReaderFunctions {


    @Test
    fun testEmptyWorks() {
        assertThat(JSONObject("{ }").getBarcodeJson()).isNull()
    }

    @Test
            // https://github.com/ligi/PassAndroid/issues/138
    fun testArrayWorks() {
        //language=JSON
        val json = "{ \"barcodes\":[{\"format\":\"PKBarcodeFormatQR\",\"message\":\"YO!\"}] }"
        val tested = JSONObject(json).getBarcodeJson()

        assertThat(tested).isNotNull
        assertThat(tested!!.has("message")).isTrue
    }

    @Test
    fun testSingleWorks() {
        //language=JSON
        val json = "{ \"barcode\":{\"format\":\"PKBarcodeFormatQR\",\"message\":\"YO!\"} }"
        val tested = JSONObject(json).getBarcodeJson()

        assertThat(tested).isNotNull
        assertThat(tested!!.has("message")).isTrue
    }

    @Test
    fun testSingleIsPreferred() {
        //language=JSON
        val json = "{ \"barcodes\":[{\"format\":\"PKBarcodeFormatQR\",\"message\":\"NO!\"}] ,\"barcode\":{\"format\":\"PKBarcodeFormatQR\",\"message\":\"YO!\"} }"
        val tested = JSONObject(json).getBarcodeJson()

        assertThat(tested).isNotNull
        assertThat(tested!!.getString("message")).isEqualTo("YO!")
    }
}
