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

package android.net.ip

import android.Manifest.permission.NETWORK_SETTINGS
import android.Manifest.permission.READ_DEVICE_CONFIG
import android.Manifest.permission.WRITE_DEVICE_CONFIG
import android.net.IIpMemoryStore
import android.net.IIpMemoryStoreCallbacks
import android.net.NetworkStackIpMemoryStore
import android.net.ipmemorystore.NetworkAttributes
import android.net.ipmemorystore.OnNetworkAttributesRetrievedListener
import android.net.ipmemorystore.Status
import android.net.networkstack.TestNetworkStackServiceClient
import android.os.Process
import android.provider.DeviceConfig
import android.provider.DeviceConfig.NAMESPACE_CONNECTIVITY
import android.util.ArrayMap
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import java.lang.System.currentTimeMillis
import java.lang.UnsupportedOperationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import org.junit.After
import org.junit.AfterClass
import org.junit.BeforeClass
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.timeout
import org.mockito.Mockito.verify

// Stable AIDL method 5 in INetworkStackConnector is allowTestUid
private const val ALLOW_TEST_UID_INDEX = 5

/**
 * Tests for IpClient, run with root access but no signature permissions.
 *
 * Tests run from this class interact with the real network stack process and can affect system
 * state, e.g. by changing flags.
 * State should be restored at the end of the test, but is not guaranteed if the test process is
 * terminated during the run.
 */
class IpClientRootTest : IpClientIntegrationTestCommon() {
    companion object {
        private val TAG = IpClientRootTest::class.java.simpleName
        private val automation by lazy { InstrumentationRegistry.getInstrumentation().uiAutomation }
        private lateinit var nsClient: TestNetworkStackServiceClient
        private lateinit var mStore: NetworkStackIpMemoryStore
        private val mContext = InstrumentationRegistry.getInstrumentation().getContext()

        private class IpMemoryStoreCallbacks(
            private val fetchedFuture: CompletableFuture<IIpMemoryStore>
        ) : IIpMemoryStoreCallbacks.Stub() {
            override fun onIpMemoryStoreFetched(ipMemoryStore: IIpMemoryStore) {
                fetchedFuture.complete(ipMemoryStore)
            }
            override fun getInterfaceVersion() = IIpMemoryStoreCallbacks.VERSION
            override fun getInterfaceHash() = IIpMemoryStoreCallbacks.HASH
        }

        @JvmStatic @BeforeClass
        fun setUpClass() {
            // Connect to the NetworkStack only once, as it is relatively expensive (~50ms plus any
            // polling time waiting for the test UID to be allowed), and there should be minimal
            // side-effects between tests compared to reconnecting every time.
            automation.adoptShellPermissionIdentity(NETWORK_SETTINGS)
            try {
                automation.executeShellCommand("su root service call network_stack " +
                        "$ALLOW_TEST_UID_INDEX i32 " + Process.myUid())
                // Connecting to the test service does not require allowing the test UID (binding is
                // always allowed with NETWORK_SETTINGS permissions on debuggable builds), but
                // allowing the test UID is required to call any method on the service.
                nsClient = TestNetworkStackServiceClient.connect()
                // Wait for oneway call to be processed: unfortunately there is no easy way to wait
                // for a success callback via the service shell command.
                // TODO: build a small native util that also waits for the success callback, bundle
                // it in the test APK, and run it as shell command as root instead.
                mStore = getIpMemoryStore()
            } finally {
                automation.dropShellPermissionIdentity()
            }
        }

        private fun getIpMemoryStore(): NetworkStackIpMemoryStore {
            // Until the test UID is allowed, oneway binder calls will not receive any reply.
            // Call fetchIpMemoryStore (which has limited side-effects) repeatedly until any call
            // gets a callback.
            val limit = currentTimeMillis() + TEST_TIMEOUT_MS
            val fetchedFuture = CompletableFuture<IIpMemoryStore>()
            Log.i(TAG, "Starting multiple attempts to fetch IpMemoryStore; failures are expected")
            while (currentTimeMillis() < limit) {
                try {
                    nsClient.fetchIpMemoryStore(IpMemoryStoreCallbacks(fetchedFuture))
                    // The future may be completed by any previous call to fetchIpMemoryStore.
                    val ipMemoryStore = fetchedFuture.get(20, TimeUnit.MILLISECONDS)
                    Log.i(TAG, "Obtained IpMemoryStore: " + ipMemoryStore)
                    return NetworkStackIpMemoryStore(mContext, ipMemoryStore)
                } catch (e: TimeoutException) {
                    // Fall through
                }
            }
            fail("fail to get the IpMemoryStore instance within timeout")
        }

        @JvmStatic @AfterClass
        fun tearDownClass() {
            nsClient.disconnect()
            automation.adoptShellPermissionIdentity(NETWORK_SETTINGS)
            try {
                // Reset the test UID as -1.
                // This may not be called if the test process is terminated before completing,
                // however this is fine as the test UID is only usable on userdebug builds, and
                // the system does not reuse UIDs across apps until reboot.
                automation.executeShellCommand("su root service call network_stack " +
                        "$ALLOW_TEST_UID_INDEX i32 -1")
            } finally {
                automation.dropShellPermissionIdentity()
            }
        }
    }

    /**
     * Wrapper class for IIpClientCallbacks.
     *
     * Used to delegate method calls to mock interfaces used to verify the calls, while using
     * real implementations of the binder stub (such as [asBinder]) to properly receive the calls.
     */
    private class BinderCbWrapper(base: IIpClientCallbacks) :
            IIpClientCallbacks.Stub(), IIpClientCallbacks by base {
        // asBinder is implemented by both base class and delegate: specify explicitly
        override fun asBinder() = super.asBinder()
        override fun getInterfaceVersion() = IIpClientCallbacks.VERSION
    }

    @After
    fun tearDownIpMemoryStore() {
        if (testSkipped()) return
        val latch = CountDownLatch(1)

        // Delete the IpMemoryStore entry corresponding to TEST_L2KEY, make sure each test starts
        // from a clean state.
        mStore.delete(TEST_L2KEY, true) { _, _ -> latch.countDown() }
        assertTrue(latch.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS))
    }

    override fun useNetworkStackSignature() = false

    override fun makeIIpClient(ifaceName: String, cbMock: IIpClientCallbacks): IIpClient {
        val ipClientCaptor = ArgumentCaptor.forClass(IIpClient::class.java)
        // Older versions of NetworkStack do not clear the calling identity when creating IpClient,
        // so READ_DEVICE_CONFIG is required to initialize it (b/168577898).
        automation.adoptShellPermissionIdentity(READ_DEVICE_CONFIG)
        try {
            nsClient.makeIpClient(ifaceName, BinderCbWrapper(cbMock))
            verify(cbMock, timeout(TEST_TIMEOUT_MS)).onIpClientCreated(ipClientCaptor.capture())
        } finally {
            automation.dropShellPermissionIdentity()
        }
        return ipClientCaptor.value
    }

    // These are not needed in IpClientRootTest because there is no dependency injection and
    // IpClient always uses the production implementations.
    override fun getDeviceConfigProperty(name: String) = throw UnsupportedOperationException()
    override fun isFeatureEnabled(name: String) = throw UnsupportedOperationException()
    override fun isFeatureNotChickenedOut(name: String) = throw UnsupportedOperationException()

    private val mOriginalPropertyValues = ArrayMap<String, String>()

    override fun setDeviceConfigProperty(name: String?, value: String?) {
        automation.adoptShellPermissionIdentity(READ_DEVICE_CONFIG, WRITE_DEVICE_CONFIG)
        try {
            // Do not use computeIfAbsent as it would overwrite null values,
            // property originally unset.
            if (!mOriginalPropertyValues.containsKey(name)) {
                mOriginalPropertyValues[name] = DeviceConfig.getProperty(
                    DeviceConfig.NAMESPACE_CONNECTIVITY,
                    (name)!!
                )
            }
            DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_CONNECTIVITY,
                (name)!!, value,
                false /* makeDefault */
            )
        } finally {
            automation.dropShellPermissionIdentity()
        }
    }

    @After
    fun tearDownDeviceConfigProperties() {
        if (testSkipped()) return
        automation.adoptShellPermissionIdentity(READ_DEVICE_CONFIG, WRITE_DEVICE_CONFIG)
        try {
            for (key in mOriginalPropertyValues.keys) {
                if (key == null) continue
                DeviceConfig.setProperty(
                    DeviceConfig.NAMESPACE_CONNECTIVITY, key,
                    mOriginalPropertyValues[key], false /* makeDefault */
                )
            }
        } finally {
            automation.dropShellPermissionIdentity()
        }
    }

    private class TestAttributesRetrievedListener : OnNetworkAttributesRetrievedListener {
        private val future = CompletableFuture<NetworkAttributes?>()
        override fun onNetworkAttributesRetrieved(
            status: Status,
            key: String,
            attr: NetworkAttributes?
        ) {
            // NetworkAttributes associated to specific l2key retrieved from IpMemoryStore might be
            // null according to testcase context, hence, make sure the callback is triggered with
            // success and the l2key param return from callback matches, which also prevents the
            // case that the NetworkAttributes haven't been stored within CompletableFuture polling
            // timeout.
            if (key != TEST_L2KEY || status.resultCode != Status.SUCCESS) {
                fail("retrieved the network attributes associated to L2Key: " + key +
                        " status: " + status.resultCode + " attributes: " + attr)
            }
            future.complete(attr)
        }

        fun getBlockingNetworkAttributes(timeout: Long): NetworkAttributes? {
            return future.get(timeout, TimeUnit.MILLISECONDS)
        }
    }

    override fun getStoredNetworkAttributes(l2Key: String, timeout: Long): NetworkAttributes {
        val listener = TestAttributesRetrievedListener()
        mStore.retrieveNetworkAttributes(l2Key, listener)
        val na = listener.getBlockingNetworkAttributes(timeout)
        assertNotNull(na)
        return na
    }

    override fun assertIpMemoryNeverStoreNetworkAttributes(l2Key: String, timeout: Long) {
        val listener = TestAttributesRetrievedListener()
        mStore.retrieveNetworkAttributes(l2Key, listener)
        assertNull(listener.getBlockingNetworkAttributes(timeout))
    }

    override fun storeNetworkAttributes(l2Key: String, na: NetworkAttributes) {
        mStore.storeNetworkAttributes(l2Key, na, null /* listener */)
    }

    private fun readNudSolicitNumFromResource(name: String): Int {
        val packageName = nsClient.getNetworkStackPackageName()
        val resource = mContext.createPackageContext(packageName, 0).getResources()
        val id = resource.getIdentifier(name, "integer", packageName)
        return resource.getInteger(id)
    }

    override fun readNudSolicitNumInSteadyStateFromResource(): Int {
        return readNudSolicitNumFromResource("config_nud_steadystate_solicit_num")
    }

    override fun readNudSolicitNumPostRoamingFromResource(): Int {
        return readNudSolicitNumFromResource("config_nud_postroaming_solicit_num")
    }
}
