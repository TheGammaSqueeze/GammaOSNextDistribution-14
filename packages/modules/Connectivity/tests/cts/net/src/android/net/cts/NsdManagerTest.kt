/*
 * Copyright (C) 2012 The Android Open Source Project
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
package android.net.cts

import android.Manifest.permission.MANAGE_TEST_NETWORKS
import android.Manifest.permission.NETWORK_SETTINGS
import android.app.compat.CompatChanges
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.DnsResolver
import android.net.InetAddresses.parseNumericAddress
import android.net.LinkAddress
import android.net.LinkProperties
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.net.MacAddress
import android.net.Network
import android.net.NetworkAgentConfig
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.NET_CAPABILITY_TEMPORARILY_NOT_METERED
import android.net.NetworkCapabilities.NET_CAPABILITY_TRUSTED
import android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED
import android.net.NetworkCapabilities.TRANSPORT_TEST
import android.net.NetworkRequest
import android.net.TestNetworkInterface
import android.net.TestNetworkManager
import android.net.TestNetworkSpecifier
import android.net.connectivity.ConnectivityCompatChanges
import android.net.cts.NsdDiscoveryRecord.DiscoveryEvent.DiscoveryStarted
import android.net.cts.NsdDiscoveryRecord.DiscoveryEvent.DiscoveryStopped
import android.net.cts.NsdDiscoveryRecord.DiscoveryEvent.ServiceFound
import android.net.cts.NsdDiscoveryRecord.DiscoveryEvent.ServiceLost
import android.net.cts.NsdRegistrationRecord.RegistrationEvent.RegistrationFailed
import android.net.cts.NsdRegistrationRecord.RegistrationEvent.ServiceRegistered
import android.net.cts.NsdRegistrationRecord.RegistrationEvent.ServiceUnregistered
import android.net.cts.NsdResolveRecord.ResolveEvent.ResolutionStopped
import android.net.cts.NsdResolveRecord.ResolveEvent.ServiceResolved
import android.net.cts.NsdResolveRecord.ResolveEvent.StopResolutionFailed
import android.net.cts.NsdServiceInfoCallbackRecord.ServiceInfoCallbackEvent.ServiceUpdated
import android.net.cts.NsdServiceInfoCallbackRecord.ServiceInfoCallbackEvent.ServiceUpdatedLost
import android.net.cts.NsdServiceInfoCallbackRecord.ServiceInfoCallbackEvent.UnregisterCallbackSucceeded
import android.net.cts.util.CtsNetUtils
import android.net.nsd.DiscoveryRequest
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.nsd.OffloadEngine
import android.net.nsd.OffloadServiceInfo
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.platform.test.annotations.AppModeFull
import android.provider.DeviceConfig.NAMESPACE_TETHERING
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants.AF_INET6
import android.system.OsConstants.EADDRNOTAVAIL
import android.system.OsConstants.ENETUNREACH
import android.system.OsConstants.ETH_P_IPV6
import android.system.OsConstants.IPPROTO_IPV6
import android.system.OsConstants.IPPROTO_UDP
import android.system.OsConstants.RT_SCOPE_LINK
import android.system.OsConstants.SOCK_DGRAM
import android.util.Log
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.PollingCheck
import com.android.compatibility.common.util.PropertyUtil
import com.android.modules.utils.build.SdkLevel.isAtLeastU
import com.android.net.module.util.DnsPacket
import com.android.net.module.util.HexDump
import com.android.net.module.util.NetworkStackConstants.IPV6_ADDR_LEN
import com.android.net.module.util.PacketBuilder
import com.android.testutils.ConnectivityModuleTest
import com.android.testutils.DevSdkIgnoreRule
import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo
import com.android.testutils.DevSdkIgnoreRunner
import com.android.testutils.DeviceConfigRule
import com.android.testutils.NSResponder
import com.android.testutils.RecorderCallback.CallbackEntry.CapabilitiesChanged
import com.android.testutils.RecorderCallback.CallbackEntry.LinkPropertiesChanged
import com.android.testutils.TapPacketReader
import com.android.testutils.TestableNetworkAgent
import com.android.testutils.TestableNetworkAgent.CallbackEntry.OnNetworkCreated
import com.android.testutils.TestableNetworkCallback
import com.android.testutils.assertEmpty
import com.android.testutils.filters.CtsNetTestCasesMaxTargetSdk30
import com.android.testutils.filters.CtsNetTestCasesMaxTargetSdk33
import com.android.testutils.runAsShell
import com.android.testutils.tryTest
import com.android.testutils.waitForIdle
import java.io.File
import java.io.IOException
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.Random
import java.util.concurrent.Executor
import kotlin.math.min
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TAG = "NsdManagerTest"
private const val TIMEOUT_MS = 2000L
// Registration may take a long time if there are devices with the same hostname on the network,
// as the device needs to try another name and probe again. This is especially true since when using
// mdnsresponder the usual hostname is "Android", and on conflict "Android-2", "Android-3", ... are
// tried sequentially
private const val REGISTRATION_TIMEOUT_MS = 10_000L
private const val DBG = false
private const val TEST_PORT = 12345
private const val MDNS_PORT = 5353.toShort()
private val multicastIpv6Addr = parseNumericAddress("ff02::fb") as Inet6Address
private val testSrcAddr = parseNumericAddress("2001:db8::123") as Inet6Address

@AppModeFull(reason = "Socket cannot bind in instant app mode")
@RunWith(DevSdkIgnoreRunner::class)
@SmallTest
@ConnectivityModuleTest
@IgnoreUpTo(Build.VERSION_CODES.S_V2)
class NsdManagerTest {
    // Rule used to filter CtsNetTestCasesMaxTargetSdkXX
    @get:Rule
    val ignoreRule = DevSdkIgnoreRule()

    @get:Rule
    val deviceConfigRule = DeviceConfigRule()

    private val context by lazy { InstrumentationRegistry.getInstrumentation().context }
    private val nsdManager by lazy {
        context.getSystemService(NsdManager::class.java) ?: fail("Could not get NsdManager service")
    }

    private val cm by lazy { context.getSystemService(ConnectivityManager::class.java)!! }
    private val serviceName = "NsdTest%09d".format(Random().nextInt(1_000_000_000))
    private val serviceName2 = "NsdTest%09d".format(Random().nextInt(1_000_000_000))
    private val serviceName3 = "NsdTest%09d".format(Random().nextInt(1_000_000_000))
    private val serviceType = "_nmt%09d._tcp".format(Random().nextInt(1_000_000_000))
    private val serviceType2 = "_nmt%09d._tcp".format(Random().nextInt(1_000_000_000))
    private val customHostname = "NsdTestHost%09d".format(Random().nextInt(1_000_000_000))
    private val customHostname2 = "NsdTestHost%09d".format(Random().nextInt(1_000_000_000))
    private val handlerThread = HandlerThread(NsdManagerTest::class.java.simpleName)
    private val ctsNetUtils by lazy{ CtsNetUtils(context) }

    private lateinit var testNetwork1: TestTapNetwork
    private lateinit var testNetwork2: TestTapNetwork

    private class TestTapNetwork(
        val iface: TestNetworkInterface,
        val requestCb: NetworkCallback,
        val agent: TestableNetworkAgent,
        val network: Network
    ) {
        fun close(cm: ConnectivityManager) {
            cm.unregisterNetworkCallback(requestCb)
            agent.unregister()
            iface.fileDescriptor.close()
            agent.waitForIdle(TIMEOUT_MS)
        }
    }

    private class TestNsdOffloadEngine : OffloadEngine,
        NsdRecord<TestNsdOffloadEngine.OffloadEvent>() {
        sealed class OffloadEvent : NsdEvent {
            data class AddOrUpdateEvent(val info: OffloadServiceInfo) : OffloadEvent()
            data class RemoveEvent(val info: OffloadServiceInfo) : OffloadEvent()
        }

        override fun onOffloadServiceUpdated(info: OffloadServiceInfo) {
            add(OffloadEvent.AddOrUpdateEvent(info))
        }

        override fun onOffloadServiceRemoved(info: OffloadServiceInfo) {
            add(OffloadEvent.RemoveEvent(info))
        }
    }

    @Before
    fun setUp() {
        handlerThread.start()

        runAsShell(MANAGE_TEST_NETWORKS) {
            testNetwork1 = createTestNetwork()
            testNetwork2 = createTestNetwork()
        }
    }

    private fun createTestNetwork(): TestTapNetwork {
        val tnm = context.getSystemService(TestNetworkManager::class.java)!!
        val iface = tnm.createTapInterface()
        val cb = TestableNetworkCallback()
        val testNetworkSpecifier = TestNetworkSpecifier(iface.interfaceName)
        cm.requestNetwork(NetworkRequest.Builder()
                .removeCapability(NET_CAPABILITY_TRUSTED)
                .addTransportType(TRANSPORT_TEST)
                .setNetworkSpecifier(testNetworkSpecifier)
                .build(), cb)
        val agent = registerTestNetworkAgent(iface.interfaceName)
        val network = agent.network ?: fail("Registered agent should have a network")

        cb.eventuallyExpect<LinkPropertiesChanged>(TIMEOUT_MS) {
            it.lp.linkAddresses.isNotEmpty()
        }

        // The network has no INTERNET capability, so will be marked validated immediately
        // It does not matter if validated capabilities come before/after the link addresses change
        cb.eventuallyExpect<CapabilitiesChanged>(TIMEOUT_MS, from = 0) {
            it.caps.hasCapability(NET_CAPABILITY_VALIDATED)
        }
        return TestTapNetwork(iface, cb, agent, network)
    }

    private fun registerTestNetworkAgent(ifaceName: String): TestableNetworkAgent {
        val lp = LinkProperties().apply {
            interfaceName = ifaceName
        }
        val agent = TestableNetworkAgent(context, handlerThread.looper,
                NetworkCapabilities().apply {
                    removeCapability(NET_CAPABILITY_TRUSTED)
                    addTransportType(TRANSPORT_TEST)
                    setNetworkSpecifier(TestNetworkSpecifier(ifaceName))
                }, lp, NetworkAgentConfig.Builder().build())
        val network = agent.register()
        agent.markConnected()
        agent.expectCallback<OnNetworkCreated>()

        // Wait until the link-local address can be used. Address flags are not available without
        // elevated permissions, so check that bindSocket works.
        PollingCheck.check("No usable v6 address on interface after $TIMEOUT_MS ms", TIMEOUT_MS) {
            // To avoid race condition between socket connection succeeding and interface returning
            // a non-empty address list. Verify that interface returns a non-empty list, before
            // trying the socket connection.
            if (NetworkInterface.getByName(ifaceName).interfaceAddresses.isEmpty()) {
                return@check false
            }

            val sock = Os.socket(AF_INET6, SOCK_DGRAM, IPPROTO_UDP)
            tryTest {
                network.bindSocket(sock)
                Os.connect(sock, parseNumericAddress("ff02::fb%$ifaceName"), 12345)
                true
            }.catch<ErrnoException> {
                if (it.errno != ENETUNREACH && it.errno != EADDRNOTAVAIL) {
                    throw it
                }
                false
            } cleanup {
                Os.close(sock)
            }
        }

        lp.setLinkAddresses(NetworkInterface.getByName(ifaceName).interfaceAddresses.map {
            LinkAddress(it.address, it.networkPrefixLength.toInt())
        })
        agent.sendLinkProperties(lp)
        return agent
    }

    private fun makeTestServiceInfo(network: Network? = null) = NsdServiceInfo().also {
        it.serviceType = serviceType
        it.serviceName = serviceName
        it.network = network
        it.port = TEST_PORT
    }

    @After
    fun tearDown() {
        runAsShell(MANAGE_TEST_NETWORKS) {
            // Avoid throwing here if initializing failed in setUp
            if (this::testNetwork1.isInitialized) testNetwork1.close(cm)
            if (this::testNetwork2.isInitialized) testNetwork2.close(cm)
        }
        handlerThread.waitForIdle(TIMEOUT_MS)
        handlerThread.quitSafely()
        handlerThread.join()
    }

    @Test
    fun testNsdManager() {
        val si = NsdServiceInfo()
        si.serviceType = serviceType
        si.serviceName = serviceName
        // Test binary data with various bytes
        val testByteArray = byteArrayOf(-128, 127, 2, 1, 0, 1, 2)
        // Test string data with 256 characters (25 blocks of 10 characters + 6)
        val string256 = "1_________2_________3_________4_________5_________6_________" +
                "7_________8_________9_________10________11________12________13________" +
                "14________15________16________17________18________19________20________" +
                "21________22________23________24________25________123456"

        // Illegal attributes
        listOf(
                Triple(null, null, "null key"),
                Triple("", null, "empty key"),
                Triple(string256, null, "key with 256 characters"),
                Triple("key", string256.substring(3),
                        "key+value combination with more than 255 characters"),
                Triple("key", string256.substring(4), "key+value combination with 255 characters"),
                Triple("\u0019", null, "key with invalid character"),
                Triple("=", null, "key with invalid character"),
                Triple("\u007f", null, "key with invalid character")
        ).forEach {
            assertFailsWith<IllegalArgumentException>(
                    "Setting invalid ${it.third} unexpectedly succeeded") {
                si.setAttribute(it.first, it.second)
            }
        }

        // Allowed attributes
        si.setAttribute("booleanAttr", null as String?)
        si.setAttribute("keyValueAttr", "value")
        si.setAttribute("keyEqualsAttr", "=")
        si.setAttribute(" whiteSpaceKeyValueAttr ", " value ")
        si.setAttribute("binaryDataAttr", testByteArray)
        si.setAttribute("nullBinaryDataAttr", null as ByteArray?)
        si.setAttribute("emptyBinaryDataAttr", byteArrayOf())
        si.setAttribute("longkey", string256.substring(9))
        val socket = ServerSocket(0)
        val localPort = socket.localPort
        si.port = localPort
        if (DBG) Log.d(TAG, "Port = $localPort")

        val registrationRecord = NsdRegistrationRecord()
        // Test registering without an Executor
        nsdManager.registerService(si, NsdManager.PROTOCOL_DNS_SD, registrationRecord)
        val registeredInfo = registrationRecord.expectCallback<ServiceRegistered>(
                REGISTRATION_TIMEOUT_MS).serviceInfo

        // Only service name is included in ServiceRegistered callbacks
        assertNull(registeredInfo.serviceType)
        assertEquals(si.serviceName, registeredInfo.serviceName)

        val discoveryRecord = NsdDiscoveryRecord()
        // Test discovering without an Executor
        nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryRecord)

        // Expect discovery started
        discoveryRecord.expectCallback<DiscoveryStarted>()

        // Expect a service record to be discovered
        val foundInfo = discoveryRecord.waitForServiceDiscovered(
                registeredInfo.serviceName, serviceType)

        // Test resolving without an Executor
        val resolveRecord = NsdResolveRecord()
        nsdManager.resolveService(foundInfo, resolveRecord)
        val resolvedService = resolveRecord.expectCallback<ServiceResolved>().serviceInfo
        assertEquals(".$serviceType", resolvedService.serviceType)
        assertEquals(registeredInfo.serviceName, resolvedService.serviceName)

        // Check Txt attributes
        assertEquals(8, resolvedService.attributes.size)
        assertTrue(resolvedService.attributes.containsKey("booleanAttr"))
        assertNull(resolvedService.attributes["booleanAttr"])
        assertEquals("value", resolvedService.attributes["keyValueAttr"].utf8ToString())
        assertEquals("=", resolvedService.attributes["keyEqualsAttr"].utf8ToString())
        assertEquals(" value ",
                resolvedService.attributes[" whiteSpaceKeyValueAttr "].utf8ToString())
        assertEquals(string256.substring(9), resolvedService.attributes["longkey"].utf8ToString())
        assertArrayEquals(testByteArray, resolvedService.attributes["binaryDataAttr"])
        assertTrue(resolvedService.attributes.containsKey("nullBinaryDataAttr"))
        assertNull(resolvedService.attributes["nullBinaryDataAttr"])
        assertTrue(resolvedService.attributes.containsKey("emptyBinaryDataAttr"))
        if (isAtLeastU() || CompatChanges.isChangeEnabled(
                ConnectivityCompatChanges.ENABLE_PLATFORM_MDNS_BACKEND
            )) {
            assertArrayEquals(byteArrayOf(), resolvedService.attributes["emptyBinaryDataAttr"])
        } else {
            assertNull(resolvedService.attributes["emptyBinaryDataAttr"])
        }
        assertEquals(localPort, resolvedService.port)

        // Unregister the service
        nsdManager.unregisterService(registrationRecord)
        registrationRecord.expectCallback<ServiceUnregistered>()

        // Expect a callback for service lost
        val lostCb = discoveryRecord.expectCallbackEventually<ServiceLost> {
            it.serviceInfo.serviceName == serviceName
        }
        // Lost service types have a dot at the end
        assertEquals("$serviceType.", lostCb.serviceInfo.serviceType)

        // Register service again to see if NsdManager can discover it
        val si2 = NsdServiceInfo()
        si2.serviceType = serviceType
        si2.serviceName = serviceName
        si2.port = localPort
        val registrationRecord2 = NsdRegistrationRecord()
        nsdManager.registerService(si2, NsdManager.PROTOCOL_DNS_SD, registrationRecord2)
        val registeredInfo2 = registrationRecord2.expectCallback<ServiceRegistered>(
                REGISTRATION_TIMEOUT_MS).serviceInfo

        // Expect a service record to be discovered (and filter the ones
        // that are unrelated to this test)
        val foundInfo2 = discoveryRecord.waitForServiceDiscovered(
                registeredInfo2.serviceName, serviceType)

        // Resolve the service
        val resolveRecord2 = NsdResolveRecord()
        nsdManager.resolveService(foundInfo2, resolveRecord2)
        val resolvedService2 = resolveRecord2.expectCallback<ServiceResolved>().serviceInfo

        // Check that the resolved service doesn't have any TXT records
        assertEquals(0, resolvedService2.attributes.size)

        nsdManager.stopServiceDiscovery(discoveryRecord)

        discoveryRecord.expectCallbackEventually<DiscoveryStopped>()

        nsdManager.unregisterService(registrationRecord2)
        registrationRecord2.expectCallback<ServiceUnregistered>()
    }

    @Test
    fun testNsdManager_DiscoverOnNetwork() {
        val si = makeTestServiceInfo()
        val registrationRecord = NsdRegistrationRecord()
        val registeredInfo = registerService(registrationRecord, si)

        tryTest {
            val discoveryRecord = NsdDiscoveryRecord()
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD,
                    testNetwork1.network, Executor { it.run() }, discoveryRecord)

            val foundInfo = discoveryRecord.waitForServiceDiscovered(
                    serviceName, serviceType, testNetwork1.network)
            assertEquals(testNetwork1.network, foundInfo.network)

            // Rewind to ensure the service is not found on the other interface
            discoveryRecord.nextEvents.rewind(0)
            assertNull(discoveryRecord.nextEvents.poll(timeoutMs = 100L) {
                it is ServiceFound &&
                        it.serviceInfo.serviceName == registeredInfo.serviceName &&
                        it.serviceInfo.network != testNetwork1.network
            }, "The service should not be found on this network")
        } cleanup {
            nsdManager.unregisterService(registrationRecord)
        }
    }

    @Test
    fun testNsdManager_DiscoverWithNetworkRequest() {
        val si = makeTestServiceInfo()
        val handler = Handler(handlerThread.looper)
        val executor = Executor { handler.post(it) }

        val registrationRecord = NsdRegistrationRecord(expectedThreadId = handlerThread.threadId)
        val registeredInfo1 = registerService(registrationRecord, si, executor)
        val discoveryRecord = NsdDiscoveryRecord(expectedThreadId = handlerThread.threadId)

        tryTest {
            val specifier = TestNetworkSpecifier(testNetwork1.iface.interfaceName)
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD,
                    NetworkRequest.Builder()
                            .removeCapability(NET_CAPABILITY_TRUSTED)
                            .addTransportType(TRANSPORT_TEST)
                            .setNetworkSpecifier(specifier)
                            .build(),
                    executor, discoveryRecord)

            val discoveryStarted = discoveryRecord.expectCallback<DiscoveryStarted>()
            assertEquals(serviceType, discoveryStarted.serviceType)

            val serviceDiscovered = discoveryRecord.expectCallback<ServiceFound>()
            assertEquals(registeredInfo1.serviceName, serviceDiscovered.serviceInfo.serviceName)
            // Discovered service types have a dot at the end
            assertEquals("$serviceType.", serviceDiscovered.serviceInfo.serviceType)
            assertEquals(testNetwork1.network, serviceDiscovered.serviceInfo.network)

            // Unregister, then register the service back: it should be lost and found again
            nsdManager.unregisterService(registrationRecord)
            val serviceLost1 = discoveryRecord.expectCallback<ServiceLost>()
            assertEquals(registeredInfo1.serviceName, serviceLost1.serviceInfo.serviceName)
            assertEquals(testNetwork1.network, serviceLost1.serviceInfo.network)

            registrationRecord.expectCallback<ServiceUnregistered>()
            val registeredInfo2 = registerService(registrationRecord, si, executor)
            val serviceDiscovered2 = discoveryRecord.expectCallback<ServiceFound>()
            assertEquals(registeredInfo2.serviceName, serviceDiscovered2.serviceInfo.serviceName)
            assertEquals("$serviceType.", serviceDiscovered2.serviceInfo.serviceType)
            assertEquals(testNetwork1.network, serviceDiscovered2.serviceInfo.network)

            // Teardown, then bring back up a network on the test interface: the service should
            // go away, then come back
            testNetwork1.agent.unregister()
            val serviceLost = discoveryRecord.expectCallback<ServiceLost>()
            assertEquals(registeredInfo2.serviceName, serviceLost.serviceInfo.serviceName)
            assertEquals(testNetwork1.network, serviceLost.serviceInfo.network)

            val newAgent = runAsShell(MANAGE_TEST_NETWORKS) {
                registerTestNetworkAgent(testNetwork1.iface.interfaceName)
            }
            val newNetwork = newAgent.network ?: fail("Registered agent should have a network")
            val serviceDiscovered3 = discoveryRecord.expectCallback<ServiceFound>()
            assertEquals(registeredInfo2.serviceName, serviceDiscovered3.serviceInfo.serviceName)
            assertEquals("$serviceType.", serviceDiscovered3.serviceInfo.serviceType)
            assertEquals(newNetwork, serviceDiscovered3.serviceInfo.network)
        } cleanupStep {
            nsdManager.stopServiceDiscovery(discoveryRecord)
            discoveryRecord.expectCallback<DiscoveryStopped>()
        } cleanup {
            nsdManager.unregisterService(registrationRecord)
        }
    }

    @Test
    fun testNsdManager_DiscoverWithNetworkRequest_NoMatchingNetwork() {
        val handler = Handler(handlerThread.looper)
        val executor = Executor { handler.post(it) }

        val discoveryRecord = NsdDiscoveryRecord(expectedThreadId = handlerThread.threadId)
        val specifier = TestNetworkSpecifier(testNetwork1.iface.interfaceName)

        tryTest {
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD,
                    NetworkRequest.Builder()
                            .removeCapability(NET_CAPABILITY_TRUSTED)
                            .addTransportType(TRANSPORT_TEST)
                            // Specified network does not have this capability
                            .addCapability(NET_CAPABILITY_TEMPORARILY_NOT_METERED)
                            .setNetworkSpecifier(specifier)
                            .build(),
                    executor, discoveryRecord)
            discoveryRecord.expectCallback<DiscoveryStarted>()
        } cleanup {
            nsdManager.stopServiceDiscovery(discoveryRecord)
            discoveryRecord.expectCallback<DiscoveryStopped>()
        }
    }

    private fun checkAddressScopeId(iface: TestNetworkInterface, address: List<InetAddress>) {
        val targetSdkVersion = context.packageManager
            .getTargetSdkVersion(context.applicationInfo.packageName)
        if (targetSdkVersion <= Build.VERSION_CODES.TIRAMISU) {
            return
        }
        val ifaceIdx = NetworkInterface.getByName(iface.interfaceName).index
        address.forEach {
            if (it is Inet6Address && it.isLinkLocalAddress) {
                assertEquals(ifaceIdx, it.scopeId)
            }
        }
    }

    @Test
    fun testNsdManager_ResolveOnNetwork() {
        val si = makeTestServiceInfo()
        val registrationRecord = NsdRegistrationRecord()
        val registeredInfo = registerService(registrationRecord, si)
        tryTest {
            val resolveRecord = NsdResolveRecord()

            val discoveryRecord = NsdDiscoveryRecord()
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryRecord)

            val foundInfo1 = discoveryRecord.waitForServiceDiscovered(
                    serviceName, serviceType, testNetwork1.network)
            assertEquals(testNetwork1.network, foundInfo1.network)
            // Rewind as the service could be found on each interface in any order
            discoveryRecord.nextEvents.rewind(0)
            val foundInfo2 = discoveryRecord.waitForServiceDiscovered(
                    serviceName, serviceType, testNetwork2.network)
            assertEquals(testNetwork2.network, foundInfo2.network)

            nsdManager.resolveService(foundInfo1, Executor { it.run() }, resolveRecord)
            val cb = resolveRecord.expectCallback<ServiceResolved>()
            cb.serviceInfo.let {
                // Resolved service type has leading dot
                assertEquals(".$serviceType", it.serviceType)
                assertEquals(registeredInfo.serviceName, it.serviceName)
                assertEquals(si.port, it.port)
                assertEquals(testNetwork1.network, it.network)
                checkAddressScopeId(testNetwork1.iface, it.hostAddresses)
            }
            // TODO: check that MDNS packets are sent only on testNetwork1.
        } cleanupStep {
            nsdManager.unregisterService(registrationRecord)
        } cleanup {
            registrationRecord.expectCallback<ServiceUnregistered>()
        }
    }

    @Test
    fun testNsdManager_RegisterOnNetwork() {
        val si = makeTestServiceInfo(testNetwork1.network)
        // Register service on testNetwork1
        val registrationRecord = NsdRegistrationRecord()
        registerService(registrationRecord, si)
        val discoveryRecord = NsdDiscoveryRecord()
        val discoveryRecord2 = NsdDiscoveryRecord()
        val discoveryRecord3 = NsdDiscoveryRecord()

        tryTest {
            // Discover service on testNetwork1.
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD,
                testNetwork1.network, Executor { it.run() }, discoveryRecord)
            // Expect that service is found on testNetwork1
            val foundInfo = discoveryRecord.waitForServiceDiscovered(
                serviceName, serviceType, testNetwork1.network)
            assertEquals(testNetwork1.network, foundInfo.network)

            // Discover service on testNetwork2.
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD,
                testNetwork2.network, Executor { it.run() }, discoveryRecord2)
            // Expect that discovery is started then no other callbacks.
            discoveryRecord2.expectCallback<DiscoveryStarted>()
            discoveryRecord2.assertNoCallback()

            // Discover service on all networks (not specify any network).
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD,
                null as Network? /* network */, Executor { it.run() }, discoveryRecord3)
            // Expect that service is found on testNetwork1
            val foundInfo3 = discoveryRecord3.waitForServiceDiscovered(
                    serviceName, serviceType, testNetwork1.network)
            assertEquals(testNetwork1.network, foundInfo3.network)
        } cleanupStep {
            nsdManager.stopServiceDiscovery(discoveryRecord2)
            discoveryRecord2.expectCallback<DiscoveryStopped>()
        } cleanup {
            nsdManager.unregisterService(registrationRecord)
        }
    }

    @Test
    fun testNsdManager_RegisterServiceNameWithNonStandardCharacters() {
        val serviceNames = "^Nsd.Test|Non-#AsCiI\\Characters&\\ufffe テスト 測試"
        val si = NsdServiceInfo().apply {
            serviceType = this@NsdManagerTest.serviceType
            serviceName = serviceNames
            port = 12345 // Test won't try to connect so port does not matter
        }

        // Register the service name which contains non-standard characters.
        val registrationRecord = NsdRegistrationRecord()
        nsdManager.registerService(si, NsdManager.PROTOCOL_DNS_SD, registrationRecord)
        registrationRecord.expectCallback<ServiceRegistered>(REGISTRATION_TIMEOUT_MS)

        tryTest {
            // Discover that service name.
            val discoveryRecord = NsdDiscoveryRecord()
            nsdManager.discoverServices(
                serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryRecord
            )
            val foundInfo = discoveryRecord.waitForServiceDiscovered(serviceNames, serviceType)

            // Expect that resolving the service name works properly even service name contains
            // non-standard characters.
            val resolveRecord = NsdResolveRecord()
            nsdManager.resolveService(foundInfo, resolveRecord)
            val resolvedCb = resolveRecord.expectCallback<ServiceResolved>()
            assertEquals(foundInfo.serviceName, resolvedCb.serviceInfo.serviceName)
        } cleanupStep {
            nsdManager.unregisterService(registrationRecord)
        } cleanup {
            registrationRecord.expectCallback<ServiceUnregistered>()
        }
    }

    @Test
    fun testRegisterService_twoServicesWithSameNameButDifferentTypes_registeredAndDiscoverable() {
        val si1 = NsdServiceInfo().also {
            it.network = testNetwork1.network
            it.serviceName = serviceName
            it.serviceType = serviceType
            it.port = TEST_PORT
        }
        val si2 = NsdServiceInfo().also {
            it.network = testNetwork1.network
            it.serviceName = serviceName
            it.serviceType = serviceType2
            it.port = TEST_PORT + 1
        }
        val registrationRecord1 = NsdRegistrationRecord()
        val registrationRecord2 = NsdRegistrationRecord()
        val discoveryRecord1 = NsdDiscoveryRecord()
        val discoveryRecord2 = NsdDiscoveryRecord()
        tryTest {
            registerService(registrationRecord1, si1)
            registerService(registrationRecord2, si2)

            nsdManager.discoverServices(serviceType,
                    NsdManager.PROTOCOL_DNS_SD,
                    testNetwork1.network, Executor { it.run() }, discoveryRecord1)
            nsdManager.discoverServices(serviceType2,
                    NsdManager.PROTOCOL_DNS_SD,
                    testNetwork1.network, Executor { it.run() }, discoveryRecord2)

            discoveryRecord1.waitForServiceDiscovered(serviceName, serviceType,
                    testNetwork1.network)
            discoveryRecord2.waitForServiceDiscovered(serviceName, serviceType2,
                    testNetwork1.network)
        } cleanupStep {
            nsdManager.stopServiceDiscovery(discoveryRecord1)
            nsdManager.stopServiceDiscovery(discoveryRecord2)
        } cleanup {
            nsdManager.unregisterService(registrationRecord1)
            nsdManager.unregisterService(registrationRecord2)
        }
    }

    fun checkOffloadServiceInfo(serviceInfo: OffloadServiceInfo, si: NsdServiceInfo) {
        val expectedServiceType = si.serviceType.split(",")[0]
        assertEquals(si.serviceName, serviceInfo.key.serviceName)
        assertEquals(expectedServiceType, serviceInfo.key.serviceType)
        assertEquals(listOf("_subtype"), serviceInfo.subtypes)
        assertTrue(serviceInfo.hostname.startsWith("Android_"))
        assertTrue(serviceInfo.hostname.endsWith("local"))
        // Test service types should not be in the priority list
        assertEquals(Integer.MAX_VALUE, serviceInfo.priority)
        assertEquals(OffloadEngine.OFFLOAD_TYPE_REPLY.toLong(), serviceInfo.offloadType)
        val offloadPayload = serviceInfo.offloadPayload
        assertNotNull(offloadPayload)
        val dnsPacket = TestDnsPacket(offloadPayload, dstAddr = multicastIpv6Addr)
        assertEquals(0x8400, dnsPacket.header.flags)
        assertEquals(0, dnsPacket.records[DnsPacket.QDSECTION].size)
        assertTrue(dnsPacket.records[DnsPacket.ANSECTION].size >= 5)
        assertEquals(0, dnsPacket.records[DnsPacket.NSSECTION].size)
        assertEquals(0, dnsPacket.records[DnsPacket.ARSECTION].size)

        val ptrRecord = dnsPacket.records[DnsPacket.ANSECTION][0]
        assertEquals("$expectedServiceType.local", ptrRecord.dName)
        assertEquals(0x0C /* PTR */, ptrRecord.nsType)
        val ptrSubRecord = dnsPacket.records[DnsPacket.ANSECTION][1]
        assertEquals("_subtype._sub.$expectedServiceType.local", ptrSubRecord.dName)
        assertEquals(0x0C /* PTR */, ptrSubRecord.nsType)
        val srvRecord = dnsPacket.records[DnsPacket.ANSECTION][2]
        assertEquals("${si.serviceName}.$expectedServiceType.local", srvRecord.dName)
        assertEquals(0x21 /* SRV */, srvRecord.nsType)
        val txtRecord = dnsPacket.records[DnsPacket.ANSECTION][3]
        assertEquals("${si.serviceName}.$expectedServiceType.local", txtRecord.dName)
        assertEquals(0x10 /* TXT */, txtRecord.nsType)
        val iface = NetworkInterface.getByName(testNetwork1.iface.interfaceName)
        val allAddress = iface.inetAddresses.toList()
        for (i in 4 until dnsPacket.records[DnsPacket.ANSECTION].size) {
            val addressRecord = dnsPacket.records[DnsPacket.ANSECTION][i]
            assertTrue(addressRecord.dName.startsWith("Android_"))
            assertTrue(addressRecord.dName.endsWith("local"))
            assertTrue(addressRecord.nsType in arrayOf(0x1C /* AAAA */, 0x01 /* A */))
            val rData = addressRecord.rr
            assertNotNull(rData)
            val addr = InetAddress.getByAddress(rData)
            assertTrue(addr in allAddress)
        }
    }

    @Test
    fun testNsdManager_registerOffloadEngine() {
        val targetSdkVersion = context.packageManager
            .getTargetSdkVersion(context.applicationInfo.packageName)
        // The offload callbacks are only supported with the new backend,
        // enabled with target SDK U+.
        assumeTrue(isAtLeastU() || targetSdkVersion > Build.VERSION_CODES.TIRAMISU)

        // TODO: also have a test that use an executor that runs in a different thread, and pass
        // in the thread ID NsdServiceInfo to check it
        val si1 = NsdServiceInfo()
        si1.serviceType = "$serviceType,_subtype"
        si1.serviceName = serviceName
        si1.network = testNetwork1.network
        si1.port = 23456
        val record1 = NsdRegistrationRecord()

        val si2 = NsdServiceInfo()
        si2.serviceType = "$serviceType,_subtype"
        si2.serviceName = serviceName2
        si2.network = testNetwork1.network
        si2.port = 12345
        val record2 = NsdRegistrationRecord()
        val offloadEngine = TestNsdOffloadEngine()

        tryTest {
            // Register service before the OffloadEngine is registered.
            nsdManager.registerService(si1, NsdManager.PROTOCOL_DNS_SD, record1)
            record1.expectCallback<ServiceRegistered>()
            runAsShell(NETWORK_SETTINGS) {
                nsdManager.registerOffloadEngine(testNetwork1.iface.interfaceName,
                    OffloadEngine.OFFLOAD_TYPE_REPLY.toLong(),
                    OffloadEngine.OFFLOAD_CAPABILITY_BYPASS_MULTICAST_LOCK.toLong(),
                    { it.run() }, offloadEngine)
            }
            val addOrUpdateEvent1 = offloadEngine
                .expectCallbackEventually<TestNsdOffloadEngine.OffloadEvent.AddOrUpdateEvent> {
                    it.info.key.serviceName == si1.serviceName
                }
            checkOffloadServiceInfo(addOrUpdateEvent1.info, si1)

            // Register service after OffloadEngine is registered.
            nsdManager.registerService(si2, NsdManager.PROTOCOL_DNS_SD, record2)
            record2.expectCallback<ServiceRegistered>()
            val addOrUpdateEvent2 = offloadEngine
                .expectCallbackEventually<TestNsdOffloadEngine.OffloadEvent.AddOrUpdateEvent> {
                    it.info.key.serviceName == si2.serviceName
                }
            checkOffloadServiceInfo(addOrUpdateEvent2.info, si2)

            nsdManager.unregisterService(record2)
            record2.expectCallback<ServiceUnregistered>()
            val unregisterEvent = offloadEngine
                .expectCallbackEventually<TestNsdOffloadEngine.OffloadEvent.RemoveEvent> {
                    it.info.key.serviceName == si2.serviceName
                }
            checkOffloadServiceInfo(unregisterEvent.info, si2)
        } cleanupStep {
            runAsShell(NETWORK_SETTINGS) {
                nsdManager.unregisterOffloadEngine(offloadEngine)
            }
        } cleanup {
            nsdManager.unregisterService(record1)
            record1.expectCallback<ServiceUnregistered>()
        }
    }

    private fun checkConnectSocketToMdnsd(shouldFail: Boolean) {
        val discoveryRecord = NsdDiscoveryRecord()
        val localSocket = LocalSocket()
        tryTest {
            // Discover any service from NsdManager to enforce NsdService to start the mdnsd.
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryRecord)
            discoveryRecord.expectCallback<DiscoveryStarted>()

            // Checks the /dev/socket/mdnsd is created.
            val socket = File("/dev/socket/mdnsd")
            val doesSocketExist = PollingCheck.waitFor(
                TIMEOUT_MS,
                {
                    socket.exists()
                },
                { doesSocketExist ->
                    doesSocketExist
                },
            )

            // If the socket is not created, then no need to check the access.
            if (doesSocketExist) {
                // Create a LocalSocket and try to connect to mdnsd.
                assertFalse("LocalSocket is connected.", localSocket.isConnected)
                val address = LocalSocketAddress("mdnsd", LocalSocketAddress.Namespace.RESERVED)
                if (shouldFail) {
                    assertFailsWith<IOException>("Expect fail but socket connected") {
                        localSocket.connect(address)
                    }
                } else {
                    localSocket.connect(address)
                    assertTrue("LocalSocket is not connected.", localSocket.isConnected)
                }
            }
        } cleanup {
            localSocket.close()
            nsdManager.stopServiceDiscovery(discoveryRecord)
            discoveryRecord.expectCallback<DiscoveryStopped>()
        }
    }

    /**
     * Starting from Android U, the access to the /dev/socket/mdnsd is blocked by the
     * sepolicy(b/265364111).
     */
    @DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
    @Test
    fun testCannotConnectSocketToMdnsd() {
        val targetSdkVersion = context.packageManager
                .getTargetSdkVersion(context.applicationInfo.packageName)
        assumeTrue(targetSdkVersion > Build.VERSION_CODES.TIRAMISU)
        val firstApiLevel = min(PropertyUtil.getFirstApiLevel(), PropertyUtil.getVendorApiLevel())
        // The sepolicy is implemented in the vendor image, so the access may not be blocked if
        // the vendor image is not update to date.
        assumeTrue(firstApiLevel > Build.VERSION_CODES.TIRAMISU)
        checkConnectSocketToMdnsd(shouldFail = true)
    }

    @Test @CtsNetTestCasesMaxTargetSdk33("mdnsd socket is accessible up to target SDK 33")
    fun testCanConnectSocketToMdnsd() {
        checkConnectSocketToMdnsd(shouldFail = false)
    }

    // Native mdns powered by Netd is removed after U.
    @DevSdkIgnoreRule.IgnoreAfter(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Test @CtsNetTestCasesMaxTargetSdk30("Socket is started with the service up to target SDK 30")
    fun testManagerCreatesLegacySocket() {
        nsdManager // Ensure the lazy-init member is initialized, so NsdManager is created
        val socket = File("/dev/socket/mdnsd")
        val timeout = System.currentTimeMillis() + TIMEOUT_MS
        while (!socket.exists() && System.currentTimeMillis() < timeout) {
            Thread.sleep(10)
        }
        assertTrue("$socket was not found after $TIMEOUT_MS ms", socket.exists())
    }

    // The compat change is part of a connectivity module update that applies to T+
    @ConnectivityModuleTest @DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.S_V2)
    @Test @CtsNetTestCasesMaxTargetSdk30("Socket is started with the service up to target SDK 30")
    fun testManagerCreatesLegacySocket_CompatChange() {
        // The socket may have been already created by some other app, or some other test, in which
        // case this test cannot verify creation. At least verify that the compat change is
        // disabled in a process with max SDK 30; unit tests already verify that start is requested
        // when the compat change is disabled.
        // Note that before T the compat constant had a different int value.
        assertFalse(CompatChanges.isChangeEnabled(
                ConnectivityCompatChanges.RUN_NATIVE_NSD_ONLY_IF_LEGACY_APPS_T_AND_LATER))
    }

    @Test
    fun testStopServiceResolution() {
        val si = makeTestServiceInfo()
        val resolveRecord = NsdResolveRecord()
        // Try to resolve an unknown service then stop it immediately.
        // Expected ResolutionStopped callback.
        nsdManager.resolveService(si, { it.run() }, resolveRecord)
        nsdManager.stopServiceResolution(resolveRecord)
        val stoppedCb = resolveRecord.expectCallback<ResolutionStopped>()
        assertEquals(si.serviceName, stoppedCb.serviceInfo.serviceName)
        assertEquals(si.serviceType, stoppedCb.serviceInfo.serviceType)
    }

    @Test
    fun testRegisterServiceInfoCallback() {
        val lp = cm.getLinkProperties(testNetwork1.network)
        assertNotNull(lp)
        val addresses = lp.addresses
        assertFalse(addresses.isEmpty())

        val si = makeTestServiceInfo(testNetwork1.network)

        // Register service on the network
        val registrationRecord = NsdRegistrationRecord()
        registerService(registrationRecord, si)

        val discoveryRecord = NsdDiscoveryRecord()
        val cbRecord = NsdServiceInfoCallbackRecord()
        tryTest {
            // Discover service on the network.
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD,
                    testNetwork1.network, Executor { it.run() }, discoveryRecord)
            val foundInfo = discoveryRecord.waitForServiceDiscovered(
                    serviceName, serviceType, testNetwork1.network)

            // Register service callback and check the addresses are the same as network addresses
            nsdManager.registerServiceInfoCallback(foundInfo, { it.run() }, cbRecord)
            val serviceInfoCb = cbRecord.expectCallback<ServiceUpdated>()
            assertEquals(foundInfo.serviceName, serviceInfoCb.serviceInfo.serviceName)
            val hostAddresses = serviceInfoCb.serviceInfo.hostAddresses
            assertEquals(addresses.size, hostAddresses.size)
            for (hostAddress in hostAddresses) {
                assertTrue(addresses.contains(hostAddress))
            }
            checkAddressScopeId(testNetwork1.iface, serviceInfoCb.serviceInfo.hostAddresses)
        } cleanupStep {
            nsdManager.unregisterService(registrationRecord)
            registrationRecord.expectCallback<ServiceUnregistered>()
            discoveryRecord.expectCallback<ServiceLost>()
            cbRecord.expectCallback<ServiceUpdatedLost>()
        } cleanupStep {
            // Cancel subscription and check stop callback received.
            nsdManager.unregisterServiceInfoCallback(cbRecord)
            cbRecord.expectCallback<UnregisterCallbackSucceeded>()
        } cleanup {
            nsdManager.stopServiceDiscovery(discoveryRecord)
            discoveryRecord.expectCallback<DiscoveryStopped>()
        }
    }

    @Test
    fun testStopServiceResolutionFailedCallback() {
        // It's not possible to make ResolutionListener#onStopResolutionFailed callback sending
        // because it is only sent in very edge-case scenarios when the legacy implementation is
        // used, and the legacy implementation is never used in the current AOSP builds. Considering
        // that this callback isn't expected to be sent at all at the moment, and this is just an
        // interface with no implementation. To verify this callback, just call
        // onStopResolutionFailed on the record directly then verify it is received.
        val resolveRecord = NsdResolveRecord()
        resolveRecord.onStopResolutionFailed(
                NsdServiceInfo(), NsdManager.FAILURE_OPERATION_NOT_RUNNING)
        val failedCb = resolveRecord.expectCallback<StopResolutionFailed>()
        assertEquals(NsdManager.FAILURE_OPERATION_NOT_RUNNING, failedCb.errorCode)
    }

    @Test
    fun testSubtypeAdvertisingAndDiscovery() {
        val si = makeTestServiceInfo(network = testNetwork1.network)
        // Test "_type._tcp.local,_subtype" syntax with the registration
        si.serviceType = si.serviceType + ",_subtype"

        val registrationRecord = NsdRegistrationRecord()

        val baseTypeDiscoveryRecord = NsdDiscoveryRecord()
        val subtypeDiscoveryRecord = NsdDiscoveryRecord()
        val otherSubtypeDiscoveryRecord = NsdDiscoveryRecord()
        tryTest {
            registerService(registrationRecord, si)

            // Test "_subtype._type._tcp.local" syntax with discovery. Note this is not
            // "_subtype._sub._type._tcp.local".
            nsdManager.discoverServices(serviceType,
                    NsdManager.PROTOCOL_DNS_SD,
                    testNetwork1.network, Executor { it.run() }, baseTypeDiscoveryRecord)
            nsdManager.discoverServices("_othersubtype.$serviceType",
                    NsdManager.PROTOCOL_DNS_SD,
                    testNetwork1.network, Executor { it.run() }, otherSubtypeDiscoveryRecord)
            nsdManager.discoverServices("_subtype.$serviceType",
                    NsdManager.PROTOCOL_DNS_SD,
                    testNetwork1.network, Executor { it.run() }, subtypeDiscoveryRecord)

            subtypeDiscoveryRecord.waitForServiceDiscovered(
                    serviceName, serviceType, testNetwork1.network)
            baseTypeDiscoveryRecord.waitForServiceDiscovered(
                    serviceName, serviceType, testNetwork1.network)
            otherSubtypeDiscoveryRecord.expectCallback<DiscoveryStarted>()
            // The subtype callback was registered later but called, no need for an extra delay
            otherSubtypeDiscoveryRecord.assertNoCallback(timeoutMs = 0)
        } cleanupStep {
            nsdManager.stopServiceDiscovery(baseTypeDiscoveryRecord)
            nsdManager.stopServiceDiscovery(subtypeDiscoveryRecord)
            nsdManager.stopServiceDiscovery(otherSubtypeDiscoveryRecord)

            baseTypeDiscoveryRecord.expectCallback<DiscoveryStopped>()
            subtypeDiscoveryRecord.expectCallback<DiscoveryStopped>()
            otherSubtypeDiscoveryRecord.expectCallback<DiscoveryStopped>()
        } cleanup {
            nsdManager.unregisterService(registrationRecord)
        }
    }

    @Test
    fun testSubtypeAdvertisingAndDiscovery_withSetSubtypesApi() {
        runSubtypeAdvertisingAndDiscoveryTest(useLegacySpecifier = false)
    }

    @Test
    fun testSubtypeAdvertisingAndDiscovery_withSetSubtypesApiAndLegacySpecifier() {
        runSubtypeAdvertisingAndDiscoveryTest(useLegacySpecifier = true)
    }

    private fun runSubtypeAdvertisingAndDiscoveryTest(useLegacySpecifier: Boolean) {
        val si = makeTestServiceInfo(network = testNetwork1.network)
        if (useLegacySpecifier) {
            si.subtypes = setOf("_subtype1")

            // Test "_type._tcp.local,_subtype" syntax with the registration
            si.serviceType = si.serviceType + ",_subtype2"
        } else {
            si.subtypes = setOf("_subtype1", "_subtype2")
        }

        val registrationRecord = NsdRegistrationRecord()

        val baseTypeDiscoveryRecord = NsdDiscoveryRecord()
        val subtype1DiscoveryRecord = NsdDiscoveryRecord()
        val subtype2DiscoveryRecord = NsdDiscoveryRecord()
        val otherSubtypeDiscoveryRecord = NsdDiscoveryRecord()
        tryTest {
            registerService(registrationRecord, si)

            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD,
                    testNetwork1.network, Executor { it.run() }, baseTypeDiscoveryRecord)

            // Test "<subtype>._type._tcp.local" syntax with discovery. Note this is not
            // "<subtype>._sub._type._tcp.local".
            nsdManager.discoverServices("_othersubtype.$serviceType",
                    NsdManager.PROTOCOL_DNS_SD,
                    testNetwork1.network, Executor { it.run() }, otherSubtypeDiscoveryRecord)
            nsdManager.discoverServices("_subtype1.$serviceType",
                    NsdManager.PROTOCOL_DNS_SD,
                    testNetwork1.network, Executor { it.run() }, subtype1DiscoveryRecord)

            nsdManager.discoverServices(
                    DiscoveryRequest.Builder(serviceType).setSubtype("_subtype2")
                            .setNetwork(testNetwork1.network).build(),
                    Executor { it.run() }, subtype2DiscoveryRecord)

            val info1 = subtype1DiscoveryRecord.waitForServiceDiscovered(
                    serviceName, serviceType, testNetwork1.network)
            assertTrue(info1.subtypes.contains("_subtype1"))
            val info2 = subtype2DiscoveryRecord.waitForServiceDiscovered(
                    serviceName, serviceType, testNetwork1.network)
            assertTrue(info2.subtypes.contains("_subtype2"))
            baseTypeDiscoveryRecord.waitForServiceDiscovered(
                    serviceName, serviceType, testNetwork1.network)
            otherSubtypeDiscoveryRecord.expectCallback<DiscoveryStarted>()
            // The subtype callback was registered later but called, no need for an extra delay
            otherSubtypeDiscoveryRecord.assertNoCallback(timeoutMs = 0)
        } cleanupStep {
            nsdManager.stopServiceDiscovery(baseTypeDiscoveryRecord)
            nsdManager.stopServiceDiscovery(subtype1DiscoveryRecord)
            nsdManager.stopServiceDiscovery(subtype2DiscoveryRecord)
            nsdManager.stopServiceDiscovery(otherSubtypeDiscoveryRecord)

            baseTypeDiscoveryRecord.expectCallback<DiscoveryStopped>()
            subtype1DiscoveryRecord.expectCallback<DiscoveryStopped>()
            subtype2DiscoveryRecord.expectCallback<DiscoveryStopped>()
            otherSubtypeDiscoveryRecord.expectCallback<DiscoveryStopped>()
        } cleanup {
            nsdManager.unregisterService(registrationRecord)
        }
    }

    @Test
    fun testMultipleSubTypeAdvertisingAndDiscovery_withUpdate() {
        val si1 = makeTestServiceInfo(network = testNetwork1.network).apply {
            serviceType += ",_subtype1"
        }
        val si2 = makeTestServiceInfo(network = testNetwork1.network).apply {
            serviceType += ",_subtype2"
        }
        val registrationRecord = NsdRegistrationRecord()
        val subtype3DiscoveryRecord = NsdDiscoveryRecord()
        tryTest {
            registerService(registrationRecord, si1)
            updateService(registrationRecord, si2)
            nsdManager.discoverServices("_subtype2.$serviceType",
                    NsdManager.PROTOCOL_DNS_SD, testNetwork1.network,
                    { it.run() }, subtype3DiscoveryRecord)
            subtype3DiscoveryRecord.waitForServiceDiscovered(serviceName,
                    serviceType, testNetwork1.network)
        } cleanupStep {
            nsdManager.stopServiceDiscovery(subtype3DiscoveryRecord)
            subtype3DiscoveryRecord.expectCallback<DiscoveryStopped>()
        } cleanup {
            nsdManager.unregisterService(registrationRecord)
        }
    }

    @Test
    fun testSubtypeAdvertisingAndDiscovery_nonAlphanumericalSubtypes() {
        // All non-alphanumerical characters between 0x20 and 0x7e, with a leading underscore
        val nonAlphanumSubtype = "_ !\"#\$%&'()*+-/:;<=>?@[\\]^_`{|}"
        // Test both legacy syntax and the subtypes setter, on different networks
        val si1 = makeTestServiceInfo(network = testNetwork1.network).apply {
            serviceType = "$serviceType,_test1,$nonAlphanumSubtype"
        }
        val si2 = makeTestServiceInfo(network = testNetwork2.network).apply {
            subtypes = setOf("_test2", nonAlphanumSubtype)
        }

        val registrationRecord1 = NsdRegistrationRecord()
        val registrationRecord2 = NsdRegistrationRecord()
        val subtypeDiscoveryRecord1 = NsdDiscoveryRecord()
        val subtypeDiscoveryRecord2 = NsdDiscoveryRecord()
        tryTest {
            registerService(registrationRecord1, si1)
            registerService(registrationRecord2, si2)
            nsdManager.discoverServices(DiscoveryRequest.Builder(serviceType)
                .setSubtype(nonAlphanumSubtype)
                .setNetwork(testNetwork1.network)
                .build(), { it.run() }, subtypeDiscoveryRecord1)
            nsdManager.discoverServices("$nonAlphanumSubtype.$serviceType",
                NsdManager.PROTOCOL_DNS_SD, testNetwork2.network, { it.run() },
                subtypeDiscoveryRecord2)

            val discoveredInfo1 = subtypeDiscoveryRecord1.waitForServiceDiscovered(serviceName,
                serviceType, testNetwork1.network)
            val discoveredInfo2 = subtypeDiscoveryRecord2.waitForServiceDiscovered(serviceName,
                serviceType, testNetwork2.network)
            assertTrue(discoveredInfo1.subtypes.contains(nonAlphanumSubtype))
            assertTrue(discoveredInfo2.subtypes.contains(nonAlphanumSubtype))
        } cleanupStep {
            nsdManager.stopServiceDiscovery(subtypeDiscoveryRecord1)
            subtypeDiscoveryRecord1.expectCallback<DiscoveryStopped>()
        } cleanupStep {
            nsdManager.stopServiceDiscovery(subtypeDiscoveryRecord2)
            subtypeDiscoveryRecord2.expectCallback<DiscoveryStopped>()
        } cleanup {
            nsdManager.unregisterService(registrationRecord1)
            nsdManager.unregisterService(registrationRecord2)
        }
    }

    @Test
    fun testSubtypeDiscovery_typeMatchButSubtypeNotMatch_notDiscovered() {
        val si1 = makeTestServiceInfo(network = testNetwork1.network).apply {
            serviceType += ",_subtype1"
        }
        val registrationRecord = NsdRegistrationRecord()
        val subtype2DiscoveryRecord = NsdDiscoveryRecord()
        tryTest {
            registerService(registrationRecord, si1)
            val request = DiscoveryRequest.Builder(serviceType)
                    .setSubtype("_subtype2").setNetwork(testNetwork1.network).build()
            nsdManager.discoverServices(request, { it.run() }, subtype2DiscoveryRecord)
            subtype2DiscoveryRecord.expectCallback<DiscoveryStarted>()
            subtype2DiscoveryRecord.assertNoCallback(timeoutMs = 2000)
        } cleanupStep {
            nsdManager.stopServiceDiscovery(subtype2DiscoveryRecord)
            subtype2DiscoveryRecord.expectCallback<DiscoveryStopped>()
        } cleanup {
            nsdManager.unregisterService(registrationRecord)
        }
    }

    @Test
    fun testSubtypeAdvertising_tooManySubtypes_returnsFailureBadParameters() {
        val si = makeTestServiceInfo(network = testNetwork1.network)
        // Sets 101 subtypes in total
        val seq = generateSequence(1) { it + 1}
        si.subtypes = seq.take(100).toList().map {it -> "_subtype" + it}.toSet()
        si.serviceType = si.serviceType + ",_subtype"

        val record = NsdRegistrationRecord()
        nsdManager.registerService(si, NsdManager.PROTOCOL_DNS_SD, Executor { it.run() }, record)

        val failedCb = record.expectCallback<RegistrationFailed>(REGISTRATION_TIMEOUT_MS)
        assertEquals(NsdManager.FAILURE_BAD_PARAMETERS, failedCb.errorCode)
    }

    @Test
    fun testSubtypeAdvertising_emptySubtypeLabel_returnsFailureBadParameters() {
        val si = makeTestServiceInfo(network = testNetwork1.network)
        si.subtypes = setOf("")

        val record = NsdRegistrationRecord()
        nsdManager.registerService(si, NsdManager.PROTOCOL_DNS_SD, Executor { it.run() }, record)

        val failedCb = record.expectCallback<RegistrationFailed>(REGISTRATION_TIMEOUT_MS)
        assertEquals(NsdManager.FAILURE_BAD_PARAMETERS, failedCb.errorCode)
    }

    @Test
    fun testRegisterWithConflictDuringProbing() {
        // This test requires shims supporting T+ APIs (NsdServiceInfo.network)
        assumeTrue(TestUtils.shouldTestTApis())

        val si = makeTestServiceInfo(testNetwork1.network)

        val packetReader = TapPacketReader(Handler(handlerThread.looper),
                testNetwork1.iface.fileDescriptor.fileDescriptor, 1500 /* maxPacketSize */)
        packetReader.startAsyncForTest()
        handlerThread.waitForIdle(TIMEOUT_MS)

        // Register service on testNetwork1
        val registrationRecord = NsdRegistrationRecord()
        nsdManager.registerService(si, NsdManager.PROTOCOL_DNS_SD, { it.run() },
                registrationRecord)

        tryTest {
            assertNotNull(packetReader.pollForProbe(serviceName, serviceType),
                    "Did not find a probe for the service")
            packetReader.sendResponse(buildConflictingAnnouncement())

            // Registration must use an updated name to avoid the conflict
            val cb = registrationRecord.expectCallback<ServiceRegistered>(REGISTRATION_TIMEOUT_MS)
            cb.serviceInfo.serviceName.let {
                assertTrue("Unexpected registered name: $it",
                        it.startsWith(serviceName) && it != serviceName)
            }
        } cleanupStep {
            nsdManager.unregisterService(registrationRecord)
            registrationRecord.expectCallback<ServiceUnregistered>()
        } cleanup {
            packetReader.handler.post { packetReader.stop() }
            handlerThread.waitForIdle(TIMEOUT_MS)
        }
    }

    @Test
    fun testRegisterServiceWithCustomHostAndAddresses_conflictDuringProbing_hostRenamed() {
        val si = makeTestServiceInfo(testNetwork1.network).apply {
            hostname = customHostname
            hostAddresses = listOf(
                    parseNumericAddress("192.0.2.24"),
                    parseNumericAddress("2001:db8::3"))
        }

        val packetReader = TapPacketReader(Handler(handlerThread.looper),
                testNetwork1.iface.fileDescriptor.fileDescriptor, 1500 /* maxPacketSize */)
        packetReader.startAsyncForTest()
        handlerThread.waitForIdle(TIMEOUT_MS)

        // Register service on testNetwork1
        val registrationRecord = NsdRegistrationRecord()
        nsdManager.registerService(si, NsdManager.PROTOCOL_DNS_SD, { it.run() },
                registrationRecord)

        tryTest {
            assertNotNull(packetReader.pollForProbe(serviceName, serviceType),
                    "Did not find a probe for the service")
            packetReader.sendResponse(buildConflictingAnnouncementForCustomHost())

            // Registration must use an updated hostname to avoid the conflict
            val cb = registrationRecord.expectCallback<ServiceRegistered>(REGISTRATION_TIMEOUT_MS)
            // Service name is not renamed because there's no conflict on the service name.
            assertEquals(serviceName, cb.serviceInfo.serviceName)
            val hostname = cb.serviceInfo.hostname ?: fail("Missing hostname")
            hostname.let {
                assertTrue("Unexpected registered hostname: $it",
                        it.startsWith(customHostname) && it != customHostname)
            }
        } cleanupStep {
            nsdManager.unregisterService(registrationRecord)
            registrationRecord.expectCallback<ServiceUnregistered>()
        } cleanup {
            packetReader.handler.post { packetReader.stop() }
            handlerThread.waitForIdle(TIMEOUT_MS)
        }
    }

    @Test
    fun testRegisterServiceWithCustomHostNoAddresses_noConflictDuringProbing_notRenamed() {
        val si = makeTestServiceInfo(testNetwork1.network).apply {
            hostname = customHostname
        }

        val packetReader = TapPacketReader(Handler(handlerThread.looper),
                testNetwork1.iface.fileDescriptor.fileDescriptor, 1500 /* maxPacketSize */)
        packetReader.startAsyncForTest()
        handlerThread.waitForIdle(TIMEOUT_MS)

        // Register service on testNetwork1
        val registrationRecord = NsdRegistrationRecord()
        nsdManager.registerService(si, NsdManager.PROTOCOL_DNS_SD, { it.run() },
                registrationRecord)

        tryTest {
            assertNotNull(packetReader.pollForProbe(serviceName, serviceType),
                    "Did not find a probe for the service")
            // Not a conflict because no record is registered for the hostname
            packetReader.sendResponse(buildConflictingAnnouncementForCustomHost())

            // Registration is not renamed because there's no conflict
            val cb = registrationRecord.expectCallback<ServiceRegistered>(REGISTRATION_TIMEOUT_MS)
            assertEquals(serviceName, cb.serviceInfo.serviceName)
            assertEquals(customHostname, cb.serviceInfo.hostname)
        } cleanupStep {
            nsdManager.unregisterService(registrationRecord)
            registrationRecord.expectCallback<ServiceUnregistered>()
        } cleanup {
            packetReader.handler.post { packetReader.stop() }
            handlerThread.waitForIdle(TIMEOUT_MS)
        }
    }

    @Test
    fun testRegisterWithConflictAfterProbing() {
        // This test requires shims supporting T+ APIs (NsdServiceInfo.network)
        assumeTrue(TestUtils.shouldTestTApis())

        val si = makeTestServiceInfo(testNetwork1.network)

        // Register service on testNetwork1
        val registrationRecord = NsdRegistrationRecord()
        val discoveryRecord = NsdDiscoveryRecord()
        val registeredService = registerService(registrationRecord, si)
        val packetReader = TapPacketReader(Handler(handlerThread.looper),
                testNetwork1.iface.fileDescriptor.fileDescriptor, 1500 /* maxPacketSize */)
        packetReader.startAsyncForTest()
        handlerThread.waitForIdle(TIMEOUT_MS)

        tryTest {
            assertNotNull(packetReader.pollForAdvertisement(serviceName, serviceType),
                    "No announcements sent after initial probing")

            assertEquals(si.serviceName, registeredService.serviceName)

            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD,
                testNetwork1.network, { it.run() }, discoveryRecord)
            discoveryRecord.waitForServiceDiscovered(si.serviceName, serviceType)

            // Send a conflicting announcement
            val conflictingAnnouncement = buildConflictingAnnouncement()
            packetReader.sendResponse(conflictingAnnouncement)

            // Expect to see probes (RFC6762 9., service is reset to probing state)
            assertNotNull(packetReader.pollForProbe(serviceName, serviceType),
                    "Probe not received within timeout after conflict")

            // Send the conflicting packet again to reply to the probe
            packetReader.sendResponse(conflictingAnnouncement)

            // Note the legacy mdnsresponder would send an exit announcement here (a 0-lifetime
            // advertisement just for the PTR record), but not the new advertiser. This probably
            // follows RFC 6762 8.4, saying that when a record rdata changed, "In the case of shared
            // records, a host MUST send a "goodbye" announcement with RR TTL zero [...] for the old
            // rdata, to cause it to be deleted from peer caches, before announcing the new rdata".
            //
            // This should be implemented by the new advertiser, but in the case of conflicts it is
            // not very valuable since an identical PTR record would be used by the conflicting
            // service (except for subtypes). In that case the exit announcement may be
            // counter-productive as it conflicts with announcements done by the conflicting
            // service.

            // Note that before sending the following ServiceRegistered callback for the renamed
            // service, the legacy mdnsresponder-based implementation would first send a
            // Service*Registered* callback for the original service name being *unregistered*; it
            // should have been a ServiceUnregistered callback instead (bug in NsdService
            // interpretation of the callback).
            val newRegistration = registrationRecord.expectCallbackEventually<ServiceRegistered>(
                    REGISTRATION_TIMEOUT_MS) {
                it.serviceInfo.serviceName.startsWith(serviceName) &&
                        it.serviceInfo.serviceName != serviceName
            }

            discoveryRecord.expectCallbackEventually<ServiceFound> {
                it.serviceInfo.serviceName == newRegistration.serviceInfo.serviceName
            }
        } cleanupStep {
            nsdManager.stopServiceDiscovery(discoveryRecord)
            discoveryRecord.expectCallback<DiscoveryStopped>()
        } cleanupStep {
            nsdManager.unregisterService(registrationRecord)
            registrationRecord.expectCallback<ServiceUnregistered>()
        } cleanup {
            packetReader.handler.post { packetReader.stop() }
            handlerThread.waitForIdle(TIMEOUT_MS)
        }
    }

    @Test
    fun testRegisterServiceWithCustomHostAndAddresses_conflictAfterProbing_hostRenamed() {
        val si = makeTestServiceInfo(testNetwork1.network).apply {
            hostname = customHostname
            hostAddresses = listOf(
                    parseNumericAddress("192.0.2.24"),
                    parseNumericAddress("2001:db8::3"))
        }

        // Register service on testNetwork1
        val registrationRecord = NsdRegistrationRecord()
        val discoveryRecord = NsdDiscoveryRecord()
        val registeredService = registerService(registrationRecord, si)
        val packetReader = TapPacketReader(
                Handler(handlerThread.looper),
                testNetwork1.iface.fileDescriptor.fileDescriptor, 1500 /* maxPacketSize */)
        packetReader.startAsyncForTest()
        handlerThread.waitForIdle(TIMEOUT_MS)

        tryTest {
            repeat(3) {
                assertNotNull(packetReader.pollForAdvertisement(serviceName, serviceType),
                        "Expect 3 announcements sent after initial probing")
            }

            assertEquals(si.serviceName, registeredService.serviceName)
            assertEquals(si.hostname, registeredService.hostname)

            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD,
                    testNetwork1.network, { it.run() }, discoveryRecord)
            val discoveredInfo = discoveryRecord.waitForServiceDiscovered(
                    si.serviceName, serviceType)

            // Send a conflicting announcement
            val conflictingAnnouncement = buildConflictingAnnouncementForCustomHost()
            packetReader.sendResponse(conflictingAnnouncement)

            // Expect to see probes (RFC6762 9., service is reset to probing state)
            assertNotNull(packetReader.pollForProbe(serviceName, serviceType),
                    "Probe not received within timeout after conflict")

            // Send the conflicting packet again to reply to the probe
            packetReader.sendResponse(conflictingAnnouncement)

            val newRegistration =
                    registrationRecord
                            .expectCallbackEventually<ServiceRegistered>(REGISTRATION_TIMEOUT_MS) {
                                it.serviceInfo.serviceName == serviceName
                                        && it.serviceInfo.hostname.let { hostname ->
                                    hostname != null
                                            && hostname.startsWith(customHostname)
                                            && hostname != customHostname
                                }
                            }

            val resolvedInfo = resolveService(discoveredInfo)
            assertEquals(newRegistration.serviceInfo.serviceName, resolvedInfo.serviceName)
            assertEquals(newRegistration.serviceInfo.hostname, resolvedInfo.hostname)

            discoveryRecord.assertNoCallback()
        } cleanupStep {
            nsdManager.stopServiceDiscovery(discoveryRecord)
            discoveryRecord.expectCallback<DiscoveryStopped>()
        } cleanupStep {
            nsdManager.unregisterService(registrationRecord)
            registrationRecord.expectCallback<ServiceUnregistered>()
        } cleanup {
            packetReader.handler.post { packetReader.stop() }
            handlerThread.waitForIdle(TIMEOUT_MS)
        }
    }

    @Test
    fun testRegisterServiceWithCustomHostNoAddresses_noConflictAfterProbing_notRenamed() {
        val si = makeTestServiceInfo(testNetwork1.network).apply {
            hostname = customHostname
        }

        // Register service on testNetwork1
        val registrationRecord = NsdRegistrationRecord()
        val discoveryRecord = NsdDiscoveryRecord()
        val registeredService = registerService(registrationRecord, si)
        val packetReader = TapPacketReader(Handler(handlerThread.looper),
                testNetwork1.iface.fileDescriptor.fileDescriptor, 1500 /* maxPacketSize */)
        packetReader.startAsyncForTest()
        handlerThread.waitForIdle(TIMEOUT_MS)

        tryTest {
            assertNotNull(packetReader.pollForAdvertisement(serviceName, serviceType),
                    "No announcements sent after initial probing")

            assertEquals(si.serviceName, registeredService.serviceName)
            assertEquals(si.hostname, registeredService.hostname)

            // Send a conflicting announcement
            val conflictingAnnouncement = buildConflictingAnnouncementForCustomHost()
            packetReader.sendResponse(conflictingAnnouncement)

            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD,
                    testNetwork1.network, { it.run() }, discoveryRecord)

            // The service is not renamed
            discoveryRecord.waitForServiceDiscovered(si.serviceName, serviceType)
        } cleanupStep {
            nsdManager.stopServiceDiscovery(discoveryRecord)
            discoveryRecord.expectCallback<DiscoveryStopped>()
        } cleanupStep {
            nsdManager.unregisterService(registrationRecord)
            registrationRecord.expectCallback<ServiceUnregistered>()
        } cleanup {
            packetReader.handler.post { packetReader.stop() }
            handlerThread.waitForIdle(TIMEOUT_MS)
        }
    }

    // Test that even if only a PTR record is received as a reply when discovering, without the
    // SRV, TXT, address records as recommended (but not mandated) by RFC 6763 12, the service can
    // still be discovered.
    @Test
    fun testDiscoveryWithPtrOnlyResponse_ServiceIsFound() {
        // Register service on testNetwork1
        val discoveryRecord = NsdDiscoveryRecord()
        val packetReader = TapPacketReader(Handler(handlerThread.looper),
                testNetwork1.iface.fileDescriptor.fileDescriptor, 1500 /* maxPacketSize */)
        packetReader.startAsyncForTest()
        handlerThread.waitForIdle(TIMEOUT_MS)

        nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD,
                testNetwork1.network, { it.run() }, discoveryRecord)

        tryTest {
            discoveryRecord.expectCallback<DiscoveryStarted>()
            assertNotNull(packetReader.pollForQuery("$serviceType.local", DnsResolver.TYPE_PTR))
            /*
            Generated with:
            scapy.raw(scapy.DNS(rd=0, qr=1, aa=1, qd = None, an =
                scapy.DNSRR(rrname='_nmt123456789._tcp.local', type='PTR', ttl=120,
                rdata='NsdTest123456789._nmt123456789._tcp.local'))).hex()
             */
            val ptrResponsePayload = HexDump.hexStringToByteArray("0000840000000001000000000d5f6e" +
                    "6d74313233343536373839045f746370056c6f63616c00000c000100000078002b104e736454" +
                    "6573743132333435363738390d5f6e6d74313233343536373839045f746370056c6f63616c00")

            replaceServiceNameAndTypeWithTestSuffix(ptrResponsePayload)
            packetReader.sendResponse(buildMdnsPacket(ptrResponsePayload))

            val serviceFound = discoveryRecord.expectCallback<ServiceFound>()
            serviceFound.serviceInfo.let {
                assertEquals(serviceName, it.serviceName)
                // Discovered service types have a dot at the end
                assertEquals("$serviceType.", it.serviceType)
                assertEquals(testNetwork1.network, it.network)
                // ServiceFound does not provide port, address or attributes (only information
                // available in the PTR record is included in that callback, regardless of whether
                // other records exist).
                assertEquals(0, it.port)
                assertEmpty(it.hostAddresses)
                assertEquals(0, it.attributes.size)
            }
        } cleanup {
            nsdManager.stopServiceDiscovery(discoveryRecord)
            discoveryRecord.expectCallback<DiscoveryStopped>()
        }
    }

    // Test RFC 6763 12. "Clients MUST be capable of functioning correctly with DNS servers [...]
    // that fail to generate these additional records automatically, by issuing subsequent queries
    // for any further record(s) they require"
    @Test
    fun testResolveWhenServerSendsNoAdditionalRecord() {
        // Resolve service on testNetwork1
        val resolveRecord = NsdResolveRecord()
        val packetReader = TapPacketReader(Handler(handlerThread.looper),
                testNetwork1.iface.fileDescriptor.fileDescriptor, 1500 /* maxPacketSize */
        )
        packetReader.startAsyncForTest()
        handlerThread.waitForIdle(TIMEOUT_MS)

        val si = makeTestServiceInfo(testNetwork1.network)
        nsdManager.resolveService(si, { it.run() }, resolveRecord)

        val serviceFullName = "$serviceName.$serviceType.local"
        // The query should ask for ANY, since both SRV and TXT are requested. Note legacy
        // mdnsresponder will ask for SRV and TXT separately, and will not proceed to asking for
        // address records without an answer for both.
        val srvTxtQuery = packetReader.pollForQuery(serviceFullName, DnsResolver.TYPE_ANY)
        assertNotNull(srvTxtQuery)

        /*
        Generated with:
        scapy.raw(scapy.dns_compress(scapy.DNS(rd=0, qr=1, aa=1, qd = None, an =
            scapy.DNSRRSRV(rrname='NsdTest123456789._nmt123456789._tcp.local',
                rclass=0x8001, port=31234, target='testhost.local', ttl=120) /
            scapy.DNSRR(rrname='NsdTest123456789._nmt123456789._tcp.local', type='TXT', ttl=120,
                rdata='testkey=testvalue')
        ))).hex()
         */
        val srvTxtResponsePayload = HexDump.hexStringToByteArray("000084000000000200000000104" +
                "e7364546573743132333435363738390d5f6e6d74313233343536373839045f746370056c6f6" +
                "3616c0000218001000000780011000000007a020874657374686f7374c030c00c00100001000" +
                "00078001211746573746b65793d7465737476616c7565")
        replaceServiceNameAndTypeWithTestSuffix(srvTxtResponsePayload)
        packetReader.sendResponse(buildMdnsPacket(srvTxtResponsePayload))

        val testHostname = "testhost.local"
        val addressQuery = packetReader.pollForQuery(testHostname,
            DnsResolver.TYPE_A, DnsResolver.TYPE_AAAA)
        assertNotNull(addressQuery)

        /*
        Generated with:
        scapy.raw(scapy.dns_compress(scapy.DNS(rd=0, qr=1, aa=1, qd = None, an =
            scapy.DNSRR(rrname='testhost.local', type='A', ttl=120,
                rdata='192.0.2.123') /
            scapy.DNSRR(rrname='testhost.local', type='AAAA', ttl=120,
                rdata='2001:db8::123')
        ))).hex()
         */
        val addressPayload = HexDump.hexStringToByteArray("0000840000000002000000000874657374" +
                "686f7374056c6f63616c0000010001000000780004c000027bc00c001c000100000078001020" +
                "010db8000000000000000000000123")
        packetReader.sendResponse(buildMdnsPacket(addressPayload))

        val serviceResolved = resolveRecord.expectCallback<ServiceResolved>()
        serviceResolved.serviceInfo.let {
            assertEquals(serviceName, it.serviceName)
            assertEquals(".$serviceType", it.serviceType)
            assertEquals(testNetwork1.network, it.network)
            assertEquals(31234, it.port)
            assertEquals(1, it.attributes.size)
            assertArrayEquals("testvalue".encodeToByteArray(), it.attributes["testkey"])
        }
        assertEquals(
                setOf(parseNumericAddress("192.0.2.123"), parseNumericAddress("2001:db8::123")),
                serviceResolved.serviceInfo.hostAddresses.toSet())
    }

    @Test
    fun testUnicastReplyUsedWhenQueryUnicastFlagSet() {
        // The flag may be removed in the future but unicast replies should be enabled by default
        // in that case. The rule will reset flags automatically on teardown.
        deviceConfigRule.setConfig(NAMESPACE_TETHERING, "test_nsd_unicast_reply_enabled", "1")

        val si = makeTestServiceInfo(testNetwork1.network)

        // Register service on testNetwork1
        val registrationRecord = NsdRegistrationRecord()
        var nsResponder: NSResponder? = null
        tryTest {
            registerService(registrationRecord, si)
            val packetReader = TapPacketReader(Handler(handlerThread.looper),
                testNetwork1.iface.fileDescriptor.fileDescriptor, 1500 /* maxPacketSize */)
            packetReader.startAsyncForTest()

            handlerThread.waitForIdle(TIMEOUT_MS)
            /*
            Send a "query unicast" query.
            Generated with:
            scapy.raw(scapy.DNS(rd=0, qr=0, aa=0, qd =
                    scapy.DNSQR(qname='_nmt123456789._tcp.local', qtype='PTR', qclass=0x8001)
            )).hex()
            */
            val mdnsPayload = HexDump.hexStringToByteArray("0000000000010000000000000d5f6e6d74313" +
                    "233343536373839045f746370056c6f63616c00000c8001")
            replaceServiceNameAndTypeWithTestSuffix(mdnsPayload)

            val testSrcAddr = makeLinkLocalAddressOfOtherDeviceOnPrefix(testNetwork1.network)
            nsResponder = NSResponder(packetReader, mapOf(
                testSrcAddr to MacAddress.fromString("01:02:03:04:05:06")
            )).apply { start() }

            packetReader.sendResponse(buildMdnsPacket(mdnsPayload, testSrcAddr))
            // The reply is sent unicast to the source address. There may be announcements sent
            // multicast around this time, so filter by destination address.
            val reply = packetReader.pollForMdnsPacket { pkt ->
                pkt.isReplyFor("$serviceType.local", DnsResolver.TYPE_PTR) &&
                        pkt.dstAddr == testSrcAddr
            }
            assertNotNull(reply)
        } cleanup {
            nsResponder?.stop()
            nsdManager.unregisterService(registrationRecord)
            registrationRecord.expectCallback<ServiceUnregistered>()
        }
    }

    @Test
    fun testReplyWhenKnownAnswerSuppressionFlagSet() {
        // The flag may be removed in the future but known-answer suppression should be enabled by
        // default in that case. The rule will reset flags automatically on teardown.
        deviceConfigRule.setConfig(NAMESPACE_TETHERING, "test_nsd_known_answer_suppression", "1")
        deviceConfigRule.setConfig(NAMESPACE_TETHERING, "test_nsd_unicast_reply_enabled", "1")

        val si = makeTestServiceInfo(testNetwork1.network)

        // Register service on testNetwork1
        val registrationRecord = NsdRegistrationRecord()
        var nsResponder: NSResponder? = null
        tryTest {
            registerService(registrationRecord, si)
            val packetReader = TapPacketReader(Handler(handlerThread.looper),
                    testNetwork1.iface.fileDescriptor.fileDescriptor, 1500 /* maxPacketSize */)
            packetReader.startAsyncForTest()

            handlerThread.waitForIdle(TIMEOUT_MS)
            /*
            Send a query with a known answer. Expect to receive a response containing TXT record
            only.
            Generated with:
            scapy.raw(scapy.DNS(rd=0, qr=0, aa=0, qd =
                    scapy.DNSQR(qname='_nmt123456789._tcp.local', qtype='PTR',
                            qclass=0x8001) /
                    scapy.DNSQR(qname='NsdTest123456789._nmt123456789._tcp.local', qtype='TXT',
                            qclass=0x8001),
                    an = scapy.DNSRR(rrname='_nmt123456789._tcp.local', type='PTR', ttl=4500,
                            rdata='NsdTest123456789._nmt123456789._tcp.local')
            )).hex()
            */
            val query = HexDump.hexStringToByteArray("0000000000020001000000000d5f6e6d74313233343" +
                    "536373839045f746370056c6f63616c00000c8001104e7364546573743132333435363738390" +
                    "d5f6e6d74313233343536373839045f746370056c6f63616c00001080010d5f6e6d743132333" +
                    "43536373839045f746370056c6f63616c00000c000100001194002b104e73645465737431323" +
                    "33435363738390d5f6e6d74313233343536373839045f746370056c6f63616c00")
            replaceServiceNameAndTypeWithTestSuffix(query)

            val testSrcAddr = makeLinkLocalAddressOfOtherDeviceOnPrefix(testNetwork1.network)
            nsResponder = NSResponder(packetReader, mapOf(
                    testSrcAddr to MacAddress.fromString("01:02:03:04:05:06")
            )).apply { start() }

            packetReader.sendResponse(buildMdnsPacket(query, testSrcAddr))
            // The reply is sent unicast to the source address. There may be announcements sent
            // multicast around this time, so filter by destination address.
            val reply = packetReader.pollForMdnsPacket { pkt ->
                pkt.isReplyFor("$serviceName.$serviceType.local", DnsResolver.TYPE_TXT) &&
                        !pkt.isReplyFor("$serviceType.local", DnsResolver.TYPE_PTR) &&
                        pkt.dstAddr == testSrcAddr
            }
            assertNotNull(reply)

            /*
            Send a query with a known answer (TTL is less than half). Expect to receive a response
            containing both PTR and TXT records.
            Generated with:
            scapy.raw(scapy.DNS(rd=0, qr=0, aa=0, qd =
                    scapy.DNSQR(qname='_nmt123456789._tcp.local', qtype='PTR',
                            qclass=0x8001) /
                    scapy.DNSQR(qname='NsdTest123456789._nmt123456789._tcp.local', qtype='TXT',
                            qclass=0x8001),
                    an = scapy.DNSRR(rrname='_nmt123456789._tcp.local', type='PTR', ttl=2150,
                            rdata='NsdTest123456789._nmt123456789._tcp.local')
            )).hex()
            */
            val query2 = HexDump.hexStringToByteArray("0000000000020001000000000d5f6e6d7431323334" +
                    "3536373839045f746370056c6f63616c00000c8001104e736454657374313233343536373839" +
                    "0d5f6e6d74313233343536373839045f746370056c6f63616c00001080010d5f6e6d74313233" +
                    "343536373839045f746370056c6f63616c00000c000100000866002b104e7364546573743132" +
                    "333435363738390d5f6e6d74313233343536373839045f746370056c6f63616c00")
            replaceServiceNameAndTypeWithTestSuffix(query2)

            packetReader.sendResponse(buildMdnsPacket(query2, testSrcAddr))
            // The reply is sent unicast to the source address. There may be announcements sent
            // multicast around this time, so filter by destination address.
            val reply2 = packetReader.pollForMdnsPacket { pkt ->
                pkt.isReplyFor("$serviceName.$serviceType.local", DnsResolver.TYPE_TXT) &&
                        pkt.isReplyFor("$serviceType.local", DnsResolver.TYPE_PTR) &&
                        pkt.dstAddr == testSrcAddr
            }
            assertNotNull(reply2)
        } cleanup {
            nsResponder?.stop()
            nsdManager.unregisterService(registrationRecord)
            registrationRecord.expectCallback<ServiceUnregistered>()
        }
    }

    @Test
    fun testReplyWithMultipacketWhenKnownAnswerSuppressionFlagSet() {
        // The flag may be removed in the future but known-answer suppression should be enabled by
        // default in that case. The rule will reset flags automatically on teardown.
        deviceConfigRule.setConfig(NAMESPACE_TETHERING, "test_nsd_known_answer_suppression", "1")
        deviceConfigRule.setConfig(NAMESPACE_TETHERING, "test_nsd_unicast_reply_enabled", "1")

        val si = makeTestServiceInfo(testNetwork1.network)

        // Register service on testNetwork1
        val registrationRecord = NsdRegistrationRecord()
        var nsResponder: NSResponder? = null
        tryTest {
            registerService(registrationRecord, si)
            val packetReader = TapPacketReader(Handler(handlerThread.looper),
                    testNetwork1.iface.fileDescriptor.fileDescriptor, 1500 /* maxPacketSize */)
            packetReader.startAsyncForTest()

            handlerThread.waitForIdle(TIMEOUT_MS)
            /*
            Send a query with truncated bit set.
            Generated with:
            scapy.raw(scapy.DNS(rd=0, qr=0, aa=0, tc=1, qd=
                    scapy.DNSQR(qname='_nmt123456789._tcp.local', qtype='PTR',
                            qclass=0x8001) /
                    scapy.DNSQR(qname='NsdTest123456789._nmt123456789._tcp.local', qtype='TXT',
                            qclass=0x8001)
            )).hex()
            */
            val query = HexDump.hexStringToByteArray("0000020000020000000000000d5f6e6d74313233343" +
                    "536373839045f746370056c6f63616c00000c8001104e7364546573743132333435363738390" +
                    "d5f6e6d74313233343536373839045f746370056c6f63616c0000108001")
            replaceServiceNameAndTypeWithTestSuffix(query)
            /*
            Send a known answer packet (other service) with truncated bit set.
            Generated with:
            scapy.raw(scapy.DNS(rd=0, qr=0, aa=0, tc=1, qd=None,
                    an = scapy.DNSRR(rrname='_test._tcp.local', type='PTR', ttl=4500,
                            rdata='NsdTest._test._tcp.local')
            )).hex()
            */
            val knownAnswer1 = HexDump.hexStringToByteArray("000002000000000100000000055f74657374" +
                    "045f746370056c6f63616c00000c000100001194001a074e736454657374055f74657374045f" +
                    "746370056c6f63616c00")
            replaceServiceNameAndTypeWithTestSuffix(knownAnswer1)
            /*
            Send a known answer packet.
            Generated with:
            scapy.raw(scapy.DNS(rd=0, qr=0, aa=0, qd=None,
                    an = scapy.DNSRR(rrname='_nmt123456789._tcp.local', type='PTR', ttl=4500,
                            rdata='NsdTest123456789._nmt123456789._tcp.local')
            )).hex()
            */
            val knownAnswer2 = HexDump.hexStringToByteArray("0000000000000001000000000d5f6e6d7431" +
                    "3233343536373839045f746370056c6f63616c00000c000100001194002b104e736454657374" +
                    "3132333435363738390d5f6e6d74313233343536373839045f746370056c6f63616c00")
            replaceServiceNameAndTypeWithTestSuffix(knownAnswer2)

            val testSrcAddr = makeLinkLocalAddressOfOtherDeviceOnPrefix(testNetwork1.network)
            nsResponder = NSResponder(packetReader, mapOf(
                    testSrcAddr to MacAddress.fromString("01:02:03:04:05:06")
            )).apply { start() }

            packetReader.sendResponse(buildMdnsPacket(query, testSrcAddr))
            packetReader.sendResponse(buildMdnsPacket(knownAnswer1, testSrcAddr))
            packetReader.sendResponse(buildMdnsPacket(knownAnswer2, testSrcAddr))
            // The reply is sent unicast to the source address. There may be announcements sent
            // multicast around this time, so filter by destination address.
            val reply = packetReader.pollForMdnsPacket { pkt ->
                pkt.isReplyFor("$serviceName.$serviceType.local", DnsResolver.TYPE_TXT) &&
                        !pkt.isReplyFor("$serviceType.local", DnsResolver.TYPE_PTR) &&
                        pkt.dstAddr == testSrcAddr
            }
            assertNotNull(reply)
        } cleanup {
            nsResponder?.stop()
            nsdManager.unregisterService(registrationRecord)
            registrationRecord.expectCallback<ServiceUnregistered>()
        }
    }

    private fun makeLinkLocalAddressOfOtherDeviceOnPrefix(network: Network): Inet6Address {
        val lp = cm.getLinkProperties(network) ?: fail("No LinkProperties for net $network")
        // Expect to have a /64 link-local address
        val linkAddr = lp.linkAddresses.firstOrNull {
            it.isIPv6 && it.scope == RT_SCOPE_LINK && it.prefixLength == 64
        } ?: fail("No /64 link-local address found in ${lp.linkAddresses} for net $network")

        // Add one to the device address to simulate the address of another device on the prefix
        val addrBytes = linkAddr.address.address
        addrBytes[IPV6_ADDR_LEN - 1]++
        return Inet6Address.getByAddress(addrBytes) as Inet6Address
    }

    @Test
    fun testAdvertisingAndDiscovery_servicesWithCustomHost_customHostAddressesFound() {
        val hostAddresses1 = listOf(
                parseNumericAddress("192.0.2.23"),
                parseNumericAddress("2001:db8::1"),
                parseNumericAddress("2001:db8::2"))
        val hostAddresses2 = listOf(
                parseNumericAddress("192.0.2.24"),
                parseNumericAddress("2001:db8::3"))
        val si1 = NsdServiceInfo().also {
            it.network = testNetwork1.network
            it.serviceName = serviceName
            it.serviceType = serviceType
            it.port = TEST_PORT
            it.hostname = customHostname
            it.hostAddresses = hostAddresses1
        }
        val si2 = NsdServiceInfo().also {
            it.network = testNetwork1.network
            it.serviceName = serviceName2
            it.serviceType = serviceType
            it.port = TEST_PORT + 1
            it.hostname = customHostname2
            it.hostAddresses = hostAddresses2
        }
        val registrationRecord1 = NsdRegistrationRecord()
        val registrationRecord2 = NsdRegistrationRecord()

        val discoveryRecord1 = NsdDiscoveryRecord()
        val discoveryRecord2 = NsdDiscoveryRecord()
        tryTest {
            registerService(registrationRecord1, si1)

            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD,
                    testNetwork1.network, Executor { it.run() }, discoveryRecord1)

            val discoveredInfo = discoveryRecord1.waitForServiceDiscovered(
                    serviceName, serviceType, testNetwork1.network)
            val resolvedInfo = resolveService(discoveredInfo)

            assertEquals(TEST_PORT, resolvedInfo.port)
            assertEquals(si1.hostname, resolvedInfo.hostname)
            assertAddressEquals(hostAddresses1, resolvedInfo.hostAddresses)

            registerService(registrationRecord2, si2)
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD,
                    testNetwork1.network, Executor { it.run() }, discoveryRecord2)

            val discoveredInfo2 = discoveryRecord2.waitForServiceDiscovered(
                    serviceName2, serviceType, testNetwork1.network)
            val resolvedInfo2 = resolveService(discoveredInfo2)

            assertEquals(TEST_PORT + 1, resolvedInfo2.port)
            assertEquals(si2.hostname, resolvedInfo2.hostname)
            assertAddressEquals(hostAddresses2, resolvedInfo2.hostAddresses)
        } cleanupStep {
            nsdManager.stopServiceDiscovery(discoveryRecord1)
            nsdManager.stopServiceDiscovery(discoveryRecord2)

            discoveryRecord1.expectCallbackEventually<DiscoveryStopped>()
            discoveryRecord2.expectCallbackEventually<DiscoveryStopped>()
        } cleanup {
            nsdManager.unregisterService(registrationRecord1)
            nsdManager.unregisterService(registrationRecord2)
        }
    }

    @Test
    fun testAdvertisingAndDiscovery_multipleRegistrationsForSameCustomHost_unionOfAddressesFound() {
        val hostAddresses1 = listOf(
                parseNumericAddress("192.0.2.23"),
                parseNumericAddress("2001:db8::1"),
                parseNumericAddress("2001:db8::2"))
        val hostAddresses2 = listOf(
                parseNumericAddress("192.0.2.24"),
                parseNumericAddress("2001:db8::3"))
        val hostAddresses3 = listOf(
                parseNumericAddress("2001:db8::3"),
                parseNumericAddress("2001:db8::5"))
        val si1 = NsdServiceInfo().also {
            it.network = testNetwork1.network
            it.hostname = customHostname
            it.hostAddresses = hostAddresses1
        }
        val si2 = NsdServiceInfo().also {
            it.network = testNetwork1.network
            it.serviceName = serviceName
            it.serviceType = serviceType
            it.port = TEST_PORT
            it.hostname = customHostname
            it.hostAddresses = hostAddresses2
        }
        val si3 = NsdServiceInfo().also {
            it.network = testNetwork1.network
            it.serviceName = serviceName3
            it.serviceType = serviceType
            it.port = TEST_PORT + 1
            it.hostname = customHostname
            it.hostAddresses = hostAddresses3
        }

        val registrationRecord1 = NsdRegistrationRecord()
        val registrationRecord2 = NsdRegistrationRecord()
        val registrationRecord3 = NsdRegistrationRecord()

        val discoveryRecord = NsdDiscoveryRecord()
        tryTest {
            registerService(registrationRecord1, si1)
            registerService(registrationRecord2, si2)

            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD,
                    testNetwork1.network, Executor { it.run() }, discoveryRecord)

            val discoveredInfo1 = discoveryRecord.waitForServiceDiscovered(
                    serviceName, serviceType, testNetwork1.network)
            val resolvedInfo1 = resolveService(discoveredInfo1)

            assertEquals(TEST_PORT, resolvedInfo1.port)
            assertEquals(si1.hostname, resolvedInfo1.hostname)
            assertAddressEquals(
                    hostAddresses1 + hostAddresses2,
                    resolvedInfo1.hostAddresses)

            registerService(registrationRecord3, si3)

            val discoveredInfo2 = discoveryRecord.waitForServiceDiscovered(
                    serviceName3, serviceType, testNetwork1.network)
            val resolvedInfo2 = resolveService(discoveredInfo2)

            assertEquals(TEST_PORT + 1, resolvedInfo2.port)
            assertEquals(si2.hostname, resolvedInfo2.hostname)
            assertAddressEquals(
                    hostAddresses1 + hostAddresses2 + hostAddresses3,
                    resolvedInfo2.hostAddresses)
        } cleanupStep {
            nsdManager.stopServiceDiscovery(discoveryRecord)

            discoveryRecord.expectCallbackEventually<DiscoveryStopped>()
        } cleanup {
            nsdManager.unregisterService(registrationRecord1)
            nsdManager.unregisterService(registrationRecord2)
            nsdManager.unregisterService(registrationRecord3)
        }
    }

    @Test
    fun testAdvertisingAndDiscovery_servicesWithTheSameCustomHostAddressOmitted_addressesFound() {
        val hostAddresses = listOf(
                parseNumericAddress("192.0.2.23"),
                parseNumericAddress("2001:db8::1"),
                parseNumericAddress("2001:db8::2"))
        val si1 = NsdServiceInfo().also {
            it.network = testNetwork1.network
            it.serviceType = serviceType
            it.serviceName = serviceName
            it.port = TEST_PORT
            it.hostname = customHostname
            it.hostAddresses = hostAddresses
        }
        val si2 = NsdServiceInfo().also {
            it.network = testNetwork1.network
            it.serviceType = serviceType
            it.serviceName = serviceName2
            it.port = TEST_PORT + 1
            it.hostname = customHostname
        }

        val registrationRecord1 = NsdRegistrationRecord()
        val registrationRecord2 = NsdRegistrationRecord()

        val discoveryRecord = NsdDiscoveryRecord()
        tryTest {
            registerService(registrationRecord1, si1)

            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD,
                    testNetwork1.network, Executor { it.run() }, discoveryRecord)

            val discoveredInfo1 = discoveryRecord.waitForServiceDiscovered(
                    serviceName, serviceType, testNetwork1.network)
            val resolvedInfo1 = resolveService(discoveredInfo1)

            assertEquals(serviceName, discoveredInfo1.serviceName)
            assertEquals(TEST_PORT, resolvedInfo1.port)
            assertEquals(si1.hostname, resolvedInfo1.hostname)
            assertAddressEquals(hostAddresses, resolvedInfo1.hostAddresses)

            registerService(registrationRecord2, si2)

            val discoveredInfo2 = discoveryRecord.waitForServiceDiscovered(
                    serviceName2, serviceType, testNetwork1.network)
            val resolvedInfo2 = resolveService(discoveredInfo2)

            assertEquals(serviceName2, discoveredInfo2.serviceName)
            assertEquals(TEST_PORT + 1, resolvedInfo2.port)
            assertEquals(si2.hostname, resolvedInfo2.hostname)
            assertAddressEquals(hostAddresses, resolvedInfo2.hostAddresses)
        } cleanupStep {
            nsdManager.stopServiceDiscovery(discoveryRecord)

            discoveryRecord.expectCallback<DiscoveryStopped>()
        } cleanup {
            nsdManager.unregisterService(registrationRecord1)
            nsdManager.unregisterService(registrationRecord2)
        }
    }

    private fun buildConflictingAnnouncement(): ByteBuffer {
        /*
        Generated with:
        scapy.raw(scapy.DNS(rd=0, qr=1, aa=1, qd = None, an =
                scapy.DNSRRSRV(rrname='NsdTest123456789._nmt123456789._tcp.local',
                    rclass=0x8001, port=31234, target='conflict.local', ttl=120)
        )).hex()
         */
        val mdnsPayload = HexDump.hexStringToByteArray("000084000000000100000000104e736454657" +
                "3743132333435363738390d5f6e6d74313233343536373839045f746370056c6f63616c00002" +
                "18001000000780016000000007a0208636f6e666c696374056c6f63616c00")
        replaceServiceNameAndTypeWithTestSuffix(mdnsPayload)

        return buildMdnsPacket(mdnsPayload)
    }

    private fun buildConflictingAnnouncementForCustomHost(): ByteBuffer {
        /*
        Generated with scapy:
        raw(DNS(rd=0, qr=1, aa=1, qd = None, an =
            DNSRR(rrname='NsdTestHost123456789.local', type=28, rclass=1, ttl=120,
                    rdata='2001:db8::321')
        )).hex()
         */
        val mdnsPayload = HexDump.hexStringToByteArray("000084000000000100000000144e7364" +
                "54657374486f7374313233343536373839056c6f63616c00001c000100000078001020010db80000" +
                "00000000000000000321")
        replaceCustomHostnameWithTestSuffix(mdnsPayload)

        return buildMdnsPacket(mdnsPayload)
    }

    /**
     * Replaces occurrences of "NsdTest123456789" and "_nmt123456789" in mDNS payload with the
     * actual random name and type that are used by the test.
     */
    private fun replaceServiceNameAndTypeWithTestSuffix(mdnsPayload: ByteArray) {
        // Test service name and types have consistent length and are always ASCII
        val testPacketName = "NsdTest123456789".encodeToByteArray()
        val testPacketTypePrefix = "_nmt123456789".encodeToByteArray()
        val encodedServiceName = serviceName.encodeToByteArray()
        val encodedTypePrefix = serviceType.split('.')[0].encodeToByteArray()

        val packetBuffer = ByteBuffer.wrap(mdnsPayload)
        replaceAll(packetBuffer, testPacketName, encodedServiceName)
        replaceAll(packetBuffer, testPacketTypePrefix, encodedTypePrefix)
    }

    /**
     * Replaces occurrences of "NsdTestHost123456789" in mDNS payload with the
     * actual random host name that are used by the test.
     */
    private fun replaceCustomHostnameWithTestSuffix(mdnsPayload: ByteArray) {
        // Test custom hostnames have consistent length and are always ASCII
        val testPacketName = "NsdTestHost123456789".encodeToByteArray()
        val encodedHostname = customHostname.encodeToByteArray()

        val packetBuffer = ByteBuffer.wrap(mdnsPayload)
        replaceAll(packetBuffer, testPacketName, encodedHostname)
    }

    private tailrec fun replaceAll(buffer: ByteBuffer, source: ByteArray, replacement: ByteArray) {
        assertEquals(source.size, replacement.size)
        val index = buffer.array().indexOf(source)
        if (index < 0) return

        val origPosition = buffer.position()
        buffer.position(index)
        buffer.put(replacement)
        buffer.position(origPosition)
        replaceAll(buffer, source, replacement)
    }

    private fun buildMdnsPacket(
        mdnsPayload: ByteArray,
        srcAddr: Inet6Address = testSrcAddr
    ): ByteBuffer {
        val packetBuffer = PacketBuilder.allocate(true /* hasEther */, IPPROTO_IPV6,
                IPPROTO_UDP, mdnsPayload.size)
        val packetBuilder = PacketBuilder(packetBuffer)
        // Multicast ethernet address for IPv6 to ff02::fb
        val multicastEthAddr = MacAddress.fromBytes(
                byteArrayOf(0x33, 0x33, 0, 0, 0, 0xfb.toByte()))
        packetBuilder.writeL2Header(
                MacAddress.fromBytes(byteArrayOf(1, 2, 3, 4, 5, 6)) /* srcMac */,
                multicastEthAddr,
                ETH_P_IPV6.toShort())
        packetBuilder.writeIpv6Header(
                0x60000000, // version=6, traffic class=0x0, flowlabel=0x0
                IPPROTO_UDP.toByte(),
                64 /* hop limit */,
                srcAddr,
                multicastIpv6Addr /* dstIp */)
        packetBuilder.writeUdpHeader(MDNS_PORT /* srcPort */, MDNS_PORT /* dstPort */)
        packetBuffer.put(mdnsPayload)
        return packetBuilder.finalizePacket()
    }

    /**
     * Register a service and return its registration record.
     */
    private fun registerService(
        record: NsdRegistrationRecord,
        si: NsdServiceInfo,
        executor: Executor = Executor { it.run() }
    ): NsdServiceInfo {
        nsdManager.registerService(si, NsdManager.PROTOCOL_DNS_SD, executor, record)
        // We may not always get the name that we tried to register;
        // This events tells us the name that was registered.
        val cb = record.expectCallback<ServiceRegistered>(REGISTRATION_TIMEOUT_MS)
        return cb.serviceInfo
    }

    /**
     * Update a service.
     */
    private fun updateService(
            record: NsdRegistrationRecord,
            si: NsdServiceInfo,
            executor: Executor = Executor { it.run() }
    ) {
        nsdManager.registerService(si, NsdManager.PROTOCOL_DNS_SD, executor, record)
        // TODO: add the callback check for the update.
    }

    private fun resolveService(discoveredInfo: NsdServiceInfo): NsdServiceInfo {
        val record = NsdResolveRecord()
        nsdManager.resolveService(discoveredInfo, Executor { it.run() }, record)
        val resolvedCb = record.expectCallback<ServiceResolved>()
        assertEquals(discoveredInfo.serviceName, resolvedCb.serviceInfo.serviceName)

        return resolvedCb.serviceInfo
    }
}

private fun ByteArray.indexOf(sub: ByteArray): Int {
    var subIndex = 0
    forEachIndexed { i, b ->
        when (b) {
            // Still matching: continue comparing with next byte
            sub[subIndex] -> {
                subIndex++
                if (subIndex == sub.size) {
                    return i - sub.size + 1
                }
            }
            // Not matching next byte but matches first byte: continue comparing with 2nd byte
            sub[0] -> subIndex = 1
            // No matches: continue comparing from first byte
            else -> subIndex = 0
        }
    }
    return -1
}

private fun ByteArray?.utf8ToString(): String {
    if (this == null) return ""
    return String(this, StandardCharsets.UTF_8)
}

private fun assertAddressEquals(expected: List<InetAddress>, actual: List<InetAddress>) {
    // No duplicate addresses in the actual address list
    assertEquals(actual.toSet().size, actual.size)
    assertEquals(expected.toSet(), actual.toSet())
}