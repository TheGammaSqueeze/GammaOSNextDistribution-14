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

package android.cts.statsdatom.appops;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.os.AtomsProto;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.util.RunUtil;

import com.google.protobuf.Descriptors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class AppOpsTests extends DeviceTestCase implements IBuildReceiver {
    private static final int NUM_APP_OPS = AtomsProto.AttributedAppOps.getDefaultInstance().getOp().
            getDescriptorForType().getValues().size() - 1;

    private static final int APP_OP_RECORD_AUDIO = 27;
    private static final int APP_OP_RECORD_AUDIO_HOTWORD = 102;
    private static final int APP_OP_ACCESS_RESTRICTED_SETTINGS = 119;

    private static final String FEATURE_AUTOMOTIVE = "android.hardware.type.automotive";
    private static final String FEATURE_LEANBACK_ONLY = "android.software.leanback_only";

    /**
     * Some ops are only available to specific dynamic uids and are otherwise transformed to less
     * privileged ops. For example, RECORD_AUDIO_HOTWORD is downgraded to RECORD_AUDIO. This stores
     * a mapping from an op to the op it can be transformed from.
     */
    private final Map<Integer, Integer> mTransformedFromOp = new HashMap<>();

    private IBuildInfo mCtsBuild;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mTransformedFromOp.clear();
        // The hotword op is allowed to all UIDs on some devices.
        boolean hotwordDetectionServiceRequired = Boolean.parseBoolean(
                getDevice().executeShellCommand(
                        "getprop ro.hotword.detection_service_required").trim());
        if (!(DeviceUtils.hasFeature(getDevice(), FEATURE_AUTOMOTIVE)
                || DeviceUtils.hasFeature(getDevice(), FEATURE_LEANBACK_ONLY)
                || !hotwordDetectionServiceRequired)) {
            mTransformedFromOp.put(APP_OP_RECORD_AUDIO, APP_OP_RECORD_AUDIO_HOTWORD);
        }

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

    public void testAppOps() throws Exception {
        // Set up what to collect
        ConfigUtils.uploadConfigForPulledAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.APP_OPS_FIELD_NUMBER);

        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), ".AtomTests", "testAppOps");
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);

        // Pull a report
        AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);

        ArrayList<Integer> expectedOps = new ArrayList<>();
        Set<Integer> transformedOps = new HashSet<>(mTransformedFromOp.values());
        for (int i = 0; i < NUM_APP_OPS; i++) {
            // Ignore access restricted setting as it cannot be read by normal app.
            if (i == APP_OP_ACCESS_RESTRICTED_SETTINGS) {
                continue;
            }
            if (!transformedOps.contains(i)) {
                expectedOps.add(i);
            }
        }

        for (Descriptors.EnumValueDescriptor valueDescriptor :
                AtomsProto.AttributedAppOps.getDefaultInstance().getOp().getDescriptorForType()
                        .getValues()) {
            if (valueDescriptor.getOptions().hasDeprecated()) {
                // Deprecated app op, remove from list of expected ones.
                expectedOps.remove(expectedOps.indexOf(valueDescriptor.getNumber()));
            }
        }
        for (AtomsProto.Atom atom : ReportUtils.getGaugeMetricAtoms(getDevice())) {

            AtomsProto.AppOps appOps = atom.getAppOps();
            if (appOps.getPackageName().equals(DeviceUtils.STATSD_ATOM_TEST_PKG)) {
                if (appOps.getOpId().getNumber() == -1) {
                    continue;
                }
                long totalNoted = appOps.getTrustedForegroundGrantedCount()
                        + appOps.getTrustedBackgroundGrantedCount()
                        + appOps.getTrustedForegroundRejectedCount()
                        + appOps.getTrustedBackgroundRejectedCount();
                int expectedNoted =
                        appOps.getOpId().getNumber() + 1
                                + computeExpectedTransformedNoted(appOps.getOpId().getNumber());
                assertWithMessage("Operation in APP_OPS_ENUM_MAP: " + appOps.getOpId().getNumber())
                        .that(totalNoted).isEqualTo(expectedNoted);
                assertWithMessage("Unexpected Op reported").that(expectedOps).contains(
                        appOps.getOpId().getNumber());
                expectedOps.remove(expectedOps.indexOf(appOps.getOpId().getNumber()));
            }
        }
        assertWithMessage("Logging app op ids are missing in report.").that(expectedOps).isEmpty();
    }

    private int computeExpectedTransformedNoted(int op) {
        if (!mTransformedFromOp.containsKey(op)) {
            return 0;
        }
        return mTransformedFromOp.get(op) + 1;
    }
}