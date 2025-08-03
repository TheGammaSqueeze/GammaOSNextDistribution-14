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
package android.permission.cts.appthatrequestpermission

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Process

/** Revokes permission for a device provided in the intent. */
class RevokeSelfPermissionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val permissionName = intent.getStringExtra("permissionName")!!
        val deviceId = intent.getIntExtra("deviceID", Context.DEVICE_ID_INVALID)
        val deviceContext = context.createDeviceContext(deviceId)
        deviceContext.revokeSelfPermissionOnKill(permissionName)

        // revokeSelfPermissionOnKill is an async API, and the work is executed by main
        // thread, so we add the kill to the queue to be executed after revoke call.
        val handler = Handler.createAsync(context.mainLooper)
        handler.postDelayed({ Process.killProcess(Process.myPid()) }, 1000)
    }
}
