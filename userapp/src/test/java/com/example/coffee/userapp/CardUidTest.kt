package com.example.coffee.userapp

import org.junit.Assert.assertEquals
import org.junit.Test

class CardUidTest {
    @Test
    fun upperCaseHexWithoutSeparators() {
        assertEquals("04A1FF0C", CardUid.toHex(byteArrayOf(0x04, 0xA1.toByte(), 0xFF.toByte(), 0x0C)))
    }

    @Test
    fun keepsLeadingZeros() {
        assertEquals("0001", CardUid.toHex(byteArrayOf(0, 1)))
    }
}
