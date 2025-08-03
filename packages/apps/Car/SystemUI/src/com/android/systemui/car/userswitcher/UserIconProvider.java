/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.systemui.car.userswitcher;

import android.annotation.UserIdInt;
import android.content.Context;
import android.content.pm.UserInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;
import android.os.UserManager;

import androidx.core.graphics.drawable.RoundedBitmapDrawable;

import com.android.car.admin.ui.UserAvatarView;
import com.android.car.internal.user.UserHelper;
import com.android.systemui.R;

/**
 * Simple class for providing icons for users.
 */
public class UserIconProvider {
    /**
     * Sets a rounded icon with the first letter of the given user name.
     * This method will update UserManager to use that icon.
     *
     * @param userInfo User for which the icon is requested.
     * @param context Context to use for resources
     */
    public void setRoundedUserIcon(UserInfo userInfo, Context context) {
        UserHelper.assignDefaultIcon(context, userInfo.getUserHandle());
    }

    /**
     * Gets a scaled rounded icon for the given user.  If a user does not have an icon saved, this
     * method will default to a generic icon and update UserManager to use that icon.
     *
     * @param userInfo User for which the icon is requested.
     * @param context Context to use for resources
     * @return {@link RoundedBitmapDrawable} representing the icon for the user.
     */
    public Drawable getRoundedUserIcon(UserInfo userInfo, Context context) {
        UserManager userManager = context.getSystemService(UserManager.class);
        Resources res = context.getResources();
        Bitmap icon = userManager.getUserIcon(userInfo.id);

        if (icon == null) {
            icon = UserHelper.assignDefaultIcon(context, userInfo.getUserHandle());
        }

        return new BitmapDrawable(res, icon);
    }

    /**
     * Gets a user icon with badge if the user profile is managed.
     *
     * @param context to use for the avatar view
     * @param userInfo User for which the icon is requested and badge is set
     * @return {@link Drawable} with badge
     */
    public Drawable getDrawableWithBadge(Context context, UserInfo userInfo) {
        return addBadge(context, getRoundedUserIcon(userInfo, context), userInfo.id);
    }

    /**
     * Gets an icon with badge if the device is managed.
     *
     * @param context context
     * @param drawable icon without badge
     * @return {@link Drawable} with badge
     */
    public Drawable getDrawableWithBadge(Context context, Drawable drawable) {
        return addBadge(context, drawable, UserHandle.USER_NULL);
    }

    private static Drawable addBadge(Context context, Drawable drawable, @UserIdInt int userId) {
        int iconSize = drawable.getIntrinsicWidth();
        UserAvatarView userAvatarView = new UserAvatarView(context);
        float badgeToIconSizeRatio =
                context.getResources().getDimension(R.dimen.car_user_switcher_managed_badge_size)
                        / context.getResources().getDimension(
                        R.dimen.car_user_switcher_image_avatar_size);
        userAvatarView.setBadgeDiameter(iconSize * badgeToIconSizeRatio);
        float badgePadding = context.getResources().getDimension(
                R.dimen.car_user_switcher_managed_badge_margin);
        userAvatarView.setBadgeMargin(badgePadding);
        if (userId != UserHandle.USER_NULL) {
            // When the userId is valid, add badge if the user is managed.
            userAvatarView.setDrawableWithBadge(drawable, userId);
        } else {
            // When the userId is not valid, add badge if the device is managed.
            userAvatarView.setDrawableWithBadge(drawable);
        }
        Drawable badgedIcon = userAvatarView.getUserIconDrawable();
        badgedIcon.setBounds(0, 0, iconSize, iconSize);
        return badgedIcon;
    }

    /** Returns a scaled, rounded, default icon for the Guest user */
    public Drawable getRoundedGuestDefaultIcon(Context context) {
        Bitmap icon = UserHelper.getGuestDefaultIcon(context);
        return new BitmapDrawable(context.getResources(), icon);
    }
}
