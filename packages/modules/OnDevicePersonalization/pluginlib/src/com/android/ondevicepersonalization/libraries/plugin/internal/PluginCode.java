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
import android.os.ParcelFileDescriptor;
import android.os.Parcelable;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import java.io.Closeable;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Native and non-native file descriptor and corresponding checksum. */
@AutoValue
public abstract class PluginCode implements Parcelable, Closeable {
    /**
     * Native file descriptor. This may point to the same file as nonNativeFd(), but we need
     * separate descriptors for each, since file descriptors cannot be created in the sandbox, and
     * the passed in file descriptor cannot be rewinded.
     */
    public abstract ParcelFileDescriptor nativeFd();

    /**
     * Non-native file descriptor. This may point to the same file as nativeFd(), but we need
     * separate descriptors for each, since file descriptors cannot be created in the sandbox, and
     * the passed in file descriptor cannot be rewinded.
     */
    public abstract ParcelFileDescriptor nonNativeFd();

    /** Checksum for version checking and caching */
    public abstract String checksum();

    @Override
    public final int describeContents() {
        return Parcelable.CONTENTS_FILE_DESCRIPTOR;
    }

    @Override
    public final void writeToParcel(Parcel dest, int flags) {
        nativeFd().writeToParcel(dest, flags);
        nonNativeFd().writeToParcel(dest, flags);
        dest.writeString(checksum());
    }

    @Override
    public void close() throws IOException {
        nativeFd().close();
        nonNativeFd().close();
    }

    public static final Creator<PluginCode> CREATOR =
            new Creator<PluginCode>() {
                @Override
                public PluginCode createFromParcel(Parcel source) {
                    Builder builder = builder();
                    builder.setNativeFd(ParcelFileDescriptor.CREATOR.createFromParcel(source));
                    builder.setNonNativeFd(ParcelFileDescriptor.CREATOR.createFromParcel(source));
                    builder.setChecksum(source.readString());
                    return builder.build();
                }

                @Override
                public PluginCode[] newArray(int size) {
                    return new PluginCode[size];
                }
            };

    /** Instantiate a default builder of {@link PluginCode}. */
    public static Builder builder() {
        return new AutoValue_PluginCode.Builder();
    }

    /** Creates a builder from this {@link PluginCode}. */
    public abstract Builder toBuilder();

    /** Builder to {@link PluginCode}. */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Sets nativeFd. */
        public abstract Builder setNativeFd(ParcelFileDescriptor nativeFd);

        /** Sets nonNativeFd. */
        public abstract Builder setNonNativeFd(ParcelFileDescriptor nonNativeFd);

        /** Sets checkSum. */
        public abstract Builder setChecksum(String checksum);

        /** Builds {@link PluginCode}. */
        public abstract PluginCode build();
    }

    /** Duplicates a list of {@link PluginCode}. */
    public static CloseableList<PluginCode> dup(Iterable<PluginCode> source) throws IOException {
        List<PluginCode> duplicatedList = new ArrayList<>();
        for (PluginCode pluginCode : source) {
            duplicatedList.add(
                    PluginCode.builder()
                            .setNativeFd(pluginCode.nativeFd().dup())
                            .setNonNativeFd(pluginCode.nonNativeFd().dup())
                            .setChecksum(pluginCode.checksum())
                            .build());
        }
        return new CloseableList<>(duplicatedList);
    }

    /** Creates a list of {@link FileInputStream} from native fds. */
    public static CloseableList<FileInputStream> createFileInputStreamListFromNativeFds(
            ImmutableList<PluginCode> fds) {
        List<FileInputStream> fileInputStreamList =
                Lists.transform(fds, fd -> new FileInputStream(fd.nativeFd().getFileDescriptor()));
        return new CloseableList<>(fileInputStreamList);
    }

    /** Creates a list of {@link FileInputStream} from non-native fds. */
    public static CloseableList<FileInputStream> createFileInputStreamListFromNonNativeFds(
            ImmutableList<PluginCode> fds) {
        List<FileInputStream> fileInputStreamList =
                Lists.transform(
                        fds, fd -> new FileInputStream(fd.nonNativeFd().getFileDescriptor()));
        return new CloseableList<>(fileInputStreamList);
    }
}
