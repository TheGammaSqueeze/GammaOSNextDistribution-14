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

package android.settings.cts;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.Instrumentation.ActivityMonitor;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.SystemProperties;
import android.provider.Settings;
import android.util.FeatureFlagUtils;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import androidx.window.embedding.SplitController;

import com.android.compatibility.common.util.CddTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests to ensure the Activity to handle
 * {@link Settings#ACTION_SETTINGS_EMBED_DEEP_LINK_ACTIVITY}
 */
@RunWith(AndroidJUnit4.class)
public class SettingsMultiPaneDeepLinkTest {

    private static final String DEEP_LINK_PERMISSION =
            "android.permission.LAUNCH_MULTI_PANE_SETTINGS_DEEP_LINK";
    private  static final String TAG = "SettingsMultiPaneDeepLinkTest";

    boolean mIsSplitSupported;
    ResolveInfo mDeepLinkIntentResolveInfo;

    @Before
    public void setUp() throws Exception {
        Context targetContext = InstrumentationRegistry.getInstrumentation()
                .getTargetContext();
        boolean isFlagEnabled =
                FeatureFlagUtils.isEnabled(targetContext, "settings_support_large_screen");
        final boolean shouldEnableLargeScreenOptimization =
                SystemProperties.getBoolean("persist.settings.large_screen_opt.enabled", false);
        boolean isSplitSupported = SplitController.getInstance(targetContext)
                .getSplitSupportStatus() == SplitController.SplitSupportStatus.SPLIT_AVAILABLE;
        mIsSplitSupported = isFlagEnabled && isSplitSupported
                && shouldEnableLargeScreenOptimization;
        Log.d(TAG, "isFlagEnabled : " + isFlagEnabled);
        Log.d(TAG, "shouldEnableLargeScreenOptimization: "
                + shouldEnableLargeScreenOptimization);
        Log.d(TAG, "isSplitSupported : " + isSplitSupported);
        Log.d(TAG, "mIsSplitSupported : " + mIsSplitSupported);
        mDeepLinkIntentResolveInfo = InstrumentationRegistry.getInstrumentation().getContext()
                .getPackageManager().resolveActivity(
                new Intent(Settings.ACTION_SETTINGS_EMBED_DEEP_LINK_ACTIVITY),
                /* flags= */ PackageManager.MATCH_DEFAULT_ONLY);

        assumeFalse("Skipping test: The device does not support Activity embedding",
                !mIsSplitSupported && mDeepLinkIntentResolveInfo == null);

        // TODO(b/214606992): Remove this check once automotive support was implemented.
        assumeFalse("Skipping test: not supported on automotive yet",
                mDeepLinkIntentResolveInfo == null
                        && InstrumentationRegistry.getInstrumentation().getContext()
                                .getPackageManager()
                                .hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE));

        assumeFalse("Skipping test: not supported on television yet",
                mDeepLinkIntentResolveInfo == null
                        && InstrumentationRegistry.getInstrumentation().getContext()
                                .getPackageManager()
                                .hasSystemFeature(PackageManager.FEATURE_LEANBACK));
    }

    @CddTest(requirement = "3.2.3.5/C-17-1")
    @Test
    public void deepLinkHomeActivity_protectedWithPermission() throws Exception {
        assertTrue("The Activity to handle the Intent ACTION_SETTINGS_EMBED_DEEP_LINK_ACTIVITY must"
                + " be protected by " + DEEP_LINK_PERMISSION,
                DEEP_LINK_PERMISSION.equals(mDeepLinkIntentResolveInfo.activityInfo.permission));
    }

    @CddTest(requirement = "3.2.3.5/C-17-1")
    @Test
    public void deepLinkHomeActivity_splitSupported_deepLinkHomeEnabled() throws Exception {
        assumeTrue(mIsSplitSupported);

        assertTrue("The Activity to handle the Intent ACTION_SETTINGS_EMBED_DEEP_LINK_ACTIVITY must"
                + " be enabled when the device supports Activity embedding",
                mDeepLinkIntentResolveInfo != null);
    }

    @CddTest(requirement = "3.2.3.5/C-17-1")
    @Test
    public void deepLinkHomeActivity_splitNotSupported_deepLinkHomeDisabled() throws Exception {
        assumeFalse(mIsSplitSupported);

        assertTrue("The Activity to handle the Intent ACTION_SETTINGS_EMBED_DEEP_LINK_ACTIVITY must"
                + " be disabled when the device does NOT support Activity embedding",
                mDeepLinkIntentResolveInfo == null);
    }

    @CddTest(requirement = "3.2.3.5/C-17-1")
    @Test
    public void deepLinkHomeActivity_receiveMultiPaneDeepLinkIntent_shouldStartActivity()
                throws Exception {
        Intent intent = new Intent(Settings.ACTION_SETTINGS_EMBED_DEEP_LINK_ACTIVITY);
        intent.putExtra(Settings.EXTRA_SETTINGS_EMBEDDED_DEEP_LINK_INTENT_URI,
                new Intent("android.settings.cts.LAUNCH_RIGHT_PANE")
                .toUri(Intent.URI_INTENT_SCHEME));
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        ActivityMonitor am = instrumentation.addMonitor(RightPaneActivity.class.getName(),
                /* result */ null, /* block */ false);

        // Take the Shell UID permission identity because Shell app has the permission
        // android.permission.LAUNCH_MULTI_PANE_SETTINGS_DEEP_LINK.
        instrumentation.getUiAutomation().adoptShellPermissionIdentity();
        try {
            instrumentation.getContext()
                    .startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } finally {
            // Drop the Shell UID permission identity.
            instrumentation.getUiAutomation().dropShellPermissionIdentity();
        }

        Activity rightPaneActivity = am.waitForActivityWithTimeout(5000);
        assertNotNull("The Activity to handle the Intent ACTION_SETTINGS_EMBED_DEEP_LINK_ACTIVITY"
                + " must start Activity for EXTRA_SETTINGS_EMBEDDED_DEEP_LINK_INTENT_URI",
                rightPaneActivity);
        rightPaneActivity.finish();
    }
}
