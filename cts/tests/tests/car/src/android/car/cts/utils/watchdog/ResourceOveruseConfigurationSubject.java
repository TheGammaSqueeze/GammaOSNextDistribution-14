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

package android.car.cts.utils.watchdog;

import static com.google.common.truth.Truth.assertAbout;

import android.car.watchdog.ResourceOveruseConfiguration;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.truth.Correspondence;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.common.truth.Truth;

import java.util.Arrays;

public class ResourceOveruseConfigurationSubject extends Subject {
    // Boiler-plate Subject.Factory for ResourceOveruseConfigurationSubject
    private static final Subject.Factory<
            ResourceOveruseConfigurationSubject,
            Iterable<ResourceOveruseConfiguration>> RESOURCE_OVERUSE_CONFIG_SUBJECT_FACTORY =
            ResourceOveruseConfigurationSubject::new;

    private final Iterable<ResourceOveruseConfiguration> mActual;

    // User-defined entry point
    public static ResourceOveruseConfigurationSubject assertThat(
            Iterable<ResourceOveruseConfiguration> stats) {
        return assertAbout(RESOURCE_OVERUSE_CONFIG_SUBJECT_FACTORY).that(stats);
    }

    public static Subject.Factory<ResourceOveruseConfigurationSubject,
            Iterable<ResourceOveruseConfiguration>> resourceOveruseStats() {
        return RESOURCE_OVERUSE_CONFIG_SUBJECT_FACTORY;
    }

    public void containsExactly(ResourceOveruseConfiguration... stats) {
        containsExactlyElementsIn(Arrays.asList(stats));
    }

    public void containsExactlyElementsIn(Iterable<ResourceOveruseConfiguration> expected) {
        Truth.assertThat(mActual)
                .comparingElementsUsing(Correspondence.from(
                        ResourceOveruseConfigurationSubject::isEquals, "is equal to"))
                .containsExactlyElementsIn(expected);
    }

    public static boolean isEquals(ResourceOveruseConfiguration actual,
            ResourceOveruseConfiguration expected) {
        if (actual == null || expected == null) {
            return (actual == null) && (expected == null);
        }
        return actual.getComponentType() == expected.getComponentType()
                && ImmutableSet.copyOf(actual.getSafeToKillPackages()).equals(
                ImmutableSet.copyOf(expected.getSafeToKillPackages()))
                && ImmutableSet.copyOf(actual.getVendorPackagePrefixes()).equals(
                ImmutableSet.copyOf(expected.getVendorPackagePrefixes()))
                && Maps.difference(actual.getPackagesToAppCategoryTypes(),
                expected.getPackagesToAppCategoryTypes()).areEqual()
                && IoOveruseConfigurationSubject.isEquals(actual.getIoOveruseConfiguration(),
                expected.getIoOveruseConfiguration());
    }

    private ResourceOveruseConfigurationSubject(FailureMetadata failureMetadata,
            Iterable<ResourceOveruseConfiguration> iterableSubject) {
        super(failureMetadata, iterableSubject);
        this.mActual = iterableSubject;
    }
}
