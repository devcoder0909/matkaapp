package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testBaseUrl() {
        assertEquals("https://matkabooking.online/", UrlDispatcher.BASE_URL)
        assertEquals("matkabooking.online", UrlDispatcher.PRIMARY_HOST)
    }
}
