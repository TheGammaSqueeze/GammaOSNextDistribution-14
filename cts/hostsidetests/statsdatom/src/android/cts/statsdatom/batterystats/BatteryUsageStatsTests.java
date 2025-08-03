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

package android.cts.statsdatom.batterystats;

import static com.google.common.truth.Truth.assertThat;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;
import android.os.PowerComponentEnum;

import com.android.internal.os.StatsdConfigProto;
import com.android.os.AtomsProto;
import com.android.os.AtomsProto.BatteryUsageStatsAtomsProto;
import com.android.os.AtomsProto.BatteryUsageStatsAtomsProto.BatteryConsumerData;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.util.RunUtil;

import java.util.List;
import java.util.function.Function;

public class BatteryUsageStatsTests extends DeviceTestCase implements IBuildReceiver {
    private IBuildInfo mCtsBuild;

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

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

    public void testBatteryUsageStatsSinceReset() throws Exception {
        if (!hasBattery()) {
            return;
        }

        runBatteryUsageStatsAtomTest(AtomsProto.Atom.BATTERY_USAGE_STATS_SINCE_RESET_FIELD_NUMBER,
                atom -> atom.getBatteryUsageStatsSinceReset().getBatteryUsageStats());
    }

    public void testBatteryUsageStatsSinceResetUsingPowerProfileModel() throws Exception {
        if (!hasBattery()) {
            return;
        }

        runBatteryUsageStatsAtomTest(
                AtomsProto.Atom.BATTERY_USAGE_STATS_SINCE_RESET_USING_POWER_PROFILE_MODEL_FIELD_NUMBER,
                atom -> atom.getBatteryUsageStatsSinceResetUsingPowerProfileModel()
                        .getBatteryUsageStats());
    }

    private void runBatteryUsageStatsAtomTest(int atomFieldNumber,
            Function<AtomsProto.Atom, BatteryUsageStatsAtomsProto> getter) throws Exception {
        unplugDevice();
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);
        try {
            DeviceUtils.runActivity(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                    "StatsdCtsForegroundActivity", "action", "action.drain_power", 5_000);
        } finally {
            plugInDevice();
        }

        StatsdConfigProto.StatsdConfig.Builder config =
                ConfigUtils.createConfigBuilder(DeviceUtils.STATSD_ATOM_TEST_PKG);
        ConfigUtils.addGaugeMetric(config, atomFieldNumber);
        ConfigUtils.uploadConfig(getDevice(), config);

        // Trigger atom pull.
        AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);

        final List<AtomsProto.Atom> atoms = ReportUtils.getGaugeMetricAtoms(getDevice());
        assertThat(atoms.size()).isAtLeast(1);
        for (final AtomsProto.Atom atom : atoms) {
            final BatteryUsageStatsAtomsProto batteryUsageStats = getter.apply(atom);
            assertBatteryUsageStatsAtom(batteryUsageStats);
        }
    }

    private void assertBatteryUsageStatsAtom(BatteryUsageStatsAtomsProto batteryUsageStats)
            throws Exception {
        assertThat(batteryUsageStats.getSessionEndMillis()).isGreaterThan(
                getDeviceTimeMs() - 5 * 60 * 1000);
        assertThat(batteryUsageStats.getSessionDurationMillis()).isGreaterThan(0);

        final BatteryConsumerData deviceBatteryConsumer =
                batteryUsageStats.getDeviceBatteryConsumer();
        assertThat(deviceBatteryConsumer.getTotalConsumedPowerDeciCoulombs()).isAtLeast(0);
        for (BatteryConsumerData.PowerComponentUsage powerComponent :
                deviceBatteryConsumer.getPowerComponentsList()) {
            assertThat(powerComponent.getDurationMillis()).isAtLeast(0);
            assertThat(powerComponent.getPowerDeciCoulombs()).isAtLeast(0);
        }

        boolean hasAppData = false;
        final List<BatteryUsageStatsAtomsProto.UidBatteryConsumer> uidBatteryConsumers =
                batteryUsageStats.getUidBatteryConsumersList();
        for (BatteryUsageStatsAtomsProto.UidBatteryConsumer consumer : uidBatteryConsumers) {
            if (consumer.getBatteryConsumerData().getTotalConsumedPowerDeciCoulombs() > 0
                    || consumer.getTimeInForegroundMillis() > 0
                    || consumer.getTimeInBackgroundMillis() > 0) {
                hasAppData = true;
                break;
            }
        }

        assertThat(hasAppData).isTrue();

        final int testUid = DeviceUtils.getStatsdTestAppUid(getDevice());
        BatteryUsageStatsAtomsProto.UidBatteryConsumer testConsumer = null;
        for (BatteryUsageStatsAtomsProto.UidBatteryConsumer consumer : uidBatteryConsumers) {
            if (consumer.getUid() == testUid) {
                testConsumer = consumer;
                break;
            }
        }

        // If the test app consumed a measurable amount of power, the break-down
        // by process state should also be present in the atom.
        if (testConsumer != null) {
            long consumedByCpu = 0;
            for (BatteryConsumerData.PowerComponentUsage pcu :
                    testConsumer.getBatteryConsumerData().getPowerComponentsList()) {
                if (pcu.getComponent() == PowerComponentEnum.POWER_COMPONENT_CPU.getNumber()) {
                    consumedByCpu = pcu.getPowerDeciCoulombs();
                    break;
                }
            }
            if (consumedByCpu > 10) {
                boolean hasProcStateData = false;
                for (int i = 0; i < testConsumer.getBatteryConsumerData().getSlicesCount(); i++) {
                    final BatteryConsumerData.PowerComponentUsageSlice slice =
                            testConsumer.getBatteryConsumerData().getSlices(i);
                    if (slice.getProcessState()
                            != BatteryConsumerData.PowerComponentUsageSlice.ProcessState.UNSPECIFIED
                            && (slice.getPowerComponent().getPowerDeciCoulombs() > 0
                                || slice.getPowerComponent().getDurationMillis() > 0)) {
                        hasProcStateData = true;
                        break;
                    }
                }

                assertThat(hasProcStateData).isTrue();
            }
        }
    }

    private boolean hasBattery() throws DeviceNotAvailableException  {
        final String batteryinfo = getDevice().executeShellCommand("dumpsys battery");
        return batteryinfo.contains("present: true");
    }

    private void unplugDevice() throws Exception {
        DeviceUtils.unplugDevice(getDevice());
        getDevice().executeShellCommand("dumpsys battery suspend_input");
        getDevice().executeShellCommand("dumpsys batterystats --reset");
    }

    private void plugInDevice() throws Exception {
        DeviceUtils.resetBatteryStatus(getDevice());
    }

    private long getDeviceTimeMs() throws Exception {
        final String timeMs = getDevice().executeShellCommand("date +%s%3N");
        return Long.parseLong(timeMs.trim());
    }
}
