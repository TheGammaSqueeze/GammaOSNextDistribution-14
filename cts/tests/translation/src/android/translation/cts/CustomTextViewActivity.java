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

package android.translation.cts;

import android.app.Activity;
import android.os.Bundle;
import android.translation.cts.views.CustomTextView;
import android.translation.cts.views.ResponseNotSetTextView;
import android.view.autofill.AutofillId;

import java.util.ArrayList;
import java.util.List;

/**
 * A simple activity that contains some custom TextViews used for translation testing.
 */
public class CustomTextViewActivity extends Activity {

    public static final String ACTIVITY_PACKAGE = "android.translation.cts";
    public static final String ID_RESPONSE_NOT_SET_TEXT = "response_not_set_textview";
    public static final String ID_CUSTOM_TEXT = "custom_textview";

    private static final String TAG = "CustomTextViewActivity";

    private ResponseNotSetTextView mResponseNotSetText;
    private CustomTextView mCustomText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.custom_textview_activity);

        mResponseNotSetText = findViewById(R.id.response_not_set_textview);
        mCustomText = findViewById(R.id.custom_textview);
    }

    List<AutofillId> getViewsForTranslation() {
        final List<AutofillId> views = new ArrayList<>();
        views.add(mResponseNotSetText.getAutofillId());
        views.add(mCustomText.getAutofillId());
        return views;
    }

    ResponseNotSetTextView getResponseNotSetText() {
        return mResponseNotSetText;
    }

    CustomTextView getCustomText() {
        return mCustomText;
    }
}
