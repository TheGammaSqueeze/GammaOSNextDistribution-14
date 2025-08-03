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
package com.android.car.notification.template;

import android.annotation.CallSuper;
import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.android.car.notification.CarNotificationItemController;
import com.android.car.notification.R;

/**
 * Recents Header template for the notification shade. This templates supports the clear all button
 * with ID clear_all_button, a header text with ID notification_recents_text when the notification
 * list is not empty and has unseen notifications.
 */
public class CarNotificationRecentsViewHolder extends RecyclerView.ViewHolder {
    private final TextView mNotificationRecentsText;
    private final Button mClearAllButton;
    private final CarNotificationItemController mNotificationItemController;
    private final boolean mShowRecents;

    public CarNotificationRecentsViewHolder(Context context, View view,
            @NonNull CarNotificationItemController notificationItemController) {
        super(view);

        mNotificationRecentsText = view.findViewById(R.id.notification_recents_text);
        mClearAllButton = view.findViewById(R.id.clear_all_button);
        mShowRecents = context.getResources().getBoolean(R.bool.config_showRecentAndOldHeaders);
        mNotificationItemController = notificationItemController;
    }

    /**
     * Sets up {@link CarNotificationRecentsViewHolder}'s text and clear button functionality
     *
     * @param containsNotification {@code true} if notifications exist under this header
     */
    @CallSuper
    public void bind(boolean containsNotification) {
        if (containsNotification && mShowRecents) {
            mNotificationRecentsText.setVisibility(View.VISIBLE);

            if (mClearAllButton == null) {
                return;
            }

            mClearAllButton.setVisibility(View.VISIBLE);
            if (!mClearAllButton.hasOnClickListeners()) {
                mClearAllButton.setOnClickListener(view -> {
                    mNotificationItemController.clearAllNotifications();
                });
            }
            return;
        }

        mNotificationRecentsText.setVisibility(View.GONE);

        if (mClearAllButton != null) {
            mClearAllButton.setVisibility(View.GONE);
        }
    }
}
