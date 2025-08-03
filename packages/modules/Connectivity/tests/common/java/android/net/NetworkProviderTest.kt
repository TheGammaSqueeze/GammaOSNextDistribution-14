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

package android.net

import android.app.Instrumentation
import android.content.Context
import android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED
import android.net.NetworkCapabilities.NET_CAPABILITY_TRUSTED
import android.net.NetworkCapabilities.TRANSPORT_TEST
import android.net.NetworkProviderTest.TestNetworkCallback.CallbackEntry.OnUnavailable
import android.net.NetworkProviderTest.TestNetworkProvider.CallbackEntry.OnNetworkRequestWithdrawn
import android.net.NetworkProviderTest.TestNetworkProvider.CallbackEntry.OnNetworkRequested
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import androidx.test.InstrumentationRegistry
import com.android.modules.utils.build.SdkLevel.isAtLeastS
import com.android.net.module.util.ArrayTrackRecord
import com.android.testutils.CompatUtil
import com.android.testutils.ConnectivityModuleTest
import com.android.testutils.DevSdkIgnoreRule
import com.android.testutils.DevSdkIgnoreRule.IgnoreAfter
import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo
import com.android.testutils.DevSdkIgnoreRunner
import com.android.testutils.TestableNetworkOfferCallback
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.fail
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoMoreInteractions

private const val DEFAULT_TIMEOUT_MS = 5000L
private const val DEFAULT_NO_CALLBACK_TIMEOUT_MS = 200L
private val instrumentation: Instrumentation
    get() = InstrumentationRegistry.getInstrumentation()
private val context: Context get() = InstrumentationRegistry.getContext()
private val PROVIDER_NAME = "NetworkProviderTest"

@RunWith(DevSdkIgnoreRunner::class)
@ConnectivityModuleTest
class NetworkProviderTest {
    @Rule @JvmField
    val mIgnoreRule = DevSdkIgnoreRule()
    private val mCm = context.getSystemService(ConnectivityManager::class.java)!!
    private val mHandlerThread = HandlerThread("${javaClass.simpleName} handler thread")

    @Before
    fun setUp() {
        instrumentation.getUiAutomation().adoptShellPermissionIdentity()
        mHandlerThread.start()
    }

    @After
    fun tearDown() {
        mHandlerThread.quitSafely()
        mHandlerThread.join()
        instrumentation.getUiAutomation().dropShellPermissionIdentity()
    }

    private class TestNetworkProvider(context: Context, looper: Looper) :
            NetworkProvider(context, looper, PROVIDER_NAME) {
        private val TAG = this::class.simpleName
        private val seenEvents = ArrayTrackRecord<CallbackEntry>().newReadHead()

        sealed class CallbackEntry {
            data class OnNetworkRequested(
                val request: NetworkRequest,
                val score: Int,
                val id: Int
            ) : CallbackEntry()
            data class OnNetworkRequestWithdrawn(val request: NetworkRequest) : CallbackEntry()
        }

        override fun onNetworkRequested(request: NetworkRequest, score: Int, id: Int) {
            Log.d(TAG, "onNetworkRequested $request, $score, $id")
            seenEvents.add(OnNetworkRequested(request, score, id))
        }

        override fun onNetworkRequestWithdrawn(request: NetworkRequest) {
            Log.d(TAG, "onNetworkRequestWithdrawn $request")
            seenEvents.add(OnNetworkRequestWithdrawn(request))
        }

        inline fun <reified T : CallbackEntry> eventuallyExpectCallbackThat(
            crossinline predicate: (T) -> Boolean
        ) = seenEvents.poll(DEFAULT_TIMEOUT_MS) { it is T && predicate(it) }
                ?: fail("Did not receive callback after ${DEFAULT_TIMEOUT_MS}ms")

        fun assertNoCallback() {
            val cb = seenEvents.poll(DEFAULT_NO_CALLBACK_TIMEOUT_MS)
            if (null != cb) fail("Expected no callback but got $cb")
        }
    }

    private fun createNetworkProvider(ctx: Context = context): TestNetworkProvider {
        return TestNetworkProvider(ctx, mHandlerThread.looper)
    }

    private fun createAndRegisterNetworkProvider(ctx: Context = context) =
        createNetworkProvider(ctx).also {
            assertEquals(it.getProviderId(), NetworkProvider.ID_NONE)
            mCm.registerNetworkProvider(it)
            assertNotEquals(it.getProviderId(), NetworkProvider.ID_NONE)
        }

    // In S+ framework, do not run this test, since the provider will no longer receive
    // onNetworkRequested for every request. Instead, provider needs to
    // call {@code registerNetworkOffer} with the description of networks they
    // might have ability to setup, and expects {@link NetworkOfferCallback#onNetworkNeeded}.
    @IgnoreAfter(Build.VERSION_CODES.R)
    @Test
    fun testOnNetworkRequested() {
        val provider = createAndRegisterNetworkProvider()

        val specifier = CompatUtil.makeTestNetworkSpecifier(
                UUID.randomUUID().toString())
        // Test network is not allowed to be trusted.
        val nr: NetworkRequest = NetworkRequest.Builder()
                .addTransportType(TRANSPORT_TEST)
                .removeCapability(NET_CAPABILITY_TRUSTED)
                .setNetworkSpecifier(specifier)
                .build()
        val cb = ConnectivityManager.NetworkCallback()
        mCm.requestNetwork(nr, cb)
        provider.eventuallyExpectCallbackThat<OnNetworkRequested>() { callback ->
            callback.request.getNetworkSpecifier() == specifier &&
            callback.request.hasTransport(TRANSPORT_TEST)
        }

        val initialScore = 40
        val updatedScore = 60
        val nc = NetworkCapabilities().apply {
                addTransportType(NetworkCapabilities.TRANSPORT_TEST)
                removeCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
                removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED)
                addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)
                addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                setNetworkSpecifier(specifier)
        }
        val lp = LinkProperties()
        val config = NetworkAgentConfig.Builder().build()
        val agent = object : NetworkAgent(context, mHandlerThread.looper, "TestAgent", nc, lp,
                initialScore, config, provider) {}
        agent.register()
        agent.markConnected()

        provider.eventuallyExpectCallbackThat<OnNetworkRequested>() { callback ->
            callback.request.getNetworkSpecifier() == specifier &&
            callback.score == initialScore &&
            callback.id == agent.providerId
        }

        agent.sendNetworkScore(updatedScore)
        provider.eventuallyExpectCallbackThat<OnNetworkRequested>() { callback ->
            callback.request.getNetworkSpecifier() == specifier &&
            callback.score == updatedScore &&
            callback.id == agent.providerId
        }

        mCm.unregisterNetworkCallback(cb)
        provider.eventuallyExpectCallbackThat<OnNetworkRequestWithdrawn>() { callback ->
            callback.request.getNetworkSpecifier() == specifier &&
            callback.request.hasTransport(TRANSPORT_TEST)
        }
        mCm.unregisterNetworkProvider(provider)
        // Provider id should be ID_NONE after unregister network provider
        assertEquals(provider.getProviderId(), NetworkProvider.ID_NONE)
        // unregisterNetworkProvider should not crash even if it's called on an
        // already unregistered provider.
        mCm.unregisterNetworkProvider(provider)
    }

    // Mainline module can't use internal HandlerExecutor, so add an identical executor here.
    // TODO: Refactor with the one in MultiNetworkPolicyTracker.
    private class HandlerExecutor(private val handler: Handler) : Executor {
        public override fun execute(command: Runnable) {
            if (!handler.post(command)) {
                throw RejectedExecutionException(handler.toString() + " is shutting down")
            }
        }
    }

    @IgnoreUpTo(Build.VERSION_CODES.R)
    @Test
    fun testRegisterNetworkOffer() {
        val provider = createAndRegisterNetworkProvider()
        val provider2 = createAndRegisterNetworkProvider()

        // Prepare the materials which will be used to create different offers.
        val specifier1 = CompatUtil.makeTestNetworkSpecifier("TEST-SPECIFIER-1")
        val specifier2 = CompatUtil.makeTestNetworkSpecifier("TEST-SPECIFIER-2")
        val scoreWeaker = NetworkScore.Builder().build()
        val scoreStronger = NetworkScore.Builder().setTransportPrimary(true).build()
        val ncFilter1 = NetworkCapabilities.Builder().addTransportType(TRANSPORT_TEST)
                .setNetworkSpecifier(specifier1).build()
        val ncFilter2 = NetworkCapabilities.Builder().addTransportType(TRANSPORT_TEST)
                .addCapability(NET_CAPABILITY_NOT_VCN_MANAGED)
                .setNetworkSpecifier(specifier1).build()
        val ncFilter3 = NetworkCapabilities.Builder().addTransportType(TRANSPORT_TEST)
                .setNetworkSpecifier(specifier2).build()
        val ncFilter4 = NetworkCapabilities.Builder().addTransportType(TRANSPORT_TEST)
                .setNetworkSpecifier(specifier2).build()

        // Make 4 offers, where 1 doesn't have NOT_VCN, 2 has NOT_VCN, 3 is similar to 1 but with
        // different specifier, and 4 is also similar to 1 but with different provider.
        val offerCallback1 = TestableNetworkOfferCallback(
                DEFAULT_TIMEOUT_MS, DEFAULT_NO_CALLBACK_TIMEOUT_MS)
        val offerCallback2 = TestableNetworkOfferCallback(
                DEFAULT_TIMEOUT_MS, DEFAULT_NO_CALLBACK_TIMEOUT_MS)
        val offerCallback3 = TestableNetworkOfferCallback(
                DEFAULT_TIMEOUT_MS, DEFAULT_NO_CALLBACK_TIMEOUT_MS)
        val offerCallback4 = TestableNetworkOfferCallback(
                DEFAULT_TIMEOUT_MS, DEFAULT_NO_CALLBACK_TIMEOUT_MS)
        provider.registerNetworkOffer(scoreWeaker, ncFilter1,
                HandlerExecutor(mHandlerThread.threadHandler), offerCallback1)
        provider.registerNetworkOffer(scoreStronger, ncFilter2,
                HandlerExecutor(mHandlerThread.threadHandler), offerCallback2)
        provider.registerNetworkOffer(scoreWeaker, ncFilter3,
                HandlerExecutor(mHandlerThread.threadHandler), offerCallback3)
        provider2.registerNetworkOffer(scoreWeaker, ncFilter4,
                HandlerExecutor(mHandlerThread.threadHandler), offerCallback4)
        // Unlike Android R, Android S+ provider will only receive interested requests via offer
        // callback. Verify that the callbacks do not see any existing request such as default
        // requests.
        offerCallback1.assertNoCallback()
        offerCallback2.assertNoCallback()
        offerCallback3.assertNoCallback()
        offerCallback4.assertNoCallback()

        // File a request with specifier but without NOT_VCN, verify network is needed for callback
        // with the same specifier.
        val nrNoNotVcn: NetworkRequest = NetworkRequest.Builder()
                .addTransportType(TRANSPORT_TEST)
                // Test network is not allowed to be trusted.
                .removeCapability(NET_CAPABILITY_TRUSTED)
                .setNetworkSpecifier(specifier1)
                .build()
        val cb1 = ConnectivityManager.NetworkCallback()
        mCm.requestNetwork(nrNoNotVcn, cb1)
        offerCallback1.expectOnNetworkNeeded(ncFilter1)
        offerCallback2.expectOnNetworkNeeded(ncFilter2)
        offerCallback3.assertNoCallback()
        offerCallback4.assertNoCallback()

        mCm.unregisterNetworkCallback(cb1)
        offerCallback1.expectOnNetworkUnneeded(ncFilter1)
        offerCallback2.expectOnNetworkUnneeded(ncFilter2)
        offerCallback3.assertNoCallback()
        offerCallback4.assertNoCallback()

        // File a request without specifier but with NOT_VCN, verify network is needed for offer
        // with NOT_VCN.
        val nrNotVcn: NetworkRequest = NetworkRequest.Builder()
                .addTransportType(TRANSPORT_TEST)
                .addCapability(NET_CAPABILITY_NOT_VCN_MANAGED)
                // Test network is not allowed to be trusted.
                .removeCapability(NET_CAPABILITY_TRUSTED)
                .build()
        val cb2 = ConnectivityManager.NetworkCallback()
        mCm.requestNetwork(nrNotVcn, cb2)
        offerCallback1.assertNoCallback()
        offerCallback2.expectOnNetworkNeeded(ncFilter2)
        offerCallback3.assertNoCallback()
        offerCallback4.assertNoCallback()

        // Upgrade offer 3 & 4 to satisfy previous request and then verify they are also needed.
        ncFilter3.addCapability(NET_CAPABILITY_NOT_VCN_MANAGED)
        provider.registerNetworkOffer(scoreWeaker, ncFilter3,
                HandlerExecutor(mHandlerThread.threadHandler), offerCallback3)
        ncFilter4.addCapability(NET_CAPABILITY_NOT_VCN_MANAGED)
        provider2.registerNetworkOffer(scoreWeaker, ncFilter4,
                HandlerExecutor(mHandlerThread.threadHandler), offerCallback4)
        offerCallback1.assertNoCallback()
        offerCallback2.assertNoCallback()
        offerCallback3.expectOnNetworkNeeded(ncFilter3)
        offerCallback4.expectOnNetworkNeeded(ncFilter4)

        // Connect an agent to fulfill the request, verify offer 4 is not needed since it is not
        // from currently serving provider nor can beat the current satisfier.
        val nc = NetworkCapabilities().apply {
            addTransportType(NetworkCapabilities.TRANSPORT_TEST)
            removeCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
            addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED)
            addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED)
            addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)
            addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            setNetworkSpecifier(specifier1)
        }
        val config = NetworkAgentConfig.Builder().build()
        val agent = object : NetworkAgent(context, mHandlerThread.looper, "TestAgent", nc,
                LinkProperties(), scoreWeaker, config, provider) {}
        agent.register()
        agent.markConnected()
        offerCallback1.assertNoCallback()  // Still unneeded.
        offerCallback2.assertNoCallback()  // Still needed.
        offerCallback3.assertNoCallback()  // Still needed.
        offerCallback4.expectOnNetworkUnneeded(ncFilter4)

        // Upgrade the agent, verify no change since the framework will treat the offer as needed
        // if a request is currently satisfied by the network provided by the same provider.
        // TODO: Consider offers with weaker score are unneeded.
        agent.sendNetworkScore(scoreStronger)
        offerCallback1.assertNoCallback()  // Still unneeded.
        offerCallback2.assertNoCallback()  // Still needed.
        offerCallback3.assertNoCallback()  // Still needed.
        offerCallback4.assertNoCallback()  // Still unneeded.

        // Verify that offer callbacks cannot receive any event if offer is unregistered.
        provider2.unregisterNetworkOffer(offerCallback4)
        agent.unregister()
        offerCallback1.assertNoCallback()  // Still unneeded.
        offerCallback2.assertNoCallback()  // Still needed.
        offerCallback3.assertNoCallback()  // Still needed.
        // Since the agent is unregistered, and the offer has chance to satisfy the request,
        // this callback should receive needed if it is not unregistered.
        offerCallback4.assertNoCallback()

        // Verify that offer callbacks cannot receive any event if provider is unregistered.
        mCm.unregisterNetworkProvider(provider)
        mCm.unregisterNetworkCallback(cb2)
        offerCallback1.assertNoCallback()  // No callback since it is still unneeded.
        offerCallback2.assertNoCallback()  // Should be unneeded if not unregistered.
        offerCallback3.assertNoCallback()  // Should be unneeded if not unregistered.
        offerCallback4.assertNoCallback()  // Already unregistered.

        // Clean up and Verify providers did not receive any callback during the entire test.
        mCm.unregisterNetworkProvider(provider2)
        provider.assertNoCallback()
        provider2.assertNoCallback()
    }

    private class TestNetworkCallback : ConnectivityManager.NetworkCallback() {
        private val seenEvents = ArrayTrackRecord<CallbackEntry>().newReadHead()
        sealed class CallbackEntry {
            object OnUnavailable : CallbackEntry()
        }

        override fun onUnavailable() {
            seenEvents.add(OnUnavailable)
        }

        inline fun <reified T : CallbackEntry> expectCallback(
            crossinline predicate: (T) -> Boolean
        ) = seenEvents.poll(DEFAULT_TIMEOUT_MS) { it is T && predicate(it) }
    }

    @Test
    fun testDeclareNetworkRequestUnfulfillable() {
        val mockContext = mock(Context::class.java)
        doReturn(mCm).`when`(mockContext).getSystemService(Context.CONNECTIVITY_SERVICE)
        val provider = createNetworkProvider(mockContext)
        // ConnectivityManager not required at creation time after R
        if (isAtLeastS()) {
            verifyNoMoreInteractions(mockContext)
        }

        mCm.registerNetworkProvider(provider)

        val specifier = CompatUtil.makeTestNetworkSpecifier(
                UUID.randomUUID().toString())
        val nr: NetworkRequest = NetworkRequest.Builder()
                .addTransportType(TRANSPORT_TEST)
                .setNetworkSpecifier(specifier)
                .build()

        val cb = TestNetworkCallback()
        mCm.requestNetwork(nr, cb)
        provider.declareNetworkRequestUnfulfillable(nr)
        cb.expectCallback<OnUnavailable>() { nr.getNetworkSpecifier() == specifier }
        mCm.unregisterNetworkProvider(provider)
    }
}
