package fr.heinisch.birthdayadapter.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import android.provider.ContactsContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PreferencesHelperTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testGetColor() {
        val color = PreferencesHelper.getColor(context)
        assertNotNull(color)
    }

    @Test
    fun testGetAllReminderMinutesDefault() {
        val reminders = PreferencesHelper.getAllReminderMinutes(context)
        assertNotNull(reminders)
        assertEquals(1, reminders.size)
    }

    @Test
    fun testGetAdditionalReminderMinutesDefault() {
        val reminders = PreferencesHelper.getAdditionalReminderMinutes(context)
        assertNotNull(reminders)
        assertEquals(1, reminders.size)
    }

    @Test
    fun testGetLabelBirthdayWithoutAge() {
        val label = PreferencesHelper.getLabel(
            context,
            ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY,
            false
        )
        assertNotNull(label)
    }

    @Test
    fun testGetLabelBirthdayWithAge() {
        val label = PreferencesHelper.getLabel(
            context,
            ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY,
            true
        )
        assertNotNull(label)
    }

    @Test
    fun testGetLabelAnniversary() {
        val label = PreferencesHelper.getLabel(
            context,
            ContactsContract.CommonDataKinds.Event.TYPE_ANNIVERSARY,
            false
        )
        assertNotNull(label)
    }

    @Test
    fun testGetPreferDDSlashMM() {
        val prefer = PreferencesHelper.getPreferDDSlashMM(context)
        assertNotNull(prefer)
    }

    @Test
    fun testGetUseLastNameFirst() {
        val useLastNameFirst = PreferencesHelper.getUseLastNameFirst(context)
        assertNotNull(useLastNameFirst)
    }
}
