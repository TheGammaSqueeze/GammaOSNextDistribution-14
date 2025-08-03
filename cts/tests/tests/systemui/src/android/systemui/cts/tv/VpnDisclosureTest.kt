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

package android.systemui.cts.tv

import android.app.Notification
import android.content.ComponentName
import android.platform.test.annotations.AppModeFull
import android.platform.test.annotations.Postsubmit
import android.server.wm.annotation.Group2
import android.service.notification.StatusBarNotification
import android.systemui.cts.NotificationListener
import android.systemui.tv.cts.ResourceNames.SYSTEM_UI_PACKAGE
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.android.internal.messages.nano.SystemMessageProto
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests VPN disclosure notifications.
 *
 * Build/Install/Run:
 * atest VpnDisclosureTest
 */
@Postsubmit
@Group2
@RunWith(AndroidJUnit4::class)
@AppModeFull
class VpnDisclosureTest : TvTestBase() {
    companion object {
        private const val TAG = "VpnDisclosureTest"
        private const val VPN_PACKAGE = "com.android.cts.vpnfirewall"
        private val VPN_CONTROL_ACTIVITY = ComponentName.createRelative(VPN_PACKAGE, ".VpnClient")
        private const val ACTION_CONNECT_AND_FINISH = "$VPN_PACKAGE.action.CONNECT_AND_FINISH"
        private const val ACTION_DISCONNECT_AND_FINISH = "$VPN_PACKAGE.action.DISCONNECT_AND_FINISH"

        private const val VPN_CONFIRM_ALERT_ACCEPT_BUTTON = "android:id/button1"
        private const val CONFIRM_VPN_TIMEOUT_MS = 3_000L
    }

    @Before
    override fun onSetUp() {
        NotificationListener.startNotificationListener()
    }

    @After
    override fun onTearDown() {
        stopVpnConnection()
        NotificationListener.stopNotificationListener()
    }

    @Test
    fun vpnDisclosure_connectedNotificationShown_afterConnectedToVpn() {
        startVpnConnection()
        assertVpnConnectedNotificationPresent()

        // Connected notification should be ongoing and persistent
        Thread.sleep(7_000)
        assertVpnConnectedNotificationPresent()
    }

    @Test
    fun vpnDisclosure_disconnectedNotificationShown_afterDisonnectedFromVpn() {
        startVpnConnection()
        // Short timeout to settle down the connection and notification
        Thread.sleep(1_000)
        stopVpnConnection()

        assertVpnDisconnectedNotificationPresent()
        // Disconnected notification should be short lived
        Thread.sleep(7_000)
        assertNoVpnNotificationsPresent()
    }

    private fun assertVpnConnectedNotificationPresent() {
        assertNotNull(
                NotificationListener.waitForNotificationToAppear {
                    it.isVpnConnectedNotification
                }, "Vpn connected notification not found!"
        )
    }

    private fun assertVpnDisconnectedNotificationPresent() {
        assertNotNull(
                NotificationListener.waitForNotificationToAppear {
                    it.isVpnDisconnectedNotification
                }, "Vpn disconnected notification not found!"
        )
    }

    private fun assertNoVpnNotificationsPresent() {
        assertTrue(
                NotificationListener.waitForNotificationToDisappear {
                    it.isVpnConnectedNotification || it.isVpnDisconnectedNotification
                }, "Vpn notification(s) still present!"
        )
    }

    private fun startVpnConnection() {
        launchActivity(VPN_CONTROL_ACTIVITY, ACTION_CONNECT_AND_FINISH)
        // Accept the VPN confirmation alert (if it appears)
        uiDevice.wait(Until.findObject(
                By.res(VPN_CONFIRM_ALERT_ACCEPT_BUTTON)), CONFIRM_VPN_TIMEOUT_MS)?.click()
                ?: Log.w(TAG, "VPN confirmation dialog was not shown. " +
                        "Was this VPN connection accepted before?")
    }

    private fun stopVpnConnection() {
        launchActivity(VPN_CONTROL_ACTIVITY, ACTION_DISCONNECT_AND_FINISH)
    }

    private val StatusBarNotification.isVpnConnectedNotification: Boolean
        get() = id == SystemMessageProto.SystemMessage.NOTE_VPN_STATUS &&
                SYSTEM_UI_PACKAGE == packageName &&
                (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0

    private val StatusBarNotification.isVpnDisconnectedNotification: Boolean
        get() = id == SystemMessageProto.SystemMessage.NOTE_VPN_DISCONNECTED &&
                SYSTEM_UI_PACKAGE == packageName
}
