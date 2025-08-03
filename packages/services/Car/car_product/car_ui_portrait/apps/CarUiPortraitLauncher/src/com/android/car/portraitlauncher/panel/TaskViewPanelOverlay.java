/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.car.portraitlauncher.panel;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.android.car.portraitlauncher.R;

/**
 * A view that is shown on top of the task view in {@link TaskViewPanel} to improve visual effects.
 */
public class TaskViewPanelOverlay extends ConstraintLayout {
    private static final String TAG = TaskViewPanelOverlay.class.getSimpleName();

    private ImageView mBackground;
    private ImageView mIcon;
    private ComponentName mComponentName;

    public TaskViewPanelOverlay(@NonNull Context context) {
        this(context, /* attrs= */ null);
    }

    public TaskViewPanelOverlay(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, /* defStyleAttr= */ 0);
    }

    public TaskViewPanelOverlay(@NonNull Context context, @Nullable AttributeSet attrs,
            int defStyleAttr) {
        this(context, attrs, defStyleAttr, /* defStyleRes= */ 0);
    }

    public TaskViewPanelOverlay(@NonNull Context context, @Nullable AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mBackground = findViewById(R.id.task_view_overlay_icon_background);
        mIcon = findViewById(R.id.task_view_overlay_icon);
    }

    /**
     * Sets the visibility of the overlay to visible and set the centered icon state according to
     * {@code withIcon}.
     */
    public void show(boolean withIcon) {
        setVisibility(VISIBLE);
        setIconEnabled(withIcon);
    }

    /** Sets the visibility of the overlay to gone and disable the centered icon. */
    public void hide() {
        setVisibility(GONE);
        setIconEnabled(/* enableIcon= */ false);
    }

    /** Sets {@code componentName} that used to retrieve the centered icon. */
    void setComponentName(ComponentName componentName) {
        mComponentName = componentName;
    }

    /** Refreshes the overlay according to current theme. */
    void refresh() {
        int backgroundColor = getResources().getColor(R.color.car_background, mContext.getTheme());
        setBackgroundColor(backgroundColor);
        Drawable iconBackgroundDrawable = getResources().getDrawable(R.drawable.app_icon_background,
                mContext.getTheme());
        mBackground.setBackground(iconBackgroundDrawable);
    }

    /**
     * Sets the state of the centered icon.
     *
     * @param enabled True if the icon is enabled, false otherwise.
     */
    private void setIconEnabled(boolean enabled) {
        mIcon.setBackground(enabled ? getApplicationIcon(mComponentName) : null);
        mIcon.setVisibility(enabled ? VISIBLE : GONE);
        mBackground.setVisibility(enabled ? VISIBLE : GONE);
    }

    /** Retrieve the icon associated with the given {@code componentName}. */
    private Drawable getApplicationIcon(ComponentName componentName) {
        if (componentName == null) {
            return null;
        }

        Drawable icon = null;
        try {
            icon = mContext.getPackageManager().getApplicationIcon(componentName.getPackageName());
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Fail to get application icon. ", e);
        }
        return icon;
    }
}
