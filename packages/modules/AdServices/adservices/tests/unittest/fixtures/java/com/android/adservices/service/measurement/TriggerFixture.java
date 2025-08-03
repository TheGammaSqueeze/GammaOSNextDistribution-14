/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.adservices.service.measurement;

import android.net.Uri;
import android.util.Pair;

import com.android.adservices.common.WebUtil;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.measurement.aggregation.AggregatableAttributionTrigger;
import com.android.adservices.service.measurement.aggregation.AggregateTriggerData;
import com.android.adservices.service.measurement.util.UnsignedLong;

import org.json.JSONArray;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class TriggerFixture {
    private TriggerFixture() { }

    // Assume the field values in this Trigger.Builder have no relation to the field values in
    // {@link ValidTriggerParams}
    public static Trigger.Builder getValidTriggerBuilder() {
        return new Trigger.Builder()
                .setAttributionDestination(ValidTriggerParams.ATTRIBUTION_DESTINATION)
                .setEnrollmentId(ValidTriggerParams.ENROLLMENT_ID)
                .setRegistrant(ValidTriggerParams.REGISTRANT)
                .setAggregatableSourceRegistrationTimeConfig(
                        ValidTriggerParams.AGGREGATABLE_SOURCE_REGISTRATION_TIME_CONFIG)
                .setRegistrationOrigin(ValidTriggerParams.REGISTRATION_ORIGIN);
    }

    // Assume the field values in this Trigger have no relation to the field values in
    // {@link ValidTriggerParams}
    public static Trigger getValidTrigger() {
        return new Trigger.Builder()
                .setId(UUID.randomUUID().toString())
                .setAttributionDestination(ValidTriggerParams.ATTRIBUTION_DESTINATION)
                .setEnrollmentId(ValidTriggerParams.ENROLLMENT_ID)
                .setRegistrant(ValidTriggerParams.REGISTRANT)
                .setTriggerTime(ValidTriggerParams.TRIGGER_TIME)
                .setEventTriggers(ValidTriggerParams.EVENT_TRIGGERS)
                .setAggregateTriggerData(ValidTriggerParams.AGGREGATE_TRIGGER_DATA)
                .setAggregateValues(ValidTriggerParams.AGGREGATE_VALUES)
                .setFilters(ValidTriggerParams.TOP_LEVEL_FILTERS_JSON_STRING)
                .setNotFilters(ValidTriggerParams.TOP_LEVEL_NOT_FILTERS_JSON_STRING)
                .setAttributionConfig(ValidTriggerParams.ATTRIBUTION_CONFIGS_STRING)
                .setAdtechBitMapping(ValidTriggerParams.X_NETWORK_KEY_MAPPING)
                .setRegistrationOrigin(ValidTriggerParams.REGISTRATION_ORIGIN)
                .setAggregationCoordinatorOrigin(ValidTriggerParams.AGGREGATION_COORDINATOR_ORIGIN)
                .setAggregatableSourceRegistrationTimeConfig(
                        ValidTriggerParams.AGGREGATABLE_SOURCE_REGISTRATION_TIME_CONFIG)
                .build();
    }

    public static class ValidTriggerParams {
        public static final Long TRIGGER_TIME = 8640000000L;
        public static final Uri ATTRIBUTION_DESTINATION =
                Uri.parse("android-app://com.destination");
        public static final Uri REGISTRANT = Uri.parse("android-app://com.registrant");
        public static final String ENROLLMENT_ID = "enrollment-id";
        public static final String TOP_LEVEL_FILTERS_JSON_STRING =
                "[{\n"
                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                        + "}]\n";

        public static final String TOP_LEVEL_NOT_FILTERS_JSON_STRING =
                "[{\"geo\": [], \"source_type\": [\"event\"]}]";

        public static final String EVENT_TRIGGERS =
                "[\n"
                        + "{\n"
                        + "  \"trigger_data\": \"5\",\n"
                        + "  \"priority\": \"123\",\n"
                        + "  \"filters\": [{\n"
                        + "    \"source_type\": [\"navigation\"],\n"
                        + "    \"key_1\": [\"value_1\"] \n"
                        + "   }]\n"
                        + "},\n"
                        + "{\n"
                        + "  \"trigger_data\": \"0\",\n"
                        + "  \"priority\": \"124\",\n"
                        + "  \"deduplication_key\": \"101\",\n"
                        + "  \"filters\": [{\n"
                        + "     \"source_type\": [\"event\"]\n"
                        + "   }]\n"
                        + "}\n"
                        + "]\n";

        public static final String AGGREGATE_TRIGGER_DATA =
                "["
                    + "{"
                        + "\"key_piece\":\"0xA80\","
                        + "\"source_keys\":[\"geoValue\",\"noMatch\"]"
                    + "}"
                + "]";

        public static final String AGGREGATE_VALUES =
                "{" + "\"campaignCounts\":32768," + "\"geoValue\":1664" + "}";

        public static final UnsignedLong DEBUG_KEY = new UnsignedLong(27836L);

        public static final AttributionConfig ATTRIBUTION_CONFIG =
                new AttributionConfig.Builder()
                        .setExpiry(604800L)
                        .setSourceAdtech("AdTech1-Ads")
                        .setSourcePriorityRange(new Pair<>(100L, 1000L))
                        .setSourceFilters(
                                Collections.singletonList(
                                        new FilterMap.Builder()
                                                .setAttributionFilterMap(
                                                        Map.of(
                                                                "campaign_type",
                                                                Collections.singletonList(
                                                                        "install"),
                                                                "source_type",
                                                                Collections.singletonList(
                                                                        "navigation")))
                                                .build()))
                        .setPriority(99L)
                        .setExpiry(604800L)
                        .setFilterData(
                                new FilterMap.Builder()
                                        .setAttributionFilterMap(
                                                Map.of(
                                                        "campaign_type",
                                                        Collections.singletonList("install")))
                                        .build())
                        .build();

        public static final String ATTRIBUTION_CONFIGS_STRING =
                new JSONArray(
                                Collections.singletonList(
                                        ATTRIBUTION_CONFIG.serializeAsJson(
                                                FlagsFactory.getFlagsForTest())))
                        .toString();

        public static final String X_NETWORK_KEY_MAPPING =
                "{"
                        + "\"AdTechA-enrollment_id\": \"0x1\","
                        + "\"AdTechB-enrollment_id\": \"0x2\""
                        + "}";
        public static final Uri REGISTRATION_ORIGIN =
                WebUtil.validUri("https://subdomain.example.test");

        public static final Uri AGGREGATION_COORDINATOR_ORIGIN =
                WebUtil.validUri("https://coordinator.example.test");

        public static final Trigger.SourceRegistrationTimeConfig
                AGGREGATABLE_SOURCE_REGISTRATION_TIME_CONFIG =
                        Trigger.SourceRegistrationTimeConfig.INCLUDE;

        public static final String PLATFORM_AD_ID = "test-platform-ad-id";
        public static final String DEBUG_AD_ID = "test-debug-ad-id";

        public static final AggregatableAttributionTrigger buildAggregatableAttributionTrigger() {
            final FilterMap filter =
                    new FilterMap.Builder()
                            .setAttributionFilterMap(
                                    Map.of(
                                            "product",
                                            List.of("1234", "4321"),
                                            "conversion_subdomain",
                                            List.of("electronics.megastore")))
                            .build();
            return new AggregatableAttributionTrigger.Builder()
                    .setValues(Map.of("x", 1))
                    .setTriggerData(
                            List.of(
                                    new AggregateTriggerData.Builder()
                                            .setKey(BigInteger.ONE)
                                            .setSourceKeys(Set.of("sourceKey"))
                                            .setFilterSet(List.of(filter))
                                            .build()))
                    .build();
        }
    }
}
