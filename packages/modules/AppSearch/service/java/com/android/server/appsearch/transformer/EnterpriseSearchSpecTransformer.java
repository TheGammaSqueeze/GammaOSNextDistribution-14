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

package com.android.server.appsearch.transformer;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.appsearch.JoinSpec;
import android.app.appsearch.SearchSpec;

import com.android.server.appsearch.contactsindexer.appsearchtypes.Person;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Transforms {@link SearchSpec} to restrict the set of properties (by schema type) visible to
 * enterprise access.
 */
// TODO(b/314974468): projection/filter transformation should only apply to our contacts corpus;
//  currently it applies to all Person types
public final class EnterpriseSearchSpecTransformer {

    private EnterpriseSearchSpecTransformer() {
    }

    /**
     * Transforms a {@link SearchSpec}, adding property filters and projections that restrict the
     * allowed properties for certain schema types when accessed through an enterprise session.
     * <p>
     * Currently, we only add filters and projections for {@link Person} schema type.
     */
    @NonNull
    public static SearchSpec transformSearchSpec(@NonNull SearchSpec searchSpec) {
        Objects.requireNonNull(searchSpec);
        boolean shouldTransformSearchSpecFilters = shouldTransformSearchSpecFilters(searchSpec);
        boolean shouldTransformJoinSpecFilters = shouldTransformJoinSpecFilters(
                searchSpec.getJoinSpec());
        if (!shouldTransformSearchSpecFilters && !shouldTransformJoinSpecFilters) {
            return searchSpec;
        }
        SearchSpec.Builder builder = new SearchSpec.Builder(searchSpec);
        if (shouldTransformSearchSpecFilters) {
            PersonEnterpriseTransformer.transformSearchSpec(searchSpec, builder);
        }
        if (shouldTransformJoinSpecFilters) {
            JoinSpec joinSpec = searchSpec.getJoinSpec();
            JoinSpec.Builder joinSpecBuilder = new JoinSpec.Builder(joinSpec);
            joinSpecBuilder.setNestedSearch(joinSpec.getNestedQuery(),
                    transformSearchSpec(joinSpec.getNestedSearchSpec()));
            builder.setJoinSpec(joinSpecBuilder.build());
        }
        return builder.build();
    }

    private static boolean shouldTransformSearchSpecFilters(@NonNull SearchSpec searchSpec) {
        List<String> filteredSchemas = searchSpec.getFilterSchemas();
        return filteredSchemas.isEmpty() || filteredSchemas.contains(Person.SCHEMA_TYPE);
    }

    private static boolean shouldTransformJoinSpecFilters(@Nullable JoinSpec joinSpec) {
        return joinSpec != null && shouldTransformSearchSpecFilters(joinSpec.getNestedSearchSpec());
    }

    /**
     * Called by {@link com.android.server.appsearch.AppSearchManagerService.Stub#getDocuments} to
     * specify the projection properties for Enterprise schema types.
     */
    public static void transformPropertiesMap(
            @NonNull Map<String, List<String>> schemaPropertiesMap) {
        Objects.requireNonNull(schemaPropertiesMap);
        PersonEnterpriseTransformer.transformPropertiesMap(schemaPropertiesMap);
    }
}
