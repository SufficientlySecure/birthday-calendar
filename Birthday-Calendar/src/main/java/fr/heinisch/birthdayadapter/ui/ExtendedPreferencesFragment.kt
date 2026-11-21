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

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.preference.EditTextPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import fr.heinisch.birthdayadapter.R
import fr.heinisch.birthdayadapter.util.AccountHelper
import fr.heinisch.birthdayadapter.util.IPurchaseHelper
import fr.heinisch.birthdayadapter.util.PreferencesHelper
import fr.heinisch.birthdayadapter.util.PurchaseHelperFactory
import fr.heinisch.birthdayadapter.util.ReviewHelper
import fr.heinisch.birthdayadapter.util.ReviewHelperFactory
import fr.heinisch.birthdayadapter.util.VersionHelper
import fr.heinisch.birthdayadapter.util.VersionHelper.isFullVersionUnlocked
import java.util.Arrays
import java.util.HashSet
import java.util.Objects
import java.util.regex.Pattern

class ExtendedPreferencesFragment : PreferenceFragmentCompat() {

    var mActivity: BaseActivity? = null
    private lateinit var mAccountHelper: AccountHelper
    private var colorPref: Preference? = null
    private var mJubileeYearsPref: Preference? = null
    private lateinit var mPurchaseHelper: IPurchaseHelper
    private lateinit var mReviewHelper: ReviewHelper
    private var mTitlePrefKeys: Set<String>? = null

    private val mSettingsListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
        if (key == null || activity == null) {
            return@OnSharedPreferenceChangeListener
        }

        // Handle purchase change
        if (key == VersionHelper.PREF_FULL_VERSION_PURCHASED) {
            activity?.recreate()
            return@OnSharedPreferenceChangeListener
        }

        // Handle title summary updates
        if (mTitlePrefKeys != null && mTitlePrefKeys!!.contains(key)) {
            val pref = findPreference<Preference>(key)
            if (pref != null) {
                val newValue = sharedPreferences.getString(key, "") ?: ""
                updateTitlePreferenceSummary(pref, newValue)
            }
        }

        if (key == getString(R.string.pref_name_format_key)) {
            // Update all title preference summaries when the name format changes
            mTitlePrefKeys?.let { keys ->
                for (titleKey in keys) {
                    val pref = findPreference<Preference>(titleKey)
                    if (pref is EditTextPreference) {
                        val value = pref.text ?: ""
                        updateTitlePreferenceSummary(pref, value)
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

    override fun onCreatePreferences(bundle: Bundle?, s: String?) {
        // Use the default shared preferences to ensure consistency across the app.
        addPreferencesFromResource(R.xml.pref_preferences)

        val isFullVersion = isFullVersionUnlocked(requireContext())

        val remindersCategory = findPreference<PreferenceCategory>(getString(R.string.pref_reminders_category_key))
        remindersCategory?.isVisible = isFullVersion

        val additionalRemindersCategory = findPreference<PreferenceCategory>(getString(R.string.pref_additional_reminders_category_key))
        additionalRemindersCategory?.isVisible = isFullVersion

        val titleCategory = findPreference<PreferenceCategory>(getString(R.string.pref_title_category_key))
        titleCategory?.isVisible = isFullVersion

        val advancedCategory = findPreference<PreferenceCategory>(getString(R.string.pref_advanced_category_key))
        advancedCategory?.isVisible = isFullVersion
        val preferDdSlashMm = findPreference<Preference>(getString(R.string.pref_prefer_dd_slash_mm_key))
        preferDdSlashMm?.isVisible = false

        val backgroundCategory = findPreference<PreferenceCategory>(getString(R.string.pref_background_category_key))
        backgroundCategory?.isVisible = isFullVersion

        val buyCategory = findPreference<PreferenceCategory>(getString(R.string.pref_buy_category_key))
        buyCategory?.isVisible = !isFullVersion

        val buyFullPref = findPreference<Preference>(getString(R.string.pref_buy_full_key))
        buyFullPref?.setOnPreferenceClickListener {
            activity?.let { act ->
                mPurchaseHelper.launchBillingFlow(act)
            }
            true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val activity = activity
        if (activity is BaseActivity) {
            mActivity = activity
        } else {
            return
        }

        mAccountHelper = AccountHelper(mActivity!!)
        mPurchaseHelper = PurchaseHelperFactory.create()
        mReviewHelper = ReviewHelperFactory.create()

        if (context != null && !isFullVersionUnlocked(requireContext())) {
            val buyFullPref = findPreference<Preference>(getString(R.string.pref_buy_full_key))
            if (buyFullPref != null && activity != null) {
                val originalTitle = buyFullPref.title

                mPurchaseHelper.queryProductDetails(requireActivity(), object : IPurchaseHelper.OnPriceFoundCallback {
                    override fun onPriceFound(price: String) {
                        if (isAdded) {
                            buyFullPref.title = getString(R.string.buy_premium_for, price)
                        }
                    }

                    override fun onPriceQueryFailed() {
                        if (isAdded) {
                            buyFullPref.title = originalTitle
                        }
                    }
                })
            }
        }

        if (context != null && isFullVersionUnlocked(requireContext())) {
            val remindersCategory = findPreference<PreferenceCategory>(getString(R.string.pref_reminders_category_key))
            if (remindersCategory != null) {
                val reminderTypesPref = findPreference<MultiSelectListPreference>(getString(R.string.pref_reminder_event_types))
                if (reminderTypesPref != null) {
                    updateReminderEventTypesSummary(reminderTypesPref)
                    reminderTypesPref.setOnPreferenceChangeListener { preference, newValue ->
                        mAccountHelper.triggerFullResync()
                        @Suppress("UNCHECKED_CAST")
                        val values = newValue as Set<String>
                        updateReminderEventTypesSummary(reminderTypesPref, values)
                        true
                    }
                }
                populateReminders(remindersCategory, getString(R.string.pref_reminders_key))
            }

            val additionalRemindersCategory = findPreference<PreferenceCategory>(getString(R.string.pref_additional_reminders_category_key))
            if (additionalRemindersCategory != null) {
                populateReminders(additionalRemindersCategory, getString(R.string.pref_additional_reminders_key))
            }

            mTitlePrefKeys = HashSet(
                listOf(
                    getString(R.string.pref_title_birthday_without_age_key),
                    getString(R.string.pref_title_birthday_with_age_key),
                    getString(R.string.pref_title_anniversary_without_age_key),
                    getString(R.string.pref_title_anniversary_with_age_key),
                    getString(R.string.pref_title_other_without_age_key),
                    getString(R.string.pref_title_other_with_age_key),
                    getString(R.string.pref_title_custom_without_age_key),
                    getString(R.string.pref_title_custom_with_age_key)
                )
            )

            // Title preferences
            for (key in mTitlePrefKeys!!) {
                setupTitlePreference(key)
            }

            mJubileeYearsPref = findPreference(getString(R.string.pref_jubilee_years_key))
            mJubileeYearsPref?.setOnPreferenceClickListener {
                showJubileeYearsInputDialog()
                true
            }

            val ignoreBatteryOptimizationsPref = findPreference<Preference>(getString(R.string.pref_ignore_battery_optimizations_key))
            ignoreBatteryOptimizationsPref?.setOnPreferenceClickListener {
                if (!isAdded) {
                    return@setOnPreferenceClickListener true
                }
                val context = requireContext()
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:" + context.packageName)
                }
                try {
                    startActivity(intent)
                } catch (e: android.content.ActivityNotFoundException) {
                    val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    startActivity(fallbackIntent)
                }
                true
            }

            val disablePermissionMonitoringPref = findPreference<Preference>(getString(R.string.pref_disable_permission_monitoring_key))
            if (disablePermissionMonitoringPref != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    disablePermissionMonitoringPref.setOnPreferenceClickListener {
                        if (!isAdded) {
                            return@setOnPreferenceClickListener true
                        }
                        val context = requireContext()
                        val intent: Intent
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            intent = Intent(Intent.ACTION_AUTO_REVOKE_PERMISSIONS).apply {
                                data = Uri.parse("package:" + context.packageName)
                            }
                        } else {
                            intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:" + context.packageName)
                            }
                        }
                        try {
                            startActivity(intent)
                        } catch (e: android.content.ActivityNotFoundException) {
                            val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:" + context.packageName)
                            }
                            startActivity(fallbackIntent)
                        }
                        true
                    }
                }
            }
        }

        colorPref = findPreference(getString(R.string.pref_color_key))
        if (colorPref != null) {
            updateColorPreferenceIcon()
            colorPref?.setOnPreferenceClickListener {
                showColorPickerDialog()
                true
            }
        }

        updateBackgroundPreferencesVisibility()

        // Trigger review dialog when reaching the bottom of the list
        val recyclerView = listView
        recyclerView?.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                // Check if the user can scroll further down
                if (!recyclerView.canScrollVertically(1)) {
                    activity?.let { act ->
                        mReviewHelper.maybeShowReviewDialog(act)
                    }
                }
            }
        })
    }

    private fun setupTitlePreference(key: String) {
        val preference = findPreference<EditTextPreference>(key)
        if (preference != null) {
            updateTitlePreferenceSummary(preference, preference.text ?: "")
        }
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (mTitlePrefKeys != null && mTitlePrefKeys!!.contains(preference.key)) {
            showTitleInputDialog(preference as EditTextPreference)
        } else if (getString(R.string.pref_reminder_event_types) == preference.key) {
            showMultiSelectListDialog(preference as MultiSelectListPreference)
        } else if (getString(R.string.pref_additional_reminders_group_key) == preference.key) {
            showGroupInputDialog(preference as EditTextPreference)
        } else {
            super.onDisplayPreferenceDialog(preference)
        }
    }

    private fun showTitleInputDialog(preference: EditTextPreference) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_preference_input, null)
        val textInput = dialogView.findViewById<EditText>(R.id.textInput)

        textInput.setText(preference.text)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(preference.dialogTitle)
            .setMessage(preference.dialogMessage)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newValue = textInput.text.toString()
                preference.text = newValue
                updateTitlePreferenceSummary(preference, newValue)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showGroupInputDialog(preference: EditTextPreference) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_preference_input, null)
        val textInput = dialogView.findViewById<EditText>(R.id.textInput)

        textInput.setText(preference.text)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(preference.dialogTitle)
            .setMessage(preference.dialogMessage)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newValue = textInput.text.toString()
                if (preference.callChangeListener(newValue)) {
                    preference.text = newValue
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showMultiSelectListDialog(preference: MultiSelectListPreference) {
        val entries = preference.entries
        val entryValues = preference.entryValues
        val values = preference.values
        val checkedItems = BooleanArray(entryValues.size)
        val newValues = HashSet(values)

        for (i in entryValues.indices) {
            checkedItems[i] = values.contains(entryValues[i].toString())
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(preference.title)
            .setMultiChoiceItems(entries, checkedItems) { _, which, isChecked ->
                if (isChecked) {
                    newValues.add(entryValues[which].toString())
                } else {
                    newValues.remove(entryValues[which].toString())
                }
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (preference.callChangeListener(newValues)) {
                    preference.values = newValues
                    updateReminderEventTypesSummary(preference, newValues)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun updateTitlePreferenceSummary(preference: Preference, newValue: Any) {
        val template = newValue as? String ?: return
        val useLastNameFirst = PreferencesHelper.getUseLastNameFirst(requireContext())
        val firstName = getString(R.string.summary_placeholder_firstname)
        val lastName = getString(R.string.summary_placeholder_lastname)
        val name = if (useLastNameFirst) "$lastName, $firstName" else "$firstName $lastName"

        val summary = template.replace("{NAME}", name)
            .replace("{FIRSTNAME}", firstName)
            .replace("{AGE}", "42")
            .replace("{LABEL}", getString(R.string.summary_placeholder_custom_label))
        preference.summary = summary
    }

    private fun updateReminderEventTypesSummary(preference: MultiSelectListPreference) {
        updateReminderEventTypesSummary(preference, preference.values)
    }

    private fun updateReminderEventTypesSummary(preference: MultiSelectListPreference, values: Set<String>) {
        if (context == null) return

        if (values.isEmpty()) {
            preference.setSummary(R.string.no_events_selected)
            return
        }

        val selectedEntries = ArrayList<String>()
        val entries = preference.entries
        val entryValues = preference.entryValues

        if (entries != null && entryValues != null) {
            for (i in entryValues.indices) {
                if (values.contains(entryValues[i].toString())) {
                    selectedEntries.add(entries[i].toString())
                }
            }
        }

        preference.summary = TextUtils.join(", ", selectedEntries)
    }

    private fun populateReminders(category: PreferenceCategory, prefKey: String) {
        val ctx = context ?: return

        val prefsToKeep = ArrayList<Preference>()
        for (i in 0 until category.preferenceCount) {
            val pref = category.getPreference(i)
            // Keep existing static preferences like event types or the group name
            if (pref is MultiSelectListPreference || pref is EditTextPreference) {
                prefsToKeep.add(pref)
            }
        }

        category.removeAll()

        for (pref in prefsToKeep) {
            category.addPreference(pref)
        }

        val reminderMinutes: IntArray = if (prefKey == getString(R.string.pref_reminders_key)) {
            PreferencesHelper.getAllReminderMinutes(ctx)
        } else {
            PreferencesHelper.getAdditionalReminderMinutes(ctx)
        }

        for (i in reminderMinutes.indices) {
            addReminderPreference(category, prefKey, reminderMinutes[i], i, false)
        }

        val addReminderPref = Preference(ctx).apply {
            setTitle(R.string.add_reminder)
            setIcon(R.drawable.ic_add)
            setOnPreferenceClickListener {
                addReminderPreference(category, prefKey, resources.getInteger(R.integer.pref_reminder_time_def), reminderMinutes.size, true)
                true
            }
        }
        category.addPreference(addReminderPref)
    }

    private fun addReminderPreference(category: PreferenceCategory, prefKey: String, minutes: Int, index: Int, isNew: Boolean) {
        val ctx = context ?: return

        val reminderPref = ReminderPreferenceCompat(ctx, null).apply {
            key = "${prefKey}_$index"
            title = "${getString(R.string.pref_reminder_time)} ${index + 1}"
            isPersistent = false // We are handling persistence manually
            setValue(minutes)
        }

        if (isNew) {
            reminderPref.setOnPreferenceChangeListener { _, _ ->
                // On OK, add to category and setup normal listeners
                setupReminderListeners(category, prefKey, reminderPref)
                category.addPreference(reminderPref)
                saveReminders(category, prefKey)
                true
            }
            reminderPref.performClick(true)
        } else {
            setupReminderListeners(category, prefKey, reminderPref)
            category.addPreference(reminderPref)
        }
    }

    private fun setupReminderListeners(category: PreferenceCategory, prefKey: String, reminderPref: ReminderPreferenceCompat) {
        reminderPref.setOnPreferenceChangeListener { _, _ ->
            saveReminders(category, prefKey)
            true
        }
        reminderPref.setOnRemoveListener { preference ->
            category.removePreference(preference)
            saveReminders(category, prefKey)
        }
        reminderPref.setOnCustomLongClickListener {
            showDeleteReminderDialog(category, prefKey, reminderPref)
            true
        }
    }

    private fun showDeleteReminderDialog(category: PreferenceCategory, prefKey: String, preference: Preference) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_reminder_title)
            .setMessage(R.string.delete_reminder_message)
            .setPositiveButton(R.string.delete) { _, _ ->
                category.removePreference(preference)
                saveReminders(category, prefKey)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun saveReminders(category: PreferenceCategory, prefKey: String) {
        val prefs = preferenceManager.sharedPreferences ?: return

        val reminderSet = HashSet<String>()
        for (i in 0 until category.preferenceCount) {
            val pref = category.getPreference(i)
            if (pref is ReminderPreferenceCompat) {
                reminderSet.add(pref.getValue().toString())
            }
        }

        prefs.edit().putStringSet(prefKey, reminderSet).apply()
        populateReminders(category, prefKey) // Repopulate to reflect changes
    }

    private fun updateJubileeYearsSummary() {
        if (mJubileeYearsPref != null && mActivity != null) {
            val jubileeYears = PreferencesHelper.getJubileeYears(mActivity!!)
            mJubileeYearsPref?.summary = jubileeYears
        }
    }

    private fun isValidJubileeYears(value: String): Boolean {
        if (TextUtils.isEmpty(value)) return true // allow empty
        return Pattern.matches("^[1-9][0-9]*(?:\\s*,\\s*[1-9][0-9]*)*$", value.trim())
    }

    private fun showJubileeYearsInputDialog() {
        val act = mActivity ?: return
        val dialogView = layoutInflater.inflate(R.layout.dialog_jubilee_input, null)
        val jubileeInput = dialogView.findViewById<EditText>(R.id.jubileeInput)
        val jubileeInputLayout = dialogView.findViewById<TextInputLayout>(R.id.jubileeInputLayout)

        val currentJubileeYears = PreferencesHelper.getJubileeYears(act)
        jubileeInput.setText(currentJubileeYears)

        val jubileeDialog = MaterialAlertDialogBuilder(act)
            .setTitle(R.string.pref_jubilee_years_title)
            .setMessage(R.string.pref_jubilee_years_description)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val jubileeYears = jubileeInput.text.toString()
                saveJubileeYears(jubileeYears)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        jubileeDialog.show()

        val positiveButton = jubileeDialog.getButton(AlertDialog.BUTTON_POSITIVE)
        positiveButton.isEnabled = isValidJubileeYears(currentJubileeYears ?: "")

        jubileeInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val text = s?.toString() ?: ""
                if (isValidJubileeYears(text)) {
                    positiveButton.isEnabled = true
                    jubileeInputLayout?.error = null
                } else {
                    positiveButton.isEnabled = false
                    jubileeInputLayout?.error = getString(R.string.invalid_format)
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun saveJubileeYears(jubileeYears: String) {
        val editor = preferenceManager.sharedPreferences!!.edit()
        editor.putString(getString(R.string.pref_jubilee_years_key), jubileeYears)
        editor.apply()
        updateJubileeYearsSummary()
    }

    private fun showColorPickerDialog() {
        val act = mActivity ?: return
        val dialogView = layoutInflater.inflate(R.layout.dialog_color_picker, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.colorPicker)

        val colorDialog = MaterialAlertDialogBuilder(act)
            .setTitle(R.string.pref_color)
            .setView(dialogView)
            .create()

        val allColors = Arrays.copyOf(baseColors, baseColors.size + 1)
        allColors[baseColors.size] = ColorPickerAdapter.CUSTOM_COLOR

        val numColumns = 4
        val adapter = ColorPickerAdapter(allColors, numColumns,
            { color ->
                saveColor(color)
                colorDialog.dismiss()
            },
            {
                colorDialog.dismiss()
                showHexInputDialog()
            }
        )
        recyclerView.layoutManager = GridLayoutManager(act, numColumns)
        recyclerView.adapter = adapter

        colorDialog.show()
    }

    private fun showHexInputDialog() {
        val act = mActivity ?: return
        val dialogView = layoutInflater.inflate(R.layout.dialog_hex_input, null)
        val hexInput = dialogView.findViewById<EditText>(R.id.hexInput)
        val hexInputLayout = dialogView.findViewById<TextInputLayout>(R.id.hexInputLayout)

        val currentColor = PreferencesHelper.getColor(act)
        hexInput.setText(String.format("#%06X", (0xFFFFFF and currentColor)))

        val hexDialog = MaterialAlertDialogBuilder(act)
            .setTitle(R.string.hex_color_title)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val hex = hexInput.text.toString()
                try {
                    val color = Color.parseColor(hex)
                    saveColor(color)
                } catch (e: IllegalArgumentException) {
                    // Invalid color, do nothing or show error
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        hexDialog.show()

        val positiveButton = hexDialog.getButton(AlertDialog.BUTTON_POSITIVE)
        positiveButton.isEnabled = true

        hexInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val text = s?.toString() ?: ""
                if (text.isEmpty()) {
                    positiveButton.isEnabled = false
                    hexInputLayout?.error = null
                    return
                }
                try {
                    Color.parseColor(text)
                    positiveButton.isEnabled = true
                    hexInputLayout?.error = null
                } catch (e: IllegalArgumentException) {
                    positiveButton.isEnabled = false
                    hexInputLayout?.error = getString(R.string.invalid_format)
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun saveColor(color: Int) {
        val editor = preferenceManager.sharedPreferences!!.edit()
        editor.putInt(getString(R.string.pref_color_key), color)
        editor.apply()
        updateColorPreferenceIcon()
    }

    private fun updateColorPreferenceIcon() {
        if (colorPref != null && mActivity != null) {
            val color = PreferencesHelper.getColor(mActivity!!)
            colorPref?.icon = createColorDrawable(color)
        }
    }

    private fun createColorDrawable(color: Int): Drawable {
        val coloredCircle = ShapeDrawable(OvalShape()).apply {
            paint.color = color
            intrinsicWidth = 72
            intrinsicHeight = 72
        }
        return coloredCircle
    }

    override fun onResume() {
        super.onResume()
        PreferenceManager.getDefaultSharedPreferences(requireContext()).registerOnSharedPreferenceChangeListener(mSettingsListener)
        updateBackgroundPreferencesVisibility()
    }

    override fun onPause() {
        super.onPause()
        PreferenceManager.getDefaultSharedPreferences(requireContext()).unregisterOnSharedPreferenceChangeListener(mSettingsListener)
    }

    private fun updateBackgroundPreferencesVisibility() {
        val ctx = context
        if (ctx == null || !isFullVersionUnlocked(ctx)) {
            return
        }

        val ignoreBatteryOptimizationsPref = findPreference<Preference>(getString(R.string.pref_ignore_battery_optimizations_key))
        val disablePermissionMonitoringPref = findPreference<Preference>(getString(R.string.pref_disable_permission_monitoring_key))

        if (ignoreBatteryOptimizationsPref == null || disablePermissionMonitoringPref == null) {
            return
        }

        var isIgnoringBatteryOptimizations = false
        try {
            val powerManager = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (powerManager != null) {
                isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(ctx.packageName)
            }
        } catch (e: Exception) {
            // ignore
        }

        // Show battery optimization preference if not ignored
        ignoreBatteryOptimizationsPref.isVisible = !isIgnoringBatteryOptimizations

        // Show permission auto-revoke preference independently if on Android R+ and not whitelisted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val isWhitelisted = ctx.packageManager.isAutoRevokeWhitelisted()
                disablePermissionMonitoringPref.isVisible = !isWhitelisted
            } catch (e: Exception) {
                disablePermissionMonitoringPref.setVisible(false)
            }
        } else {
            disablePermissionMonitoringPref.setVisible(false)
        }

        val backgroundCategory = findPreference<PreferenceCategory>(getString(R.string.pref_background_category_key))
        backgroundCategory?.isVisible = ignoreBatteryOptimizationsPref.isVisible || disablePermissionMonitoringPref.isVisible
    }
}
