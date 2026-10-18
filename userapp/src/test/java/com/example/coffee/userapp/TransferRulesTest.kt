package com.example.coffee.userapp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferRulesTest {
    @Test
    fun wholeHundredsWithinTheCredit() {
        assertTrue(TransferRules.valid(100, 100))
        assertTrue(TransferRules.valid(500, 1200))
        assertFalse(TransferRules.valid(150, 1000))
        assertFalse(TransferRules.valid(0, 1000))
        assertFalse(TransferRules.valid(600, 500))
        assertFalse(TransferRules.valid(200_000, 500_000))
    }
}
