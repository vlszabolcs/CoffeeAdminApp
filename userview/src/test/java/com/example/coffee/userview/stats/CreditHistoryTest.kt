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
    fun emptyLog() {
        assertEquals(emptyList<CreditEvent>(), CreditHistory.classify(emptyMap()))
    }
}
