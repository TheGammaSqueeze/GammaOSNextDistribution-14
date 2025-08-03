/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.bluetooth.cts;

import static android.Manifest.permission.BLUETOOTH_CONNECT;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothAssignedNumbers.OrganizationId;
import android.bluetooth.le.TransportBlockFilter;
import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.CddTest;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class TransportBlockFilterTest {
    private static final int TEST_TDS_FLAG = 0x3;
    private static final int TEST_TDS_FLAG_MASK = 0b10;
    private static final byte[] TEST_TRANSPORT_DATA = {0x0, 0x3, 0x2, 0x3, 0x4, 0x5};
    private static final byte[] TEST_TRANSPORT_DATA_MASK = {0x0, 0x1, 0xF, 0xF, 0xF, 0xF};
    private static final byte[] TEST_TRANSPORT_DATA_LONG = {0x0, 0x3, 0x2, 0x3, 0x4, 0x5, 0x6};
    private static final byte[] TEST_TRANSPORT_DATA_MASK_LONG = {0x0, 0x1, 0xF, 0xF, 0xF, 0xF, 0xF};
    private static final byte[] TEST_VALID_WIFI_NAN_HASH =
            {0x1, 0x2, 0x3, 0x4, 0x5, 0x6, 0x7, 0x8};

    private Context mContext;
    private BluetoothAdapter mAdapter;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();

        Assume.assumeTrue(TestUtils.isBleSupported(mContext));

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT);
        mAdapter = TestUtils.getBluetoothAdapterOrDie();
        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));
    }

    @After
    public void tearDown() {
        TestUtils.dropPermissionAsShellUid();
        mAdapter = null;
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void emptyTransportBlockFilterFromBuilder() {
        TransportBlockFilter filter = new TransportBlockFilter.Builder(
                OrganizationId.BLUETOOTH_SIG).build();
        assertEquals(OrganizationId.BLUETOOTH_SIG, filter.getOrgId());
        assertEquals(0, filter.getTdsFlags());
        assertEquals(0, filter.getTdsFlagsMask());
        assertNull(filter.getTransportData());
        assertNull(filter.getTransportDataMask());
        assertNull(filter.getWifiNanHash());
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void createTransportBlockFilterFromBuilder() {
        TransportBlockFilter filter = new TransportBlockFilter.Builder(
                OrganizationId.WIFI_ALLIANCE_SERVICE_ADVERTISEMENT)
                .setTdsFlags(TEST_TDS_FLAG, TEST_TDS_FLAG_MASK)
                .setTransportData(TEST_TRANSPORT_DATA, TEST_TRANSPORT_DATA_MASK).build();
        assertEquals(OrganizationId.WIFI_ALLIANCE_SERVICE_ADVERTISEMENT, filter.getOrgId());
        assertEquals(TEST_TDS_FLAG, filter.getTdsFlags());
        assertEquals(TEST_TDS_FLAG_MASK, filter.getTdsFlagsMask());
        assertArrayEquals(TEST_TRANSPORT_DATA, filter.getTransportData());
        assertArrayEquals(TEST_TRANSPORT_DATA_MASK, filter.getTransportDataMask());
        assertNull(filter.getWifiNanHash());
        assertNotNull(filter.toString());
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void createWifiNanTransportBlockFilterFromBuilder() {
        TransportBlockFilter filter = new TransportBlockFilter.Builder(
                OrganizationId.WIFI_ALLIANCE_NEIGHBOR_AWARENESS_NETWORKING)
                .setTdsFlags(TEST_TDS_FLAG, TEST_TDS_FLAG_MASK)
                .setWifiNanHash(TEST_VALID_WIFI_NAN_HASH).build();
        assertEquals(OrganizationId.WIFI_ALLIANCE_NEIGHBOR_AWARENESS_NETWORKING, filter.getOrgId());
        assertEquals(TEST_TDS_FLAG, filter.getTdsFlags());
        assertEquals(TEST_TDS_FLAG_MASK, filter.getTdsFlagsMask());
        assertNull(filter.getTransportData());
        assertNull(filter.getTransportDataMask());
        assertArrayEquals(TEST_VALID_WIFI_NAN_HASH, filter.getWifiNanHash());
        assertNotNull(filter.toString());
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void cannotSetWifiNanHashForWrongOrgId() {
        TransportBlockFilter.Builder builder = new TransportBlockFilter.Builder(
                OrganizationId.WIFI_ALLIANCE_SERVICE_ADVERTISEMENT)
                .setTdsFlags(TEST_TDS_FLAG, TEST_TDS_FLAG_MASK);
        assertThrows(IllegalArgumentException.class,
                () -> builder.setWifiNanHash(TEST_VALID_WIFI_NAN_HASH));
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setTransportDataNonWifiNan() {
        TransportBlockFilter.Builder builder = new TransportBlockFilter.Builder(
                OrganizationId.WIFI_ALLIANCE_SERVICE_ADVERTISEMENT)
                .setTdsFlags(TEST_TDS_FLAG, TEST_TDS_FLAG_MASK);
        assertThrows(NullPointerException.class,
                () -> builder.setTransportData(TEST_TRANSPORT_DATA, null));
        assertThrows(NullPointerException.class,
                () -> builder.setTransportData(null, TEST_TRANSPORT_DATA_MASK));
        assertThrows(NullPointerException.class,
                () -> builder.setTransportData(null, null));
        assertThrows(IllegalArgumentException.class,
                () -> builder.setTransportData(TEST_TRANSPORT_DATA, TEST_TRANSPORT_DATA_MASK_LONG));
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setTransportDataWifiNan() {
        TransportBlockFilter.Builder builder = new TransportBlockFilter.Builder(
                OrganizationId.WIFI_ALLIANCE_NEIGHBOR_AWARENESS_NETWORKING)
                .setTdsFlags(TEST_TDS_FLAG, TEST_TDS_FLAG_MASK);
        assertThrows(IllegalArgumentException.class,
                () -> builder.setTransportData(TEST_TRANSPORT_DATA, null));
        assertThrows(IllegalArgumentException.class,
                () -> builder.setTransportData(null, TEST_TRANSPORT_DATA_MASK));
        assertThrows(IllegalArgumentException.class,
                () -> builder.setTransportData(null, null));
        assertThrows(IllegalArgumentException.class,
                () -> builder.setTransportData(TEST_TRANSPORT_DATA_LONG,
                        TEST_TRANSPORT_DATA_MASK_LONG));
    }
}
