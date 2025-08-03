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

import static android.translation.cts.Helper.CUSTOM_TRANSLATION_ID_MY_TAG;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.translation.TranslationRequestValue;
import android.view.translation.ViewTranslationRequest;
import android.view.translation.ViewTranslationResponse;
import android.widget.TextView;

import java.util.function.Consumer;

/**
 * A custom TextView that overrides translation {@link View#onCreateViewTranslationRequest} and
 * {@link View#onViewTranslationResponse} and the {@link ViewTranslationResponse} is set.
 */
public class CustomTextView extends TextView {

    private boolean mMyTagTranslationSupported;

    public CustomTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onCreateViewTranslationRequest(int[] supportedFormats,
            Consumer<ViewTranslationRequest> requestsCollector) {
        mMyTagTranslationSupported = false;
        ViewTranslationRequest.Builder requestBuilder =
                new ViewTranslationRequest.Builder(getAutofillId());
        requestBuilder.setValue(ViewTranslationRequest.ID_TEXT,
                TranslationRequestValue.forText(getText()));
        requestBuilder.setValue(CUSTOM_TRANSLATION_ID_MY_TAG,
                TranslationRequestValue.forText(getText()));
        requestsCollector.accept(requestBuilder.build());
    }

    @Override
    public void onViewTranslationResponse(ViewTranslationResponse response) {
        super.onViewTranslationResponse(response);

        if (response.getKeys().contains(CUSTOM_TRANSLATION_ID_MY_TAG)) {
            mMyTagTranslationSupported = true;
        }
    }

    public boolean isMyTagTranslationSupported() {
        return  mMyTagTranslationSupported;
    }
}
