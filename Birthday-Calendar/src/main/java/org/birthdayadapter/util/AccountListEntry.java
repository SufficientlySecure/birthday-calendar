/*
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

package org.birthdayadapter.util;

import android.accounts.Account;
import android.accounts.AuthenticatorDescription;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources.NotFoundException;
import android.graphics.drawable.Drawable;

public class AccountListEntry {
    private String label;
    private Drawable icon;
    private boolean selected;
    private Account account;

    public AccountListEntry(Context context, Account account, AuthenticatorDescription description,
                            boolean selected) {
        this.account = account;
        this.selected = selected;
        init(context, account, description);
    }

    /**
     * Load label and icon for this entry
     */
    public void init(Context context, Account account, AuthenticatorDescription description) {
        PackageManager pm = context.getPackageManager();
        label = description.packageName;
        try {
            label = pm.getResourcesForApplication(description.packageName).getString(
                    description.labelId);
        } catch (NotFoundException | NameNotFoundException e) {
            Log.e(Constants.TAG, "Error retrieving label!", e);
        }

        try {
            icon = pm.getDrawable(description.packageName, description.iconId, null);
        } catch (Exception e) {
            Log.e(Constants.TAG, "Error retrieving icon!", e);
            icon = null;
        }
    }

    public String getLabel() {
        return label;
    }

    public Account getAccount() {
        return account;
    }

    public Drawable getIcon() {
        return icon;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    @Override
    public String toString() {
        return "name: " + account.name + ", type: " + account.type;
    }
}
