/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.car.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.car.feature.Flags;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.host.HostFlagsValueProvider;

import com.android.car.wifi.CarWifiDumpProto;
import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.ProtoUtils;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.AfterClassWithInfo;
import com.android.tradefed.testtype.junit4.BeforeClassWithInfo;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

@RunWith(DeviceJUnit4ClassRunner.class)
public final class CarWifiHostTest extends CarHostJUnit4TestCase {
    private static final long TIMEOUT_MS = TimeUnit.SECONDS.toMillis(15);

    private static final String GET_PERSISTENT_TETHERING =
            "settings get global android.car.ENABLE_PERSISTENT_TETHERING";
    private static final String ENABLE_PERSISTENT_TETHERING =
            "settings put global android.car.ENABLE_PERSISTENT_TETHERING true";
    private static final String DISABLE_PERSISTENT_TETHERING =
            "settings put global android.car.ENABLE_PERSISTENT_TETHERING false";
    private static final String CMD_DUMPSYS_WIFI =
            "dumpsys car_service --services CarWifiService";
    private static final String CMD_DUMPSYS_WIFI_PROTO =
            "dumpsys car_service --services CarWifiService --proto";
    private static final String GET_TETHERING_CAPABILITY =
            "cmd car_service get-tethering-capability";
    private static final String WIFI_HOTSPOT_ON = "cmd wifi start-softap CarWifiService open";
    private static final String WIFI_HOTSPOT_OFF = "cmd wifi stop-softap";
    private static boolean sTetheringStatusBefore;
    private static boolean sTetheringPersistingBefore;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            HostFlagsValueProvider.createCheckFlagsRule(this::getDevice);

    /**
     * Prepares the device to restore back to original state post-test.
     *
     * @param testInfo Test Information
     * @throws Exception if connection with device is lost and cannot be recovered.
     */
    @BeforeClassWithInfo
    public static void beforeClassWithDevice(TestInformation testInfo) throws Exception {
        // TODO: b/324961709 - Re-factor to use proto dump
        sTetheringStatusBefore = testInfo.getDevice().executeShellCommand(CMD_DUMPSYS_WIFI)
                .contains("Tethering enabled: true");
        sTetheringPersistingBefore = testInfo.getDevice().executeShellCommand(
                GET_PERSISTENT_TETHERING).contains("true");
    }

    /**
     * Restores original state to conditions before testing.
     *
     * @param testInfo Test Information
     * @throws Exception if connection with device is lost and cannot be recovered.
     */
    @AfterClassWithInfo
    public static void afterClassWithInfo(TestInformation testInfo) throws Exception {
        String hotspotCommand = (sTetheringStatusBefore ? WIFI_HOTSPOT_ON : WIFI_HOTSPOT_OFF);
        testInfo.getDevice().executeShellCommand(hotspotCommand);

        String persistTetheringCommand =
                (sTetheringPersistingBefore ? ENABLE_PERSISTENT_TETHERING
                        : DISABLE_PERSISTENT_TETHERING);
        testInfo.getDevice().executeShellCommand(persistTetheringCommand);
    }

    @Test
    @RequiresFlagsEnabled({Flags.FLAG_PERSIST_AP_SETTINGS, Flags.FLAG_CAR_DUMP_TO_PROTO})
    @ApiTest(apis = {"android.car.settings.CarSettings#ENABLE_PERSISTENT_TETHERING"})
    public void testPersistTetheringCarSetting_enablingWithCapability_autoShutdownDisabled()
            throws Exception {
        assumeTrue("Skipping test: tethering capability disabled",
                isPersistTetheringCapabilityEnabled());
        executeCommand(ENABLE_PERSISTENT_TETHERING);
        assertThat(isAutoShutdownEnabled()).isFalse();
    }

    @Test
    @RequiresFlagsEnabled({Flags.FLAG_PERSIST_AP_SETTINGS, Flags.FLAG_CAR_DUMP_TO_PROTO})
    @ApiTest(apis = {"android.car.settings.CarSettings#ENABLE_PERSISTENT_TETHERING"})
    public void testPersistTetheringCarSetting_disablingWithCapability_autoShutdownEnabled()
            throws Exception {
        assumeTrue("Skipping test: tethering capability disabled",
                isPersistTetheringCapabilityEnabled());
        executeCommand(DISABLE_PERSISTENT_TETHERING);
        assertThat(isAutoShutdownEnabled()).isTrue();
    }

    @Test
    @RequiresFlagsEnabled({Flags.FLAG_PERSIST_AP_SETTINGS, Flags.FLAG_CAR_DUMP_TO_PROTO})
    @ApiTest(apis = {"android.car.settings.CarSettings#ENABLE_PERSISTENT_TETHERING"})
    public void testPersistTetheringCarSetting_enablingNoCapability_autoShutdownUnchanged()
            throws Exception {
        assumeFalse("Skipping test: tethering capability enabled",
                isPersistTetheringCapabilityEnabled());
        boolean autoShutdownEnabledBefore = isAutoShutdownEnabled();
        executeCommand(ENABLE_PERSISTENT_TETHERING);
        assertThat(isAutoShutdownEnabled()).isEqualTo(autoShutdownEnabledBefore);
    }

    @Test
    @RequiresFlagsEnabled({Flags.FLAG_PERSIST_AP_SETTINGS, Flags.FLAG_CAR_DUMP_TO_PROTO})
    @ApiTest(apis = {"android.car.settings.CarSettings#ENABLE_PERSISTENT_TETHERING"})
    public void testPersistTetheringCarSetting_disablingNoCapability_autoShutdownUnchanged()
            throws Exception {
        assumeFalse("Skipping test: tethering capability enabled",
                isPersistTetheringCapabilityEnabled());
        boolean autoShutdownEnabledBefore = isAutoShutdownEnabled();
        executeCommand(DISABLE_PERSISTENT_TETHERING);
        assertThat(isAutoShutdownEnabled()).isEqualTo(autoShutdownEnabledBefore);
    }

    @Test
    @RequiresFlagsEnabled({Flags.FLAG_PERSIST_AP_SETTINGS, Flags.FLAG_CAR_DUMP_TO_PROTO})
    @ApiTest(apis = {"android.car.wifi.CarWifiManager#canControlPersistTetheringSettings",
            "android.car.settings.CarSettings#ENABLE_PERSISTENT_TETHERING"})
    public void testPersistTetheringCarSetting_withCapabilityTetheringEnabled_tetheringOnReboot()
            throws Exception {
        assumeTrue("Skipping test: tethering capability disabled",
                isPersistTetheringCapabilityEnabled());

        enablePersistTetheringAndReboot(/* enableTethering= */ true);

        PollingCheck.check("Tethering NOT enabled", TIMEOUT_MS, this::isTetheringEnabled);
        assertThat(isAutoShutdownEnabled()).isFalse();
    }

    @Test
    @RequiresFlagsEnabled({Flags.FLAG_PERSIST_AP_SETTINGS, Flags.FLAG_CAR_DUMP_TO_PROTO})
    @ApiTest(apis = {"android.car.wifi.CarWifiManager#canControlPersistTetheringSettings",
            "android.car.settings.CarSettings#ENABLE_PERSISTENT_TETHERING"})
    public void testPersistTetheringCarSetting_withCapabilityTetheringDisabled_noTetheringOnReboot()
            throws Exception {
        assumeTrue("Skipping test: tethering capability disabled",
                isPersistTetheringCapabilityEnabled());

        enablePersistTetheringAndReboot(/* enableTethering= */ false);

        assertThrows(AssertionError.class,
                () -> PollingCheck.check("Tethering NOT enabled", TIMEOUT_MS,
                        this::isTetheringEnabled));
    }

    @Test
    @RequiresFlagsEnabled({Flags.FLAG_PERSIST_AP_SETTINGS, Flags.FLAG_CAR_DUMP_TO_PROTO})
    @ApiTest(apis = {"android.car.wifi.CarWifiManager#canControlPersistTetheringSettings",
            "android.car.settings.CarSettings#ENABLE_PERSISTENT_TETHERING"})
    public void testPersistTetheringCarSetting_noCapabilityTetheringEnabled_noTetheringOnReboot()
            throws Exception {
        assumeFalse("Skipping test: tethering capability enabled",
                isPersistTetheringCapabilityEnabled());

        enablePersistTetheringAndReboot(/* enableTethering= */ true);

        assertThrows(AssertionError.class,
                () -> PollingCheck.check("Tethering NOT enabled", TIMEOUT_MS,
                        this::isTetheringEnabled));
    }

    private boolean isTetheringEnabled() throws Exception {
        CarWifiDumpProto carWifiDump = ProtoUtils.getProto(getDevice(),
                CarWifiDumpProto.parser(), CMD_DUMPSYS_WIFI_PROTO);
        return carWifiDump.getTetheringEnabled();
    }

    private boolean isAutoShutdownEnabled() throws Exception {
        CarWifiDumpProto carWifiDump = ProtoUtils.getProto(getDevice(),
                CarWifiDumpProto.parser(), CMD_DUMPSYS_WIFI_PROTO);
        return carWifiDump.getAutoShutdownEnabled();
    }

    private boolean isPersistTetheringCapabilityEnabled() throws Exception {
        String output = executeCommand(GET_TETHERING_CAPABILITY);
        return output.contains("true");
    }

    private void enablePersistTetheringAndReboot(boolean enableTethering) throws Exception {
        String hotspotCommand = (enableTethering ? WIFI_HOTSPOT_ON : WIFI_HOTSPOT_OFF);
        executeCommand(hotspotCommand);

        executeCommand(ENABLE_PERSISTENT_TETHERING);

        reboot();
        waitForCarServiceReady();
        waitForUserInitialized(/* userId= */ 0);
    }
}
