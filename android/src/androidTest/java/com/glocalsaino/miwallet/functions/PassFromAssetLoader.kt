package com.glocalsaino.miwallet.functions

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.assertj.core.api.Fail.fail
import com.glocalsaino.miwallet.TestApp
import com.glocalsaino.miwallet.model.InputStreamWithSource
import com.glocalsaino.miwallet.model.PassStore
import com.glocalsaino.miwallet.model.pass.Pass
import com.glocalsaino.miwallet.reader.AppleStylePassReader
import com.glocalsaino.miwallet.ui.UnzipPassController
import com.glocalsaino.miwallet.ui.UnzipPassController.FailCallback
import com.glocalsaino.miwallet.ui.UnzipPassController.InputStreamUnzipControllerSpec
import org.mockito.Mockito.*
import java.io.File


private fun getTestTargetPath(context: Context) = File(context.cacheDir, "test_passes")


fun loadPassFromAsset(asset: String, callback: (pass: Pass?) -> Unit) {
    try {

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val inputStream = instrumentation.context.resources.assets.open(asset)
        val inputStreamWithSource = InputStreamWithSource("none", inputStream)

        val mock = mock(FailCallback::class.java)
        val spec = InputStreamUnzipControllerSpec(inputStreamWithSource, instrumentation.targetContext, mock(
                PassStore::class.java),
                object : UnzipPassController.SuccessCallback {
                    override fun call(uuid: String) {
                        callback.invoke(AppleStylePassReader.read(File(getTestTargetPath(instrumentation.targetContext), uuid), "en",
                                instrumentation.targetContext,TestApp.tracker))
                    }
                },
                mock
        )

        spec.overwrite = true
        spec.targetPath = getTestTargetPath(spec.context)
        UnzipPassController.processInputStream(spec)

        verify(mock, never()).fail(anyString())

    } catch (e: Exception) {
        fail("should be able to load file ", e)
    }

}
