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
package com.android.customization.picker.mode;

import android.content.Context;
import android.content.res.Configuration;
import android.util.AttributeSet;

import androidx.annotation.Nullable;

import com.android.themepicker.R;
import com.android.wallpaper.picker.SectionView;
import com.google.android.material.materialswitch.MaterialSwitch;

/** The view of section in the customization picker fragment. */
public final class DarkModeSectionView extends SectionView {

    private boolean mIsDarkModeActivated;

    public DarkModeSectionView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setTitle(context.getString(R.string.mode_title));
        mIsDarkModeActivated = (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_YES) != 0;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        MaterialSwitch switchView = findViewById(R.id.dark_mode_toggle);
        switchView.setChecked(mIsDarkModeActivated);
        switchView.setOnCheckedChangeListener((buttonView, isChecked) ->
                switchView.setChecked(mIsDarkModeActivated)
        );
        setOnClickListener(view -> modeToggleClicked());
    }

    private void modeToggleClicked() {
        mIsDarkModeActivated = !mIsDarkModeActivated;
        viewActivated(mIsDarkModeActivated);
    }

    private void viewActivated(boolean isChecked) {
        if (mSectionViewListener != null) {
            mSectionViewListener.onViewActivated(getContext(), isChecked);
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        final int numOfChildViews = getChildCount();
        for (int i = 0; i < numOfChildViews; i++) {
            getChildAt(i).setEnabled(enabled);
        }
    }
}
