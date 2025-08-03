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

package com.android.cobalt.domain;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import com.google.cobalt.CobaltRegistry;
import com.google.cobalt.CustomerConfig;
import com.google.cobalt.MetricDefinition;
import com.google.cobalt.ProjectConfig;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;

@RunWith(JUnit4.class)
public final class ProjectTest {
    private static final int CUSTOMER_ID = 1;
    private static final int PROJECT_ID = 2;
    private static final int METRIC_ID = 3;

    @Test
    public void create_populatesIdsAndMetrics_withEmptyMetrics() throws Exception {
        ProjectConfig projectConfig = ProjectConfig.newBuilder().setProjectId(PROJECT_ID).build();
        CustomerConfig customerConfig =
                CustomerConfig.newBuilder()
                        .setCustomerId(CUSTOMER_ID)
                        .addProjects(projectConfig)
                        .build();
        CobaltRegistry protoRegistry =
                CobaltRegistry.newBuilder().addCustomers(customerConfig).build();

        Project project = Project.create(protoRegistry);
        assertThat(project).isEqualTo(Project.create(CUSTOMER_ID, PROJECT_ID, List.of()));
    }

    @Test
    public void create_populatesIdsAndMetrics_withNonEmptyMetrics() throws Exception {
        MetricDefinition metric = MetricDefinition.newBuilder().setId(METRIC_ID).build();
        ProjectConfig projectConfig =
                ProjectConfig.newBuilder().setProjectId(PROJECT_ID).addMetrics(metric).build();
        CustomerConfig customerConfig =
                CustomerConfig.newBuilder()
                        .setCustomerId(CUSTOMER_ID)
                        .addProjects(projectConfig)
                        .build();
        CobaltRegistry protoRegistry =
                CobaltRegistry.newBuilder().addCustomers(customerConfig).build();

        Project project = Project.create(protoRegistry);
        assertThat(project).isEqualTo(Project.create(CUSTOMER_ID, PROJECT_ID, List.of(metric)));
    }

    @Test
    public void create_nullProto_throwsNullPointerException() throws Exception {
        assertThrows(
                NullPointerException.class,
                () -> {
                    Project.create(null);
                });
    }

    @Test
    public void create_nullMetrics_throwsNullPointerException() throws Exception {
        assertThrows(
                NullPointerException.class,
                () -> {
                    Project.create(CUSTOMER_ID, PROJECT_ID, null);
                });
    }

    @Test
    public void create_moreThanOneCustomer_throwsIllegalArgumentException() throws Exception {
        MetricDefinition metric = MetricDefinition.newBuilder().setId(METRIC_ID).build();
        ProjectConfig projectConfig =
                ProjectConfig.newBuilder().setProjectId(PROJECT_ID).addMetrics(metric).build();
        CustomerConfig customerConfig =
                CustomerConfig.newBuilder()
                        .setCustomerId(CUSTOMER_ID)
                        .addProjects(projectConfig)
                        .build();
        CobaltRegistry protoRegistry =
                CobaltRegistry.newBuilder()
                        .addCustomers(customerConfig)
                        .addCustomers(customerConfig)
                        .build();
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    Project.create(protoRegistry);
                });
    }

    @Test
    public void create_moreThanOneProject_throwsIllegalArgumentException() throws Exception {
        MetricDefinition metric = MetricDefinition.newBuilder().setId(METRIC_ID).build();
        ProjectConfig projectConfig =
                ProjectConfig.newBuilder().setProjectId(PROJECT_ID).addMetrics(metric).build();
        CustomerConfig customerConfig =
                CustomerConfig.newBuilder()
                        .setCustomerId(CUSTOMER_ID)
                        .addProjects(projectConfig)
                        .addProjects(projectConfig)
                        .build();
        CobaltRegistry protoRegistry =
                CobaltRegistry.newBuilder().addCustomers(customerConfig).build();
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    Project.create(protoRegistry);
                });
    }
}
