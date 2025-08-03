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

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.car.builtin.util.Slogf;
import android.car.feature.FeatureFlags;
import android.car.feature.FeatureFlagsImpl;
import android.os.FileObserver;
import android.os.SystemProperties;
import android.util.proto.ProtoOutputStream;

import com.android.car.CarLog;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.car.power.CarPowerDumpProto.SilentModeHandlerProto;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import libcore.io.IoUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * Class to handle Silent Mode and Non-Silent Mode.
 *
 * <p>This monitors {@code /sys/kernel/silent_boot/pm_silentmode_hw_state} to figure out when to
 * switch to Silent Mode and updates {@code /sys/kernel/silent_boot/pm_silentmode_kernel_state} to
 * tell early-init services about Silent Mode change. Also, it handles forced Silent Mode for
 * testing purpose, which is given through reboot reason.
 */
final class SilentModeHandler {
    static final String SILENT_MODE_FORCED_SILENT = "forced-silent";
    static final String SILENT_MODE_FORCED_NON_SILENT = "forced-non-silent";
    static final String SILENT_MODE_NON_FORCED = "non-forced-silent-mode";

    private static final String TAG = CarLog.tagFor(SilentModeHandler.class);

    /**
     * The folders that are searched for sysfs files.
     *
     * <p>The sysfs files for Silent Mode are searched in the following order:
     * <ol>
     *   <li>/sys/kernel/silent_boot
     *   <li>/sys/power
     * </ol>
     *
     * <p>Placing the sysfs files in {@code /sys/power} is deprecated, but for backwad
     * compatibility, we fallback to the folder when the files don't exist in
     * {@code /sys/kernel/silent_boot}.
     */
    private static final String[] SYSFS_DIRS_FOR_SILENT_MODE =
            new String[]{"/sys/kernel/silent_boot", "/sys/power"};
    private static final String SYSFS_FILENAME_HW_STATE_MONITORING = "pm_silentmode_hw_state";
    private static final String SYSFS_FILENAME_KERNEL_SILENTMODE = "pm_silentmode_kernel_state";
    private static final String VALUE_SILENT_MODE = "1";
    private static final String VALUE_NON_SILENT_MODE = "0";
    private static final String SYSTEM_BOOT_REASON = "sys.boot.reason";
    private static final String FORCED_NON_SILENT = "reboot,forcednonsilent";
    private static final String FORCED_SILENT = "reboot,forcedsilent";

    private final Object mLock = new Object();
    private final CarPowerManagementService mService;
    private final String mHwStateMonitoringFileName;
    private final String mKernelSilentModeFileName;

    @GuardedBy("mLock")
    private FileObserver mFileObserver;
    @GuardedBy("mLock")
    private boolean mSilentModeByHwState;
    @GuardedBy("mLock")
    private boolean mForcedMode;

    // Allows for injecting feature flag values during testing
    private FeatureFlags mFeatureFlags = new FeatureFlagsImpl();

    @VisibleForTesting
    SilentModeHandler(@NonNull CarPowerManagementService service, FeatureFlags featureFlags,
            @Nullable String hwStateMonitoringFileName, @Nullable String kernelSilentModeFileName,
            @Nullable String bootReason) {
        Objects.requireNonNull(service, "CarPowerManagementService must not be null");
        mService = service;
        mFeatureFlags = featureFlags;
        String sysfsDir = searchForSysfsDir();
        mHwStateMonitoringFileName = hwStateMonitoringFileName == null
                ? sysfsDir + SYSFS_FILENAME_HW_STATE_MONITORING : hwStateMonitoringFileName;
        mKernelSilentModeFileName = kernelSilentModeFileName == null
                ? sysfsDir + SYSFS_FILENAME_KERNEL_SILENTMODE : kernelSilentModeFileName;
        String reason = bootReason;
        if (reason == null) {
            reason = SystemProperties.get(SYSTEM_BOOT_REASON);
        }
        switch (reason) {
            case FORCED_SILENT:
                Slogf.i(TAG, "Starting in forced silent mode");
                mForcedMode = true;
                mSilentModeByHwState = true;
                break;
            case FORCED_NON_SILENT:
                Slogf.i(TAG, "Starting in forced non-silent mode");
                mForcedMode = true;
                mSilentModeByHwState = false;
                break;
            default:
                mForcedMode = false;
        }
    }

    void init() {
        boolean forcedMode;
        boolean silentMode;
        synchronized (mLock) {
            forcedMode = mForcedMode;
            silentMode = mSilentModeByHwState;
        }
        if (forcedMode) {
            updateKernelSilentMode(silentMode);
            if (!mFeatureFlags.carPowerPolicyRefactoring()) {
                mService.notifySilentModeChange(silentMode);
            }
            Slogf.i(TAG, "Now in forced mode: monitoring %s is disabled",
                    mHwStateMonitoringFileName);
        } else {
            startMonitoringSilentModeHwState();
        }
    }

    void release() {
        synchronized (mLock) {
            stopMonitoringSilentModeHwStateLocked();
        }
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    void dump(IndentingPrintWriter writer) {
        synchronized (mLock) {
            writer.printf("mHwStateMonitoringFileName: %s\n", mHwStateMonitoringFileName);
            writer.printf("mKernelSilentModeFileName: %s\n", mKernelSilentModeFileName);
            writer.printf("Monitoring HW state signal: %b\n", mFileObserver != null);
            writer.printf("Silent mode by HW state signal: %b\n", mSilentModeByHwState);
            writer.printf("Forced silent mode: %b\n", mForcedMode);
        }
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    void dumpProto(ProtoOutputStream proto) {
        synchronized (mLock) {
            long silentModeHandlerToken = proto.start(
                    CarPowerDumpProto.SILENT_MODE_HANDLER);
            proto.write(SilentModeHandlerProto.HW_STATE_MONITORING_FILE_NAME,
                    mHwStateMonitoringFileName);
            proto.write(SilentModeHandlerProto.KERNEL_SILENT_MODE_FILE_NAME,
                    mKernelSilentModeFileName);
            proto.write(
                    SilentModeHandlerProto.IS_MONITORING_HW_STATE_SIGNAL, mFileObserver != null);
            proto.write(SilentModeHandlerProto.SILENT_MODE_BY_HW_STATE, mSilentModeByHwState);
            proto.write(SilentModeHandlerProto.FORCED_SILENT_MODE, mForcedMode);
            proto.end(silentModeHandlerToken);
        }
    }

    boolean isSilentMode() {
        synchronized (mLock) {
            return mSilentModeByHwState;
        }
    }

    void querySilentModeHwState() {
        FileObserver fileObserver;
        synchronized (mLock) {
            fileObserver = mFileObserver;
        }
        if (fileObserver != null) {
            fileObserver.onEvent(FileObserver.MODIFY, mHwStateMonitoringFileName);
        }
    }

    void updateKernelSilentMode(boolean silent) {
        try (BufferedWriter writer =
                new BufferedWriter(new FileWriter(mKernelSilentModeFileName))) {
            String value = silent ? VALUE_SILENT_MODE : VALUE_NON_SILENT_MODE;
            writer.write(value);
            writer.flush();
            Slogf.i(TAG, "%s is updated to %s", mKernelSilentModeFileName, value);
        } catch (IOException e) {
            Slogf.w(TAG, "Failed to update %s to %s", mKernelSilentModeFileName,
                    silent ? VALUE_SILENT_MODE : VALUE_NON_SILENT_MODE);
        }
    }

    void setSilentMode(String silentMode) {
        switch (silentMode) {
            case SILENT_MODE_FORCED_SILENT:
                switchToForcedMode(true);
                break;
            case SILENT_MODE_FORCED_NON_SILENT:
                switchToForcedMode(false);
                break;
            case SILENT_MODE_NON_FORCED:
                switchToNonForcedMode();
                break;
            default:
                Slogf.w(TAG, "Unsupported silent mode: %s", silentMode);
        }
    }

    private void switchToForcedMode(boolean silentMode) {
        boolean updated = false;
        synchronized (mLock) {
            if (!mForcedMode) {
                stopMonitoringSilentModeHwStateLocked();
                mForcedMode = true;
            }
            if (mSilentModeByHwState != silentMode) {
                mSilentModeByHwState = silentMode;
                updated = true;
            }
        }
        if (updated) {
            updateKernelSilentMode(silentMode);
            if (!mFeatureFlags.carPowerPolicyRefactoring()) {
                mService.notifySilentModeChange(silentMode);
            }
        }
        Slogf.i(TAG, "Now in forced %s mode: monitoring %s is disabled",
                silentMode ? "silent" : "non-silent", mHwStateMonitoringFileName);
    }

    private void switchToNonForcedMode() {
        boolean updated = false;
        synchronized (mLock) {
            if (mForcedMode) {
                Slogf.i(TAG, "Now in non forced mode: monitoring %s is started",
                        mHwStateMonitoringFileName);
                mForcedMode = false;
                updated = true;
            }
        }
        if (updated) {
            startMonitoringSilentModeHwState();
        }
    }

    private void startMonitoringSilentModeHwState() {
        File monitorFile = new File(mHwStateMonitoringFileName);
        if (!monitorFile.exists()) {
            Slogf.w(TAG, "Failed to start monitoring Silent Mode HW state: %s doesn't exist",
                    mHwStateMonitoringFileName);
            return;
        }
        FileObserver fileObserver = new FileObserver(monitorFile, FileObserver.MODIFY) {
            @Override
            public void onEvent(int event, String filename) {
                boolean newSilentMode;
                boolean oldSilentMode;
                synchronized (mLock) {
                    // FileObserver can report events even after stopWatching is called.
                    if (mForcedMode || mFileObserver == null) {
                        return;
                    }
                    oldSilentMode = mSilentModeByHwState;
                    try {
                        String contents = IoUtils.readFileAsString(mHwStateMonitoringFileName)
                                .trim();
                        mSilentModeByHwState = VALUE_SILENT_MODE.equals(contents);
                        Slogf.i(TAG, "%s indicates %s mode", mHwStateMonitoringFileName,
                                mSilentModeByHwState ? "silent" : "non-silent");
                    } catch (Exception e) {
                        Slogf.w(TAG, e, "Failed to read %s", mHwStateMonitoringFileName);
                        return;
                    }
                    newSilentMode = mSilentModeByHwState;
                }
                if (newSilentMode != oldSilentMode) {
                    updateKernelSilentMode(newSilentMode);
                    if (!mFeatureFlags.carPowerPolicyRefactoring()) {
                        mService.notifySilentModeChange(newSilentMode);
                    }
                }
            }
        };
        synchronized (mLock) {
            mFileObserver = fileObserver;
        }
        fileObserver.startWatching();
        // Trigger the observer to get the initial contents
        querySilentModeHwState();
    }

    @GuardedBy("mLock")
    private void stopMonitoringSilentModeHwStateLocked() {
        if (mFileObserver != null) {
            mFileObserver.stopWatching();
            mFileObserver = null;
        }
    }

    private static String searchForSysfsDir() {
        for (String dir : SYSFS_DIRS_FOR_SILENT_MODE) {
            if (Files.isDirectory(Paths.get(dir))) {
                return dir + "/";
            }
        }
        return SYSFS_DIRS_FOR_SILENT_MODE[0] + "/";
    }
}
