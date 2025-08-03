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

package com.android.car.settings.common;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.preference.Preference;

import com.android.car.settings.R;
import com.android.car.ui.uxr.DrawableStateToggleButton;

import java.util.function.Consumer;

/**
 * Handles ToggleButton action item logic
 */
public final class ToggleButtonActionItem extends BaseActionItem {
    private boolean mIsChecked = true;
    @Nullable
    private Consumer<Boolean> mOnClickListener;
    @Nullable
    private Consumer<Preference> mOnClickWhileDisabledListener;

    @Nullable
    private Drawable mDrawable;

    @Nullable
    private String mContentDescription;

    public ToggleButtonActionItem(ActionItemInfoChangeListener actionItemInfoChangeListener) {
        super(actionItemInfoChangeListener);
    }

    /**
     * Create and setup views.
     *
     * @param frameLayout ViewGroup to attach views to
     */
    @Override
    public void bindViewHolder(FrameLayout frameLayout) {
        // Required to be effectively final for inner class access
        final DrawableStateToggleButton toggleButton = getOptionalToggleButton(frameLayout);
        toggleButton.setOnClickListener(null);
        // Prevent "double calls" when checked status is changed
        toggleButton.setOnCheckedChangeListener(null);
        toggleButton.setButtonDrawable(mDrawable);
        toggleButton.setChecked(mIsChecked);
        toggleButton.setEnabled(isEnabled());
        toggleButton.setAllowClickWhenDisabled(true);
        toggleButton.setContentDescription(mContentDescription);
        toggleButton.setOnCheckedChangeListener((view, isChecked) -> {
            onClick();
            toggleButton.setChecked(mIsChecked);
        });
    }

    private DrawableStateToggleButton getOptionalToggleButton(FrameLayout frameLayout) {
        DrawableStateToggleButton toggleButton =
                frameLayout.findViewById(R.id.multi_action_preference_toggle_button);

        if (toggleButton == null) {
            toggleButton = createView(frameLayout.getContext(), frameLayout);
            // Ensure the default "On" and "Off" text don't show
            toggleButton.setText(null);
            toggleButton.setTextOn(null);
            toggleButton.setTextOff(null);
            frameLayout.addView(toggleButton);
        }
        return toggleButton;
    }

    @Override
    public int getLayoutResource() {
        return R.layout.multi_action_preference_toggle_button;
    }

    /**
     * Set the checked state.
     */
    public boolean isChecked() {
        return mIsChecked;
    }

    /**
     * Get the checked state.
     */
    public void setChecked(boolean checked) {
        if (checked != mIsChecked) {
            mIsChecked = checked;
            update();
        }
    }

    /**
     * Get the Consumer that should run when toggle button is clicked.
     */
    public Consumer<Boolean> getOnClickListener() {
        return mOnClickListener;
    }

    /**
     * Set the Consumer that should run when toggle button is clicked.
     */
    public void setOnClickListener(Consumer<Boolean> onClickListener) {
        if (onClickListener != mOnClickListener) {
            mOnClickListener = onClickListener;
            update();
        }
    }

    /**
     * Set the Consumer that should run when toggle button is clicked even when disabled.
     */
    public void setOnClickWhileDisabledListener(Consumer<Preference> listener) {
        if (listener != mOnClickWhileDisabledListener) {
            mOnClickWhileDisabledListener = listener;
            update();
        }
    }

    /**
     * Get the ToggleButton drawable.
     */
    public Drawable getDrawable() {
        return mDrawable;
    }

    /**
     * Set the ToggleButton drawable.
     */
    public void setDrawable(Drawable drawable) {
        if (drawable != mDrawable) {
            mDrawable = drawable;
            update();
        }
    }

    /**
     * Set the ToggleButton drawable.
     */
    public void setDrawable(Context context, @DrawableRes int iconResId) {
        Drawable drawable = context.getDrawable(iconResId);

        if (drawable != mDrawable) {
            mDrawable = drawable;
            update();
        }
    }

    /**
     * Get the ToggleButton content description.
     */
    @Nullable
    public String getContentDescription() {
        return mContentDescription;
    }

    /**
     * Set the ToggleButton content description.
     */
    public void setContentDescription(@Nullable String contentDescription) {
        mContentDescription = contentDescription;
    }

    /**
     * Set the ToggleButton content description.
     */
    public void setContentDescription(@NonNull Context context,
            @StringRes int contentDescriptionResId) {
        mContentDescription = context.getString(contentDescriptionResId);
    }

    /**
     * Executes when ToggleButton is clicked.
     */
    public void onClick() {
        if (mIsRestricted && mPreference != null
                && mRestrictedOnClickListener != null) {
            mRestrictedOnClickListener.accept(mPreference);
        } else if (isEnabled() && !mIsRestricted && mOnClickListener != null) {
            mIsChecked = !mIsChecked;
            mOnClickListener.accept(mIsChecked);
        } else if (!isEnabled() && mOnClickWhileDisabledListener != null) {
            mOnClickWhileDisabledListener.accept(mPreference);
        }
    }

    private DrawableStateToggleButton createView(Context context, ViewGroup viewGroup) {
        LayoutInflater layoutInflater = LayoutInflater.from(context);

        return (DrawableStateToggleButton) layoutInflater
                .inflate(getLayoutResource(), viewGroup, false);
    }
}
