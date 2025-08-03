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

package android.bluetooth

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.MacAddress
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.pandora.intentFlow
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.ByteString
import com.google.protobuf.Empty
import io.grpc.ManagedChannel
import io.grpc.okhttp.OkHttpChannelBuilder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import pandora.HostGrpc
import pandora.HostProto.ConnectRequest
import pandora.HostProto.DisconnectRequest

@Suppress("DEPRECATION")
@kotlinx.coroutines.ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class BluetoothMetricsHelperTest {

    companion object {
        private const val TAG = "BluetoothMetricsHelperTest"

        private lateinit var mChannel: ManagedChannel
        private lateinit var mHostBlockingStub: HostGrpc.HostBlockingStub
        private lateinit var mHostStub: HostGrpc.HostStub

        @BeforeClass
        fun setUpClass() {
            InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity()
        }
    }

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val context = InstrumentationRegistry.getInstrumentation().getContext()

    private val bluetoothAdapter: BluetoothAdapter =
        context.getSystemService(BluetoothManager::class.java)!!.adapter
    private val adapterState = MutableStateFlow(bluetoothAdapter.state)

    init {
        context.registerReceiver(
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    adapterState.tryEmit(
                        intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    )
                }
            },
            IntentFilter(BluetoothAdapter.ACTION_BLE_STATE_CHANGED)
        )
    }

    @Before
    fun setUp() {
        val uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation()
        // Adopt all the permissions of the shell
        uiAutomation.adoptShellPermissionIdentity()

        // FactorReset is killing the server and restart
        // all channel created before the server restarted
        // cannot be reused
        val channel = OkHttpChannelBuilder.forAddress("localhost", 7999).usePlaintext().build()

        HostGrpc.newBlockingStub(channel).factoryReset(Empty.getDefaultInstance())

        // terminate the channel
        channel.shutdown().awaitTermination(1, TimeUnit.SECONDS)

        // Create a new channel for all successive grpc calls
        mChannel = OkHttpChannelBuilder.forAddress("localhost", 7999).usePlaintext().build()

        mHostBlockingStub = HostGrpc.newBlockingStub(mChannel)
        mHostStub = HostGrpc.newStub(mChannel)
        mHostBlockingStub.withWaitForReady()?.readLocalAddress(Empty.getDefaultInstance())

        // Make sure the Adapter is on.
        bluetoothAdapter.enable()
        runBlocking { adapterState.first { it == BluetoothAdapter.STATE_ON } }
    }

    @After
    fun tearDown() {
        // terminate the channel
        mChannel.shutdown()?.awaitTermination(1, TimeUnit.SECONDS)
    }

    @Test
    fun incomingClassicConnectionTest() = runTest {
        val intentFilter = IntentFilter()
        intentFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        intentFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        val flow: Flow<Intent> =
            intentFlow(context, intentFilter, testScope).shareIn(testScope, SharingStarted.Eagerly)

        val incomingConnection = async {
            flow
                .filter { it.action == BluetoothDevice.ACTION_ACL_CONNECTED }
                .filter {
                    it.getIntExtra(BluetoothDevice.EXTRA_TRANSPORT, BluetoothDevice.ERROR) ==
                        BluetoothDevice.TRANSPORT_BREDR
                }
                .first()
        }

        val localMacAddress = MacAddress.fromString(bluetoothAdapter.getAddress())
        val connectRequest =
            ConnectRequest.newBuilder()
                .setAddress(ByteString.copyFrom(localMacAddress.toByteArray()))
                .build()
        val connectResponse = mHostBlockingStub.connect(connectRequest)
        assertThat(connectResponse).isNotNull()
        assertThat(connectResponse.hasConnection()).isTrue()
        incomingConnection.await()

        val disconnectRequest =
            DisconnectRequest.newBuilder().setConnection(connectResponse.connection).build()
        mHostBlockingStub.disconnect(disconnectRequest)
    }

    @Test
    fun testBluetoothDisableEnable() = runTest {
        bluetoothAdapter.disable()
        adapterState.first { it == BluetoothAdapter.STATE_OFF }
        bluetoothAdapter.enable()
        adapterState.first { it == BluetoothAdapter.STATE_ON }
    }
}
