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

package com.android.car.watchdog;

import static com.google.common.truth.Truth.assertAbout;

import android.annotation.Nullable;
import android.automotive.watchdog.internal.PackageMetadata;
import android.automotive.watchdog.internal.ResourceOveruseConfiguration;
import android.automotive.watchdog.internal.ResourceSpecificConfiguration;

import com.google.common.collect.ImmutableSet;
import com.google.common.truth.Correspondence;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.common.truth.Truth;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class InternalResourceOveruseConfigurationSubject extends Subject {
    // Boiler-plate Subject.Factory for InternalResourceOveruseConfigurationSubject
    private static final Subject.Factory<
            com.android.car.watchdog.InternalResourceOveruseConfigurationSubject,
            Iterable<ResourceOveruseConfiguration>> RESOURCE_OVERUSE_CONFIG_SUBJECT_FACTORY =
            com.android.car.watchdog.InternalResourceOveruseConfigurationSubject::new;

    private static final Correspondence<List<PackageMetadata>, List<PackageMetadata>>
            METADATA_LIST_CORRESPONDENCE = Correspondence.from(
                    InternalResourceOveruseConfigurationSubject::isPackageMetadataEquals,
            "is equal to");

    private static final Correspondence<List<ResourceSpecificConfiguration>,
            List<ResourceSpecificConfiguration>>
            RESOURCE_SPECIFIC_CONFIG_LIST_CORRESPONDENCE = Correspondence.from(
            InternalResourceOveruseConfigurationSubject::isResourceSpecificConfigEquals,
            "is equal to");

    private final Iterable<ResourceOveruseConfiguration> mActual;

    // User-defined entry point
    public static InternalResourceOveruseConfigurationSubject assertThat(
            @Nullable Iterable<ResourceOveruseConfiguration> stats) {
        return assertAbout(RESOURCE_OVERUSE_CONFIG_SUBJECT_FACTORY).that(stats);
    }

    public static Subject.Factory<InternalResourceOveruseConfigurationSubject,
            Iterable<ResourceOveruseConfiguration>> resourceOveruseConfigurations() {
        return RESOURCE_OVERUSE_CONFIG_SUBJECT_FACTORY;
    }

    public void containsExactly(ResourceOveruseConfiguration... stats) {
        containsExactlyElementsIn(Arrays.asList(stats));
    }

    public void containsExactlyElementsIn(Iterable<ResourceOveruseConfiguration> expected) {
        Truth.assertWithMessage("Expected: " + toString(expected) + "\nActual: "
                + toString(mActual)).that(mActual)
                .comparingElementsUsing(Correspondence.from(
                        InternalResourceOveruseConfigurationSubject::isEquals, "is equal to"))
                .containsExactlyElementsIn(expected);
    }

    public static boolean isPackageMetadataEquals(List<PackageMetadata> actual,
            List<PackageMetadata> expected) {
        Set<String> actualStr = toMetadataStrings(actual);
        Set<String> expectedStr = toMetadataStrings(expected);
        return actualStr.equals(expectedStr);
    }

    public static boolean isResourceSpecificConfigEquals(List<ResourceSpecificConfiguration> actual,
            List<ResourceSpecificConfiguration> expected) {
        if (actual.size() != expected.size()) {
            return false;
        }
        if (actual.size() == 0) {
            return true;
        }
        /*
         * When more resource types are added make this comparison more generic. Because the
         * resource overuse configuration should contain only one I/O overuse configuration, the
         * comparison only checks for first I/O overuse configuration.
         */
        ResourceSpecificConfiguration actualElement = actual.get(0);
        ResourceSpecificConfiguration expectedElement = expected.get(0);
        if (actualElement.getTag() != expectedElement.getTag()) {
            return false;
        }
        if (actualElement.getTag() != ResourceSpecificConfiguration.ioOveruseConfiguration) {
            return false;
        }
        return InternalIoOveruseConfigurationSubject.isEquals(
                actualElement.getIoOveruseConfiguration(),
                expectedElement.getIoOveruseConfiguration());
    }

    public static boolean isEquals(ResourceOveruseConfiguration actual,
            ResourceOveruseConfiguration expected) {
        if (actual == null || expected == null) {
            return (actual == null) && (expected == null);
        }
        return actual.componentType == expected.componentType
                && ImmutableSet.copyOf(actual.safeToKillPackages).equals(
                        ImmutableSet.copyOf(expected.safeToKillPackages))
                && ImmutableSet.copyOf(actual.vendorPackagePrefixes).equals(
                        ImmutableSet.copyOf(expected.vendorPackagePrefixes))
                && METADATA_LIST_CORRESPONDENCE.compare(actual.packageMetadata,
                        expected.packageMetadata)
                && RESOURCE_SPECIFIC_CONFIG_LIST_CORRESPONDENCE.compare(
                        actual.resourceSpecificConfigurations,
                        expected.resourceSpecificConfigurations);
    }

    private InternalResourceOveruseConfigurationSubject(FailureMetadata failureMetadata,
            @Nullable Iterable<ResourceOveruseConfiguration> iterableSubject) {
        super(failureMetadata, iterableSubject);
        this.mActual = iterableSubject;
    }

    private static Set<String> toMetadataStrings(List<PackageMetadata> metadata) {
        return metadata.stream().map(x -> String.format("%s:%d", x.packageName, x.appCategoryType))
                .collect(Collectors.toSet());
    }

    private static String toString(
            Iterable<ResourceOveruseConfiguration> configs) {
        StringBuilder builder = new StringBuilder();
        builder.append("[");
        for (ResourceOveruseConfiguration config : configs) {
            toStringBuilder(builder, config).append(", ");
        }
        builder.append("]");
        return builder.toString();
    }

    private static StringBuilder toStringBuilder(StringBuilder builder,
            ResourceOveruseConfiguration config) {
        builder.append("{Component type: ").append(config.componentType)
                .append(", Safe-to-kill packages: [")
                .append(config.safeToKillPackages.stream().map(Object::toString)
                        .collect(Collectors.joining(", ")))
                .append("], Vendor package prefixes: [")
                .append(config.vendorPackagePrefixes.stream().map(Object::toString)
                        .collect(Collectors.joining(", ")))
                .append("], Package Metadata: [")
                .append(config.packageMetadata.stream()
                        .map(InternalResourceOveruseConfigurationSubject::toPackageMetadataString)
                        .collect(Collectors.joining(", ")))
                .append("], Resource specific configurations: [")
                .append(config.resourceSpecificConfigurations.stream()
                        .map(InternalResourceOveruseConfigurationSubject
                                ::toResourceOveruseConfigString).collect(Collectors.joining(", ")))
                .append("]}");
        return builder;
    }

    private static String toResourceOveruseConfigString(ResourceSpecificConfiguration config) {
        StringBuilder builder = new StringBuilder();
        builder.append("{Tag: ").append(config.getTag()).append(", Value: ");
        if (config.getTag() == ResourceSpecificConfiguration.ioOveruseConfiguration) {
            InternalIoOveruseConfigurationSubject.toStringBuilder(builder,
                    config.getIoOveruseConfiguration());
        } else {
            builder.append("UNKNOWN");
        }
        return builder.toString();
    }

    private static String toPackageMetadataString(PackageMetadata metadata) {
        StringBuilder builder = new StringBuilder();
        builder.append("{Name: ").append(metadata.packageName).append(", App category type: ")
                .append(metadata.appCategoryType).append("}");
        return builder.toString();
    }
}
