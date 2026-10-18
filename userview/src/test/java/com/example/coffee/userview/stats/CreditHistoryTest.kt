package com.example.coffee.userview.stats

import org.junit.Assert.assertEquals
import org.junit.Test

class CreditHistoryTest {
    @Test
    fun classifiesByDifferenceToPreviousEntry() {
        val events = CreditHistory.classify(mapOf(300L to 350L, 100L to 500L, 200L to 2500L, 400L to -100L))

        assertEquals(
            listOf(
                CreditEvent(100, CreditEvent.Type.INITIAL, 500, 500),
                CreditEvent(200, CreditEvent.Type.TOP_UP, 2000, 2500),
                CreditEvent(300, CreditEvent.Type.PURCHASE, 2150, 350),
                CreditEvent(400, CreditEvent.Type.PURCHASE, 450, -100)
            ),
            events
        )
    }

    @Test
    fun skipsEntriesWithoutChange() {
        val events = CreditHistory.classify(mapOf(1L to 500L, 2L to 500L, 3L to 350L))
        assertEquals(listOf(CreditEvent.Type.INITIAL, CreditEvent.Type.PURCHASE), events.map { it.type })
    }

    @Test
    fun transfersAreNeitherPurchaseNorTopUp() {
        val events = CreditHistory.classify(
            mapOf(1L to 1000L, 2L to 500L, 3L to 700L, 4L to 600L),
            mapOf(2L to TransferMark(-500, "Anna"), 3L to TransferMark(200, null))
        )
        assertEquals(
            listOf(
                CreditEvent(1, CreditEvent.Type.INITIAL, 1000, 1000),
                CreditEvent(2, CreditEvent.Type.TRANSFER_OUT, 500, 500, "Anna"),
                CreditEvent(3, CreditEvent.Type.TRANSFER_IN, 200, 700),
                CreditEvent(4, CreditEvent.Type.PURCHASE, 100, 600)
            ),
            events
        )
        assertEquals(1, UsageStats.summary(events).coffees)
    }

    @Test
    fun emptyLog() {
        assertEquals(emptyList<CreditEvent>(), CreditHistory.classify(emptyMap()))
    }
}
