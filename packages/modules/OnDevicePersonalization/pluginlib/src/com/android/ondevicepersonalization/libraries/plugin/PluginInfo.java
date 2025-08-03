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

package com.android.ondevicepersonalization.libraries.plugin;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;

import org.checkerframework.checker.nullness.qual.Nullable;

/** Info passed to PluginManagers to load & run plugins. */
@AutoValue
public abstract class PluginInfo {
    /**
     * Information about the plugin archive : filename, and optionally packageName, if not the
     * current package.
     */
    @AutoValue
    public abstract static class ArchiveInfo {
        /** The filename of the plugin APK. */
        public abstract @Nullable String filename();

        /** The packageName that contains the plugin APK. */
        public abstract @Nullable String packageName();

        /** Instantiate a default builder of {@link ArchiveInfo}. */
        public static Builder builder() {
            return new AutoValue_PluginInfo_ArchiveInfo.Builder();
        }

        /** Creates a Builder from this {@link ArchiveInfo} */
        public abstract Builder toBuilder();

        /** Builder to {@link ArchiveInfo}. */
        @AutoValue.Builder
        public abstract static class Builder {
            /** Sets filename. */
            public abstract Builder setFilename(String filename);

            /** Sets packageName. */
            public abstract Builder setPackageName(String packageName);

            /** Builds an {@link ArchiveInfo}. */
            public abstract ArchiveInfo build();
        }
    }

    /** Distinguish the task. */
    public abstract String taskName();

    /** Information about the plugin APKs and the package containing them. */
    public abstract ImmutableList<ArchiveInfo> archives();

    /** Name of the entry point class implementing {@code Plugin}. */
    public abstract @Nullable String entryPointClassName();

    /** Create {@link PluginInfo} for a JVM plugin with list of {@link ArchiveInfo}. */
    public static PluginInfo createJvmInfo(
            String taskName, ImmutableList<ArchiveInfo> archives, String entryPointClassName) {
        Builder builder = builder();
        builder.setTaskName(taskName);
        builder.setArchives(archives);
        builder.setEntryPointClassName(entryPointClassName);
        return builder.build();
    }

    /** Create {@link PluginInfo} for a JVM plugin with one APK from the current package. */
    public static PluginInfo createJvmInfo(
            String taskName, String filename, String entryPointClassName) {
        return createJvmInfo(
                taskName,
                ImmutableList.of(ArchiveInfo.builder().setFilename(filename).build()),
                entryPointClassName);
    }

    /** Instantiate a default builder of {@link PluginInfo}. */
    public static Builder builder() {
        return new AutoValue_PluginInfo.Builder();
    }

    /** Builder to {@link PluginInfo}. */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Sets taskName. */
        public abstract Builder setTaskName(String taskName);

        /** Sets archives. */
        public abstract Builder setArchives(ImmutableList<ArchiveInfo> archives);

        /** Sets entryPointClassName. */
        public abstract Builder setEntryPointClassName(@Nullable String entryPointClassName);

        /** Builds a {@link PluginInfo}. */
        public abstract PluginInfo build();
    }
}
