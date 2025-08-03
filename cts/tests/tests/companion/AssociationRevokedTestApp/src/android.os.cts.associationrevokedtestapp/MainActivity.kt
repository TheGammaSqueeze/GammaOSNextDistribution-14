/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.os.cts.associationrevokedtestapp

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.AssociationRequest.DEVICE_PROFILE_WATCH
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.os.Bundle
import android.widget.Toast

class MainActivity : Activity() {
    val cdm: CompanionDeviceManager by lazy { val java = CompanionDeviceManager::class.java
        getSystemService(java)!! }
    val bt: BluetoothAdapter by lazy { val java = BluetoothManager::class.java
        getSystemService(java)!!.adapter }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        var associationRequest = AssociationRequest.Builder().apply {
            setSingleDevice(true)
            setDeviceProfile(DEVICE_PROFILE_WATCH)
        }.build()

        cdm.associate(
            associationRequest,
            this.mainExecutor,
            object : CompanionDeviceManager.Callback() {
                override fun onAssociationPending(intentSender: IntentSender) {
                    intentSender?.let {
                        startIntentSenderForResult(it, REQUEST_CODE_CDM, null, 0, 0, 0)
                    }
                }

                override fun onAssociationCreated(associationInfo: AssociationInfo) {
                }

                override fun onFailure(errorMessage: CharSequence?) {
                }
            }
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE_CDM) {
            toast("resultCode: $resultCode")
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    fun Context.toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    companion object {
        const val REQUEST_CODE_CDM = 1
    }
}
