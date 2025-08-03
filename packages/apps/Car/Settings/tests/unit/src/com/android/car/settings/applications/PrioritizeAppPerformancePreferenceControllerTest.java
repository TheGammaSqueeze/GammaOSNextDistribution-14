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

package com.android.car.settings.applications;

import static android.car.watchdog.PackageKillableState.KILLABLE_STATE_NEVER;
import static android.car.watchdog.PackageKillableState.KILLABLE_STATE_NO;
import static android.car.watchdog.PackageKillableState.KILLABLE_STATE_YES;

import static com.android.car.settings.applications.PrioritizeAppPerformancePreferenceController.TURN_ON_PRIORITIZE_APP_PERFORMANCE_DIALOG_TAG;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.Car;
import android.car.drivingstate.CarUxRestrictions;
import android.car.watchdog.CarWatchdogManager;
import android.car.watchdog.PackageKillableState;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.os.UserHandle;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.SwitchPreference;
import androidx.preference.TwoStatePreference;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.ConfirmationDialogFragment;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.Collections;

@RunWith(AndroidJUnit4.class)
public final class PrioritizeAppPerformancePreferenceControllerTest {
    private static final String TEST_PKG_NAME = "test.package.name";
    private static final int TEST_UID = 10012345;
    private static final int TEST_USER_ID = 100;

    private final Context mContext = ApplicationProvider.getApplicationContext();

    private MockitoSession mMockingSession;
    private LifecycleOwner mLifecycleOwner;
    private CarUxRestrictions mCarUxRestrictions;
    private PrioritizeAppPerformancePreferenceController mController;
    private TwoStatePreference mTwoStatePreference;

    @Captor
    ArgumentCaptor<Car.CarServiceLifecycleListener> mCarLifecycleCaptor;
    @Captor
    ArgumentCaptor<ConfirmationDialogFragment> mDialogFragment;
    @Mock
    private FragmentController mFragmentController;
    @Mock
    private Car mMockCar;
    @Mock
    private CarWatchdogManager mMockManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mMockingSession = mockitoSession()
                .initMocks(this)
                .mockStatic(Car.class)
                .strictness(Strictness.LENIENT)
                .startMocking();
        mLifecycleOwner = new TestLifecycleOwner();

        mTwoStatePreference = new SwitchPreference(mContext);

        CarUxRestrictions restrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();
        mController = new PrioritizeAppPerformancePreferenceController(mContext,
                /* preferenceKey= */ "key", mFragmentController, restrictions);

        PreferenceControllerTestUtil.assignPreference(mController, mTwoStatePreference);

        when(Car.createCar(any(), any(), anyLong(), mCarLifecycleCaptor.capture())).then(
                invocation -> {
                    Car.CarServiceLifecycleListener listener = mCarLifecycleCaptor.getValue();
                    listener.onLifecycleChanged(mMockCar, true);
                    return mMockCar;
                });
        when(mMockCar.getCarManager(Car.CAR_WATCHDOG_SERVICE)).thenReturn(mMockManager);

        PackageInfo packageInfo = new PackageInfo();
        packageInfo.packageName = TEST_PKG_NAME;
        packageInfo.applicationInfo = new ApplicationInfo();
        packageInfo.applicationInfo.uid = TEST_UID;
        packageInfo.applicationInfo.packageName = TEST_PKG_NAME;
        mController.setPackageInfo(packageInfo);
    }

    @After
    public void tearDown() {
        mMockingSession.finishMocking();
    }

    @Test
    public void onCreate_prioritizeAppPerformance_withKillableStateYes() {
        when(mMockManager.getPackageKillableStatesAsUser(any())).thenReturn(
                Collections.singletonList(
                        new PackageKillableState(TEST_PKG_NAME, TEST_USER_ID, KILLABLE_STATE_YES)));

        mController.onCreate(mLifecycleOwner);

        assertThat(mTwoStatePreference.isChecked()).isFalse();
        assertThat(mTwoStatePreference.isEnabled()).isTrue();
    }

    @Test
    public void onCreate_prioritizeAppPerformance_withKillableStateNo() {
        when(mMockManager.getPackageKillableStatesAsUser(any())).thenReturn(
                Collections.singletonList(
                        new PackageKillableState(TEST_PKG_NAME, TEST_USER_ID, KILLABLE_STATE_NO)));

        mController.onCreate(mLifecycleOwner);

        assertThat(mTwoStatePreference.isChecked()).isTrue();
        assertThat(mTwoStatePreference.isEnabled()).isTrue();
    }

    @Test
    public void onCreate_prioritizeAppPerformance_withKillableStateNever() {
        when(mMockManager.getPackageKillableStatesAsUser(any())).thenReturn(
                Collections.singletonList(new PackageKillableState(
                        TEST_PKG_NAME, TEST_USER_ID, KILLABLE_STATE_NEVER)));

        mController.onCreate(mLifecycleOwner);

        assertThat(mTwoStatePreference.isChecked()).isFalse();
        assertThat(mTwoStatePreference.isEnabled()).isFalse();
    }

    @Test
    public void callChangeListener_turnOffPrioritizeAppPerformance() {
        when(mMockManager.getPackageKillableStatesAsUser(any())).thenReturn(
                Collections.singletonList(
                        new PackageKillableState(TEST_PKG_NAME, TEST_USER_ID, KILLABLE_STATE_NO)));
        mController.onCreate(mLifecycleOwner);
        assertThat(mTwoStatePreference.isChecked()).isTrue();

        mTwoStatePreference.performClick();

        verify(mMockManager).setKillablePackageAsUser(
                TEST_PKG_NAME, UserHandle.getUserHandleForUid(TEST_UID), true);
        assertThat(mTwoStatePreference.isChecked()).isFalse();
        assertThat(mTwoStatePreference.isEnabled()).isTrue();
    }

    @Test
    public void callChangeListener_turnOnPrioritizeAppPerformance() {
        when(mMockManager.getPackageKillableStatesAsUser(any())).thenReturn(
                Collections.singletonList(
                        new PackageKillableState(TEST_PKG_NAME, TEST_USER_ID, KILLABLE_STATE_YES)));
        mController.onCreate(mLifecycleOwner);
        assertThat(mTwoStatePreference.isChecked()).isFalse();

        mTwoStatePreference.callChangeListener(true);

        verify(mFragmentController).showDialog(
                mDialogFragment.capture(), eq(TURN_ON_PRIORITIZE_APP_PERFORMANCE_DIALOG_TAG));

        mDialogFragment.getValue().getConfirmListener().onConfirm(new Bundle());

        verify(mMockManager).setKillablePackageAsUser(
                TEST_PKG_NAME, UserHandle.getUserHandleForUid(TEST_UID), false);
        assertThat(mTwoStatePreference.isChecked()).isTrue();
        assertThat(mTwoStatePreference.isEnabled()).isTrue();
    }
}
