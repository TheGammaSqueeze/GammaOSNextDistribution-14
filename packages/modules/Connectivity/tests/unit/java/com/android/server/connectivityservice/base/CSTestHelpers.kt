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

@file:JvmName("CsTestHelpers")

package com.android.server

import android.app.ActivityManager
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PackageManager.FEATURE_BLUETOOTH
import android.content.pm.PackageManager.FEATURE_ETHERNET
import android.content.pm.PackageManager.FEATURE_WIFI
import android.content.pm.PackageManager.FEATURE_WIFI_DIRECT
import android.content.pm.UserInfo
import android.content.res.Resources
import android.net.IDnsResolver
import android.net.INetd
import android.net.IpPrefix
import android.net.LinkAddress
import android.net.LinkProperties
import android.net.NetworkAgentConfig
import android.net.NetworkCapabilities
import android.net.NetworkScore
import android.net.RouteInfo
import android.net.metrics.IpConnectivityLog
import android.os.Binder
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.os.SystemConfigManager
import android.os.UserHandle
import android.os.UserManager
import android.provider.Settings
import android.test.mock.MockContentResolver
import com.android.connectivity.resources.R
import com.android.internal.util.WakeupMessage
import com.android.internal.util.test.FakeSettingsProvider
import com.android.modules.utils.build.SdkLevel
import com.android.server.ConnectivityService.Dependencies
import com.android.server.connectivity.ConnectivityResources
import kotlin.test.fail
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.argThat
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doNothing
import org.mockito.Mockito.doReturn

internal inline fun <reified T> mock() = Mockito.mock(T::class.java)
internal inline fun <reified T> any() = any(T::class.java)

internal fun emptyAgentConfig(legacyType: Int) = NetworkAgentConfig.Builder()
        .setLegacyType(legacyType)
        .build()

internal fun defaultNc() = NetworkCapabilities.Builder()
        // Add sensible defaults for agents that don't want to care
        .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED)
        .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)
        .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED)
        .build()

internal fun defaultScore() = FromS(NetworkScore.Builder().build())

internal fun defaultLp() = LinkProperties().apply {
    addLinkAddress(LinkAddress(LOCAL_IPV4_ADDRESS, 32))
    addRoute(RouteInfo(IpPrefix("0.0.0.0/0"), null, null))
}

internal fun makeMockContentResolver(context: Context) = MockContentResolver(context).apply {
    addProvider(Settings.AUTHORITY, FakeSettingsProvider())
}

internal fun makeMockUserManager(info: UserInfo, handle: UserHandle) = mock<UserManager>().also {
    doReturn(listOf(info)).`when`(it).getAliveUsers()
    doReturn(listOf(handle)).`when`(it).getUserHandles(ArgumentMatchers.anyBoolean())
}

internal fun makeActivityManager() = mock<ActivityManager>().also {
    if (SdkLevel.isAtLeastU()) {
        doNothing().`when`(it).registerUidFrozenStateChangedCallback(any(), any())
    }
}

internal fun makeMockPackageManager(realContext: Context) = mock<PackageManager>().also { pm ->
    val supported = listOf(FEATURE_WIFI, FEATURE_WIFI_DIRECT, FEATURE_BLUETOOTH, FEATURE_ETHERNET)
    doReturn(true).`when`(pm).hasSystemFeature(argThat { supported.contains(it) })
    val myPackageName = realContext.packageName
    val myPackageInfo = realContext.packageManager.getPackageInfo(myPackageName,
            PackageManager.GET_PERMISSIONS)
    // Very high version code so that the checks for the module version will always
    // say that it is recent enough. This is the most sensible default, but if some
    // test needs to test with different version codes they can re-mock this with a
    // different value.
    myPackageInfo.longVersionCode = 9999999L
    doReturn(arrayOf(myPackageName)).`when`(pm).getPackagesForUid(Binder.getCallingUid())
    doReturn(myPackageInfo).`when`(pm).getPackageInfoAsUser(
            eq(myPackageName), anyInt(), eq(UserHandle.getCallingUserId()))
    doReturn(listOf(myPackageInfo)).`when`(pm)
            .getInstalledPackagesAsUser(eq(PackageManager.GET_PERMISSIONS), anyInt())
}

internal fun makeMockConnResources(resources: Resources, pm: PackageManager) = mock<Context>().let {
    doReturn(resources).`when`(it).resources
    doReturn(pm).`when`(it).packageManager
    ConnectivityResources.setResourcesContextForTest(it)
    ConnectivityResources(it)
}

private val UNREASONABLY_LONG_ALARM_WAIT_MS = 1000
internal fun makeMockAlarmManager(handlerThread: HandlerThread) = mock<AlarmManager>().also { am ->
    val alrmHdlr = handlerThread.threadHandler
    doAnswer {
        val (_, date, _, wakeupMsg, handler) = it.arguments
        wakeupMsg as WakeupMessage
        handler as Handler
        val delayMs = ((date as Long) - SystemClock.elapsedRealtime()).coerceAtLeast(0)
        if (delayMs > UNREASONABLY_LONG_ALARM_WAIT_MS) {
            fail("Attempting to send msg more than $UNREASONABLY_LONG_ALARM_WAIT_MS" +
                    "ms into the future : $delayMs")
        }
        alrmHdlr.postDelayed({ handler.post(wakeupMsg::onAlarm) }, wakeupMsg, delayMs)
    }.`when`(am).setExact(eq(AlarmManager.ELAPSED_REALTIME_WAKEUP), anyLong(), anyString(),
            any<WakeupMessage>(), any())
    doAnswer {
        alrmHdlr.removeCallbacksAndMessages(it.getArgument<WakeupMessage>(0))
    }.`when`(am).cancel(any<WakeupMessage>())
}

internal fun makeMockSystemConfigManager() = mock<SystemConfigManager>().also {
    doReturn(intArrayOf(0)).`when`(it).getSystemPermissionUids(anyString())
}

// Mocking resources used by ConnectivityService. Note these can't be defined to return the
// value returned by the mocking, because a non-null method would mean the helper would also
// return non-null and the compiler would check that, but mockito has no qualms returning null
// from a @NonNull method when stubbing. Hence, mock() = doReturn().getString() would crash
// at runtime, because getString() returns non-null String, therefore mock returns non-null String,
// and kotlinc adds an intrinsics check for that, which crashes at runtime when mockito actually
// returns null.
private fun Resources.mock(r: Int, v: Boolean) { doReturn(v).`when`(this).getBoolean(r) }
private fun Resources.mock(r: Int, v: Int) { doReturn(v).`when`(this).getInteger(r) }
private fun Resources.mock(r: Int, v: String) { doReturn(v).`when`(this).getString(r) }
private fun Resources.mock(r: Int, v: Array<String?>) { doReturn(v).`when`(this).getStringArray(r) }
private fun Resources.mock(r: Int, v: IntArray) { doReturn(v).`when`(this).getIntArray(r) }

internal fun initMockedResources(res: Resources) {
    // Resources accessed through reflection need to return the id
    doReturn(R.array.config_networkSupportedKeepaliveCount).`when`(res)
            .getIdentifier(eq("config_networkSupportedKeepaliveCount"), eq("array"), any())
    doReturn(R.array.network_switch_type_name).`when`(res)
            .getIdentifier(eq("network_switch_type_name"), eq("array"), any())
    // Mock the values themselves
    res.mock(R.integer.config_networkTransitionTimeout, 60_000)
    res.mock(R.string.config_networkCaptivePortalServerUrl, "")
    res.mock(R.array.config_wakeonlan_supported_interfaces, arrayOf(WIFI_WOL_IFNAME))
    res.mock(R.array.config_networkSupportedKeepaliveCount, arrayOf("0,1", "1,3"))
    res.mock(R.array.config_networkNotifySwitches, arrayOfNulls<String>(size = 0))
    res.mock(R.array.config_protectedNetworks, intArrayOf(10, 11, 12, 14, 15))
    res.mock(R.array.network_switch_type_name, arrayOfNulls<String>(size = 0))
    res.mock(R.integer.config_networkAvoidBadWifi, 1)
    res.mock(R.integer.config_activelyPreferBadWifi, 0)
    res.mock(R.bool.config_cellular_radio_timesharing_capable, true)
}

private val TEST_LINGER_DELAY_MS = 400
private val TEST_NASCENT_DELAY_MS = 300
internal fun makeConnectivityService(context: Context, netd: INetd, deps: Dependencies) =
        ConnectivityService(
                context,
                mock<IDnsResolver>(),
                mock<IpConnectivityLog>(),
                netd,
                deps).also {
            it.mLingerDelayMs = TEST_LINGER_DELAY_MS
            it.mNascentDelayMs = TEST_NASCENT_DELAY_MS
        }
