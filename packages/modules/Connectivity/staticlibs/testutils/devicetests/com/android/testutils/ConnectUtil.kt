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

package com.android.testutils

import android.Manifest.permission
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
import android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED
import android.net.NetworkCapabilities.TRANSPORT_CELLULAR
import android.net.NetworkCapabilities.TRANSPORT_WIFI
import android.net.NetworkRequest
import android.net.wifi.ScanResult
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.testutils.RecorderCallback.CallbackEntry
import com.android.testutils.RecorderCallback.CallbackEntry.CapabilitiesChanged
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

private const val MAX_WIFI_CONNECT_RETRIES = 10
private const val WIFI_CONNECT_INTERVAL_MS = 500L
private const val WIFI_CONNECT_TIMEOUT_MS = 30_000L

// Constants used by WifiManager.ActionListener#onFailure. Although onFailure is SystemApi,
// the error code constants are not (b/204277752)
private const val WIFI_ERROR_IN_PROGRESS = 1
private const val WIFI_ERROR_BUSY = 2

class ConnectUtil(private val context: Context) {
    private val TAG = ConnectUtil::class.java.simpleName

    private val cm = context.getSystemService(ConnectivityManager::class.java)
            ?: fail("Could not find ConnectivityManager")
    private val wifiManager = context.getSystemService(WifiManager::class.java)
            ?: fail("Could not find WifiManager")

    fun ensureWifiConnected(): Network = ensureWifiConnected(requireValidated = false)
    fun ensureWifiValidated(): Network = ensureWifiConnected(requireValidated = true)

    fun ensureCellularValidated(): Network {
        val cb = TestableNetworkCallback()
        cm.requestNetwork(
            NetworkRequest.Builder()
                .addTransportType(TRANSPORT_CELLULAR)
                .addCapability(NET_CAPABILITY_INTERNET).build(), cb)
        return tryTest {
            val errorMsg = "The device does not have mobile data available. Check that it is " +
                    "setup with a SIM card that has a working data plan, that the APN " +
                    "configuration is valid, and that the device can access the internet through " +
                    "mobile data."
            cb.eventuallyExpect<CapabilitiesChanged>(errorMsg) {
                it.caps.hasCapability(NET_CAPABILITY_VALIDATED)
            }.network
        } cleanup {
            cm.unregisterNetworkCallback(cb)
        }
    }

    private fun ensureWifiConnected(requireValidated: Boolean): Network {
        val callback = TestableNetworkCallback(timeoutMs = WIFI_CONNECT_TIMEOUT_MS)
        cm.registerNetworkCallback(NetworkRequest.Builder()
                .addTransportType(TRANSPORT_WIFI)
                .build(), callback)

        return tryTest {
            val connInfo = wifiManager.connectionInfo
            Log.d(TAG, "connInfo=" + connInfo)
            if (connInfo == null || connInfo.networkId == -1) {
                clearWifiBlocklist()
                val pfd = getInstrumentation().uiAutomation.executeShellCommand("svc wifi enable")
                // Read the output stream to ensure the command has completed
                ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes() }
                val config = getOrCreateWifiConfiguration()
                connectToWifiConfig(config)
            }
            val errorMsg = if (requireValidated) {
                "The wifi access point did not have access to the internet after " +
                        "$WIFI_CONNECT_TIMEOUT_MS ms. Check that it has a working connection."
            } else {
                "Could not connect to a wifi access point within $WIFI_CONNECT_TIMEOUT_MS ms. " +
                        "Check that the test device has a wifi network configured, and that the " +
                        "test access point is functioning properly."
            }
            val cb = callback.eventuallyExpect<CapabilitiesChanged>(errorMsg) {
                (!requireValidated || it.caps.hasCapability(NET_CAPABILITY_VALIDATED))
            }
            cb.network
        } cleanup {
            cm.unregisterNetworkCallback(callback)
        }
    }

    // Suppress warning because WifiManager methods to connect to a config are
    // documented not to be deprecated for privileged users.
    @Suppress("DEPRECATION")
    fun connectToWifiConfig(config: WifiConfiguration) {
        repeat(MAX_WIFI_CONNECT_RETRIES) {
            val error = runAsShell(permission.NETWORK_SETTINGS) {
                val listener = ConnectWifiListener()
                wifiManager.connect(config, listener)
                listener.connectFuture.get(WIFI_CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            } ?: return // Connect succeeded

            // Only retry for IN_PROGRESS and BUSY
            if (error != WIFI_ERROR_IN_PROGRESS && error != WIFI_ERROR_BUSY) {
                fail("Failed to connect to " + config.SSID + ": " + error)
            }
            Log.w(TAG, "connect failed with $error; waiting before retry")
            SystemClock.sleep(WIFI_CONNECT_INTERVAL_MS)
        }
        fail("Failed to connect to ${config.SSID} after $MAX_WIFI_CONNECT_RETRIES retries")
    }

    private class ConnectWifiListener : WifiManager.ActionListener {
        /**
         * Future completed when the connect process ends. Provides the error code or null if none.
         */
        val connectFuture = CompletableFuture<Int?>()
        override fun onSuccess() {
            connectFuture.complete(null)
        }

        override fun onFailure(reason: Int) {
            connectFuture.complete(reason)
        }
    }

    private fun getOrCreateWifiConfiguration(): WifiConfiguration {
        val configs = runAsShell(permission.NETWORK_SETTINGS) {
            wifiManager.getConfiguredNetworks()
        }
        // If no network is configured, add a config for virtual access points if applicable
        if (configs.size == 0) {
            val scanResults = getWifiScanResults()
            val virtualConfig = maybeConfigureVirtualNetwork(scanResults)
            assertNotNull(virtualConfig, "The device has no configured wifi network")
            return virtualConfig
        }
        // No need to add a configuration: there is already one.
        if (configs.size > 1) {
            // For convenience in case of local testing on devices with multiple saved configs,
            // prefer the first configuration that is in range.
            // In actual tests, there should only be one configuration, and it should be usable as
            // assumed by WifiManagerTest.testConnect.
            Log.w(TAG, "Multiple wifi configurations found: " +
                    configs.joinToString(", ") { it.SSID })
            val scanResultsList = getWifiScanResults()
            Log.i(TAG, "Scan results: " + scanResultsList.joinToString(", ") {
                "${it.SSID} (${it.level})"
            })

            val scanResults = scanResultsList.map { "\"${it.SSID}\"" }.toSet()
            return configs.firstOrNull { scanResults.contains(it.SSID) } ?: configs[0]
        }
        return configs[0]
    }

    private fun getWifiScanResults(): List<ScanResult> {
        val scanResultsFuture = CompletableFuture<List<ScanResult>>()
        runAsShell(permission.NETWORK_SETTINGS) {
            val receiver: BroadcastReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    scanResultsFuture.complete(wifiManager.scanResults)
                }
            }
            context.registerReceiver(receiver,
                    IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
            wifiManager.startScan()
        }
        return try {
            scanResultsFuture.get(WIFI_CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            throw AssertionError("Wifi scan results not received within timeout", e)
        }
    }

    /**
     * If a virtual wifi network is detected, add a configuration for that network.
     * TODO(b/158150376): have the test infrastructure add virtual wifi networks when appropriate.
     */
    private fun maybeConfigureVirtualNetwork(scanResults: List<ScanResult>): WifiConfiguration? {
        // Virtual wifi networks used on the emulator and cloud testing infrastructure
        val virtualSsids = listOf("VirtWifi", "AndroidWifi")
        Log.d(TAG, "Wifi scan results: $scanResults")
        val virtualScanResult = scanResults.firstOrNull { virtualSsids.contains(it.SSID) }
                ?: return null

        // Only add the virtual configuration if the virtual AP is detected in scans
        val virtualConfig = WifiConfiguration()
        // ASCII SSIDs need to be surrounded by double quotes
        virtualConfig.SSID = "\"${virtualScanResult.SSID}\""
        virtualConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
        runAsShell(permission.NETWORK_SETTINGS) {
            val networkId = wifiManager.addNetwork(virtualConfig)
            assertTrue(networkId >= 0)
            assertTrue(wifiManager.enableNetwork(networkId, false /* attemptConnect */))
        }
        return virtualConfig
    }

    /**
     * Re-enable wifi networks that were blocked, typically because no internet connection was
     * detected the last time they were connected. This is necessary to make sure wifi can reconnect
     * to them.
     */
    private fun clearWifiBlocklist() {
        runAsShell(permission.NETWORK_SETTINGS, permission.ACCESS_WIFI_STATE) {
            for (cfg in wifiManager.configuredNetworks) {
                assertTrue(wifiManager.enableNetwork(cfg.networkId, false /* attemptConnect */))
            }
        }
    }
}

private inline fun <reified T : CallbackEntry> TestableNetworkCallback.eventuallyExpect(
    errorMsg: String,
    crossinline predicate: (T) -> Boolean = { true }
): T = history.poll(defaultTimeoutMs, mark) { it is T && predicate(it) }.also {
    assertNotNull(it, errorMsg)
} as T
