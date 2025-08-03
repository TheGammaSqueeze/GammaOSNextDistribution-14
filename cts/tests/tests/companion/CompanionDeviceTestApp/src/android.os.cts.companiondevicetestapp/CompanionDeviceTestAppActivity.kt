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

package android.os.cts.companiondevicetestapp

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.CALL_PHONE
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanResult
import android.companion.AssociationRequest
import android.companion.AssociationRequest.DEVICE_PROFILE_GLASSES
import android.companion.AssociationRequest.DEVICE_PROFILE_WATCH
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.os.Process
import android.util.Log
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.LinearLayout.VERTICAL
import android.widget.TextView
import android.widget.Toast
import java.util.regex.Pattern

class CompanionDeviceTestAppActivity : Activity() {

    val associationStatus by lazy { TextView(this) }
    val permissionStatus by lazy { TextView(this) }
    val notificationsStatus by lazy { TextView(this) }
    val bypassStatus by lazy { TextView(this) }

    val nameFilter by lazy { EditText(this).apply {
        hint = "Name Filter"
        contentDescription = "name filter" // Do not change: used in the tests.
    } }
    val singleCheckbox by lazy { CheckBox(this).apply { text = "Single Device" } }
    val watchCheckbox by lazy { CheckBox(this).apply { text = "Watch" } }
    val glassesCheckbox by lazy { CheckBox(this).apply { text = "Glasses" } }

    val cdm: CompanionDeviceManager by lazy { val java = CompanionDeviceManager::class.java
        getSystemService(java)!! }
    val bt: BluetoothAdapter by lazy { val java = BluetoothManager::class.java
        getSystemService(java)!!.adapter }

    var device: BluetoothDevice? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(LinearLayout(this).apply {
            orientation = VERTICAL

            addView(associationStatus)
            addView(permissionStatus)
            addView(notificationsStatus)
            addView(bypassStatus)

            addView(Button(ctx).apply {
                text = "^^^ Refresh"
                setOnClickListener { refresh() }
            })

            addView(nameFilter)
            addView(singleCheckbox)
            addView(watchCheckbox)
            addView(glassesCheckbox)

            addView(cdmButton("Associate") {
                if (singleCheckbox.isChecked) {
                    setSingleDevice(true)
                }
                if (watchCheckbox.isChecked) {
                    setDeviceProfile(DEVICE_PROFILE_WATCH)
                }
                if (glassesCheckbox.isChecked) {
                    setDeviceProfile(DEVICE_PROFILE_GLASSES)
                }
                addDeviceFilter(BluetoothDeviceFilter.Builder().apply {
                    if (!nameFilter.text.isEmpty()) {
                        setNamePattern(Pattern.compile(".*${nameFilter.text}.*"))
                    }
                }.build())
            })

            addView(Button(ctx).apply {
                text = "Request notifications"
                setOnClickListener {
                    cdm.requestNotificationAccess(
                            ComponentName(ctx, NotificationListener::class.java))
                }
            })
            addView(Button(ctx).apply {
                text = "Disassociate"
                setOnClickListener {
                    cdm.associations.forEach { address ->
                        toast("Disassociating $address")
                        cdm.disassociate(address)
                    }
                }
            })

            addView(Button(ctx).apply {
                text = "Register PresenceListener"
                setOnClickListener {
                    cdm.associations.forEach { address ->
                        toast("startObservingDevicePresence $address")
                        cdm.startObservingDevicePresence(address)
                    }
                }
            })

            addView(Button(ctx).apply {
                text = "Request permission transfer"
                setOnClickListener {
                    cdm.myAssociations.firstNotNullOf { associationInfo ->
                        val associationId = associationInfo.id
                        toast("requestSystemDataTransfer $associationId")
                        val intentSender = cdm.buildPermissionTransferUserConsentIntent(
                                associationId)
                        if (intentSender != null) {
                            startIntentSender(intentSender, null, 0, 0, 0)
                        }
                    }
                }
            })

            addView(Button(ctx).apply {
                text = "Check location permission"
                setOnClickListener {
                    val locationAccess = ctx.checkSelfPermission(ACCESS_FINE_LOCATION)
                    toast("location access: $locationAccess")
                }
            })

            addView(Button(ctx).apply {
                text = "Request location permission"
                setOnClickListener {
                    requestPermissions(arrayOf(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION), 10)
                }
            })
        })
    }

    private fun cdmButton(label: String, initReq: AssociationRequest.Builder.() -> Unit): Button {
        return Button(ctx).apply {
            text = label

            setOnClickListener {
                cdm.associate(AssociationRequest.Builder()
                        .apply { initReq() }
                        .build(),
                        object : CompanionDeviceManager.Callback() {
                            override fun onFailure(error: CharSequence?) {
                                toast("error: $error")
                            }

                            override fun onDeviceFound(chooserLauncher: IntentSender) {
                                toast("launching $chooserLauncher")
                                chooserLauncher?.let {
                                    startIntentSenderForResult(it, REQUEST_CODE_CDM, null, 0, 0, 0)
                                }
                            }
                        },
                        mainHandler)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE_CDM) {
            device = getDevice(data)
            toast("result code: $resultCode, device: $device")
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun getDevice(data: Intent?): BluetoothDevice? {
        val rawDevice = data?.getParcelableExtra<Parcelable?>(CompanionDeviceManager.EXTRA_DEVICE)
        return when (rawDevice) {
            is BluetoothDevice -> rawDevice
            is ScanResult -> rawDevice.device
            else -> null
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        associationStatus.text = "Have associations: ${cdm.associations.isNotEmpty()}"

        permissionStatus.text = "Phone granted: ${
            checkPermission(CALL_PHONE, Process.myPid(), Process.myUid()) ==
                    PackageManager.PERMISSION_GRANTED}"

        notificationsStatus.postDelayed({
            notificationsStatus.text = "Notifications granted: ${
                try {
                    cdm.hasNotificationAccess(
                            ComponentName.createRelative(
                                    this, NotificationListener::class.java.name))
                } catch (e: Exception) {
                    toast("" + e.message)
                    false
                }
            }"
        }, 1000)
    }

    companion object {
        const val REQUEST_CODE_CDM = 1
    }
}

fun Context.toast(msg: String) {
    Log.i("CompanionDeviceManagerTest", "toast: $msg")
    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}

val Context.ctx get() = this
