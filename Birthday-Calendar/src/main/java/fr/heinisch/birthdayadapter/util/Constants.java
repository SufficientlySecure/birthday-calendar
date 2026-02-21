/*
 * Copyright (C) 2025-2026 Matthias Heinisch <birthdayadapter@heinisch.fr>
 * Copyright (C) 2012-2013 Dominik Schürmann <dominik@dominikschuermann.de>
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

package fr.heinisch.birthdayadapter.util;

import android.content.Context;

import fr.heinisch.birthdayadapter.R;

public class Constants {
    public static final String TAG = "Birthday Adapter";

    public static String getAccountName(Context context) {
        return context.getString(R.string.app_name);
    }

    public static final String PREFS_NAME = "preferences";

    public static final int SYNC_INTERVAL_DAYS = 5;

    public static final String GROUP_TITLE_NO_GROUP = "NO_GROUP";
}
