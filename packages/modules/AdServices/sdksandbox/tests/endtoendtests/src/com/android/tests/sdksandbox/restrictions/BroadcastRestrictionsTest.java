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

package com.android.tests.sdksandbox.endtoend.restrictions;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.app.sdksandbox.SandboxedSdk;
import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.testutils.ConfigListener;
import android.app.sdksandbox.testutils.DeviceConfigUtils;
import android.app.sdksandbox.testutils.FakeLoadSdkCallback;
import android.app.sdksandbox.testutils.ProtoUtil;
import android.app.sdksandbox.testutils.SdkSandboxDeviceSupportedRule;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.DeviceConfig;
import android.util.ArrayMap;
import android.util.ArraySet;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.modules.utils.build.SdkLevel;
import com.android.tests.sdkprovider.restrictions.IRestrictionsSdkApi;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(JUnit4.class)
public class BroadcastRestrictionsTest {
    private SdkSandboxManager mSdkSandboxManager;
    private static final String PROPERTY_ENFORCE_RESTRICTIONS = "sdksandbox_enforce_restrictions";

    // Keep consistent with SdkSandboxManagerService.PROPERTY_BROADCASTRECEIVER_ALLOWLIST
    private static final String PROPERTY_BROADCASTRECEIVER_ALLOWLIST =
            "sdksandbox_broadcastreceiver_allowlist_per_targetSdkVersion";

    // Keep the value consistent with
    // SdkSandboxManagerService.PROPERTY_APPLY_SDK_SANDBOX_NEXT_RESTRICTIONS.
    private static final String PROPERTY_APPLY_SDK_SANDBOX_NEXT_RESTRICTIONS =
            "apply_sdk_sandbox_next_restrictions";

    // Keep the value consistent with
    // SdkSandboxManagerService.PROPERTY_NEXT_BROADCASTRECEIVER_ALLOWLIST.
    private static final String PROPERTY_NEXT_BROADCASTRECEIVER_ALLOWLIST =
            "sdksandbox_next_broadcastreceiver_allowlist";

    private static final String SDK_PACKAGE = "com.android.tests.sdkprovider.restrictions";
    private static final String NAMESPACE = DeviceConfig.NAMESPACE_ADSERVICES;

    private String mEnforceBroadcastRestrictions;
    private String mInitialBroadcastReceiverAllowlistValue;
    private String mInitialApplySdkSandboxNextRestrictionsValue;
    private String mInitialNextBroadcastReceiverAllowlistValue;

    private static final List<String> UNPROTECTED_INTENT_ACTIONS =
            new ArrayList<>(
                    Arrays.asList(
                            Intent.ACTION_WEB_SEARCH,
                            Intent.ACTION_VOICE_ASSIST,
                            Intent.ACTION_CALL_BUTTON,
                            Intent.ACTION_VOICE_COMMAND,
                            Intent.ACTION_SET_WALLPAPER,
                            Intent.ACTION_SHOW_WORK_APPS));

    private IRestrictionsSdkApi mRestrictionsSdkApi;
    private ConfigListener mConfigListener;
    private DeviceConfigUtils mDeviceConfigUtils;

    @Rule(order = 0)
    public final SdkSandboxDeviceSupportedRule supportedRule = new SdkSandboxDeviceSupportedRule();

    @Rule(order = 1)
    public final ActivityScenarioRule mRule = new ActivityScenarioRule<>(TestActivity.class);

    @Before
    public void setup() throws Exception {
        assumeTrue(SdkLevel.isAtLeastU());
        Context context = ApplicationProvider.getApplicationContext();
        mSdkSandboxManager = context.getSystemService(SdkSandboxManager.class);
        assertThat(mSdkSandboxManager).isNotNull();
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.WRITE_DEVICE_CONFIG,
                        Manifest.permission.READ_DEVICE_CONFIG);

        mConfigListener = new ConfigListener();
        DeviceConfig.addOnPropertiesChangedListener(
                NAMESPACE, context.getMainExecutor(), mConfigListener);
        mDeviceConfigUtils = new DeviceConfigUtils(mConfigListener, NAMESPACE);

        mEnforceBroadcastRestrictions =
                DeviceConfig.getProperty(
                        DeviceConfig.NAMESPACE_ADSERVICES, PROPERTY_ENFORCE_RESTRICTIONS);
        mInitialBroadcastReceiverAllowlistValue =
                DeviceConfig.getProperty(
                        DeviceConfig.NAMESPACE_ADSERVICES, PROPERTY_BROADCASTRECEIVER_ALLOWLIST);
        mInitialApplySdkSandboxNextRestrictionsValue =
                DeviceConfig.getProperty(
                        DeviceConfig.NAMESPACE_ADSERVICES,
                        PROPERTY_APPLY_SDK_SANDBOX_NEXT_RESTRICTIONS);
        mInitialNextBroadcastReceiverAllowlistValue =
                DeviceConfig.getProperty(
                        DeviceConfig.NAMESPACE_ADSERVICES,
                        PROPERTY_NEXT_BROADCASTRECEIVER_ALLOWLIST);

        mDeviceConfigUtils.deleteProperty(PROPERTY_ENFORCE_RESTRICTIONS);
        mDeviceConfigUtils.deleteProperty(PROPERTY_BROADCASTRECEIVER_ALLOWLIST);
        mDeviceConfigUtils.deleteProperty(PROPERTY_APPLY_SDK_SANDBOX_NEXT_RESTRICTIONS);
        mDeviceConfigUtils.deleteProperty(PROPERTY_NEXT_BROADCASTRECEIVER_ALLOWLIST);

        mRule.getScenario();
    }

    @After
    public void tearDown() throws Exception {
        try {
            mDeviceConfigUtils.resetToInitialValue(
                    PROPERTY_ENFORCE_RESTRICTIONS, mEnforceBroadcastRestrictions);
            mDeviceConfigUtils.resetToInitialValue(
                    PROPERTY_BROADCASTRECEIVER_ALLOWLIST, mInitialBroadcastReceiverAllowlistValue);
            mDeviceConfigUtils.resetToInitialValue(
                    PROPERTY_APPLY_SDK_SANDBOX_NEXT_RESTRICTIONS,
                    mInitialApplySdkSandboxNextRestrictionsValue);
            mDeviceConfigUtils.resetToInitialValue(
                    PROPERTY_NEXT_BROADCASTRECEIVER_ALLOWLIST,
                    mInitialNextBroadcastReceiverAllowlistValue);

            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .dropShellPermissionIdentity();

            // Greedily unload SDK to reduce flakiness
            mSdkSandboxManager.unloadSdk(SDK_PACKAGE);
        } catch (Exception ignored) {
        }
    }

    /**
     * Tests that a SecurityException is thrown when SDK sandbox process tries to register a
     * broadcast receiver because of the default value of true.
     */
    @Test
    public void testRegisterBroadcastReceiver_defaultValueRestrictionsApplied() throws Exception {
        loadSdk();
        assertThrows(
                SecurityException.class,
                () -> mRestrictionsSdkApi.registerBroadcastReceiver(UNPROTECTED_INTENT_ACTIONS));
    }

    /**
     * Tests that a SecurityException is thrown when SDK sandbox process tries to register a
     * broadcast receiver. This behavior depends on the value of a {@link DeviceConfig} property.
     */
    @Test
    public void testRegisterBroadcastReceiver_restrictionsApplied() throws Exception {
        mDeviceConfigUtils.setProperty(PROPERTY_ENFORCE_RESTRICTIONS, "true");
        loadSdk();

        SecurityException thrown =
                assertThrows(
                        SecurityException.class,
                        () ->
                                mRestrictionsSdkApi.registerBroadcastReceiver(
                                        UNPROTECTED_INTENT_ACTIONS));

        assertThat(thrown).hasMessageThat().contains(Intent.ACTION_VOICE_COMMAND);
        assertThat(thrown).hasMessageThat().contains(Intent.ACTION_SET_WALLPAPER);
        assertThat(thrown).hasMessageThat().contains(Intent.ACTION_SHOW_WORK_APPS);
        assertThat(thrown).hasMessageThat().contains(Intent.ACTION_CALL_BUTTON);
        assertThat(thrown).hasMessageThat().contains(Intent.ACTION_WEB_SEARCH);
        assertThat(thrown).hasMessageThat().contains(Intent.ACTION_VOICE_ASSIST);
        assertThat(thrown)
                .hasMessageThat()
                .contains(
                        "SDK sandbox not allowed to register receiver with the given IntentFilter");
    }

    /**
     * Tests that a SecurityException is not thrown when SDK sandbox process tries to register a
     * broadcast receiver. This behavior depends on the value of a {@link DeviceConfig} property.
     */
    @Test(expected = Test.None.class /* no exception expected */)
    public void testRegisterBroadcastReceiver_restrictionsNotApplied() throws Exception {
        mDeviceConfigUtils.setProperty(PROPERTY_ENFORCE_RESTRICTIONS, "false");
        loadSdk();

        mRestrictionsSdkApi.registerBroadcastReceiver(UNPROTECTED_INTENT_ACTIONS);
    }

    @Test
    public void testRegisterBroadcastReceiver_DeviceConfigEmptyAllowlistApplied() throws Exception {
        mDeviceConfigUtils.setProperty(PROPERTY_ENFORCE_RESTRICTIONS, "true");

        // Set an empty allowlist for effectiveTargetSdkVersion U. This should block all
        // BroadcastReceivers.
        mDeviceConfigUtils.setProperty(PROPERTY_BROADCASTRECEIVER_ALLOWLIST, "CgQIIhIA");
        loadSdk();

        assertThrows(
                SecurityException.class,
                () -> mRestrictionsSdkApi.registerBroadcastReceiver(UNPROTECTED_INTENT_ACTIONS));

        // Even protected broadcasts should be blocked.
        assertThrows(
                SecurityException.class,
                () ->
                        mRestrictionsSdkApi.registerBroadcastReceiver(
                                new ArrayList<>(Arrays.asList(Intent.ACTION_SCREEN_OFF))));
    }

    @Test
    public void testRegisterBroadcastReceiver_DeviceConfigAllowlistApplied() throws Exception {
        mDeviceConfigUtils.setProperty(PROPERTY_ENFORCE_RESTRICTIONS, "true");

        ArrayMap<Integer, List<String>> allowedBroadcastReceivers = new ArrayMap<>();
        allowedBroadcastReceivers.put(
                Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
                new ArrayList<>(Arrays.asList(Intent.ACTION_VIEW, Intent.ACTION_SCREEN_OFF)));
        String encodedAllowlist =
                ProtoUtil.encodeBroadcastReceiverAllowlist(allowedBroadcastReceivers);

        mDeviceConfigUtils.setProperty(PROPERTY_BROADCASTRECEIVER_ALLOWLIST, encodedAllowlist);
        loadSdk();

        mRestrictionsSdkApi.registerBroadcastReceiver(
                new ArrayList<>(Arrays.asList(Intent.ACTION_VIEW)));
        mRestrictionsSdkApi.registerBroadcastReceiver(
                new ArrayList<>(Arrays.asList(Intent.ACTION_SCREEN_OFF)));
        mRestrictionsSdkApi.registerBroadcastReceiver(
                new ArrayList<>(Arrays.asList(Intent.ACTION_VIEW, Intent.ACTION_SCREEN_OFF)));
        assertThrows(
                SecurityException.class,
                () ->
                        mRestrictionsSdkApi.registerBroadcastReceiver(
                                new ArrayList<>(Arrays.asList(Intent.ACTION_BATTERY_CHANGED))));
        assertThrows(
                SecurityException.class,
                () ->
                        mRestrictionsSdkApi.registerBroadcastReceiver(
                                new ArrayList<>(Arrays.asList(Intent.ACTION_SEND))));
        assertThrows(
                SecurityException.class,
                () -> mRestrictionsSdkApi.registerBroadcastReceiver(UNPROTECTED_INTENT_ACTIONS));
    }

    @Test
    public void testRegisterBroadcastReceiver_DeviceConfigNextAllowlistApplied() throws Exception {
        mDeviceConfigUtils.setProperty(PROPERTY_ENFORCE_RESTRICTIONS, "true");

        mDeviceConfigUtils.setProperty(PROPERTY_APPLY_SDK_SANDBOX_NEXT_RESTRICTIONS, "true");

        String encodedNextAllowlist =
                ProtoUtil.encodeBroadcastReceiverAllowlist(
                        new ArraySet<>(Arrays.asList(Intent.ACTION_VIEW, Intent.ACTION_SEND)));
        // Set the canary set.
        mDeviceConfigUtils.setProperty(
                PROPERTY_NEXT_BROADCASTRECEIVER_ALLOWLIST, encodedNextAllowlist);
        loadSdk();

        // No exception should be thrown when registering a BroadcastReceiver with
        // Intent.ACTION_VIEW and Intent.ACTION_SEND.
        mRestrictionsSdkApi.registerBroadcastReceiver(new ArrayList<>(List.of(Intent.ACTION_VIEW)));
        mRestrictionsSdkApi.registerBroadcastReceiver(new ArrayList<>(List.of(Intent.ACTION_SEND)));
        mRestrictionsSdkApi.registerBroadcastReceiver(
                new ArrayList<>(List.of(Intent.ACTION_SEND, Intent.ACTION_VIEW)));
        assertThrows(
                SecurityException.class,
                () ->
                        mRestrictionsSdkApi.registerBroadcastReceiver(
                                new ArrayList<>(List.of(Intent.ACTION_SCREEN_OFF))));
        assertThrows(
                SecurityException.class,
                () ->
                        mRestrictionsSdkApi.registerBroadcastReceiver(
                                new ArrayList<>(
                                        Arrays.asList(
                                                Intent.ACTION_SEND, Intent.ACTION_SCREEN_OFF))));
    }

    @Test
    public void testRegisterBroadcastReceiver_DeviceConfigNextRestrictions_AllowlistNotSet()
            throws Exception {
        mDeviceConfigUtils.setProperty(PROPERTY_ENFORCE_RESTRICTIONS, "true");
        // Apply next restrictions, but don't set any value for the allowlist.
        mDeviceConfigUtils.setProperty(PROPERTY_APPLY_SDK_SANDBOX_NEXT_RESTRICTIONS, "true");
        loadSdk();

        // No exception should be thrown when it is a protected broadcast.
        mRestrictionsSdkApi.registerBroadcastReceiver(
                new ArrayList<>(List.of(Intent.ACTION_SCREEN_OFF)));
        assertThrows(
                SecurityException.class,
                () -> mRestrictionsSdkApi.registerBroadcastReceiver(UNPROTECTED_INTENT_ACTIONS));
    }

    /**
     * Tests that a SecurityException is thrown when SDK sandbox process tries to register a
     * broadcast receiver with no action mentioned in the {@link android.content.IntentFilter}
     * object.
     */
    @Test
    public void testRegisterBroadcastReceiver_intentFilterWithoutAction() throws Exception {
        loadSdk();
        SecurityException thrown =
                assertThrows(
                        SecurityException.class,
                        () -> mRestrictionsSdkApi.registerBroadcastReceiver(new ArrayList<>()));

        assertThat(thrown)
                .hasMessageThat()
                .contains(
                        "SDK sandbox not allowed to register receiver with the given IntentFilter");
    }

    @Test
    public void testRegisterBroadcastReceiver_protectedBroadcast() throws Exception {
        mDeviceConfigUtils.setProperty(PROPERTY_ENFORCE_RESTRICTIONS, "true");
        loadSdk();
        mRestrictionsSdkApi.registerBroadcastReceiver(
                new ArrayList<>(
                        Arrays.asList(
                                Intent.ACTION_SHOW_FOREGROUND_SERVICE_MANAGER,
                                Intent.ACTION_BOOT_COMPLETED,
                                Intent.ACTION_SCREEN_ON,
                                Intent.ACTION_SCREEN_OFF)));
    }

    private void loadSdk() {
        FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(SDK_PACKAGE, new Bundle(), Runnable::run, callback);
        callback.assertLoadSdkIsSuccessful();
        SandboxedSdk sandboxedSdk = callback.getSandboxedSdk();

        IBinder binder = sandboxedSdk.getInterface();
        mRestrictionsSdkApi = IRestrictionsSdkApi.Stub.asInterface(binder);
    }
}
