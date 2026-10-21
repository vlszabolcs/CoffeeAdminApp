package com.example.coffee.userview.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionsTest {
    @Test
    fun comparesNumerically() {
        assertTrue(AppVersions.isNewer("1.1", "1.0"))
        assertTrue(AppVersions.isNewer("1.10", "1.9"))
        assertTrue(AppVersions.isNewer("2", "1.9.9"))
        assertFalse(AppVersions.isNewer("1.0", "1.0"))
        assertFalse(AppVersions.isNewer("1.1.0", "1.1"))
        assertFalse(AppVersions.isNewer("1.0", "1.1"))
    }

    @Test
    fun unknownVersionIsNeverNewer() {
        assertFalse(AppVersions.isNewer("beta", "1.0"))
        assertFalse(AppVersions.isNewer("1.1", ""))
    }

    @Test
    fun picksHighestReleaseOfThisApp() {
        val releases = listOf(
            ReleaseInfo("adminapp-v3.0", draft = false, prerelease = false, url = "admin"),
            ReleaseInfo("userapp-v1.0", draft = false, prerelease = false, url = "u10"),
            ReleaseInfo("userapp-v1.10", draft = false, prerelease = false, url = "u110"),
            ReleaseInfo("userapp-v1.9", draft = false, prerelease = false, url = "u19"),
            ReleaseInfo("userapp-v2.0", draft = true, prerelease = false, url = "draft"),
            ReleaseInfo("userapp-v1.11", draft = false, prerelease = true, url = "pre")
        )
        assertEquals(AppRelease("1.10", "u110"), AppVersions.latest(releases, "userapp-v"))
        assertEquals(AppRelease("3.0", "admin"), AppVersions.latest(releases, "adminapp-v"))
        assertNull(AppVersions.latest(releases, "other-v"))
    }
}
