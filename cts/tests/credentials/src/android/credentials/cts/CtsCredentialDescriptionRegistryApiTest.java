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

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.pm.PackageManager;
import android.credentials.CredentialManager;
import android.credentials.cts.testcore.CtsCredentialManagerUtils;
import android.credentials.cts.testcore.DeviceConfigStateRequiredRule;
import android.os.Build;
import android.provider.DeviceConfig;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.rules.ActivityScenarioRule;

import com.android.compatibility.common.util.DeviceConfigStateManager;
import com.android.compatibility.common.util.RequiredFeatureRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class CtsCredentialDescriptionRegistryApiTest {
    public static final String CTS_PACKAGE_NAME =
            CtsNoOpCredentialProviderService.class.getPackage().getName();
    private static final String TAG = "CtsCredentialDescriptionRegistryApiTest";
    public static final String DEVICE_CONFIG_ENABLE_CREDENTIAL_MANAGER =
            "enable_credential_manager";
    public static final String DEVICE_CONFIG_ENABLE_CREDENTIAL_REGISTRY_API =
            "enable_credential_description_api";

    private CredentialManager mCredentialManager;
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
            new DeviceConfigStateRequiredRule(DEVICE_CONFIG_ENABLE_CREDENTIAL_MANAGER,
                    DeviceConfig.NAMESPACE_CREDENTIAL, mContext, "true");

    @Rule
    public final DeviceConfigStateRequiredRule mDeviceConfigCredentialRegistryRequiredRule =
            new DeviceConfigStateRequiredRule(DEVICE_CONFIG_ENABLE_CREDENTIAL_REGISTRY_API,
                    DeviceConfig.NAMESPACE_CREDENTIAL, mContext, "true");

    @Before
    public void setUp() {
        Log.i(TAG, "Enabling CredentialManager flags as well...");
        enableCredentialManagerDeviceFeature(mContext);
        enableCredentialDescriptionRegistryApiDeviceFeature(mContext);
        mCredentialManager = (CredentialManager) mContext.getSystemService(
                Context.CREDENTIAL_SERVICE);
        assumeTrue("VERSION.SDK_INT=" + Build.VERSION.SDK_INT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        assumeFalse("Skipping test: Auto does not support CredentialManager yet",
                CtsCredentialManagerUtils.isAuto(mContext));
    }

    @After
    public void tearDown() {
        Log.i(TAG, "Disabling credman services and device feature flag");
    }

    @Test
    public void testRegisterCredentialDescriptionRequest_nullRequest_throwsNPE() {
        ActivityScenario<TestCredentialActivity> activityScenario =
                ActivityScenario.launch(TestCredentialActivity.class);
        activityScenario.onActivity(activity -> {

            assertThrows("expect null request to throw NPE", NullPointerException.class,
                    () -> mCredentialManager.registerCredentialDescription(null));
        });
    }

    @Test
    public void testUnregisterCredentialDescriptionRequest_nullRequest_throwsNPE() {
        ActivityScenario<TestCredentialActivity> activityScenario =
                ActivityScenario.launch(TestCredentialActivity.class);
        activityScenario.onActivity(activity -> {

            assertThrows("expect null request to throw NPE", NullPointerException.class,
                    () -> mCredentialManager.unregisterCredentialDescription(null));
        });
    }

    /**
     * Enable the main credential manager feature.
     * If this is off, any underlying changes for autofill-credentialManager integrations are off.
     */
    public static void enableCredentialManagerDeviceFeature(@NonNull Context context) {
        setCredentialManagerFeature(context, true);
    }

    public static void disableCredentialManagerDeviceFeature(@NonNull Context context) {
        setCredentialManagerFeature(context, false);
    }

    /**
     * Enable the Credential Description API feature.
     */
    public static void enableCredentialDescriptionRegistryApiDeviceFeature(
            @NonNull Context context) {
        setCredentialDescriptionRegistryApiFeature(context, true);
    }

    public static void disableCredentialDescriptionRegistryApiDeviceFeature(
            @NonNull Context context) {
        setCredentialDescriptionRegistryApiFeature(context, false);
    }

    /**
     * Enable Credential Manager related autofill changes
     */
    public static void setCredentialManagerFeature(@NonNull Context context, boolean enabled) {
        setDeviceConfig(context,
                DEVICE_CONFIG_ENABLE_CREDENTIAL_MANAGER, enabled);
    }

    /**
     * Enable Credential Manager : Credential Description Registry API feature.
     */
    public static void setCredentialDescriptionRegistryApiFeature(@NonNull Context context,
            boolean enabled) {
        setDeviceConfig(context,
                DEVICE_CONFIG_ENABLE_CREDENTIAL_REGISTRY_API, enabled);
    }

    /**
     * Set device config to set flag values.
     */
    public static void setDeviceConfig(
            @NonNull Context context, @NonNull String feature, boolean value) {
        DeviceConfigStateManager deviceConfigStateManager =
                new DeviceConfigStateManager(context, DeviceConfig.NAMESPACE_CREDENTIAL, feature);
        setDeviceConfig(deviceConfigStateManager, String.valueOf(value));
    }

    public static void setDeviceConfig(@NonNull DeviceConfigStateManager deviceConfigStateManager,
            @Nullable String value) {
        final String previousValue = deviceConfigStateManager.get();
        if (TextUtils.isEmpty(value) && TextUtils.isEmpty(previousValue)
                || TextUtils.equals(previousValue, value)) {
            Log.v(TAG, "No changed in config: " + deviceConfigStateManager);
            return;
        }

        deviceConfigStateManager.set(value);
    }
}
