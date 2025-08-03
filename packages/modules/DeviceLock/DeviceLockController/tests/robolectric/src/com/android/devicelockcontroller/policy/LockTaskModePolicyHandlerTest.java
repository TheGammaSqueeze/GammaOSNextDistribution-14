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

package com.android.devicelockcontroller.policy;

import static android.Manifest.permission.RECEIVE_EMERGENCY_BROADCAST;
import static android.content.pm.PackageInfo.REQUESTED_PERMISSION_GRANTED;

import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_KIOSK_ALLOWLIST;
import static com.android.devicelockcontroller.common.DeviceLockConstants.EXTRA_KIOSK_PACKAGE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.robolectric.Shadows.shadowOf;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Looper;
import android.provider.Settings;
import android.provider.Settings.Secure;
import android.provider.Telephony;
import android.telecom.TelecomManager;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.core.content.pm.ApplicationInfoBuilder;
import androidx.test.core.content.pm.PackageInfoBuilder;
import androidx.work.testing.WorkManagerTestInitHelper;

import com.android.devicelockcontroller.storage.SetupParametersClient;
import com.android.devicelockcontroller.storage.UserParameters;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowPackageManager;
import org.robolectric.shadows.ShadowTelecomManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

@RunWith(RobolectricTestRunner.class)
public final class LockTaskModePolicyHandlerTest {
    private static final String TEST_PACKAGE = "test.package1";
    private static final String DIALER_PACKAGE = "test.dialer";
    private static final String SETTINGS_PACKAGE = "test.settings";
    private static final String PERMISSION_PACKAGE = "test.permissions";
    private static final String DEVICELOCK_CONTROLLER_PACKAGE = "com.android.devicelockcontroller";
    private static final String PACKAGE_OVERRIDING_HOME = "com.home.package";
    private static final String[] EXPECTED_ALLOWLIST_PACKAGES =
            new String[]{TEST_PACKAGE, SETTINGS_PACKAGE, DIALER_PACKAGE,
                    DEVICELOCK_CONTROLLER_PACKAGE};
    private static final String TEST_ACTIVITY = "TestActivity";
    private static final String CELL_BROADCAST_RECEIVER_PACKAGE =
            "test.cell.broadcast.receiver";
    private static final String IME_PACKAGE = "test.ime";
    private static final String IME_COMPONENT = IME_PACKAGE + "/.inputmethod";

    @Rule
    public MockitoRule mMockitoRule = MockitoJUnit.rule();
    @Mock
    private DevicePolicyManager mMockDpm;
    @Captor
    private ArgumentCaptor<Integer> mAllowedFeatures;
    @Captor
    private ArgumentCaptor<String[]> mAllowedPackages;

    private ShadowTelecomManager mTelecomManager;
    private LockTaskModePolicyHandler mHandler;
    private Context mContext;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        WorkManagerTestInitHelper.initializeTestWorkManager(mContext);
        mTelecomManager = Shadow.extract(mContext.getSystemService(TelecomManager.class));
        mHandler = new LockTaskModePolicyHandler(mContext, mMockDpm,
                Executors.newSingleThreadExecutor());
    }

    @Test
    public void onProvisionInProgress_shouldHaveExpectedLockTaskFeaturesAndPackages()
            throws ExecutionException, InterruptedException {
        final String[] expectedAllowlistPackages = {DEVICELOCK_CONTROLLER_PACKAGE};
        mHandler.onProvisionInProgress().get();
        shadowOf(Looper.getMainLooper()).idle();
        assertLockTaskMode(LockTaskModePolicyHandler.DEFAULT_LOCK_TASK_FEATURES_FOR_DLC,
                expectedAllowlistPackages);
    }

    @Test
    public void onProvisionInProgress_shouldHaveDefaultDialerInExpectedLockTaskPackages()
            throws ExecutionException, InterruptedException {
        final String[] expectedAllowlistPackages = {DIALER_PACKAGE, DEVICELOCK_CONTROLLER_PACKAGE};
        mTelecomManager.setDefaultDialer(DIALER_PACKAGE);
        mHandler.onProvisionInProgress().get();
        shadowOf(Looper.getMainLooper()).idle();
        assertLockTaskMode(LockTaskModePolicyHandler.DEFAULT_LOCK_TASK_FEATURES_FOR_DLC,
                expectedAllowlistPackages);
    }

    @Test
    public void onProvisioned_shouldHaveNonDuplicateLockTaskFeaturesAndPackages()
            throws ExecutionException, InterruptedException {
        setupKioskAllowListWithDuplicates();
        mHandler.onProvisioned().get();
        shadowOf(Looper.getMainLooper()).idle();
        assertLockTaskMode(LockTaskModePolicyHandler.DEFAULT_LOCK_TASK_FEATURES_FOR_KIOSK,
                EXPECTED_ALLOWLIST_PACKAGES);
    }

    @Test
    public void onProvisionPaused_shouldDisableLockTaskMode()
            throws ExecutionException, InterruptedException {
        mHandler.onProvisionPaused().get();
        shadowOf(Looper.getMainLooper()).idle();
        assertDisableLockTaskMode();
    }

    @Test
    public void onProvisionFailed_shouldDisableLockTaskMode()
            throws ExecutionException, InterruptedException {
        mHandler.onProvisionFailed().get();
        shadowOf(Looper.getMainLooper()).idle();
        assertDisableLockTaskMode();
    }

    @Test
    public void onLocked_shouldHaveNonDuplicateLockTaskFeaturesAndPackages()
            throws ExecutionException, InterruptedException {
        setupKioskAllowListWithDuplicates();
        mTelecomManager.setDefaultDialer(DIALER_PACKAGE);
        mHandler.onLocked().get();
        shadowOf(Looper.getMainLooper()).idle();
        assertLockTaskMode(LockTaskModePolicyHandler.DEFAULT_LOCK_TASK_FEATURES_FOR_KIOSK,
                EXPECTED_ALLOWLIST_PACKAGES);
    }

    @Test
    public void onLocked_withDefaultSystemPackages_shouldHaveExpectedLockTaskFeaturesAndPackages()
            throws ExecutionException, InterruptedException {
        final String[] expectedAllowlistPackages =
                new String[]{TEST_PACKAGE, SETTINGS_PACKAGE, DIALER_PACKAGE, IME_PACKAGE,
                        PERMISSION_PACKAGE, CELL_BROADCAST_RECEIVER_PACKAGE,
                        DEVICELOCK_CONTROLLER_PACKAGE};
        Bundle bundle = new Bundle();
        bundle.putString(EXTRA_KIOSK_PACKAGE, TEST_PACKAGE);
        SetupParametersClient.getInstance().createPrefs(bundle).get();
        setupDefaultSystemPackages();
        mHandler.onLocked().get();
        shadowOf(Looper.getMainLooper()).idle();
        assertLockTaskMode(LockTaskModePolicyHandler.DEFAULT_LOCK_TASK_FEATURES_FOR_KIOSK,
                expectedAllowlistPackages);
    }

    @Test
    public void onUnlocked_shouldDisableLockTaskMode()
            throws ExecutionException, InterruptedException {
        mHandler.onUnlocked().get();
        shadowOf(Looper.getMainLooper()).idle();
        assertDisableLockTaskMode();
    }

    @Test
    public void onCleared_shouldDisableLockTaskMode()
            throws ExecutionException, InterruptedException {
        mHandler.onCleared().get();
        shadowOf(Looper.getMainLooper()).idle();
        assertDisableLockTaskMode();
        verify(mMockDpm, times(0)).clearPackagePersistentPreferredActivities(eq(null),
                eq(PACKAGE_OVERRIDING_HOME));
    }

    @Test
    public void onUnlocked_withHomePackageOverride_shouldDisableLockTaskMode()
            throws ExecutionException, InterruptedException {
        UserParameters.setPackageOverridingHome(mContext, PACKAGE_OVERRIDING_HOME);
        mHandler.onProvisionFailed().get();
        shadowOf(Looper.getMainLooper()).idle();
        assertDisableLockTaskMode();

        verify(mMockDpm).clearPackagePersistentPreferredActivities(eq(null),
                eq(PACKAGE_OVERRIDING_HOME));
        Executors.newSingleThreadExecutor().submit(() -> {
            assertThat(UserParameters.getPackageOverridingHome(mContext)).isNull();
        }).get();
    }

    private void setupDefaultSystemPackages() {
        ShadowPackageManager shadowPackageManager = Shadows.shadowOf(mContext.getPackageManager());

        IntentFilter dialerIntent = new IntentFilter(Intent.ACTION_DIAL);
        dialerIntent.addCategory(Intent.CATEGORY_DEFAULT);
        ComponentName dialerComponent = new ComponentName(DIALER_PACKAGE, TEST_ACTIVITY);

        PackageInfo dialerPackage =
                PackageInfoBuilder.newBuilder()
                        .setPackageName(DIALER_PACKAGE)
                        .setApplicationInfo(
                                ApplicationInfoBuilder.newBuilder()
                                        .setName(DIALER_PACKAGE)
                                        .setPackageName(DIALER_PACKAGE)
                                        .build())
                        .build();
        dialerPackage.applicationInfo.flags |= ApplicationInfo.FLAG_SYSTEM;
        shadowPackageManager.installPackage(dialerPackage);
        shadowPackageManager.addActivityIfNotPresent(dialerComponent);
        shadowPackageManager.addIntentFilterForActivity(dialerComponent, dialerIntent);

        IntentFilter settingsIntent = new IntentFilter(Settings.ACTION_SETTINGS);
        settingsIntent.addCategory(Intent.CATEGORY_DEFAULT);
        ComponentName settingsComponent = new ComponentName(SETTINGS_PACKAGE, TEST_ACTIVITY);

        PackageInfo settingsPackage =
                PackageInfoBuilder.newBuilder()
                        .setPackageName(SETTINGS_PACKAGE)
                        .setApplicationInfo(
                                ApplicationInfoBuilder.newBuilder()
                                        .setName(SETTINGS_PACKAGE)
                                        .setPackageName(SETTINGS_PACKAGE)
                                        .build())
                        .build();
        settingsPackage.applicationInfo.flags |= ApplicationInfo.FLAG_SYSTEM;
        shadowPackageManager.installPackage(settingsPackage);
        shadowPackageManager.addActivityIfNotPresent(settingsComponent);
        shadowPackageManager.addIntentFilterForActivity(settingsComponent, settingsIntent);

        IntentFilter requestPermissionIntent = new IntentFilter(
                PackageManager.ACTION_REQUEST_PERMISSIONS);
        requestPermissionIntent.addCategory(Intent.CATEGORY_DEFAULT);
        ComponentName requestPermissionComponent = new ComponentName(PERMISSION_PACKAGE,
                TEST_ACTIVITY);

        PackageInfo requestPermissionPackage =
                PackageInfoBuilder.newBuilder()
                        .setPackageName(PERMISSION_PACKAGE)
                        .setApplicationInfo(
                                ApplicationInfoBuilder.newBuilder()
                                        .setName(PERMISSION_PACKAGE)
                                        .setPackageName(PERMISSION_PACKAGE)
                                        .build())
                        .build();
        requestPermissionPackage.applicationInfo.flags |= ApplicationInfo.FLAG_SYSTEM;
        shadowPackageManager.installPackage(requestPermissionPackage);
        shadowPackageManager.addActivityIfNotPresent(requestPermissionComponent);
        shadowPackageManager.addIntentFilterForActivity(requestPermissionComponent,
                requestPermissionIntent);

        PackageInfo cellBroadcastReceiverPackage =
                PackageInfoBuilder.newBuilder()
                        .setPackageName(CELL_BROADCAST_RECEIVER_PACKAGE)
                        .setApplicationInfo(
                                ApplicationInfoBuilder.newBuilder()
                                        .setName(CELL_BROADCAST_RECEIVER_PACKAGE)
                                        .setPackageName(CELL_BROADCAST_RECEIVER_PACKAGE)
                                        .build())
                        .build();
        cellBroadcastReceiverPackage.applicationInfo.flags |= ApplicationInfo.FLAG_SYSTEM;
        cellBroadcastReceiverPackage.requestedPermissions =
                new String[]{RECEIVE_EMERGENCY_BROADCAST};
        cellBroadcastReceiverPackage.requestedPermissionsFlags =
                new int[]{REQUESTED_PERMISSION_GRANTED};
        shadowPackageManager.installPackage(cellBroadcastReceiverPackage);

        IntentFilter cellBroadcastIntentFilter =
                new IntentFilter(Telephony.Sms.Intents.SMS_CB_RECEIVED_ACTION);
        ComponentName cellBroadcastComponent =
                new ComponentName(CELL_BROADCAST_RECEIVER_PACKAGE, TEST_ACTIVITY);

        shadowPackageManager.addActivityIfNotPresent(cellBroadcastComponent);
        shadowPackageManager.addIntentFilterForActivity(
                cellBroadcastComponent, cellBroadcastIntentFilter);
        Secure.putString(mContext.getContentResolver(), Secure.DEFAULT_INPUT_METHOD, IME_COMPONENT);
    }

    private static void setupKioskAllowListWithDuplicates()
            throws ExecutionException, InterruptedException {
        final String[] allowlistPackagesWithDuplicates =
                {TEST_PACKAGE, TEST_PACKAGE, DIALER_PACKAGE, SETTINGS_PACKAGE, DIALER_PACKAGE};
        Bundle bundle = new Bundle();
        bundle.putString(EXTRA_KIOSK_PACKAGE, TEST_PACKAGE);
        bundle.putStringArrayList(EXTRA_KIOSK_ALLOWLIST,
                new ArrayList<>(List.of(allowlistPackagesWithDuplicates)));
        SetupParametersClient.getInstance().createPrefs(bundle).get();
    }

    private void assertLockTaskMode(int defaultLockTaskFeatures,
            String[] expectedAllowlistPackages) {
        verify(mMockDpm, times(2)).setLockTaskFeatures(eq(null), mAllowedFeatures.capture());
        verify(mMockDpm, times(3)).setLockTaskPackages(eq(null), mAllowedPackages.capture());
        assertThat(mAllowedFeatures.getAllValues()).containsExactlyElementsIn(
                new Integer[]{DevicePolicyManager.LOCK_TASK_FEATURE_NONE,
                        defaultLockTaskFeatures});
        assertThat(mAllowedPackages.getValue()).isEqualTo(expectedAllowlistPackages);
    }

    private void assertDisableLockTaskMode() {
        verify(mMockDpm).setLockTaskFeatures(eq(null), mAllowedFeatures.capture());
        verify(mMockDpm, times(2)).setLockTaskPackages(eq(null), mAllowedPackages.capture());
        assertThat(mAllowedFeatures.getValue()).isEqualTo(
                DevicePolicyManager.LOCK_TASK_FEATURE_NONE);
        List<String[]> allowedPackages = mAllowedPackages.getAllValues();
        assertThat(allowedPackages.get(0)).isEqualTo(new String[]{""});
        assertThat(allowedPackages.get(1)).isEqualTo(new String[0]);
    }
}
