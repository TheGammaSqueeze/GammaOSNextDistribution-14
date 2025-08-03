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

import static com.google.common.base.Preconditions.checkArgument;

import static java.util.Objects.requireNonNull;

import android.annotation.NonNull;

import com.android.internal.annotations.VisibleForTesting;

import com.google.auto.value.AutoValue;
import com.google.cobalt.CobaltRegistry;
import com.google.cobalt.MetricDefinition;
import com.google.common.collect.ImmutableList;

/** Domain object for Cobalt's registry proto that supports a single customer and project. */
@AutoValue
public abstract class Project {
    /**
     * Parses a Project from a {@link com.google.cobalt.CobaltRegistry}.
     *
     * @param registry the {@link com.google.cobalt.CobaltRegistry} protocol buffer
     * @return the parsed {@link Project}
     * @throws Exception if the provided registry has more than 1 customer or more than 1 project
     */
    @NonNull
    public static Project create(@NonNull CobaltRegistry registry) {
        requireNonNull(registry);
        checkArgument(registry.getCustomersCount() == 1, "must be one customer");
        checkArgument(registry.getCustomers(0).getProjectsCount() == 1, "must be one project");

        return Project.create(
                registry.getCustomers(0).getCustomerId(),
                registry.getCustomers(0).getProjects(0).getProjectId(),
                registry.getCustomers(0).getProjects(0).getMetricsList());
    }

    /**
     * Creates a Project with the provided customer and project info.
     *
     * @param customerId the customer's id
     * @param projectId the customer's project id
     * @param metrics the metrics the customer is collecting in this project id
     * @return a {@link Project} for the customer's project
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    @NonNull
    public static Project create(
            int customerId, int projectId, @NonNull Iterable<MetricDefinition> metrics) {
        return new AutoValue_Project(
                customerId, projectId, ImmutableList.copyOf(requireNonNull(metrics)));
    }

    /**
     * @return the customer id
     */
    public abstract int getCustomerId();

    /**
     * @return the customer's project id
     */
    public abstract int getProjectId();

    /**
     * @return the metrics being collected by the customer for the project
     */
    @NonNull
    public abstract ImmutableList<MetricDefinition> getMetrics();
}
