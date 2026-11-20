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

package fr.heinisch.birthdayadapter.util

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class SyncStatusManager private constructor() {
    private val mIsSyncing = MutableLiveData<Boolean>()

    fun isSyncing(): LiveData<Boolean> = mIsSyncing

    fun setSyncing(isSyncing: Boolean) {
        mIsSyncing.postValue(isSyncing)
    }

    companion object {
        @Volatile
        private var sInstance: SyncStatusManager? = null

        @JvmStatic
        @Synchronized
        fun getInstance(): SyncStatusManager {
            return sInstance ?: SyncStatusManager().also { sInstance = it }
        }
    }
}
