/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.permissionpolicy.cts.receivecallbroadcast

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ProcessOutgoingCallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        context!!.sendBroadcast(
            Intent(ACTION_TEST_APP_RECEIVED_OUTGOING_CALL).setPackage(TEST_CLASS_PKG_NAME)
        )
    }

    class BaseActivity : Activity()
}

const val TEST_CLASS_PKG_NAME = "android.permissionpolicy.cts"
const val ACTION_TEST_APP_RECEIVED_OUTGOING_CALL =
    "android.permissionpolicy.cts.TEST_APP_RECEIVED_CALL"
