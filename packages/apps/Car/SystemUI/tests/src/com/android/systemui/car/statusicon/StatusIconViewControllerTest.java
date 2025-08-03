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

package com.android.systemui.car.statusicon;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import android.graphics.drawable.Drawable;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.filters.SmallTest;

import com.android.systemui.R;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.systembar.element.CarSystemBarElementStateController;
import com.android.systemui.car.systembar.element.CarSystemBarElementStatusBarDisableController;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
@SmallTest
public class StatusIconViewControllerTest extends SysuiTestCase {

    private TestStatusIconViewController mController;

    @Mock
    private StatusIconView mStatusIconView;
    @Mock
    private CarSystemBarElementStatusBarDisableController mDisableController;
    @Mock
    private CarSystemBarElementStateController mStateController;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mController = new TestStatusIconViewController(mStatusIconView, mDisableController,
                mStateController);
    }

    @Test
    public void onStatusUpdated_viewAttached_registeredViewUpdatesIconAsSpecified() {
        Drawable testIconDrawable = mContext.getDrawable(R.drawable.ic_android);
        mController.setIconDrawableToDisplay(testIconDrawable);
        mController.onViewAttached();
        reset(mStatusIconView);

        mController.onStatusUpdated();

        verify(mStatusIconView).setImageDrawable(testIconDrawable);
    }

    @Test
    public void onStatusUpdated_viewUnattached_unregisteredViewDoesNotUpdateIconAsSpecified() {
        Drawable testIconDrawable = mContext.getDrawable(R.drawable.ic_android);
        mController.setIconDrawableToDisplay(testIconDrawable);
        reset(mStatusIconView);

        mController.onStatusUpdated();

        verify(mStatusIconView, never()).setImageDrawable(testIconDrawable);
    }

    private static class TestStatusIconViewController extends StatusIconViewController {

        protected TestStatusIconViewController(StatusIconView view,
                CarSystemBarElementStatusBarDisableController disableController,
                CarSystemBarElementStateController selectionRestorer) {
            super(view, disableController, selectionRestorer);
        }

        @Override
        protected void updateStatus() {
            // no-op.
        }

        @Override
        protected void updateIconView(StatusIconData data) {
            mView.setImageDrawable(data.getIconDrawable());
        }
    }
}
