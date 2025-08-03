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

package com.android.adservices.cobalt;

import static com.google.common.truth.Truth.assertThat;

import static java.util.stream.Collectors.toList;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.cobalt.domain.Project;

import com.google.cobalt.IntegerBuckets;
import com.google.cobalt.MetricDefinition;
import com.google.cobalt.MetricDefinition.MetricType;
import com.google.cobalt.ReportDefinition;
import com.google.cobalt.ReportDefinition.LocalAggregationProcedure;
import com.google.cobalt.ReportDefinition.PrivacyLevel;
import com.google.cobalt.ReportDefinition.ReportType;
import com.google.cobalt.ReportDefinition.ReportingInterval;
import com.google.cobalt.StringSketchParameters;
import com.google.cobalt.SystemProfileSelectionPolicy;
import com.google.cobalt.WindowSize;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class CobaltRegistryLoaderTest {
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void cobaltRegistryIsValidated_isTrue() throws Exception {
        assertThat(CobaltRegistryValidated.IS_REGISTRY_VALIDATED).isTrue();
    }

    @Test
    public void getRegistry_registryCanBeLoaded() throws Exception {
        Project registry = CobaltRegistryLoader.getRegistry(CONTEXT);
        assertThat(registry).isNotNull();
    }

    @Test
    public void getRegistry_unsupportedFeaturesNotInRegistry() throws Exception {
        Project registry = CobaltRegistryLoader.getRegistry(CONTEXT);
        for (MetricDefinition metric : registry.getMetrics()) {
            assertThat(metric.getMetricType()).isAnyOf(MetricType.OCCURRENCE, MetricType.STRING);
        }

        List<ReportDefinition> reports =
                registry.getMetrics().stream()
                        .flatMap(m -> m.getReportsList().stream())
                        .collect(toList());
        for (ReportDefinition report : reports) {
            assertThat(report.getReportType())
                    .isAnyOf(ReportType.FLEETWIDE_OCCURRENCE_COUNTS, ReportType.STRING_COUNTS);
            if (report.getReportType() == ReportType.STRING_COUNTS) {
                assertThat(report.getPrivacyLevel()).isEqualTo(PrivacyLevel.NO_ADDED_PRIVACY);
            }
            assertThat(report.getReportingInterval()).isEqualTo(ReportingInterval.DAYS_1);
            assertThat(report.getLocalAggregationProcedure())
                    .isEqualTo(LocalAggregationProcedure.LOCAL_AGGREGATION_PROCEDURE_UNSET);
            assertThat(report.getExperimentIdList()).isEmpty();
            assertThat(report.getSystemProfileSelection())
                    .isEqualTo(SystemProfileSelectionPolicy.REPORT_ALL);
            assertThat(report.getStringSketchParams())
                    .isEqualTo(StringSketchParameters.getDefaultInstance());
            assertThat(report.getIntBuckets()).isEqualTo(IntegerBuckets.getDefaultInstance());
            assertThat(report.getLocalAggregationProcedurePercentileN()).isEqualTo(0);
            assertThat(report.getExpeditedSending()).isFalse();
            assertThat(report.getExperimentIdList()).isEmpty();

            // Sme parts of the code always assume a local aggregation period of 1 day independent
            // of the values in reports, e.g. database clean. Supporting larger windows in reports
            // must be done with a careful check of existing code.
            assertThat(report.getLocalAggregationPeriod()).isEqualTo(WindowSize.UNSET);
        }
    }
}
