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

package com.android.car.internal.user;

import android.annotation.ColorInt;
import android.annotation.NonNull;
import android.content.Context;
import android.content.pm.UserInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BlendMode;
import android.graphics.BlendModeColorFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import com.android.internal.util.UserIcons;

/**
 * Helper class for providing Car user icons.
 *
 * @hide
 */
class CarUserIconProvider {
    private static final int[] USER_NAME_ICON_COLORS = {
            R.color.car_internal_user_name_icon_1,
            R.color.car_internal_user_name_icon_2,
            R.color.car_internal_user_name_icon_3,
            R.color.car_internal_user_name_icon_4,
            R.color.car_internal_user_name_icon_5,
            R.color.car_internal_user_name_icon_6,
            R.color.car_internal_user_name_icon_7,
            R.color.car_internal_user_name_icon_8
    };

    private static final int[] USER_BACKGROUND_ICON_COLORS = {
            R.color.car_internal_user_background_icon_1,
            R.color.car_internal_user_background_icon_2,
            R.color.car_internal_user_background_icon_3,
            R.color.car_internal_user_background_icon_4,
            R.color.car_internal_user_background_icon_5,
            R.color.car_internal_user_background_icon_6,
            R.color.car_internal_user_background_icon_7,
            R.color.car_internal_user_background_icon_8
    };

    static Bitmap getDefaultUserIcon(@NonNull Context context, @NonNull UserInfo userInfo) {
        Resources resources = context.getResources();
        if (userInfo.isGuest()) {
            return getGuestDefaultUserIcon(resources);
        }

        Drawable icon = resources.getDrawable(
                R.drawable.car_internal_user_icon_circle_background, /* theme= */ null)
                .mutate();
        icon.setBounds(/* left= */ 0, /* top= */ 0,
                icon.getIntrinsicWidth(), icon.getIntrinsicHeight());
        // Set color for the background of the user icon.
        int backgroundColor = getUserBackgroundIconColor(context, userInfo);
        icon.setColorFilter(new BlendModeColorFilter(backgroundColor, BlendMode.SRC_IN));

        Bitmap userIconBitmap = UserIcons.convertToBitmap(icon);

        // Set the first letter of user name as user icon.
        String firstLetter = userInfo.name.substring(/* beginIndex= */ 0, /* endIndex= */ 1);
        Paint paint = new Paint();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(getUserNameIconColor(context, userInfo));
        paint.setTextSize(resources.getDimension(R.dimen.car_internal_user_icon_text_size));
        paint.setTextAlign(Paint.Align.LEFT);

        // Draw text in center of the canvas.
        Canvas canvas = new Canvas(userIconBitmap);
        Rect textBounds = new Rect();
        paint.getTextBounds(firstLetter, /*start=*/0, /*end=*/1, textBounds);
        float x = canvas.getWidth() * 0.5f - textBounds.exactCenterX();
        float y = canvas.getHeight() * 0.5f - textBounds.exactCenterY();
        canvas.drawText(firstLetter, x, y, paint);

        return userIconBitmap;
    }

    static Bitmap getGuestDefaultUserIcon(Resources resources) {
        return UserIcons.convertToBitmap(
                resources.getDrawable(R.drawable.car_internal_guest_user_icon, null));
    }

    @ColorInt
    static int getUserNameIconColor(@NonNull Context context, @NonNull UserInfo userInfo) {
        if (userInfo.isGuest()) {
            return context.getColor(R.color.car_internal_guest_user_avatar_color);
        }
        return context.getColor(USER_NAME_ICON_COLORS[userInfo.id % USER_NAME_ICON_COLORS.length]);
    }

    @ColorInt
    static int getUserBackgroundIconColor(@NonNull Context context, @NonNull UserInfo userInfo) {
        if (userInfo.isGuest()) {
            return context.getColor(R.color.car_internal_guest_user_background_color);
        }
        return context.getColor(
                USER_BACKGROUND_ICON_COLORS[userInfo.id % USER_BACKGROUND_ICON_COLORS.length]);
    }
}
