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

package com.android.systemui.car.systembar;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.TypedArray;
import android.hardware.input.InputManager;
import android.testing.AndroidTestingRunner;
import android.testing.TestableResources;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.View;

import androidx.test.filters.SmallTest;

import com.android.systemui.R;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.shared.system.TaskStackChangeListener;
import com.android.systemui.statusbar.AlphaOptimizedImageView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.function.Consumer;
import java.util.function.Function;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@SmallTest
public class RecentsButtonStateProviderTest extends SysuiTestCase {
    private static final String RECENTS_ACTIVITY_NAME =
            "com.android.car.carlauncher/.recents.CarRecentsActivity";
    private static final String DIALER_ACTIVITY_NAME = "com.android.car.dialer/.ui.TelecomActivity";
    private static final float SELECTED_ALPHA = 1f;

    private RecentsButtonStateProvider mRecentsButtonStateProvider;
    private TaskStackChangeListener mTaskStackChangeListener;
    private TestableResources mTestableResources;

    @Mock
    private CarSystemBarButton mCarSystemBarButton;
    @Mock
    private InputManager mInputManager;
    @Mock
    private ActivityManager.RunningTaskInfo mRecentsRunningTaskInfo;
    @Mock
    private ActivityManager.RunningTaskInfo mDialerRunningTaskInfo;
    @Mock
    private ActivityManager.RunningTaskInfo mNoTopComponentRunningTaskInfo;
    @Mock
    private Intent mDialerBaseIntent;
    @Mock
    private Intent mBaseIntentWithNoComponent;
    @Mock
    private AlphaOptimizedImageView mAlphaOptimizedImageView;
    @Mock
    private TypedArray mTypedArray;
    @Mock
    private Consumer<TypedArray> mTypedArrayConsumer;
    @Mock
    private Intent mIntent;
    @Mock
    private Function<Intent, View.OnClickListener> mIntentAndOnClickListenerFunction;
    @Mock
    private Consumer<AlphaOptimizedImageView> mAlphaOptimizedImageViewConsumer;
    @Mock
    private View.OnClickListener mOnClickListener;
    @Captor
    ArgumentCaptor<View.OnLongClickListener> mOnLongClickListenerCaptor;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mTestableResources = mContext.getOrCreateTestableResources();
        mTestableResources.addOverride(R.bool.config_enableRecentsEntryPoint, true);
        mContext = spy(mContext);
        when(mInputManager.injectInputEvent(any(InputEvent.class), anyInt())).thenReturn(true);
        doReturn(mInputManager).when(mContext).getSystemService(InputManager.class);
        mTestableResources.addOverride(com.android.internal.R.string.config_recentsComponentName,
                /* value= */ RECENTS_ACTIVITY_NAME);
        mRecentsRunningTaskInfo.topActivity = ComponentName.unflattenFromString(
                RECENTS_ACTIVITY_NAME);
        mDialerRunningTaskInfo.topActivity = ComponentName.unflattenFromString(
                DIALER_ACTIVITY_NAME);
        mNoTopComponentRunningTaskInfo.baseIntent = mBaseIntentWithNoComponent;
        when(mDialerBaseIntent.getComponent())
                .thenReturn(ComponentName.unflattenFromString(DIALER_ACTIVITY_NAME));
        when(mCarSystemBarButton.getSelectedAlpha()).thenReturn(SELECTED_ALPHA);
        when(mIntentAndOnClickListenerFunction.apply(any())).thenReturn(mOnClickListener);
        mRecentsButtonStateProvider = new RecentsButtonStateProvider(mContext, mCarSystemBarButton);
        mTaskStackChangeListener = mRecentsButtonStateProvider.getTaskStackChangeListener();
    }

    @Test
    public void recents_movedToFront_recentsActive() {
        mTaskStackChangeListener.onTaskMovedToFront(mDialerRunningTaskInfo);
        mTaskStackChangeListener.onTaskMovedToFront(mRecentsRunningTaskInfo);

        assertThat(mRecentsButtonStateProvider.getIsRecentsActive()).isTrue();
    }

    @Test
    public void dialer_movedToFront_recentsNotActive() {
        mTaskStackChangeListener.onTaskMovedToFront(mRecentsRunningTaskInfo);
        mTaskStackChangeListener.onTaskMovedToFront(mDialerRunningTaskInfo);

        assertThat(mRecentsButtonStateProvider.getIsRecentsActive()).isFalse();
    }

    @Test
    public void noTopActivityDialerTask_movedToFront_recentsNotActive() {
        mDialerRunningTaskInfo.topActivity = null;
        mDialerRunningTaskInfo.baseIntent = mDialerBaseIntent;

        mTaskStackChangeListener.onTaskMovedToFront(mRecentsRunningTaskInfo);
        mTaskStackChangeListener.onTaskMovedToFront(mDialerRunningTaskInfo);

        assertThat(mRecentsButtonStateProvider.getIsRecentsActive()).isFalse();
    }

    @Test
    public void noTopActivityAndNoBaseIntentTask_movedToFront_recentsNotActive() {
        mTaskStackChangeListener.onTaskMovedToFront(mRecentsRunningTaskInfo);
        mTaskStackChangeListener.onTaskMovedToFront(mNoTopComponentRunningTaskInfo);

        assertThat(mRecentsButtonStateProvider.getIsRecentsActive()).isFalse();
    }

    @Test
    public void setUpIntents_callsParent() {
        mRecentsButtonStateProvider.setUpIntents(mTypedArray, mTypedArrayConsumer);

        verify(mTypedArrayConsumer, times(1)).accept(mTypedArray);
    }

    @Test
    public void setUpIntents_sets_longClickListener() {
        mRecentsButtonStateProvider.setUpIntents(mTypedArray, mTypedArrayConsumer);

        verify(mCarSystemBarButton, times(1))
                .setOnLongClickListener(any(View.OnLongClickListener.class));
    }

    @Test
    public void setUpIntents_onLongClick_recentsNotActive_returnsTrue() {
        mRecentsButtonStateProvider.setIsRecentsActive(false);

        mRecentsButtonStateProvider.setUpIntents(mTypedArray, mTypedArrayConsumer);
        verify(mCarSystemBarButton, times(1))
                .setOnLongClickListener(mOnLongClickListenerCaptor.capture());

        assertThat(mOnLongClickListenerCaptor.getValue().onLongClick(mCarSystemBarButton)).isTrue();
    }

    @Test
    public void setUpIntents_onLongClick_recentsNotActive_keyEventSent() {
        mRecentsButtonStateProvider.setIsRecentsActive(false);

        mRecentsButtonStateProvider.setUpIntents(mTypedArray, mTypedArrayConsumer);
        verify(mCarSystemBarButton, times(1))
                .setOnLongClickListener(mOnLongClickListenerCaptor.capture());
        mOnLongClickListenerCaptor.getValue().onLongClick(mCarSystemBarButton);

        verify(mInputManager, times(1))
                .injectInputEvent(argThat(this::isRecentsKeyEvent), anyInt());
    }

    @Test
    public void setUpIntents_onLongClick_configSetToFalse_recentsNotActive_returnsFalse() {
        mTestableResources.addOverride(R.bool.config_enableRecentsEntryPoint, false);
        mRecentsButtonStateProvider = new RecentsButtonStateProvider(mContext, mCarSystemBarButton);
        mTaskStackChangeListener = mRecentsButtonStateProvider.getTaskStackChangeListener();
        mRecentsButtonStateProvider.setIsRecentsActive(false);

        mRecentsButtonStateProvider.setUpIntents(mTypedArray, mTypedArrayConsumer);
        verify(mCarSystemBarButton, times(1))
                .setOnLongClickListener(mOnLongClickListenerCaptor.capture());

        assertThat(
                mOnLongClickListenerCaptor.getValue().onLongClick(mCarSystemBarButton)).isFalse();
    }

    @Test
    public void setUpIntents_onLongClick_configSetToFalse_recentsNotActive_noKeyEventSent() {
        mTestableResources.addOverride(R.bool.config_enableRecentsEntryPoint, false);
        mRecentsButtonStateProvider = new RecentsButtonStateProvider(mContext, mCarSystemBarButton);
        mTaskStackChangeListener = mRecentsButtonStateProvider.getTaskStackChangeListener();
        mRecentsButtonStateProvider.setIsRecentsActive(false);

        mRecentsButtonStateProvider.setUpIntents(mTypedArray, mTypedArrayConsumer);
        verify(mCarSystemBarButton, times(1))
                .setOnLongClickListener(mOnLongClickListenerCaptor.capture());
        mOnLongClickListenerCaptor.getValue().onLongClick(mCarSystemBarButton);

        verify(mInputManager, never()).injectInputEvent(argThat(this::isRecentsKeyEvent), anyInt());
    }

    @Test
    public void setUpIntents_onLongClick_recentsActive_returnsFalse() {
        mRecentsButtonStateProvider.setIsRecentsActive(true);

        mRecentsButtonStateProvider.setUpIntents(mTypedArray, mTypedArrayConsumer);
        verify(mCarSystemBarButton, times(1))
                .setOnLongClickListener(mOnLongClickListenerCaptor.capture());

        assertThat(mOnLongClickListenerCaptor.getValue().onLongClick(mCarSystemBarButton))
                .isFalse();
    }

    @Test
    public void setUpIntents_onLongClick_recentsActive_noKeyEventSent() {
        mRecentsButtonStateProvider.setIsRecentsActive(true);

        mRecentsButtonStateProvider.setUpIntents(mTypedArray, mTypedArrayConsumer);
        verify(mCarSystemBarButton, times(1))
                .setOnLongClickListener(mOnLongClickListenerCaptor.capture());
        mOnLongClickListenerCaptor.getValue().onLongClick(mCarSystemBarButton);

        verify(mInputManager, never()).injectInputEvent(argThat(this::isRecentsKeyEvent), anyInt());
    }

    @Test
    public void getButtonClickListener_onClick_recentsNotActive_callsParent() {
        mRecentsButtonStateProvider.setIsRecentsActive(false);

        View.OnClickListener onClickListener = mRecentsButtonStateProvider.getButtonClickListener(
                mIntent, mIntentAndOnClickListenerFunction);
        onClickListener.onClick(mCarSystemBarButton);

        verify(mIntentAndOnClickListenerFunction, times(1)).apply(mIntent);
    }

    @Test
    public void getButtonClickListener_onClick_recentsActive_doesNotCallParent() {
        mRecentsButtonStateProvider.setIsRecentsActive(true);

        View.OnClickListener onClickListener = mRecentsButtonStateProvider.getButtonClickListener(
                mIntent, mIntentAndOnClickListenerFunction);
        onClickListener.onClick(mCarSystemBarButton);

        verify(mIntentAndOnClickListenerFunction, never()).apply(any());
    }

    @Test
    public void getButtonClickListener_onClick_recentsNotActive_noKeyEventSent() {
        mRecentsButtonStateProvider.setIsRecentsActive(false);

        View.OnClickListener onClickListener = mRecentsButtonStateProvider.getButtonClickListener(
                mIntent, mIntentAndOnClickListenerFunction);
        onClickListener.onClick(mCarSystemBarButton);

        verify(mInputManager, never()).injectInputEvent(argThat(this::isRecentsKeyEvent), anyInt());
    }

    @Test
    public void getButtonClickListener_onClick_recentsActive_keyEventSent() {
        mRecentsButtonStateProvider.setIsRecentsActive(true);

        View.OnClickListener onClickListener = mRecentsButtonStateProvider.getButtonClickListener(
                mIntent, mIntentAndOnClickListenerFunction);
        onClickListener.onClick(mCarSystemBarButton);

        verify(mInputManager, times(1))
                .injectInputEvent(argThat(this::isRecentsKeyEvent), anyInt());
    }

    @Test
    public void updateImage_recentsNotActive_callsParent() {
        mRecentsButtonStateProvider.setIsRecentsActive(false);

        mRecentsButtonStateProvider.updateImage(mAlphaOptimizedImageView,
                mAlphaOptimizedImageViewConsumer);

        verify(mAlphaOptimizedImageViewConsumer, times(1))
                .accept(mAlphaOptimizedImageView);
    }

    @Test
    public void updateImage_recentsActive_doesNotCallParent() {
        mRecentsButtonStateProvider.setIsRecentsActive(true);

        mRecentsButtonStateProvider.updateImage(mAlphaOptimizedImageView,
                mAlphaOptimizedImageViewConsumer);

        verify(mAlphaOptimizedImageViewConsumer, never()).accept(any());
    }

    @Test
    public void updateImage_recentsNotActive_iconResourceNotSetToRecentsIcon() {
        mRecentsButtonStateProvider.setIsRecentsActive(false);

        mRecentsButtonStateProvider.updateImage(mAlphaOptimizedImageView,
                mAlphaOptimizedImageViewConsumer);

        verify(mAlphaOptimizedImageView, never())
                .setImageResource(eq(com.android.systemui.R.drawable.car_ic_recents));
    }

    @Test
    public void updateImage_recentsActive_iconResourceSetToRecentsIcon() {
        mRecentsButtonStateProvider.setIsRecentsActive(true);

        mRecentsButtonStateProvider.updateImage(mAlphaOptimizedImageView,
                mAlphaOptimizedImageViewConsumer);

        verify(mAlphaOptimizedImageView, times(1))
                .setImageResource(eq(com.android.systemui.R.drawable.car_ic_recents));
    }

    @Test
    public void refreshIconAlpha_recentsNotActive_callsParent() {
        mRecentsButtonStateProvider.setIsRecentsActive(false);

        mRecentsButtonStateProvider.refreshIconAlpha(mAlphaOptimizedImageView,
                mAlphaOptimizedImageViewConsumer);

        verify(mAlphaOptimizedImageViewConsumer, times(1))
                .accept(mAlphaOptimizedImageView);
    }

    @Test
    public void refreshIconAlpha_recentsActive_doesNotCallParent() {
        mRecentsButtonStateProvider.setIsRecentsActive(true);

        mRecentsButtonStateProvider.refreshIconAlpha(mAlphaOptimizedImageView,
                mAlphaOptimizedImageViewConsumer);

        verify(mAlphaOptimizedImageViewConsumer, never()).accept(any());
    }

    @Test
    public void refreshIconAlpha_recentsNotActive_iconAlphaSetToSelectedAlpha() {
        mRecentsButtonStateProvider.setIsRecentsActive(false);

        mRecentsButtonStateProvider.refreshIconAlpha(mAlphaOptimizedImageView,
                mAlphaOptimizedImageViewConsumer);

        verify(mAlphaOptimizedImageView, never()).setAlpha(SELECTED_ALPHA);
    }

    @Test
    public void refreshIconAlpha_recentsActive_iconAlphaNotSetToSelectedAlpha() {
        mRecentsButtonStateProvider.setIsRecentsActive(true);

        mRecentsButtonStateProvider.refreshIconAlpha(mAlphaOptimizedImageView,
                mAlphaOptimizedImageViewConsumer);

        verify(mAlphaOptimizedImageView, times(1)).setAlpha(SELECTED_ALPHA);
    }

    private boolean isRecentsKeyEvent(InputEvent event) {
        return event instanceof KeyEvent
                && ((KeyEvent) event).getKeyCode() == KeyEvent.KEYCODE_APP_SWITCH;
    }
}
