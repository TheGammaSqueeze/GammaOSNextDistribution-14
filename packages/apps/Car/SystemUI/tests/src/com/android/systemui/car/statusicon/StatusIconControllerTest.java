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

package com.android.systemui.car.statusicon;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import android.graphics.drawable.Drawable;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.view.View;
import android.widget.ImageView;

import androidx.test.filters.SmallTest;

import com.android.systemui.R;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
@SmallTest
public class StatusIconControllerTest extends SysuiTestCase {

    private TestStatusIconController mTestStatusIconController;

    @Mock
    private ImageView mImageView;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mTestStatusIconController = new TestStatusIconController();
    }

    @Test
    public void registerAnImageView_viewNotRegistered_viewIsRegistered() {
        mTestStatusIconController.registerIconView(mImageView);

        assertThat(mTestStatusIconController.isViewRegistered(mImageView)).isTrue();
    }

    @Test
    public void registerAnImageView_viewAlreadyRegistered_viewRegisteredAndDoesNotThrowError() {
        mTestStatusIconController.registerIconView(mImageView);
        mTestStatusIconController.registerIconView(mImageView);

        assertThat(mTestStatusIconController.isViewRegistered(mImageView)).isTrue();
    }

    @Test
    public void unregisterAnImageView_viewAlreadyRegistered_viewIsUnregistered() {
        mTestStatusIconController.registerIconView(mImageView);
        mTestStatusIconController.unregisterIconView(mImageView);

        assertThat(mTestStatusIconController.isViewRegistered(mImageView)).isFalse();
    }

    @Test
    public void unregisterAnImageView_viewNotRegistered_viewIsUnregisteredAndDoesNotThrowError() {
        mTestStatusIconController.unregisterIconView(mImageView);
        mTestStatusIconController.unregisterIconView(mImageView);

        assertThat(mTestStatusIconController.isViewRegistered(mImageView)).isFalse();
    }

    @Test
    public void onStatusUpdated_viewRegistered_registeredViewUpdatesIconAsSpecified() {
        Drawable testIconDrawable = mContext.getDrawable(R.drawable.ic_android);
        mTestStatusIconController.registerIconView(mImageView);
        mTestStatusIconController.setIconDrawableToDisplay(testIconDrawable);
        reset(mImageView);

        mTestStatusIconController.onStatusUpdated();

        verify(mImageView).setImageDrawable(testIconDrawable);
    }

    @Test
    public void onStatusUpdated_viewUnregistered_unregisteredViewDoesNotUpdateIconAsSpecified() {
        Drawable testIconDrawable = mContext.getDrawable(R.drawable.ic_android);
        mTestStatusIconController.registerIconView(mImageView);
        mTestStatusIconController.unregisterIconView(mImageView);
        mTestStatusIconController.setIconDrawableToDisplay(testIconDrawable);
        reset(mImageView);

        mTestStatusIconController.onStatusUpdated();

        verify(mImageView, never()).setImageDrawable(testIconDrawable);
    }

    private class TestStatusIconController extends StatusIconController {

        @Override
        protected void updateStatus() {
            // no-op.
        }

        @Override
        protected void updateIconView(ImageView view, StatusIconData data) {
            view.setImageDrawable(data.getIconDrawable());
        }

        @Override
        protected int getId() {
            return View.generateViewId();
        }
    }
}
