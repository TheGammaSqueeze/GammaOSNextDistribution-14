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

import android.annotation.ColorInt;
import android.annotation.Nullable;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.car.notification.R;

/**
 * Represents a button that can have some text and a drawable.
 */
public class CarNotificationActionButton extends LinearLayout {
    private final ImageView mImageView;
    private final TextView mTextView;
    private final boolean mUseIconsInActionButton;
    @ColorInt
    private final int mDefaultTextColor;

    public CarNotificationActionButton(Context context) {
        this(context, /* attrs= */ null);
    }

    public CarNotificationActionButton(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, /* defStyleAttr= */ 0);
    }

    public CarNotificationActionButton(Context context, @Nullable AttributeSet attrs,
            int defStyleAttr) {
        this(context, attrs, defStyleAttr, /* defStyleRes= */ 0);
    }

    public CarNotificationActionButton(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        inflate(context, R.layout.car_notification_action_button, /* root= */ this);
        mTextView = findViewById(R.id.car_action_button_text);
        mImageView = findViewById(R.id.car_action_button_icon);
        mUseIconsInActionButton =
                context.getResources().getBoolean(R.bool.config_showIconsInActionButtons);

        if (attrs != null) {
            TypedArray attributes =
                    context.obtainStyledAttributes(attrs, R.styleable.CarNotificationActionButton);
            int color = attributes.getColor(
                    R.styleable.CarNotificationActionButton_textColor, /* defaultValue= */
                    context.getResources().getColor(R.color.notification_accent_color));
            attributes.recycle();
            mDefaultTextColor = color;
        } else {
            mDefaultTextColor = context.getResources().getColor(R.color.notification_accent_color);
        }
        mTextView.setTextColor(mDefaultTextColor);
    }

    /**
     * Set text for button.
     *
     * If text is null, then {@link TextView} is hidden.
     */
    public void setText(String text) {
        mTextView.setText(text);
        if (text == null) {
            mTextView.setVisibility(View.GONE);
        } else {
            mTextView.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Set text color for button.
     */
    public void setTextColor(@ColorInt int color) {
        mTextView.setTextColor(color);
    }

    /**
     * @return default text color defined by
     * {@code R.styleable.CarNotificationActionButton_textColor}
     */
    @ColorInt
    public int getDefaultTextColor() {
        return mDefaultTextColor;
    }

    /**
     * @return text that has been set for button.
     */
    public CharSequence getText() {
        return mTextView.getText();
    }

    /**
     * Set drawable for button.
     *
     * If drawable is null, then {@link ImageView} is hidden.
     */
    public void setImageDrawable(Drawable drawable) {
        if (!mUseIconsInActionButton) {
            mImageView.setVisibility(View.GONE);
            return;
        }

        mImageView.setImageDrawable(drawable);
        if (drawable == null) {
            mImageView.setVisibility(View.GONE);
        } else {
            mImageView.setVisibility(View.VISIBLE);
        }
    }

    /**
     * @return drawable that has been set for button.
     */
    public Drawable getDrawable() {
        return mImageView.getDrawable();
    }
}
