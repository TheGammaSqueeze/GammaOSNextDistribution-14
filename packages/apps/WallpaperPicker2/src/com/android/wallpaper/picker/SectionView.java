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
package com.android.wallpaper.picker;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

/** The SectionView base for views hosting in the {@link CustomizationPickerFragment}. */
public abstract class SectionView extends LinearLayout {

    /** The callback for the section view updates. */
    public interface SectionViewListener {
        void onViewActivated(@Nullable Context context, boolean viewActivated);
    }

    protected SectionViewListener mSectionViewListener;
    private String mTitle;

    public SectionView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public void setTitle(String title) {
        mTitle = title;
    }

    public String getTitle() {
        return mTitle;
    }

    /** Sets the listener to the {@code SectionView} instance for reacting the view changes. */
    public void setViewListener(SectionViewListener sectionViewListener) {
        mSectionViewListener = sectionViewListener;
    }
}
