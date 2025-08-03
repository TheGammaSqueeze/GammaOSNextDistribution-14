/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.cts.packagemanager.stats.host;

import static com.google.common.truth.Truth.assertThat;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.os.AtomsProto;
import com.android.tradefed.util.RunUtil;

import com.google.common.truth.Truth;

import java.util.ArrayList;
import java.util.List;

public class InstalledIncrementalPackageStatsTests extends PackageManagerStatsTestsBase {
    private static final String TEST_INSTALL_APK = "CtsStatsdAtomEmptyApp.apk";
    private static final String TEST_INSTALL_PACKAGE =
            "com.android.cts.packagemanager.stats.emptyapp";
    private static final String TEST_INSTALL_APK2 = "CtsStatsdAtomEmptyApp2.apk";
    private static final String TEST_INSTALL_PACKAGE2 =
            "com.android.cts.packagemanager.stats.emptyapp2";

    @Override
    protected void tearDown() throws Exception {
        getDevice().uninstallPackage(TEST_INSTALL_PACKAGE);
        getDevice().uninstallPackage(TEST_INSTALL_PACKAGE2);
        super.tearDown();
    }

    // Install 2 incremental packages and check if their UIDs are included in the pulled metrics
    public void testInstalledIncrementalMetricsReported() throws Throwable {
        if (!Utils.hasIncrementalFeature(getDevice())) {
            return;
        }
        ConfigUtils.uploadConfigForPulledAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.INSTALLED_INCREMENTAL_PACKAGE_FIELD_NUMBER);
        long currentEpochTimeSeconds = Math.floorDiv(getDevice().getDeviceDate(), 1000);
        // Install 2 incremental packages
        installPackageUsingIncremental(new String[]{TEST_INSTALL_APK});
        assertTrue(getDevice().isPackageInstalled(TEST_INSTALL_PACKAGE,
                String.valueOf(getDevice().getCurrentUser())));
        installPackageUsingIncremental(new String[]{TEST_INSTALL_APK2});
        assertTrue(getDevice().isPackageInstalled(TEST_INSTALL_PACKAGE2,
                String.valueOf(getDevice().getCurrentUser())));
        AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<AtomsProto.Atom> data = ReportUtils.getGaugeMetricAtoms(getDevice());
        assertThat(data).isNotNull();

        int[] expectedAppIds = new int[]{
                getAppUid(TEST_INSTALL_PACKAGE), getAppUid(TEST_INSTALL_PACKAGE2)};
        List<AtomsProto.Atom> filterMetrics = filterTestAppMetrics(expectedAppIds, data);
        assertEquals(2, filterMetrics.size());

        // The order of the UIDs in the metrics can be different from the order of the installations
        for (AtomsProto.Atom atom : data) {
            assertFalse(atom.getInstalledIncrementalPackage().getIsLoading());
            Truth.assertThat(atom.getInstalledIncrementalPackage().getLoadingCompletedTimestamp()
                    ).isGreaterThan(currentEpochTimeSeconds);
        }
    }

    private List<AtomsProto.Atom> filterTestAppMetrics(int[] appIds,
            List<AtomsProto.Atom> metricData) {
        List<AtomsProto.Atom> result = new ArrayList<>();
        for (AtomsProto.Atom atom : metricData) {
            int uid = atom.getInstalledIncrementalPackage().getUid();
            if (contains(appIds, uid)) {
                result.add(atom);
            }
        }
        return result;
    }

    private boolean contains(int[] array, int value) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] == value) {
                return true;
            }
        }
        return false;
    }
}
