package com.example.coffeeadmin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VirtualCardTest {
    @Test
    fun generatesSixteenDigits() {
        val uid = VirtualCard.generate()
        assertEquals(16, uid.length)
        assertTrue(uid.all { it.isDigit() })
        assertTrue(VirtualCard.isVirtual(uid))
    }

    @Test
    fun realCardUidsAreNotVirtual() {
        assertFalse(VirtualCard.isVirtual("04A1B2C3"))
        assertFalse(VirtualCard.isVirtual("04A1B2C3D4E5F6"))
        assertFalse(VirtualCard.isVirtual("12345678901234"))
        assertFalse(VirtualCard.isVirtual("04A1B2C3D4E5F60718"))
        assertFalse(VirtualCard.isVirtual(null))
    }

    @Test
    fun virtualUidFitsTheFirmwareFormat() {
        // FirebaseCloud.cpp validCardUid and the database rules: 8-20 characters of 0-9A-F
        assertTrue(Regex("^[0-9A-F]{8,20}$").matches(VirtualCard.generate()))
    }

    @Test
    fun skipsTakenValues() {
        val first = VirtualCard.generate()
        assertNotEquals(first, VirtualCard.generate(setOf(first)))
        assertEquals(8, PairCode.generate().length)
    }
}
