package com.kvieta.companion.connection

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileUpdateCheckerTest {

    @Test
    fun sameVersionIsNotRemoteNewer() {
        assertFalse(MobileUpdateChecker.isRemoteNewer("1.0.0-alpha1.3", "alpha-1.3"))
        assertFalse(MobileUpdateChecker.isRemoteNewer("1.0.0-alpha1.3", "alpha1.3"))
        assertFalse(MobileUpdateChecker.isRemoteNewer("alpha-1.3", "alpha-1.3"))
        assertFalse(MobileUpdateChecker.isRemoteNewer("1.0.0-alpha1.3", "1.0.0-alpha1.3"))
    }

    @Test
    fun remoteNewerAlphaDetected() {
        assertTrue(MobileUpdateChecker.isRemoteNewer("1.0.0-alpha1.3", "alpha-1.4"))
        assertTrue(MobileUpdateChecker.isRemoteNewer("1.0.0-alpha1.3", "alpha-2.0"))
        assertTrue(MobileUpdateChecker.isRemoteNewer("alpha-1.3", "alpha-1.4"))
        assertTrue(MobileUpdateChecker.isRemoteNewer("1.0.0-alpha1.3", "v1.0.0-alpha1.4"))
    }

    @Test
    fun localNewerIsNotRemoteNewer() {
        assertFalse(MobileUpdateChecker.isRemoteNewer("1.0.0-alpha1.4", "alpha-1.3"))
        assertFalse(MobileUpdateChecker.isRemoteNewer("alpha-2.0", "alpha-1.9"))
        assertFalse(MobileUpdateChecker.isRemoteNewer("1.0.0-alpha2.0", "alpha-1.9"))
    }

    @Test
    fun semverComparisonWorks() {
        assertTrue(MobileUpdateChecker.isRemoteNewer("1.0.0", "1.0.1"))
        assertFalse(MobileUpdateChecker.isRemoteNewer("1.0.1", "1.0.0"))
        assertFalse(MobileUpdateChecker.isRemoteNewer("1.0.0", "1.0.0"))
    }
}
