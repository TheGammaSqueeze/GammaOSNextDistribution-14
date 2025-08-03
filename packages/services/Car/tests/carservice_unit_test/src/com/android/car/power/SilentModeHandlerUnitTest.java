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

package com.android.car.power;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.car.feature.FakeFeatureFlagsImpl;
import android.car.feature.Flags;
import android.car.test.util.TemporaryFile;
import android.os.SystemClock;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Tests for {@link SilentModeHandler}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class SilentModeHandlerUnitTest {
    private static final String BOOT_REASON_NORMAL = "reboot,shell";
    private static final String BOOT_REASON_FORCED_SILENT = "reboot,forcedsilent";
    private static final String BOOT_REASON_FORCED_NON_SILENT = "reboot,forcednonsilent";
    private static final String VALUE_SILENT_MODE = "1";
    private static final String VALUE_NON_SILENT_MODE = "0";
    private static final int BUFFER_TIME_TO_AVOID_RACE_CONDITION = 10000;

    private static final int MAX_POLLING_TRIES = 5;
    private static final int POLLING_DELAY_MS = 50;

    private final FakeFeatureFlagsImpl mFeatureFlags = new FakeFeatureFlagsImpl();
    private final TemporaryFile mFileHwStateMonitoring;
    private final TemporaryFile mFileKernelSilentMode;

    @Mock private CarPowerManagementService mCarPowerManagementService;

    public SilentModeHandlerUnitTest() throws Exception {
        mFileHwStateMonitoring = new TemporaryFile("HW_STATE_MONITORING");
        mFileKernelSilentMode = new TemporaryFile("KERNEL_SILENT_MODE");
        mFeatureFlags.setFlag(
                Flags.FLAG_CAR_POWER_POLICY_REFACTORING, Flags.carPowerPolicyRefactoring());
    }

    @Test
    public void testSilentModeHwStateMonitoring() throws Exception {
        SilentModeHandler handler = new SilentModeHandler(mCarPowerManagementService,
                mFeatureFlags, mFileHwStateMonitoring.getFile().getPath(),
                mFileKernelSilentMode.getFile().getPath(), BOOT_REASON_NORMAL);
        handler.init();

        writeStringToFile(mFileHwStateMonitoring.getFile(), VALUE_SILENT_MODE);

        assertSilentMode(handler, /* isForcedMode= */ false, /* expectedMode= */ true);

        writeStringToFile(mFileHwStateMonitoring.getFile(), VALUE_NON_SILENT_MODE);

        assertSilentMode(handler, /* isForcedMode= */ false, /* expectedMode= */ false);
    }

    @Test
    public void testRebootForForcedSilentMode() throws Exception {
        SilentModeHandler handler = new SilentModeHandler(mCarPowerManagementService,
                mFeatureFlags, mFileHwStateMonitoring.getFile().getPath(),
                mFileKernelSilentMode.getFile().getPath(), BOOT_REASON_FORCED_SILENT);
        handler.init();

        assertWithMessage("Silent mode").that(handler.isSilentMode()).isTrue();

        writeStringToFile(mFileHwStateMonitoring.getFile(), VALUE_SILENT_MODE);

        assertWithMessage("Silent mode in forced mode").that(handler.isSilentMode()).isTrue();
    }

    @Test
    public void testRebootForForcedNonSilentMode() throws Exception {
        SilentModeHandler handler = new SilentModeHandler(mCarPowerManagementService,
                mFeatureFlags, mFileHwStateMonitoring.getFile().getPath(),
                mFileKernelSilentMode.getFile().getPath(), BOOT_REASON_FORCED_NON_SILENT);
        handler.init();

        assertWithMessage("Silent mode").that(handler.isSilentMode()).isFalse();

        writeStringToFile(mFileHwStateMonitoring.getFile(), VALUE_SILENT_MODE);

        assertWithMessage("Silent mode in forced mode").that(handler.isSilentMode()).isFalse();
    }

    @Test
    public void testUpdateKernelSilentMode() throws Exception {
        SilentModeHandler handler = new SilentModeHandler(mCarPowerManagementService,
                mFeatureFlags, mFileHwStateMonitoring.getFile().getPath(),
                mFileKernelSilentMode.getFile().getPath(), BOOT_REASON_NORMAL);
        handler.init();

        handler.updateKernelSilentMode(/* silent= */ true);

        String contents = readFileAsString(mFileKernelSilentMode.getPath());
        assertWithMessage("Kernel silent mode").that(contents).isEqualTo(VALUE_SILENT_MODE);

        handler.updateKernelSilentMode(/* silent= */ false);

        contents = readFileAsString(mFileKernelSilentMode.getPath());
        assertWithMessage("Kernel silent mode").that(contents).isEqualTo(VALUE_NON_SILENT_MODE);
    }

    @Test
    public void testSetSilentMode_normalSilentToForcedSilent() throws Exception {
        testSetSilentMode_toForced(/* initSilentMode= */ true, /* expectedSilentMode= */ true);
    }

    @Test
    public void testSetSilentMode_normalSilentToForcedNonSilent() throws Exception {
        testSetSilentMode_toForced(/* initSilentMode= */ true, /* expectedSilentMode= */ false);
        if (!mFeatureFlags.carPowerPolicyRefactoring()) {
            verify(mCarPowerManagementService, timeout(BUFFER_TIME_TO_AVOID_RACE_CONDITION))
                    .notifySilentModeChange(false);
        }
    }

    @Test
    public void testSetSilentMode_normalNonSilentToForcedSilent() throws Exception {
        testSetSilentMode_toForced(/* initSilentMode= */ false, /* expectedSilentMode= */ true);
        if (!mFeatureFlags.carPowerPolicyRefactoring()) {
            verify(mCarPowerManagementService, timeout(BUFFER_TIME_TO_AVOID_RACE_CONDITION))
                    .notifySilentModeChange(true);
        }
    }

    @Test
    public void testSetSilentMode_normalNonSilentToForcedNonSilent() throws Exception {
        testSetSilentMode_toForced(/* initSilentMode= */ false, /* expectedSilentMode= */ false);
    }

    @Test
    public void testSetSilentMode_forcedSilentToNonForced() throws Exception {
        testSetSilentMode_toNonForced(/* initSilentMode= */ true);
    }

    @Test
    public void testSetSilentMode_forcedNonSilentToNonForced() throws Exception {
        testSetSilentMode_toNonForced(/* initSilentMode= */ false);
    }

    @Test
    public void testStopSilentModeHwStateMonitoring() throws Exception {
        SilentModeHandler handler = new SilentModeHandler(mCarPowerManagementService,
                mFeatureFlags, mFileHwStateMonitoring.getFile().getPath(),
                mFileKernelSilentMode.getFile().getPath(), BOOT_REASON_NORMAL);
        handler.init();
        writeStringToFile(mFileHwStateMonitoring.getFile(), VALUE_SILENT_MODE);

        assertSilentMode(handler, /* isForcedMode= */ false, /* expectedSilentMode= */ true);

        handler.release();
        boolean currentSilentMode = handler.isSilentMode();
        writeStringToFile(mFileHwStateMonitoring.getFile(), VALUE_NON_SILENT_MODE);

        // Silent mode should not change.
        assertSilentMode(handler, /* isForcedMode= */ false, currentSilentMode);
    }

    private void testSetSilentMode_toForced(boolean initSilentMode, boolean expectedSilentMode)
            throws Exception {
        SilentModeHandler handler = new SilentModeHandler(mCarPowerManagementService,
                mFeatureFlags, mFileHwStateMonitoring.getFile().getPath(),
                mFileKernelSilentMode.getFile().getPath(), BOOT_REASON_NORMAL);
        handler.init();
        handler.updateKernelSilentMode(initSilentMode);

        writeStringToFile(mFileHwStateMonitoring.getFile(),
                initSilentMode ? VALUE_SILENT_MODE : VALUE_NON_SILENT_MODE);

        assertSilentMode(handler, /* isForcedMode= */ false, initSilentMode);

        handler.setSilentMode(expectedSilentMode ? SilentModeHandler.SILENT_MODE_FORCED_SILENT
                : SilentModeHandler.SILENT_MODE_FORCED_NON_SILENT);
        writeStringToFile(mFileHwStateMonitoring.getFile(),
                expectedSilentMode ? VALUE_NON_SILENT_MODE : VALUE_SILENT_MODE);

        assertSilentMode(handler, /* isForcedMode= */ true, expectedSilentMode);
    }

    private void testSetSilentMode_toNonForced(boolean initSilentMode) throws Exception {
        SilentModeHandler handler = new SilentModeHandler(mCarPowerManagementService,
                mFeatureFlags, mFileHwStateMonitoring.getFile().getPath(),
                mFileKernelSilentMode.getFile().getPath(),
                initSilentMode ? BOOT_REASON_FORCED_SILENT : BOOT_REASON_FORCED_NON_SILENT);
        handler.init();

        handler.setSilentMode(SilentModeHandler.SILENT_MODE_NON_FORCED);

        writeStringToFile(mFileHwStateMonitoring.getFile(),
                initSilentMode ? VALUE_NON_SILENT_MODE : VALUE_SILENT_MODE);

        assertSilentMode(handler, /* isForcedMode= */ false, !initSilentMode);
        if (!mFeatureFlags.carPowerPolicyRefactoring()) {
            verify(mCarPowerManagementService, timeout(BUFFER_TIME_TO_AVOID_RACE_CONDITION))
                    .notifySilentModeChange(!initSilentMode);
        }
    }

    private String readFileAsString(Path path) throws Exception {
        List<String> lines = Files.readAllLines(path);
        StringBuilder contentBuilder = new StringBuilder();
        for (String line : lines) {
            contentBuilder.append(line).append("\n");
        }
        return contentBuilder.toString().trim();
    }

    private void writeStringToFile(File file, String contents) throws Exception {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(contents);
            writer.flush();
        }
    }

    private void assertSilentMode(SilentModeHandler handler, boolean isForcedMode,
            boolean expectedMode) throws Exception {
        String expectedValue = expectedMode ? VALUE_SILENT_MODE : VALUE_NON_SILENT_MODE;
        for (int i = 0; i < MAX_POLLING_TRIES; i++) {
            String contents = readFileAsString(mFileKernelSilentMode.getPath());
            if (handler.isSilentMode() == expectedMode
                    && (isForcedMode || contents.equals(expectedValue))) {
                return;
            }
            SystemClock.sleep(POLLING_DELAY_MS);
        }
        throw new IllegalStateException("The current mode should be "
                + (expectedMode ? "Silent Mode" : "Non Silent Mode"));
    }

}
