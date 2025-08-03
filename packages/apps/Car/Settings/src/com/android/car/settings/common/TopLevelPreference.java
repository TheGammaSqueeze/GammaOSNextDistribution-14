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
import android.util.AttributeSet;

import com.android.car.settings.R;
import com.android.car.ui.preference.CarUiPreference;

/**
 * Extends {@link CarUiPreference} to customize styling for top-level entries.
 */
public class TopLevelPreference extends CarUiPreference {
    public TopLevelPreference(Context context, AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context);
    }

    public TopLevelPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    public TopLevelPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public TopLevelPreference(Context context) {
        super(context);
        init(context);
    }

    private void init(Context context) {
        setLayoutResource(R.layout.top_level_preference);
        setShowChevron(context.getResources().getBoolean(R.bool.config_top_level_enable_chevrons));
    }
}
