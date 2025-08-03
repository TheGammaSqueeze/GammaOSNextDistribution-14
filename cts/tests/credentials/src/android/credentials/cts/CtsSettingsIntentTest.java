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

package android.credentials.cts;

import static android.credentials.flags.Flags.FLAG_NEW_SETTINGS_INTENTS;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.credentials.cts.testcore.CtsCredentialManagerUtils;
import android.credentials.cts.testcore.DeviceConfigStateRequiredRule;
import android.net.Uri;
import android.os.StrictMode;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.DeviceConfig;
import android.util.Log;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;

import com.android.compatibility.common.util.RequiredFeatureRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

@Presubmit
@AppModeFull(reason = "Service-specific test")
public class CtsSettingsIntentTest {
    private static final String TAG = "CtsSettingsIntentTest";

    public static final String DEVICE_CONFIG_ENABLE_CREDENTIAL_MANAGER =
            "enable_credential_manager";

    private final Context mContext = getInstrumentation().getContext();

    @Rule
    public ActivityScenarioRule mActivityScenarioRule =
            new ActivityScenarioRule(TestCredentialActivity.class);

    // Sets up the feature flag rule and the device flag rule
    @Rule
    public final RequiredFeatureRule mRequiredFeatureRule =
            new RequiredFeatureRule(PackageManager.FEATURE_CREDENTIALS);

    @Rule
    public final DeviceConfigStateRequiredRule mDeviceConfigStateRequiredRule =
            new DeviceConfigStateRequiredRule(
                    DEVICE_CONFIG_ENABLE_CREDENTIAL_MANAGER,
                    DeviceConfig.NAMESPACE_CREDENTIAL,
                    mContext,
                    "true");

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private final UiDevice mDevice;

    public CtsSettingsIntentTest() {
        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    }

    @After
    public void killSettings() {
        // Make sure there's no Settings activity left, as it could fail future tests.
        runShellCommand("am force-stop com.android.settings");
    }
    @Before
    public void setUp() {
        assumeFalse("Skipping test: Auto does not support CredentialManager yet",
                CtsCredentialManagerUtils.isAuto(mContext));
    }

    @Test
    @RequiresFlagsEnabled(FLAG_NEW_SETTINGS_INTENTS)
    public void testCredentialManagerSettingsIntent() throws Exception {
        StrictMode.setVmPolicy(
                new StrictMode.VmPolicy.Builder().permitUnsafeIntentLaunch().penaltyLog().build());

        // Disable on watches since they don't have the traditional settings UI.
        if (mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH)) {
            Log.w(TAG, "Credential Manager Settings Intent test should not be enabled on watches");
            return;
        }

        // Ensure that the intent is resolvable.
        final Intent intent =
                newSettingsIntent(android.provider.Settings.ACTION_CREDENTIAL_PROVIDER);
        final ResolveInfo ri =
                mContext.getPackageManager()
                        .resolveActivity(intent, PackageManager.MATCH_DISABLED_COMPONENTS);
        assertThat(ri).isNotNull();

        ActivityScenario<TestCredentialActivity> activityScenario =
                ActivityScenario.launch(TestCredentialActivity.class);

        activityScenario.moveToState(Lifecycle.State.CREATED);

        activityScenario.onActivity(
                activity -> {
                    // Launches settings using provider intent.
                    activity.startSettingsActivity(intent);
                });

        // The UI is not exactly the same on all platforms so this is desired
        // but not required. The most important thing about this test is that
        // the intent opens an activity and is not lost because it has not
        // been implemented.
        assumeTrue(hasViewWithText("Test Provider Service Alternate"));
        assumeTrue(hasViewWithText("Additional providers"));
    }

    @Test
    @RequiresFlagsEnabled(FLAG_NEW_SETTINGS_INTENTS)
    public void testCredentialManagerAutofillSettingsIntent() throws Exception {
        StrictMode.setVmPolicy(
                new StrictMode.VmPolicy.Builder().permitUnsafeIntentLaunch().penaltyLog().build());

        // Disable on watches since they don't have the traditional settings UI.
        if (mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH)) {
            Log.w(TAG, "Credential Manager Settings Intent test should not be enabled on watches");
            return;
        }

        // Ensure that the intent is resolvable.
        final Intent intent =
                newSettingsIntent(android.provider.Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE);
        final ResolveInfo ri =
                mContext.getPackageManager()
                        .resolveActivity(intent, PackageManager.MATCH_DISABLED_COMPONENTS);
        assertThat(ri).isNotNull();

        ActivityScenario<TestCredentialActivity> activityScenario =
                ActivityScenario.launch(TestCredentialActivity.class);

        activityScenario.moveToState(Lifecycle.State.CREATED);

        activityScenario.onActivity(
                activity -> {
                    // Launches settings using provider intent.
                    activity.startSettingsActivity(intent);
                });

        assertThat(hasViewWithText("Test Provider Service Alternate")).isTrue();
        assertThat(hasViewWithText("Additional providers")).isFalse();
    }

    /** Returns true if there is a view with the supplied text on the screen. */
    public boolean hasViewWithText(String name) {
        Log.v(TAG, "hasViewWithText(): " + name);

        return mDevice.findObject(By.text(name)) != null;
    }

    private Intent newSettingsIntent(String action) {
        return new Intent(action)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .setData(Uri.parse("package:android.credentials.cts"));
    }
}
