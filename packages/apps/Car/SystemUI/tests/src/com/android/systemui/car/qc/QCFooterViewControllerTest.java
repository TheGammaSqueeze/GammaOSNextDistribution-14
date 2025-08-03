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

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Intent;
import android.os.UserHandle;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.filters.SmallTest;

import com.android.car.ui.utils.CarUxRestrictionsUtil;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.systembar.element.CarSystemBarElementStateController;
import com.android.systemui.car.systembar.element.CarSystemBarElementStatusBarDisableController;
import com.android.systemui.settings.UserTracker;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class QCFooterViewControllerTest extends SysuiTestCase {
    @Mock
    private CarUxRestrictionsUtil mCarUxRestrictionsUtil;
    @Mock
    private UserTracker mUserTracker;
    @Mock
    private CarSystemBarElementStatusBarDisableController mDisableController;
    @Mock
    private CarSystemBarElementStateController mStateController;

    private MockitoSession mMockingSession;
    private QCFooterView mView;
    private QCFooterViewController mController;
    private Intent mIntent;

    @Before
    public void setUp() {
        mMockingSession = mockitoSession()
                .initMocks(this)
                .mockStatic(CarUxRestrictionsUtil.class)
                .strictness(Strictness.WARN)
                .startMocking();

        mContext = spy(mContext);
        mView = spy(new QCFooterView(mContext));
        when(mUserTracker.getUserHandle()).thenReturn(UserHandle.of(1000));
        mIntent = new Intent();
        when(mView.getOnClickIntent()).thenReturn(mIntent);
        mController = new QCFooterViewController(mView, mDisableController, mStateController,
                mContext, mUserTracker);
        mController.init();
        doReturn(mCarUxRestrictionsUtil).when(() -> CarUxRestrictionsUtil.getInstance(any()));
    }

    @After
    public void tearDown() {
        if (mMockingSession != null) {
            mMockingSession.finishMocking();
        }
    }

    @Test
    public void onButtonClicked_launchesIntent() {
        mView.callOnClick();

        verify(mContext).startActivityAsUser(eq(mIntent), any(), any());
    }

    @Test
    public void onAttachedToWindow_enableWhileDriving_doNotRegisterListener() {
        when(mView.isDisableWhileDriving()).thenReturn(false);
        mController.onViewAttached();

        verify(mCarUxRestrictionsUtil, never()).register(any());
    }

    @Test
    public void onAttachedToWindow_disableWhileDriving_registerListener() {
        when(mView.isDisableWhileDriving()).thenReturn(true);
        mController.onViewAttached();

        verify(mCarUxRestrictionsUtil).register(any());
    }

    @Test
    public void onRestrictionsChanged_registeredListener_setQCFooterViewEnabled() {
        when(mView.isDisableWhileDriving()).thenReturn(true);
        ArgumentCaptor<CarUxRestrictionsUtil.OnUxRestrictionsChangedListener> captor =
                ArgumentCaptor.forClass(
                CarUxRestrictionsUtil.OnUxRestrictionsChangedListener.class);
        mController.onViewAttached();
        verify(mCarUxRestrictionsUtil).register(captor.capture());
        CarUxRestrictionsUtil.OnUxRestrictionsChangedListener listener = captor.getValue();
        CarUxRestrictions carUxRestrictions = mock(CarUxRestrictions.class);

        listener.onRestrictionsChanged(carUxRestrictions);

        verify(mView).setEnabled(anyBoolean());
    }

    @Test
    public void onDetachedFromWindow_enableWhileDriving_doNotUnregisterListener() {
        when(mView.isDisableWhileDriving()).thenReturn(false);
        mController.onViewAttached();

        mController.onViewDetached();

        verify(mCarUxRestrictionsUtil, never()).unregister(any());
    }

    @Test
    public void onDetachedFromWindow_disableWhileDriving_unregisterListener() {
        when(mView.isDisableWhileDriving()).thenReturn(true);
        mController.onViewAttached();

        mController.onViewDetached();

        verify(mCarUxRestrictionsUtil).unregister(any());
    }
}
