/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.google.snippet.connectivity

import android.Manifest.permission.OVERRIDE_WIFI_CONFIG
import android.content.pm.PackageManager.FEATURE_TELEPHONY
import android.content.pm.PackageManager.FEATURE_WIFI
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED
import android.net.NetworkCapabilities.TRANSPORT_WIFI
import android.net.NetworkRequest
import android.net.cts.util.CtsNetUtils
import android.net.cts.util.CtsTetheringUtils
import android.net.wifi.ScanResult
import android.net.wifi.SoftApConfiguration
import android.net.wifi.SoftApConfiguration.SECURITY_TYPE_WPA2_PSK
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.net.wifi.WifiSsid
import androidx.test.platform.app.InstrumentationRegistry
import com.android.testutils.ConnectUtil
import com.android.testutils.RecorderCallback.CallbackEntry.Available
import com.android.testutils.RecorderCallback.CallbackEntry.CapabilitiesChanged
import com.android.testutils.TestableNetworkCallback
import com.android.testutils.runAsShell
import com.google.android.mobly.snippet.Snippet
import com.google.android.mobly.snippet.rpc.Rpc

class ConnectivityMultiDevicesSnippet : Snippet {
    private val context = InstrumentationRegistry.getInstrumentation().getTargetContext()
    private val wifiManager = context.getSystemService(WifiManager::class.java)!!
    private val cm = context.getSystemService(ConnectivityManager::class.java)!!
    private val pm = context.packageManager
    private val ctsNetUtils = CtsNetUtils(context)
    private val ctsTetheringUtils = CtsTetheringUtils(context)
    private var oldSoftApConfig: SoftApConfiguration? = null

    @Rpc(description = "Check whether the device has wifi feature.")
    fun hasWifiFeature() = pm.hasSystemFeature(FEATURE_WIFI)

    @Rpc(description = "Check whether the device has telephony feature.")
    fun hasTelephonyFeature() = pm.hasSystemFeature(FEATURE_TELEPHONY)

    @Rpc(description = "Check whether the device supporters AP + STA concurrency.")
    fun isStaApConcurrencySupported() {
        wifiManager.isStaApConcurrencySupported()
    }

    @Rpc(description = "Request cellular connection and ensure it is the default network.")
    fun requestCellularAndEnsureDefault() {
        ctsNetUtils.disableWifi()
        val network = ctsNetUtils.connectToCell()
        ctsNetUtils.expectNetworkIsSystemDefault(network)
    }

    @Rpc(description = "Unrequest cellular connection.")
    fun unrequestCellular() {
        ctsNetUtils.disconnectFromCell()
    }

    @Rpc(description = "Ensure any wifi is connected and is the default network.")
    fun ensureWifiIsDefault() {
        val network = ctsNetUtils.ensureWifiConnected()
        ctsNetUtils.expectNetworkIsSystemDefault(network)
    }

    @Rpc(description = "Connect to specified wifi network.")
    // Suppress warning because WifiManager methods to connect to a config are
    // documented not to be deprecated for privileged users.
    @Suppress("DEPRECATION")
    fun connectToWifi(ssid: String, passphrase: String, requireValidation: Boolean): Network {
        val specifier = WifiNetworkSpecifier.Builder()
            .setSsid(ssid)
            .setWpa2Passphrase(passphrase)
            .setBand(ScanResult.WIFI_BAND_24_GHZ)
            .build()
        val wifiConfig = WifiConfiguration()
        wifiConfig.SSID = "\"" + ssid + "\""
        wifiConfig.preSharedKey = "\"" + passphrase + "\""
        wifiConfig.hiddenSSID = true
        wifiConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA2_PSK)
        wifiConfig.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP)
        wifiConfig.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP)

        // Register network callback for the specific wifi.
        val networkCallback = TestableNetworkCallback()
        val wifiRequest = NetworkRequest.Builder().addTransportType(TRANSPORT_WIFI)
            .setNetworkSpecifier(specifier)
            .build()
        cm.registerNetworkCallback(wifiRequest, networkCallback)

        try {
            // Add the test configuration and connect to it.
            val connectUtil = ConnectUtil(context)
            connectUtil.connectToWifiConfig(wifiConfig)

            val event = networkCallback.expect<Available>()
            if (requireValidation) {
                networkCallback.eventuallyExpect<CapabilitiesChanged> {
                    it.caps.hasCapability(NET_CAPABILITY_VALIDATED)
                }
            }
            return event.network
        } finally {
            cm.unregisterNetworkCallback(networkCallback)
        }
    }

    @Rpc(description = "Check whether the device supports hotspot feature.")
    fun hasHotspotFeature(): Boolean {
        val tetheringCallback = ctsTetheringUtils.registerTetheringEventCallback()
        try {
            return tetheringCallback.isWifiTetheringSupported(context)
        } finally {
            ctsTetheringUtils.unregisterTetheringEventCallback(tetheringCallback)
        }
    }

    @Rpc(description = "Start a hotspot with given SSID and passphrase.")
    fun startHotspot(ssid: String, passphrase: String) {
        // Store old config.
        runAsShell(OVERRIDE_WIFI_CONFIG) {
            oldSoftApConfig = wifiManager.getSoftApConfiguration()
        }

        val softApConfig = SoftApConfiguration.Builder()
            .setWifiSsid(WifiSsid.fromBytes(ssid.toByteArray()))
            .setPassphrase(passphrase, SECURITY_TYPE_WPA2_PSK)
            .setBand(SoftApConfiguration.BAND_2GHZ)
            .build()
        runAsShell(OVERRIDE_WIFI_CONFIG) {
            wifiManager.setSoftApConfiguration(softApConfig)
        }
        val tetheringCallback = ctsTetheringUtils.registerTetheringEventCallback()
        try {
            tetheringCallback.expectNoTetheringActive()
            ctsTetheringUtils.startWifiTethering(tetheringCallback)
        } finally {
            ctsTetheringUtils.unregisterTetheringEventCallback(tetheringCallback)
        }
    }

    @Rpc(description = "Stop all tethering.")
    fun stopAllTethering() {
        ctsTetheringUtils.stopAllTethering()

        // Restore old config.
        oldSoftApConfig?.let {
            runAsShell(OVERRIDE_WIFI_CONFIG) {
                wifiManager.setSoftApConfiguration(it)
            }
        }
    }
}
