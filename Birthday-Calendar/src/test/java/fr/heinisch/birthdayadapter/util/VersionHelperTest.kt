package fr.heinisch.birthdayadapter.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VersionHelperTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testSetAndGetFullVersionUnlocked() {
        VersionHelper.setFullVersionUnlocked(context, true)
        val unlocked = VersionHelper.isFullVersionUnlocked(context)
        assertEquals(true, unlocked)

        VersionHelper.setFullVersionUnlocked(context, false)
        val unlockedAfter = VersionHelper.isFullVersionUnlocked(context)
        assertNotNull(unlockedAfter)
    }
}
