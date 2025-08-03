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

import android.app.Notification;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class IceviewNotificationView extends LinearLayout {
    private static final String TAG = "IceviewNotificationView";

    public IceviewNotificationView(Context context) {
        this(context, null);
    }

    public IceviewNotificationView(Context context, AttributeSet attrSet) {
        super(context, attrSet);
    }

    public void setNotification(Notification notification) {
        Icon largeIcon = notification.getLargeIcon();
        Icon icon = largeIcon != null ? largeIcon : notification.getSmallIcon();
        String titleString = notification.extras.getString(Notification.EXTRA_TITLE);
        String textString = notification.extras.getString(Notification.EXTRA_TEXT);

        ImageView iconView = findViewById(R.id.iceview_notification_icon);
        TextView titleView = findViewById(R.id.iceview_notification_title);
        TextView descView = findViewById(R.id.iceview_notification_description);

        titleView.setText(titleString);
        descView.setText(textString);
        if (icon != null) {
            Drawable drawable = icon.loadDrawable(mContext);
            iconView.setImageDrawable(drawable);
        }
    }
}
