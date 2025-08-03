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

package com.android.server.sdksandbox;

import static com.android.server.wm.ActivityInterceptorCallback.MAINLINE_SDK_SANDBOX_ORDER_ID;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.eq;

import android.Manifest;
import android.app.sdksandbox.testutils.FakeSdkSandboxManagerLocal;
import android.app.sdksandbox.testutils.FakeSdkSandboxService;
import android.app.sdksandbox.testutils.ProtoUtil;
import android.app.sdksandbox.testutils.SdkSandboxDeviceSupportedRule;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ProviderInfo;
import android.os.Build;
import android.os.Process;
import android.util.ArrayMap;
import android.util.ArraySet;

import androidx.annotation.Nullable;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.dx.mockito.inline.extended.StaticMockitoSessionBuilder;
import com.android.modules.utils.build.SdkLevel;
import com.android.server.pm.PackageManagerLocal;
import com.android.server.sdksandbox.testutils.FakeSdkSandboxProvider;
import com.android.server.wm.ActivityInterceptorCallback;
import com.android.server.wm.ActivityInterceptorCallbackRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class SdkSandboxRestrictionsUnitTest {
    private static final String INTENT_ACTION = "action.test";
    private static final String PACKAGE_NAME = "packageName.test";
    private static final String COMPONENT_CLASS_NAME = "className.test";
    private static final String COMPONENT_PACKAGE_NAME = "componentPackageName.test";
    private static final String PROPERTY_APPLY_SDK_SANDBOX_NEXT_RESTRICTIONS =
            "apply_sdk_sandbox_next_restrictions";
    private static final String PROPERTY_ENFORCE_RESTRICTIONS = "sdksandbox_enforce_restrictions";
    private static final String PROPERTY_ACTIVITY_ALLOWLIST =
            "sdksandbox_activity_allowlist_per_targetSdkVersion";
    private static final String PROPERTY_NEXT_ACTIVITY_ALLOWLIST =
            "sdksandbox_next_activity_allowlist";
    private static final String PROPERTY_SERVICES_ALLOWLIST =
            "services_allowlist_per_targetSdkVersion";
    private static final String PROPERTY_NEXT_SERVICE_ALLOWLIST =
            "sdksandbox_next_service_allowlist";

    private SdkSandboxManagerService mService;
    private MockitoSession mStaticMockSession;
    private ArgumentCaptor<ActivityInterceptorCallback> mInterceptorCallbackArgumentCaptor =
            ArgumentCaptor.forClass(ActivityInterceptorCallback.class);
    private SdkSandboxManagerLocal mSdkSandboxManagerLocal;
    private SdkSandboxSettingsListener mSdkSandboxSettingsListener;
    private SdkSandboxManagerService.Injector mInjector;
    private DeviceConfigUtil mDeviceConfigUtil;

    @Rule(order = 0)
    public final SdkSandboxDeviceSupportedRule supportedRule = new SdkSandboxDeviceSupportedRule();

    @Before
    public void setup() {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();

        StaticMockitoSessionBuilder mockitoSessionBuilder =
                ExtendedMockito.mockitoSession()
                        .strictness(Strictness.LENIENT)
                        .spyStatic(Process.class)
                        .initMocks(this);

        if (SdkLevel.isAtLeastU()) {
            mockitoSessionBuilder =
                    mockitoSessionBuilder.mockStatic(ActivityInterceptorCallbackRegistry.class);
        }

        mStaticMockSession = mockitoSessionBuilder.startMocking();

        if (SdkLevel.isAtLeastU()) {
            // mock the activity interceptor registry anc capture the callback if called
            ActivityInterceptorCallbackRegistry registryMock =
                    Mockito.mock(ActivityInterceptorCallbackRegistry.class);
            ExtendedMockito.doReturn(registryMock)
                    .when(ActivityInterceptorCallbackRegistry::getInstance);
            Mockito.doNothing()
                    .when(registryMock)
                    .registerActivityInterceptorCallback(
                            eq(MAINLINE_SDK_SANDBOX_ORDER_ID),
                            mInterceptorCallbackArgumentCaptor.capture());
        }

        // Required to access <sdk-library> information and DeviceConfig update.
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.READ_DEVICE_CONFIG,
                        Manifest.permission.WRITE_DEVICE_CONFIG,
                        // for Context#registerReceiverForAllUsers
                        Manifest.permission.INTERACT_ACROSS_USERS_FULL);

        mInjector =
                Mockito.spy(
                        new FakeInjector(
                                context,
                                new SdkSandboxStorageManager(
                                        context,
                                        new FakeSdkSandboxManagerLocal(),
                                        Mockito.spy(PackageManagerLocal.class),
                                        /*rootDir=*/ context.getDir(
                                                        "test_dir", Context.MODE_PRIVATE)
                                                .getPath()),
                                new FakeSdkSandboxProvider(
                                        Mockito.spy(FakeSdkSandboxService.class)),
                                Mockito.spy(SdkSandboxPulledAtoms.class),
                                new SdkSandboxStatsdLogger()));
        mService = new SdkSandboxManagerService(context, mInjector);
        mSdkSandboxManagerLocal = mService.getLocalManager();
        assertThat(mSdkSandboxManagerLocal).isNotNull();

        mSdkSandboxSettingsListener = mService.getSdkSandboxSettingsListener();
        mDeviceConfigUtil = new DeviceConfigUtil(mSdkSandboxSettingsListener);

        ExtendedMockito.doReturn(true).when(() -> Process.isSdkSandboxUid(Mockito.anyInt()));
    }

    @After
    public void tearDown() {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    /** Tests that only allowed intents may be sent from the sdk sandbox. */
    @Test
    public void testEnforceAllowedToSendBroadcast() {
        Intent disallowedIntent = new Intent(Intent.ACTION_SCREEN_ON);
        assertThrows(
                SecurityException.class,
                () -> mSdkSandboxManagerLocal.enforceAllowedToSendBroadcast(disallowedIntent));
    }

    /** Tests that no broadcast can be sent from the sdk sandbox. */
    @Test
    public void testCanSendBroadcast() {
        assertThat(mSdkSandboxManagerLocal.canSendBroadcast(new Intent())).isFalse();
    }

    /** Tests that only allowed activities may be started from the sdk sandbox. */
    @Test
    public void testEnforceAllowedToStartActivity_defaultAllowedValues() {
        for (String action : SdkSandboxManagerService.DEFAULT_ACTIVITY_ALLOWED_ACTIONS) {
            Intent allowedIntent = new Intent(action);
            mSdkSandboxManagerLocal.enforceAllowedToStartActivity(allowedIntent);
        }

        Intent intentWithoutAction = new Intent();
        mSdkSandboxManagerLocal.enforceAllowedToStartActivity(intentWithoutAction);

        Intent disallowedIntent = new Intent(Intent.ACTION_SCREEN_OFF);
        assertThrows(
                SecurityException.class,
                () -> mSdkSandboxManagerLocal.enforceAllowedToStartActivity(disallowedIntent));
    }

    @Test
    public void testEnforceAllowedToStartActivity_restrictionsNotApplied() {
        mDeviceConfigUtil.setDeviceConfigProperty(PROPERTY_ENFORCE_RESTRICTIONS, "false");
        Intent intent = new Intent(Intent.ACTION_CALL);
        mSdkSandboxManagerLocal.enforceAllowedToStartActivity(intent);
    }

    @Test
    public void testEnforceAllowedToStartActivity_deviceConfigAllowlist() {
        ArrayMap<Integer, List<String>> allowedActivities = new ArrayMap<>();
        allowedActivities.put(
                Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
                new ArrayList<>(Arrays.asList(Intent.ACTION_CALL)));
        String encodedAllowedActivities = ProtoUtil.encodeActivityAllowlist(allowedActivities);
        mDeviceConfigUtil.setDeviceConfigProperty(
                PROPERTY_ACTIVITY_ALLOWLIST, encodedAllowedActivities);

        Intent intent = new Intent(Intent.ACTION_CALL);
        mSdkSandboxManagerLocal.enforceAllowedToStartActivity(intent);

        for (String action : SdkSandboxManagerService.DEFAULT_ACTIVITY_ALLOWED_ACTIONS) {
            assertThrows(
                    SecurityException.class,
                    () ->
                            mSdkSandboxManagerLocal.enforceAllowedToStartActivity(
                                    new Intent(action)));
        }
    }

    @Test
    public void testEnforceAllowedToStartActivity_restrictionEnforcedDeviceConfigAllowlistNotSet() {
        mDeviceConfigUtil.setDeviceConfigProperty(PROPERTY_ACTIVITY_ALLOWLIST, null);

        for (String action : SdkSandboxManagerService.DEFAULT_ACTIVITY_ALLOWED_ACTIONS) {
            mSdkSandboxManagerLocal.enforceAllowedToStartActivity(new Intent(action));
        }

        assertThrows(
                SecurityException.class,
                () ->
                        mSdkSandboxManagerLocal.enforceAllowedToStartActivity(
                                new Intent(Intent.ACTION_CALL)));
    }

    @Test
    public void testEnforceAllowedToStartActivity_restrictionsNotEnforced() {
        mDeviceConfigUtil.setDeviceConfigProperty(PROPERTY_ENFORCE_RESTRICTIONS, "false");
        mSdkSandboxManagerLocal.enforceAllowedToStartActivity(new Intent());
    }

    @Test
    public void testEnforceAllowedToStartActivity_nextRestrictionsApplied() {
        ArrayMap<Integer, List<String>> allowedActivities = new ArrayMap<>();
        allowedActivities.put(
                Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
                new ArrayList<>(Arrays.asList(Intent.ACTION_CALL)));
        String encodedAllowedActivities = ProtoUtil.encodeActivityAllowlist(allowedActivities);
        mDeviceConfigUtil.setDeviceConfigProperty(
                PROPERTY_ACTIVITY_ALLOWLIST, encodedAllowedActivities);

        mSdkSandboxManagerLocal.enforceAllowedToStartActivity(new Intent(Intent.ACTION_CALL));
        assertThrows(
                SecurityException.class,
                () ->
                        mSdkSandboxManagerLocal.enforceAllowedToStartActivity(
                                new Intent(Intent.ACTION_VIEW)));

        ArraySet<String> nextAllowedActivities =
                new ArraySet<>(Arrays.asList(Intent.ACTION_WEB_SEARCH));
        String encodedNextAllowedActivities =
                ProtoUtil.encodeActivityAllowlist(nextAllowedActivities);
        mDeviceConfigUtil.setDeviceConfigProperty(
                PROPERTY_NEXT_ACTIVITY_ALLOWLIST, encodedNextAllowedActivities);

        mDeviceConfigUtil.setDeviceConfigProperty(
                PROPERTY_APPLY_SDK_SANDBOX_NEXT_RESTRICTIONS, "true");
        mSdkSandboxManagerLocal.enforceAllowedToStartActivity(new Intent(Intent.ACTION_WEB_SEARCH));
        assertThrows(
                SecurityException.class,
                () ->
                        mSdkSandboxManagerLocal.enforceAllowedToStartActivity(
                                new Intent(Intent.ACTION_CALL)));
    }

    @Test
    public void testEnforceAllowedToStartActivity_nextRestrictionsAppliedButAllowlistNotSet() {
        mDeviceConfigUtil.setDeviceConfigProperty(
                PROPERTY_APPLY_SDK_SANDBOX_NEXT_RESTRICTIONS, "true");
        mDeviceConfigUtil.setDeviceConfigProperty(PROPERTY_NEXT_ACTIVITY_ALLOWLIST, "");

        Intent intent = new Intent(Intent.ACTION_CALL);

        assertThrows(
                SecurityException.class,
                () -> mSdkSandboxManagerLocal.enforceAllowedToStartActivity(intent));
        for (String action : SdkSandboxManagerService.DEFAULT_ACTIVITY_ALLOWED_ACTIONS) {
            mSdkSandboxManagerLocal.enforceAllowedToStartActivity(new Intent(action));
        }

        ArrayMap<Integer, List<String>> allowedActivities = new ArrayMap<>();
        allowedActivities.put(
                Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
                new ArrayList<>(Arrays.asList(Intent.ACTION_CALL)));
        String encodedAllowedActivities = ProtoUtil.encodeActivityAllowlist(allowedActivities);
        mDeviceConfigUtil.setDeviceConfigProperty(
                PROPERTY_ACTIVITY_ALLOWLIST, encodedAllowedActivities);

        mSdkSandboxManagerLocal.enforceAllowedToStartActivity(intent);
        for (String action : SdkSandboxManagerService.DEFAULT_ACTIVITY_ALLOWED_ACTIONS) {
            assertThrows(
                    SecurityException.class,
                    () ->
                            mSdkSandboxManagerLocal.enforceAllowedToStartActivity(
                                    new Intent(action)));
        }
    }

    @Test
    public void testEnforceAllowedToStartOrBindService_disallowNonExistentPackage() {
        Intent intent = new Intent().setComponent(new ComponentName("nonexistent.package", "test"));
        assertThrows(
                SecurityException.class,
                () -> mSdkSandboxManagerLocal.enforceAllowedToStartOrBindService(intent));
    }

    @Test
    public void testEnforceAllowedToStartOrBindService_AdServicesApkNotPresent() throws Exception {
        String adServicesPackageName = mInjector.getAdServicesPackageName();
        Mockito.when(mInjector.getAdServicesPackageName()).thenReturn(null);
        Intent intent = new Intent().setComponent(new ComponentName(adServicesPackageName, "test"));
        assertThrows(
                SecurityException.class,
                () -> mSdkSandboxManagerLocal.enforceAllowedToStartOrBindService(intent));
    }

    @Test
    public void testEnforceAllowedToStartOrBindService_allowedPackages() throws Exception {
        Intent intent =
                new Intent()
                        .setComponent(
                                new ComponentName(mInjector.getAdServicesPackageName(), "test"));
        mSdkSandboxManagerLocal.enforceAllowedToStartOrBindService(intent);
    }

    @Test
    public void testServiceRestriction_noFieldsSet() {
        /*
         * Service allowlist
         * allowlist_per_target_sdk {
         *   key: 34
         *   value: {
         *     allowed_services: {
         *     }
         *   }
         * }
         */
        ArrayMap<Integer, List<ArrayMap<String, String>>> allowedServices = new ArrayMap<>();
        allowedServices.put(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, Arrays.asList());
        String encodedServiceAllowlist = ProtoUtil.encodeServiceAllowlist(allowedServices);
        mDeviceConfigUtil.setDeviceConfigProperty(
                PROPERTY_SERVICES_ALLOWLIST, encodedServiceAllowlist);

        /* Allows all the services to start/ bind */
        assertThrows(
                SecurityException.class,
                () ->
                        testServiceRestriction(
                                /*action=*/ null,
                                /*packageName=*/ null,
                                /*componentClassName=*/ null,
                                /*componentPackageName=*/ null));
    }

    @Test
    public void testServiceRestriction_oneFieldSet() {
        /*
         * Service allowlist
         * allowlist_per_target_sdk {
         *   key: 34
         *   value: {
         *     allowed_services: {
         *       action : "*"
         *       packageName : "packageName.test"
         *       componentClassName : "*"
         *       componentPackageName : "*"
         *     }
         *     allowed_services: {
         *       action : "*"
         *       packageName : "*"
         *       componentClassName : "className.test"
         *       componentPackageName : "*"
         *     }
         *     allowed_services: {
         *       action : "action.test"
         *       packageName : "*"
         *       componentClassName : "*"
         *       componentPackageName : "*"
         *     }
         *     allowed_services: {
         *       action : "*"
         *       packageName : "*"
         *       componentClassName : "*"
         *       componentPackageName : "componentPackageName.test"
         *     }
         *   }
         * }
         */
        ArrayMap<Integer, List<ArrayMap<String, String>>> allowedServices = new ArrayMap<>();
        List<ArrayMap<String, String>> services = new ArrayList<>();
        services.add(
                getAllowedServicesMap(
                        /*action=*/ "action.test",
                        /*packageName=*/ "*",
                        /*componentClassName=*/ "*",
                        /*componentPackageName=*/ "*"));
        services.add(
                getAllowedServicesMap(
                        /*action=*/ "*",
                        /*packageName=*/ "packageName.test",
                        /*componentClassName=*/ "*",
                        /*componentPackageName=*/ "*"));
        services.add(
                getAllowedServicesMap(
                        /*action=*/ "*",
                        /*packageName=*/ "*",
                        /*componentClassName=*/ "className.test",
                        /*componentPackageName=*/ "*"));
        services.add(
                getAllowedServicesMap(
                        /*action=*/ "*",
                        /*packageName=*/ "*",
                        /*componentClassName=*/ "*",
                        /*componentPackageName=*/ "componentPackageName.test"));
        allowedServices.put(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, services);
        String encodedServiceAllowlist = ProtoUtil.encodeServiceAllowlist(allowedServices);
        mDeviceConfigUtil.setDeviceConfigProperty(
                PROPERTY_SERVICES_ALLOWLIST, encodedServiceAllowlist);

        testServiceRestriction(
                /*action=*/ INTENT_ACTION,
                /*packageName=*/ null,
                /*componentClassName=*/ null,
                /*componentPackageName=*/ null);

        testServiceRestriction(
                /*action=*/ null,
                /*packageName=*/ PACKAGE_NAME,
                /*componentClassName=*/ null,
                /*componentPackageName=*/ null);

        testServiceRestriction(
                /*action=*/ null,
                /*packageName=*/ null,
                /*componentClassName=*/ COMPONENT_CLASS_NAME,
                /*componentPackageName=*/ null);

        assertThrows(
                SecurityException.class,
                () ->
                        testServiceRestriction(
                                /*action=*/ null,
                                /*packageName=*/ null,
                                /*componentClassName=*/ null,
                                /*componentPackageName=*/ null));
    }

    @Test
    public void testServiceRestriction_twoFieldsSet() {
        /*
         * Service allowlist
         * allowlist_per_target_sdk {
         *   key: 34
         *   value: {
         *     allowed_services: {
         *       action : "action.test"
         *       packageName : "packageName.test"
         *       componentClassName : "*"
         *       componentPackageName : "*"
         *     }
         *     allowed_services: {
         *       action : "action.test"
         *       packageName : "*"
         *       componentClassName : "className.test"
         *       componentPackageName : "*"
         *     }
         *     allowed_services: {
         *       action : "*"
         *       packageName : "packageName.test"
         *       componentClassName : "className.test"
         *       componentPackageName : "*"
         *     }
         *   }
         * }
         */
        ArrayMap<Integer, List<ArrayMap<String, String>>> allowedServices = new ArrayMap<>();
        List<ArrayMap<String, String>> services = new ArrayList<>();
        services.add(
                getAllowedServicesMap(
                        /*action=*/ "action.test",
                        /*packageName=*/ "packageName.test",
                        /*componentClassName=*/ "*",
                        /*componentPackageName=*/ "*"));
        services.add(
                getAllowedServicesMap(
                        /*action=*/ "action.test",
                        /*packageName=*/ "*",
                        /*componentClassName=*/ "className.test",
                        /*componentPackageName=*/ "*"));
        services.add(
                getAllowedServicesMap(
                        /*action=*/ "*",
                        /*packageName=*/ "packageName.test",
                        /*componentClassName=*/ "className.test",
                        /*componentPackageName=*/ "*"));
        allowedServices.put(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, services);
        String encodedServiceAllowlist = ProtoUtil.encodeServiceAllowlist(allowedServices);
        mDeviceConfigUtil.setDeviceConfigProperty(
                PROPERTY_SERVICES_ALLOWLIST, encodedServiceAllowlist);

        testServiceRestriction(
                /*action=*/ INTENT_ACTION,
                /*packageName=*/ PACKAGE_NAME,
                /*componentClassName=*/ null,
                /*componentPackageName=*/ null);

        testServiceRestriction(
                /*action=*/ INTENT_ACTION,
                /*packageName=*/ null,
                /*componentClassName=*/ COMPONENT_CLASS_NAME,
                /*componentPackageName=*/ null);

        testServiceRestriction(
                /*action=*/ null,
                /*packageName=*/ PACKAGE_NAME,
                /*componentClassName=*/ COMPONENT_CLASS_NAME,
                /*componentPackageName=*/ null);

        assertThrows(
                SecurityException.class,
                () ->
                        testServiceRestriction(
                                /*action=*/ null,
                                /*packageName=*/ null,
                                /*componentClassName=*/ null,
                                /*componentPackageName=*/ null));

        assertThrows(
                SecurityException.class,
                () ->
                        testServiceRestriction(
                                /*action=*/ INTENT_ACTION,
                                /*packageName=*/ null,
                                /*componentClassName=*/ null,
                                /*componentPackageName=*/ null));
    }

    @Test
    public void testServiceRestriction_threeFieldsSet() {
        /*
         * Service allowlist
         * allowlist_per_target_sdk {
         *   key: 34
         *   value: {
         *     allowed_services: {
         *       action : "action.test"
         *       packageName : "packageName.test"
         *       componentClassName : "className.test"
         *       componentPackageName : "*"
         *     }
         *     allowed_services: {
         *       action : "action.test"
         *       packageName : "packageName.test"
         *       componentClassName : "*"
         *       componentPackageName : "componentPackageName.test"
         *     }
         *     allowed_services: {
         *       action : "action.test"
         *       packageName : "*"
         *       componentClassName : "className.test"
         *       componentPackageName : "componentPackageName.test"
         *     }
         *     allowed_services: {
         *       action : "*"
         *       packageName : "packageName.test"
         *       componentClassName : "className.test"
         *       componentPackageName : "componentPackageName.test"
         *     }
         *   }
         * }
         */
        ArrayMap<Integer, List<ArrayMap<String, String>>> allowedServices = new ArrayMap<>();
        List<ArrayMap<String, String>> services = new ArrayList<>();
        services.add(
                getAllowedServicesMap(
                        /*action=*/ "action.test",
                        /*packageName=*/ "packageName.test",
                        /*componentClassName=*/ "className.test",
                        /*componentPackageName=*/ "*"));
        services.add(
                getAllowedServicesMap(
                        /*action=*/ "action.test",
                        /*packageName=*/ "packageName.test",
                        /*componentClassName=*/ "*",
                        /*componentPackageName=*/ "componentPackageName.test"));
        services.add(
                getAllowedServicesMap(
                        /*action=*/ "action.test",
                        /*packageName=*/ "*",
                        /*componentClassName=*/ "className.test",
                        /*componentPackageName=*/ "componentPackageName.test"));
        services.add(
                getAllowedServicesMap(
                        /*action=*/ "*",
                        /*packageName=*/ "packageName.test",
                        /*componentClassName=*/ "className.test",
                        /*componentPackageName=*/ "componentPackageName.test"));
        allowedServices.put(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, services);
        String encodedServiceAllowlist = ProtoUtil.encodeServiceAllowlist(allowedServices);
        mDeviceConfigUtil.setDeviceConfigProperty(
                PROPERTY_SERVICES_ALLOWLIST, encodedServiceAllowlist);

        testServiceRestriction(
                /*action=*/ INTENT_ACTION,
                /*packageName=*/ PACKAGE_NAME,
                /*componentClassName=*/ COMPONENT_CLASS_NAME,
                /*componentPackageName=*/ null);

        testServiceRestriction(
                /*action=*/ INTENT_ACTION,
                /*packageName=*/ PACKAGE_NAME,
                /*componentClassName=*/ null,
                /*componentPackageName=*/ COMPONENT_PACKAGE_NAME);

        testServiceRestriction(
                /*action=*/ INTENT_ACTION,
                /*packageName=*/ null,
                /*componentClassName=*/ COMPONENT_CLASS_NAME,
                /*componentPackageName=*/ COMPONENT_PACKAGE_NAME);

        testServiceRestriction(
                /*action=*/ null,
                /*packageName=*/ PACKAGE_NAME,
                /*componentClassName=*/ COMPONENT_CLASS_NAME,
                /*componentPackageName=*/ COMPONENT_PACKAGE_NAME);

        assertThrows(
                SecurityException.class,
                () ->
                        testServiceRestriction(
                                /*action=*/ INTENT_ACTION,
                                /*packageName=*/ null,
                                /*componentClassName=*/ null,
                                /*componentPackageName=*/ null));
    }

    @Test
    public void testServiceRestriction_multipleEntriesAllowlist() {
        /*
         * Service allowlist
         * allowlist_per_target_sdk {
         *   key: 34
         *   value: {
         *     allowed_services: {
         *       action : "action.test1"
         *       packageName : "packageName.test1"
         *       componentClassName : "className.test1"
         *       componentPackageName : "componentPackageName.test1"
         *     }
         *     allowed_services: {
         *       action : "action.test2"
         *       packageName : "packageName.test2"
         *       componentClassName : "className.test2"
         *       componentPackageName : "componentPackageName.test2"
         *     }
         *   }
         * }
         */
        ArrayMap<Integer, List<ArrayMap<String, String>>> allowedServices = new ArrayMap<>();
        List<ArrayMap<String, String>> services = new ArrayList<>();
        services.add(
                getAllowedServicesMap(
                        /*action=*/ "action.test1",
                        /*packageName=*/ "packageName.test1",
                        /*componentClassName=*/ "className.test1",
                        /*componentPackageName=*/ "componentPackageName.test1"));
        services.add(
                getAllowedServicesMap(
                        /*action=*/ "action.test2",
                        /*packageName=*/ "packageName.test2",
                        /*componentClassName=*/ "className.test2",
                        /*componentPackageName=*/ "componentPackageName.test2"));
        allowedServices.put(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, services);
        String encodedServiceAllowlist = ProtoUtil.encodeServiceAllowlist(allowedServices);
        mDeviceConfigUtil.setDeviceConfigProperty(
                PROPERTY_SERVICES_ALLOWLIST, encodedServiceAllowlist);

        testServiceRestriction(
                /*action=*/ "action.test1",
                /*packageName=*/ "packageName.test1",
                /*componentClassName=*/ "className.test1",
                /*componentPackageName=*/ "componentPackageName.test1");
    }

    @Test
    public void testServiceRestrictions_DeviceConfigNextAllowlistApplied() throws Exception {
        mDeviceConfigUtil.setDeviceConfigProperty(
                PROPERTY_APPLY_SDK_SANDBOX_NEXT_RESTRICTIONS, "true");
        /*
         * Service allowlist
         * allowlist_per_target_sdk {
         *   key: 34
         *   value: {
         *     allowed_services: {
         *       action : "action.test"
         *       packageName : "packageName.test"
         *       componentClassName : "className.test"
         *       componentPackageName : "*"
         *     }
         *   }
         * }
         */
        ArrayMap<Integer, List<ArrayMap<String, String>>> allowedServices = new ArrayMap<>();
        List<ArrayMap<String, String>> services = new ArrayList<>();
        services.add(
                getAllowedServicesMap(
                        /*action=*/ "action.test",
                        /*packageName=*/ "packageName.test",
                        /*componentClassName=*/ "className.test",
                        /*componentPackageName=*/ "*"));
        allowedServices.put(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, services);
        String encodedServiceAllowlist = ProtoUtil.encodeServiceAllowlist(allowedServices);
        mDeviceConfigUtil.setDeviceConfigProperty(
                PROPERTY_SERVICES_ALLOWLIST, encodedServiceAllowlist);

        /*
         * Service allowlist
         * allowed_services {
         *   action : "action.next"
         *   packageName : "packageName.next"
         *   componentClassName : "className.next"
         *   componentPackageName : "*"
         * }
         */
        List<ArrayMap<String, String>> allowedNextServices =
                new ArrayList<>(
                        Arrays.asList(
                                getAllowedServicesMap(
                                        /*action=*/ "action.next",
                                        /*packageName=*/ "packageName.next",
                                        /*componentClassName=*/ "className.next",
                                        /*componentPackageName=*/ "*")));
        String encodedNextServiceAllowlist = ProtoUtil.encodeServiceAllowlist(allowedNextServices);
        mDeviceConfigUtil.setDeviceConfigProperty(
                PROPERTY_NEXT_SERVICE_ALLOWLIST, encodedNextServiceAllowlist);

        testServiceRestriction(
                /*action=*/ "action.next",
                /*packageName=*/ "packageName.next",
                /*componentClassName=*/ "className.next",
                /*componentPackageName=*/ null);

        assertThrows(
                SecurityException.class,
                () ->
                        testServiceRestriction(
                                /*action=*/ "action.test",
                                /*packageName=*/ "packageName.test",
                                /*componentClassName=*/ "className.test",
                                /*componentPackageName=*/ null));
    }

    @Test
    public void testServiceRestrictions_ComponentNotSet() {
        /*
         * Service allowlist
         * allowlist_per_target_sdk {
         *   key: 34
         *   value: {
         *     allowed_services: {
         *       action : "action.test"
         *       packageName : "*"
         *       componentClassName : "*"
         *       componentPackageName: "*"
         *     }
         *   }
         * }
         */
        ArrayMap<Integer, List<ArrayMap<String, String>>> allowedServices = new ArrayMap<>();
        List<ArrayMap<String, String>> services =
                new ArrayList<>(
                        Arrays.asList(
                                getAllowedServicesMap(
                                        /*action=*/ "action.test",
                                        /*packageName=*/ "*",
                                        /*componentClassName=*/ "*",
                                        /*componentPackageName=*/ "*")));
        allowedServices.put(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, services);
        String encodedServiceAllowlist = ProtoUtil.encodeServiceAllowlist(allowedServices);
        mDeviceConfigUtil.setDeviceConfigProperty(
                PROPERTY_SERVICES_ALLOWLIST, encodedServiceAllowlist);

        final Intent intent = new Intent(INTENT_ACTION);
        mSdkSandboxManagerLocal.enforceAllowedToStartOrBindService(intent);
    }

    @Test
    public void testServiceRestrictions_AllFieldsSetToWildcard() {
        /*
         * Service allowlist
         * allowlist_per_target_sdk {
         *   key: 34
         *   value: {
         *     allowed_services: {
         *       action : "*"
         *       packageName : "*"
         *       componentPackageName : "*"
         *       componentClassName : "*"
         *     }
         *   }
         * }
         */
        ArrayMap<Integer, List<ArrayMap<String, String>>> allowedServices = new ArrayMap<>();
        List<ArrayMap<String, String>> services =
                new ArrayList<>(
                        Arrays.asList(
                                getAllowedServicesMap(
                                        /*action=*/ "*",
                                        /*packageName=*/ "*",
                                        /*componentClassName=*/ "*",
                                        /*componentPackageName=*/ "*")));
        allowedServices.put(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, services);
        String encodedServiceAllowlist = ProtoUtil.encodeServiceAllowlist(allowedServices);
        mDeviceConfigUtil.setDeviceConfigProperty(
                PROPERTY_SERVICES_ALLOWLIST, encodedServiceAllowlist);

        testServiceRestriction(
                /*action=*/ INTENT_ACTION,
                /*packageName=*/ COMPONENT_PACKAGE_NAME,
                /*componentClassName=*/ COMPONENT_CLASS_NAME,
                /*componentPackageName=*/ COMPONENT_PACKAGE_NAME);

        testServiceRestriction(
                /*action=*/ null,
                /*packageName=*/ null,
                /*componentClassName=*/ null,
                /*componentPackageName=*/ null);

        testServiceRestriction(
                /*action=*/ INTENT_ACTION,
                /*packageName=*/ null,
                /*componentClassName=*/ null,
                /*componentPackageName=*/ null);
    }

    @Test
    public void testServiceRestrictions_AllFieldsSet() {
        /*
         * Service allowlist
         * allowlist_per_target_sdk {
         *   key: 34
         *   value: {
         *     allowed_services: {
         *       action : "action.test"
         *       packageName : "packageName.test"
         *       componentClassName : "className.test"
         *       componentPackageName : "componentPackageName.test"
         *       }
         *     }
         * }
         */
        ArrayMap<Integer, List<ArrayMap<String, String>>> allowedServices = new ArrayMap<>();
        List<ArrayMap<String, String>> services =
                new ArrayList<>(
                        Arrays.asList(
                                getAllowedServicesMap(
                                        /*action=*/ "action.test",
                                        /*packageName=*/ "packageName.test",
                                        /*componentClassName=*/ "className.test",
                                        /*componentPackageName=*/ "componentPackageName.test")));
        allowedServices.put(Build.VERSION_CODES.UPSIDE_DOWN_CAKE, services);
        String encodedServiceAllowlist = ProtoUtil.encodeServiceAllowlist(allowedServices);
        mDeviceConfigUtil.setDeviceConfigProperty(
                PROPERTY_SERVICES_ALLOWLIST, encodedServiceAllowlist);
        testServiceRestriction(
                INTENT_ACTION, PACKAGE_NAME, COMPONENT_CLASS_NAME, COMPONENT_PACKAGE_NAME);
    }

    /**
     * Tests expected behavior when restrictions are enabled and only protected broadcasts included.
     */
    @Test
    public void testCanRegisterBroadcastReceiver_deviceConfigUnsetProtectedBroadcasts() {
        assertThat(
                        mSdkSandboxManagerLocal.canRegisterBroadcastReceiver(
                                new IntentFilter(Intent.ACTION_SCREEN_OFF),
                                /*flags= */ 0,
                                /*onlyProtectedBroadcasts= */ true))
                .isTrue();
    }

    /** Tests expected behavior when restrictions are enabled and no protected broadcast. */
    @Test
    public void testCanRegisterBroadcastReceiver_deviceConfigUnsetUnprotectedBroadcasts() {
        assertThat(
                        mSdkSandboxManagerLocal.canRegisterBroadcastReceiver(
                                new IntentFilter(Intent.ACTION_SEND),
                                /*flags= */ 0,
                                /*onlyProtectedBroadcasts= */ false))
                .isFalse();
    }

    /** Tests expected behavior when broadcast receiver restrictions are not applied. */
    @Test
    public void testCanRegisterBroadcastReceiver_restrictionsNotApplied() {
        mDeviceConfigUtil.setDeviceConfigProperty(PROPERTY_ENFORCE_RESTRICTIONS, "false");
        assertThat(
                        mSdkSandboxManagerLocal.canRegisterBroadcastReceiver(
                                new IntentFilter(Intent.ACTION_SEND),
                                /*flags= */ 0,
                                /*onlyProtectedBroadcasts= */ false))
                .isTrue();
    }

    /** Tests expected behavior when broadcast receiver restrictions are applied. */
    @Test
    public void testCanRegisterBroadcastReceiver_restrictionsApplied() {
        mDeviceConfigUtil.setDeviceConfigProperty(PROPERTY_ENFORCE_RESTRICTIONS, "true");
        assertThat(
                        mSdkSandboxManagerLocal.canRegisterBroadcastReceiver(
                                new IntentFilter(Intent.ACTION_SEND),
                                /*flags= */ 0,
                                /*onlyProtectedBroadcasts= */ false))
                .isFalse();
    }

    /** Tests expected behavior when callingUid is not a sandbox UID. */
    @Test
    public void testCanRegisterBroadcastReceiver_notSandboxProcess() {
        ExtendedMockito.doReturn(false).when(() -> Process.isSdkSandboxUid(Mockito.anyInt()));
        assertThat(
                        mSdkSandboxManagerLocal.canRegisterBroadcastReceiver(
                                new IntentFilter(Intent.ACTION_SEND),
                                /*flags= */ 0,
                                /*onlyProtectedBroadcasts= */ false))
                .isTrue();
    }

    /** Tests expected behavior when IntentFilter is blank. */
    @Test
    public void testCanRegisterBroadcastReceiver_blankIntentFilter() {
        assertThat(
                        mSdkSandboxManagerLocal.canRegisterBroadcastReceiver(
                                new IntentFilter(),
                                /*flags= */ 0,
                                /*onlyProtectedBroadcasts= */ false))
                .isFalse();
    }

    /**
     * Tests expected behavior when broadcast receiver is registering a broadcast which contains
     * only protected broadcasts
     */
    @Test
    public void testCanRegisterBroadcastReceiver_protectedBroadcast() {
        mDeviceConfigUtil.setDeviceConfigProperty(PROPERTY_ENFORCE_RESTRICTIONS, "true");
        assertThat(
                        mSdkSandboxManagerLocal.canRegisterBroadcastReceiver(
                                new IntentFilter(Intent.ACTION_SEND),
                                /*flags= */ Context.RECEIVER_NOT_EXPORTED,
                                /*onlyProtectedBroadcasts= */ true))
                .isTrue();
    }

    @Test
    public void testSdkSandboxSettings_canAccessContentProviderFromSdkSandbox_DefaultAccess() {
        mDeviceConfigUtil.setDeviceConfigProperty(PROPERTY_ENFORCE_RESTRICTIONS, null);
        // The default value of the flag enforcing restrictions is true and access should be
        // restricted.
        assertThat(
                        mSdkSandboxManagerLocal.canAccessContentProviderFromSdkSandbox(
                                new ProviderInfo()))
                .isFalse();
    }

    @Test
    public void testSdkSandboxSettings_canAccessContentProviderFromSdkSandbox_AccessNotAllowed() {
        mDeviceConfigUtil.setDeviceConfigProperty(PROPERTY_ENFORCE_RESTRICTIONS, "true");
        assertThat(
                        mSdkSandboxManagerLocal.canAccessContentProviderFromSdkSandbox(
                                new ProviderInfo()))
                .isFalse();
    }

    @Test
    public void testSdkSandboxSettings_canAccessContentProviderFromSdkSandbox_AccessAllowed() {
        mDeviceConfigUtil.setDeviceConfigProperty(PROPERTY_ENFORCE_RESTRICTIONS, "false");
        assertThat(
                        mSdkSandboxManagerLocal.canAccessContentProviderFromSdkSandbox(
                                new ProviderInfo()))
                .isTrue();
    }

    private void testServiceRestriction(
            @Nullable String action,
            @Nullable String packageName,
            @Nullable String componentClassName,
            @Nullable String componentPackageName) {
        Intent intent = Objects.isNull(action) ? new Intent() : new Intent(action);
        intent.setPackage(packageName);
        if (Objects.isNull(componentPackageName)) {
            componentPackageName = "nonexistent.package";
        }
        if (Objects.isNull(componentClassName)) {
            componentClassName = "nonexistent.class";
        }
        intent.setComponent(new ComponentName(componentPackageName, componentClassName));

        mSdkSandboxManagerLocal.enforceAllowedToStartOrBindService(intent);
    }

    private ArrayMap<String, String> getAllowedServicesMap(
            String action,
            String packageName,
            String componentClassName,
            String componentPackageName) {
        ArrayMap<String, String> data = new ArrayMap<>(/* capacity= */ 4);
        data.put("action", action);
        data.put("packageName", packageName);
        data.put("componentClassName", componentClassName);
        data.put("componentPackageName", componentPackageName);
        return data;
    }
}
