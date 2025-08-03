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
import android.util.LongSparseArray;
import android.view.View;
import android.view.autofill.AutofillId;
import android.view.translation.TranslationRequestValue;
import android.view.translation.ViewTranslationRequest;
import android.view.translation.ViewTranslationResponse;
import java.util.function.Consumer;

/**
 * A custom view that contains virtual child.
 */
public class VirtualContainerView extends View {

    private static final String TAG = "VirtualContainerView";

    private LongSparseArray<ViewTranslationResponse> mResponse;

    public VirtualContainerView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onCreateVirtualViewTranslationRequests(long[] virtualIds, int[] supportedFormats,
            Consumer<ViewTranslationRequest> requestsCollector) {
        AutofillId hostViewAutofillId = getAutofillId();
        int virtualIdCount = virtualIds.length;
        for (int i=0; i< virtualIdCount; i++) {
            ViewTranslationRequest.Builder requestBuilder =
                    new ViewTranslationRequest.Builder(hostViewAutofillId, virtualIds[i]);
            ViewTranslationRequest request = requestBuilder.setValue(ViewTranslationRequest.ID_TEXT,
                    TranslationRequestValue.forText("Hello " + i)).build();
            requestsCollector.accept(request);
        }
    }

    @Override
    public void onVirtualViewTranslationResponses(
            LongSparseArray<ViewTranslationResponse> response) {
        mResponse = response;
    }

    public LongSparseArray<ViewTranslationResponse> getViewTranslationResponseForCustomView() {
        return mResponse;
    }
}
