package com.example.coffeeadmin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.coffeeadmin.ui.theme.CoffeeAdminTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot of the admin app's user list for the portfolio site, with sample users.
 * MainScreen and UserListScreen need the Firebase view model, so this test puts together the
 * same frame (top bar, search row, user rows, bottom navigation) from the app's own composables.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
class AdminScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val users = listOf(
        User(uid = "04A1B2C3", name = "Anna", credit = 2300) to 46,
        User(uid = "04B7C1D2", name = "Bence", credit = 1800) to 31,
        User(uid = "04C3D9E1", name = "Csilla", credit = 400) to 58,
        User(uid = "04D4E2F7", name = "Dávid", credit = 3100) to 12,
        User(uid = "04E8F1A3", name = "Eszter", credit = 0) to 27,
        User(uid = "04F2A6B8", name = "Gábor", credit = 900) to 39,
        User(uid = "0419C4D5", name = "Hanna", credit = 1500) to 8,
    )

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun adminUserList() {
        compose.setContent {
            CoffeeAdminTheme(darkTheme = false) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.app_name)) },
                            actions = {
                                IconButton(onClick = {}) {
                                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                                }
                            }
                        )
                    },
                    bottomBar = {
                        NavigationBar {
                            listOf(R.string.nav_users, R.string.nav_machine, R.string.nav_logs).forEachIndexed { index, title ->
                                NavigationBarItem(
                                    selected = index == 0,
                                    onClick = {},
                                    label = { Text(stringResource(title)) },
                                    icon = {
                                        Icon(
                                            when (index) {
                                                0 -> Icons.Default.Person
                                                1 -> Icons.Default.Settings
                                                else -> Icons.AutoMirrored.Filled.List
                                            },
                                            contentDescription = null
                                        )
                                    }
                                )
                            }
                        }
                    }
                ) { padding ->
                    Box(modifier = Modifier.padding(padding)) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextField(
                                    value = "",
                                    onValueChange = {},
                                    modifier = Modifier.weight(1f),
                                    placeholder = { Text(stringResource(R.string.search_placeholder)) },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                FilledIconButton(onClick = {}, modifier = Modifier.size(56.dp)) {
                                    Icon(Icons.Default.PersonAdd, contentDescription = null)
                                }
                            }
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(users, key = { it.first.uid ?: "" }) { (user, coffees) ->
                                    UserRow(user = user, coffeeCount = coffees, onClick = {})
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screenshots/admin-users.png")
    }
}
