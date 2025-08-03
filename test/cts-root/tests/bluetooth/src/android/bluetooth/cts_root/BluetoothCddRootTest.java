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

package android.bluetooth.cts_root;

import static android.Manifest.permission.BLUETOOTH_CONNECT;
import static android.Manifest.permission.BLUETOOTH_PRIVILEGED;
import static android.Manifest.permission.BLUETOOTH_SCAN;
import static android.Manifest.permission.DUMP;
import static android.Manifest.permission.PACKAGE_USAGE_STATS;

import static com.google.common.truth.Truth.assertThat;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.cts.BTAdapterUtils;
import android.bluetooth.cts.TestUtils;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.CddTest;
import com.android.helpers.StatsdHelper;
import com.android.os.nano.AtomsProto;
import com.android.os.nano.StatsLog;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.Set;

/**
 * Test cases that can only run in rooted environments
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class BluetoothCddRootTest {
    private static final String TAG = BluetoothCddRootTest.class.getSimpleName();
    private static final int BLUETOOTH_CORE_SPECIFICATION_4_2 = 0x08;
    private static final int BLUETOOTH_CORE_SPECIFICATION_5_0 = 0x09;
    private static final int BLUETOOTH_LOCAL_VERSION_REPORTED_ATOM_ID = 530;
    // Some devices need some extra time after entering STATE_OFF
    private static final int BLUETOOTH_TOGGLE_DELAY_MS = 2000;

    private Context mContext;
    private boolean mHasBluetooth;
    private BluetoothAdapter mAdapter;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mHasBluetooth = TestUtils.hasBluetooth();
        Assume.assumeTrue(mHasBluetooth);
        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT,
                BLUETOOTH_PRIVILEGED, BLUETOOTH_SCAN, DUMP, PACKAGE_USAGE_STATS);
        assertThat(TestUtils.getAdoptedShellPermissions()).containsAtLeast(BLUETOOTH_CONNECT,
                BLUETOOTH_PRIVILEGED, BLUETOOTH_SCAN, DUMP, PACKAGE_USAGE_STATS);
        mAdapter = TestUtils.getBluetoothAdapterOrDie();
        if (mAdapter.isEnabled()) {
            assertThat(BTAdapterUtils.disableAdapter(mAdapter, mContext)).isTrue();
            try {
                Thread.sleep(BLUETOOTH_TOGGLE_DELAY_MS);
            } catch (InterruptedException ignored) { }
        }
    }

    @After
    public void tearDown() {
        if (!mHasBluetooth) {
            return;
        }
        if (mAdapter != null && mAdapter.getState() != BluetoothAdapter.STATE_OFF) {
            if (mAdapter.getState() == BluetoothAdapter.STATE_ON) {
                assertThat(BTAdapterUtils.disableAdapter(mAdapter, mContext)).isTrue();
            }
            try {
                Thread.sleep(BLUETOOTH_TOGGLE_DELAY_MS);
            } catch (InterruptedException ignored) { }
        }
        mAdapter = null;
        TestUtils.dropPermissionAsShellUid();
    }

    @CddTest(requirements = {"7.4.3/C-1-1"})
    @Test
    public void test_C_1_1_VrHighPerformance() {
        Assume.assumeTrue(mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_VR_MODE_HIGH_PERFORMANCE));
        assertThat(mHasBluetooth).isTrue();
        AtomsProto.BluetoothLocalVersionsReported version = getBluetoothVersion();
        assertThat(version.hciVersion).isAtLeast(BLUETOOTH_CORE_SPECIFICATION_4_2);
        assertThat(TestUtils.isBleSupported(mContext)).isTrue();
        // TODO: Enforce LE data length extension
    }

    @CddTest(requirements = {"7.4.3/H-1-1"})
    @Test
    public void test_H_1_1_AshaRequirements() {
        Assume.assumeTrue(mHasBluetooth);
        Assume.assumeTrue("Skip 7.4.3/H-1-1 test for non-BLE devices",
                TestUtils.isBleSupported(mContext));
        Assume.assumeFalse("Skip 7.4.3/H-1-1 test for automotive devices",
                TestUtils.isAutomotive(mContext));
        Assume.assumeFalse("Skip 7.4.3/H-1-1 test for watch devices",
                TestUtils.isWatch(mContext));
        Assume.assumeFalse("Skip 7.4.3/H-1-1 test for TV devices",
                TestUtils.isTv(mContext));
        AtomsProto.BluetoothLocalVersionsReported version = getBluetoothVersion();
        Assume.assumeTrue(version.hciVersion >= BLUETOOTH_CORE_SPECIFICATION_5_0);
        assertThat(BTAdapterUtils.enableAdapter(mAdapter, mContext)).isTrue();
        assertThat(mAdapter.getSupportedProfiles()).contains(BluetoothProfile.HEARING_AID);
        TestUtils.BluetoothCtsServiceConnector connector =
                new TestUtils.BluetoothCtsServiceConnector(TAG,
                        BluetoothProfile.HEARING_AID, mAdapter, mContext);
        try {
            assertThat(connector.openProfileProxyAsync()).isTrue();
            assertThat(connector.waitForProfileConnect()).isTrue();
            assertThat(connector.getProfileProxy()).isNotNull();
        } finally {
            connector.closeProfileProxy();
        }
    }

    @CddTest(requirements = {"7.4.3/C-12-1"})
    @Test
    public void test_C_12_1_Bluetooth5Requirements() {
        Assume.assumeTrue(mHasBluetooth);
        AtomsProto.BluetoothLocalVersionsReported version = getBluetoothVersion();
        if (version.hciVersion >= BLUETOOTH_CORE_SPECIFICATION_5_0) {
            // Assert LMP Version is larger than or equal to HCI version
            assertThat(version.lmpVersion).isAtLeast(version.hciVersion);
            assertThat(mAdapter.isLe2MPhySupported()).isTrue();
            assertThat(mAdapter.isLeCodedPhySupported()).isTrue();
            assertThat(mAdapter.isLeExtendedAdvertisingSupported()).isTrue();
            assertThat(mAdapter.isLePeriodicAdvertisingSupported()).isTrue();
            assertThat(mAdapter.isMultipleAdvertisementSupported()).isTrue();
            // TODO: Enforce number of advertisement supported
            // TODO: Enforce number of concurrent LE-ACL connections supported
        }
    }

    /**
     * Get Bluetooth version information. Bluetooth is enabled after this method call.
     *
     * Requires ROOT access on the running Android device
     *
     * @return Bluetooth version proto
     */
    private AtomsProto.BluetoothLocalVersionsReported getBluetoothVersion() {
        Set<String> permissionsAdopted = TestUtils.getAdoptedShellPermissions();
        String[] permissionArray = permissionsAdopted.toArray(String[]::new);
        if (mAdapter.isEnabled()) {
            assertThat(BTAdapterUtils.disableAdapter(mAdapter, mContext)).isTrue();
            try {
                Thread.sleep(BLUETOOTH_TOGGLE_DELAY_MS);
            } catch (InterruptedException ignored) { }
        }
        StatsdHelper statsdHelper = new StatsdHelper();
        // Requires root to enable metrics
        assertThat(statsdHelper.addEventConfig(
                List.of(BLUETOOTH_LOCAL_VERSION_REPORTED_ATOM_ID))).isTrue();
        assertThat(BTAdapterUtils.enableAdapter(mAdapter, mContext)).isTrue();
        List<StatsLog.EventMetricData> metrics = statsdHelper.getEventMetrics();
        AtomsProto.BluetoothLocalVersionsReported summaryAtom =
                new AtomsProto.BluetoothLocalVersionsReported();
        // When multiple atoms are reported use the maximum value of HCI version
        // They should really all be the same
        int i = 0;
        for (StatsLog.EventMetricData data : metrics) {
            AtomsProto.BluetoothLocalVersionsReported atom =
                    data.atom.getBluetoothLocalVersionsReported();
            if (atom == null) {
                continue;
            }
            Log.i("BluetoothCddRootTest", "[" + i + "] HCI version is " + atom.hciVersion
                    + ", LMP version is " + atom.lmpVersion);
            assertThat(atom.lmpManufacturerName).isGreaterThan(0);
            assertThat(atom.lmpVersion).isGreaterThan(0);
            assertThat(atom.hciVersion).isGreaterThan(0);
            if (atom.hciVersion > summaryAtom.hciVersion) {
                summaryAtom.lmpManufacturerName = atom.lmpManufacturerName;
                summaryAtom.lmpVersion = atom.lmpVersion;
                summaryAtom.lmpSubversion = atom.lmpSubversion;
                summaryAtom.hciVersion = atom.hciVersion;
                summaryAtom.hciRevision = atom.hciRevision;
            }
            i++;
        }
        TestUtils.dropPermissionAsShellUid();
        TestUtils.adoptPermissionAsShellUid(permissionArray);
        return summaryAtom;
    }
}
