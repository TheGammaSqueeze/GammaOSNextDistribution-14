/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.autofillservice.cts.testcore;


import android.app.assist.AssistStructure;
import android.service.assist.classification.FieldClassification;
import android.service.assist.classification.FieldClassificationResponse;
import android.util.Log;
import android.view.autofill.AutofillId;

import androidx.annotation.NonNull;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

/**
 * Helper class used to produce a
 * {@link android.service.assist.classification.FieldClassificationResponse} based on expected
 * fields that should be present in the {@link AssistStructure}.
 *
 * <p>Typical usage:
 *
 * <pre class="prettyprint">
 * InstrumentedFieldClassificationService.getReplier().addResponse(
 *         new CannedFieldClassificationResponse.Builder()
 *               .addFieldClassification(
 *                         new CannedFieldClassificationResponse.CannedFieldClassification(
 *                                 "resource_id1", Set.of("autofill_hint_1")))
 *               .build());
 * </pre class="prettyprint">
 */
public final class CannedFieldClassificationResponse {
    private static final String TAG = CannedFieldClassificationResponse.class.getSimpleName();
    private Set<CannedFieldClassification> mCannedFieldClassifications = new HashSet<>();

    private CannedFieldClassificationResponse(Builder builder) {
        mCannedFieldClassifications = builder.mClassifications;
    }

    public static final class Builder {
        private Set<CannedFieldClassification> mClassifications = new HashSet<>();

        public Builder addFieldClassification(CannedFieldClassification classification) {
            mClassifications.add(classification);
            return this;
        }

        public CannedFieldClassificationResponse build() {
            return new CannedFieldClassificationResponse(this);
        }
    }

    /**
     * Creates a new response, replacing the dataset field ids by the real ids from the assist
     * structure.
     */
    public FieldClassificationResponse asResponse(
            @NonNull Function<String, AssistStructure.ViewNode> nodeResolver) {
        return asResponseWithAutofillId((id)-> {
            AssistStructure.ViewNode node = nodeResolver.apply(id);
            if (node == null) {
                throw new AssertionError("No node with resource id " + id);
            }
            return node.getAutofillId();
        });
    }

    /**
     * Creates a new response, replacing the dataset field ids by the real ids from the assist
     * structure.
     */
    private FieldClassificationResponse asResponseWithAutofillId(
            @NonNull Function<String, AutofillId> autofillIdResolver) {
        Set<FieldClassification> fieldClassifications = new HashSet<>();
        for (CannedFieldClassification cannedFieldClassification : mCannedFieldClassifications) {
            fieldClassifications.add(
                    cannedFieldClassification.asFieldClassification(autofillIdResolver));
        }
        return new FieldClassificationResponse(fieldClassifications);
    }

    /**
     * Helper class used to produce a {@link FieldClassification} based on expected fields that
     *  should be present in the {@link AssistStructure}.
     *
     * <p>Typical usage:
     *
     * <pre class="prettyprint">
     * InstrumentedFieldClassificationService.getReplier().addResponse(
     *         new CannedFieldClassificationResponse.Builder()
     *               .addFieldClassification(
     *                         new CannedFieldClassificationResponse.CannedFieldClassification(
     *                                 "resource_id1", Set.of("autofill_hint_1")))
     *               .build());
     * </pre class="prettyprint">
     */
    public static class CannedFieldClassification {
        private final Set<String> mHints;
        private final String mId;

        public CannedFieldClassification(String id, Set<String> hints) {
            mId = id;
            mHints = hints;
        }

        /**
         * Creates a new dataset, replacing the field ids by the real ids from the assist structure.
         */
        public FieldClassification asFieldClassificationtWithNodeResolver(
                Function<String, AssistStructure.ViewNode> nodeResolver) {
            return asFieldClassification((id) -> {
                AssistStructure.ViewNode node = nodeResolver.apply(id);
                if (node == null) {
                    throw new AssertionError("No node with resource id " + id);
                }
                return node.getAutofillId();
            });
        }

        public FieldClassification asFieldClassification(
                Function<String, AutofillId> autofillIdResolver) {

            final AutofillId autofillId = autofillIdResolver.apply(mId);
            if (autofillId == null) {
                throw new AssertionError("No node with resource id " + mId);
            }
            FieldClassification response = new FieldClassification(autofillId, mHints);
            Log.v(TAG, "Response: " + response);
            return response;
        }
    }
}
