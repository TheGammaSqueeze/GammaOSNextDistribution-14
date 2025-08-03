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

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;

/** Business logic for changing the data warning value. */
public class DataUsageEditTextPreferenceController extends
        PreferenceController<DataUsageEditTextPreference> {

    private String mDefaultText;

    public DataUsageEditTextPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected Class<DataUsageEditTextPreference> getPreferenceType() {
        return DataUsageEditTextPreference.class;
    }

    /** Sets the default text of the edit text */
    public void setDefaultText(String text) {
        mDefaultText = text;
    }

    @Override
    protected void checkInitialized() {
        if (mDefaultText == null) {
            throw new IllegalStateException(
                    "Default text should be set before calling this function");
        }
    }

    @Override
    protected void onCreateInternal() {
        super.onCreateInternal();

        getPreference().setText(mDefaultText);
    }

    /** Returns the current text of the edit text */
    public String getText() {
        return getPreference().getText();
    }
}
