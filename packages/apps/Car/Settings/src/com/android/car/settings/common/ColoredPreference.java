/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.car.settings.common;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.TextView;

import androidx.preference.PreferenceViewHolder;

import com.android.car.settings.R;
import com.android.car.ui.preference.CarUiPreference;
import com.android.settingslib.Utils;

/**
 * Extends {@link CarUiPreference} to customize title and icon colors.
 */
public class ColoredPreference extends CarUiPreference {
    private ColorStateList mTitleColor;
    private int mIconColor;

    public ColoredPreference(Context context, AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs);
    }

    public ColoredPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    public ColoredPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public ColoredPreference(Context context) {
        super(context);
        init(context, /* attrs= */ null);
    }

    private void init(Context context, AttributeSet attrs) {
        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.ColoredPreference);
        mTitleColor = a.getColorStateList(R.styleable.ColoredPreference_titleColor);
        if (mTitleColor == null) {
            mTitleColor = Utils.getColorAttr(context, android.R.attr.textColorPrimary);
        }
        mIconColor = a.getColor(R.styleable.ColoredPreference_iconColor, /* defValue= */ 0);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        TextView title = holder.itemView.findViewById(android.R.id.title);
        if (title != null) {
            title.setTextColor(mTitleColor);
        }
        if (mIconColor != 0) {
            getIcon().setTint(mIconColor);
        }
    }
}
