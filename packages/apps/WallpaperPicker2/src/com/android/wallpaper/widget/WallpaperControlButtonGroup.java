/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.wallpaper.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ToggleButton;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;

import com.android.wallpaper.R;

/**
 * Custom layout for a group of wallpaper control buttons.
 */
public final class WallpaperControlButtonGroup extends FrameLayout {

    public static final int DELETE = 0;
    public static final int EDIT = 1;
    public static final int CUSTOMIZE = 2;
    public static final int EFFECTS = 3;
    public static final int INFORMATION = 4;
    public static final int SHARE = 5;
    final int[] mFloatingSheetControlButtonTypes = {CUSTOMIZE, EFFECTS, SHARE, INFORMATION};
    ToggleButton mDeleteButton;
    ToggleButton mEditButton;
    ToggleButton mCustomizeButton;
    ToggleButton mEffectsButton;
    ToggleButton mShareButton;
    ToggleButton mInformationButton;

    /**
     * The default orientation is vertical
     */
    private int mOrientation = LinearLayout.VERTICAL;

    /**
     * Constructor
     */
    public WallpaperControlButtonGroup(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initAttributes(attrs);
        LayoutInflater.from(context).inflate(R.layout.wallpaper_control_button_group, this,
                true);
        LinearLayout buttonGroupContainer = findViewById(R.id.wallpaper_control_container);
        if (mOrientation == LinearLayout.HORIZONTAL) {
            buttonGroupContainer.setOrientation(LinearLayout.HORIZONTAL);
            buttonGroupContainer.setDividerDrawable(context.getDrawable(
                    R.drawable.wallpaper_control_button_group_divider_horizontal));
        } else {
            buttonGroupContainer.setOrientation(LinearLayout.VERTICAL);
            buttonGroupContainer.setDividerDrawable(
                    context.getDrawable(
                            R.drawable.wallpaper_control_button_group_divider_vertical));
        }

        mDeleteButton = findViewById(R.id.delete_button);
        mEditButton = findViewById(R.id.edit_button);
        mCustomizeButton = findViewById(R.id.customize_button);
        mEffectsButton = findViewById(R.id.effects_button);
        mShareButton = findViewById(R.id.share_button);
        mInformationButton = findViewById(R.id.information_button);
    }

    /**
     * @param attrs sets the local attribute properties for [WallpaperControlButtonGroup]
     */
    private void initAttributes(AttributeSet attrs) {
        if (attrs != null) {
            TypedArray typeArray = getContext().obtainStyledAttributes(attrs,
                    R.styleable.WallpaperControlButtonGroup);

            // default to vertical
            mOrientation = typeArray.getInt(R.styleable.WallpaperControlButtonGroup_orientation,
                    LinearLayout.VERTICAL);

            typeArray.recycle();
        }
    }

    /**
     * Show a button by giving a correspondent listener
     */
    public void showButton(@WallpaperControlType int type,
            CompoundButton.OnCheckedChangeListener listener) {
        ToggleButton button = getActionButton(type);
        if (button != null) {
            button.setVisibility(VISIBLE);
            button.setOnCheckedChangeListener(listener);
        }
    }

    private ToggleButton getActionButton(@WallpaperControlType int type) {
        switch (type) {
            case DELETE:
                return mDeleteButton;
            case EDIT:
                return mEditButton;
            case CUSTOMIZE:
                return mCustomizeButton;
            case EFFECTS:
                return mEffectsButton;
            case SHARE:
                return mShareButton;
            case INFORMATION:
                return mInformationButton;
            default:
                return null;
        }
    }

    /**
     * Hide a button
     */
    public void hideButton(@WallpaperControlType int type) {
        getActionButton(type).setVisibility(GONE);
    }

    /**
     * Set checked for a button
     */
    public void setChecked(@WallpaperControlType int type, boolean checked) {
        getActionButton(type).setChecked(checked);
    }

    /**
     * Update the background color in case the context theme has changed.
     */
    public void updateBackgroundColor() {
        Context context = getContext();
        if (context == null) {
            return;
        }
        mDeleteButton.setForeground(null);
        mEditButton.setForeground(null);
        mCustomizeButton.setForeground(null);
        mEffectsButton.setForeground(null);
        mShareButton.setForeground(null);
        mInformationButton.setForeground(null);
        mDeleteButton.setForeground(AppCompatResources.getDrawable(context,
                R.drawable.wallpaper_control_button_delete));
        mEditButton.setForeground(
                AppCompatResources.getDrawable(context, R.drawable.wallpaper_control_button_edit));
        mCustomizeButton.setForeground(AppCompatResources.getDrawable(context,
                R.drawable.wallpaper_control_button_customize));
        mEffectsButton.setForeground(AppCompatResources.getDrawable(context,
                R.drawable.wallpaper_control_button_effect));
        mShareButton.setForeground(AppCompatResources.getDrawable(context,
                R.drawable.wallpaper_control_button_share));
        mInformationButton.setForeground(
                AppCompatResources.getDrawable(context, R.drawable.wallpaper_control_button_info));
    }

    /**
     * Ensures only one toggle button with a floating sheet is selected at a time
     */
    public void deselectOtherFloatingSheetControlButtons(@WallpaperControlType int selectedType) {
        for (int type : mFloatingSheetControlButtonTypes) {
            if (type != selectedType) {
                getActionButton(type).setChecked(false);
            }
        }
    }

    /**
     * Returns true if there is a floating sheet button selected, and false if not
     */
    public boolean isFloatingSheetControlButtonSelected() {
        for (int type : mFloatingSheetControlButtonTypes) {
            if (getActionButton(type).isChecked()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Deselects all floating sheet toggle buttons in the Wallpaper Control Button Group
     */
    public void deselectAllFloatingSheetControlButtons() {
        for (int type : mFloatingSheetControlButtonTypes) {
            getActionButton(type).setChecked(false);
        }
    }

    /**
     * Overlay tab
     */
    @IntDef({DELETE, EDIT, CUSTOMIZE, EFFECTS, SHARE, INFORMATION})
    public @interface WallpaperControlType {
    }
}
