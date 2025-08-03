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
 * Older Header template for the notification shade. This templates supports the clear all button
 * with ID clear_all_button, a header text with ID notification_older_text when the notification
 * list is not empty and has seen notifications. Clear all button is only shown if no unseen
 * notifications are present.
 */
public class CarNotificationOlderViewHolder extends RecyclerView.ViewHolder {
    private final TextView mNotificationOlderText;
    private final Button mClearAllButton;
    private final CarNotificationItemController mNotificationItemController;
    private final boolean mShowOlder;
    private final float mAlpha;

    public CarNotificationOlderViewHolder(Context context, View view,
            @NonNull CarNotificationItemController notificationItemController) {
        super(view);

        mNotificationOlderText = view.findViewById(R.id.notification_older_text);
        mClearAllButton = view.findViewById(R.id.clear_all_button);
        mShowOlder = context.getResources().getBoolean(R.bool.config_showRecentAndOldHeaders);
        mNotificationItemController = notificationItemController;
        mAlpha = context.getResources().getFloat(R.dimen.config_olderNotificationsAlpha);

        if (mNotificationOlderText != null) {
            mNotificationOlderText.setAlpha(mAlpha);
        }
        if (mClearAllButton != null) {
            mClearAllButton.setOnClickListener(
                    v -> mNotificationItemController.clearAllNotifications());
            mClearAllButton.setAlpha(mAlpha);
        }
    }

    /**
     * Sets up {@link CarNotificationOlderViewHolder}'s text and clear button functionality
     *
     * @param containsNotification {@code true} if notifications exist under this header
     * @param showClearAllButton {@code true} if clear button should be visible
     */
    @CallSuper
    public void bind(boolean containsNotification, boolean showClearAllButton) {
        if (containsNotification && mShowOlder) {
            mNotificationOlderText.setVisibility(View.VISIBLE);

            if (mClearAllButton == null) {
                return;
            }

            if (showClearAllButton) {
                mClearAllButton.setVisibility(View.VISIBLE);
            } else {
                mClearAllButton.setVisibility(View.GONE);
            }
            return;
        }

        mNotificationOlderText.setVisibility(View.GONE);
        if (mClearAllButton != null) {
            mClearAllButton.setVisibility(View.GONE);
        }
    }
}
