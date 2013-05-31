/*
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

package org.birthdayadapter.provider;

import android.net.Uri;
import android.provider.BaseColumns;

public class BirthdayAdapterContract {

    interface AccountBlacklistColumns {
        String ACCOUNT_NAME = "account_name";
        String ACCOUNT_TYPE = "account_type";
    }

    public static final String CONTENT_AUTHORITY = "org.birthdayadapter";

    private static final Uri BASE_CONTENT_URI = Uri.parse("content://" + CONTENT_AUTHORITY);

    public static final String PATH_ACCOUNT_BLACKLIST = "account_blacklist";

    public static class AccountBlacklist implements AccountBlacklistColumns, BaseColumns {
        public static final Uri CONTENT_URI = BASE_CONTENT_URI.buildUpon()
                .appendPath(PATH_ACCOUNT_BLACKLIST).build();

        /**
         * Use if multiple items get returned
         */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.birthdayadapter.account_blacklist";

        /**
         * Use if a single item is returned
         */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.birthdayadapter.account";

        /**
         * Default "ORDER BY" clause.
         */
        public static final String DEFAULT_SORT = AccountBlacklistColumns.ACCOUNT_TYPE + " ASC";

        public static Uri buildUri(String id) {
            return CONTENT_URI.buildUpon().appendPath(id).build();
        }

        public static String getId(Uri uri) {
            return uri.getLastPathSegment();
        }
    }

    private BirthdayAdapterContract() {
    }
}
