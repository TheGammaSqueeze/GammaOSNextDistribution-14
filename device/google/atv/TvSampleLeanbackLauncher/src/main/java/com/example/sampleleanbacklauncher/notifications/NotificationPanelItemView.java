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
 * limitations under the License
 */

package com.example.sampleleanbacklauncher.notifications;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.Layout;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.sampleleanbacklauncher.R;

/**
 * View for a non-dismissible notification displayed in the notifications side panel.
 */
public class NotificationPanelItemView extends LinearLayout
        implements ViewTreeObserver.OnGlobalFocusChangeListener {
    private RectF mProgressBounds;
    private Paint mProgressPaint;
    private Paint mProgressMaxPaint;
    private int mProgressStrokeWidth;
    private int mProgressDiameter;
    private int mProgressPaddingStart;
    private int mProgressPaddingTop;
    private int mProgressColor;
    private int mProgressMaxColor;
    private int mProgress;
    private int mProgressMax;
    private boolean mIsRtl;
    private ImageView mIcon;
    private TextView mTitle;
    private TextView mText;
    private TextView mExpandedText;
    protected View mMainContentText;
    protected String mNotificationKey;
    private TvNotification mNotification;

    public NotificationPanelItemView(Context context) {
        super(context);
        initializeLayoutValues();
    }

    public NotificationPanelItemView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initializeLayoutValues();
    }

    private void initializeLayoutValues() {
        Configuration config = getContext().getResources().getConfiguration();
        mIsRtl = (config.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL);

        Resources res = getResources();
        mProgressStrokeWidth = res.getDimensionPixelSize(
                R.dimen.notification_progress_stroke_width);
        mProgressColor = res.getColor(R.color.notification_progress_stroke_color, null);
        mProgressMaxColor = res.getColor(R.color.notification_progress_stroke_max_color, null);
        mProgressDiameter = res.getDimensionPixelSize(R.dimen.notification_progress_circle_size);
        mProgressPaddingTop = res.getDimensionPixelOffset(
                R.dimen.notification_progress_circle_padding_top);
        mProgressPaddingStart = res.getDimensionPixelOffset(
                R.dimen.notification_progress_circle_padding_start);

        mProgressPaint = new Paint();
        mProgressPaint.setAntiAlias(true);
        mProgressPaint.setStyle(Paint.Style.STROKE);
        mProgressPaint.setColor(mProgressColor);
        mProgressPaint.setStrokeWidth(mProgressStrokeWidth);

        mProgressMaxPaint = new Paint();
        mProgressMaxPaint.setAntiAlias(true);
        mProgressMaxPaint.setStyle(Paint.Style.STROKE);
        mProgressMaxPaint.setColor(mProgressMaxColor);
        mProgressMaxPaint.setStrokeWidth(mProgressStrokeWidth);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnGlobalFocusChangeListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        getViewTreeObserver().removeOnGlobalFocusChangeListener(this);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mIcon = findViewById(R.id.notification_icon);
        mTitle = findViewById(R.id.notification_title);
        mText = findViewById(R.id.notification_text);
        mMainContentText = findViewById(R.id.notification_container);
        mExpandedText = findViewById(R.id.notification_expanded_text);

        mMainContentText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mNotificationKey != null) {
                    NotificationsUtils.openNotification(view.getContext(), mNotificationKey);
                }
            }
        });
    }

    public void setNotification(TvNotification notif) {
        mNotification = notif;
        mNotificationKey = notif.getNotificationKey();
        mTitle.setText(notif.getTitle());
        mText.setText(notif.getText());
        if (!TextUtils.isEmpty(notif.getTitle())) {
            if (!TextUtils.isEmpty(notif.getText())) {
                String formatting = getResources().getString(
                        R.string.notification_content_description_format);
                mMainContentText.setContentDescription(
                        String.format(formatting, notif.getTitle(), notif.getText()));
            } else {
                mMainContentText.setContentDescription(notif.getTitle());
            }
        } else {
            mMainContentText.setContentDescription(notif.getText());
        }
        mExpandedText.setText(notif.getText());
        mIcon.setImageIcon(notif.getSmallIcon());
        setProgress(notif.getProgress(), notif.getProgressMax());
        mMainContentText.setVisibility(VISIBLE);
    }

    public void setProgress(int progress, int progressMax) {
        mProgress = progress;
        mProgressMax = progressMax;
        if (mProgressMax != 0) {
            if (mProgressBounds == null) {
                mProgressBounds = new RectF();
            }
            setWillNotDraw(false);
        } else {
            mProgressBounds = null;
            setWillNotDraw(true);
        }
        requestLayout();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (mProgressBounds != null) {
            int left, right;
            int top = mProgressPaddingTop;
            int bottom = top + mProgressDiameter;
            if (mIsRtl) {
                right = r - mProgressPaddingStart;
                left = right - mProgressDiameter;
            } else {
                left = mProgressPaddingStart;
                right = left + mProgressDiameter;
            }

            mProgressBounds.set(left, top, right, bottom);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mProgressMax != 0) {
            float sweepAngle = 360f * mProgress / mProgressMax;
            if (mIsRtl) {
                canvas.drawArc(mProgressBounds, -90, -sweepAngle, false, mProgressPaint);
                canvas.drawArc(mProgressBounds, -90, 360 - sweepAngle, false,
                        mProgressMaxPaint);
            } else {
                canvas.drawArc(mProgressBounds, -90, sweepAngle, false, mProgressPaint);
                canvas.drawArc(mProgressBounds, sweepAngle - 90, 360 - sweepAngle, false,
                        mProgressMaxPaint);
            }
        }
    }

    private boolean isContentTextCutOff() {
        Layout layout = mText.getLayout();
        if (layout != null) {
            int lines = layout.getLineCount();
            if (lines > 0) {
                int ellipsisCount = layout.getEllipsisCount(lines - 1);
                if (ellipsisCount > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    protected void expandText() {
        mText.setVisibility(GONE);
        mTitle.setMaxLines(2);
        mExpandedText.setVisibility(VISIBLE);
        setBackgroundColor(
                getResources().getColor(R.color.notification_expanded_text_background));
    }

    protected void collapseText() {
        mExpandedText.setVisibility(GONE);
        mTitle.setMaxLines(1);
        mText.setVisibility(VISIBLE);
        setBackgroundColor(Color.TRANSPARENT);
    }

    @Override
    public void onGlobalFocusChanged(View oldFocus, View newFocus) {
        View currentFocus = getFocusedChild();
        if (currentFocus == null) {
            collapseText();
        } else if ((newFocus == currentFocus || newFocus.getParent() == currentFocus)
                && isContentTextCutOff()) {
            expandText();
        }
    }
}
