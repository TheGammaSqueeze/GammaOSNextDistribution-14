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

package com.android.car.am;

import static android.view.Display.DEFAULT_DISPLAY;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.Car;
import android.car.app.CarActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;

import com.android.car.CarLocalServices;
import com.android.car.CarServiceHelperWrapper;
import com.android.car.CarServiceHelperWrapperUnitTest;
import com.android.car.internal.ICarServiceHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public class CarActivityServiceUnitTest {

    // Comes from android.window.DisplayAreaOrganizer.FEATURE_DEFAULT_TASK_CONTAINER
    private static final int FEATURE_DEFAULT_TASK_CONTAINER = 1;

    private CarActivityService mCarActivityService;

    private final ComponentName mTestActivity = new ComponentName("test.pkg", "test.activity");

    @Rule
    public TestName mTestName = new TestName();
    @Mock
    private Context mContext;
    @Mock
    private ICarServiceHelper mICarServiceHelper;

    @Before
    public void setUp() {
        mCarActivityService = spy(new CarActivityService(mContext));

        int nonCurrentUserId = 9999990;
        boolean isNonCurrentUserTest = mTestName.getMethodName().contains("NonCurrentUser");
        int callerId = isNonCurrentUserTest ? nonCurrentUserId : UserHandle.USER_SYSTEM;
        when(mCarActivityService.getCaller()).thenReturn(callerId);

        CarServiceHelperWrapper wrapper = CarServiceHelperWrapper.create();
        wrapper.setCarServiceHelper(mICarServiceHelper);
    }

    @After
    public void tearDown() {
        CarLocalServices.removeServiceForTest(CarServiceHelperWrapper.class);
    }

    @Test
    public void setPersistentActivityThrowsException_ifICarServiceHelperIsNotSet() {
        // Remove already create one and reset to not set state.
        CarServiceHelperWrapperUnitTest.createWithImmediateTimeout();

        assertThrows(IllegalStateException.class,
                () -> mCarActivityService.setPersistentActivity(
                        mTestActivity, DEFAULT_DISPLAY, FEATURE_DEFAULT_TASK_CONTAINER));
    }

    @Test
    public void setPersistentActivityThrowsException_withoutPermission() {
        when(mContext.checkCallingOrSelfPermission(eq(Car.PERMISSION_CONTROL_CAR_APP_LAUNCH)))
                .thenReturn(PackageManager.PERMISSION_DENIED);

        assertThrows(SecurityException.class,
                () -> mCarActivityService.setPersistentActivity(
                        mTestActivity, DEFAULT_DISPLAY, FEATURE_DEFAULT_TASK_CONTAINER));
    }

    @Test
    public void setPersistentActivityInvokesICarServiceHelper() throws RemoteException {
        int displayId = 9;
        int ret = mCarActivityService.setPersistentActivity(
                mTestActivity, displayId, FEATURE_DEFAULT_TASK_CONTAINER);
        assertThat(ret).isEqualTo(CarActivityManager.RESULT_SUCCESS);

        ArgumentCaptor<ComponentName> activityCaptor = ArgumentCaptor.forClass(ComponentName.class);
        ArgumentCaptor<Integer> displayIdCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> featureIdCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mICarServiceHelper).setPersistentActivity(
                activityCaptor.capture(), displayIdCaptor.capture(), featureIdCaptor.capture());

        assertThat(activityCaptor.getValue()).isEqualTo(mTestActivity);
        assertThat(displayIdCaptor.getValue()).isEqualTo(displayId);
        assertThat(featureIdCaptor.getValue()).isEqualTo(FEATURE_DEFAULT_TASK_CONTAINER);
    }

    @Test
    public void setPersistentActivitiesOnRootTaskThrowsException_withoutPermission() {
        when(mContext.checkCallingOrSelfPermission(eq(Car.PERMISSION_CONTROL_CAR_APP_LAUNCH)))
                .thenReturn(PackageManager.PERMISSION_DENIED);

        assertThrows(SecurityException.class,
                () -> mCarActivityService.setPersistentActivitiesOnRootTask(
                        List.of(mTestActivity), new Binder()));
    }

    @Test
    public void setPersistentActivitiesOnRootTaskInvokesICarServiceHelper() throws RemoteException {
        IBinder tempToken = new Binder();
        mCarActivityService.setPersistentActivitiesOnRootTask(List.of(mTestActivity), tempToken);

        ArgumentCaptor<List<ComponentName>> activityCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Binder> rootTaskTokenCaptor = ArgumentCaptor.forClass(Binder.class);
        verify(mICarServiceHelper).setPersistentActivitiesOnRootTask(
                activityCaptor.capture(), rootTaskTokenCaptor.capture());
        assertThat(activityCaptor.getValue()).isEqualTo(List.of(mTestActivity));
        assertThat(rootTaskTokenCaptor.getValue()).isEqualTo(tempToken);
    }

    @Test
    public void setPersistentActivityReturnsErrorForNonCurrentUser() throws RemoteException {
        int ret = mCarActivityService.setPersistentActivity(
                mTestActivity, DEFAULT_DISPLAY, FEATURE_DEFAULT_TASK_CONTAINER);
        assertThat(ret).isEqualTo(CarActivityManager.RESULT_INVALID_USER);
    }
}
