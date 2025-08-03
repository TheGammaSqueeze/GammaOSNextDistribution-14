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
package android.net.cts

import android.net.EthernetTetheringTestBase
import android.net.LinkAddress
import android.net.TestNetworkInterface
import android.net.TetheringManager.CONNECTIVITY_SCOPE_LOCAL
import android.net.TetheringManager.TETHERING_ETHERNET
import android.net.TetheringManager.TetheringRequest
import android.net.nsd.NsdManager
import android.os.Build
import android.platform.test.annotations.AppModeFull
import androidx.test.filters.SmallTest
import com.android.testutils.ConnectivityModuleTest
import com.android.testutils.DevSdkIgnoreRule
import com.android.testutils.DevSdkIgnoreRunner
import com.android.testutils.TapPacketReader
import com.android.testutils.tryTest
import java.util.Random
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.After
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(DevSdkIgnoreRunner::class)
@SmallTest
@ConnectivityModuleTest
@AppModeFull(reason = "WifiManager cannot be obtained in instant mode")
@DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.S_V2)
class NsdManagerDownstreamTetheringTest : EthernetTetheringTestBase() {
    private val nsdManager by lazy { context.getSystemService(NsdManager::class.java)!! }
    private val serviceType = "_nmt%09d._tcp".format(Random().nextInt(1_000_000_000))

    @Before
    override fun setUp() {
        super.setUp()
        setIncludeTestInterfaces(true)
    }

    @After
    override fun tearDown() {
        super.tearDown()
        setIncludeTestInterfaces(false)
    }

    @Test
    fun testMdnsDiscoveryCanSendPacketOnLocalOnlyDownstreamTetheringInterface() {
        assumeFalse(isInterfaceForTetheringAvailable())

        var downstreamIface: TestNetworkInterface? = null
        var tetheringEventCallback: MyTetheringEventCallback? = null
        var downstreamReader: TapPacketReader? = null

        val discoveryRecord = NsdDiscoveryRecord()

        tryTest {
            downstreamIface = createTestInterface()
            val iface = tetheredInterface
            assertEquals(iface, downstreamIface?.interfaceName)
            val request = TetheringRequest.Builder(TETHERING_ETHERNET)
                .setConnectivityScope(CONNECTIVITY_SCOPE_LOCAL).build()
            tetheringEventCallback = enableEthernetTethering(
                iface, request,
                null /* any upstream */
            ).apply {
                awaitInterfaceLocalOnly()
            }
            // This shouldn't be flaky because the TAP interface will buffer all packets even
            // before the reader is started.
            downstreamReader = makePacketReader(downstreamIface)
            waitForRouterAdvertisement(downstreamReader, iface, WAIT_RA_TIMEOUT_MS)

            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryRecord)
            discoveryRecord.expectCallback<NsdDiscoveryRecord.DiscoveryEvent.DiscoveryStarted>()
            assertNotNull(downstreamReader?.pollForQuery("$serviceType.local", 12 /* type PTR */))
        } cleanupStep {
            nsdManager.stopServiceDiscovery(discoveryRecord)
            discoveryRecord.expectCallback<NsdDiscoveryRecord.DiscoveryEvent.DiscoveryStopped>()
        } cleanupStep {
            maybeStopTapPacketReader(downstreamReader)
        } cleanupStep {
            maybeCloseTestInterface(downstreamIface)
        } cleanup {
            maybeUnregisterTetheringEventCallback(tetheringEventCallback)
        }
    }

    @Test
    fun testMdnsDiscoveryWorkOnTetheringInterface() {
        assumeFalse(isInterfaceForTetheringAvailable())
        setIncludeTestInterfaces(true)

        var downstreamIface: TestNetworkInterface? = null
        var tetheringEventCallback: MyTetheringEventCallback? = null
        var downstreamReader: TapPacketReader? = null

        val discoveryRecord = NsdDiscoveryRecord()

        tryTest {
            downstreamIface = createTestInterface()
            val iface = tetheredInterface
            assertEquals(iface, downstreamIface?.interfaceName)

            val localAddr = LinkAddress("192.0.2.3/28")
            val clientAddr = LinkAddress("192.0.2.2/28")
            val request = TetheringRequest.Builder(TETHERING_ETHERNET)
                .setStaticIpv4Addresses(localAddr, clientAddr)
                .setShouldShowEntitlementUi(false).build()
            tetheringEventCallback = enableEthernetTethering(
                iface, request,
                null /* any upstream */
            ).apply {
                awaitInterfaceTethered()
            }

            val fd = downstreamIface?.fileDescriptor?.fileDescriptor
            assertNotNull(fd)
            downstreamReader = makePacketReader(fd, getMTU(downstreamIface))

            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryRecord)
            discoveryRecord.expectCallback<NsdDiscoveryRecord.DiscoveryEvent.DiscoveryStarted>()
            assertNotNull(downstreamReader?.pollForQuery("$serviceType.local", 12 /* type PTR */))
            // TODO: Add another test to check packet reply can trigger serviceFound.
        } cleanupStep {
            nsdManager.stopServiceDiscovery(discoveryRecord)
            discoveryRecord.expectCallback<NsdDiscoveryRecord.DiscoveryEvent.DiscoveryStopped>()
        } cleanupStep {
            maybeStopTapPacketReader(downstreamReader)
        } cleanupStep {
            maybeCloseTestInterface(downstreamIface)
        } cleanup {
            maybeUnregisterTetheringEventCallback(tetheringEventCallback)
        }
    }
}
