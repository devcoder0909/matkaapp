package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun readStringFromContext() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("CARLO 567", appName)
    }

    @Test
    fun testErrorStringsAvailable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertNotNull(context.getString(R.string.error_no_internet_title))
        assertNotNull(context.getString(R.string.error_no_internet_msg))
        assertNotNull(context.getString(R.string.btn_retry))
    }

    @Test
    fun testSplashStringsAvailable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertNotNull(context.getString(R.string.splash_tagline))
        assertNotNull(context.getString(R.string.splash_loading))
    }

    @Test
    fun testDrawablesAvailable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertNotNull(context.getDrawable(R.drawable.app_logo))
        assertNotNull(context.getDrawable(R.drawable.ic_launcher_foreground_asset))
        assertNotNull(context.getDrawable(R.drawable.ic_launcher_foreground_padded))
        assertNotNull(context.getDrawable(R.drawable.ic_app_logo))
        assertNotNull(context.getDrawable(R.drawable.ic_wifi_off))
        assertNotNull(context.getDrawable(R.drawable.ic_refresh))
        assertNotNull(context.getDrawable(R.drawable.ic_home))
        assertNotNull(context.getDrawable(R.drawable.ic_back_arrow))
    }
}
