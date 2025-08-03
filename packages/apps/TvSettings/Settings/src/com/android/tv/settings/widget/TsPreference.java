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
package com.android.tv.settings.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;

import androidx.preference.Preference;

import com.android.tv.settings.R;

/** Augmented preference for TvSettings **/
public class TsPreference extends Preference {
    private boolean mUpdatableFromGoogleSettings;

    public TsPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        initStyleAttributes(attrs);
    }

    public TsPreference(Context context) {
        super(context);
        initStyleAttributes(null);
    }


    private void initStyleAttributes(AttributeSet attrs) {
        if (attrs != null) {
            final TypedArray a = getContext().obtainStyledAttributes(
                    attrs, R.styleable.TsPreference);
            for (int i = a.getIndexCount() - 1; i >= 0; i--) {
                int attr = a.getIndex(i);
                if (attr == R.styleable.TsPreference_updatableFromGoogleSettings) {
                    mUpdatableFromGoogleSettings = a.getBoolean(attr, false);
                    break;
                }
            }
        }
    }

    public void setUpdatableFromGoogleSettings(boolean updatableFromGoogleSettings) {
        mUpdatableFromGoogleSettings = updatableFromGoogleSettings;
    }

    public boolean updatableFromGoogleSettings() {
        return mUpdatableFromGoogleSettings;
    }
}
