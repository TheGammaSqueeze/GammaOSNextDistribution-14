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

package com.android.ondevicepersonalization.libraries.plugin.internal;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.auto.value.AutoValue;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Information used to perform preload & run tasks for Plugins. */
@AutoValue
public abstract class PluginInfoInternal implements Parcelable {
    /** Human readable name of the task that is running. */
    public abstract String taskName();

    /** Plugin code. */
    public abstract ImmutableList<PluginCode> pluginCodeList();

    /** Fully qualified name of the entry point class implementing Plugin. */
    public abstract String entryPointClassName();

    /** Computes the checksums of the plugin files. */
    public @Nullable String computeChecksum() {
        List<String> checksums =
                new ArrayList<>(Lists.transform(pluginCodeList(), PluginCode::checksum));
        if (checksums.contains("")) {
            // If we're missing the checksum for any individual file, we cannot compute the combined
            // checksum.
            return null;
        }
        Collections.sort(checksums);
        Joiner joiner = Joiner.on(":");
        return joiner.join(checksums);
    }

    @Override
    public final int describeContents() {
        return Parcelable.CONTENTS_FILE_DESCRIPTOR;
    }

    @Override
    public final void writeToParcel(Parcel dest, int flags) {
        dest.writeString(taskName());
        dest.writeParcelableList(pluginCodeList(), flags);
        dest.writeString(entryPointClassName());
    }

    public static final Creator<PluginInfoInternal> CREATOR =
            new Creator<PluginInfoInternal>() {
                @Override
                public PluginInfoInternal createFromParcel(Parcel source) {
                    Builder builder = builder();
                    String taskName = Objects.requireNonNull(source.readString());
                    builder.setTaskName(taskName);

                    List<PluginCode> pluginCodeList = new ArrayList<>();
                    builder.setPluginCodeList(
                            ImmutableList.copyOf(
                                    source.readParcelableList(
                                            pluginCodeList, PluginCode.class.getClassLoader())));
                    String entryPointClassName = Objects.requireNonNull(source.readString());
                    builder.setEntryPointClassName(entryPointClassName);
                    return builder.build();
                }

                @Override
                public PluginInfoInternal[] newArray(int size) {
                    return new PluginInfoInternal[size];
                }
            };

    /** Instantiate a default builder of {@link PluginInfoInternal}. */
    public static Builder builder() {
        return new AutoValue_PluginInfoInternal.Builder();
    }

    /** Creates a builder from this {@link PluginInfoInternal}. */
    public abstract Builder toBuilder();

    /** Builder to {@link PluginInfoInternal}. */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Sets taskName. */
        public abstract Builder setTaskName(String taskName);

        /** Sets pluginCodeList. */
        public abstract Builder setPluginCodeList(ImmutableList<PluginCode> fds);

        /** Sets entryPointClassName. */
        public abstract Builder setEntryPointClassName(String entryPointClassName);

        /** Builds a {@link PluginInfoInternal} */
        public abstract PluginInfoInternal build();
    }
}
