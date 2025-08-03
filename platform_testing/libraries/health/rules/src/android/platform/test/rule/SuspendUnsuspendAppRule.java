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

package android.platform.test.rule;

import android.content.pm.PackageManager;
import android.content.pm.SuspendDialogInfo;
import android.util.Log;

import androidx.annotation.VisibleForTesting;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.runner.Description;

/**
 * This rule will suspend and then immediately unsuspend the app using the package name. This
 * finishes the activities of the given app without killing it. This can be used as an approach to
 * do a warm launch of the app without removing it from home screen. This is an alternate to using
 * FinishActivitiesWithoutProcessKillRule which removes the app from home screen.
 */
public class SuspendUnsuspendAppRule extends TestWatcher {

    @VisibleForTesting
    static final String ENABLE_SUSPEND_UNSUSPEND_APP = "enable-suspend-unsuspend-app";

    private static final String TAG = SuspendUnsuspendAppRule.class.getSimpleName();
    private final boolean mEnableRule;
    private final String mPkgName;

    @VisibleForTesting
    static final SuspendDialogInfo SUSPEND_DIALOG_INFO =
            new SuspendDialogInfo.Builder().setMessage("Package Suspended").build();

    @VisibleForTesting
    static final SuspendDialogInfo UNSUSPEND_DIALOG_INFO =
            new SuspendDialogInfo.Builder().setMessage("Package Unsuspended").build();

    private PackageManager mPackageManager;

    public SuspendUnsuspendAppRule(String appPackageName) {
        this(appPackageName, true);
    }

    public SuspendUnsuspendAppRule(String appPackageName, boolean enableRule) {
        mPkgName = appPackageName;
        mPackageManager =
                InstrumentationRegistry.getInstrumentation().getContext().getPackageManager();
        mEnableRule = enableRule;
    }

    @Override
    protected void starting(Description description) {
        if (!mEnableRule
                && !Boolean.parseBoolean(
                        getArguments().getString(ENABLE_SUSPEND_UNSUSPEND_APP, "true"))) {
            return;
        }

        // Needed to avoid security exception when changing the App enabled setting.
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity();

        // Suspend the App
        try {
            mPackageManager.setPackagesSuspended(
                    new String[] {mPkgName},
                    true, // suspended
                    null, // appExtras
                    null, // launcherExtras
                    SUSPEND_DIALOG_INFO);
        } catch (Exception e) {
            Log.e(TAG, "Could not suspend the package " + mPkgName);
            throw new RuntimeException(e);
        }

        // Unsuspend the App
        try {
            mPackageManager.setPackagesSuspended(
                    new String[] {mPkgName},
                    false, // suspended
                    null, // appExtras
                    null, // launcherExtras
                    UNSUSPEND_DIALOG_INFO);
        } catch (Exception e) {
            Log.e(TAG, "Could not unsuspend the package " + mPkgName);
            throw new RuntimeException(e);
        }
    }

    @VisibleForTesting
    void setPackageManager(PackageManager packageManager) {
        mPackageManager = packageManager;
    }
}
