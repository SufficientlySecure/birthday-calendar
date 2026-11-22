/*
 * Copyright (C) 2025-2026 Matthias Heinisch <birthdayadapter@heinisch.fr>
 *
 * This file is part of Birthday Adapter.
 *
 * Birthday Adapter is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Birthday Adapter is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Birthday Adapter.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package fr.heinisch.birthdayadapter.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import fr.heinisch.birthdayadapter.R
import fr.heinisch.birthdayadapter.util.AccountHelper
import kotlinx.coroutines.launch
import java.util.Calendar

class OnboardingActivity : ComponentActivity() {

    companion object {
        const val EXTRA_IGNORE_PERMISSION_CHECK_ONCE = "IGNORE_PERMISSION_CHECK_ONCE"
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.GET_ACCOUNTS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            BirthdayAdapterTheme {
                OnboardingScreen(
                    onFinish = { finishOnboarding() },
                    onActivateAccount = { activateAdapterIfNeeded() }
                )
            }
        }
    }

    private fun areAllPermissionsGranted(): Boolean {
        for (permission in REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    private fun activateAdapterIfNeeded() {
        val accountHelper = AccountHelper(this)
        if (!accountHelper.isAccountActivated()) {
            Thread {
                accountHelper.addAccountAndSync()
            }.start()

            val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            prefs.edit { putBoolean(getString(R.string.pref_enabled_key), true) }
        }
    }

    fun finishOnboarding() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.edit { putBoolean("has_seen_onboarding", true) }

        val intent = Intent(this, BaseActivity::class.java).apply {
            if (!areAllPermissionsGranted()) {
                putExtra(EXTRA_IGNORE_PERMISSION_CHECK_ONCE, true)
            }
        }
        startActivity(intent)
        finish()
    }
}

private enum class OnboardingStep {
    INTRO,
    CONTACTS_PERMISSION,
    CALENDAR_PERMISSION,
    FINISH
}

@Composable
private fun OnboardingScreen(
    onFinish: () -> Unit,
    onActivateAccount: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var restartKey by remember { mutableIntStateOf(0) }

    // Pages list evaluated at start or restart only
    val pages = remember(restartKey) {
        val list = mutableListOf<OnboardingStep>()
        list.add(OnboardingStep.INTRO)

        val contactsGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.GET_ACCOUNTS) == PackageManager.PERMISSION_GRANTED
        if (!contactsGranted) {
            list.add(OnboardingStep.CONTACTS_PERMISSION)
        }

        val calendarGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
        if (!calendarGranted) {
            list.add(OnboardingStep.CALENDAR_PERMISSION)
        }

        list.add(OnboardingStep.FINISH)
        list
    }

    val pagerState = rememberPagerState(pageCount = { pages.size })

    val permissions = remember {
        arrayOf(
            Manifest.permission.GET_ACCOUNTS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR
        )
    }
    val areAllPermissionsGranted = remember(pagerState.currentPage, context) {
        permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    Scaffold(
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Page indicator
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(pages.size) { index ->
                            val color = if (pagerState.currentPage == index) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(color)
                            )
                        }
                    }

                    // Next / Finish / Restart button
                    val isLastPage = pagerState.currentPage == pages.size - 1
                    val buttonText = if (isLastPage) {
                        if (areAllPermissionsGranted) {
                            stringResource(R.string.finish)
                        } else {
                            stringResource(R.string.restart_onboarding)
                        }
                    } else {
                        stringResource(R.string.next)
                    }

                    Button(
                        onClick = {
                            if (!isLastPage) {
                                scope.launch {
                                    if (pagerState.currentPage < pages.size - 1) {
                                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                    }
                                }
                            } else {
                                if (areAllPermissionsGranted) {
                                    onFinish()
                                } else {
                                    restartKey++
                                    scope.launch {
                                        pagerState.scrollToPage(0)
                                    }
                                }
                            }
                        }
                    ) {
                        Text(text = buttonText, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) { pageIndex ->
            when (pages[pageIndex]) {
                OnboardingStep.INTRO -> OnboardingIntroPage()
                OnboardingStep.CONTACTS_PERMISSION -> OnboardingContactsPage(
                    onPermissionGranted = {
                        scope.launch {
                            if (pagerState.currentPage < pages.size - 1) {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    }
                )
                OnboardingStep.CALENDAR_PERMISSION -> OnboardingCalendarPage(
                    onPermissionGranted = {
                        scope.launch {
                            if (pagerState.currentPage < pages.size - 1) {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    }
                )
                OnboardingStep.FINISH -> OnboardingFinishPage(
                    isWarningMode = !areAllPermissionsGranted,
                    onActivateAccount = onActivateAccount,
                    onIgnore = onFinish
                )
            }
        }
    }
}

@Composable
private fun AppIconImage(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val appIcon = remember(context) {
        try {
            context.packageManager.getApplicationIcon(context.packageName)
        } catch (_: Exception) {
            null
        }
    }

    if (appIcon != null) {
        AndroidView(
            factory = { ctx ->
                ImageView(ctx).apply {
                    setImageDrawable(appIcon)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
            },
            modifier = modifier
        )
    }
}

@Composable
private fun OnboardingIntroPage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AppIconImage(modifier = Modifier.size(110.dp))
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.onboarding_intro_title),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.onboarding_intro_text),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 18.sp
        )
    }
}

@Composable
private fun OnboardingContactsPage(onPermissionGranted: () -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) {
            onPermissionGranted()
        } else {
            Toast.makeText(context, R.string.onboarding_permission_denied, Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_contacts),
            contentDescription = null,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
            modifier = Modifier.size(100.dp)
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.onboarding_contacts_title),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.onboarding_contacts_text),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 18.sp
        )
        Spacer(modifier = Modifier.height(36.dp))
        Button(
            onClick = {
                launcher.launch(
                    arrayOf(
                        Manifest.permission.READ_CONTACTS,
                        Manifest.permission.GET_ACCOUNTS
                    )
                )
            }
        ) {
            Text(stringResource(R.string.onboarding_grant_permission), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun OnboardingCalendarPage(onPermissionGranted: () -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) {
            onPermissionGranted()
        } else {
            Toast.makeText(context, R.string.onboarding_permission_denied, Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_calendar),
            contentDescription = null,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
            modifier = Modifier.size(100.dp)
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.onboarding_calendar_title),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.onboarding_calendar_text),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 18.sp
        )
        Spacer(modifier = Modifier.height(36.dp))
        Button(
            onClick = {
                launcher.launch(
                    arrayOf(
                        Manifest.permission.READ_CALENDAR,
                        Manifest.permission.WRITE_CALENDAR
                    )
                )
            }
        ) {
            Text(stringResource(R.string.onboarding_grant_permission), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun OnboardingFinishPage(
    isWarningMode: Boolean,
    onActivateAccount: () -> Unit,
    onIgnore: () -> Unit
) {
    val context = LocalContext.current

    LaunchedEffect(isWarningMode) {
        if (!isWarningMode) {
            onActivateAccount()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AppIconImage(modifier = Modifier.size(110.dp))

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = if (isWarningMode) {
                stringResource(R.string.onboarding_finish_warning_title)
            } else {
                stringResource(R.string.onboarding_finish_title)
            },
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        val bodyText = if (isWarningMode) {
            stringResource(R.string.onboarding_finish_warning_text)
        } else {
            getCalendarSpecificText(context)
        }

        Text(
            text = bodyText,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 18.sp
        )

        Spacer(modifier = Modifier.height(36.dp))

        if (!isWarningMode) {
            Button(
                onClick = {
                    val builder = CalendarContract.CONTENT_URI.buildUpon()
                    builder.appendPath("time")
                    ContentUris.appendId(builder, Calendar.getInstance().timeInMillis)
                    val intent = Intent(Intent.ACTION_VIEW).setData(builder.build())
                    try {
                        context.startActivity(intent)
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(context, "No calendar app found", Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                Text(stringResource(R.string.pref_calendar_open), style = MaterialTheme.typography.titleMedium)
            }
        } else {
            OutlinedButton(onClick = onIgnore) {
                Text(stringResource(R.string.ignore), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

private fun getCalendarSpecificText(context: Context): String {
    val pm = context.packageManager
    val builder = CalendarContract.CONTENT_URI.buildUpon()
    builder.appendPath("time")
    ContentUris.appendId(builder, Calendar.getInstance().timeInMillis)
    val specificCalendarIntent = Intent(Intent.ACTION_VIEW).setData(builder.build())
    val defaultResolution = pm.resolveActivity(specificCalendarIntent, PackageManager.MATCH_DEFAULT_ONLY)

    val defaultPackageName = defaultResolution?.activityInfo?.packageName
    if ("com.google.android.calendar" == defaultPackageName) {
        return context.getString(R.string.onboarding_finish_text_google_calendar)
    }
    if ("com.samsung.android.calendar" == defaultPackageName) {
        return context.getString(R.string.onboarding_finish_text_samsung_calendar)
    }
    if ("org.fossify.calendar" == defaultPackageName) {
        return context.getString(R.string.onboarding_finish_text_fossify_calendar)
    }

    val genericCalendarIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR)
    val allCalendars = pm.queryIntentActivities(genericCalendarIntent, PackageManager.MATCH_DEFAULT_ONLY)

    var googleInstalled = false
    var samsungInstalled = false
    var fossifyInstalled = false

    for (info in allCalendars) {
        val pkg = info.activityInfo?.packageName
        if (pkg == "com.google.android.calendar") googleInstalled = true
        if (pkg == "com.samsung.android.calendar") samsungInstalled = true
        if (pkg == "org.fossify.calendar") fossifyInstalled = true
    }

    return when {
        googleInstalled -> context.getString(R.string.onboarding_finish_text_google_calendar)
        samsungInstalled -> context.getString(R.string.onboarding_finish_text_samsung_calendar)
        fossifyInstalled -> context.getString(R.string.onboarding_finish_text_fossify_calendar)
        else -> context.getString(R.string.onboarding_finish_text)
    }
}
