/*
 * Copyright (c) 2017-2021 The LineageOS Project
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 * Also add information on how to contact you by electronic and paper mail.
 *
 */

package org.lineageos.flipflap;

import android.content.Context;
import android.service.notification.StatusBarNotification;
import android.view.View;
import android.widget.LinearLayout;

import java.util.List;

public class IceviewView extends FlipFlapView {
    private static final String TAG = "IceviewView";

    private final ClockPanel mClockPanel;
    private final LinearLayout mNotificationsView;

    public IceviewView(Context context) {
        super(context);

        inflate(context, R.layout.iceview_view, this);

        mClockPanel = findViewById(R.id.clock_panel);
        mClockPanel.bringToFront();

        mNotificationsView = findViewById(R.id.iceview_notifications);
    }

    @Override
    protected boolean supportsAlarmActions() {
        return true;
    }

    @Override
    protected void updateAlarmState(boolean active) {
        setVisibility(active ? View.INVISIBLE : View.VISIBLE);
    }

    @Override
    protected boolean supportsCallActions() {
        return true;
    }

    @Override
    protected void updateCallState(CallState callState) {
        setVisibility(callState.isActive() ? View.INVISIBLE : View.VISIBLE);
    }

    @Override
    protected boolean supportsNotifications() {
        return true;
    }

    @Override
    protected void updateNotifications(List<StatusBarNotification> notifications) {
        mNotificationsView.removeAllViews();
        for (StatusBarNotification sbn : notifications) {
            if (shouldShowNotification(sbn)) {
                IceviewNotificationView inv = (IceviewNotificationView) inflate(getContext(),
                        R.layout.iceview_notification_view, null);
                inv.setNotification(sbn.getNotification());
                mNotificationsView.addView(inv);
            }
        }
    }

    private boolean shouldShowNotification(StatusBarNotification sbn) {
        return !FlipFlapUtils.OUR_PACKAGE_NAME.equals(sbn.getPackageName());
    }
}
