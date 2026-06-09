/*
 * Copyright (C) 2025-2026 Matthias Heinisch <birthdayadapter@heinisch.fr>
 * Copyright (C) 2013 Dominik Schürmann <dominik@dominikschuermann.de>
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

package fr.heinisch.birthdayadapter.provider;

import android.net.Uri;
import android.provider.BaseColumns;

import fr.heinisch.birthdayadapter.BuildConfig;

public class BirthdayAdapterContract {

    public interface AccountBlacklistColumns {
        String ACCOUNT_NAME = "account_name";
        String ACCOUNT_TYPE = "account_type";
        String ACCOUNT_GROUP = "account_group";
    }

    public interface ContactMappingColumns {
        String LOOKUP_KEY = "lookup_key";
    }

    public static final String CONTENT_AUTHORITY = BuildConfig.APPLICATION_ID;

    private static final Uri BASE_CONTENT_URI = Uri.parse("content://" + CONTENT_AUTHORITY);

    public static final String PATH_ACCOUNT_BLACKLIST = "account_blacklist";
    public static final String PATH_CONTACT_MAPPING = "contact_mapping";

    public static class AccountBlacklist implements AccountBlacklistColumns, BaseColumns {
        public static final Uri CONTENT_URI = BASE_CONTENT_URI.buildUpon()
                .appendPath(PATH_ACCOUNT_BLACKLIST).build();

        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.birthdayadapter.account_blacklist";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.birthdayadapter.account";

        public static final String DEFAULT_SORT = AccountBlacklistColumns.ACCOUNT_TYPE + " ASC";

        public static Uri buildUri(String id) {
            return CONTENT_URI.buildUpon().appendPath(id).build();
        }
    }

    public static class ContactMapping implements ContactMappingColumns, BaseColumns {
        public static final Uri CONTENT_URI = BASE_CONTENT_URI.buildUpon()
                .appendPath(PATH_CONTACT_MAPPING).build();

        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.birthdayadapter.contact_mapping";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.birthdayadapter.contact_mapping";

        public static Uri buildUri(String id) {
            return CONTENT_URI.buildUpon().appendPath(id).build();
        }
    }

    private BirthdayAdapterContract() {
    }
}
