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
import android.util.AttributeSet;

import androidx.preference.Preference;

import com.android.car.settings.R;

/**
 * Non-interactive preference that displays a horizontal divider.
 */
public class DividerPreference extends Preference {
    public DividerPreference(Context context, AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    public DividerPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public DividerPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DividerPreference(Context context) {
        super(context);
        init();
    }

    private void init() {
        setLayoutResource(R.layout.divider_preference);
        setSelectable(false);
    }
}
