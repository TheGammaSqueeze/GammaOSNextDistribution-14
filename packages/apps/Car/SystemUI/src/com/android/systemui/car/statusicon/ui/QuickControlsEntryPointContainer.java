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

package com.android.systemui.car.statusicon.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.LinearLayout;

import com.android.systemui.R;

/**
 * Container for the quick controls entry point.
 *
 * Allows for the location where the quick controls panel will be displayed to be specified via the
 * {@link R.styleable.QuickControlsEntryPointContainer_showAsDropDown} attribute and the
 * {@link R.styleable.QuickControlsEntryPointContainer_panelGravity} attribute.
 */

public class QuickControlsEntryPointContainer extends LinearLayout {
    private final boolean mShowAsDropDown;
    private final int mPanelGravity;

    public QuickControlsEntryPointContainer(Context context) {
        this(context, /* attrs= */ null);
    }

    public QuickControlsEntryPointContainer(Context context, AttributeSet attrs) {
        this(context, attrs, /* defStyleAttr= */ 0);
    }

    public QuickControlsEntryPointContainer(Context context, AttributeSet attrs,
            int defStyleAttr) {
        this(context, attrs, defStyleAttr, /* defStyleRes= */ 0);
    }

    public QuickControlsEntryPointContainer(Context context, AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        TypedArray typedArray = context.obtainStyledAttributes(attrs,
                R.styleable.QuickControlsEntryPointContainer);
        mShowAsDropDown = typedArray.getBoolean(
                R.styleable.QuickControlsEntryPointContainer_showAsDropDown, /* defValue= */ true);
        mPanelGravity = typedArray.getInt(
                R.styleable.QuickControlsEntryPointContainer_panelGravity,
                /* defValue= */ Gravity.TOP | Gravity.START);
        typedArray.recycle();
    }

    /**
     * <p>Indicates whether the panel associated with this entry point container will be displayed
     * anchored to the corner of the anchor view<p/>
     *
     * @return true if the panel will be displayed anchored to the corner of the anchor view,
     *        false if the panel will be displayed at the specified location.
     */
    public boolean showAsDropDown() {
        return mShowAsDropDown;
    }

    /**
     * Determine the gravity value for the panel associated with this entry point container.
     *
     * @return gravity value to use for the panel.
     */
    public int getPanelGravity() {
        return mPanelGravity;
    }
}
