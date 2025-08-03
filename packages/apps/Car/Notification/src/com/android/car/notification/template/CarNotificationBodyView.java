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
import android.annotation.Nullable;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.DateTimeView;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.VisibleForTesting;

import com.android.car.notification.NotificationUtils;
import com.android.car.notification.R;

/**
 * Common notification body that consists of a title line, a content text line, and an image icon on
 * the end.
 *
 * <p> For example, for a messaging notification, the title is the sender's name,
 * the content is the message, and the image icon is the sender's avatar.
 */
public class CarNotificationBodyView extends RelativeLayout {
    private static final String TAG = "CarNotificationBodyView";
    private static final int DEFAULT_MAX_LINES = 3;
    @ColorInt
    private final int mDefaultPrimaryTextColor;
    @ColorInt
    private final int mDefaultSecondaryTextColor;
    private final boolean mDefaultUseLauncherIcon;

    /**
     * Key that system apps can add to the Notification extras to override the default
     * {@link R.bool.config_useLauncherIcon} behavior. If this is set to false, a small and a large
     * icon should be specified to be shown properly in the relevant default configuration.
     */
    @VisibleForTesting
    static final String EXTRA_USE_LAUNCHER_ICON =
            "com.android.car.notification.EXTRA_USE_LAUNCHER_ICON";

    private boolean mIsHeadsUp;
    private boolean mShowBigIcon;
    private int mMaxLines;
    @Nullable
    private TextView mTitleView;
    @Nullable
    private TextView mContentView;
    @Nullable
    private ImageView mLargeIconView;
    @Nullable
    private TextView mCountView;
    @Nullable
    private DateTimeView mTimeView;
    @Nullable
    private ImageView mTitleIconView;

    public CarNotificationBodyView(Context context) {
        super(context);
        init(/* attrs= */ null);
    }

    public CarNotificationBodyView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public CarNotificationBodyView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    public CarNotificationBodyView(
            Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(attrs);
    }

    {
        mDefaultPrimaryTextColor = getContext().getColor(R.color.primary_text_color);
        mDefaultSecondaryTextColor = getContext().getColor(R.color.secondary_text_color);
        mDefaultUseLauncherIcon = getResources().getBoolean(R.bool.config_useLauncherIcon);
    }

    private void init(AttributeSet attrs) {
        if (attrs != null) {
            TypedArray attributes =
                    getContext().obtainStyledAttributes(attrs, R.styleable.CarNotificationBodyView);
            mShowBigIcon =
                    attributes.getBoolean(R.styleable.CarNotificationBodyView_showBigIcon,
                            /* defValue= */ false);
            mMaxLines = attributes.getInteger(R.styleable.CarNotificationBodyView_maxLines,
                    /* defValue= */ DEFAULT_MAX_LINES);
            mIsHeadsUp =
                    attributes.getBoolean(R.styleable.CarNotificationBodyView_isHeadsUp,
                            /* defValue= */ false);
            attributes.recycle();
        }

        inflate(getContext(), mIsHeadsUp ? R.layout.car_headsup_notification_body_view
                : R.layout.car_notification_body_view, /* root= */ this);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mTitleView = findViewById(R.id.notification_body_title);
        mTitleIconView = findViewById(R.id.notification_body_title_icon);
        mContentView = findViewById(R.id.notification_body_content);
        mLargeIconView = findViewById(R.id.notification_body_icon);
        mCountView = findViewById(R.id.message_count);
        mTimeView = findViewById(R.id.time);
        if (mTimeView != null) {
            mTimeView.setShowRelativeTime(true);
        }
    }

    /**
     * Binds the notification body.
     *
     * @param title     the primary text
     * @param content   the secondary text, if this is null then content view will be hidden
     * @param launcherIcon  the launcher icon drawable for notification's package.
     *        If this and largeIcon are null then large icon view will be hidden.
     * @param largeIcon the large icon, usually used for avatars.
     *        If this and launcherIcon are null then large icon view will be hidden.
     * @param countText text signifying the number of messages inside this notification
     * @param when      wall clock time in milliseconds for the notification
     */
    public void bind(CharSequence title, @Nullable CharSequence content,
            StatusBarNotification sbn, @Nullable Icon largeIcon, @Nullable Drawable titleIcon,
            @Nullable CharSequence countText, @Nullable Long when) {
        setVisibility(View.VISIBLE);

        boolean useLauncherIcon = setUseLauncherIcon(sbn);
        Drawable launcherIcon = loadAppLauncherIcon(sbn);
        if (mLargeIconView != null) {
            if (useLauncherIcon && launcherIcon != null) {
                mLargeIconView.setVisibility(View.VISIBLE);
                mLargeIconView.setImageDrawable(launcherIcon);
            } else if (!useLauncherIcon && (mShowBigIcon || mDefaultUseLauncherIcon)) {
                if (largeIcon != null) {
                    largeIcon.loadDrawableAsync(getContext(), drawable -> {
                        mLargeIconView.setVisibility(View.VISIBLE);
                        mLargeIconView.setImageDrawable(drawable);
                    }, Handler.createAsync(Looper.myLooper()));
                } else {
                    Log.w(TAG, "Notification with title=" + title
                            + " did not specify a large icon");
                }
            } else {
                mLargeIconView.setVisibility(View.GONE);
            }
        }

        if (mTitleView != null) {
            mTitleView.setVisibility(View.VISIBLE);
            mTitleView.setText(title);
        }

        if (mTitleIconView != null && titleIcon != null) {
            mTitleIconView.setVisibility(View.VISIBLE);
            mTitleIconView.setImageDrawable(titleIcon);
        }

        if (mContentView != null) {
            if (!TextUtils.isEmpty(content)) {
                mContentView.setVisibility(View.VISIBLE);
                mContentView.setMaxLines(mMaxLines);
                mContentView.setText(content);
            } else {
                mContentView.setVisibility(View.GONE);
            }
        }

        // optional field: time
        if (mTimeView != null) {
            if (when != null && !mIsHeadsUp) {
                mTimeView.setVisibility(View.VISIBLE);
                mTimeView.setTime(when);
            } else {
                mTimeView.setVisibility(View.GONE);
            }
        }

        if (mCountView != null) {
            if (countText != null) {
                mCountView.setVisibility(View.VISIBLE);
                mCountView.setText(countText);
            } else {
                mCountView.setVisibility(View.GONE);
            }
        }
    }

    /**
     * Sets the primary text color.
     */
    public void setSecondaryTextColor(@ColorInt int color) {
        if (mContentView != null) {
            mContentView.setTextColor(color);
        }
    }

    /**
     * Sets max lines for the content view.
     */
    public void setContentMaxLines(int maxLines) {
        if (mContentView != null) {
            mContentView.setMaxLines(maxLines);
        }
    }

    /**
     * Sets the secondary text color.
     */
    public void setPrimaryTextColor(@ColorInt int color) {
        if (mTitleView != null) {
            mTitleView.setTextColor(color);
        }
    }

    /**
     * Sets the text color for the count field.
     */
    public void setCountTextColor(@ColorInt int color) {
        if (mCountView != null) {
            mCountView.setTextColor(color);
        }
    }

    /**
     * Sets the alpha for the count field.
     */
    public void setCountTextAlpha(float alpha) {
        if (mCountView != null) {
            mCountView.setAlpha(alpha);
        }
    }

    /**
     * Sets the {@link OnClickListener} for the count field.
     */
    public void setCountOnClickListener(@Nullable OnClickListener listener) {
        if (mCountView != null) {
            mCountView.setOnClickListener(listener);
        }
    }

    /**
     * Sets the text color for the time field.
     */
    public void setTimeTextColor(@ColorInt int color) {
        if (mTimeView != null) {
            mTimeView.setTextColor(color);
        }
    }

    /**
     * Resets the notification actions empty for recycling.
     */
    public void reset() {
        setVisibility(View.GONE);
        if (mTitleView != null) {
            mTitleView.setVisibility(View.GONE);
        }
        if (mTitleIconView != null) {
            mTitleIconView.setVisibility(View.GONE);
        }
        if (mContentView != null) {
            setContentMaxLines(mMaxLines);
            mContentView.setVisibility(View.GONE);
        }
        if (mLargeIconView != null) {
            mLargeIconView.setVisibility(View.GONE);
        }
        setPrimaryTextColor(mDefaultPrimaryTextColor);
        setSecondaryTextColor(mDefaultSecondaryTextColor);
        if (mTimeView != null) {
            mTimeView.setVisibility(View.GONE);
            mTimeView.setTime(0);
            setTimeTextColor(mDefaultPrimaryTextColor);
        }

        if (mCountView != null) {
            mCountView.setVisibility(View.GONE);
            mCountView.setText(null);
            mCountView.setTextColor(mDefaultPrimaryTextColor);
        }
    }

    /**
     * Returns true if the launcher icon should be used for a given notification.
     */
    private boolean setUseLauncherIcon(StatusBarNotification sbn) {
        Bundle notificationExtras = sbn.getNotification().extras;
        if (notificationExtras == null) {
            return getContext().getResources().getBoolean(R.bool.config_useLauncherIcon);
        }

        if (notificationExtras.containsKey(EXTRA_USE_LAUNCHER_ICON)
                && NotificationUtils.isSystemApp(getContext(), sbn)) {
            return notificationExtras.getBoolean(EXTRA_USE_LAUNCHER_ICON);
        }
        return getContext().getResources().getBoolean(R.bool.config_useLauncherIcon);
    }

    @Nullable
    private Drawable loadAppLauncherIcon(StatusBarNotification sbn) {
        if (!setUseLauncherIcon(sbn)) {
            return null;
        }
        Context packageContext = sbn.getPackageContext(getContext());
        PackageManager pm = packageContext.getPackageManager();
        return pm.getApplicationIcon(packageContext.getApplicationInfo());
    }

    @VisibleForTesting
    TextView getTitleView() {
        return mTitleView;
    }

    @VisibleForTesting
    TextView getContentView() {
        return mContentView;
    }

    @VisibleForTesting
    TextView getCountView() {
        return mCountView;
    }

    @VisibleForTesting
    DateTimeView getTimeView() {
        return mTimeView;
    }
}
