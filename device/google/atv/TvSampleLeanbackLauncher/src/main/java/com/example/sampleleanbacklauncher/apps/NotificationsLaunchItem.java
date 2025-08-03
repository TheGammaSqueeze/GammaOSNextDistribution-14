package com.example.sampleleanbacklauncher.apps;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.util.Log;

import com.example.sampleleanbacklauncher.R;

public class NotificationsLaunchItem extends LaunchItem {
    private static final String ACTION_OPEN_NOTIFICATIONS = "com.android.tv.NOTIFICATIONS_PANEL";

    // Change this to use a notification panel activity from a different package.
    private static final String NOTIFICATIONS_PKG = "com.example.sampleleanbacklauncher";

    private final Context mContext;
    private int mNotifsCount = 0;

    public NotificationsLaunchItem(Context context) {
        super(context, new Intent(ACTION_OPEN_NOTIFICATIONS).setPackage(NOTIFICATIONS_PKG),
                context.getResources().getDrawable(R.drawable.ic_notifications, null),
                context.getResources().getString(R.string.system_notifications));
        mContext = context;
    }

    public void setNotificationsCount(int count) {
        mNotifsCount = count;
    }

    @Override
    public Intent getIntent() {
        return mIntent;
    }

    @Override
    public Drawable getBanner() {
        // No banner for notifications launch item.
        return null;
    }

    @Override
    public CharSequence getLabel() {
        return mContext.getResources().getQuantityString(R.plurals.notifications_title,
                mNotifsCount, mNotifsCount);
    }
}
