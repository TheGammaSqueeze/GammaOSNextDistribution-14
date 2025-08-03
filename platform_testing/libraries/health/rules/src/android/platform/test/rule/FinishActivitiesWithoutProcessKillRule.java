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

package android.platform.test.rule;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.VisibleForTesting;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.runner.Description;
import org.junit.runners.model.InitializationError;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** This rule finsihes all the activities associated with the given package name without killing
 *  the process.
 */
public class FinishActivitiesWithoutProcessKillRule extends TestWatcher {

    private static final String TAG = FinishActivitiesWithoutProcessKillRule.class.getSimpleName();
    private static final long TOTAL_TIMEOUT = 10000;
    private static final long TIMEOUT_INTERVAL = 2000;

    private String mPkgName;
    private Context mContext;
    private final Pattern mAppActivityRecordPattern;
    private final boolean mEnableRule;

    @VisibleForTesting
    static final String FINISH_ACTIVITY_WITHOUT_PROCESS_KILL =
            "finish-activity-without-process-kill";

    public FinishActivitiesWithoutProcessKillRule() throws InitializationError {
        throw new InitializationError("Must supply an application pkg name to"
                + "finish the associated activities.");
    }

    public FinishActivitiesWithoutProcessKillRule(String appPackageName) {
        this(appPackageName, false);
    }

    public FinishActivitiesWithoutProcessKillRule(String appPackageName,
            boolean enableRule) {
        mPkgName = appPackageName;
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mAppActivityRecordPattern = Pattern.compile(
                "ActivityRecord\\{.*"
                        + appPackageName.replace(".", "\\.")
                        + "/.*\\st([0-9]+)\\}");
        mEnableRule = enableRule;
    }

    @Override
    protected void starting(Description description) {

        if(!mEnableRule) {
            if (!Boolean.parseBoolean(
                    getArguments().getString(FINISH_ACTIVITY_WITHOUT_PROCESS_KILL, "true"))) {
                return;
            }
        }

        if (!getAppActivityMatcher().find()) {
            Log.e(TAG, "No activities associated with the package name is found.");
            return;
        }

        // Needed to avoid security exception when changing the App enabled setting.
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity();
        try {
            // This will remove all the activities associated with the package name without
            // killing the app.
            mContext.getPackageManager().setApplicationEnabledSetting(mPkgName,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);

            try {
                if (!waitUntilActivitiesRemoved()) {
                    throw new IllegalStateException("Activities not removed successfully.");
                }
            } finally {
                mContext.getPackageManager().setApplicationEnabledSetting(mPkgName,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP);
            }
        } finally {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }

        super.starting(description);
    }

    /**
     * Wait until the activities associated with the package name is removed within given timeout.
     *
     * @return true if the activities are removed successfully.
     */
    private boolean waitUntilActivitiesRemoved() {
        long startTime = SystemClock.uptimeMillis();
        while (SystemClock.uptimeMillis() - startTime <= TOTAL_TIMEOUT) {
            if (getAppActivityMatcher().find()) {
                Log.v(TAG, String.format("Waiting for another %f secs", TIMEOUT_INTERVAL / 1000f));
                SystemClock.sleep(TIMEOUT_INTERVAL);
            } else {
                Log.v(TAG,
                        "Activities associated with the package name were removed successfully.");
                return true;
            }
        }
        return false;
    }

    private Matcher getAppActivityMatcher() {
        return mAppActivityRecordPattern
                .matcher(executeShellCommand("dumpsys activity activities"));
    }
}
