package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UrlDispatcherTest {

    @Test
    fun testInternalUrlRecognition() {
        assertEquals("https://matkabooking.online/", UrlDispatcher.BASE_URL)
        assertTrue(UrlDispatcher.isInternalUrl("https://matkabooking.online/admin/"))
        assertTrue(UrlDispatcher.isInternalUrl("https://matkabooking.online/"))
        assertTrue(UrlDispatcher.isInternalUrl("https://matkabooking.online/login"))
        assertTrue(UrlDispatcher.isInternalUrl("https://www.matkabooking.online/dashboard"))
        assertTrue(UrlDispatcher.isInternalUrl("https://app.matkabooking.online/play?game=1"))
        assertTrue(UrlDispatcher.isInternalUrl("javascript:void(0)"))
        assertTrue(UrlDispatcher.isInternalUrl("about:blank"))
    }

    @Test
    fun testExternalUrlRejection() {
        assertFalse(UrlDispatcher.isInternalUrl("https://google.com"))
        assertFalse(UrlDispatcher.isInternalUrl("https://matkabooking.online.attacker.com/"))
        assertFalse(UrlDispatcher.isInternalUrl("https://attacker-matkabooking.online/"))
        assertFalse(UrlDispatcher.isInternalUrl(""))
        assertFalse(UrlDispatcher.isInternalUrl(null))
    }

    @Test
    fun testClassifyInternalUrl() {
        val dest = UrlDispatcher.classifyUrl("https://matkabooking.online/home")
        assertTrue(dest is UrlDispatcher.UrlDestination.Internal)
        assertEquals("https://matkabooking.online/home", (dest as UrlDispatcher.UrlDestination.Internal).url)
    }

    @Test
    fun testClassifyJavascriptAndAbout() {
        val destJs = UrlDispatcher.classifyUrl("javascript:void(0)")
        assertTrue(destJs is UrlDispatcher.UrlDestination.Internal)

        val destAbout = UrlDispatcher.classifyUrl("about:blank")
        assertTrue(destAbout is UrlDispatcher.UrlDestination.Internal)
    }

    @Test
    fun testClassifyExternalBrowser() {
        val dest = UrlDispatcher.classifyUrl("https://en.wikipedia.org/wiki/Main_Page")
        assertTrue(dest is UrlDispatcher.UrlDestination.ExternalBrowser)
        assertEquals("https", (dest as UrlDispatcher.UrlDestination.ExternalBrowser).uri.scheme)
    }

    @Test
    fun testClassifyUpiAndPaymentApps() {
        val upiLink = "upi://pay?pa=merchant@upi&pn=MatkaBooking&am=100.00&cu=INR"
        val destUpi = UrlDispatcher.classifyUrl(upiLink)
        assertTrue(destUpi is UrlDispatcher.UrlDestination.Upi)

        val phonepeLink = "phonepe://pay?pa=merchant@phonepe&am=200"
        val destPhonePe = UrlDispatcher.classifyUrl(phonepeLink)
        assertTrue(destPhonePe is UrlDispatcher.UrlDestination.Upi)

        val paytmLink = "paytmmp://pay?pa=merchant@paytm&am=300"
        val destPaytm = UrlDispatcher.classifyUrl(paytmLink)
        assertTrue(destPaytm is UrlDispatcher.UrlDestination.Upi)
    }

    @Test
    fun testClassifyDialer() {
        val telLink = "tel:+919876543210"
        val dest = UrlDispatcher.classifyUrl(telLink)
        assertTrue(dest is UrlDispatcher.UrlDestination.Dial)
        assertEquals("tel", (dest as UrlDispatcher.UrlDestination.Dial).uri.scheme)
    }

    @Test
    fun testClassifyEmail() {
        val mailtoLink = "mailto:support@matkabooking.online?subject=Help"
        val dest = UrlDispatcher.classifyUrl(mailtoLink)
        assertTrue(dest is UrlDispatcher.UrlDestination.Email)
        assertEquals("mailto", (dest as UrlDispatcher.UrlDestination.Email).uri.scheme)
    }

    @Test
    fun testClassifyWhatsApp() {
        val waDirect = "whatsapp://send?phone=919876543210&text=Hello"
        val destDirect = UrlDispatcher.classifyUrl(waDirect)
        assertTrue(destDirect is UrlDispatcher.UrlDestination.WhatsApp)

        val waMe = "https://wa.me/919876543210?text=Hello"
        val destMe = UrlDispatcher.classifyUrl(waMe)
        assertTrue(destMe is UrlDispatcher.UrlDestination.WhatsApp)
    }

    @Test
    fun testClassifyTelegram() {
        val tgDirect = "tg://resolve?domain=carlo567"
        val destDirect = UrlDispatcher.classifyUrl(tgDirect)
        assertTrue(destDirect is UrlDispatcher.UrlDestination.Telegram)

        val tMe = "https://t.me/carlo567"
        val destTMe = UrlDispatcher.classifyUrl(tMe)
        assertTrue(destTMe is UrlDispatcher.UrlDestination.Telegram)
    }

    @Test
    fun testClassifySms() {
        val smsLink = "sms:9876543210?body=OTP"
        val dest = UrlDispatcher.classifyUrl(smsLink)
        assertTrue(dest is UrlDispatcher.UrlDestination.Sms)
        assertEquals("sms", (dest as UrlDispatcher.UrlDestination.Sms).uri.scheme)
    }
}
