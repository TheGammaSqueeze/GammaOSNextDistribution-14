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

package com.android.server.connectivity

import android.net.MulticastRoutingConfig
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.os.test.TestLooper
import android.system.Os
import android.system.OsConstants.AF_INET6
import android.system.OsConstants.IPPROTO_UDP
import android.system.OsConstants.SOCK_DGRAM
import android.util.Log
import androidx.test.filters.LargeTest
import com.android.net.module.util.structs.StructMf6cctl
import com.android.net.module.util.structs.StructMrt6Msg
import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo
import com.android.testutils.DevSdkIgnoreRunner
import com.android.testutils.tryTest
import com.google.common.truth.Truth.assertThat
import java.io.FileDescriptor
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Inet6Address
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.timeout
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private const val TIMEOUT_MS = 2_000L

@RunWith(DevSdkIgnoreRunner::class)
@IgnoreUpTo(Build.VERSION_CODES.TIRAMISU)
class MulticastRoutingCoordinatorServiceTest {

    // mocks are lateinit as they need to be setup between tests
    @Mock private lateinit var mDeps: MulticastRoutingCoordinatorService.Dependencies
    @Mock private lateinit var mMulticastSocket: MulticastSocket

    val mSock = DatagramSocket()
    val mPfd = ParcelFileDescriptor.fromDatagramSocket(mSock)
    val mFd = mPfd.getFileDescriptor()
    val mIfName1 = "interface1"
    val mIfName2 = "interface2"
    val mIfName3 = "interface3"
    val mIfPhysicalIndex1 = 10
    val mIfPhysicalIndex2 = 11
    val mIfPhysicalIndex3 = 12
    val mSourceAddress = Inet6Address.getByName("2000::8888") as Inet6Address
    val mGroupAddressScope5 = Inet6Address.getByName("ff05::1234") as Inet6Address
    val mGroupAddressScope4 = Inet6Address.getByName("ff04::1234") as Inet6Address
    val mGroupAddressScope3 = Inet6Address.getByName("ff03::1234") as Inet6Address
    val mSocketAddressScope5 = InetSocketAddress(mGroupAddressScope5, 0)
    val mSocketAddressScope4 = InetSocketAddress(mGroupAddressScope4, 0)
    val mEmptyOifs = setOf<Int>()
    val mClock = FakeClock()
    val mNetworkInterface1 = createEmptyNetworkInterface()
    val mNetworkInterface2 = createEmptyNetworkInterface()
    // MulticastRoutingCoordinatorService needs to be initialized after the dependencies
    // are mocked.
    lateinit var mService: MulticastRoutingCoordinatorService
    lateinit var mLooper: TestLooper

    class FakeClock() : Clock() {
        private var offsetMs = 0L

        fun fastForward(ms: Long) {
            offsetMs += ms
        }

        override fun instant(): Instant {
            return Instant.now().plusMillis(offsetMs)
        }

        override fun getZone(): ZoneId {
            throw RuntimeException("Not implemented");
        }

        override fun withZone(zone: ZoneId): Clock {
            throw RuntimeException("Not implemented");
        }

    }

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        doReturn(mClock).`when`(mDeps).getClock()
        doReturn(mFd).`when`(mDeps).createMulticastRoutingSocket()
        doReturn(mMulticastSocket).`when`(mDeps).createMulticastSocket()
        doReturn(mIfPhysicalIndex1).`when`(mDeps).getInterfaceIndex(mIfName1)
        doReturn(mIfPhysicalIndex2).`when`(mDeps).getInterfaceIndex(mIfName2)
        doReturn(mIfPhysicalIndex3).`when`(mDeps).getInterfaceIndex(mIfName3)
        doReturn(mNetworkInterface1).`when`(mDeps).getNetworkInterface(mIfPhysicalIndex1)
        doReturn(mNetworkInterface2).`when`(mDeps).getNetworkInterface(mIfPhysicalIndex2)
    }

    @After
    fun tearDown() {
        mSock.close()
    }

    // Functions under @Before and @Test run in different threads,
    // (i.e. androidx.test.runner.AndroidJUnitRunner vs Time-limited test)
    // MulticastRoutingCoordinatorService requires the jobs are run on the thread looper,
    // so TestLooper needs to be created inside each test case to install the
    // correct looper.
    fun prepareService() {
        mLooper = TestLooper()
        val handler = Handler(mLooper.getLooper())

        mService = MulticastRoutingCoordinatorService(handler, mDeps)
    }

    private fun createEmptyNetworkInterface(): NetworkInterface {
        val constructor = NetworkInterface::class.java.getDeclaredConstructor()
        constructor.isAccessible = true
        return constructor.newInstance()
    }

    private fun createStructMf6cctl(src: Inet6Address, dst: Inet6Address, iifIdx: Int,
            oifSet: Set<Int>): StructMf6cctl {
        return StructMf6cctl(src, dst, iifIdx, oifSet)
    }

    // Send a MRT6MSG_NOCACHE packet to sock, to indicate a packet has arrived without matching MulticastRoutingCache
    private fun sendMrt6msgNocachePacket(interfaceVirtualIndex: Int,
            source: Inet6Address, destination: Inet6Address) {
        mLooper.dispatchAll() // let MulticastRoutingCoordinatorService handle all msgs first to
                              // apply any possible multicast routing config changes
        val mrt6Msg = StructMrt6Msg(0 /* mbz must be 0 */, StructMrt6Msg.MRT6MSG_NOCACHE,
                interfaceVirtualIndex, source, destination)
        mLooper.getNewExecutor().execute({ mService.handleMulticastNocacheUpcall(mrt6Msg) })
        mLooper.dispatchAll()
    }

    private fun applyMulticastForwardNone(fromIf: String, toIf: String) {
        val configNone = MulticastRoutingConfig.CONFIG_FORWARD_NONE

        mService.applyMulticastRoutingConfig(fromIf, toIf, configNone)
    }

    private fun applyMulticastForwardMinimumScope(fromIf: String, toIf: String, minScope: Int) {
        val configMinimumScope = MulticastRoutingConfig.Builder(
            MulticastRoutingConfig.FORWARD_WITH_MIN_SCOPE, minScope).build()

        mService.applyMulticastRoutingConfig(fromIf, toIf, configMinimumScope)
    }

    private fun applyMulticastForwardSelected(fromIf: String, toIf: String) {
        val configSelected = MulticastRoutingConfig.Builder(
            MulticastRoutingConfig.FORWARD_SELECTED)
            .addListeningAddress(mGroupAddressScope5).build()

        mService.applyMulticastRoutingConfig(fromIf, toIf, configSelected)
    }

    @Test
    fun testConstructor_multicastRoutingSocketIsCreated() {
        prepareService()
        verify(mDeps).createMulticastRoutingSocket()
    }

    @Test
    fun testMulticastRouting_applyForwardNone() {
        prepareService()

        applyMulticastForwardNone(mIfName1, mIfName2)
        mLooper.dispatchAll()

        // Both interfaces are not added as multicast routing interfaces
        verify(mDeps, never()).setsockoptMrt6AddMif(eq(mFd), any())
        // No MFC should be added for FORWARD_NONE
        verify(mDeps, never()).setsockoptMrt6AddMfc(eq(mFd), any())
        assertEquals(MulticastRoutingConfig.CONFIG_FORWARD_NONE,
                mService.getMulticastRoutingConfig(mIfName1, mIfName2));
    }

    @Test
    fun testMulticastRouting_applyForwardMinimumScope() {
        prepareService()

        applyMulticastForwardMinimumScope(mIfName1, mIfName2, 4 /* minScope */)
        mLooper.dispatchAll()

        // No MFC is added for FORWARD_WITH_MIN_SCOPE
        verify(mDeps, never()).setsockoptMrt6AddMfc(eq(mFd), any())
        assertEquals(MulticastRoutingConfig.FORWARD_WITH_MIN_SCOPE,
                mService.getMulticastRoutingConfig(mIfName1, mIfName2).getForwardingMode())
        assertEquals(4, mService.getMulticastRoutingConfig(mIfName1, mIfName2).getMinimumScope())
    }

    @Test
    fun testMulticastRouting_addressScopelargerThanMinScope_allowMfcIsAdded() {
        prepareService()
        applyMulticastForwardMinimumScope(mIfName1, mIfName2, 4 /* minScope */)
        mLooper.dispatchAll()
        val oifs = setOf(mService.getVirtualInterfaceIndex(mIfName2))
        val mf6cctl = createStructMf6cctl(mSourceAddress, mGroupAddressScope5,
                mService.getVirtualInterfaceIndex(mIfName1), oifs)

        // simulate a MRT6MSG_NOCACHE upcall for a packet sent to group address of scope 5
        sendMrt6msgNocachePacket(0, mSourceAddress, mGroupAddressScope5)

        // an MFC is added for the packet
        verify(mDeps).setsockoptMrt6AddMfc(eq(mFd), eq(mf6cctl))
    }

    @Test
    fun testMulticastRouting_addressScopeSmallerThanMinScope_blockingMfcIsAdded() {
        prepareService()
        applyMulticastForwardMinimumScope(mIfName1, mIfName2, 4)
        val mf6cctl = createStructMf6cctl(mSourceAddress, mGroupAddressScope3,
                mService.getVirtualInterfaceIndex(mIfName1), mEmptyOifs)

        // simulate a MRT6MSG_NOCACHE upcall when a packet should not be forwarded
        sendMrt6msgNocachePacket(0, mSourceAddress, mGroupAddressScope3)

        // a blocking MFC is added
        verify(mDeps).setsockoptMrt6AddMfc(eq(mFd), eq(mf6cctl))
    }

    @Test
    fun testMulticastRouting_applyForwardSelected_joinsGroup() {
        prepareService()

        applyMulticastForwardSelected(mIfName1, mIfName2)
        mLooper.dispatchAll()

        verify(mMulticastSocket).joinGroup(eq(mSocketAddressScope5), eq(mNetworkInterface1))
        assertEquals(MulticastRoutingConfig.FORWARD_SELECTED,
                mService.getMulticastRoutingConfig(mIfName1, mIfName2).getForwardingMode())
    }

    @Test
    fun testMulticastRouting_addListeningAddressInForwardSelected_joinsGroup() {
        prepareService()

        val configSelectedNoAddress = MulticastRoutingConfig.Builder(
            MulticastRoutingConfig.FORWARD_SELECTED).build()
        mService.applyMulticastRoutingConfig(mIfName1, mIfName2, configSelectedNoAddress)
        mLooper.dispatchAll()

        val configSelectedWithAddresses = MulticastRoutingConfig.Builder(
            MulticastRoutingConfig.FORWARD_SELECTED)
            .addListeningAddress(mGroupAddressScope5)
            .addListeningAddress(mGroupAddressScope4)
            .build()
        mService.applyMulticastRoutingConfig(mIfName1, mIfName2, configSelectedWithAddresses)
        mLooper.dispatchAll()

        verify(mMulticastSocket).joinGroup(eq(mSocketAddressScope5), eq(mNetworkInterface1))
        verify(mMulticastSocket).joinGroup(eq(mSocketAddressScope4), eq(mNetworkInterface1))
    }

    @Test
    fun testMulticastRouting_removeListeningAddressInForwardSelected_leavesGroup() {
        prepareService()
        val configSelectedWith2Addresses = MulticastRoutingConfig.Builder(
            MulticastRoutingConfig.FORWARD_SELECTED)
            .addListeningAddress(mGroupAddressScope5)
            .addListeningAddress(mGroupAddressScope4)
            .build()
        mService.applyMulticastRoutingConfig(mIfName1, mIfName2, configSelectedWith2Addresses)
        mLooper.dispatchAll()

        verify(mMulticastSocket).joinGroup(eq(mSocketAddressScope5), eq(mNetworkInterface1))
        verify(mMulticastSocket).joinGroup(eq(mSocketAddressScope4), eq(mNetworkInterface1))

        // remove the scope4 address
        val configSelectedWith1Address = MulticastRoutingConfig.Builder(
            MulticastRoutingConfig.FORWARD_SELECTED)
            .addListeningAddress(mGroupAddressScope5)
            .build()
        mService.applyMulticastRoutingConfig(mIfName1, mIfName2, configSelectedWith1Address)
        mLooper.dispatchAll()

        verify(mMulticastSocket).leaveGroup(eq(mSocketAddressScope4), eq(mNetworkInterface1))
        verify(mMulticastSocket, never())
                .leaveGroup(eq(mSocketAddressScope5), eq(mNetworkInterface1))
    }

    @Test
    fun testMulticastRouting_fromForwardSelectedToForwardNone_leavesGroup() {
        prepareService()
        applyMulticastForwardSelected(mIfName1, mIfName2)
        mLooper.dispatchAll()

        verify(mMulticastSocket).joinGroup(eq(mSocketAddressScope5), eq(mNetworkInterface1))

        applyMulticastForwardNone(mIfName1, mIfName2)
        mLooper.dispatchAll()

        verify(mMulticastSocket).leaveGroup(eq(mSocketAddressScope5), eq(mNetworkInterface1))
        assertEquals(MulticastRoutingConfig.CONFIG_FORWARD_NONE,
                mService.getMulticastRoutingConfig(mIfName1, mIfName2));
    }

    @Test
    fun testMulticastRouting_fromFowardSelectedToForwardNone_removesMulticastInterfaces() {
        prepareService()

        applyMulticastForwardSelected(mIfName1, mIfName2)
        applyMulticastForwardSelected(mIfName1, mIfName3)
        mLooper.dispatchAll()

        assertNotNull(mService.getVirtualInterfaceIndex(mIfName1))
        assertNotNull(mService.getVirtualInterfaceIndex(mIfName2))
        assertNotNull(mService.getVirtualInterfaceIndex(mIfName3))

        applyMulticastForwardNone(mIfName1, mIfName2)
        mLooper.dispatchAll()

        assertNotNull(mService.getVirtualInterfaceIndex(mIfName1))
        assertNull(mService.getVirtualInterfaceIndex(mIfName2))
        assertNotNull(mService.getVirtualInterfaceIndex(mIfName3))
    }

    @Test
    fun testMulticastRouting_addMulticastRoutingInterfaces() {
        prepareService()

        applyMulticastForwardSelected(mIfName1, mIfName2)
        mLooper.dispatchAll()

        assertNotNull(mService.getVirtualInterfaceIndex(mIfName1))
        assertNotNull(mService.getVirtualInterfaceIndex(mIfName2))
        assertNotEquals(mService.getVirtualInterfaceIndex(mIfName1),
                mService.getVirtualInterfaceIndex(mIfName2))
    }

    @Test
    fun testMulticastRouting_removeMulticastRoutingInterfaces() {
        prepareService()

        applyMulticastForwardSelected(mIfName1, mIfName2)
        mService.removeInterfaceFromMulticastRouting(mIfName1)
        mLooper.dispatchAll()

        assertNull(mService.getVirtualInterfaceIndex(mIfName1))
        assertNotNull(mService.getVirtualInterfaceIndex(mIfName2))
    }

    @Test
    fun testMulticastRouting_applyConfigNone_removesMfc() {
        prepareService()

        applyMulticastForwardMinimumScope(mIfName1, mIfName2, 4 /* minScope */)
        applyMulticastForwardSelected(mIfName1, mIfName3)

        sendMrt6msgNocachePacket(0, mSourceAddress, mGroupAddressScope5)
        val oifs = setOf(mService.getVirtualInterfaceIndex(mIfName2),
                mService.getVirtualInterfaceIndex(mIfName3))
        val oifsUpdate = setOf(mService.getVirtualInterfaceIndex(mIfName3))
        val mf6cctlAdd = createStructMf6cctl(mSourceAddress, mGroupAddressScope5,
                mService.getVirtualInterfaceIndex(mIfName1), oifs)
        val mf6cctlUpdate = createStructMf6cctl(mSourceAddress, mGroupAddressScope5,
                mService.getVirtualInterfaceIndex(mIfName1), oifsUpdate)
        val mf6cctlDel = createStructMf6cctl(mSourceAddress, mGroupAddressScope5,
                mService.getVirtualInterfaceIndex(mIfName1), mEmptyOifs)

        verify(mDeps).setsockoptMrt6AddMfc(eq(mFd), eq(mf6cctlAdd))

        applyMulticastForwardNone(mIfName1, mIfName2)
        mLooper.dispatchAll()

        verify(mDeps).setsockoptMrt6AddMfc(eq(mFd), eq(mf6cctlUpdate))

        applyMulticastForwardNone(mIfName1, mIfName3)
        mLooper.dispatchAll()

        verify(mDeps, timeout(TIMEOUT_MS).times(1)).setsockoptMrt6DelMfc(eq(mFd), eq(mf6cctlDel))
    }

    @Test
    @LargeTest
    fun testMulticastRouting_maxNumberOfMfcs() {
        prepareService()

        // add MFC_MAX_NUMBER_OF_ENTRIES MFCs
        applyMulticastForwardMinimumScope(mIfName1, mIfName2, 4 /* minScope */)
        for (i in 1..MulticastRoutingCoordinatorService.MFC_MAX_NUMBER_OF_ENTRIES) {
            val groupAddress =
                Inet6Address.getByName("ff05::" + Integer.toHexString(i)) as Inet6Address
            sendMrt6msgNocachePacket(0, mSourceAddress, groupAddress)
        }
        val mf6cctlDel = createStructMf6cctl(mSourceAddress,
                Inet6Address.getByName("ff05::1" ) as Inet6Address,
                mService.getVirtualInterfaceIndex(mIfName1), mEmptyOifs)

        verify(mDeps, times(MulticastRoutingCoordinatorService.MFC_MAX_NUMBER_OF_ENTRIES)).
            setsockoptMrt6AddMfc(eq(mFd), any())
        // when number of mfcs reaches the max value, one mfc should be removed
        verify(mDeps).setsockoptMrt6DelMfc(eq(mFd), eq(mf6cctlDel))
    }

    @Test
    fun testMulticastRouting_interfaceWithoutActiveConfig_isRemoved() {
        prepareService()
        applyMulticastForwardMinimumScope(mIfName1, mIfName2, 4 /* minScope */)
        mLooper.dispatchAll()
        val virtualIndexIf1 = mService.getVirtualInterfaceIndex(mIfName1)
        val virtualIndexIf2 = mService.getVirtualInterfaceIndex(mIfName2)

        applyMulticastForwardNone(mIfName1, mIfName2)
        mLooper.dispatchAll()

        verify(mDeps).setsockoptMrt6DelMif(eq(mFd), eq(virtualIndexIf1))
        verify(mDeps).setsockoptMrt6DelMif(eq(mFd), eq(virtualIndexIf2))
    }

    @Test
    fun testMulticastRouting_interfaceWithActiveConfig_isNotRemoved() {
        prepareService()
        applyMulticastForwardMinimumScope(mIfName1, mIfName2, 4 /* minScope */)
        applyMulticastForwardMinimumScope(mIfName2, mIfName3, 4 /* minScope */)
        mLooper.dispatchAll()
        val virtualIndexIf1 = mService.getVirtualInterfaceIndex(mIfName1)
        val virtualIndexIf2 = mService.getVirtualInterfaceIndex(mIfName2)
        val virtualIndexIf3 = mService.getVirtualInterfaceIndex(mIfName3)

        applyMulticastForwardNone(mIfName1, mIfName2)
        mLooper.dispatchAll()

        verify(mDeps).setsockoptMrt6DelMif(eq(mFd), eq(virtualIndexIf1))
        verify(mDeps, never()).setsockoptMrt6DelMif(eq(mFd), eq(virtualIndexIf2))
        verify(mDeps, never()).setsockoptMrt6DelMif(eq(mFd), eq(virtualIndexIf3))
    }

    @Test
    fun testMulticastRouting_unusedMfc_isRemovedAfterTimeout() {
        prepareService()
        applyMulticastForwardMinimumScope(mIfName1, mIfName2, 4 /* minScope */)
        sendMrt6msgNocachePacket(0, mSourceAddress, mGroupAddressScope5)
        val oifs = setOf(mService.getVirtualInterfaceIndex(mIfName2))
        val mf6cctlAdd = createStructMf6cctl(mSourceAddress, mGroupAddressScope5,
                mService.getVirtualInterfaceIndex(mIfName1), oifs)
        val mf6cctlDel = createStructMf6cctl(mSourceAddress, mGroupAddressScope5,
                mService.getVirtualInterfaceIndex(mIfName1), mEmptyOifs)

        // An MFC is added
        verify(mDeps).setsockoptMrt6AddMfc(eq(mFd), eq(mf6cctlAdd))

        repeat(MulticastRoutingCoordinatorService.MFC_INACTIVE_TIMEOUT_MS /
                MulticastRoutingCoordinatorService.MFC_INACTIVE_CHECK_INTERVAL_MS + 1) {
            mClock.fastForward(MulticastRoutingCoordinatorService
                    .MFC_INACTIVE_CHECK_INTERVAL_MS.toLong())
            mLooper.moveTimeForward(MulticastRoutingCoordinatorService
                    .MFC_INACTIVE_CHECK_INTERVAL_MS.toLong())
            mLooper.dispatchAll();
        }

        verify(mDeps).setsockoptMrt6DelMfc(eq(mFd), eq(mf6cctlDel))
    }
}
