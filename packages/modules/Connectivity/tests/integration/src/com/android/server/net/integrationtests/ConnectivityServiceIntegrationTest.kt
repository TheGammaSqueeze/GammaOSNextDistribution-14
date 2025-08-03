/*
 * Copyright (C) 2019 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.server.net.integrationtests

import android.app.usage.NetworkStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Context.BIND_AUTO_CREATE
import android.content.Context.BIND_IMPORTANT
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Resources
import android.net.ConnectivityManager
import android.net.IDnsResolver
import android.net.INetd
import android.net.LinkProperties
import android.net.NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL
import android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
import android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED
import android.net.NetworkCapabilities.TRANSPORT_CELLULAR
import android.net.NetworkRequest
import android.net.TestNetworkStackClient
import android.net.Uri
import android.net.metrics.IpConnectivityLog
import android.os.ConditionVariable
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemConfigManager
import android.os.UserHandle
import android.os.VintfRuntimeInfo
import android.telephony.TelephonyManager
import android.testing.TestableContext
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.SystemUtil
import com.android.connectivity.resources.R
import com.android.net.module.util.BpfUtils
import com.android.networkstack.apishim.TelephonyManagerShimImpl
import com.android.server.BpfNetMaps
import com.android.server.ConnectivityService
import com.android.server.NetworkAgentWrapper
import com.android.server.TestNetIdManager
import com.android.server.connectivity.CarrierPrivilegeAuthenticator
import com.android.server.connectivity.ConnectivityResources
import com.android.server.connectivity.MockableSystemProperties
import com.android.server.connectivity.MultinetworkPolicyTracker
import com.android.server.connectivity.ProxyTracker
import com.android.server.connectivity.SatelliteAccessController
import com.android.testutils.DevSdkIgnoreRunner
import com.android.testutils.DeviceInfoUtils
import com.android.testutils.RecorderCallback.CallbackEntry.LinkPropertiesChanged
import com.android.testutils.TestableNetworkCallback
import com.android.testutils.tryTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.AdditionalAnswers
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito.any
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.doNothing
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.eq
import org.mockito.Mockito.mock
import org.mockito.MockitoAnnotations
import org.mockito.Spy
import java.util.function.Consumer
import java.util.function.BiConsumer

const val SERVICE_BIND_TIMEOUT_MS = 5_000L
const val TEST_TIMEOUT_MS = 10_000L

/**
 * Test that exercises an instrumented version of ConnectivityService against an instrumented
 * NetworkStack in a different test process.
 */
@RunWith(DevSdkIgnoreRunner::class)
@DevSdkIgnoreRunner.MonitorThreadLeak
class ConnectivityServiceIntegrationTest {
    // lateinit used here for mocks as they need to be reinitialized between each test and the test
    // should crash if they are used before being initialized.
    @Mock
    private lateinit var statsManager: NetworkStatsManager
    @Mock
    private lateinit var log: IpConnectivityLog
    @Mock
    private lateinit var netd: INetd
    @Mock
    private lateinit var dnsResolver: IDnsResolver
    @Mock
    private lateinit var systemConfigManager: SystemConfigManager
    @Mock
    private lateinit var resources: Resources
    @Mock
    private lateinit var resourcesContext: Context
    @Spy
    private var context = TestableContext(realContext)

    // lateinit for these three classes under test, as they should be reset to a different instance
    // for every test but should always be initialized before use (or the test should crash).
    private lateinit var networkStackClient: TestNetworkStackClient
    private lateinit var service: ConnectivityService
    private lateinit var cm: ConnectivityManager

    private val handlerThreads = mutableListOf<HandlerThread>()

    companion object {
        // lateinit for this binder token, as it must be initialized before any test code is run
        // and use of it before init should crash the test.
        private lateinit var nsInstrumentation: INetworkStackInstrumentation
        private val bindingCondition = ConditionVariable(false)

        private val realContext get() = InstrumentationRegistry.getInstrumentation().context
        private val httpProbeUrl get() =
            realContext.getResources().getString(com.android.server.net.integrationtests.R.string
                    .config_captive_portal_http_url)
        private val httpsProbeUrl get() =
            realContext.getResources().getString(com.android.server.net.integrationtests.R.string
                    .config_captive_portal_https_url)

        private class InstrumentationServiceConnection : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                Log.i("TestNetworkStack", "Service connected")
                try {
                    if (service == null) fail("Error binding to NetworkStack instrumentation")
                    if (::nsInstrumentation.isInitialized) fail("Service already connected")
                    nsInstrumentation = INetworkStackInstrumentation.Stub.asInterface(service)
                } finally {
                    bindingCondition.open()
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) = Unit
        }

        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            val intent = Intent(realContext, NetworkStackInstrumentationService::class.java)
            intent.action = INetworkStackInstrumentation::class.qualifiedName
            assertTrue(realContext.bindService(intent, InstrumentationServiceConnection(),
                    BIND_AUTO_CREATE or BIND_IMPORTANT),
                    "Error binding to instrumentation service")
            assertTrue(bindingCondition.block(SERVICE_BIND_TIMEOUT_MS),
                    "Timed out binding to instrumentation service " +
                            "after $SERVICE_BIND_TIMEOUT_MS ms")
        }
    }

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        val asUserCtx = mock(Context::class.java, AdditionalAnswers.delegatesTo<Context>(context))
        doReturn(UserHandle.ALL).`when`(asUserCtx).user
        doReturn(asUserCtx).`when`(context).createContextAsUser(eq(UserHandle.ALL), anyInt())
        doNothing().`when`(context).sendStickyBroadcast(any(), any())
        doReturn(Context.SYSTEM_CONFIG_SERVICE).`when`(context)
                .getSystemServiceName(SystemConfigManager::class.java)
        doReturn(systemConfigManager).`when`(context)
                .getSystemService(Context.SYSTEM_CONFIG_SERVICE)
        doReturn(IntArray(0)).`when`(systemConfigManager).getSystemPermissionUids(anyString())

        doReturn(60000).`when`(resources).getInteger(R.integer.config_networkTransitionTimeout)
        doReturn("").`when`(resources).getString(R.string.config_networkCaptivePortalServerUrl)
        doReturn(arrayOf<String>("test_wlan_wol")).`when`(resources)
                .getStringArray(R.array.config_wakeonlan_supported_interfaces)
        doReturn(arrayOf("0,1", "1,3")).`when`(resources)
                .getStringArray(R.array.config_networkSupportedKeepaliveCount)
        doReturn(emptyArray<String>()).`when`(resources)
                .getStringArray(R.array.config_networkNotifySwitches)
        doReturn(intArrayOf(10, 11, 12, 14, 15)).`when`(resources)
                .getIntArray(R.array.config_protectedNetworks)
        // We don't test the actual notification value strings, so just return an empty array.
        // It doesn't matter what the values are as long as it's not null.
        doReturn(emptyArray<String>()).`when`(resources).getStringArray(
                R.array.network_switch_type_name)
        doReturn(1).`when`(resources).getInteger(R.integer.config_networkAvoidBadWifi)
        doReturn(R.array.config_networkSupportedKeepaliveCount).`when`(resources)
                .getIdentifier(eq("config_networkSupportedKeepaliveCount"), eq("array"), any())

        doReturn(resources).`when`(resourcesContext).getResources()
        ConnectivityResources.setResourcesContextForTest(resourcesContext)

        networkStackClient = TestNetworkStackClient(realContext)
        networkStackClient.start()

        service = TestConnectivityService(TestDependencies())
        cm = ConnectivityManager(context, service)
        context.addMockSystemService(Context.CONNECTIVITY_SERVICE, cm)
        context.addMockSystemService(Context.NETWORK_STATS_SERVICE, statsManager)

        service.systemReadyInternal()
    }

    private inner class TestConnectivityService(deps: Dependencies) : ConnectivityService(
            context, dnsResolver, log, netd, deps)

    private inner class TestDependencies : ConnectivityService.Dependencies() {
        override fun getNetworkStack() = networkStackClient
        override fun makeProxyTracker(context: Context, connServiceHandler: Handler) =
            mock(ProxyTracker::class.java)
        override fun getSystemProperties() = mock(MockableSystemProperties::class.java)
        override fun makeNetIdManager() = TestNetIdManager()
        override fun getBpfNetMaps(context: Context?, netd: INetd?) = mock(BpfNetMaps::class.java)

        override fun makeMultinetworkPolicyTracker(
            c: Context,
            h: Handler,
            r: Runnable
        ) = MultinetworkPolicyTracker(c, h, r,
            object : MultinetworkPolicyTracker.Dependencies() {
                override fun getResourcesForActiveSubId(
                    connResources: ConnectivityResources,
                    activeSubId: Int
                ) = resources
            })

        override fun makeHandlerThread(tag: String): HandlerThread =
            super.makeHandlerThread(tag).also { handlerThreads.add(it) }

        override fun makeCarrierPrivilegeAuthenticator(
            context: Context,
            tm: TelephonyManager,
            requestRestrictedWifiEnabled: Boolean,
            listener: BiConsumer<Int, Int>
        ): CarrierPrivilegeAuthenticator {
            return CarrierPrivilegeAuthenticator(context,
                object : CarrierPrivilegeAuthenticator.Dependencies() {
                    override fun makeHandlerThread(): HandlerThread =
                        super.makeHandlerThread().also { handlerThreads.add(it) }
                },
                tm, TelephonyManagerShimImpl.newInstance(tm),
                requestRestrictedWifiEnabled, listener)
        }

        override fun makeSatelliteAccessController(
            context: Context,
            updateSatellitePreferredUid: Consumer<MutableSet<Int>>?,
            connectivityServiceInternalHandler: Handler
        ): SatelliteAccessController? = mock(
            SatelliteAccessController::class.java)
    }

    @After
    fun tearDown() {
        nsInstrumentation.clearAllState()
        ConnectivityResources.setResourcesContextForTest(null)
        handlerThreads.forEach {
            it.quitSafely()
            it.join()
        }
        handlerThreads.clear()
    }

    @Test
    fun testValidation() {
        val request = NetworkRequest.Builder()
                .clearCapabilities()
                .addCapability(NET_CAPABILITY_INTERNET)
                .build()
        val testCallback = TestableNetworkCallback()

        cm.registerNetworkCallback(request, testCallback)
        nsInstrumentation.addHttpResponse(HttpResponse(httpProbeUrl, responseCode = 204))
        nsInstrumentation.addHttpResponse(HttpResponse(httpsProbeUrl, responseCode = 204))

        val na = NetworkAgentWrapper(TRANSPORT_CELLULAR, LinkProperties(), null /* ncTemplate */,
                context)
        networkStackClient.verifyNetworkMonitorCreated(na.network, TEST_TIMEOUT_MS)

        na.addCapability(NET_CAPABILITY_INTERNET)
        na.connect()

        tryTest {
            testCallback.expectAvailableThenValidatedCallbacks(na.network, TEST_TIMEOUT_MS)
            val requestedSize = nsInstrumentation.getRequestUrls().size
            if (requestedSize == 2 || (requestedSize == 1 &&
                        nsInstrumentation.getRequestUrls()[0] == httpsProbeUrl)
            ) {
                return@tryTest
            }
            fail("Unexpected request urls: ${nsInstrumentation.getRequestUrls()}")
        } cleanup {
            na.destroy()
        }
    }

    @Test
    fun testCapportApi() {
        val request = NetworkRequest.Builder()
                .clearCapabilities()
                .addCapability(NET_CAPABILITY_INTERNET)
                .build()
        val testCb = TestableNetworkCallback()
        val apiUrl = "https://capport.android.com"

        cm.registerNetworkCallback(request, testCb)
        nsInstrumentation.addHttpResponse(HttpResponse(
                apiUrl,
                """
                    |{
                    |  "captive": true,
                    |  "user-portal-url": "https://login.capport.android.com",
                    |  "venue-info-url": "https://venueinfo.capport.android.com"
                    |}
                """.trimMargin()))

        // Tests will fail if a non-mocked query is received: mock the HTTPS probe, but not the
        // HTTP probe as it should not be sent.
        // Even if the HTTPS probe succeeds, a portal should be detected as the API takes precedence
        // in that case.
        nsInstrumentation.addHttpResponse(HttpResponse(httpsProbeUrl, responseCode = 204))

        val lp = LinkProperties()
        lp.captivePortalApiUrl = Uri.parse(apiUrl)
        val na = NetworkAgentWrapper(TRANSPORT_CELLULAR, lp, null /* ncTemplate */, context)

        tryTest {
            networkStackClient.verifyNetworkMonitorCreated(na.network, TEST_TIMEOUT_MS)

            na.addCapability(NET_CAPABILITY_INTERNET)
            na.connect()

            testCb.expectAvailableCallbacks(na.network, validated = false, tmt = TEST_TIMEOUT_MS)

            val capportData = testCb.expect<LinkPropertiesChanged>(na, TEST_TIMEOUT_MS) {
                it.lp.captivePortalData != null
            }.lp.captivePortalData
            assertNotNull(capportData)
            assertTrue(capportData.isCaptive)
            assertEquals(Uri.parse("https://login.capport.android.com"), capportData.userPortalUrl)
            assertEquals(
                Uri.parse("https://venueinfo.capport.android.com"),
                capportData.venueInfoUrl
            )

            testCb.expectCaps(na, TEST_TIMEOUT_MS) {
                it.hasCapability(NET_CAPABILITY_CAPTIVE_PORTAL) &&
                        !it.hasCapability(NET_CAPABILITY_VALIDATED)
            }
        } cleanup {
            na.destroy()
        }
    }

    private fun isBpfGetCgroupProgramIdSupportedByKernel(): Boolean {
        val kVersionString = VintfRuntimeInfo.getKernelVersion()
        return DeviceInfoUtils.compareMajorMinorVersion(kVersionString, "4.19") >= 0
    }

    @Test
    fun testBpfProgramAttachStatus() {
        Assume.assumeTrue(isBpfGetCgroupProgramIdSupportedByKernel())

        listOf(
                BpfUtils.BPF_CGROUP_INET_INGRESS,
                BpfUtils.BPF_CGROUP_INET_EGRESS,
                BpfUtils.BPF_CGROUP_INET_SOCK_CREATE
        ).forEach {
            val ret = SystemUtil.runShellCommand(InstrumentationRegistry.getInstrumentation(),
                    "cmd connectivity bpf-get-cgroup-program-id $it").trim()

            assertTrue(Integer.parseInt(ret) > 0, "Unexpected output $ret for type $it")
        }
    }
}
