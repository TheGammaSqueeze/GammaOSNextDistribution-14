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
package com.android.tv.twopanelsettings;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.ListPreference;

/**
 * A ListPreference that optionally displays a summary (aka subtitle) for
 * each of its options.
 */
public class SummaryListPreference extends ListPreference {

    private CharSequence[] mSummaries;

    public SummaryListPreference(Context context, AttributeSet attrs) {
        super(context, attrs, 0, 0);
    }

    public SummaryListPreference(Context context) {
        super(context, null);
    }

    public void setSummaries(CharSequence[] summaries) {
        this.mSummaries = summaries;
    }

    public CharSequence[] getSummaries() {
        return mSummaries;
    }
}
