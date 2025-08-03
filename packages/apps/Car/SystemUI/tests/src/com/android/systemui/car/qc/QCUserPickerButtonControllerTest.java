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

package com.android.systemui.car.qc;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.Car;
import android.car.app.CarActivityManager;
import android.content.pm.UserInfo;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;
import android.os.UserManager;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.widget.ImageView;

import androidx.test.filters.SmallTest;

import com.android.systemui.SysuiTestCase;
import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.statusbar.UserNameViewController;
import com.android.systemui.car.systembar.element.CarSystemBarElementStateController;
import com.android.systemui.car.systembar.element.CarSystemBarElementStatusBarDisableController;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.tests.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class QCUserPickerButtonControllerTest extends SysuiTestCase {
    private final UserInfo mUserInfo =
            new UserInfo(/* id= */ 0, /* name= */ "Test User", /* flags= */ 0);
    @Mock
    private Car mCar;
    @Mock
    private CarActivityManager mCarActivityManager;
    @Mock
    private UserTracker mUserTracker;
    @Mock
    private BroadcastDispatcher mBroadcastDispatcher;
    @Mock
    private UserManager mUserManager;
    @Mock
    private ImageView mUserIconView;
    @Mock
    private CarServiceProvider mCarServiceProvider;
    @Mock
    private CarSystemBarElementStatusBarDisableController mDisableController;
    @Mock
    private CarSystemBarElementStateController mStateController;

    private UserHandle mUserHandle;
    private QCFooterView mView;
    private QCUserPickerButtonController mController;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mContext = spy(mContext);
        mUserHandle = UserHandle.of(1000);
        when(mContext.getSystemService(UserManager.class)).thenReturn(mUserManager);
        when(mUserTracker.getUserHandle()).thenReturn(mUserHandle);
        when(mUserTracker.getUserInfo()).thenReturn(mUserInfo);
        when(mCar.getCarManager(CarActivityManager.class)).thenReturn(mCarActivityManager);

        mView = spy(new QCFooterView(mContext));
        when(mView.findViewById(R.id.user_icon)).thenReturn(mUserIconView);
        mController = new QCUserPickerButtonController(mView, mDisableController,
                mStateController, mContext, mUserTracker, mCarServiceProvider,
                mBroadcastDispatcher);
        mController.init();

        attachCarService();
    }

    @Test
    public void onUserButtonClicked_startUserPickerActivity() {
        int displayId = 100;
        when(mContext.getDisplayId()).thenReturn(displayId);

        mView.callOnClick();

        verify(mCarActivityManager).startUserPickerOnDisplay(eq(displayId));
    }

    @Test
    public void onInit_setUserIconView() {
        verify(mUserIconView).setImageDrawable(any(Drawable.class));
    }

    @Test
    public void onDetachedFromWindow_removeUserNameView() {
        spyOn(mController.mUserNameViewController);
        UserNameViewController controllerSpy = mController.mUserNameViewController;

        mController.onViewDetached();

        verify(controllerSpy).removeUserNameView(eq(mView));
    }

    private void attachCarService() {
        ArgumentCaptor<CarServiceProvider.CarServiceOnConnectedListener> captor =
                ArgumentCaptor.forClass(CarServiceProvider.CarServiceOnConnectedListener.class);
        mController.onViewAttached();
        verify(mCarServiceProvider).addListener(captor.capture());
        assertThat(captor.getValue()).isNotNull();
        captor.getValue().onConnected(mCar);
    }
}
