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

package com.android.testutils

import android.annotation.SuppressLint
import android.net.LinkAddress
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import com.android.testutils.RecorderCallback.CallbackEntry
import com.android.testutils.RecorderCallback.CallbackEntry.Available
import com.android.testutils.RecorderCallback.CallbackEntry.BlockedStatus
import com.android.testutils.RecorderCallback.CallbackEntry.CapabilitiesChanged
import com.android.testutils.RecorderCallback.CallbackEntry.Companion.AVAILABLE
import com.android.testutils.RecorderCallback.CallbackEntry.Companion.BLOCKED_STATUS
import com.android.testutils.RecorderCallback.CallbackEntry.Companion.LINK_PROPERTIES_CHANGED
import com.android.testutils.RecorderCallback.CallbackEntry.Companion.LOSING
import com.android.testutils.RecorderCallback.CallbackEntry.Companion.LOST
import com.android.testutils.RecorderCallback.CallbackEntry.Companion.NETWORK_CAPS_UPDATED
import com.android.testutils.RecorderCallback.CallbackEntry.Companion.RESUMED
import com.android.testutils.RecorderCallback.CallbackEntry.Companion.SUSPENDED
import com.android.testutils.RecorderCallback.CallbackEntry.Companion.UNAVAILABLE
import com.android.testutils.RecorderCallback.CallbackEntry.LinkPropertiesChanged
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

const val SHORT_TIMEOUT_MS = 20L
const val DEFAULT_LINGER_DELAY_MS = 30000
const val NOT_METERED = NetworkCapabilities.NET_CAPABILITY_NOT_METERED
const val WIFI = NetworkCapabilities.TRANSPORT_WIFI
const val CELLULAR = NetworkCapabilities.TRANSPORT_CELLULAR
const val TEST_INTERFACE_NAME = "testInterfaceName"

@RunWith(JUnit4::class)
@SuppressLint("NewApi") // Uses hidden APIs, which the linter would identify as missing APIs.
class TestableNetworkCallbackTest {
    private lateinit var mCallback: TestableNetworkCallback

    private fun makeHasNetwork(netId: Int) = object : TestableNetworkCallback.HasNetwork {
        override val network: Network = Network(netId)
    }

    @Before
    fun setUp() {
        mCallback = TestableNetworkCallback()
    }

    @Test
    fun testLastAvailableNetwork() {
        // Make sure there is no last available network at first, then the last available network
        // is returned after onAvailable is called.
        val net2097 = Network(2097)
        assertNull(mCallback.lastAvailableNetwork)
        mCallback.onAvailable(net2097)
        assertEquals(mCallback.lastAvailableNetwork, net2097)

        // Make sure calling onCapsChanged/onLinkPropertiesChanged don't affect the last available
        // network.
        mCallback.onCapabilitiesChanged(net2097, NetworkCapabilities())
        mCallback.onLinkPropertiesChanged(net2097, LinkProperties())
        assertEquals(mCallback.lastAvailableNetwork, net2097)

        // Make sure onLost clears the last available network.
        mCallback.onLost(net2097)
        assertNull(mCallback.lastAvailableNetwork)

        // Do the same but with a different network after onLost : make sure the last available
        // network is the new one, not the original one.
        val net2098 = Network(2098)
        mCallback.onAvailable(net2098)
        mCallback.onCapabilitiesChanged(net2098, NetworkCapabilities())
        mCallback.onLinkPropertiesChanged(net2098, LinkProperties())
        assertEquals(mCallback.lastAvailableNetwork, net2098)

        // Make sure onAvailable changes the last available network even if onLost was not called.
        val net2099 = Network(2099)
        mCallback.onAvailable(net2099)
        assertEquals(mCallback.lastAvailableNetwork, net2099)

        // For legacy reasons, lastAvailableNetwork is null as soon as any is lost, not necessarily
        // the last available one. Check that behavior.
        mCallback.onLost(net2098)
        assertNull(mCallback.lastAvailableNetwork)

        // Make sure that losing the really last available one still results in null.
        mCallback.onLost(net2099)
        assertNull(mCallback.lastAvailableNetwork)

        // Make sure multiple onAvailable in a row then onLost still results in null.
        mCallback.onAvailable(net2097)
        mCallback.onAvailable(net2098)
        mCallback.onAvailable(net2099)
        mCallback.onLost(net2097)
        assertNull(mCallback.lastAvailableNetwork)
    }

    @Test
    fun testAssertNoCallback() {
        mCallback.assertNoCallback(SHORT_TIMEOUT_MS)
        mCallback.onAvailable(Network(100))
        assertFails { mCallback.assertNoCallback(SHORT_TIMEOUT_MS) }
        val net = Network(101)
        mCallback.assertNoCallback { it is Available }
        mCallback.onAvailable(net)
        // Expect no blocked status change. Receive other callback does not fail the test.
        mCallback.assertNoCallback { it is BlockedStatus }
        mCallback.onBlockedStatusChanged(net, true)
        assertFails { mCallback.assertNoCallback { it is BlockedStatus } }
        mCallback.onBlockedStatusChanged(net, false)
        mCallback.onCapabilitiesChanged(net, NetworkCapabilities())
        assertFails { mCallback.assertNoCallback { it is CapabilitiesChanged } }
    }

    @Test
    fun testCapabilitiesWithAndWithout() {
        val net = Network(101)
        val matcher = makeHasNetwork(101)
        val meteredNc = NetworkCapabilities()
        val unmeteredNc = NetworkCapabilities().addCapability(NOT_METERED)
        // Check that expecting caps (with or without) fails when no callback has been received.
        assertFails {
            mCallback.expectCaps(matcher, SHORT_TIMEOUT_MS) { it.hasCapability(NOT_METERED) }
        }
        assertFails {
            mCallback.expectCaps(matcher, SHORT_TIMEOUT_MS) { !it.hasCapability(NOT_METERED) }
        }

        // Add NOT_METERED and check that With succeeds and Without fails.
        mCallback.onCapabilitiesChanged(net, unmeteredNc)
        mCallback.expectCaps(matcher) { it.hasCapability(NOT_METERED) }
        mCallback.onCapabilitiesChanged(net, unmeteredNc)
        assertFails {
            mCallback.expectCaps(matcher, SHORT_TIMEOUT_MS) { !it.hasCapability(NOT_METERED) }
        }

        // Don't add NOT_METERED and check that With fails and Without succeeds.
        mCallback.onCapabilitiesChanged(net, meteredNc)
        assertFails {
            mCallback.expectCaps(matcher, SHORT_TIMEOUT_MS) { it.hasCapability(NOT_METERED) }
        }
        mCallback.onCapabilitiesChanged(net, meteredNc)
        mCallback.expectCaps(matcher) { !it.hasCapability(NOT_METERED) }
    }

    @Test
    fun testExpectWithPredicate() {
        val net = Network(193)
        val netCaps = NetworkCapabilities().addTransportType(CELLULAR)
        // Check that expecting callbackThat anything fails when no callback has been received.
        assertFails { mCallback.expect<CallbackEntry>(timeoutMs = SHORT_TIMEOUT_MS) { true } }

        // Basic test for true and false
        mCallback.onAvailable(net)
        mCallback.expect<Available> { true }
        mCallback.onAvailable(net)
        assertFails { mCallback.expect<CallbackEntry>(timeoutMs = SHORT_TIMEOUT_MS) { false } }

        // Try a positive and a negative case
        mCallback.onBlockedStatusChanged(net, true)
        mCallback.expect<CallbackEntry> { cb -> cb is BlockedStatus && cb.blocked }
        mCallback.onCapabilitiesChanged(net, netCaps)
        assertFails { mCallback.expect<CallbackEntry>(timeoutMs = SHORT_TIMEOUT_MS) { cb ->
            cb is CapabilitiesChanged && cb.caps.hasTransport(WIFI)
        } }
    }

    @Test
    fun testExpectCaps() {
        val net = Network(101)
        val netCaps = NetworkCapabilities().addCapability(NOT_METERED).addTransportType(WIFI)
        // Check that expecting capabilitiesThat anything fails when no callback has been received.
        assertFails { mCallback.expectCaps(net, SHORT_TIMEOUT_MS) { true } }

        // Basic test for true and false
        mCallback.onCapabilitiesChanged(net, netCaps)
        mCallback.expectCaps(net) { true }
        mCallback.onCapabilitiesChanged(net, netCaps)
        assertFails { mCallback.expectCaps(net, SHORT_TIMEOUT_MS) { false } }

        // Try a positive and a negative case
        mCallback.onCapabilitiesChanged(net, netCaps)
        mCallback.expectCaps(net) {
            it.hasCapability(NOT_METERED) && it.hasTransport(WIFI) && !it.hasTransport(CELLULAR)
        }
        mCallback.onCapabilitiesChanged(net, netCaps)
        assertFails { mCallback.expectCaps(net, SHORT_TIMEOUT_MS) { it.hasTransport(CELLULAR) } }

        // Try a matching callback on the wrong network
        mCallback.onCapabilitiesChanged(net, netCaps)
        assertFails {
            mCallback.expectCaps(Network(100), SHORT_TIMEOUT_MS) { true }
        }
    }

    @Test
    fun testLinkPropertiesCallbacks() {
        val net = Network(112)
        val linkAddress = LinkAddress("fe80::ace:d00d/64")
        val mtu = 1984
        val linkProps = LinkProperties().apply {
            this.mtu = mtu
            interfaceName = TEST_INTERFACE_NAME
            addLinkAddress(linkAddress)
        }

        // Check that expecting linkPropsThat anything fails when no callback has been received.
        assertFails { mCallback.expect<LinkPropertiesChanged>(net, SHORT_TIMEOUT_MS) { true } }

        // Basic test for true and false
        mCallback.onLinkPropertiesChanged(net, linkProps)
        mCallback.expect<LinkPropertiesChanged>(net) { true }
        mCallback.onLinkPropertiesChanged(net, linkProps)
        assertFails { mCallback.expect<LinkPropertiesChanged>(net, SHORT_TIMEOUT_MS) { false } }

        // Try a positive and negative case
        mCallback.onLinkPropertiesChanged(net, linkProps)
        mCallback.expect<LinkPropertiesChanged>(net) {
            it.lp.interfaceName == TEST_INTERFACE_NAME &&
                    it.lp.linkAddresses.contains(linkAddress) &&
                    it.lp.mtu == mtu
        }
        mCallback.onLinkPropertiesChanged(net, linkProps)
        assertFails { mCallback.expect<LinkPropertiesChanged>(net, SHORT_TIMEOUT_MS) {
            it.lp.interfaceName != TEST_INTERFACE_NAME
        } }

        // Try a matching callback on the wrong network
        mCallback.onLinkPropertiesChanged(net, linkProps)
        assertFails { mCallback.expect<LinkPropertiesChanged>(Network(114), SHORT_TIMEOUT_MS) {
            it.lp.interfaceName == TEST_INTERFACE_NAME
        } }
    }

    @Test
    fun testExpect() {
        val net = Network(103)
        // Test expectCallback fails when nothing was sent.
        assertFails { mCallback.expect<BlockedStatus>(net, SHORT_TIMEOUT_MS) }

        // Test onAvailable is seen and can be expected
        mCallback.onAvailable(net)
        mCallback.expect<Available>(net, SHORT_TIMEOUT_MS)

        // Test onAvailable won't return calls with a different network
        mCallback.onAvailable(Network(106))
        assertFails { mCallback.expect<Available>(net, SHORT_TIMEOUT_MS) }

        // Test onAvailable won't return calls with a different callback
        mCallback.onAvailable(net)
        assertFails { mCallback.expect<BlockedStatus>(net, SHORT_TIMEOUT_MS) }
    }

    @Test
    fun testAllExpectOverloads() {
        // This test should never run, it only checks that all overloads exist and build
        assumeTrue(false)
        val hn = object : TestableNetworkCallback.HasNetwork { override val network = ANY_NETWORK }

        // Method with all arguments (version that takes a Network)
        mCallback.expect(AVAILABLE, ANY_NETWORK, 10, "error") { true }

        // Java overloads omitting one argument. One line for omitting each argument, in positional
        // order. Versions that take a Network.
        mCallback.expect(AVAILABLE, 10, "error") { true }
        mCallback.expect(AVAILABLE, ANY_NETWORK, "error") { true }
        mCallback.expect(AVAILABLE, ANY_NETWORK, 10) { true }
        mCallback.expect(AVAILABLE, ANY_NETWORK, 10, "error")

        // Java overloads for omitting two arguments. One line for omitting each pair of arguments.
        // Versions that take a Network.
        mCallback.expect(AVAILABLE, "error") { true }
        mCallback.expect(AVAILABLE, 10) { true }
        mCallback.expect(AVAILABLE, 10, "error")
        mCallback.expect(AVAILABLE, ANY_NETWORK) { true }
        mCallback.expect(AVAILABLE, ANY_NETWORK, "error")
        mCallback.expect(AVAILABLE, ANY_NETWORK, 10)

        // Java overloads for omitting three arguments. One line for each remaining argument.
        // Versions that take a Network.
        mCallback.expect(AVAILABLE) { true }
        mCallback.expect(AVAILABLE, "error")
        mCallback.expect(AVAILABLE, 10)
        mCallback.expect(AVAILABLE, ANY_NETWORK)

        // Java overload for omitting all four arguments.
        mCallback.expect(AVAILABLE)

        // Same orders as above, but versions that take a HasNetwork. Except overloads that
        // were already tested because they omitted the Network argument
        mCallback.expect(AVAILABLE, hn, 10, "error") { true }
        mCallback.expect(AVAILABLE, hn, "error") { true }
        mCallback.expect(AVAILABLE, hn, 10) { true }
        mCallback.expect(AVAILABLE, hn, 10, "error")

        mCallback.expect(AVAILABLE, hn) { true }
        mCallback.expect(AVAILABLE, hn, "error")
        mCallback.expect(AVAILABLE, hn, 10)

        mCallback.expect(AVAILABLE, hn)

        // Same as above but for reified versions.
        mCallback.expect<Available>(ANY_NETWORK, 10, "error") { true }
        mCallback.expect<Available>(timeoutMs = 10, errorMsg = "error") { true }
        mCallback.expect<Available>(network = ANY_NETWORK, errorMsg = "error") { true }
        mCallback.expect<Available>(network = ANY_NETWORK, timeoutMs = 10) { true }
        mCallback.expect<Available>(network = ANY_NETWORK, timeoutMs = 10, errorMsg = "error")

        mCallback.expect<Available>(errorMsg = "error") { true }
        mCallback.expect<Available>(timeoutMs = 10) { true }
        mCallback.expect<Available>(timeoutMs = 10, errorMsg = "error")
        mCallback.expect<Available>(network = ANY_NETWORK) { true }
        mCallback.expect<Available>(network = ANY_NETWORK, errorMsg = "error")
        mCallback.expect<Available>(network = ANY_NETWORK, timeoutMs = 10)

        mCallback.expect<Available> { true }
        mCallback.expect<Available>(errorMsg = "error")
        mCallback.expect<Available>(timeoutMs = 10)
        mCallback.expect<Available>(network = ANY_NETWORK)
        mCallback.expect<Available>()

        mCallback.expect<Available>(hn, 10, "error") { true }
        mCallback.expect<Available>(network = hn, errorMsg = "error") { true }
        mCallback.expect<Available>(network = hn, timeoutMs = 10) { true }
        mCallback.expect<Available>(network = hn, timeoutMs = 10, errorMsg = "error")

        mCallback.expect<Available>(network = hn) { true }
        mCallback.expect<Available>(network = hn, errorMsg = "error")
        mCallback.expect<Available>(network = hn, timeoutMs = 10)

        mCallback.expect<Available>(network = hn)
    }

    @Test
    fun testExpectClass() {
        val net = Network(1)
        mCallback.onAvailable(net)
        assertFails { mCallback.expect(LOST, net) }
    }

    @Test
    fun testPoll() {
        assertNull(mCallback.poll(SHORT_TIMEOUT_MS))
        TNCInterpreter.interpretTestSpec(initial = mCallback, lineShift = 1,
                threadTransform = { cb -> cb.createLinkedCopy() }, spec = """
            sleep; onAvailable(133)    | poll(2) = Available(133) time 1..4
                                       | poll(1) = null
            onCapabilitiesChanged(108) | poll(1) = CapabilitiesChanged(108) time 0..3
            onBlockedStatus(199)       | poll(1) = BlockedStatus(199) time 0..3
        """)
    }

    @Test
    fun testEventuallyExpect() {
        // TODO: Current test does not verify the inline one. Also verify the behavior after
        // aligning two eventuallyExpect()
        val net1 = Network(100)
        val net2 = Network(101)
        mCallback.onAvailable(net1)
        mCallback.onCapabilitiesChanged(net1, NetworkCapabilities())
        mCallback.onLinkPropertiesChanged(net1, LinkProperties())
        mCallback.eventuallyExpect(LINK_PROPERTIES_CHANGED) {
            net1.equals(it.network)
        }
        // No further new callback. Expect no callback.
        assertFails { mCallback.eventuallyExpect(LINK_PROPERTIES_CHANGED, SHORT_TIMEOUT_MS) }

        // Verify no predicate set.
        mCallback.onAvailable(net2)
        mCallback.onLinkPropertiesChanged(net2, LinkProperties())
        mCallback.onBlockedStatusChanged(net1, false)
        mCallback.eventuallyExpect(BLOCKED_STATUS) { net1.equals(it.network) }
        // Verify no callback received if the callback does not happen.
        assertFails { mCallback.eventuallyExpect(LOSING, SHORT_TIMEOUT_MS) }
    }

    @Test
    fun testEventuallyExpectOnMultiThreads() {
        TNCInterpreter.interpretTestSpec(initial = mCallback, lineShift = 1,
                threadTransform = { cb -> cb.createLinkedCopy() }, spec = """
                onAvailable(100)                   | eventually(CapabilitiesChanged(100), 1) fails
                sleep ; onCapabilitiesChanged(100) | eventually(CapabilitiesChanged(100), 3)
                onAvailable(101) ; onBlockedStatus(101) | eventually(BlockedStatus(100), 2) fails
                onSuspended(100) ; sleep ; onLost(100)  | eventually(Lost(100), 3)
        """)
    }
}

private object TNCInterpreter : ConcurrentInterpreter<TestableNetworkCallback>(interpretTable)

val EntryList = CallbackEntry::class.sealedSubclasses.map { it.simpleName }.joinToString("|")
private fun callbackEntryFromString(name: String): KClass<out CallbackEntry> {
    return CallbackEntry::class.sealedSubclasses.first { it.simpleName == name }
}

@SuppressLint("NewApi") // Uses hidden APIs, which the linter would identify as missing APIs.
private val interpretTable = listOf<InterpretMatcher<TestableNetworkCallback>>(
    // Interpret "Available(xx)" as "call to onAvailable with netId xx", and likewise for
    // all callback types. This is implemented above by enumerating the subclasses of
    // CallbackEntry and reading their simpleName.
    Regex("""(.*)\s+=\s+($EntryList)\((\d+)\)""") to { i, cb, t ->
        val record = i.interpret(t.strArg(1), cb)
        assertTrue(callbackEntryFromString(t.strArg(2)).isInstance(record))
        // Strictly speaking testing for is CallbackEntry is useless as it's been tested above
        // but the compiler can't figure things out from the isInstance call. It does understand
        // from the assertTrue(is CallbackEntry) that this is true, which allows to access
        // the 'network' member below.
        assertTrue(record is CallbackEntry)
        assertEquals(record.network.netId, t.intArg(3))
    },
    // Interpret "onAvailable(xx)" as calling "onAvailable" with a netId of xx, and likewise for
    // all callback types. NetworkCapabilities and LinkProperties just get an empty object
    // as their argument. Losing gets the default linger timer. Blocked gets false.
    Regex("""on($EntryList)\((\d+)\)""") to { i, cb, t ->
        val net = Network(t.intArg(2))
        when (t.strArg(1)) {
            "Available" -> cb.onAvailable(net)
            // PreCheck not used in tests. Add it here if it becomes useful.
            "CapabilitiesChanged" -> cb.onCapabilitiesChanged(net, NetworkCapabilities())
            "LinkPropertiesChanged" -> cb.onLinkPropertiesChanged(net, LinkProperties())
            "Suspended" -> cb.onNetworkSuspended(net)
            "Resumed" -> cb.onNetworkResumed(net)
            "Losing" -> cb.onLosing(net, DEFAULT_LINGER_DELAY_MS)
            "Lost" -> cb.onLost(net)
            "Unavailable" -> cb.onUnavailable()
            "BlockedStatus" -> cb.onBlockedStatusChanged(net, false)
            else -> fail("Unknown callback type")
        }
    },
    Regex("""poll\((\d+)\)""") to { i, cb, t -> cb.poll(t.timeArg(1)) },
    // Interpret "eventually(Available(xx), timeout)" as calling eventuallyExpect that expects
    // CallbackEntry.AVAILABLE with netId of xx within timeout*INTERPRET_TIME_UNIT timeout, and
    // likewise for all callback types.
    Regex("""eventually\(($EntryList)\((\d+)\),\s+(\d+)\)""") to { i, cb, t ->
        val net = Network(t.intArg(2))
        val timeout = t.timeArg(3)
        when (t.strArg(1)) {
            "Available" -> cb.eventuallyExpect(AVAILABLE, timeout) { net == it.network }
            "Suspended" -> cb.eventuallyExpect(SUSPENDED, timeout) { net == it.network }
            "Resumed" -> cb.eventuallyExpect(RESUMED, timeout) { net == it.network }
            "Losing" -> cb.eventuallyExpect(LOSING, timeout) { net == it.network }
            "Lost" -> cb.eventuallyExpect(LOST, timeout) { net == it.network }
            "Unavailable" -> cb.eventuallyExpect(UNAVAILABLE, timeout) { net == it.network }
            "BlockedStatus" -> cb.eventuallyExpect(BLOCKED_STATUS, timeout) { net == it.network }
            "CapabilitiesChanged" ->
                cb.eventuallyExpect(NETWORK_CAPS_UPDATED, timeout) { net == it.network }
            "LinkPropertiesChanged" ->
                cb.eventuallyExpect(LINK_PROPERTIES_CHANGED, timeout) { net == it.network }
            else -> fail("Unknown callback type")
        }
    }
)
