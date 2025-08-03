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

package com.android.car.admin.ui;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.verify;

import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;

import androidx.test.ext.junit.rules.ActivityScenarioRule;

import com.android.settingslib.drawable.UserIconDrawable;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

/** Unit tests for {@link UserAvatarView}. */
@RunWith(MockitoJUnitRunner.class)
public final class UserAvatarViewTest {
    @Rule
    public ActivityScenarioRule<CarAdminUiTestActivity> mActivityScenarioRule =
            new ActivityScenarioRule<>(CarAdminUiTestActivity.class);

    private UserAvatarView mUserAvatarView;
    private DevicePolicyManager mMockDevicePolicyManager;

    @Before
    public void setup() {
        mActivityScenarioRule.getScenario().onActivity(activity -> {
            mUserAvatarView = ((CarAdminUiTestActivity) activity).mUserAvatarView;
            mMockDevicePolicyManager = ((CarAdminUiTestActivity) activity).mMockDevicePolicyManager;
        });
    }

    @Test
    public void setAvatar() {
        Bitmap bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);

        mUserAvatarView.setAvatar(bitmap);

        assertThat(mUserAvatarView.getUserIconDrawable().getUserIcon()).isEqualTo(bitmap);
        assertThat(mUserAvatarView.getUserIconDrawable().getBadge()).isNull();
    }

    @Test
    public void setAvatarWithBadge_success() {
        Bitmap bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);

        mUserAvatarView.setAvatarWithBadge(bitmap, ActivityManager.getCurrentUser());

        assertThat(mUserAvatarView.getUserIconDrawable().getUserIcon()).isEqualTo(bitmap);
        verify(mMockDevicePolicyManager).getProfileOwnerAsUser(ActivityManager.getCurrentUser());
        // mBadge still remains null because of unmanaged user.
        assertThat(mUserAvatarView.getUserIconDrawable().getBadge()).isNull();
    }

    @Test
    public void setDrawable_userIconDrawable_throwsError() {
        assertThrows(IllegalArgumentException.class,
                () -> mUserAvatarView.setDrawable(new UserIconDrawable()));
    }

    @Test
    public void setDrawable_success() {
        Drawable d = new ShapeDrawable(new OvalShape());

        mUserAvatarView.setDrawable(d);

        assertThat(mUserAvatarView.getUserIconDrawable().getUserDrawable()).isEqualTo(d);
    }

    @Test
    public void setDrawableWithBadgeAndUserId_userIconDrawable_throwsError() {
        assertThrows(IllegalArgumentException.class,
                () -> mUserAvatarView.setDrawableWithBadge(new UserIconDrawable(),
                        ActivityManager.getCurrentUser()));
    }

    @Test
    public void setDrawableWithBadgeAndUserId_success() {
        Drawable d = new ShapeDrawable(new OvalShape());

        mUserAvatarView.setDrawableWithBadge(d, ActivityManager.getCurrentUser());

        verify(mMockDevicePolicyManager).getProfileOwnerAsUser(ActivityManager.getCurrentUser());
        // mBadge still remains null because of unmanaged user.
        assertThat(mUserAvatarView.getUserIconDrawable().getBadge()).isNull();
    }

    @Test
    public void setDrawableWithBadge_userIconDrawable_throwsError() {
        assertThrows(IllegalArgumentException.class,
                () -> mUserAvatarView.setDrawableWithBadge(new UserIconDrawable()));
    }

    @Test
    public void setDrawableWithBadge_success() {
        Drawable d = new ShapeDrawable(new OvalShape());

        mUserAvatarView.setDrawableWithBadge(d);

        verify(mMockDevicePolicyManager).getDeviceOwnerComponentOnAnyUser();
        // mBadge still remains null because of unmanaged user.
        assertThat(mUserAvatarView.getUserIconDrawable().getBadge()).isNull();
    }

    @Test
    public void getUserIconDrawable_returnsDrawable() {
        assertThat(mUserAvatarView.getUserIconDrawable()).isEqualTo(
                mUserAvatarView.getUserIconDrawable());
    }

    @Test
    public void setActivated_invalidatesUserIcon() {
        mUserAvatarView.setActivated(true);

        assertThat(mUserAvatarView.getUserIconDrawable().isInvalidated()).isTrue();
    }
}
