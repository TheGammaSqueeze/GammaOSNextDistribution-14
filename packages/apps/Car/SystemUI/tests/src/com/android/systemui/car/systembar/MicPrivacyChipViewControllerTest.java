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

package com.android.systemui.car.systembar;

import static android.hardware.SensorPrivacyManager.Sensors.MICROPHONE;
import static android.hardware.SensorPrivacyManager.Sources.QS_TILE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.Car;
import android.hardware.SensorPrivacyManager;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import androidx.test.filters.SmallTest;

import com.android.systemui.R;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.privacy.MicPrivacyChip;
import com.android.systemui.privacy.PrivacyItem;
import com.android.systemui.privacy.PrivacyItemController;
import com.android.systemui.privacy.PrivacyType;
import com.android.systemui.settings.UserTracker;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.concurrent.Executor;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class MicPrivacyChipViewControllerTest extends SysuiTestCase {

    private MicPrivacyChipViewController mMicPrivacyChipViewController;
    private FrameLayout mFrameLayout;
    private MicPrivacyChip mMicPrivacyChip;

    @Captor
    private ArgumentCaptor<Runnable> mRunnableArgumentCaptor;
    @Captor
    private ArgumentCaptor<PrivacyItemController.Callback> mPicCallbackArgumentCaptor;
    @Captor
    private ArgumentCaptor<SensorPrivacyManager.OnSensorPrivacyChangedListener>
            mOnSensorPrivacyChangedListenerArgumentCaptor;

    @Mock
    private PrivacyItemController mPrivacyItemController;
    @Mock
    private PrivacyItem mPrivacyItem;
    @Mock
    private Executor mExecutor;
    @Mock
    private SensorPrivacyManager mSensorPrivacyManager;
    @Mock
    private UserTracker mUserTracker;
    @Mock
    private Car mCar;
    @Mock
    private Runnable mQsTileNotifyUpdateRunnable;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(/* testClass= */ this);

        mFrameLayout = new FrameLayout(mContext);
        mMicPrivacyChip = spy((MicPrivacyChip) LayoutInflater.from(mContext)
                .inflate(R.layout.mic_privacy_chip, /* root= */ null));
        mFrameLayout.addView(mMicPrivacyChip);
        mContext = spy(mContext);

        when(mContext.getMainExecutor()).thenReturn(mExecutor);
        when(mCar.isConnected()).thenReturn(true);

        mMicPrivacyChipViewController = new MicPrivacyChipViewController(mContext,
                mPrivacyItemController, mSensorPrivacyManager, mUserTracker);
    }

    @Test
    public void addPrivacyChipView_privacyChipViewPresent_addCallbackCalled() {
        mMicPrivacyChipViewController.addPrivacyChipView(mFrameLayout);

        verify(mPrivacyItemController).addCallback(any());
        verify(mUserTracker).addCallback(any(), any());
    }

    @Test
    public void addPrivacyChipView_privacyChipViewPresent_micStatusSet() {
        when(mSensorPrivacyManager.isSensorPrivacyEnabled(anyInt(), eq(MICROPHONE)))
                .thenReturn(false);
        mMicPrivacyChipViewController.addPrivacyChipView(mFrameLayout);
        verify(mExecutor).execute(mRunnableArgumentCaptor.capture());

        mRunnableArgumentCaptor.getValue().run();

        verify(mMicPrivacyChip).setSensorEnabled(eq(true));
    }

    @Test
    public void addPrivacyChipView_privacyChipViewNotPresent_addCallbackNotCalled() {
        mMicPrivacyChipViewController.addPrivacyChipView(new View(getContext()));

        verify(mPrivacyItemController, never()).addCallback(any());
        verify(mUserTracker, never()).addCallback(any(), any());
    }

    @Test
    public void onUserChanged_micStatusSet() {
        when(mSensorPrivacyManager.isSensorPrivacyEnabled(anyInt(), eq(MICROPHONE)))
                .thenReturn(false);
        mMicPrivacyChipViewController.addPrivacyChipView(mFrameLayout);
        ArgumentCaptor<UserTracker.Callback> captor = ArgumentCaptor.forClass(
                UserTracker.Callback.class);
        verify(mUserTracker).addCallback(captor.capture(), any());

        captor.getValue().onUserChanged(mContext.getUserId(), mContext);
        verify(mMicPrivacyChip).setSensorEnabled(eq(true));
    }

    @Test
    public void onPrivacyItemsChanged_micIsPartOfPrivacyItems_animateInCalled() {
        when(mPrivacyItem.getPrivacyType()).thenReturn(PrivacyType.TYPE_MICROPHONE);
        mMicPrivacyChipViewController.addPrivacyChipView(mFrameLayout);
        verify(mPrivacyItemController).addCallback(mPicCallbackArgumentCaptor.capture());
        mPicCallbackArgumentCaptor.getValue().onFlagAllChanged(true);
        mPicCallbackArgumentCaptor.getValue().onFlagMicCameraChanged(true);

        mPicCallbackArgumentCaptor.getValue()
                .onPrivacyItemsChanged(Collections.singletonList(mPrivacyItem));
        verify(mExecutor, times(2)).execute(mRunnableArgumentCaptor.capture());
        mRunnableArgumentCaptor.getAllValues().forEach(Runnable::run);

        verify(mMicPrivacyChip).animateIn();
    }

    @Test
    public void onPrivacyItemsChanged_micIsPartOfPrivacyItemsTwice_animateInCalledOnce() {
        when(mPrivacyItem.getPrivacyType()).thenReturn(PrivacyType.TYPE_MICROPHONE);
        mMicPrivacyChipViewController.addPrivacyChipView(mFrameLayout);
        verify(mPrivacyItemController).addCallback(mPicCallbackArgumentCaptor.capture());
        mPicCallbackArgumentCaptor.getValue().onFlagAllChanged(true);
        mPicCallbackArgumentCaptor.getValue().onFlagMicCameraChanged(true);

        mPicCallbackArgumentCaptor.getValue()
                .onPrivacyItemsChanged(Collections.singletonList(mPrivacyItem));
        mPicCallbackArgumentCaptor.getValue()
                .onPrivacyItemsChanged(Collections.singletonList(mPrivacyItem));
        verify(mExecutor, times(2)).execute(mRunnableArgumentCaptor.capture());
        mRunnableArgumentCaptor.getAllValues().forEach(Runnable::run);

        verify(mMicPrivacyChip).animateIn();
    }

    @Test
    public void onPrivacyItemsChanged_micIsNotPartOfPrivacyItems_animateOutCalled() {
        when(mPrivacyItem.getPrivacyType()).thenReturn(PrivacyType.TYPE_MICROPHONE);
        mMicPrivacyChipViewController.addPrivacyChipView(mFrameLayout);
        verify(mPrivacyItemController).addCallback(mPicCallbackArgumentCaptor.capture());
        mPicCallbackArgumentCaptor.getValue().onFlagAllChanged(true);
        mPicCallbackArgumentCaptor.getValue().onFlagMicCameraChanged(true);
        mPicCallbackArgumentCaptor.getValue()
                .onPrivacyItemsChanged(Collections.singletonList(mPrivacyItem));

        mPicCallbackArgumentCaptor.getValue().onPrivacyItemsChanged(Collections.emptyList());
        verify(mExecutor, times(3))
                .execute(mRunnableArgumentCaptor.capture());
        mRunnableArgumentCaptor.getAllValues().forEach(Runnable::run);

        verify(mMicPrivacyChip).animateOut();
    }

    @Test
    public void onPrivacyItemsChanged_micIsNotPartOfPrivacyItemsTwice_animateOutCalledOnce() {
        when(mPrivacyItem.getPrivacyType()).thenReturn(PrivacyType.TYPE_MICROPHONE);
        mMicPrivacyChipViewController.addPrivacyChipView(mFrameLayout);
        verify(mPrivacyItemController).addCallback(mPicCallbackArgumentCaptor.capture());
        mPicCallbackArgumentCaptor.getValue().onFlagAllChanged(true);
        mPicCallbackArgumentCaptor.getValue().onFlagMicCameraChanged(true);
        mPicCallbackArgumentCaptor.getValue()
                .onPrivacyItemsChanged(Collections.singletonList(mPrivacyItem));

        mPicCallbackArgumentCaptor.getValue().onPrivacyItemsChanged(Collections.emptyList());
        mPicCallbackArgumentCaptor.getValue().onPrivacyItemsChanged(Collections.emptyList());
        verify(mExecutor, times(3))
                .execute(mRunnableArgumentCaptor.capture());
        mRunnableArgumentCaptor.getAllValues().forEach(Runnable::run);

        verify(mMicPrivacyChip).animateOut();
    }

    @Test
    public void onPrivacyItemsChanged_qsTileNotifyUpdateRunnableExecuted() {
        when(mPrivacyItem.getPrivacyType()).thenReturn(PrivacyType.TYPE_MICROPHONE);
        mMicPrivacyChipViewController.setNotifyUpdateRunnable(mQsTileNotifyUpdateRunnable);
        mMicPrivacyChipViewController.addPrivacyChipView(mFrameLayout);
        verify(mPrivacyItemController).addCallback(mPicCallbackArgumentCaptor.capture());
        mPicCallbackArgumentCaptor.getValue().onFlagAllChanged(true);
        mPicCallbackArgumentCaptor.getValue().onFlagMicCameraChanged(true);

        mPicCallbackArgumentCaptor.getValue().onPrivacyItemsChanged(Collections.emptyList());
        verify(mExecutor).execute(mRunnableArgumentCaptor.capture());
        mRunnableArgumentCaptor.getAllValues().forEach(Runnable::run);

        verify(mQsTileNotifyUpdateRunnable).run();
    }

    @Test
    public void onSensorPrivacyChanged_argTrue_setSensorEnabledWithFalseCalled() {
        mMicPrivacyChipViewController.addPrivacyChipView(mFrameLayout);
        verify(mSensorPrivacyManager).addSensorPrivacyListener(eq(MICROPHONE),
                mOnSensorPrivacyChangedListenerArgumentCaptor.capture());
        reset(mMicPrivacyChip);
        reset(mExecutor);
        mOnSensorPrivacyChangedListenerArgumentCaptor.getValue()
                .onSensorPrivacyChanged(MICROPHONE, /* enabled= */ true);
        verify(mExecutor).execute(mRunnableArgumentCaptor.capture());

        mRunnableArgumentCaptor.getAllValues().forEach(Runnable::run);

        verify(mMicPrivacyChip).setSensorEnabled(eq(false));
    }

    @Test
    public void onSensorPrivacyChanged_argFalse_setSensorEnabledWithTrueCalled() {
        mMicPrivacyChipViewController.addPrivacyChipView(mFrameLayout);
        verify(mSensorPrivacyManager).addSensorPrivacyListener(eq(MICROPHONE),
                mOnSensorPrivacyChangedListenerArgumentCaptor.capture());
        reset(mMicPrivacyChip);
        reset(mExecutor);
        mOnSensorPrivacyChangedListenerArgumentCaptor.getValue()
                .onSensorPrivacyChanged(MICROPHONE, /* enabled= */ false);
        verify(mExecutor).execute(mRunnableArgumentCaptor.capture());

        mRunnableArgumentCaptor.getAllValues().forEach(Runnable::run);

        verify(mMicPrivacyChip).setSensorEnabled(eq(true));
    }

    @Test
    public void onSensorPrivacyChanged_qsTileNotifyUpdateRunnableExecuted() {
        mMicPrivacyChipViewController.setNotifyUpdateRunnable(mQsTileNotifyUpdateRunnable);
        mMicPrivacyChipViewController.addPrivacyChipView(mFrameLayout);
        verify(mSensorPrivacyManager).addSensorPrivacyListener(eq(MICROPHONE),
                mOnSensorPrivacyChangedListenerArgumentCaptor.capture());
        reset(mMicPrivacyChip);
        reset(mExecutor);
        mOnSensorPrivacyChangedListenerArgumentCaptor.getValue()
                .onSensorPrivacyChanged(MICROPHONE, /* enabled= */ true);
        verify(mExecutor).execute(mRunnableArgumentCaptor.capture());

        mRunnableArgumentCaptor.getAllValues().forEach(Runnable::run);

        verify(mQsTileNotifyUpdateRunnable).run();
    }

    @Test
    public void isSensorEnabled_sensorPrivacyEnabled_returnFalse() {
        when(mSensorPrivacyManager.isSensorPrivacyEnabled(anyInt(), eq(MICROPHONE)))
                .thenReturn(true);

        assertThat(mMicPrivacyChipViewController.isSensorEnabled()).isFalse();
    }

    @Test
    public void isSensorEnabled_sensorPrivacyDisabled_returnTrue() {
        when(mSensorPrivacyManager.isSensorPrivacyEnabled(anyInt(), eq(MICROPHONE)))
                .thenReturn(false);

        assertThat(mMicPrivacyChipViewController.isSensorEnabled()).isTrue();
    }

    @Test
    public void toggleSensor_micTurnedOn_sensorPrivacyEnabled() {
        when(mSensorPrivacyManager.isSensorPrivacyEnabled(anyInt(), eq(MICROPHONE)))
                .thenReturn(false);

        mMicPrivacyChipViewController.toggleSensor();

        verify(mSensorPrivacyManager).setSensorPrivacy(eq(QS_TILE), eq(MICROPHONE), eq(true));
    }

    @Test
    public void toggleSensor_micTurnedOff_sensorPrivacyDisabled() {
        when(mSensorPrivacyManager.isSensorPrivacyEnabled(anyInt(), eq(MICROPHONE)))
                .thenReturn(true);

        mMicPrivacyChipViewController.toggleSensor();

        verify(mSensorPrivacyManager).setSensorPrivacy(eq(QS_TILE), eq(MICROPHONE), eq(false));
    }
}
