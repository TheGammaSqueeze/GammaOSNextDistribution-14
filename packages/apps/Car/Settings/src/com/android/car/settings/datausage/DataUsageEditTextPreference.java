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

package com.android.car.settings.datausage;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.EditText;

import androidx.preference.PreferenceViewHolder;

import com.android.car.settings.R;
import com.android.car.ui.preference.CarUiPreference;

/** CarUiPreference that shows an edit text with numberDecimal input type */
public class DataUsageEditTextPreference extends CarUiPreference {

    private String mTextToSet;
    private EditText mEditText;

    public DataUsageEditTextPreference(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    public DataUsageEditTextPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public DataUsageEditTextPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DataUsageEditTextPreference(Context context) {
        super(context);
        init();
    }

    private void init() {
        setLayoutResource(R.layout.data_usage_edit_text_preference);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder view) {
        super.onBindViewHolder(view);

        mEditText = (EditText) view.findViewById(R.id.data_usage_edit_text);

        if (mTextToSet != null) {
            mEditText.setText(mTextToSet);
            mTextToSet = null;
        }
    }

    /** Sets the EditText text */
    public void setText(String textToSet) {
        mTextToSet = textToSet;
        notifyChanged();
    }

    /** Returns the current the EditText text */
    public String getText() {
        return mEditText.getText().toString();
    }
}
