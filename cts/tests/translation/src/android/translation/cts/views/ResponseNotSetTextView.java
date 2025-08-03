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

package android.translation.cts.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.translation.ViewTranslationResponse;
import android.widget.TextView;

/**
 * A custom TextView that overrides translation {@link View#onViewTranslationResponse} but doesn't
 * set the {@link ViewTranslationResponse} for TextView, it saves the response locally.
 */
public class ResponseNotSetTextView extends TextView {

    ViewTranslationResponse mMySavedResponse;

    public ResponseNotSetTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onViewTranslationResponse(ViewTranslationResponse response) {
        // The ViewTranslationResponse will not be set for the view.
        mMySavedResponse = response;
    }

    public ViewTranslationResponse getSavedResponse() {
        return mMySavedResponse;
    }
}