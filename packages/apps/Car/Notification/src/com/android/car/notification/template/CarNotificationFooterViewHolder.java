/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.car.notification.template;

import android.annotation.CallSuper;
import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;

import androidx.recyclerview.widget.RecyclerView;

import com.android.car.notification.CarNotificationItemController;
import com.android.car.notification.NotificationClickHandlerFactory;
import com.android.car.notification.NotificationUtils;
import com.android.car.notification.R;

/**
 * Footer template for the notification shade. This templates supports the clear all button with id
 * clear_all_button.
 */
public class CarNotificationFooterViewHolder extends RecyclerView.ViewHolder {
    private final Context mContext;
    private final Button mClearAllButton;
    private final CarNotificationItemController mNotificationItemController;
    private final boolean mShowFooter;
    private final boolean mShowRecentsAndOlderHeaders;
    private final NotificationClickHandlerFactory mClickHandlerFactory;
    private final float mAlpha;
    private final boolean mCollapsePanelAfterManageButton;

    public CarNotificationFooterViewHolder(Context context, View view,
            CarNotificationItemController notificationItemController,
            NotificationClickHandlerFactory notificationClickHandlerFactory) {
        super(view);

        if (notificationItemController == null) {
            throw new IllegalArgumentException(
                    "com.android.car.notification.template.CarNotificationFooterViewHolder did not "
                            + "receive NotificationItemController from the Adapter.");
        }

        mContext = context;
        mClickHandlerFactory = notificationClickHandlerFactory;
        mShowFooter = context.getResources().getBoolean(R.bool.config_showFooterForNotifications);
        mClearAllButton = view.findViewById(R.id.clear_all_button);
        mNotificationItemController = notificationItemController;
        mShowRecentsAndOlderHeaders =
                context.getResources().getBoolean(R.bool.config_showRecentAndOldHeaders);
        mAlpha = context.getResources().getFloat(R.dimen.config_olderNotificationsAlpha);
        mCollapsePanelAfterManageButton = context.getResources().getBoolean(
                R.bool.config_collapseShadePanelAfterManageButtonPress);
    }

    @CallSuper
    public void bind(boolean containsNotification, boolean containsSeenNotifications) {
        if (mClearAllButton == null || !mShowFooter) {
            return;
        }

        if (containsNotification) {
            mClearAllButton.setVisibility(View.VISIBLE);
            if (mShowRecentsAndOlderHeaders) {
                mClearAllButton.setId(R.id.manage_button);
                mClearAllButton.setText(R.string.manage_text);
                mClearAllButton.setOnClickListener(this::manageButtonOnClickListener);
                mClearAllButton.setAlpha(containsSeenNotifications ? mAlpha : 1);
            } else {
                mClearAllButton.setOnClickListener(
                        view -> mNotificationItemController.clearAllNotifications());
            }
            return;
        }
        mClearAllButton.setVisibility(View.GONE);
    }

    private void manageButtonOnClickListener(View v) {
        Intent intent = new Intent(Settings.ACTION_NOTIFICATION_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        mContext.startActivityAsUser(intent,
                UserHandle.of(NotificationUtils.getCurrentUser(mContext)));

        if (mClickHandlerFactory != null && mCollapsePanelAfterManageButton) {
            mClickHandlerFactory.collapsePanel();
        }
    }
}
