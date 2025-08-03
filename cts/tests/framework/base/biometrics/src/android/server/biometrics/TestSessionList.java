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

package android.server.biometrics;


import android.hardware.biometrics.BiometricTestSession;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * List of sessions that will be closed after a test.
 *
 * Prefer to simply use a try block with a single session when possible.
 */
public class TestSessionList implements AutoCloseable {
    private final Idler mIdler;
    private final List<BiometricTestSession> mSessions = new ArrayList<>();
    private final Map<Integer, BiometricTestSession> mSessionMap = new HashMap<>();

    public interface Idler {
        /** Wait for all sensor to be idle. */
        void waitForIdleSensors();
    }

    /** Create a list with the given idler.  */
    public TestSessionList(@NonNull Idler idler) {
        mIdler = idler;
    }

    /** Add a session. */
    public void add(@NonNull BiometricTestSession session) {
        mSessions.add(session);
    }

    /** Add a session associated with a sensor id. */
    public void put(int sensorId, @NonNull BiometricTestSession session) {
        mSessions.add(session);
        mSessionMap.put(sensorId, session);
    }

    /** The first session. */
    @Nullable
    public BiometricTestSession first() {
        return mSessions.get(0);
    }

    /** The session associated with the id added with {@link #put(int, BiometricTestSession)}. */
    @Nullable
    public BiometricTestSession find(int sensorId) {
        return mSessionMap.get(sensorId);
    }

    @Override
    public void close() throws Exception {
        for (BiometricTestSession session : mSessions) {
            session.close();
        }
        mIdler.waitForIdleSensors();
    }
}
