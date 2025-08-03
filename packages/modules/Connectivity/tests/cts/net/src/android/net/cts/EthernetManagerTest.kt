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
package android.net.cts

import android.Manifest.permission.CONNECTIVITY_USE_RESTRICTED_NETWORKS
import android.Manifest.permission.MANAGE_TEST_NETWORKS
import android.Manifest.permission.NETWORK_SETTINGS
import android.content.Context
import android.net.ConnectivityManager
import android.net.EthernetManager
import android.net.EthernetManager.ETHERNET_STATE_DISABLED
import android.net.EthernetManager.ETHERNET_STATE_ENABLED
import android.net.EthernetManager.InterfaceStateListener
import android.net.EthernetManager.ROLE_CLIENT
import android.net.EthernetManager.ROLE_NONE
import android.net.EthernetManager.ROLE_SERVER
import android.net.EthernetManager.STATE_ABSENT
import android.net.EthernetManager.STATE_LINK_DOWN
import android.net.EthernetManager.STATE_LINK_UP
import android.net.EthernetManager.TetheredInterfaceCallback
import android.net.EthernetManager.TetheredInterfaceRequest
import android.net.EthernetNetworkManagementException
import android.net.EthernetNetworkSpecifier
import android.net.EthernetNetworkUpdateRequest
import android.net.InetAddresses
import android.net.IpConfiguration
import android.net.LinkAddress
import android.net.MacAddress
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.NET_CAPABILITY_NOT_CONGESTED
import android.net.NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED
import android.net.NetworkCapabilities.NET_CAPABILITY_TEMPORARILY_NOT_METERED
import android.net.NetworkCapabilities.NET_CAPABILITY_TRUSTED
import android.net.NetworkCapabilities.TRANSPORT_ETHERNET
import android.net.NetworkCapabilities.TRANSPORT_TEST
import android.net.NetworkRequest
import android.net.StaticIpConfiguration
import android.net.TestNetworkInterface
import android.net.TestNetworkManager
import android.net.cts.EthernetManagerTest.EthernetStateListener.CallbackEntry.EthernetStateChanged
import android.net.cts.EthernetManagerTest.EthernetStateListener.CallbackEntry.InterfaceStateChanged
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.OutcomeReceiver
import android.os.Process
import android.os.SystemProperties
import android.platform.test.annotations.AppModeFull
import androidx.test.platform.app.InstrumentationRegistry
import com.android.net.module.util.ArrayTrackRecord
import com.android.net.module.util.TrackRecord
import com.android.testutils.ConnectivityModuleTest
import com.android.testutils.DevSdkIgnoreRule
import com.android.testutils.DevSdkIgnoreRunner
import com.android.testutils.DeviceInfoUtils.isKernelVersionAtLeast
import com.android.testutils.RecorderCallback.CallbackEntry.Available
import com.android.testutils.RecorderCallback.CallbackEntry.CapabilitiesChanged
import com.android.testutils.RecorderCallback.CallbackEntry.LinkPropertiesChanged
import com.android.testutils.RecorderCallback.CallbackEntry.Lost
import com.android.testutils.RouterAdvertisementResponder
import com.android.testutils.TapPacketReader
import com.android.testutils.TestableNetworkCallback
import com.android.testutils.assertThrows
import com.android.testutils.runAsShell
import com.android.testutils.waitForIdle
import java.io.IOException
import java.net.Inet6Address
import java.net.Socket
import java.util.Random
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.function.IntConsumer
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private const val TAG = "EthernetManagerTest"
// This timeout does not affect the test duration for passing tests. It needs to be long enough to
// account for RS delay (and potentially the first retry interval (4s)). There have been failures
// where the interface did not gain provisioning within the allotted timeout.
private const val TIMEOUT_MS = 10_000L
// Timeout used to confirm no callbacks matching given criteria are received. Must be long enough to
// process all callbacks including ip provisioning when using the updateConfiguration API.
// Note that increasing this timeout increases the test duration.
private const val NO_CALLBACK_TIMEOUT_MS = 500L

private val DEFAULT_IP_CONFIGURATION = IpConfiguration(IpConfiguration.IpAssignment.DHCP,
        IpConfiguration.ProxySettings.NONE, null, null)
private val ETH_REQUEST: NetworkRequest = NetworkRequest.Builder()
        .addTransportType(TRANSPORT_TEST)
        .addTransportType(TRANSPORT_ETHERNET)
        .removeCapability(NET_CAPABILITY_TRUSTED)
        .build()
private val TEST_CAPS = NetworkCapabilities.Builder(ETH_REQUEST.networkCapabilities)
        .addCapability(NET_CAPABILITY_TEMPORARILY_NOT_METERED)
        .addCapability(NET_CAPABILITY_NOT_CONGESTED)
        .build()
private val STATIC_IP_CONFIGURATION = IpConfiguration.Builder()
        .setStaticIpConfiguration(StaticIpConfiguration.Builder()
                .setIpAddress(LinkAddress("192.0.2.1/30")).build())
        .build()

@AppModeFull(reason = "Instant apps can't access EthernetManager")
// EthernetManager is not updatable before T, so tests do not need to be backwards compatible.
@RunWith(DevSdkIgnoreRunner::class)
// This test depends on behavior introduced post-T as part of connectivity module updates
@ConnectivityModuleTest
@DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.S_V2)
class EthernetManagerTest {

    private val context by lazy { InstrumentationRegistry.getInstrumentation().context }
    private val em by lazy { context.getSystemService(EthernetManager::class.java)!! }
    private val cm by lazy { context.getSystemService(ConnectivityManager::class.java)!! }
    private val handler by lazy { Handler(Looper.getMainLooper()) }

    private val ifaceListener = EthernetStateListener()
    private val createdIfaces = ArrayList<EthernetTestInterface>()
    private val addedListeners = ArrayList<EthernetStateListener>()
    private val registeredCallbacks = ArrayList<TestableNetworkCallback>()

    private var tetheredInterfaceRequest: TetheredInterfaceRequest? = null

    private class EthernetTestInterface(
        context: Context,
        private val handler: Handler,
        hasCarrier: Boolean
    ) {
        private val tapInterface: TestNetworkInterface
        private val packetReader: TapPacketReader
        private val raResponder: RouterAdvertisementResponder
        private val tnm: TestNetworkManager
        val name get() = tapInterface.interfaceName
        val onLinkPrefix get() = raResponder.prefix

        init {
            tnm = runAsShell(MANAGE_TEST_NETWORKS) {
                context.getSystemService(TestNetworkManager::class.java)!!
            }
            tapInterface = runAsShell(MANAGE_TEST_NETWORKS) {
                // Configuring a tun/tap interface always enables the carrier. If hasCarrier is
                // false, it is subsequently disabled. This means that the interface may briefly get
                // link. With IPv6 provisioning delays (RS delay and DAD) disabled, this can cause
                // tests that expect no network to come up when hasCarrier is false to become flaky.
                tnm.createTapInterface(hasCarrier, false /* bringUp */)
            }
            val mtu = tapInterface.mtu
            packetReader = TapPacketReader(handler, tapInterface.fileDescriptor.fileDescriptor, mtu)
            raResponder = RouterAdvertisementResponder(packetReader)
            val iidString = "fe80::${Integer.toHexString(Random().nextInt(65536))}"
            val linklocal = InetAddresses.parseNumericAddress(iidString) as Inet6Address
            raResponder.addRouterEntry(MacAddress.fromString("01:23:45:67:89:ab"), linklocal)

            packetReader.startAsyncForTest()
            raResponder.start()
        }

        // WARNING: this function requires kernel support. Call assumeChangingCarrierSupported() at
        // the top of your test.
        fun setCarrierEnabled(enabled: Boolean) {
            runAsShell(MANAGE_TEST_NETWORKS) {
                tnm.setCarrierEnabled(tapInterface, enabled)
            }
        }

        fun destroy() {
            raResponder.stop()
            handler.post({ packetReader.stop() })
            handler.waitForIdle(TIMEOUT_MS)
        }
    }

    private open class EthernetStateListener private constructor(
        private val history: ArrayTrackRecord<CallbackEntry>
    ) : InterfaceStateListener, IntConsumer,
                TrackRecord<EthernetStateListener.CallbackEntry> by history {
        constructor() : this(ArrayTrackRecord())

        val events = history.newReadHead()

        sealed class CallbackEntry {
            data class InterfaceStateChanged(
                val iface: String,
                val state: Int,
                val role: Int,
                val configuration: IpConfiguration?
            ) : CallbackEntry() {
                override fun toString(): String {
                    val stateString = when (state) {
                        STATE_ABSENT -> "STATE_ABSENT"
                        STATE_LINK_UP -> "STATE_LINK_UP"
                        STATE_LINK_DOWN -> "STATE_LINK_DOWN"
                        else -> state.toString()
                    }
                    val roleString = when (role) {
                        ROLE_NONE -> "ROLE_NONE"
                        ROLE_CLIENT -> "ROLE_CLIENT"
                        ROLE_SERVER -> "ROLE_SERVER"
                        else -> role.toString()
                    }
                    return ("InterfaceStateChanged(iface=$iface, state=$stateString, " +
                            "role=$roleString, ipConfig=$configuration)")
                }
            }

            data class EthernetStateChanged(val state: Int) : CallbackEntry() {
                override fun toString(): String {
                    val stateString = when (state) {
                        ETHERNET_STATE_ENABLED -> "ETHERNET_STATE_ENABLED"
                        ETHERNET_STATE_DISABLED -> "ETHERNET_STATE_DISABLED"
                        else -> state.toString()
                    }
                    return "EthernetStateChanged(state=$stateString)"
                }
            }
        }

        override fun onInterfaceStateChanged(
            iface: String,
            state: Int,
            role: Int,
            cfg: IpConfiguration?
        ) {
            add(InterfaceStateChanged(iface, state, role, cfg))
        }

        override fun accept(state: Int) {
            add(EthernetStateChanged(state))
        }

        fun <T : CallbackEntry> expectCallback(expected: T): T {
            val event = events.poll(TIMEOUT_MS)
            assertEquals(expected, event)
            return event as T
        }

        fun expectCallback(iface: EthernetTestInterface, state: Int, role: Int) {
            expectCallback(createChangeEvent(iface.name, state, role))
        }

        fun expectCallback(state: Int) {
            expectCallback(EthernetStateChanged(state))
        }

        private fun createChangeEvent(iface: String, state: Int, role: Int) =
                InterfaceStateChanged(iface, state, role,
                        if (state != STATE_ABSENT) DEFAULT_IP_CONFIGURATION else null)

        fun eventuallyExpect(expected: CallbackEntry) {
            val cb = events.poll(TIMEOUT_MS) { it == expected }
            assertNotNull(cb, "Never received expected $expected. Received: ${events.backtrace()}")
        }

        fun eventuallyExpect(iface: EthernetTestInterface, state: Int, role: Int) {
            eventuallyExpect(createChangeEvent(iface.name, state, role))
        }

        fun eventuallyExpect(state: Int) {
            eventuallyExpect(EthernetStateChanged(state))
        }

        fun assertNoCallback() {
            val cb = events.poll(NO_CALLBACK_TIMEOUT_MS)
            assertNull(cb, "Expected no callback but got $cb")
        }
    }

    private class TetheredInterfaceListener : TetheredInterfaceCallback {
        private val available = CompletableFuture<String>()

        override fun onAvailable(iface: String) {
            available.complete(iface)
        }

        override fun onUnavailable() {
            available.completeExceptionally(IllegalStateException("onUnavailable was called"))
        }

        fun expectOnAvailable(timeout: Long = TIMEOUT_MS): String {
            return available.get(timeout, TimeUnit.MILLISECONDS)
        }

        fun expectOnUnavailable() {
            // Assert that the future fails with the IllegalStateException from the
            // completeExceptionally() call inside onUnavailable.
            assertFailsWith(IllegalStateException::class) {
                try {
                    available.get(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                } catch (e: ExecutionException) {
                    throw e.cause!!
                }
            }
        }
    }

    private class EthernetOutcomeReceiver :
        OutcomeReceiver<String, EthernetNetworkManagementException> {
        private val result = CompletableFuture<String>()

        override fun onResult(iface: String) {
            assertFalse(result.isDone())
            result.complete(iface)
        }

        override fun onError(e: EthernetNetworkManagementException) {
            assertFalse(result.isDone())
            result.completeExceptionally(e)
        }

        fun expectResult(expected: String) {
            assertEquals(expected, result.get(TIMEOUT_MS, TimeUnit.MILLISECONDS))
        }

        fun expectError() {
            // Assert that the future fails with EthernetNetworkManagementException from the
            // completeExceptionally() call inside onUnavailable.
            assertFailsWith(EthernetNetworkManagementException::class) {
                try {
                    result.get()
                } catch (e: ExecutionException) {
                    throw e.cause!!
                }
            }
        }
    }

    private fun isEthernetSupported() = em != null

    @Before
    fun setUp() {
        assumeTrue(isEthernetSupported())
        setIncludeTestInterfaces(true)
        addInterfaceStateListener(ifaceListener)
        // Handler.post() events may get processed after native fd events, so it is possible that
        // RTM_NEWLINK (from a subsequent createInterface() call) arrives before the interface state
        // listener is registered. This affects the callbacks and breaks the tests.
        // setEthernetEnabled() will always wait on a callback, so it is used as a barrier to ensure
        // proper listener registration before proceeding.
        setEthernetEnabled(true)
    }

    @After
    fun tearDown() {
        if (!isEthernetSupported()) return
        // Reenable ethernet, so ABSENT callbacks are received.
        setEthernetEnabled(true)

        for (iface in createdIfaces) {
            iface.destroy()
            ifaceListener.eventuallyExpect(iface, STATE_ABSENT, ROLE_NONE)
        }

        // After test interfaces are removed, disable tracking.
        setIncludeTestInterfaces(false)

        for (listener in addedListeners) {
            // Even if a given listener was not registered as both an interface and ethernet state
            // listener, calling remove is safe.
            em.removeInterfaceStateListener(listener)
            em.removeEthernetStateListener(listener)
        }
        registeredCallbacks.forEach { cm.unregisterNetworkCallback(it) }
        releaseTetheredInterface()
        // Force releaseTetheredInterface() to be processed before starting the next test by calling
        // setEthernetEnabled(true) which always waits on a callback.
        setEthernetEnabled(true)
    }

    // Setting the carrier up / down relies on TUNSETCARRIER which was added in kernel version 5.0.
    private fun assumeChangingCarrierSupported() {
        assumeTrue(isKernelVersionAtLeast("5.0.0"))
    }

    // Configuring a tap interface without carrier relies on IFF_NO_CARRIER
    // which was added in kernel version 6.0.
    private fun assumeCreateInterfaceWithoutCarrierSupported() {
        assumeTrue(isKernelVersionAtLeast("6.0.0"))
    }

    private fun isAdbOverEthernet(): Boolean {
        // If no ethernet interface is available, adb is not connected over ethernet.
        if (em.getInterfaceList().isEmpty()) return false

        // cuttlefish is special and does not connect adb over ethernet.
        if (SystemProperties.get("ro.product.board", "") == "cutf") return false

        // Check if adb is connected over the network.
        return (SystemProperties.getInt("persist.adb.tcp.port", -1) > -1 ||
                SystemProperties.getInt("service.adb.tcp.port", -1) > -1)
    }

    private fun addInterfaceStateListener(listener: EthernetStateListener) {
        em.addInterfaceStateListener(handler::post, listener)
        addedListeners.add(listener)
    }

    private fun addEthernetStateListener(listener: EthernetStateListener) {
        em.addEthernetStateListener(handler::post, listener)
        addedListeners.add(listener)
    }

    // WARNING: setting hasCarrier to false requires kernel support. Call
    // assumeCreateInterfaceWithoutCarrierSupported() at the top of your test.
    private fun createInterface(hasCarrier: Boolean = true): EthernetTestInterface {
        val iface = EthernetTestInterface(
            context,
            handler,
            hasCarrier
        ).also { createdIfaces.add(it) }

        // when an interface comes up, we should always see a down cb before an up cb.
        ifaceListener.eventuallyExpect(iface, STATE_LINK_DOWN, ROLE_CLIENT)
        if (hasCarrier) {
            ifaceListener.expectCallback(iface, STATE_LINK_UP, ROLE_CLIENT)
        }
        return iface
    }

    private fun setIncludeTestInterfaces(value: Boolean) {
        runAsShell(NETWORK_SETTINGS) {
            em.setIncludeTestInterfaces(value)
        }
    }

    private fun removeInterface(iface: EthernetTestInterface) {
        iface.destroy()
        createdIfaces.remove(iface)
        ifaceListener.eventuallyExpect(iface, STATE_ABSENT, ROLE_NONE)
    }

    private fun requestNetwork(request: NetworkRequest): TestableNetworkCallback {
        return TestableNetworkCallback(
                timeoutMs = TIMEOUT_MS,
                noCallbackTimeoutMs = NO_CALLBACK_TIMEOUT_MS).also {
            cm.requestNetwork(request, it)
            registeredCallbacks.add(it)
        }
    }

    private fun registerNetworkListener(request: NetworkRequest): TestableNetworkCallback {
        return TestableNetworkCallback(
                timeoutMs = TIMEOUT_MS,
                noCallbackTimeoutMs = NO_CALLBACK_TIMEOUT_MS).also {
            cm.registerNetworkCallback(request, it)
            registeredCallbacks.add(it)
        }
    }

    private fun requestTetheredInterface() = TetheredInterfaceListener().also {
        tetheredInterfaceRequest = runAsShell(NETWORK_SETTINGS) {
            em.requestTetheredInterface(handler::post, it)
        }
    }

    private fun releaseTetheredInterface() {
        runAsShell(NETWORK_SETTINGS) {
            tetheredInterfaceRequest?.release()
            tetheredInterfaceRequest = null
        }
    }

    private fun releaseRequest(cb: TestableNetworkCallback) {
        cm.unregisterNetworkCallback(cb)
        registeredCallbacks.remove(cb)
    }

    private fun disableInterface(iface: EthernetTestInterface) = EthernetOutcomeReceiver().also {
        runAsShell(MANAGE_TEST_NETWORKS) {
            em.disableInterface(iface.name, handler::post, it)
        }
    }

    private fun enableInterface(iface: EthernetTestInterface) = EthernetOutcomeReceiver().also {
        runAsShell(MANAGE_TEST_NETWORKS) {
            em.enableInterface(iface.name, handler::post, it)
        }
    }

    private fun updateConfiguration(
        iface: EthernetTestInterface,
        ipConfig: IpConfiguration? = null,
        capabilities: NetworkCapabilities? = null
    ) = EthernetOutcomeReceiver().also {
        runAsShell(MANAGE_TEST_NETWORKS) {
            em.updateConfiguration(
                iface.name,
                EthernetNetworkUpdateRequest.Builder()
                    .setIpConfiguration(ipConfig)
                    .setNetworkCapabilities(capabilities).build(),
                handler::post,
                it)
        }
    }

    // WARNING: check that isAdbOverEthernet() is false before calling setEthernetEnabled(false).
    private fun setEthernetEnabled(enabled: Boolean) {
        runAsShell(NETWORK_SETTINGS) { em.setEthernetEnabled(enabled) }

        val listener = EthernetStateListener()
        addEthernetStateListener(listener)
        listener.eventuallyExpect(if (enabled) ETHERNET_STATE_ENABLED else ETHERNET_STATE_DISABLED)
    }

    // NetworkRequest.Builder does not create a copy of the passed NetworkRequest, so in order to
    // keep ETH_REQUEST as it is, a defensive copy is created here.
    private fun NetworkRequest.copyWithEthernetSpecifier(ifaceName: String) =
        NetworkRequest.Builder(NetworkRequest(ETH_REQUEST))
            .setNetworkSpecifier(EthernetNetworkSpecifier(ifaceName)).build()

    // b/233534110: eventuallyExpect<Lost>() does not advance ReadHead, use
    // eventuallyExpect(Lost::class) instead.
    private fun TestableNetworkCallback.eventuallyExpectLost(n: Network? = null) =
        eventuallyExpect(Lost::class) { n?.equals(it.network) ?: true }

    private fun TestableNetworkCallback.assertNeverLost(n: Network? = null) =
        assertNoCallback { it is Lost && (n?.equals(it.network) ?: true) }

    private fun TestableNetworkCallback.assertNeverAvailable(n: Network? = null) =
        assertNoCallback { it is Available && (n?.equals(it.network) ?: true) }

    private fun TestableNetworkCallback.expectCapabilitiesWithInterfaceName(name: String) =
        expect<CapabilitiesChanged> { it.caps.networkSpecifier == EthernetNetworkSpecifier(name) }

    private fun TestableNetworkCallback.eventuallyExpectCapabilities(nc: NetworkCapabilities) {
        // b/233534110: eventuallyExpect<CapabilitiesChanged>() does not advance ReadHead.
        eventuallyExpect(CapabilitiesChanged::class) {
            // CS may mix in additional capabilities, so NetworkCapabilities#equals cannot be used.
            // Check if all expected capabilities are present instead.
            it is CapabilitiesChanged && nc.capabilities.all { c -> it.caps.hasCapability(c) }
        }
    }

    private fun TestableNetworkCallback.eventuallyExpectLpForStaticConfig(
        config: StaticIpConfiguration
    ) {
        // b/233534110: eventuallyExpect<LinkPropertiesChanged>() does not advance ReadHead.
        eventuallyExpect(LinkPropertiesChanged::class) {
            it is LinkPropertiesChanged && it.lp.linkAddresses.any { la ->
                la.isSameAddressAs(config.ipAddress)
            }
        }
    }

    @Test
    fun testCallbacks() {
        // Only run this test when no non-restricted / physical interfaces are present.
        // This test ensures that interface state listeners function properly, so the assumption
        // check is explicitly *not* using an interface state listener.
        // Since restricted interfaces cannot be used for tethering,
        // assumeNoInterfaceForTetheringAvailable() is an okay proxy.
        assumeNoInterfaceForTetheringAvailable()

        // If an interface exists when the callback is registered, it is reported on registration.
        val iface = createInterface()
        val listener1 = EthernetStateListener()
        addInterfaceStateListener(listener1)
        listener1.expectCallback(iface, STATE_LINK_UP, ROLE_CLIENT)

        // If an interface appears, existing callbacks see it.
        val iface2 = createInterface()
        listener1.expectCallback(iface2, STATE_LINK_DOWN, ROLE_CLIENT)
        listener1.expectCallback(iface2, STATE_LINK_UP, ROLE_CLIENT)

        // Register a new listener, it should see state of all existing interfaces immediately.
        val listener2 = EthernetStateListener()
        addInterfaceStateListener(listener2)
        listener2.expectCallback(iface, STATE_LINK_UP, ROLE_CLIENT)
        listener2.expectCallback(iface2, STATE_LINK_UP, ROLE_CLIENT)

        // Removing interfaces first sends link down, then STATE_ABSENT/ROLE_NONE.
        removeInterface(iface)
        for (listener in listOf(listener1, listener2)) {
            listener.expectCallback(iface, STATE_LINK_DOWN, ROLE_CLIENT)
            listener.expectCallback(iface, STATE_ABSENT, ROLE_NONE)
        }

        removeInterface(iface2)
        for (listener in listOf(listener1, listener2)) {
            listener.expectCallback(iface2, STATE_LINK_DOWN, ROLE_CLIENT)
            listener.expectCallback(iface2, STATE_ABSENT, ROLE_NONE)
            listener.assertNoCallback()
        }
    }

    @Test
    fun testCallbacks_withRunningInterface() {
        assumeFalse(isAdbOverEthernet())
        // Only run this test when no non-restricted / physical interfaces are present.
        assumeNoInterfaceForTetheringAvailable()

        val iface = createInterface()
        val listener = EthernetStateListener()
        addInterfaceStateListener(listener)
        listener.eventuallyExpect(iface, STATE_LINK_UP, ROLE_CLIENT)

        // Remove running interface. The interface stays running but is no longer tracked.
        setEthernetEnabled(false)
        listener.expectCallback(iface, STATE_ABSENT, ROLE_NONE)

        setEthernetEnabled(true)
        listener.expectCallback(iface, STATE_LINK_UP, ROLE_CLIENT)
        listener.assertNoCallback()
    }

    private fun assumeNoInterfaceForTetheringAvailable() {
        // Interfaces that have configured NetworkCapabilities will never be used for tethering,
        // see aosp/2123900.
        try {
            // assumeException does not exist.
            requestTetheredInterface().expectOnAvailable(NO_CALLBACK_TIMEOUT_MS)
            // interface used for tethering is available, throw an assumption error.
            assumeTrue(false)
        } catch (e: TimeoutException) {
            // do nothing -- the TimeoutException indicates that no interface is available for
            // tethering.
            releaseTetheredInterface()
            // Force releaseTetheredInterface() to be processed before proceeding by calling
            // setEthernetEnabled(true) which always waits on a callback.
            setEthernetEnabled(true)
        }
    }

    @Test
    fun testCallbacks_forServerModeInterfaces() {
        // do not run this test if an interface that can be used for tethering already exists.
        assumeNoInterfaceForTetheringAvailable()

        val iface = createInterface()
        requestTetheredInterface().expectOnAvailable()

        val listener = EthernetStateListener()
        addInterfaceStateListener(listener)
        // TODO(b/295146844): do not report IpConfiguration for server mode interfaces.
        listener.eventuallyExpect(iface, STATE_LINK_UP, ROLE_SERVER)

        releaseTetheredInterface()
        listener.eventuallyExpect(iface, STATE_LINK_UP, ROLE_CLIENT)

        requestTetheredInterface().expectOnAvailable()
        // This should be changed to expectCallback, once b/236895792 is fixed.
        listener.eventuallyExpect(iface, STATE_LINK_UP, ROLE_SERVER)

        releaseTetheredInterface()
        listener.expectCallback(iface, STATE_LINK_UP, ROLE_CLIENT)
    }

    @Test
    fun testCallbacks_afterRemovingServerModeInterface() {
        // do not run this test if an interface that can be used for tethering already exists.
        assumeNoInterfaceForTetheringAvailable()

        val iface = createInterface()
        requestTetheredInterface().expectOnAvailable()
        removeInterface(iface)

        val listener = EthernetStateListener()
        addInterfaceStateListener(listener)
        listener.assertNoCallback()
    }

    @Test
    fun testGetInterfaceList() {
        // Create two test interfaces and check the return list contains the interface names.
        val iface1 = createInterface()
        val iface2 = createInterface()
        var ifaces = em.getInterfaceList()
        assertTrue(ifaces.size > 0)
        assertTrue(ifaces.contains(iface1.name))
        assertTrue(ifaces.contains(iface2.name))

        // Remove one existing test interface and check the return list doesn't contain the
        // removed interface name.
        removeInterface(iface1)
        ifaces = em.getInterfaceList()
        assertFalse(ifaces.contains(iface1.name))
        assertTrue(ifaces.contains(iface2.name))

        removeInterface(iface2)
    }

    @Test
    fun testNetworkRequest_withSingleExistingInterface() {
        createInterface()

        // install a listener which will later be used to verify the Lost callback
        val listenerCb = registerNetworkListener(ETH_REQUEST)
        // assert the network is only brought up by a request.
        listenerCb.assertNeverAvailable()

        val cb = requestNetwork(ETH_REQUEST)
        val network = cb.expect<Available>().network

        cb.assertNeverLost()
        releaseRequest(cb)
        listenerCb.eventuallyExpectLost(network)
    }

    @Test
    fun testNetworkRequest_beforeSingleInterfaceIsUp() {
        val cb = requestNetwork(ETH_REQUEST)

        // bring up interface after network has been requested.
        // Note: there is no guarantee that the NetworkRequest has been processed before the
        // interface is actually created. That being said, it takes a few seconds between calling
        // createInterface and the interface actually being properly registered with the ethernet
        // module, so it is extremely unlikely that the CS handler thread has not run until then.
        val iface = createInterface()
        val network = cb.expect<Available>().network

        // remove interface before network request has been removed
        cb.assertNeverLost()
        removeInterface(iface)
        cb.eventuallyExpectLost()
    }

    @Test
    fun testNetworkRequest_withMultipleInterfaces() {
        val iface1 = createInterface()
        val iface2 = createInterface()

        val cb = requestNetwork(ETH_REQUEST.copyWithEthernetSpecifier(iface2.name))

        cb.expect<Available>()
        cb.expectCapabilitiesWithInterfaceName(iface2.name)

        removeInterface(iface1)
        cb.assertNeverLost()
        removeInterface(iface2)
        cb.eventuallyExpectLost()
    }

    @Test
    fun testNetworkRequest_withInterfaceBeingReplaced() {
        val iface1 = createInterface()

        val cb = requestNetwork(ETH_REQUEST)
        val network = cb.expect<Available>().network

        // create another network and verify the request sticks to the current network
        val iface2 = createInterface()
        cb.assertNeverLost()

        // remove iface1 and verify the request brings up iface2
        removeInterface(iface1)
        cb.eventuallyExpectLost(network)
        cb.expect<Available>()
    }

    @Test
    fun testNetworkRequest_withMultipleInterfacesAndRequests() {
        val iface1 = createInterface()
        val iface2 = createInterface()

        val cb1 = requestNetwork(ETH_REQUEST.copyWithEthernetSpecifier(iface1.name))
        val cb2 = requestNetwork(ETH_REQUEST.copyWithEthernetSpecifier(iface2.name))
        val cb3 = requestNetwork(ETH_REQUEST)

        cb1.expect<Available>()
        cb1.expectCapabilitiesWithInterfaceName(iface1.name)
        cb2.expect<Available>()
        cb2.expectCapabilitiesWithInterfaceName(iface2.name)
        // this request can be matched by either network.
        cb3.expect<Available>()

        cb1.assertNeverLost()
        cb2.assertNeverLost()
        cb3.assertNeverLost()
    }

    @Test
    fun testNetworkRequest_ensureProperRefcounting() {
        // create first request before interface is up / exists; create another request after it has
        // been created; release one of them and check that the network stays up.
        val listener = registerNetworkListener(ETH_REQUEST)
        val cb1 = requestNetwork(ETH_REQUEST)

        val iface = createInterface()
        val network = cb1.expect<Available>().network

        val cb2 = requestNetwork(ETH_REQUEST)
        cb2.expect<Available>()

        // release the first request; this used to trigger b/197548738
        releaseRequest(cb1)

        cb2.assertNeverLost()
        releaseRequest(cb2)
        listener.eventuallyExpectLost(network)
    }

    @Test
    fun testNetworkRequest_forInterfaceWhileTogglingCarrier() {
        assumeCreateInterfaceWithoutCarrierSupported()
        assumeChangingCarrierSupported()

        val iface = createInterface(false /* hasCarrier */)

        val cb = requestNetwork(ETH_REQUEST)
        cb.assertNeverAvailable()

        iface.setCarrierEnabled(true)
        cb.expect<Available>()

        iface.setCarrierEnabled(false)
        cb.eventuallyExpectLost()
    }

    // TODO: move to MTS
    @Test
    fun testNetworkRequest_linkPropertiesUpdate() {
        val iface = createInterface()
        val cb = requestNetwork(ETH_REQUEST)
        // b/233534110: eventuallyExpect<LinkPropertiesChanged>() does not advance ReadHead
        cb.eventuallyExpect(LinkPropertiesChanged::class) {
            it is LinkPropertiesChanged && it.lp.addresses.any {
                address -> iface.onLinkPrefix.contains(address)
            }
        }
    }

    @Test
    fun testRemoveInterface_whileInServerMode() {
        assumeNoInterfaceForTetheringAvailable()

        val listener = EthernetStateListener()
        addInterfaceStateListener(listener)

        val iface = createInterface()
        val ifaceName = requestTetheredInterface().expectOnAvailable()

        assertEquals(iface.name, ifaceName)
        listener.eventuallyExpect(iface, STATE_LINK_UP, ROLE_SERVER)

        removeInterface(iface)

        // Note: removeInterface already verifies that a STATE_ABSENT, ROLE_NONE callback is
        // received, but it can't hurt to explicitly check for it.
        listener.expectCallback(iface, STATE_ABSENT, ROLE_NONE)
        releaseTetheredInterface()
        listener.assertNoCallback()
    }

    @Test
    fun testEnableDisableInterface_withActiveRequest() {
        val iface = createInterface()
        val cb = requestNetwork(ETH_REQUEST)
        cb.expect<Available>()
        cb.assertNeverLost()

        disableInterface(iface).expectResult(iface.name)
        cb.eventuallyExpectLost()

        enableInterface(iface).expectResult(iface.name)
        cb.expect<Available>()
    }

    @Test
    fun testEnableDisableInterface_withoutStateChange() {
        val iface = createInterface()
        // Interface is already enabled, so enableInterface() should return success
        enableInterface(iface).expectResult(iface.name)

        disableInterface(iface).expectResult(iface.name)
        // Interface is already disabled, so disableInterface() should return success.
        disableInterface(iface).expectResult(iface.name)
    }

    @Test
    fun testEnableDisableInterface_withMissingInterface() {
        val iface = createInterface()
        removeInterface(iface)
        // Interface does not exist, enable/disableInterface() should both return an error.
        enableInterface(iface).expectError()
        disableInterface(iface).expectError()
    }

    @Test
    fun testUpdateConfiguration_forBothIpConfigAndCapabilities() {
        val iface = createInterface()
        val cb = requestNetwork(ETH_REQUEST.copyWithEthernetSpecifier(iface.name))
        cb.expect<Available>()

        updateConfiguration(iface, STATIC_IP_CONFIGURATION, TEST_CAPS).expectResult(iface.name)
        cb.eventuallyExpectCapabilities(TEST_CAPS)
        cb.eventuallyExpectLpForStaticConfig(STATIC_IP_CONFIGURATION.staticIpConfiguration)
    }

    @Test
    fun testUpdateConfiguration_forOnlyIpConfig() {
        val iface = createInterface()
        val cb = requestNetwork(ETH_REQUEST.copyWithEthernetSpecifier(iface.name))
        cb.expect<Available>()

        updateConfiguration(iface, STATIC_IP_CONFIGURATION).expectResult(iface.name)
        cb.eventuallyExpectLpForStaticConfig(STATIC_IP_CONFIGURATION.staticIpConfiguration)
    }

    @Test
    fun testUpdateConfiguration_forOnlyCapabilities() {
        val iface = createInterface()
        val cb = requestNetwork(ETH_REQUEST.copyWithEthernetSpecifier(iface.name))
        cb.expect<Available>()

        updateConfiguration(iface, capabilities = TEST_CAPS).expectResult(iface.name)
        cb.eventuallyExpectCapabilities(TEST_CAPS)
    }

    @Test
    fun testUpdateConfiguration_forAllowedUids() {
        // Configure a restricted network.
        val iface = createInterface()
        val request = NetworkRequest.Builder(ETH_REQUEST.copyWithEthernetSpecifier(iface.name))
                .removeCapability(NET_CAPABILITY_NOT_RESTRICTED).build()
        updateConfiguration(iface, capabilities = request.networkCapabilities)
                .expectResult(iface.name)

        // Request the restricted network as the shell with CONNECTIVITY_USE_RESTRICTED_NETWORKS.
        val cb = runAsShell(CONNECTIVITY_USE_RESTRICTED_NETWORKS) { requestNetwork(request) }
        val network = cb.expect<Available>().network
        cb.assertNeverLost(network)

        // The network is restricted therefore binding to it when available will fail.
        Socket().use { socket ->
            assertThrows(IOException::class.java, { network.bindSocket(socket) })
        }

        // Add the test process UID to the allowed UIDs for the network and ultimately bind again.
        val allowedUids = setOf(Process.myUid())
        val nc = NetworkCapabilities.Builder(request.networkCapabilities)
                .setAllowedUids(allowedUids).build()
        updateConfiguration(iface, capabilities = nc).expectResult(iface.name)

        // UpdateConfiguration() currently does a restart on the ethernet interface therefore lost
        // will be expected first before available, as part of the restart.
        cb.expect<Lost>(network)
        val updatedNetwork = cb.expect<Available>().network
        // With the test process UID allowed, binding to a restricted network should be successful.
        Socket().use { socket -> updatedNetwork.bindSocket(socket) }

        // Reset capabilities to not-restricted, otherwise tearDown won't see the interface callback
        // as ifaceListener does not have the restricted permission.
        // TODO: Find a better way to do this when there are more tests around restricted
        // interfaces.
        updateConfiguration(iface, capabilities = TEST_CAPS).expectResult(iface.name)
    }

    // TODO: consider only having this test in MTS as it makes use of the fact that
    // setEthernetEnabled(false) untracks all interfaces. This behavior is an implementation detail
    // and may change in the future.
    @Test
    fun testUpdateConfiguration_onUntrackedInterface() {
        assumeFalse(isAdbOverEthernet())
        val iface = createInterface()
        setEthernetEnabled(false)

        // Updating the IpConfiguration and NetworkCapabilities on absent interfaces is a supported
        // use case.
        updateConfiguration(iface, STATIC_IP_CONFIGURATION, TEST_CAPS).expectResult(iface.name)

        setEthernetEnabled(true)
        val cb = requestNetwork(ETH_REQUEST)
        cb.expect<Available>()
        cb.eventuallyExpectCapabilities(TEST_CAPS)
        cb.eventuallyExpectLpForStaticConfig(STATIC_IP_CONFIGURATION.staticIpConfiguration)
    }

    @Test
    fun testUpdateConfiguration_withLinkDown() {
        assumeChangingCarrierSupported()
        // createInterface without carrier is racy, so create it and then remove carrier.
        val iface = createInterface()
        val cb = requestNetwork(ETH_REQUEST)
        cb.expect<Available>()

        iface.setCarrierEnabled(false)
        cb.eventuallyExpectLost()

        updateConfiguration(iface, STATIC_IP_CONFIGURATION, TEST_CAPS).expectResult(iface.name)
        cb.assertNoCallback()

        iface.setCarrierEnabled(true)
        cb.eventuallyExpectCapabilities(TEST_CAPS)
        cb.eventuallyExpectLpForStaticConfig(STATIC_IP_CONFIGURATION.staticIpConfiguration)
    }
}
