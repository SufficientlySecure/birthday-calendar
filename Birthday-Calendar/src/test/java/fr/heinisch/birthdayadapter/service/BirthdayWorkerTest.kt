package fr.heinisch.birthdayadapter.service

import android.content.Context
import android.provider.CalendarContract
import androidx.test.core.app.ApplicationProvider
import fr.heinisch.birthdayadapter.util.CalendarHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BirthdayWorkerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testCalendarHelperUriGeneration() {
        val uri = CalendarHelper.getBirthdayAdapterUri(context, CalendarContract.Events.CONTENT_URI)
        assertNotNull(uri)
        assertEquals("true", uri.getQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER))
    }
}
