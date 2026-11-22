/*
 * Copyright (C) 2025-2026 Matthias Heinisch <birthdayadapter@heinisch.fr>
 * Copyright (C) 2012-2016 Dominik Schürmann <dominik@dominikschuermann.de>
 * Copyright (C) 2010 Sam Steele
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

import android.accounts.AccountAuthenticatorResponse
import android.accounts.AccountManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import fr.heinisch.birthdayadapter.util.AccountHelper

/**
 * This activity is transparent, i.e., it has no layout
 */
class AddAccountActivity : AppCompatActivity() {

    private var accountAuthenticatorResponse: AccountAuthenticatorResponse? = null
    private var resultBundle: Bundle? = null

    /**
     * Set the result that is to be sent as the result of the request that caused this
     * Activity to be launched.
     */
    fun setAccountAuthenticatorResult(result: Bundle?) {
        resultBundle = result
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        accountAuthenticatorResponse = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(
                AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE,
                AccountAuthenticatorResponse::class.java
            )
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE)
        }

        accountAuthenticatorResponse?.onRequestContinued()

        val accHelper = AccountHelper(this)
        val result = accHelper.addAccountAndSync()
        if (result != null) {
            setAccountAuthenticatorResult(result)
        }

        finish()
    }

    /**
     * Sends the result or an ERROR_CODE_CANCELED error if a result isn't present.
     */
    override fun finish() {
        val response = accountAuthenticatorResponse
        if (response != null) {
            val bundle = resultBundle
            if (bundle != null) {
                response.onResult(bundle)
            } else {
                response.onError(
                    AccountManager.ERROR_CODE_CANCELED,
                    "canceled"
                )
            }
            accountAuthenticatorResponse = null
        }
        super.finish()
    }
}
