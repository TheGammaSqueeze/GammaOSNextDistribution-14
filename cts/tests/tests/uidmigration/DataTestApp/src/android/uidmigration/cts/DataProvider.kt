/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package android.uidmigration.cts

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import java.util.UUID

class DataProvider : BaseProvider() {

    companion object {
        private const val RESULT_KEY = "result"
    }

    private lateinit var mPrefs: SharedPreferences

    override fun onCreate(): Boolean {
        mPrefs = requireContext().getSharedPreferences("prefs", Context.MODE_PRIVATE)
        return true
    }

    // The first time this method is called, it returns a new random UUID and stores it in prefs.
    // After an upgrade, if data migration works properly, it returns the previously generated UUID.
    // The tester app asserts that the UUIDs returned before/after the upgrade to be the same.
    private fun checkData(): Bundle {
        val prefsKey = "uuid"
        val data = Bundle()
        val uuid = mPrefs.getString(prefsKey, null) ?: UUID.randomUUID().toString().also {
            mPrefs.edit().putString(prefsKey, it).commit()
        }
        data.putString(RESULT_KEY, uuid)
        return data
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        return when (method) {
            "data" -> checkData()
            else -> Bundle()
        }
    }
}