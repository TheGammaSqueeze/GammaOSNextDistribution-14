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
package com.android.customization.picker.themedicon;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Nullable;

import com.android.themepicker.R;
import com.android.wallpaper.picker.SectionView;
import com.google.android.material.materialswitch.MaterialSwitch;

/**
 * The {@link SectionView} for themed icon section view
 */
public class ThemedIconSectionView extends SectionView {

    private MaterialSwitch mSwitchView;

    public ThemedIconSectionView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setTitle(context.getString(R.string.themed_icon_title));
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mSwitchView = findViewById(R.id.themed_icon_toggle);
        setOnClickListener(v -> {
            mSwitchView.toggle();
            if (mSectionViewListener != null) {
                mSectionViewListener.onViewActivated(getContext(), mSwitchView.isChecked());
            }
        });
    }

    /** Gets the switch view. */
    public MaterialSwitch getSwitch() {
        return mSwitchView;
    }
}
