package fr.heinisch.birthdayadapter.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AccountHelperTest {

    private lateinit var context: Context
    private lateinit var accountHelper: AccountHelper

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        accountHelper = AccountHelper(context)
    }

    @Test
    fun testIsAccountActivatedCheck() {
        val activated = accountHelper.isAccountActivated()
        assertNotNull(activated)
    }
}
