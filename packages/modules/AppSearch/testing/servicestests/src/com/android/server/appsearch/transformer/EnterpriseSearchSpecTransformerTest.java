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

import android.app.appsearch.JoinSpec;
import android.app.appsearch.SearchSpec;
import android.util.ArrayMap;

import com.android.server.appsearch.contactsindexer.appsearchtypes.Person;

import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class EnterpriseSearchSpecTransformerTest {
    @Test
    public void testTransformProjection_whenProjectionNotSuppliedByUser() {
        SearchSpec searchSpec = new SearchSpec.Builder().build();

        SearchSpec transformedSpec = EnterpriseSearchSpecTransformer.transformSearchSpec(
                searchSpec);

        assertThat(transformedSpec.getProjections().get(Person.SCHEMA_TYPE))
                .containsExactlyElementsIn(
                        PersonEnterpriseTransformer.PERSON_ACCESSIBLE_PROPERTIES_SET);
    }

    @Test
    public void testTransformProjection_whenProjectionSuppliedByUser() {
        List<String> clientProjection = Arrays.asList(Person.PERSON_PROPERTY_NAME,
                Person.PERSON_PROPERTY_IS_IMPORTANT);
        SearchSpec searchSpec = new SearchSpec.Builder()
                .addProjection(Person.SCHEMA_TYPE, clientProjection)
                .build();

        SearchSpec transformedSpec = EnterpriseSearchSpecTransformer.transformSearchSpec(
                searchSpec);

        assertThat(transformedSpec.getProjections().get(Person.SCHEMA_TYPE))
                .containsExactlyElementsIn(getIntersection(clientProjection,
                        PersonEnterpriseTransformer.PERSON_ACCESSIBLE_PROPERTIES_SET));
    }

    @Test
    public void testTransformProjection_doesNotAffectNonEnterpriseSchemaTypes() {
        SearchSpec searchSpec = new SearchSpec.Builder()
                .addProjection("testSchemaType", Arrays.asList("fieldA", "fieldB"))
                .build();

        SearchSpec transformedSpec = EnterpriseSearchSpecTransformer.transformSearchSpec(
                searchSpec);

        assertThat(transformedSpec.getProjections().get("testSchemaType"))
                .containsExactlyElementsIn(Arrays.asList("fieldA", "fieldB"));
        assertThat(transformedSpec.getProjections().get(Person.SCHEMA_TYPE))
                .containsExactlyElementsIn(
                        PersonEnterpriseTransformer.PERSON_ACCESSIBLE_PROPERTIES_SET);
    }

    @Test
    public void testTransformProperties_addsProjectionWhenNotSuppliedByUser() {
        Map<String, List<String>> schemaPropertiesMap = new ArrayMap<>();

        EnterpriseSearchSpecTransformer.transformPropertiesMap(schemaPropertiesMap);

        assertThat(schemaPropertiesMap.get(Person.SCHEMA_TYPE)).containsExactlyElementsIn(
                PersonEnterpriseTransformer.PERSON_ACCESSIBLE_PROPERTIES_SET);
    }

    @Test
    public void testTransformProperties_whenProjectionSuppliedByUser() {
        Map<String, List<String>> schemaPropertiesMap = new ArrayMap<>();
        List<String> properties = new ArrayList<>(
                Arrays.asList(Person.PERSON_PROPERTY_NAME, Person.PERSON_PROPERTY_IS_IMPORTANT));
        schemaPropertiesMap.put(Person.SCHEMA_TYPE, properties);

        EnterpriseSearchSpecTransformer.transformPropertiesMap(schemaPropertiesMap);

        assertThat(schemaPropertiesMap.get(Person.SCHEMA_TYPE)).containsExactlyElementsIn(
                getIntersection(properties,
                        PersonEnterpriseTransformer.PERSON_ACCESSIBLE_PROPERTIES_SET));
    }

    @Test
    public void testTransformProperties_doesNotAffectNonEnterpriseSchemaTypes() {
        Map<String, List<String>> schemaPropertiesMap = new ArrayMap<>();
        schemaPropertiesMap.put("testSchemaType",
                new ArrayList<>(Arrays.asList("fieldA", "fieldB")));

        EnterpriseSearchSpecTransformer.transformPropertiesMap(schemaPropertiesMap);

        assertThat(schemaPropertiesMap.get("testSchemaType")).containsExactlyElementsIn(
                Arrays.asList("fieldA", "fieldB"));
        assertThat(schemaPropertiesMap.get(Person.SCHEMA_TYPE)).containsExactlyElementsIn(
                PersonEnterpriseTransformer.PERSON_ACCESSIBLE_PROPERTIES_SET);
    }

    @Test
    public void testTransformPropertyFilters_whenFiltersNotSuppliedByUser() {
        SearchSpec searchSpec = new SearchSpec.Builder().build();

        SearchSpec transformedSpec = EnterpriseSearchSpecTransformer.transformSearchSpec(
                searchSpec);

        assertThat(transformedSpec.getFilterProperties().get(Person.SCHEMA_TYPE))
                .containsExactlyElementsIn(
                        PersonEnterpriseTransformer.PERSON_ACCESSIBLE_PROPERTIES_SET);
    }

    @Test
    public void testTransformPropertyFilters_whenFiltersSuppliedByUser() {
        List<String> clientFilterProperties = Arrays.asList(Person.PERSON_PROPERTY_NAME,
                Person.PERSON_PROPERTY_IS_IMPORTANT);
        SearchSpec searchSpec = new SearchSpec.Builder()
                .addFilterProperties(Person.SCHEMA_TYPE, clientFilterProperties)
                .build();

        SearchSpec transformedSpec = EnterpriseSearchSpecTransformer.transformSearchSpec(
                searchSpec);

        assertThat(transformedSpec.getFilterProperties().get(Person.SCHEMA_TYPE))
                .containsExactlyElementsIn(getIntersection(clientFilterProperties,
                        PersonEnterpriseTransformer.PERSON_ACCESSIBLE_PROPERTIES_SET));
    }

    @Test
    public void testTransformPropertyFilters_doesNotAffectNonEnterpriseSchemaTypes() {
        SearchSpec searchSpec = new SearchSpec.Builder()
                .addFilterProperties("testSchemaType", Arrays.asList("fieldA", "fieldB"))
                .build();

        SearchSpec transformedSpec = EnterpriseSearchSpecTransformer.transformSearchSpec(
                searchSpec);

        assertThat(transformedSpec.getFilterProperties().get("testSchemaType"))
                .containsExactlyElementsIn(Arrays.asList("fieldA", "fieldB"));
        assertThat(transformedSpec.getFilterProperties().get(Person.SCHEMA_TYPE))
                .containsExactlyElementsIn(
                        PersonEnterpriseTransformer.PERSON_ACCESSIBLE_PROPERTIES_SET);
    }

    @Test
    public void testTransformJoinSpec() {
        String nestedQuery = "nestedQuery";
        JoinSpec joinSpec = new JoinSpec.Builder("childPropertyExpression")
                .setNestedSearch(nestedQuery, new SearchSpec.Builder().build())
                .build();
        SearchSpec searchSpec = new SearchSpec.Builder().setJoinSpec(joinSpec).build();

        SearchSpec transformedSpec = EnterpriseSearchSpecTransformer.transformSearchSpec(
                searchSpec);

        assertThat(transformedSpec.getJoinSpec().getNestedQuery())
                .isEqualTo(nestedQuery);
        assertThat(transformedSpec.getJoinSpec().getNestedSearchSpec()
                .getProjections().get(Person.SCHEMA_TYPE)).containsExactlyElementsIn(
                PersonEnterpriseTransformer.PERSON_ACCESSIBLE_PROPERTIES_SET);
        assertThat(transformedSpec.getJoinSpec().getNestedSearchSpec()
                .getFilterProperties().get(Person.SCHEMA_TYPE)).containsExactlyElementsIn(
                PersonEnterpriseTransformer.PERSON_ACCESSIBLE_PROPERTIES_SET);
    }

    private List<String> getIntersection(Collection<String> col1, Collection<String> col2) {
        List<String> intersection = new ArrayList<>(col1);
        intersection.retainAll(col2);
        return intersection;
    }
}
