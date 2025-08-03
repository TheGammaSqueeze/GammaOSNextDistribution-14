/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static com.android.car.ui.utils.CarUiUtils.drawableToBitmap;

import android.content.Context;
import android.content.pm.UserInfo;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Handler;

import com.android.car.qc.QCItem;
import com.android.car.qc.QCRow;
import com.android.systemui.R;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.settings.UserTracker;

import javax.inject.Inject;

/**
 * Local provider for the profile switcher panel with secondary text for current profile.
 */
public class CarUiPortraitProfileSwitcher extends ProfileSwitcher {

    @Inject
    public CarUiPortraitProfileSwitcher(Context context, UserTracker userTracker,
            CarServiceProvider carServiceProvider,  @Background Handler handler) {
        super(context, userTracker, carServiceProvider, handler);

    }

    @Override
    protected QCRow createUserProfileRow(UserInfo userInfo) {
        if (userInfo.id == mUserTracker.getUserId()) {
            return createUserProfileRowForCurrentProfile(userInfo);
        }
        return super.createUserProfileRow(userInfo);
    }

    @Override
    protected QCRow createGuestProfileRow() {
        if (mUserTracker.getUserInfo() != null && mUserTracker.getUserInfo().isGuest()) {
            return createGuestProfileRowForCurrentProfile();
        } else {
            return super.createGuestProfileRow();
        }
    }

    private QCRow createUserProfileRowForCurrentProfile(UserInfo userInfo) {
        QCItem.ActionHandler actionHandler = (item, context, intent) -> {
            if (mPendingUserAdd) {
                return;
            }
            switchUser(userInfo.id);
        };
        return createUserProfileRowForCurrentProfile(userInfo.name,
                mUserIconProvider.getDrawableWithBadge(mContext, userInfo), actionHandler);
    }

    private QCRow createGuestProfileRowForCurrentProfile() {
        QCItem.ActionHandler actionHandler = (item, context, intent) -> {
            if (mPendingUserAdd) {
                return;
            }
            UserInfo guest = createNewOrFindExistingGuest(mContext);
            if (guest != null) {
                switchUser(guest.id);
            }
        };

        return createUserProfileRowForCurrentProfile(
                mContext.getString(com.android.internal.R.string.guest_name),
                mUserIconProvider.getRoundedGuestDefaultIcon(mContext),
                actionHandler);
    }

    private QCRow createUserProfileRowForCurrentProfile(String title, Drawable iconDrawable,
            QCItem.ActionHandler actionHandler) {
        Icon icon = Icon.createWithBitmap(drawableToBitmap(iconDrawable));
        String subtitle = mContext.getString(R.string.current_profile_subtitle);
        QCRow row = new QCRow.Builder()
                .setIcon(icon)
                .setIconTintable(false)
                .setTitle(title)
                .setSubtitle(subtitle)
                .build();
        row.setActionHandler(actionHandler);
        return row;
    }
}
