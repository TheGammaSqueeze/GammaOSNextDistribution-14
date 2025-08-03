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

package com.android.server

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.content.pm.UserInfo
import android.content.res.Resources
import android.net.ConnectivityManager
import android.net.INetd
import android.net.InetAddresses
import android.net.LinkProperties
import android.net.LocalNetworkConfig
import android.net.NetworkAgentConfig
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED
import android.net.NetworkCapabilities.TRANSPORT_BLUETOOTH
import android.net.NetworkCapabilities.TRANSPORT_CELLULAR
import android.net.NetworkCapabilities.TRANSPORT_ETHERNET
import android.net.NetworkCapabilities.TRANSPORT_TEST
import android.net.NetworkCapabilities.TRANSPORT_VPN
import android.net.NetworkCapabilities.TRANSPORT_WIFI
import android.net.NetworkPolicyManager
import android.net.NetworkProvider
import android.net.NetworkScore
import android.net.PacProxyManager
import android.net.networkstack.NetworkStackClientBase
import android.os.BatteryStatsManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.permission.PermissionManager.PermissionResult
import android.telephony.TelephonyManager
import android.testing.TestableContext
import android.util.ArraySet
import androidx.test.platform.app.InstrumentationRegistry
import com.android.internal.app.IBatteryStats
import com.android.internal.util.test.BroadcastInterceptingContext
import com.android.modules.utils.build.SdkLevel
import com.android.net.module.util.ArrayTrackRecord
import com.android.networkstack.apishim.common.UnsupportedApiLevelException
import com.android.server.connectivity.AutomaticOnOffKeepaliveTracker
import com.android.server.connectivity.CarrierPrivilegeAuthenticator
import com.android.server.connectivity.ClatCoordinator
import com.android.server.connectivity.ConnectivityFlags
import com.android.server.connectivity.MulticastRoutingCoordinatorService
import com.android.server.connectivity.MultinetworkPolicyTracker
import com.android.server.connectivity.MultinetworkPolicyTrackerTestDependencies
import com.android.server.connectivity.NetworkRequestStateStatsMetrics
import com.android.server.connectivity.ProxyTracker
import com.android.server.connectivity.SatelliteAccessController
import com.android.testutils.visibleOnHandlerThread
import com.android.testutils.waitForIdle
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import java.util.function.BiConsumer
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail
import org.junit.After
import org.junit.Before
import org.mockito.AdditionalAnswers.delegatesTo
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock

internal const val HANDLER_TIMEOUT_MS = 2_000L
internal const val BROADCAST_TIMEOUT_MS = 3_000L
internal const val TEST_PACKAGE_NAME = "com.android.test.package"
internal const val WIFI_WOL_IFNAME = "test_wlan_wol"
internal val LOCAL_IPV4_ADDRESS = InetAddresses.parseNumericAddress("192.0.2.1")

open class FromS<Type>(val value: Type)

internal const val VERSION_UNMOCKED = -1
internal const val VERSION_R = 1
internal const val VERSION_S = 2
internal const val VERSION_T = 3
internal const val VERSION_U = 4
internal const val VERSION_V = 5
internal const val VERSION_MAX = VERSION_V

private fun NetworkCapabilities.getLegacyType() =
        when (transportTypes.getOrElse(0) { TRANSPORT_WIFI }) {
            TRANSPORT_BLUETOOTH -> ConnectivityManager.TYPE_BLUETOOTH
            TRANSPORT_CELLULAR -> ConnectivityManager.TYPE_MOBILE
            TRANSPORT_ETHERNET -> ConnectivityManager.TYPE_ETHERNET
            TRANSPORT_TEST -> ConnectivityManager.TYPE_TEST
            TRANSPORT_VPN -> ConnectivityManager.TYPE_VPN
            TRANSPORT_WIFI -> ConnectivityManager.TYPE_WIFI
            else -> ConnectivityManager.TYPE_NONE
        }

/**
 * Base class for tests testing ConnectivityService and its satellites.
 *
 * This class sets up a ConnectivityService running locally in the test.
 */
// TODO (b/272685721) : make ConnectivityServiceTest smaller and faster by moving the setup
// parts into this class and moving the individual tests to multiple separate classes.
open class CSTest {
    companion object {
        val CSTestExecutor = Executors.newSingleThreadExecutor()
    }

    init {
        if (!SdkLevel.isAtLeastS()) {
            throw UnsupportedApiLevelException("CSTest subclasses must be annotated to only " +
                    "run on S+, e.g. @DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.R)")
        }
    }

    val instrumentationContext =
            TestableContext(InstrumentationRegistry.getInstrumentation().context)
    val context = CSContext(instrumentationContext)

    // See constructor for default-enabled features. All queried features must be either enabled
    // or disabled, because the test can't hold READ_DEVICE_CONFIG and device config utils query
    // permissions using static contexts.
    val enabledFeatures = HashMap<String, Boolean>().also {
        it[ConnectivityFlags.NO_REMATCH_ALL_REQUESTS_ON_REGISTER] = true
        it[ConnectivityFlags.REQUEST_RESTRICTED_WIFI] = true
        it[ConnectivityService.KEY_DESTROY_FROZEN_SOCKETS_VERSION] = true
        it[ConnectivityService.DELAY_DESTROY_FROZEN_SOCKETS_VERSION] = true
        it[ConnectivityService.ALLOW_SYSUI_CONNECTIVITY_REPORTS] = true
        it[ConnectivityService.LOG_BPF_RC] = true
        it[ConnectivityService.ALLOW_SATALLITE_NETWORK_FALLBACK] = true
    }
    fun enableFeature(f: String) = enabledFeatures.set(f, true)
    fun disableFeature(f: String) = enabledFeatures.set(f, false)

    // When adding new members, consider if it's not better to build the object in CSTestHelpers
    // to keep this file clean of implementation details. Generally, CSTestHelpers should only
    // need changes when new details of instrumentation are needed.
    val contentResolver = makeMockContentResolver(context)

    val PRIMARY_USER = 0
    val PRIMARY_USER_INFO = UserInfo(PRIMARY_USER, "" /* name */, UserInfo.FLAG_PRIMARY)
    val PRIMARY_USER_HANDLE = UserHandle(PRIMARY_USER)
    val userManager = makeMockUserManager(PRIMARY_USER_INFO, PRIMARY_USER_HANDLE)
    val activityManager = makeActivityManager()

    val networkStack = mock<NetworkStackClientBase>()
    val csHandlerThread = HandlerThread("CSTestHandler")
    val sysResources = mock<Resources>().also { initMockedResources(it) }
    val packageManager = makeMockPackageManager(instrumentationContext)
    val connResources = makeMockConnResources(sysResources, packageManager)

    val netd = mock<INetd>()
    val bpfNetMaps = mock<BpfNetMaps>()
    val clatCoordinator = mock<ClatCoordinator>()
    val networkRequestStateStatsMetrics = mock<NetworkRequestStateStatsMetrics>()
    val proxyTracker = ProxyTracker(context, mock<Handler>(), 16 /* EVENT_PROXY_HAS_CHANGED */)
    val systemConfigManager = makeMockSystemConfigManager()
    val batteryStats = mock<IBatteryStats>()
    val batteryManager = BatteryStatsManager(batteryStats)
    val telephonyManager = mock<TelephonyManager>().also {
        doReturn(true).`when`(it).isDataCapable()
    }

    val multicastRoutingCoordinatorService = mock<MulticastRoutingCoordinatorService>()
    val satelliteAccessController = mock<SatelliteAccessController>()

    val deps = CSDeps()

    // Initializations that start threads are done from setUp to avoid thread leak
    lateinit var alarmHandlerThread: HandlerThread
    lateinit var alarmManager: AlarmManager
    lateinit var service: ConnectivityService
    lateinit var cm: ConnectivityManager
    lateinit var csHandler: Handler

    @Before
    fun setUp() {
        alarmHandlerThread = HandlerThread("TestAlarmManager").also { it.start() }
        alarmManager = makeMockAlarmManager(alarmHandlerThread)
        service = makeConnectivityService(context, netd, deps).also { it.systemReadyInternal() }
        cm = ConnectivityManager(context, service)
        // csHandler initialization must be after makeConnectivityService since ConnectivityService
        // constructor starts csHandlerThread
        csHandler = Handler(csHandlerThread.looper)
    }

    @After
    fun tearDown() {
        csHandlerThread.quitSafely()
        csHandlerThread.join()
        alarmHandlerThread.quitSafely()
        alarmHandlerThread.join()
    }

    inner class CSDeps : ConnectivityService.Dependencies() {
        override fun getResources(ctx: Context) = connResources
        override fun getBpfNetMaps(context: Context, netd: INetd) = this@CSTest.bpfNetMaps
        override fun getClatCoordinator(netd: INetd?) = this@CSTest.clatCoordinator
        override fun getNetworkStack() = this@CSTest.networkStack

        override fun makeHandlerThread(tag: String) = csHandlerThread
        override fun makeProxyTracker(context: Context, connServiceHandler: Handler) = proxyTracker
        override fun makeMulticastRoutingCoordinatorService(handler: Handler) =
                this@CSTest.multicastRoutingCoordinatorService

        override fun makeCarrierPrivilegeAuthenticator(
                context: Context,
                tm: TelephonyManager,
                requestRestrictedWifiEnabled: Boolean,
                listener: BiConsumer<Int, Int>
        ) = if (SdkLevel.isAtLeastT()) mock<CarrierPrivilegeAuthenticator>() else null

        var satelliteNetworkFallbackUidUpdate: Consumer<Set<Int>>? = null
        override fun makeSatelliteAccessController(
            context: Context,
            updateSatelliteNetworkFallackUid: Consumer<Set<Int>>?,
            csHandlerThread: Handler
        ): SatelliteAccessController? {
            satelliteNetworkFallbackUidUpdate = updateSatelliteNetworkFallackUid
            return satelliteAccessController
        }

        private inner class AOOKTDeps(c: Context) : AutomaticOnOffKeepaliveTracker.Dependencies(c) {
            override fun isTetheringFeatureNotChickenedOut(name: String): Boolean {
                return isFeatureEnabled(context, name)
            }
        }
        override fun makeAutomaticOnOffKeepaliveTracker(c: Context, h: Handler) =
                AutomaticOnOffKeepaliveTracker(c, h, AOOKTDeps(c))

        override fun makeMultinetworkPolicyTracker(c: Context, h: Handler, r: Runnable) =
                MultinetworkPolicyTracker(c, h, r,
                        MultinetworkPolicyTrackerTestDependencies(connResources.get()))

        override fun makeNetworkRequestStateStatsMetrics(c: Context) =
                this@CSTest.networkRequestStateStatsMetrics

        // All queried features must be mocked, because the test cannot hold the
        // READ_DEVICE_CONFIG permission and device config utils use static methods for
        // checking permissions.
        override fun isFeatureEnabled(context: Context?, name: String?) =
                enabledFeatures[name] ?: fail("Unmocked feature $name, see CSTest.enabledFeatures")
        override fun isFeatureNotChickenedOut(context: Context?, name: String?) =
                enabledFeatures[name] ?: fail("Unmocked feature $name, see CSTest.enabledFeatures")

        // Mocked change IDs
        private val enabledChangeIds = ArraySet<Long>()
        fun setChangeIdEnabled(enabled: Boolean, changeId: Long) {
            // enabledChangeIds is read on the handler thread and maybe the test thread, so
            // make sure both threads see it before continuing.
            visibleOnHandlerThread(csHandler) {
                if (enabled) {
                    enabledChangeIds.add(changeId)
                } else {
                    enabledChangeIds.remove(changeId)
                }
            }
        }

        override fun isChangeEnabled(changeId: Long, pkg: String, user: UserHandle) =
                changeId in enabledChangeIds
        override fun isChangeEnabled(changeId: Long, uid: Int) =
                changeId in enabledChangeIds

        // In AOSP, build version codes can't always distinguish between some versions (e.g. at the
        // time of this writing U == V). Define custom ones.
        private var sdkLevel = VERSION_UNMOCKED
        private val isSdkUnmocked get() = sdkLevel == VERSION_UNMOCKED

        fun setBuildSdk(sdkLevel: Int) {
            require(sdkLevel <= VERSION_MAX) {
                "setBuildSdk must not be called with Build.VERSION constants but " +
                        "CsTest.VERSION_* constants"
            }
            visibleOnHandlerThread(csHandler) { this.sdkLevel = sdkLevel }
        }

        override fun isAtLeastS() = if (isSdkUnmocked) super.isAtLeastS() else sdkLevel >= VERSION_S
        override fun isAtLeastT() = if (isSdkUnmocked) super.isAtLeastT() else sdkLevel >= VERSION_T
        override fun isAtLeastU() = if (isSdkUnmocked) super.isAtLeastU() else sdkLevel >= VERSION_U
        override fun isAtLeastV() = if (isSdkUnmocked) super.isAtLeastV() else sdkLevel >= VERSION_V
    }

    inner class CSContext(base: Context) : BroadcastInterceptingContext(base) {
        val pacProxyManager = mock<PacProxyManager>()
        val networkPolicyManager = mock<NetworkPolicyManager>()

        // Map of permission name -> PermissionManager.Permission_{GRANTED|DENIED} constant
        // For permissions granted across the board, the key is only the permission name.
        // For permissions only granted to a combination of uid/pid, the key
        // is "<permission name>,<pid>,<uid>". PID+UID permissions have priority over generic ones.
        private val mMockedPermissions: HashMap<String, Int> = HashMap()
        private val mStartedActivities = LinkedBlockingQueue<Intent>()
        override fun getPackageManager() = this@CSTest.packageManager
        override fun getContentResolver() = this@CSTest.contentResolver

        // If the permission result does not set in the mMockedPermissions, it will be
        // considered as PERMISSION_GRANTED as existing design to prevent breaking other tests.
        override fun checkPermission(permission: String, pid: Int, uid: Int) =
            checkMockedPermission(permission, pid, uid, PERMISSION_GRANTED)

        override fun enforceCallingOrSelfPermission(permission: String, message: String?) {
            // If the permission result does not set in the mMockedPermissions, it will be
            // considered as PERMISSION_GRANTED as existing design to prevent breaking other tests.
            val granted = checkMockedPermission(permission, Process.myPid(), Process.myUid(),
                PERMISSION_GRANTED)
            if (!granted.equals(PERMISSION_GRANTED)) {
                throw SecurityException("[Test] permission denied: " + permission)
            }
        }

        // If the permission result does not set in the mMockedPermissions, it will be
        // considered as PERMISSION_GRANTED as existing design to prevent breaking other tests.
        override fun checkCallingOrSelfPermission(permission: String) =
            checkMockedPermission(permission, Process.myPid(), Process.myUid(), PERMISSION_GRANTED)

        private fun checkMockedPermission(permission: String, pid: Int, uid: Int, default: Int):
                Int {
            val processSpecificKey = "$permission,$pid,$uid"
            return mMockedPermissions[processSpecificKey]
                    ?: mMockedPermissions[permission] ?: default
        }

        /**
         * Mock checks for the specified permission, and have them behave as per `granted` or
         * `denied`.
         *
         * This will apply to all calls no matter what the checked UID and PID are.
         *
         * @param granted One of {@link PackageManager#PermissionResult}.
         */
        fun setPermission(permission: String, @PermissionResult granted: Int) {
            mMockedPermissions.put(permission, granted)
        }

        /**
         * Mock checks for the specified permission, and have them behave as per `granted` or
         * `denied`.
         *
         * This will only apply to the passed UID and PID.
         *
         * @param granted One of {@link PackageManager#PermissionResult}.
         */
        fun setPermission(permission: String, pid: Int, uid: Int, @PermissionResult granted: Int) {
            mMockedPermissions.put("$permission,$pid,$uid", granted)
        }

        // Necessary for MultinetworkPolicyTracker, which tries to register a receiver for
        // all users. The test can't do that since it doesn't hold INTERACT_ACROSS_USERS.
        // TODO : ensure MultinetworkPolicyTracker's BroadcastReceiver is tested ; ideally,
        // just returning null should not have tests pass
        override fun registerReceiverForAllUsers(
                receiver: BroadcastReceiver?,
                filter: IntentFilter,
                broadcastPermission: String?,
                scheduler: Handler?
        ): Intent? = null

        // Create and cache user managers on the fly as necessary.
        val userManagers = HashMap<UserHandle, UserManager>()
        override fun createContextAsUser(user: UserHandle, flags: Int): Context {
            val asUser = mock(Context::class.java, delegatesTo<Any>(this))
            doReturn(user).`when`(asUser).getUser()
            doAnswer { userManagers.computeIfAbsent(user) {
                mock(UserManager::class.java, delegatesTo<Any>(userManager)) }
            }.`when`(asUser).getSystemService(Context.USER_SERVICE)
            return asUser
        }

        // List of mocked services. Add additional services here or in subclasses.
        override fun getSystemService(serviceName: String) = when (serviceName) {
            Context.CONNECTIVITY_SERVICE -> cm
            Context.PAC_PROXY_SERVICE -> pacProxyManager
            Context.NETWORK_POLICY_SERVICE -> networkPolicyManager
            Context.ALARM_SERVICE -> alarmManager
            Context.USER_SERVICE -> userManager
            Context.ACTIVITY_SERVICE -> activityManager
            Context.SYSTEM_CONFIG_SERVICE -> systemConfigManager
            Context.TELEPHONY_SERVICE -> telephonyManager
            Context.BATTERY_STATS_SERVICE -> batteryManager
            Context.STATS_MANAGER -> null // Stats manager is final and can't be mocked
            else -> super.getSystemService(serviceName)
        }

        internal val orderedBroadcastAsUserHistory = ArrayTrackRecord<Intent>().newReadHead()

        fun expectNoDataActivityBroadcast(timeoutMs: Int) {
            assertNull(orderedBroadcastAsUserHistory.poll(
                    timeoutMs.toLong()) { intent -> true })
        }

        override fun sendOrderedBroadcastAsUser(
                intent: Intent,
                user: UserHandle,
                receiverPermission: String?,
                resultReceiver: BroadcastReceiver?,
                scheduler: Handler?,
                initialCode: Int,
                initialData: String?,
                initialExtras: Bundle?
        ) {
            orderedBroadcastAsUserHistory.add(intent)
        }

        override fun startActivityAsUser(intent: Intent, handle: UserHandle) {
            mStartedActivities.put(intent)
        }

        fun expectStartActivityIntent(timeoutMs: Long = HANDLER_TIMEOUT_MS): Intent {
            val intent = mStartedActivities.poll(timeoutMs, TimeUnit.MILLISECONDS)
            assertNotNull(intent, "Did not receive sign-in intent after " + timeoutMs + "ms")
            return intent
        }
    }

    // Utility methods for subclasses to use
    fun waitForIdle() = csHandlerThread.waitForIdle(HANDLER_TIMEOUT_MS)

    // Network agents. See CSAgentWrapper. This class contains utility methods to simplify
    // creation.
    fun Agent(
            nc: NetworkCapabilities = defaultNc(),
            nac: NetworkAgentConfig = emptyAgentConfig(nc.getLegacyType()),
            lp: LinkProperties = defaultLp(),
            lnc: FromS<LocalNetworkConfig>? = null,
            score: FromS<NetworkScore> = defaultScore(),
            provider: NetworkProvider? = null
    ) = CSAgentWrapper(context, deps, csHandlerThread, networkStack,
            nac, nc, lp, lnc, score, provider)
    fun Agent(vararg transports: Int, lp: LinkProperties = defaultLp()): CSAgentWrapper {
        val nc = NetworkCapabilities.Builder().apply {
            transports.forEach {
                addTransportType(it)
            }
        }.addCapability(NET_CAPABILITY_NOT_SUSPENDED)
                .build()
        return Agent(nc = nc, lp = lp)
    }
}
