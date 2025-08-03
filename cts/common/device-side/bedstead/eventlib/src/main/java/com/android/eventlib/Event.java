/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.eventlib;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.time.Instant;

/**
 * Represents a single action which has been logged.
 */
public abstract class Event implements Serializable {

    private static final long serialVersionUID = 1;

    // This class should contain all standard data applicable to all Events.

    protected String mPackageName;
    protected Instant mTimestamp;

    /** Get the package name this event was logged by. */
    public String packageName() {
        return mPackageName;
    }

    /** Get the time that this event was logged. */
    public Instant timestamp() {
        return mTimestamp;
    }

    /**
     * Serialize the {@link Event} to a byte array.
     *
     * <p>The resulting array can be deserialized using {@link #fromBytes(byte[])}.
     */
    byte[] toBytes() throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream out = new ObjectOutputStream(bos)) {
            out.writeObject(this);
            out.flush();
            return bos.toByteArray();
        }
    }

    /**
     * Deserialize an {@link Event} from a byte array created using {@link #toBytes()}.
     */
    static Event fromBytes(byte[] bytes) throws IOException {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
             ObjectInput in = new ObjectInputStream(bis)) {
            try {
                return (Event) in.readObject();
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(
                        "Trying to read Event which is not on classpath", e);
            }
        }
    }
}
