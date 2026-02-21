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

package fr.heinisch.birthdayadapter.util;

import android.app.Activity;
import android.content.Context;

public class PurchaseHelperImpl implements IPurchaseHelper {
    @Override
    public void launchBillingFlow(Activity activity) {
        // Not used in the full version
    }

    @Override
    public void queryProductDetails(Activity activity, PriceCallback callback) {
        // Not used in the full version
    }

    @Override
    public void verifyAndRestorePurchases(Context context) {
        // Not used in the full version
    }
}
