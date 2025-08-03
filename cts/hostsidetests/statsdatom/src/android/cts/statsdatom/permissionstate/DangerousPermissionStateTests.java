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

package android.cts.statsdatom.permissionstate;

import com.android.tradefed.util.RunUtil;
import static com.google.common.truth.Truth.assertThat;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.os.AtomsProto;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;

import java.util.ArrayList;
import java.util.List;

public class DangerousPermissionStateTests extends DeviceTestCase implements IBuildReceiver {
    private static final int FLAG_PERMISSION_USER_SENSITIVE_WHEN_GRANTED = 1 << 8;

    private IBuildInfo mCtsBuild;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        assertThat(mCtsBuild).isNotNull();
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.installStatsdTestApp(getDevice(), mCtsBuild);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
    }

    @Override
    protected void tearDown() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.uninstallStatsdTestApp(getDevice());
        super.tearDown();
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    public void testDangerousPermissionState() throws Exception {

        final int FLAG_PERMISSION_USER_SENSITIVE_WHEN_DENIED = 1 << 9;
        final int PROTECTION_FLAG_DANGEROUS = 1;
        final int PROTECTION_FLAG_INSTANT = 0x1000;

        // Set up what to collect
        ConfigUtils.uploadConfigForPulledAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.DANGEROUS_PERMISSION_STATE_FIELD_NUMBER);

        boolean verifiedKnowPermissionState = false;

        // Pull a report
        AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);

        int testAppId = getAppId(DeviceUtils.getStatsdTestAppUid(getDevice()));

        for (AtomsProto.Atom atom : ReportUtils.getGaugeMetricAtoms(getDevice())) {
            AtomsProto.DangerousPermissionState permissionState = atom.getDangerousPermissionState();

            assertThat(permissionState.getPermissionName()).isNotNull();
            assertThat(permissionState.getUid()).isAtLeast(0);
            assertThat(permissionState.getPackageName()).isNotNull();

            if (getAppId(permissionState.getUid()) == testAppId) {

                if (permissionState.getPermissionName().contains(
                        "ACCESS_FINE_LOCATION")) {
                    assertThat(permissionState.getIsGranted()).isTrue();
                    assertThat(permissionState.getPermissionFlags() & ~(
                            FLAG_PERMISSION_USER_SENSITIVE_WHEN_DENIED
                                    | FLAG_PERMISSION_USER_SENSITIVE_WHEN_GRANTED))
                            .isEqualTo(0);
                    assertThat(permissionState.getProtectionFlags()).isEqualTo(
                            PROTECTION_FLAG_DANGEROUS | PROTECTION_FLAG_INSTANT
                    );

                    verifiedKnowPermissionState = true;
                }
            }
        }

        assertThat(verifiedKnowPermissionState).isTrue();
    }

    public void testDangerousPermissionStateSampled() throws Exception {
        // get full atom for reference
        ConfigUtils.uploadConfigForPulledAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.DANGEROUS_PERMISSION_STATE_FIELD_NUMBER);

        AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);

        List<AtomsProto.DangerousPermissionState> fullDangerousPermissionState = new ArrayList<>();
        for (AtomsProto.Atom atom : ReportUtils.getGaugeMetricAtoms(getDevice())) {
            fullDangerousPermissionState.add(atom.getDangerousPermissionState());
        }

        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice()); // Clears data.
        List<AtomsProto.Atom> gaugeMetricDataList = null;

        // retries in case sampling returns full list or empty list - which should be extremely rare
        for (int attempt = 0; attempt < 10; attempt++) {
            // Set up what to collect
            ConfigUtils.uploadConfigForPulledAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                    AtomsProto.Atom.DANGEROUS_PERMISSION_STATE_SAMPLED_FIELD_NUMBER);

            // Pull a report
            AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
            RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);

            gaugeMetricDataList = ReportUtils.getGaugeMetricAtoms(getDevice());
            if (gaugeMetricDataList.size() > 0
                    && gaugeMetricDataList.size() < fullDangerousPermissionState.size()) {
                break;
            }
            ConfigUtils.removeConfig(getDevice());
            ReportUtils.clearReports(getDevice()); // Clears data.
        }
        assertThat(gaugeMetricDataList.size()).isGreaterThan(0);
        assertThat(gaugeMetricDataList.size()).isLessThan(fullDangerousPermissionState.size());

        long lastUid = -1;
        int fullIndex = 0;

        for (AtomsProto.Atom atom : ReportUtils.getGaugeMetricAtoms(getDevice())) {
            AtomsProto.DangerousPermissionStateSampled permissionState =
                    atom.getDangerousPermissionStateSampled();

            AtomsProto.DangerousPermissionState referenceState
                    = fullDangerousPermissionState.get(fullIndex);

            if (referenceState.getUid() != permissionState.getUid()) {
                // atoms are sampled on uid basis if uid is present, all related permissions must
                // be logged.
                assertThat(permissionState.getUid()).isNotEqualTo(lastUid);
                continue;
            }

            lastUid = permissionState.getUid();

            assertThat(permissionState.getPermissionFlags()).isEqualTo(
                    referenceState.getPermissionFlags());
            assertThat(permissionState.getIsGranted()).isEqualTo(referenceState.getIsGranted());
            assertThat(permissionState.getPermissionName()).isEqualTo(
                    referenceState.getPermissionName());
            assertThat(permissionState.getProtectionFlags()).isEqualTo(
                    referenceState.getProtectionFlags());

            fullIndex++;
        }
    }

    /**
     * The app id from a uid.
     *
     * @param uid The uid of the app
     *
     * @return The app id of the app
     *
     * @see android.os.UserHandle#getAppId
     */
    private static int getAppId(int uid) {
        return uid % 100000;
    }
}
