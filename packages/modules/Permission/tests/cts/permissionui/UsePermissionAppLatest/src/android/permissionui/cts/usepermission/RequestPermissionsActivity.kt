/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.permissionui.cts.usepermission

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.android.modules.utils.build.SdkLevel

class RequestPermissionsActivity : Activity() {

    private var shouldAskTwice = false
    private var timesAsked = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            Log.w(TAG, "Activity was recreated. (Perhaps due to a configuration change?)")
            return
        }

        val permissions = intent.getStringArrayExtra("$packageName.PERMISSIONS")!!
        shouldAskTwice = intent.getBooleanExtra("$packageName.ASK_TWICE", false)
        if (SdkLevel.isAtLeastV()) {
            // TODO: make deviceId dynamic
            requestPermissions(permissions, 1, Context.DEVICE_ID_DEFAULT)
        } else {
            requestPermissions(permissions, 1)
        }
        timesAsked = 1
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        handleResult(permissions, grantResults)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
        deviceId: Int
    ) {
        handleResult(permissions, grantResults, deviceId)
    }

    private fun handleResult(
        permissions: Array<out String>,
        grantResults: IntArray,
        deviceId: Int? = null
    ) {
        if (shouldAskTwice && timesAsked < 2) {
            requestPermissions(permissions, 1)
            timesAsked += 1
            return
        }

        setResult(
            RESULT_OK,
            Intent().apply {
                putExtra("$packageName.PERMISSIONS", permissions)
                putExtra("$packageName.GRANT_RESULTS", grantResults)
                if (deviceId != null) {
                    putExtra("$packageName.DEVICE_ID", deviceId)
                }
            }
        )
        finish()
    }

    companion object {
        private val TAG = RequestPermissionsActivity::class.simpleName
    }
}
