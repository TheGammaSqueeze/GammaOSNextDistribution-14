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

package com.android.queryable.info;

import android.os.Bundle;

import java.util.Set;

/**
 * Wrapper for information about any type of Receiver.
 */
public class ReceiverInfo extends ClassInfo {

    private final String mClassName;
    private Set<Bundle> mMetadata;

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(ReceiverInfo receiverInfo) {
        return builder().name(receiverInfo.name()).metadata(receiverInfo.metadata());
    }

    public String name() {
        return mClassName;
    }

    public Set<Bundle> metadata() {
        return mMetadata;
    }

    @Override
    public String toString() {
        return "ReceiverInfo{" +
                "name=" + mClassName +
                ", metadata=" + mMetadata +
                "}";
    }

    public ReceiverInfo(String receiverClassName) {
        super(receiverClassName);
        this.mClassName = receiverClassName;
    }

    private ReceiverInfo(String name, Set<Bundle> metadata) {
        super(name);
        this.mClassName = name;
        this.mMetadata = metadata;
    }

    public static final class Builder {
        String mClassName;
        Set<Bundle> mMetadata;

        public Builder name(String name) {
            mClassName = name;
            return this;
        }

        public Builder metadata(Set<Bundle> metadata) {
            mMetadata = metadata;
            return this;
        }

        public ReceiverInfo build() {
            return new ReceiverInfo(mClassName, mMetadata);
        }
    }


}
