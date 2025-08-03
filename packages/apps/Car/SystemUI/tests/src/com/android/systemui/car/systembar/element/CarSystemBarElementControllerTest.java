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

package com.android.systemui.car.systembar.element;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.StatusBarManager;
import android.content.Context;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.view.Display;
import android.view.View;

import androidx.test.filters.SmallTest;

import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class CarSystemBarElementControllerTest extends SysuiTestCase {

    private TestCarSystemBarElement mElement;
    private TestCarSystemBarElementController mController;

    @Mock
    CarSystemBarElementStatusBarDisableController mDisableController;
    @Mock
    CarSystemBarElementStateController mStateController;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mElement = new TestCarSystemBarElement(mContext);
        mController = new TestCarSystemBarElementController(mElement);
    }

    @Test
    public void onViewAttached_noFlagsSet_doesNotSetDisableListener() {
        mController.onViewAttached();
        verify(mDisableController, never()).addListener(any(), anyInt(), anyInt(), anyInt(),
                anyBoolean());
    }

    @Test
    public void onViewAttached_disableFlagsSet_setsDisableListener() {
        mElement.setDisableFlag(StatusBarManager.DISABLE_HOME);
        mController.onViewAttached();
        verify(mDisableController).addListener(any(), anyInt(), eq(StatusBarManager.DISABLE_HOME),
                anyInt(), anyBoolean());
    }

    @Test
    public void onViewAttached_disable2FlagsSet_setsDisableListener() {
        mElement.setDisable2Flag(StatusBarManager.DISABLE2_SYSTEM_ICONS);
        mController.onViewAttached();
        verify(mDisableController).addListener(any(), anyInt(), anyInt(),
                eq(StatusBarManager.DISABLE2_SYSTEM_ICONS), anyBoolean());
    }

    @Test
    public void onViewAttached_lockTaskFlagsSet_setsDisableListener() {
        mElement.setDisableForLockTaskModeLocked(true);
        mController.onViewAttached();
        verify(mDisableController).addListener(any(), anyInt(), anyInt(), anyInt(), eq(true));
    }

    @Test
    public void onViewAttached_shouldNotRestoreState_doesNotSetStateController() {
        mController.setShouldRestoreState(false);
        mController.onViewAttached();
        verify(mStateController, never()).registerController(any());
    }

    @Test
    public void onViewAttached_shouldRestoreState_setsStateController() {
        mController.setShouldRestoreState(true);
        mController.onViewAttached();
        verify(mStateController).registerController(any());
    }

    @Test
    public void onViewDetached_unsetsDisableListener() {
        mElement.setDisableFlag(StatusBarManager.DISABLE_HOME);
        mController.onViewAttached();
        mController.onViewDetached();
        verify(mDisableController).removeListener(any());
    }

    @Test
    public void onViewDetached_unsetsStateController() {
        mController.setShouldRestoreState(true);
        mController.onViewAttached();
        mController.onViewDetached();
        verify(mStateController).unregisterController(any());
    }

    private static class TestCarSystemBarElement extends View implements CarSystemBarElement {
        private int mDisableFlag = StatusBarManager.DISABLE_NONE;
        private int mDisable2Flag = StatusBarManager.DISABLE2_NONE;
        private boolean mDisableForLockTaskModeLocked;
        TestCarSystemBarElement(Context context) {
            super(context);
        }

        @Override
        public Class<?> getElementControllerClass() {
            return TestCarSystemBarElementController.class;
        }

        @Override
        public int getSystemBarDisableFlags() {
            return mDisableFlag;
        }

        @Override
        public int getSystemBarDisable2Flags() {
            return mDisable2Flag;
        }

        @Override
        public boolean disableForLockTaskModeLocked() {
            return mDisableForLockTaskModeLocked;
        }

        @Override
        public Display getDisplay() {
            Display display = mock(Display.class);
            when(display.getDisplayId()).thenReturn(Display.DEFAULT_DISPLAY);
            return display;
        }

        void setDisableFlag(int flag) {
            mDisableFlag = flag;
        }

        void setDisable2Flag(int flag) {
            mDisable2Flag = flag;
        }

        void setDisableForLockTaskModeLocked(boolean disable) {
            mDisableForLockTaskModeLocked = disable;
        }
    }

    private class TestCarSystemBarElementController extends
            CarSystemBarElementController<TestCarSystemBarElement> {
        private boolean mShouldRestoreState;

        TestCarSystemBarElementController(TestCarSystemBarElement view) {
            super(view, mDisableController, mStateController);
        }

        @Override
        protected boolean shouldRestoreState() {
            return mShouldRestoreState;
        }

        void setShouldRestoreState(boolean restore) {
            mShouldRestoreState = restore;
        }
    }
}
