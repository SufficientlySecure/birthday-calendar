/*
 * Copyright (C) 2025-2026 Matthias Heinisch <birthdayadapter@heinisch.fr>
 * Copyright (C) 2012-2016 Dominik Schürmann <dominik@dominikschuermann.de>
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

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TimePicker as ViewTimePicker
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.preference.PreferenceManager
import fr.heinisch.birthdayadapter.R
import fr.heinisch.birthdayadapter.util.PreferencesHelper
import fr.heinisch.birthdayadapter.util.PurchaseHelperFactory
import fr.heinisch.birthdayadapter.util.VersionHelper
import kotlin.math.abs
import java.util.regex.Pattern

class ExtendedPreferencesFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                BirthdayAdapterTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        ExtendedPreferencesScreen()
                    }
                }
            }
        }
    }
}

private val baseColors = intArrayOf(
    0xfff44336.toInt(), 0xffe91e63.toInt(), 0xff9c27b0.toInt(), 0xff673ab7.toInt(), 0xff3f51b5.toInt(), 0xff2196f3.toInt(), 0xff03a9f4.toInt(), 0xff00bcd4.toInt(),
    0xff009688.toInt(), 0xff4caf50.toInt(), 0xff8bc34a.toInt(), 0xffcddc39.toInt(), 0xffffeb3b.toInt(), 0xffffc107.toInt(), 0xffff9800.toInt(), 0xffff5722.toInt(),
    0xff795548.toInt(), 0xff9e9e9e.toInt(), 0xff607d8b.toInt()
)

@Composable
private fun ExtendedPreferencesScreen() {
    val context = LocalContext.current
    val prefs = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    val isFullVersion = remember(context) { VersionHelper.isFullVersionUnlocked(context) }
    val purchaseHelper = remember { PurchaseHelperFactory.create() }

    // Lifecycle observer for updating background optimization checks when resuming app
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeTrigger by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                resumeTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var isIgnoringBattery by remember { mutableStateOf(false) }
    var isAutoRevokeWhitelisted by remember { mutableStateOf(true) }

    DisposableEffect(resumeTrigger, context) {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (pm != null) {
                isIgnoringBattery = pm.isIgnoringBatteryOptimizations(context.packageName)
            }
        } catch (_: Exception) {}

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                isAutoRevokeWhitelisted = context.packageManager.isAutoRevokeWhitelisted
            } catch (_: Exception) {}
        }
        onDispose {}
    }

    // States for preferences
    var currentColor by remember { mutableIntStateOf(PreferencesHelper.getColor(context)) }
    var groupFiltering by remember {
        mutableStateOf(prefs.getBoolean(context.getString(R.string.pref_group_filtering_key), true))
    }
    var nameFormat by remember {
        mutableStateOf(prefs.getBoolean(context.getString(R.string.pref_name_format_key), true))
    }
    var titleEnable by remember {
        mutableStateOf(prefs.getBoolean(context.getString(R.string.pref_title_enable_key), false))
    }
    var jubileeYears by remember {
        mutableStateOf(PreferencesHelper.getJubileeYears(context) ?: "")
    }

    // Reminder event types
    val eventTypeValues = context.resources.getStringArray(R.array.pref_reminder_event_types_values).toList()
    val eventTypeEntries = context.resources.getStringArray(R.array.pref_reminder_event_types_entries).toList()
    val defaultReminderTypes = context.resources.getStringArray(R.array.pref_reminder_event_types_default_values).toSet()
    var reminderTypes by remember {
        mutableStateOf(prefs.getStringSet(context.getString(R.string.pref_reminder_event_types), defaultReminderTypes) ?: defaultReminderTypes)
    }

    // Reminder minutes lists
    var reminderMinutesList by remember {
        mutableStateOf(PreferencesHelper.getAllReminderMinutes(context).toList())
    }
    var additionalReminderMinutesList by remember {
        mutableStateOf(PreferencesHelper.getAdditionalReminderMinutes(context).toList())
    }

    // Additional reminders group name
    var additionalRemindersGroup by remember {
        mutableStateOf(prefs.getString(context.getString(R.string.pref_additional_reminders_group_key), "") ?: "")
    }

    // Title formats (Triple of keyRes, titleRes, dialogMessageRes)
    val titleConfigs = listOf(
        Triple(R.string.pref_title_birthday_without_age_key, R.string.pref_title_birthday_without_age, R.string.pref_title_summary_without_age),
        Triple(R.string.pref_title_birthday_with_age_key, R.string.pref_title_birthday_with_age, R.string.pref_title_summary_with_age),
        Triple(R.string.pref_title_anniversary_without_age_key, R.string.pref_title_anniversary_without_age, R.string.pref_title_summary_without_age),
        Triple(R.string.pref_title_anniversary_with_age_key, R.string.pref_title_anniversary_with_age, R.string.pref_title_summary_with_age),
        Triple(R.string.pref_title_other_without_age_key, R.string.pref_title_other_without_age, R.string.pref_title_summary_without_age),
        Triple(R.string.pref_title_other_with_age_key, R.string.pref_title_other_with_age, R.string.pref_title_summary_with_age),
        Triple(R.string.pref_title_custom_without_age_key, R.string.pref_title_custom_without_age, R.string.pref_title_summary_custom_without_age),
        Triple(R.string.pref_title_custom_with_age_key, R.string.pref_title_custom_with_age, R.string.pref_title_summary_custom_with_age)
    )

    var titleValues by remember {
        mutableStateOf(
            titleConfigs.associate { (keyRes, _, _) ->
                val key = context.getString(keyRes)
                val defaultVal = when (keyRes) {
                    R.string.pref_title_birthday_without_age_key -> context.getString(R.string.event_title_birthday_without_age)
                    R.string.pref_title_birthday_with_age_key -> context.getString(R.string.event_title_birthday_with_age)
                    R.string.pref_title_anniversary_without_age_key -> context.getString(R.string.event_title_anniversary_without_age)
                    R.string.pref_title_anniversary_with_age_key -> context.getString(R.string.event_title_anniversary_with_age)
                    R.string.pref_title_other_without_age_key -> context.getString(R.string.event_title_other_without_age)
                    R.string.pref_title_other_with_age_key -> context.getString(R.string.event_title_other_with_age)
                    R.string.pref_title_custom_without_age_key -> context.getString(R.string.event_title_custom_without_age)
                    else -> context.getString(R.string.event_title_custom_with_age)
                }
                key to (prefs.getString(key, defaultVal) ?: defaultVal)
            }
        )
    }

    // Dialog state
    var activeDialog by remember { mutableStateOf<DialogConfig?>(null) }

    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            when (key) {
                context.getString(R.string.pref_group_filtering_key) -> groupFiltering = sharedPreferences.getBoolean(key, true)
                context.getString(R.string.pref_name_format_key) -> nameFormat = sharedPreferences.getBoolean(key, true)
                context.getString(R.string.pref_title_enable_key) -> titleEnable = sharedPreferences.getBoolean(key, false)
                context.getString(R.string.pref_jubilee_years_key) -> jubileeYears = PreferencesHelper.getJubileeYears(context) ?: ""
                context.getString(R.string.pref_color_key) -> currentColor = PreferencesHelper.getColor(context)
                context.getString(R.string.pref_reminders_key) -> reminderMinutesList = PreferencesHelper.getAllReminderMinutes(context).toList()
                context.getString(R.string.pref_additional_reminders_key) -> additionalReminderMinutesList = PreferencesHelper.getAdditionalReminderMinutes(context).toList()
                context.getString(R.string.pref_reminder_event_types) -> {
                    reminderTypes = sharedPreferences.getStringSet(key, defaultReminderTypes) ?: defaultReminderTypes
                }
                context.getString(R.string.pref_additional_reminders_group_key) -> {
                    additionalRemindersGroup = sharedPreferences.getString(key, "") ?: ""
                }
                else -> {
                    titleConfigs.forEach { (keyRes, _, _) ->
                        val k = context.getString(keyRes)
                        if (key == k) {
                            val defaultVal = context.getString(R.string.event_title_birthday_without_age)
                            titleValues = titleValues + (k to (sharedPreferences.getString(k, defaultVal) ?: defaultVal))
                        }
                    }
                }
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        // Buy Section (if free version)
        if (!isFullVersion) {
            PreferenceCategoryHeader(title = stringResource(R.string.pref_buy))
            ClickablePreferenceItem(
                title = stringResource(R.string.pref_buy_full),
                summary = stringResource(R.string.pref_buy_full_summary),
                iconRes = R.drawable.ic_star,
                onClick = {
                    val act = context as? BaseActivity
                    if (act != null) {
                        purchaseHelper.launchBillingFlow(act)
                    }
                }
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        }

        // General Category (Color Picker)
        PreferenceCategoryHeader(title = stringResource(R.string.pref_general))
        ClickablePreferenceItem(
            title = stringResource(R.string.pref_color),
            summary = String.format("#%06X", (0xFFFFFF and currentColor)),
            iconCustom = {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(currentColor))
                )
            },
            onClick = {
                activeDialog = DialogConfig.ColorPicker(
                    initialColor = currentColor,
                    onColorSelected = { color ->
                        prefs.edit { putInt(context.getString(R.string.pref_color_key), color) }
                        currentColor = color
                        activeDialog = null
                    },
                    onOpenHex = {
                        activeDialog = DialogConfig.HexInput(
                            initialColor = currentColor,
                            onColorSelected = { color ->
                                prefs.edit { putInt(context.getString(R.string.pref_color_key), color) }
                                currentColor = color
                                activeDialog = null
                            }
                        )
                    }
                )
            }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // Reminders Category (if full version)
        if (isFullVersion) {
            PreferenceCategoryHeader(title = stringResource(R.string.pref_reminders))
            val selectedReminderEntries = eventTypeEntries.indices
                .filter { reminderTypes.contains(eventTypeValues[it].toString()) }
                .map { eventTypeEntries[it] }
            val reminderSummary = if (selectedReminderEntries.isEmpty()) {
                stringResource(R.string.no_events_selected)
            } else {
                selectedReminderEntries.joinToString(", ")
            }

            ClickablePreferenceItem(
                title = stringResource(R.string.pref_events_with_reminders),
                summary = reminderSummary,
                onClick = {
                    activeDialog = DialogConfig.MultiSelect(
                        title = context.getString(R.string.pref_events_with_reminders),
                        entries = eventTypeEntries,
                        entryValues = eventTypeValues,
                        initialValues = reminderTypes,
                        onSave = { newValues ->
                            prefs.edit { putStringSet(context.getString(R.string.pref_reminder_event_types), newValues) }
                            reminderTypes = newValues
                        }
                    )
                }
            )

            // Dynamic Reminders List (Long Click supported to delete)
            reminderMinutesList.forEachIndexed { index, minutes ->
                val summaryStr = ReminderPreferenceCompat.getSummary(context, minutes)
                ClickablePreferenceItem(
                    title = "${context.getString(R.string.pref_reminder_time)} ${index + 1}",
                    summary = summaryStr,
                    onClick = {
                        activeDialog = DialogConfig.ReminderTime(
                            title = "${context.getString(R.string.pref_reminder_time)} ${index + 1}",
                            initialMinutes = minutes,
                            onSave = { newMinutes ->
                                val updated = reminderMinutesList.toMutableList()
                                updated[index] = newMinutes
                                reminderMinutesList = updated
                                val set = updated.map { it.toString() }.toSet()
                                prefs.edit { putStringSet(context.getString(R.string.pref_reminders_key), set) }
                            },
                            onDelete = {
                                val updated = reminderMinutesList.toMutableList()
                                updated.removeAt(index)
                                reminderMinutesList = updated
                                val set = updated.map { it.toString() }.toSet()
                                prefs.edit { putStringSet(context.getString(R.string.pref_reminders_key), set) }
                            }
                        )
                    },
                    onLongClick = {
                        activeDialog = DialogConfig.ConfirmDeleteReminder(
                            onConfirm = {
                                val updated = reminderMinutesList.toMutableList()
                                updated.removeAt(index)
                                reminderMinutesList = updated
                                val set = updated.map { it.toString() }.toSet()
                                prefs.edit { putStringSet(context.getString(R.string.pref_reminders_key), set) }
                            }
                        )
                    }
                )
            }

            ClickablePreferenceItem(
                title = stringResource(R.string.add_reminder),
                summary = "",
                iconRes = R.drawable.ic_add,
                onClick = {
                    activeDialog = DialogConfig.ReminderTime(
                        title = context.getString(R.string.add_reminder),
                        initialMinutes = context.resources.getInteger(R.integer.pref_reminder_time_def),
                        onSave = { newMinutes ->
                            val updated = reminderMinutesList.toMutableList()
                            updated.add(newMinutes)
                            reminderMinutesList = updated
                            val set = updated.map { it.toString() }.toSet()
                            prefs.edit { putStringSet(context.getString(R.string.pref_reminders_key), set) }
                        },
                        onDelete = null
                    )
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Additional Reminders Category
            PreferenceCategoryHeader(title = stringResource(R.string.pref_additional_reminders_category))
            ClickablePreferenceItem(
                title = stringResource(R.string.pref_additional_reminders_group_title),
                summary = additionalRemindersGroup.ifEmpty { stringResource(R.string.pref_additional_reminders_group_def) },
                onClick = {
                    val key = context.getString(R.string.pref_additional_reminders_group_key)
                    activeDialog = DialogConfig.EditText(
                        title = context.getString(R.string.pref_additional_reminders_group_title),
                        message = context.getString(R.string.pref_additional_reminders_group_description),
                        initialValue = additionalRemindersGroup,
                        onSave = { newValue ->
                            prefs.edit { putString(key, newValue) }
                        }
                    )
                }
            )

            additionalReminderMinutesList.forEachIndexed { index, minutes ->
                val summaryStr = ReminderPreferenceCompat.getSummary(context, minutes)
                ClickablePreferenceItem(
                    title = "${context.getString(R.string.pref_reminder_time)} ${index + 1}",
                    summary = summaryStr,
                    onClick = {
                        activeDialog = DialogConfig.ReminderTime(
                            title = "${context.getString(R.string.pref_reminder_time)} ${index + 1}",
                            initialMinutes = minutes,
                            onSave = { newMinutes ->
                                val updated = additionalReminderMinutesList.toMutableList()
                                updated[index] = newMinutes
                                additionalReminderMinutesList = updated
                                val set = updated.map { it.toString() }.toSet()
                                prefs.edit { putStringSet(context.getString(R.string.pref_additional_reminders_key), set) }
                            },
                            onDelete = {
                                val updated = additionalReminderMinutesList.toMutableList()
                                updated.removeAt(index)
                                additionalReminderMinutesList = updated
                                val set = updated.map { it.toString() }.toSet()
                                prefs.edit { putStringSet(context.getString(R.string.pref_additional_reminders_key), set) }
                            }
                        )
                    },
                    onLongClick = {
                        activeDialog = DialogConfig.ConfirmDeleteReminder(
                            onConfirm = {
                                val updated = additionalReminderMinutesList.toMutableList()
                                updated.removeAt(index)
                                additionalReminderMinutesList = updated
                                val set = updated.map { it.toString() }.toSet()
                                prefs.edit { putStringSet(context.getString(R.string.pref_additional_reminders_key), set) }
                            }
                        )
                    }
                )
            }

            ClickablePreferenceItem(
                title = stringResource(R.string.add_reminder),
                summary = "",
                iconRes = R.drawable.ic_add,
                onClick = {
                    activeDialog = DialogConfig.ReminderTime(
                        title = context.getString(R.string.add_reminder),
                        initialMinutes = context.resources.getInteger(R.integer.pref_additional_reminder_time_def),
                        onSave = { newMinutes ->
                            val updated = additionalReminderMinutesList.toMutableList()
                            updated.add(newMinutes)
                            additionalReminderMinutesList = updated
                            val set = updated.map { it.toString() }.toSet()
                            prefs.edit { putStringSet(context.getString(R.string.pref_additional_reminders_key), set) }
                        },
                        onDelete = null
                    )
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Title Category
            PreferenceCategoryHeader(title = stringResource(R.string.pref_title))
            SwitchPreferenceItem(
                title = stringResource(R.string.pref_title_enable),
                summary = stringResource(R.string.pref_title_enable_summary),
                checked = titleEnable,
                onCheckedChange = { checked ->
                    titleEnable = checked
                    prefs.edit { putBoolean(context.getString(R.string.pref_title_enable_key), checked) }
                }
            )

            if (titleEnable) {
                titleConfigs.forEach { (keyRes, titleRes, msgRes) ->
                    val prefKey = context.getString(keyRes)
                    val prefTitle = stringResource(titleRes)
                    val prefMsg = stringResource(msgRes)
                    val currentValue = titleValues[prefKey] ?: ""

                    val useLastNameFirst = PreferencesHelper.getUseLastNameFirst(context)
                    val firstName = stringResource(R.string.summary_placeholder_firstname)
                    val lastName = stringResource(R.string.summary_placeholder_lastname)
                    val placeholderName = if (useLastNameFirst) "$lastName, $firstName" else "$firstName $lastName"
                    val formattedSummary = currentValue
                        .replace("{NAME}", placeholderName)
                        .replace("{FIRSTNAME}", firstName)
                        .replace("{AGE}", "42")
                        .replace("{LABEL}", stringResource(R.string.summary_placeholder_custom_label))

                    ClickablePreferenceItem(
                        title = prefTitle,
                        summary = formattedSummary,
                        onClick = {
                            activeDialog = DialogConfig.EditText(
                                title = prefTitle,
                                message = prefMsg,
                                initialValue = currentValue,
                                onSave = { newValue ->
                                    prefs.edit { putString(prefKey, newValue) }
                                }
                            )
                        }
                    )
                }
            }

            ClickablePreferenceItem(
                title = stringResource(R.string.pref_jubilee_years_title),
                summary = jubileeYears,
                onClick = {
                    val key = context.getString(R.string.pref_jubilee_years_key)
                    activeDialog = DialogConfig.EditText(
                        title = context.getString(R.string.pref_jubilee_years_title),
                        message = context.getString(R.string.pref_jubilee_years_description),
                        initialValue = jubileeYears,
                        validator = { text ->
                            text.isEmpty() || Pattern.matches("^[1-9][0-9]*(?:\\s*,\\s*[1-9][0-9]*)*$", text.trim())
                        },
                        onSave = { newValue ->
                            prefs.edit { putString(key, newValue) }
                        }
                    )
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        }

        // Advanced Category
        PreferenceCategoryHeader(title = stringResource(R.string.pref_advanced_category))
        SwitchPreferenceItem(
            title = stringResource(R.string.pref_group_filtering_title),
            summary = stringResource(R.string.pref_group_filtering_summary),
            checked = groupFiltering,
            onCheckedChange = { checked ->
                groupFiltering = checked
                prefs.edit { putBoolean(context.getString(R.string.pref_group_filtering_key), checked) }
            }
        )

        SwitchPreferenceItem(
            title = stringResource(R.string.pref_name_format_title),
            summary = stringResource(R.string.pref_name_format_summary),
            checked = nameFormat,
            onCheckedChange = { checked ->
                nameFormat = checked
                prefs.edit { putBoolean(context.getString(R.string.pref_name_format_key), checked) }
            }
        )

        // Background Category
        if (isFullVersion) {
            if (!isIgnoringBattery || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !isAutoRevokeWhitelisted)) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                PreferenceCategoryHeader(title = stringResource(R.string.pref_background_category))

                if (!isIgnoringBattery) {
                    ClickablePreferenceItem(
                        title = stringResource(R.string.pref_ignore_battery_optimizations_title),
                        summary = stringResource(R.string.pref_ignore_battery_optimizations_summary),
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = ("package:" + context.packageName).toUri()
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                try {
                                    val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    context.startActivity(fallback)
                                } catch (_: Exception) {}
                            }
                        }
                    )
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !isAutoRevokeWhitelisted) {
                    ClickablePreferenceItem(
                        title = stringResource(R.string.pref_disable_permission_monitoring_title),
                        summary = stringResource(R.string.pref_disable_permission_monitoring_summary),
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = ("package:" + context.packageName).toUri()
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        }
                    )
                }
            }
        }
    }

    // Active Dialog handling
    when (val dialog = activeDialog) {
        is DialogConfig.EditText -> {
            var textVal by remember(dialog) { mutableStateOf(dialog.initialValue) }
            var isError by remember(dialog) { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { activeDialog = null },
                title = { Text(text = dialog.title) },
                text = {
                    Column {
                        if (dialog.message.isNotEmpty()) {
                            Text(
                                text = dialog.message,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        OutlinedTextField(
                            value = textVal,
                            onValueChange = {
                                textVal = it
                                isError = dialog.validator != null && !dialog.validator(it)
                            },
                            isError = isError,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (isError) {
                            Text(
                                text = stringResource(R.string.invalid_format),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (!isError) {
                                dialog.onSave(textVal)
                                activeDialog = null
                            }
                        },
                        enabled = !isError
                    ) {
                        Text(stringResource(android.R.string.ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { activeDialog = null }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            )
        }

        is DialogConfig.ConfirmDeleteReminder -> {
            AlertDialog(
                onDismissRequest = { activeDialog = null },
                title = { Text(text = stringResource(R.string.delete_reminder_title)) },
                text = { Text(text = stringResource(R.string.delete_reminder_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        dialog.onConfirm()
                        activeDialog = null
                    }) {
                        Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { activeDialog = null }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            )
        }

        is DialogConfig.ReminderTime -> {
            val oneDayMinutes = 24 * 60
            var day = (dialog.initialMinutes + oneDayMinutes) / oneDayMinutes
            if (dialog.initialMinutes % oneDayMinutes == 0) day--
            val dayBaseValues = intArrayOf(0, 1, 2, 3, 5, 7, 10, 14)
            val dayOptions = context.resources.getStringArray(R.array.pref_reminder_time_drop_down)
            var initialDayIndex = 0
            for (i in dayBaseValues.indices) {
                if (day == dayBaseValues[i]) {
                    initialDayIndex = i
                    break
                }
            }
            val timeFromMinutes = abs(dialog.initialMinutes - (day * oneDayMinutes))
            val initHour = timeFromMinutes / 60
            val initMinute = timeFromMinutes % 60

            var selectedDayIndex by remember(dialog) { mutableIntStateOf(initialDayIndex) }
            var hour by remember(dialog) { mutableIntStateOf(initHour) }
            var minute by remember(dialog) { mutableIntStateOf(initMinute) }

            AlertDialog(
                onDismissRequest = { activeDialog = null },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        DropdownSelector(
                            selectedValue = dayOptions.getOrElse(selectedDayIndex) { dayOptions[0] },
                            options = dayOptions,
                            onOptionSelected = { idx ->
                                selectedDayIndex = idx
                            }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Stable Android Framework TimePicker View (Zero experimental Compose APIs!)
                        AndroidView(
                            factory = { ctx ->
                                ViewTimePicker(ctx).apply {
                                    setIs24HourView(DateFormat.is24HourFormat(ctx))
                                    @Suppress("DEPRECATION")
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        this.hour = initHour
                                        this.minute = initMinute
                                    } else {
                                        currentHour = initHour
                                        currentMinute = initMinute
                                    }
                                    setOnTimeChangedListener { _, h, m ->
                                        hour = h
                                        minute = m
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val selectedDayValue = dayBaseValues[selectedDayIndex]
                        val minutes = (selectedDayValue * oneDayMinutes) - (hour * 60) - minute
                        dialog.onSave(minutes)
                        activeDialog = null
                    }) {
                        Text(stringResource(android.R.string.ok))
                    }
                },
                dismissButton = {
                    Row {
                        if (dialog.onDelete != null) {
                            TextButton(onClick = {
                                dialog.onDelete.invoke()
                                activeDialog = null
                            }) {
                                Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        TextButton(onClick = { activeDialog = null }) {
                            Text(stringResource(android.R.string.cancel))
                        }
                    }
                }
            )
        }

        is DialogConfig.MultiSelect -> {
            var selectedValues by remember(dialog) { mutableStateOf(dialog.initialValues) }

            AlertDialog(
                onDismissRequest = { activeDialog = null },
                title = { Text(text = dialog.title) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        dialog.entryValues.indices.forEach { i ->
                            val value = dialog.entryValues[i].toString()
                            val entryLabel = dialog.entries[i].toString()
                            val isChecked = selectedValues.contains(value)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedValues = if (isChecked) {
                                            selectedValues - value
                                        } else {
                                            selectedValues + value
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        selectedValues = if (checked) {
                                            selectedValues + value
                                        } else {
                                            selectedValues - value
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = entryLabel,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        dialog.onSave(selectedValues)
                        activeDialog = null
                    }) {
                        Text(stringResource(android.R.string.ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { activeDialog = null }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            )
        }

        is DialogConfig.ColorPicker -> {
            AlertDialog(
                onDismissRequest = { activeDialog = null },
                title = { Text(text = stringResource(R.string.pref_color)) },
                text = {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(baseColors.toList()) { colorInt ->
                            val isSelected = (colorInt and 0xFFFFFF) == (dialog.initialColor and 0xFFFFFF)
                            val r = (colorInt shr 16) and 0xFF
                            val g = (colorInt shr 8) and 0xFF
                            val b = colorInt and 0xFF
                            val brightness = (r * 299 + g * 587 + b * 114) / 1000
                            val checkColor = if (brightness > 160) Color.Black else Color.White

                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .padding(4.dp)
                                    .clip(CircleShape)
                                    .background(Color(colorInt))
                                    .clickable {
                                        dialog.onColorSelected(colorInt)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = checkColor,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                        item {
                            Surface(
                                onClick = dialog.onOpenHex,
                                modifier = Modifier
                                    .size(48.dp)
                                    .padding(4.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(text = "HEX", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { activeDialog = null }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            )
        }

        is DialogConfig.HexInput -> {
            var hexText by remember(dialog) {
                mutableStateOf(String.format("#%06X", (0xFFFFFF and dialog.initialColor)))
            }
            var parseError by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { activeDialog = null },
                title = { Text(text = stringResource(R.string.hex_color_title)) },
                text = {
                    Column {
                        OutlinedTextField(
                            value = hexText,
                            onValueChange = {
                                hexText = it
                                parseError = try {
                                    it.toColorInt()
                                    false
                                } catch (_: Exception) {
                                    true
                                }
                            },
                            isError = parseError,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (parseError) {
                            Text(
                                text = stringResource(R.string.invalid_format),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            try {
                                val color = hexText.toColorInt()
                                dialog.onColorSelected(color)
                            } catch (_: Exception) {}
                        },
                        enabled = !parseError
                    ) {
                        Text(stringResource(android.R.string.ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { activeDialog = null }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            )
        }

        null -> {}
    }
}

private sealed class DialogConfig {
    data class EditText(
        val title: String,
        val message: String,
        val initialValue: String,
        val validator: ((String) -> Boolean)? = null,
        val onSave: (String) -> Unit
    ) : DialogConfig()

    data class ConfirmDeleteReminder(
        val onConfirm: () -> Unit
    ) : DialogConfig()

    data class ReminderTime(
        val title: String,
        val initialMinutes: Int,
        val onSave: (Int) -> Unit,
        val onDelete: (() -> Unit)?
    ) : DialogConfig()

    data class MultiSelect(
        val title: String,
        val entries: List<CharSequence>,
        val entryValues: List<CharSequence>,
        val initialValues: Set<String>,
        val onSave: (Set<String>) -> Unit
    ) : DialogConfig()

    data class ColorPicker(
        val initialColor: Int,
        val onColorSelected: (Int) -> Unit,
        val onOpenHex: () -> Unit
    ) : DialogConfig()

    data class HexInput(
        val initialColor: Int,
        val onColorSelected: (Int) -> Unit
    ) : DialogConfig()
}

@Composable
private fun PreferenceCategoryHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun SwitchPreferenceItem(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        onClick = { onCheckedChange(!checked) },
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
private fun DropdownSelector(
    selectedValue: String,
    options: Array<String>,
    onOptionSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        onClick = { expanded = true },
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = selectedValue,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onOptionSelected(index)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ClickablePreferenceItem(
    title: String,
    summary: String,
    iconRes: Int = 0,
    iconCustom: @Composable (() -> Unit)? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    if (onLongClick != null) {
        val interactionSource = remember { MutableInteractionSource() }
        Surface(
            color = Color.Transparent,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    onClick = {}
                )
                .pointerInput(onClick, onLongClick) {
                    detectTapGestures(
                        onPress = { offset ->
                            val press = PressInteraction.Press(offset)
                            interactionSource.emit(press)
                            val released = tryAwaitRelease()
                            if (released) {
                                interactionSource.emit(PressInteraction.Release(press))
                            } else {
                                interactionSource.emit(PressInteraction.Cancel(press))
                            }
                        },
                        onTap = { onClick() },
                        onLongPress = { onLongClick() }
                    )
                }
        ) {
            PreferenceItemContent(title, summary, iconRes, iconCustom)
        }
    } else {
        Surface(
            onClick = onClick,
            color = Color.Transparent,
            modifier = Modifier.fillMaxWidth()
        ) {
            PreferenceItemContent(title, summary, iconRes, iconCustom)
        }
    }
}

@Composable
private fun PreferenceItemContent(
    title: String,
    summary: String,
    iconRes: Int = 0,
    iconCustom: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconCustom != null) {
            iconCustom()
            Spacer(modifier = Modifier.width(16.dp))
        } else if (iconRes != 0) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (summary.isNotEmpty()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
