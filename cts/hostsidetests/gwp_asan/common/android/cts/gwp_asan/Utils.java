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

package android.cts.gwp_asan;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Application;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.cts.gwp_asan.Android13Tombstone.Tombstone;
import android.util.Log;

import com.android.compatibility.common.util.DropBoxReceiver;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class Utils {
    static {
        System.loadLibrary("gwp_asan_cts_library_jni");
    }

    public static final int TEST_SUCCESS = Activity.RESULT_FIRST_USER + 100;
    public static final int TEST_FAILURE = Activity.RESULT_FIRST_USER + 101;

    public static final int TEST_IS_GWP_ASAN_ENABLED = 42;
    public static final int TEST_IS_GWP_ASAN_DISABLED = 43;
    public static final int TEST_USE_AFTER_FREE = 44;

    public static final String DROPBOX_TAG = "data_app_native_crash";
    public static final String DROPBOX_RECOVERABLE_TAG = "data_app_native_recoverable_crash";

    // Check that GWP-ASan is enabled by allocating a whole bunch of heap pointers and making sure
    // one of them is a GWP-ASan allocation (by comparing with /proc/self/maps).
    public static native boolean isGwpAsanEnabled();
    // Check that GWP-ASan is disabled by ensuring there's no GWP-ASan mappings in /proc/self/maps.
    public static boolean isGwpAsanDisabled() throws IOException {
        try (FileReader fr = new FileReader("/proc/self/maps");
                BufferedReader reader = new BufferedReader(fr)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("GWP-ASan Guard Page")) {
                    Log.e(Application.getProcessName(),
                            "Line contained guard page: " + line);
                    return false;
                }
            }
        }
        return true;
    }

    // Trigger a GWP-ASan instrumented use-after-free. This should always trigger a GWP-ASan report
    // if it's enabled. In non-recoverable mode (i.e. gwpAsanMode=always), then this will crash the
    // app with SEGV. In recoverable mode (i.e. gwpAsanMode=default), then this won't crash, but
    // should still trigger all the dropbox/debuggerd entries.
    public static native void instrumentedUseAfterFree();

    public static int runTest(int testId) {
        try {
            if (testId == TEST_IS_GWP_ASAN_ENABLED) {
                return Utils.isGwpAsanEnabled() ? Utils.TEST_SUCCESS : Utils.TEST_FAILURE;
            }
            if (testId == TEST_IS_GWP_ASAN_DISABLED) {
                return Utils.isGwpAsanDisabled() ? Utils.TEST_SUCCESS : Utils.TEST_FAILURE;
            }
            if (testId == TEST_USE_AFTER_FREE) {
                Utils.instrumentedUseAfterFree();
                return Utils.TEST_SUCCESS;
            }
        } catch (Exception e) {
            return Utils.TEST_FAILURE;
        }
        return Utils.TEST_FAILURE;
    }

    public static DropBoxReceiver getDropboxReceiver(
            Context context, String processNameSuffix, String crashTag) {
        return new DropBoxReceiver(
                context,
                crashTag,
                context.getPackageName() + ":" + processNameSuffix,
                "SEGV_ACCERR",
                "Cause: [GWP-ASan]: Use After Free",
                "deallocated by",
                "backtrace:");
    }

    public static DropBoxReceiver getDropboxReceiver(Context context, String processNameSuffix) {
        return getDropboxReceiver(context, processNameSuffix, DROPBOX_TAG);
    }

    public static boolean appExitInfoHasReport(Context context, String processNameSuffix)
            throws Exception {
        ActivityManager am = context.getSystemService(ActivityManager.class);
        List<ApplicationExitInfo> exitReasons =
                am.getHistoricalProcessExitReasons(
                        processNameSuffix == null
                                ? null
                                : context.getPackageName() + ":" + processNameSuffix,
                        0,
                        0);
        for (ApplicationExitInfo exitReason : exitReasons) {
            if (exitReason.getReason() != ApplicationExitInfo.REASON_CRASH_NATIVE) continue;
            InputStream data = exitReason.getTraceInputStream();
            if (data == null) continue;
            Tombstone tombstone = Android13Tombstone.Tombstone.parseFrom(data.readAllBytes());
            String cause = tombstone.getCauses(0).getHumanReadable();
            if (cause.contains("[GWP-ASan]: Use After Free")) {
                return true;
            }
        }
        return false;
    }
}
