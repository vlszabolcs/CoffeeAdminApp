package com.example.coffee.userapp.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.coffee.userview.data.CardUser
import com.example.coffee.userview.data.MachineConfig
import com.example.coffee.userview.data.MachinePresence
import com.example.coffee.userview.data.RemoteVendRepository
import com.example.coffee.userview.data.UserState
import com.example.coffee.userview.data.UserViewRepository
import com.example.coffee.userview.stats.TransferMark
import com.example.coffee.userview.ui.UserScreen
import com.github.takahirom.roborazzi.captureRoboImage
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.ZoneId

/**
 * Screenshots of the user app's main screen for the portfolio site.
 * The real UserScreen is rendered; only the Firebase repositories are replaced by sample data.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
class UserAppScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val price = 100L
    private val cardUid = "04A1B2C3"

    /** About six weeks of sample history: a starting balance, weekday coffees, a top-up and a transfer. */
    private fun sampleUser(): CardUser {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        // (epoch seconds, change in Ft, transfer mark) - collected first, then applied in time order
        val changes = mutableListOf<Triple<Long, Long, TransferMark?>>()
        var day = today.minusDays(42)
        var n = 0
        while (!day.isAfter(today)) {
            val at = { h: Int, m: Int -> day.atTime(h, m).atZone(zone).toEpochSecond() }
            if (day == today.minusDays(20)) changes += Triple(at(12, 30), 3000L, null)
            if (day == today.minusDays(3)) changes += Triple(at(16, 10), -500L, TransferMark(amount = -500, peer = "Bence"))
            if (day.dayOfWeek.value <= 5) {
                changes += Triple(at(7, 40 + (n % 15)), -price, null)
                if (n % 3 == 0) changes += Triple(at(13, 15 + (n % 20)), -price, null)
                n++
            }
            day = day.plusDays(1)
        }
        val log = sortedMapOf<Long, Long>()
        val transfers = mutableMapOf<Long, TransferMark>()
        var credit = 4000L
        log[today.minusDays(43).atTime(9, 0).atZone(zone).toEpochSecond()] = credit
        for ((ts, delta, mark) in changes.sortedBy { it.first }) {
            credit += delta
            log[ts] = credit
            if (mark != null) transfers[ts] = mark
        }
        return CardUser(uid = cardUid, name = "Anna", credit = credit, log = log, transfers = transfers)
    }

    private fun render() {
        val user = sampleUser()
        val repository = mockk<UserViewRepository>(relaxed = true)
        every { repository.machineId } returns "machine1"
        every { repository.observeUser(any()) } returns flowOf(UserState.Ready(user))
        every { repository.observeConfig() } returns flowOf(MachineConfig(price = price, free = false))
        every { repository.observePresence() } returns flowOf(
            MachinePresence(lastSeenMs = System.currentTimeMillis(), ready = true, transfer = true)
        )
        val vend = mockk<RemoteVendRepository>(relaxed = true)

        compose.setContent {
            CoffeeAppTheme(darkTheme = false) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    UserScreen(
                        cardUid = cardUid,
                        onClose = null,
                        untitled = "CoffeeApp",
                        repository = repository,
                        vendRepository = vend,
                        actions = {
                            IconButton(onClick = {}) { Icon(Icons.Default.SwapHoriz, contentDescription = null) }
                            IconButton(onClick = {}) { Icon(Icons.Default.CreditCard, contentDescription = null) }
                        }
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun userAppLog() {
        render()
        compose.onRoot().captureRoboImage("build/screenshots/userapp-log.png")
    }

    @Test
    fun userAppStats() {
        render()
        compose.onNodeWithText("Statistics").performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screenshots/userapp-stats.png")
    }
}
