/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.annotation.ColorInt;
import android.app.Notification;
import android.os.Bundle;
import android.view.View;

import androidx.cardview.widget.CardView;

import com.android.car.notification.AlertEntry;
import com.android.car.notification.NotificationClickHandlerFactory;
import com.android.car.notification.R;

/**
 * Notification view template that displays a car emergency notification.
 */
public class EmergencyNotificationViewHolder extends CarNotificationBaseViewHolder {
    private final CardView mCardView;
    private final CarNotificationHeaderView mHeaderView;
    private final CarNotificationActionsView mActionsView;
    private final CarNotificationBodyView mBodyView;
    @ColorInt
    private final int mEmergencyBackgroundColor;
    @ColorInt
    private final int mEmergencyPrimaryColor;
    @ColorInt
    private final int mEmergencySecondaryColor;

    private NotificationClickHandlerFactory mClickHandlerFactory;

    public EmergencyNotificationViewHolder(View view,
            NotificationClickHandlerFactory clickHandlerFactory) {
        super(view, clickHandlerFactory);
        mCardView = view.findViewById(R.id.card_view);
        mHeaderView = view.findViewById(R.id.notification_header);
        mBodyView = view.findViewById(R.id.notification_body);
        mActionsView = view.findViewById(R.id.notification_actions);
        mEmergencyBackgroundColor = view.getContext().getColor(R.color.emergency_background_color);
        mEmergencyPrimaryColor = view.getContext().getColor(R.color.emergency_primary_text_color);
        mEmergencySecondaryColor = view.getContext().getColor(
                R.color.emergency_secondary_text_color);
        mClickHandlerFactory = clickHandlerFactory;
    }

    @Override
    boolean hasCustomBackgroundColor() {
        return true;
    }

    /**
     * Binds a {@link AlertEntry} to a car emergency notification template.
     */
    @Override
    public void bind(AlertEntry alertEntry, boolean isInGroup,
            boolean isHeadsUp, boolean isSeen) {
        super.bind(alertEntry, isInGroup, isHeadsUp, isSeen);

        Notification notification = alertEntry.getNotification();

        mCardView.setCardBackgroundColor(mEmergencyBackgroundColor);
        mHeaderView.bind(alertEntry, isInGroup);
        mActionsView.bind(mClickHandlerFactory, alertEntry);

        Bundle extraData = notification.extras;
        CharSequence title = extraData.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence text = extraData.getCharSequence(Notification.EXTRA_TEXT);

        mBodyView.bind(title, text,
                alertEntry.getStatusBarNotification(),
                notification.getLargeIcon(), /* titleIcon= */ null, /* countText= */ null,
                notification.showsTime() ? notification.when : null);
        mBodyView.setPrimaryTextColor(mEmergencyPrimaryColor);
        mBodyView.setSecondaryTextColor(mEmergencySecondaryColor);
    }
}
