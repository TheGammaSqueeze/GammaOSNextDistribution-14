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

package com.android.car.settings.bluetooth;

import static com.android.car.settings.bluetooth.BluetoothRequestPermissionActivity.DEFAULT_DISCOVERABLE_TIMEOUT;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.widget.Button;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.settingslib.bluetooth.LocalBluetoothAdapter;
import com.android.settingslib.bluetooth.LocalBluetoothManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

import java.util.Collections;

@RunWith(AndroidJUnit4.class)
public class BluetoothRequestPermissionActivityTest {
    private static final String PACKAGE_NAME = "pkg.test";

    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private TestBluetoothRequestPermissionActivity mActivity;
    private MockitoSession mSession;

    @Rule
    public ActivityTestRule<TestBluetoothRequestPermissionActivity> mActivityTestRule =
            new ActivityTestRule<>(TestBluetoothRequestPermissionActivity.class, false, false);
    @Mock
    private LocalBluetoothManager mMockLocalBluetoothManager;
    @Mock
    private LocalBluetoothAdapter mMockLocalBluetoothAdapter;
    @Mock
    private PackageManager mMockPackageManager;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        // Fields that need to be mocked in activity's onCreate(). Workaround since ActivityTestRule
        // doesn't create the activity instance or context until launched, so the fields can't be
        // mocked normally.
        TestBluetoothRequestPermissionActivity.PACKAGE_MANAGER = mMockPackageManager;
        TestBluetoothRequestPermissionActivity.CALLING_PACKAGE = PACKAGE_NAME;

        when(mMockPackageManager.getApplicationInfo(PACKAGE_NAME, 0))
                .thenReturn(new ApplicationInfo());

        mSession = ExtendedMockito.mockitoSession().mockStatic(
                LocalBluetoothManager.class, withSettings().lenient()).startMocking();
        ExtendedMockito.when(LocalBluetoothManager.getInstance(any(), any()))
                .thenReturn(mMockLocalBluetoothManager);
        when(mMockLocalBluetoothManager.getBluetoothAdapter())
                .thenReturn(mMockLocalBluetoothAdapter);
        when(mMockLocalBluetoothAdapter.setScanMode(
                BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE, DEFAULT_DISCOVERABLE_TIMEOUT))
                .thenReturn(false);
    }

    @After
    public void tearDown() {
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    @Test
    public void onCreate_requestDisableIntent_hasDisableRequestType() {
        Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISABLE);
        mActivityTestRule.launchActivity(intent);
        mActivity = mActivityTestRule.getActivity();

        assertThat(mActivity.getRequestType()).isEqualTo(
                BluetoothRequestPermissionActivity.REQUEST_DISABLE);
    }

    @Test
    public void onCreate_requestDiscoverableIntent_hasDiscoverableRequestType() {
        Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        mActivityTestRule.launchActivity(intent);
        mActivity = mActivityTestRule.getActivity();

        assertThat(mActivity.getRequestType()).isEqualTo(
                BluetoothRequestPermissionActivity.REQUEST_ENABLE_DISCOVERABLE);
    }

    @Test
    public void onCreate_requestDiscoverableIntent_noTimeoutSpecified_hasDefaultTimeout() {
        Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        mActivityTestRule.launchActivity(intent);
        mActivity = mActivityTestRule.getActivity();

        assertThat(mActivity.getTimeout()).isEqualTo(
                DEFAULT_DISCOVERABLE_TIMEOUT);
    }

    @Test
    public void onCreate_requestDiscoverableIntent_timeoutSpecified_hasTimeout() {
        Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        intent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,
                BluetoothRequestPermissionActivity.MAX_DISCOVERABLE_TIMEOUT);
        mActivityTestRule.launchActivity(intent);
        mActivity = mActivityTestRule.getActivity();

        assertThat(mActivity.getTimeout()).isEqualTo(
                BluetoothRequestPermissionActivity.MAX_DISCOVERABLE_TIMEOUT);
    }

    @Test
    public void onCreate_requestDiscoverableIntent_bypassforSetup_startsDiscoverableScan() {
        when(mMockLocalBluetoothAdapter.getState()).thenReturn(BluetoothAdapter.STATE_ON);
        Intent intent = createSetupWizardIntent();
        mActivityTestRule.launchActivity(intent);

        verify(mMockLocalBluetoothAdapter).setScanMode(
                BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE, DEFAULT_DISCOVERABLE_TIMEOUT);
    }

    @Test
    public void onCreate_requestDiscoverableIntent_bypassforSetup_turningOn_noDialog() {
        when(mMockLocalBluetoothAdapter.getState()).thenReturn(BluetoothAdapter.STATE_TURNING_ON);
        Intent intent = createSetupWizardIntent();
        mActivityTestRule.launchActivity(intent);
        mActivity = mActivityTestRule.getActivity();

        assertThat(mActivity.getCurrentDialog()).isNull();
    }

    @Test
    public void onCreate_requestDiscoverableIntent_bypassforSetup_turningOn_receiverRegistered() {
        when(mMockLocalBluetoothAdapter.getState()).thenReturn(BluetoothAdapter.STATE_TURNING_ON);
        Intent intent = createSetupWizardIntent();
        mActivityTestRule.launchActivity(intent);
        mActivity = mActivityTestRule.getActivity();

        assertThat(mActivity.getCurrentReceiver()).isNotNull();
    }

    @Test
    public void onCreate_requestDiscoverableIntent_bypassforSetup_turningOn_enableDiscovery() {
        when(mMockLocalBluetoothAdapter.getState()).thenReturn(BluetoothAdapter.STATE_TURNING_ON);

        Intent intent = createSetupWizardIntent();
        mActivityTestRule.launchActivity(intent);
        mActivity = mActivityTestRule.getActivity();

        // Simulate bluetooth callback from STATE_TURNING_ON to STATE_ON
        Intent stateChangedIntent = new Intent();
        stateChangedIntent.putExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_ON);
        mActivity.getCurrentReceiver().onReceive(mContext, stateChangedIntent);

        verify(mMockLocalBluetoothAdapter).setScanMode(
                BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE, DEFAULT_DISCOVERABLE_TIMEOUT);
    }

    @Test
    public void onCreate_requestDiscoverableIntent_bypassforGeneric_noScanModeChange() {
        when(mMockLocalBluetoothAdapter.getState()).thenReturn(BluetoothAdapter.STATE_ON);
        Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        intent.putExtra(BluetoothRequestPermissionActivity.EXTRA_BYPASS_CONFIRM_DIALOG, true);
        mActivityTestRule.launchActivity(intent);
        mActivity = mActivityTestRule.getActivity();

        verify(mMockLocalBluetoothAdapter, never()).setScanMode(
                BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE, DEFAULT_DISCOVERABLE_TIMEOUT);
    }

    @Test
    public void onCreate_requestEnableIntent_hasEnableRequestType() {
        Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        mActivityTestRule.launchActivity(intent);
        mActivity = mActivityTestRule.getActivity();

        assertThat(mActivity.getRequestType()).isEqualTo(
                BluetoothRequestPermissionActivity.REQUEST_ENABLE);
    }

    @Test
    public void onCreate_bluetoothOff_requestDisableIntent_noDialog() {
        when(mMockLocalBluetoothAdapter.getState()).thenReturn(BluetoothAdapter.STATE_OFF);
        Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISABLE);
        mActivityTestRule.launchActivity(intent);
        mActivity = mActivityTestRule.getActivity();

        assertThat(mActivity.getCurrentDialog()).isNull();
    }

    @Test
    public void onCreate_bluetoothOn_requestDisableIntent_startsDialog() {
        when(mMockLocalBluetoothAdapter.getState()).thenReturn(BluetoothAdapter.STATE_ON);
        Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISABLE);
        mActivityTestRule.launchActivity(intent);
        mActivity = mActivityTestRule.getActivity();

        assertThat(mActivity.getCurrentDialog()).isNotNull();
        assertThat(mActivity.getCurrentDialog().isShowing()).isTrue();
    }

    @Test
    public void onCreate_bluetoothOff_requestDiscoverableIntent_startsDialog() {
        when(mMockLocalBluetoothAdapter.getState()).thenReturn(BluetoothAdapter.STATE_OFF);
        Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        mActivityTestRule.launchActivity(intent);
        mActivity = mActivityTestRule.getActivity();

        assertThat(mActivity.getCurrentDialog()).isNotNull();
        assertThat(mActivity.getCurrentDialog().isShowing()).isTrue();
    }

    @Test
    public void onCreate_bluetoothOn_requestDiscoverableIntent_startsDialog() {
        when(mMockLocalBluetoothAdapter.getState()).thenReturn(BluetoothAdapter.STATE_ON);
        Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        mActivityTestRule.launchActivity(intent);
        mActivity = mActivityTestRule.getActivity();

        assertThat(mActivity.getCurrentDialog()).isNotNull();
        assertThat(mActivity.getCurrentDialog().isShowing()).isTrue();
    }

    @Test
    public void onCreate_bluetoothOff_requestEnableIntent_startsDialog() {
        when(mMockLocalBluetoothAdapter.getState()).thenReturn(BluetoothAdapter.STATE_OFF);
        Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        mActivityTestRule.launchActivity(intent);
        mActivity = mActivityTestRule.getActivity();

        assertThat(mActivity.getCurrentDialog()).isNotNull();
        assertThat(mActivity.getCurrentDialog().isShowing()).isTrue();
    }

    @Test
    public void onCreate_bluetoothOn_requestEnableIntent_noDialog() {
        when(mMockLocalBluetoothAdapter.getState()).thenReturn(BluetoothAdapter.STATE_ON);
        Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        mActivityTestRule.launchActivity(intent);
        mActivity = mActivityTestRule.getActivity();

        assertThat(mActivity.getCurrentDialog()).isNull();
    }

    @Test
    public void onPositiveClick_disableDialog_disables() throws Throwable {
        when(mMockLocalBluetoothAdapter.getState()).thenReturn(BluetoothAdapter.STATE_ON);
        Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISABLE);
        mActivityTestRule.launchActivity(intent);
        mActivity = mActivityTestRule.getActivity();

        Button button = mActivity.getCurrentDialog().getButton(DialogInterface.BUTTON_POSITIVE);
        mActivityTestRule.runOnUiThread(button::performClick);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        verify(mMockLocalBluetoothAdapter).disable();
    }

    @Test
    public void onPositiveClick_discoverableDialog_scanModeSet() throws Throwable {
        when(mMockLocalBluetoothAdapter.getState()).thenReturn(BluetoothAdapter.STATE_ON);
        Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        mActivityTestRule.launchActivity(intent);
        mActivity = mActivityTestRule.getActivity();

        Button button = mActivity.getCurrentDialog().getButton(DialogInterface.BUTTON_POSITIVE);
        mActivityTestRule.runOnUiThread(button::performClick);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        verify(mMockLocalBluetoothAdapter).setScanMode(
                BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE, DEFAULT_DISCOVERABLE_TIMEOUT);
    }

    @Test
    public void onPositiveClick_enableDialog_enables() throws Throwable {
        when(mMockLocalBluetoothAdapter.getState()).thenReturn(BluetoothAdapter.STATE_OFF);
        Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        mActivityTestRule.launchActivity(intent);
        mActivity = mActivityTestRule.getActivity();

        Button button = mActivity.getCurrentDialog().getButton(DialogInterface.BUTTON_POSITIVE);
        mActivityTestRule.runOnUiThread(button::performClick);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        verify(mMockLocalBluetoothAdapter).enable();
    }

    private Intent createSetupWizardIntent() {
        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.activityInfo = new ActivityInfo();
        resolveInfo.activityInfo.packageName = PACKAGE_NAME;

        when(mMockPackageManager.queryIntentActivities(any(), anyInt()))
                .thenReturn(Collections.singletonList(resolveInfo));

        Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        intent.setComponent(new ComponentName(mContext,
                TestBluetoothRequestPermissionActivity.class));
        intent.putExtra(BluetoothRequestPermissionActivity.EXTRA_BYPASS_CONFIRM_DIALOG, true);
        return intent;
    }
}
