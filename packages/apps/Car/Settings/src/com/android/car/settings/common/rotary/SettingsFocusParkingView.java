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

package com.android.car.settings.common.rotary;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

/**
 * A transparent {@link View} that can take focus. It is used as a view that can invisibly
 * take rotary focus when another view is unable to take this focus.
 *
 * Adapted from {@link com.android.car.ui.FocusParkingView} for finer control over focus in
 * dual-pane layouts. This separate class is also needed to allow a second "FocusParkingView"
 * element within an activity, since only one {@link com.android.car.ui.FocusParkingView} is
 * allowed per activity.
 */
public class SettingsFocusParkingView extends View {
    public SettingsFocusParkingView(Context context) {
        super(context);
        init();
    }

    public SettingsFocusParkingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SettingsFocusParkingView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public SettingsFocusParkingView(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init() {
        // This view is focusable, visible and enabled so it can take focus.
        setFocusable(View.FOCUSABLE);
        setVisibility(VISIBLE);
        setEnabled(true);

        // This view is not clickable so it won't affect the app's behavior when the user clicks on
        // it by accident.
        setClickable(false);

        // This view is always transparent.
        setAlpha(0f);

        // Prevent Android from drawing the default focus highlight for this view when it's focused.
        setDefaultFocusHighlightEnabled(false);
    }
}
