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
 * limitations under the License.
 */

package com.android.testutils

import android.net.ConnectivityManager.NetworkCallback
import android.net.LinkProperties
import android.net.LocalNetworkInfo
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED
import android.util.Log
import com.android.net.module.util.ArrayTrackRecord
import com.android.testutils.RecorderCallback.CallbackEntry.Available
import com.android.testutils.RecorderCallback.CallbackEntry.BlockedStatus
import com.android.testutils.RecorderCallback.CallbackEntry.BlockedStatusInt
import com.android.testutils.RecorderCallback.CallbackEntry.CapabilitiesChanged
import com.android.testutils.RecorderCallback.CallbackEntry.LinkPropertiesChanged
import com.android.testutils.RecorderCallback.CallbackEntry.LocalInfoChanged
import com.android.testutils.RecorderCallback.CallbackEntry.Losing
import com.android.testutils.RecorderCallback.CallbackEntry.Lost
import com.android.testutils.RecorderCallback.CallbackEntry.Resumed
import com.android.testutils.RecorderCallback.CallbackEntry.Suspended
import com.android.testutils.RecorderCallback.CallbackEntry.Unavailable
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.fail

object NULL_NETWORK : Network(-1)
object ANY_NETWORK : Network(-2)
fun anyNetwork() = ANY_NETWORK

open class RecorderCallback private constructor(
    private val backingRecord: ArrayTrackRecord<CallbackEntry>
) : NetworkCallback() {
    public constructor() : this(ArrayTrackRecord())
    protected constructor(src: RecorderCallback?) : this(src?.backingRecord ?: ArrayTrackRecord())

    private val TAG = this::class.simpleName

    sealed class CallbackEntry {
        // To get equals(), hashcode(), componentN() etc for free, the child classes of
        // this class are data classes. But while data classes can inherit from other classes,
        // they may only have visible members in the constructors, so they couldn't declare
        // a constructor with a non-val arg to pass to CallbackEntry. Instead, force all
        // subclasses to implement a `network' property, which can be done in a data class
        // constructor by specifying override.
        abstract val network: Network

        data class Available(override val network: Network) : CallbackEntry()
        data class CapabilitiesChanged(
            override val network: Network,
            val caps: NetworkCapabilities
        ) : CallbackEntry()
        data class LinkPropertiesChanged(
            override val network: Network,
            val lp: LinkProperties
        ) : CallbackEntry()
        data class LocalInfoChanged(
            override val network: Network,
            val info: LocalNetworkInfo
        ) : CallbackEntry()
        data class Suspended(override val network: Network) : CallbackEntry()
        data class Resumed(override val network: Network) : CallbackEntry()
        data class Losing(override val network: Network, val maxMsToLive: Int) : CallbackEntry()
        data class Lost(override val network: Network) : CallbackEntry()
        data class Unavailable private constructor(
            override val network: Network
        ) : CallbackEntry() {
            constructor() : this(NULL_NETWORK)
        }
        data class BlockedStatus(
            override val network: Network,
            val blocked: Boolean
        ) : CallbackEntry()
        data class BlockedStatusInt(
            override val network: Network,
            val reason: Int
        ) : CallbackEntry()
        // Convenience constants for expecting a type
        companion object {
            @JvmField
            val AVAILABLE = Available::class
            @JvmField
            val NETWORK_CAPS_UPDATED = CapabilitiesChanged::class
            @JvmField
            val LINK_PROPERTIES_CHANGED = LinkPropertiesChanged::class
            @JvmField
            val LOCAL_INFO_CHANGED = LocalInfoChanged::class
            @JvmField
            val SUSPENDED = Suspended::class
            @JvmField
            val RESUMED = Resumed::class
            @JvmField
            val LOSING = Losing::class
            @JvmField
            val LOST = Lost::class
            @JvmField
            val UNAVAILABLE = Unavailable::class
            @JvmField
            val BLOCKED_STATUS = BlockedStatus::class
            @JvmField
            val BLOCKED_STATUS_INT = BlockedStatusInt::class
        }
    }

    val history = backingRecord.newReadHead()
    val mark get() = history.mark

    override fun onAvailable(network: Network) {
        Log.d(TAG, "onAvailable $network")
        history.add(Available(network))
    }

    // PreCheck is not used in the tests today. For backward compatibility with existing tests that
    // expect the callbacks not to record this, do not listen to PreCheck here.

    override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
        Log.d(TAG, "onCapabilitiesChanged $network $caps")
        history.add(CapabilitiesChanged(network, caps))
    }

    override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) {
        Log.d(TAG, "onLinkPropertiesChanged $network $lp")
        history.add(LinkPropertiesChanged(network, lp))
    }

    override fun onLocalNetworkInfoChanged(network: Network, info: LocalNetworkInfo) {
        Log.d(TAG, "onLocalNetworkInfoChanged $network $info")
        history.add(LocalInfoChanged(network, info))
    }

    override fun onBlockedStatusChanged(network: Network, blocked: Boolean) {
        Log.d(TAG, "onBlockedStatusChanged $network $blocked")
        history.add(BlockedStatus(network, blocked))
    }

    // Cannot do:
    // fun onBlockedStatusChanged(network: Network, blocked: Int) {
    // because on S, that needs to be "override fun", and on R, that cannot be "override fun".
    override fun onNetworkSuspended(network: Network) {
        Log.d(TAG, "onNetworkSuspended $network $network")
        history.add(Suspended(network))
    }

    override fun onNetworkResumed(network: Network) {
        Log.d(TAG, "$network onNetworkResumed $network")
        history.add(Resumed(network))
    }

    override fun onLosing(network: Network, maxMsToLive: Int) {
        Log.d(TAG, "onLosing $network $maxMsToLive")
        history.add(Losing(network, maxMsToLive))
    }

    override fun onLost(network: Network) {
        Log.d(TAG, "onLost $network")
        history.add(Lost(network))
    }

    override fun onUnavailable() {
        Log.d(TAG, "onUnavailable")
        history.add(Unavailable())
    }
}

private const val DEFAULT_TIMEOUT = 30_000L // ms
private const val DEFAULT_NO_CALLBACK_TIMEOUT = 200L // ms
private val NOOP = Runnable {}

/**
 * See comments on the public constructor below for a description of the arguments.
 */
open class TestableNetworkCallback private constructor(
    src: TestableNetworkCallback?,
    val defaultTimeoutMs: Long = DEFAULT_TIMEOUT,
    val defaultNoCallbackTimeoutMs: Long = DEFAULT_NO_CALLBACK_TIMEOUT,
    val waiterFunc: Runnable = NOOP // "() -> Unit" would forbid calling with a void func from Java
) : RecorderCallback(src) {
    /**
     * Construct a testable network callback.
     * @param timeoutMs the default timeout for expecting a callback. Default 30 seconds. This
     *                  should be long in most cases, because the success case doesn't incur
     *                  the wait.
     * @param noCallbackTimeoutMs the timeout for expecting that no callback is received. Default
     *                            200ms. Because the success case does incur the timeout, this
     *                            should be short in most cases, but not so short as to frequently
     *                            time out before an incorrect callback is received.
     * @param waiterFunc a function to use before asserting no callback. For some specific tests,
     *                   it is useful to run test-specific code before asserting no callback to
     *                   increase the likelihood that a spurious callback is correctly detected.
     *                   As an example, a unit test using mock loopers may want to use this to
     *                   make sure the loopers are drained before asserting no callback, since
     *                   one of them may cause a callback to be called. @see ConnectivityServiceTest
     *                   for such an example.
     */
    @JvmOverloads
    constructor(
        timeoutMs: Long = DEFAULT_TIMEOUT,
        noCallbackTimeoutMs: Long = DEFAULT_NO_CALLBACK_TIMEOUT,
        waiterFunc: Runnable = NOOP
    ) : this(null, timeoutMs, noCallbackTimeoutMs, waiterFunc)

    fun createLinkedCopy() = TestableNetworkCallback(
            this, defaultTimeoutMs, defaultNoCallbackTimeoutMs, waiterFunc)

    // The last available network, or null if any network was lost since the last call to
    // onAvailable. TODO : fix this by fixing the tests that rely on this behavior
    val lastAvailableNetwork: Network?
        get() = when (val it = history.lastOrNull { it is Available || it is Lost }) {
            is Available -> it.network
            else -> null
        }

    /**
     * Get the next callback or null if timeout.
     *
     * With no argument, this method waits out the default timeout. To wait forever, pass
     * Long.MAX_VALUE.
     */
    @JvmOverloads
    fun poll(timeoutMs: Long = defaultTimeoutMs, predicate: (CallbackEntry) -> Boolean = { true }) =
            history.poll(timeoutMs, predicate)

    /*****
     * expect family of methods.
     * These methods fetch the next callback and assert it matches the conditions : type,
     * passed predicate. If no callback is received within the timeout, these methods fail.
     */
    @JvmOverloads
    fun <T : CallbackEntry> expect(
        type: KClass<T>,
        network: Network = ANY_NETWORK,
        timeoutMs: Long = defaultTimeoutMs,
        errorMsg: String? = null,
        test: (T) -> Boolean = { true }
    ) = expect<CallbackEntry>(network, timeoutMs, errorMsg) {
        if (type.isInstance(it)) {
            test(it as T) // Cast can't fail since type.isInstance(it) and type: KClass<T>
        } else {
            fail("Expected callback ${type.simpleName}, got $it")
        }
    } as T

    @JvmOverloads
    fun <T : CallbackEntry> expect(
        type: KClass<T>,
        network: HasNetwork,
        timeoutMs: Long = defaultTimeoutMs,
        errorMsg: String? = null,
        test: (T) -> Boolean = { true }
    ) = expect(type, network.network, timeoutMs, errorMsg, test)

    // Java needs an explicit overload to let it omit arguments in the middle, so define these
    // here. Note that @JvmOverloads give us the versions without the last arguments too, so
    // there is no need to explicitly define versions without the test predicate.
    // Without |network|
    @JvmOverloads
    fun <T : CallbackEntry> expect(
        type: KClass<T>,
        timeoutMs: Long,
        errorMsg: String?,
        test: (T) -> Boolean = { true }
    ) = expect(type, ANY_NETWORK, timeoutMs, errorMsg, test)

    // Without |timeout|, in Network and HasNetwork versions
    @JvmOverloads
    fun <T : CallbackEntry> expect(
        type: KClass<T>,
        network: Network,
        errorMsg: String?,
        test: (T) -> Boolean = { true }
    ) = expect(type, network, defaultTimeoutMs, errorMsg, test)

    @JvmOverloads
    fun <T : CallbackEntry> expect(
        type: KClass<T>,
        network: HasNetwork,
        errorMsg: String?,
        test: (T) -> Boolean = { true }
    ) = expect(type, network.network, defaultTimeoutMs, errorMsg, test)

    // Without |errorMsg|, in Network and HasNetwork versions
    @JvmOverloads
    fun <T : CallbackEntry> expect(
        type: KClass<T>,
        network: Network,
        timeoutMs: Long,
        test: (T) -> Boolean
    ) = expect(type, network, timeoutMs, null, test)

    @JvmOverloads
    fun <T : CallbackEntry> expect(
        type: KClass<T>,
        network: HasNetwork,
        timeoutMs: Long,
        test: (T) -> Boolean
    ) = expect(type, network.network, timeoutMs, null, test)

    // Without |network| or |timeout|
    @JvmOverloads
    fun <T : CallbackEntry> expect(
        type: KClass<T>,
        errorMsg: String?,
        test: (T) -> Boolean = { true }
    ) = expect(type, ANY_NETWORK, defaultTimeoutMs, errorMsg, test)

    // Without |network| or |errorMsg|
    @JvmOverloads
    fun <T : CallbackEntry> expect(
        type: KClass<T>,
        timeoutMs: Long,
        test: (T) -> Boolean = { true }
    ) = expect(type, ANY_NETWORK, timeoutMs, null, test)

    // Without |timeout| or |errorMsg|, in Network and HasNetwork versions
    @JvmOverloads
    fun <T : CallbackEntry> expect(
        type: KClass<T>,
        network: Network,
        test: (T) -> Boolean
    ) = expect(type, network, defaultTimeoutMs, null, test)

    @JvmOverloads
    fun <T : CallbackEntry> expect(
        type: KClass<T>,
        network: HasNetwork,
        test: (T) -> Boolean
    ) = expect(type, network.network, defaultTimeoutMs, null, test)

    // Without |network| or |timeout| or |errorMsg|
    @JvmOverloads
    fun <T : CallbackEntry> expect(
        type: KClass<T>,
        test: (T) -> Boolean
    ) = expect(type, ANY_NETWORK, defaultTimeoutMs, null, test)

    // Kotlin reified versions. Don't call methods above, or the predicate would need to be noinline
    inline fun <reified T : CallbackEntry> expect(
        network: Network = ANY_NETWORK,
        timeoutMs: Long = defaultTimeoutMs,
        errorMsg: String? = null,
        test: (T) -> Boolean = { true }
    ) = (poll(timeoutMs) ?: fail("Did not receive ${T::class.simpleName} after ${timeoutMs}ms"))
            .also {
                if (it !is T) fail("Expected callback ${T::class.simpleName}, got $it")
                if (ANY_NETWORK !== network && it.network != network) {
                    fail("Expected network $network for callback : $it")
                }
                if (!test(it)) {
                    fail("${errorMsg ?: "Callback doesn't match predicate"} : $it")
                }
            } as T

    inline fun <reified T : CallbackEntry> expect(
        network: HasNetwork,
        timeoutMs: Long = defaultTimeoutMs,
        errorMsg: String? = null,
        test: (T) -> Boolean = { true }
    ) = expect(network.network, timeoutMs, errorMsg, test)

    /*****
     * assertNoCallback family of methods.
     * These methods make sure that no callback that matches the predicate was received.
     * If no predicate is given, they make sure that no callback at all was received.
     * These methods run the waiter func given in the constructor if any.
     */
    @JvmOverloads
    fun assertNoCallback(
        timeoutMs: Long = defaultNoCallbackTimeoutMs,
        valid: (CallbackEntry) -> Boolean = { true }
    ) {
        waiterFunc.run()
        history.poll(timeoutMs) { valid(it) }?.let { fail("Expected no callback but got $it") }
    }

    fun assertNoCallback(valid: (CallbackEntry) -> Boolean) =
            assertNoCallback(defaultNoCallbackTimeoutMs, valid)

    /*****
     * eventuallyExpect family of methods.
     * These methods make sure a callback that matches the type/predicate is received eventually.
     * Any callback of the wrong type, or doesn't match the optional predicate, is ignored.
     * They fail if no callback matching the predicate is received within the timeout.
     */
    inline fun <reified T : CallbackEntry> eventuallyExpect(
        timeoutMs: Long = defaultTimeoutMs,
        from: Int = mark,
        crossinline predicate: (T) -> Boolean = { true }
    ): T = history.poll(timeoutMs, from) { it is T && predicate(it) }.also {
        assertNotNull(it, "Callback ${T::class} not received within ${timeoutMs}ms. " +
                "Got ${history.backtrace()}")
    } as T

    @JvmOverloads
    fun <T : CallbackEntry> eventuallyExpect(
        type: KClass<T>,
        timeoutMs: Long = defaultTimeoutMs,
        predicate: (cb: T) -> Boolean = { true }
    ) = history.poll(timeoutMs) { type.java.isInstance(it) && predicate(it as T) }.also {
        assertNotNull(it, "Callback ${type.java} not received within ${timeoutMs}ms. " +
                "Got ${history.backtrace()}")
    } as T

    fun <T : CallbackEntry> eventuallyExpect(
        type: KClass<T>,
        timeoutMs: Long = defaultTimeoutMs,
        from: Int = mark,
        predicate: (cb: T) -> Boolean = { true }
    ) = history.poll(timeoutMs, from) { type.java.isInstance(it) && predicate(it as T) }.also {
        assertNotNull(it, "Callback ${type.java} not received within ${timeoutMs}ms. " +
                "Got ${history.backtrace()}")
    } as T

    // Expects onAvailable and the callbacks that follow it. These are:
    // - onSuspended, iff the network was suspended when the callbacks fire.
    // - onCapabilitiesChanged.
    // - onLinkPropertiesChanged.
    // - onBlockedStatusChanged.
    //
    // @param network the network to expect the callbacks on.
    // @param suspended whether to expect a SUSPENDED callback.
    // @param validated the expected value of the VALIDATED capability in the
    //        onCapabilitiesChanged callback.
    // @param tmt how long to wait for the callbacks.
    @JvmOverloads
    fun expectAvailableCallbacks(
        net: Network,
        suspended: Boolean = false,
        validated: Boolean? = true,
        blocked: Boolean = false,
        upstream: Network? = null,
        tmt: Long = defaultTimeoutMs
    ) {
        expectAvailableCallbacksCommon(net, suspended, validated, upstream, tmt)
        expect<BlockedStatus>(net, tmt) { it.blocked == blocked }
    }

    // For backward compatibility, add a method that allows callers to specify a timeout but
    // no upstream.
    fun expectAvailableCallbacks(
        net: Network,
        suspended: Boolean = false,
        validated: Boolean? = true,
        blocked: Boolean = false,
        tmt: Long = defaultTimeoutMs
    ) = expectAvailableCallbacks(net, suspended, validated, blocked, upstream = null, tmt = tmt)

    fun expectAvailableCallbacks(
        net: Network,
        suspended: Boolean,
        validated: Boolean,
        blockedReason: Int,
        upstream: Network? = null,
        tmt: Long
    ) {
        expectAvailableCallbacksCommon(net, suspended, validated, upstream, tmt)
        expect<BlockedStatusInt>(net) { it.reason == blockedReason }
    }

    // For backward compatibility, add a method that allows callers to specify a timeout but
    // no upstream.
    fun expectAvailableCallbacks(
            net: Network,
            suspended: Boolean = false,
            validated: Boolean = true,
            blockedReason: Int,
            tmt: Long = defaultTimeoutMs
    ) = expectAvailableCallbacks(net, suspended, validated, blockedReason, upstream = null, tmt)

    private fun expectAvailableCallbacksCommon(
        net: Network,
        suspended: Boolean,
        validated: Boolean?,
        upstream: Network?,
        tmt: Long
    ) {
        expect<Available>(net, tmt)
        if (suspended) {
            expect<Suspended>(net, tmt)
        }
        val caps = expect<CapabilitiesChanged>(net, tmt) {
            validated == null || validated == it.caps.hasCapability(NET_CAPABILITY_VALIDATED)
        }.caps
        expect<LinkPropertiesChanged>(net, tmt)
        if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_LOCAL_NETWORK)) {
            expect<LocalInfoChanged>(net, tmt) { it.info.upstreamNetwork == upstream }
        }
    }

    // Backward compatibility for existing Java code. Use named arguments instead and remove all
    // these when there is no user left.
    fun expectAvailableAndSuspendedCallbacks(
        net: Network,
        validated: Boolean,
        tmt: Long = defaultTimeoutMs
    ) = expectAvailableCallbacks(net, suspended = true, validated = validated, tmt = tmt)

    // Expects the available callbacks (where the onCapabilitiesChanged must contain the
    // VALIDATED capability), plus another onCapabilitiesChanged which is identical to the
    // one we just sent.
    // TODO: this is likely a bug. Fix it and remove this method.
    fun expectAvailableDoubleValidatedCallbacks(net: Network, tmt: Long = defaultTimeoutMs) {
        val mark = history.mark
        expectAvailableCallbacks(net, tmt = tmt)
        val firstCaps = history.poll(tmt, mark) { it is CapabilitiesChanged }
        assertEquals(firstCaps, expect<CapabilitiesChanged>(net, tmt))
    }

    // Expects the available callbacks where the onCapabilitiesChanged must not have validated,
    // then expects another onCapabilitiesChanged that has the validated bit set. This is used
    // when a network connects and satisfies a callback, and then immediately validates.
    fun expectAvailableThenValidatedCallbacks(net: Network, tmt: Long = defaultTimeoutMs) {
        expectAvailableCallbacks(net, validated = false, tmt = tmt)
        expectCaps(net, tmt) { it.hasCapability(NET_CAPABILITY_VALIDATED) }
    }

    fun expectAvailableThenValidatedCallbacks(
        net: Network,
        blockedReason: Int,
        tmt: Long = defaultTimeoutMs
    ) {
        expectAvailableCallbacks(net, validated = false, suspended = false,
                blockedReason = blockedReason, tmt = tmt)
        expectCaps(net, tmt) { it.hasCapability(NET_CAPABILITY_VALIDATED) }
    }

    // Temporary Java compat measure : have MockNetworkAgent implement this so that all existing
    // calls with networkAgent can be routed through here without moving MockNetworkAgent.
    // TODO: clean this up, remove this method.
    interface HasNetwork {
        val network: Network
    }

    @JvmOverloads
    fun expectAvailableCallbacks(
        n: HasNetwork,
        suspended: Boolean,
        validated: Boolean,
        blocked: Boolean,
        upstream: Network? = null,
        timeoutMs: Long
    ) = expectAvailableCallbacks(n.network, suspended, validated, blocked, upstream, timeoutMs)

    fun expectAvailableAndSuspendedCallbacks(n: HasNetwork, expectValidated: Boolean) {
        expectAvailableAndSuspendedCallbacks(n.network, expectValidated)
    }

    fun expectAvailableCallbacksValidated(n: HasNetwork) {
        expectAvailableCallbacks(n.network)
    }

    fun expectAvailableCallbacksValidatedAndBlocked(n: HasNetwork) {
        expectAvailableCallbacks(n.network, blocked = true)
    }

    fun expectAvailableCallbacksUnvalidated(n: HasNetwork) {
        expectAvailableCallbacks(n.network, validated = false)
    }

    fun expectAvailableCallbacksUnvalidatedAndBlocked(n: HasNetwork) {
        expectAvailableCallbacks(n.network, validated = false, blocked = true)
    }

    fun expectAvailableDoubleValidatedCallbacks(n: HasNetwork) {
        expectAvailableDoubleValidatedCallbacks(n.network, defaultTimeoutMs)
    }

    fun expectAvailableThenValidatedCallbacks(n: HasNetwork) {
        expectAvailableThenValidatedCallbacks(n.network, defaultTimeoutMs)
    }

    @JvmOverloads
    fun expectCaps(
        n: HasNetwork,
        tmt: Long = defaultTimeoutMs,
        valid: (NetworkCapabilities) -> Boolean = { true }
    ) = expect<CapabilitiesChanged>(n.network, tmt) { valid(it.caps) }.caps

    @JvmOverloads
    fun expectCaps(
        n: Network,
        tmt: Long = defaultTimeoutMs,
        valid: (NetworkCapabilities) -> Boolean
    ) = expect<CapabilitiesChanged>(n, tmt) { valid(it.caps) }.caps

    fun expectCaps(
        n: HasNetwork,
        valid: (NetworkCapabilities) -> Boolean
    ) = expect<CapabilitiesChanged>(n.network) { valid(it.caps) }.caps

    fun expectCaps(
        tmt: Long,
        valid: (NetworkCapabilities) -> Boolean
    ) = expect<CapabilitiesChanged>(ANY_NETWORK, tmt) { valid(it.caps) }.caps
}
