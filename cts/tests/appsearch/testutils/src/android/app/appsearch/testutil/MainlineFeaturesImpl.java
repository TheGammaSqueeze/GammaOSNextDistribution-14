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

package android.app.appsearch.testutil;

import android.annotation.NonNull;
import android.app.appsearch.Features;
import android.os.Build;

/**
 * An implementation of {@link Features}. It returns true for most of the features, as all features
 * should be ready in the AppSearch platform backend. However, some features are disabled manually
 * because we have chosen to only land them after a specific Android version.
 * @hide
 */
public class MainlineFeaturesImpl implements Features {

    @Override
    public boolean isFeatureSupported(@NonNull String feature) {
        switch (feature) {
            // Features supported on all devices to which we ship.
            case Features.ADD_PERMISSIONS_AND_GET_VISIBILITY:
                // fall through
            case Features.GLOBAL_SEARCH_SESSION_GET_SCHEMA:
                // fall through
            case Features.GLOBAL_SEARCH_SESSION_GET_BY_ID:
                // fall through
            case Features.GLOBAL_SEARCH_SESSION_REGISTER_OBSERVER_CALLBACK:
                // fall through
            case Features.SEARCH_RESULT_MATCH_INFO_SUBMATCH:
                // fall through
            case Features.JOIN_SPEC_AND_QUALIFIED_ID:
                // fall through
            case Features.LIST_FILTER_QUERY_LANGUAGE:
                // fall through
            case Features.NUMERIC_SEARCH:
                // fall through
            case Features.SEARCH_SPEC_ADVANCED_RANKING_EXPRESSION:
                // fall through
            case Features.SEARCH_SPEC_PROPERTY_WEIGHTS:
                // fall through
            case Features.SEARCH_SUGGESTION:
                // fall through
            case Features.TOKENIZER_TYPE_RFC822:
                // fall through
            case Features.VERBATIM_SEARCH:
                // fall through
            case Features.SEARCH_SPEC_GROUPING_TYPE_PER_SCHEMA:
                // fall through
            case Features.SCHEMA_SET_DELETION_PROPAGATION:
                // fall through
            case Features.SCHEMA_ADD_INDEXABLE_NESTED_PROPERTIES:
                // fall through
            case Features.SEARCH_SPEC_ADD_FILTER_PROPERTIES:
                // fall through
            case Features.LIST_FILTER_HAS_PROPERTY_FUNCTION:
                // fall through
            case Features.SEARCH_SPEC_SET_SEARCH_SOURCE_LOG_TAG:
                // fall through
            case Features.SET_SCHEMA_REQUEST_SET_PUBLICLY_VISIBLE:
                // fall through
            case Features.SET_SCHEMA_REQUEST_ADD_SCHEMA_TYPE_VISIBLE_TO_CONFIG:
                return true;

            // Features which are supported on U+ devices only.
            case Features.SET_SCHEMA_CIRCULAR_REFERENCES:
                // fall through
            case Features.SCHEMA_ADD_PARENT_TYPE:
                return Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE;

            default:
                return false;
        }
    }

    @Override
    public int getMaxIndexedProperties() {
        return 64;
    }
}
