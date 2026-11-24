package fr.heinisch.birthdayadapter.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import android.provider.CalendarContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CalendarHelperTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testGetBirthdayAdapterUri() {
        val uri = CalendarHelper.getBirthdayAdapterUri(context, CalendarContract.Calendars.CONTENT_URI)
        assertNotNull(uri)
        assertEquals("true", uri.getQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER))
        assertEquals(Constants.getAccountName(context), uri.getQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME))
    }
}
