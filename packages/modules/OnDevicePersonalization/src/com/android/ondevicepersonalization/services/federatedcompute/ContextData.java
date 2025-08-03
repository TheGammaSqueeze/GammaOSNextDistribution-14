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

package com.android.ondevicepersonalization.services.federatedcompute;

import android.annotation.NonNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * ContextData object to pass to federatedcompute
 * TODO(278106108): Move this class depending on scheduling impl.
 */
public class ContextData implements Serializable {
    @NonNull
    String mPackageName;

    @NonNull
    String mClassName;

    public ContextData(@NonNull String packageName, @NonNull String className) {
        this.mPackageName = packageName;
        this.mClassName = className;
    }

    /**
     * Converts the given ContextData into a serialized byte[]
     */
    public static byte[] toByteArray(ContextData contextData) throws IOException {
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
             ObjectOutputStream objectOutputStream = new ObjectOutputStream(
                     byteArrayOutputStream)) {
            objectOutputStream.writeObject(contextData);
            return byteArrayOutputStream.toByteArray();
        }
    }

    /**
     * Converts the given serialized byte[] into a ContextData object
     */
    public static ContextData fromByteArray(byte[] arr) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(arr);
             ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream)) {
            return (ContextData) objectInputStream.readObject();
        }
    }

    @NonNull
    public String getPackageName() {
        return mPackageName;
    }

    @NonNull
    public String getClassName() {
        return mClassName;
    }
}
