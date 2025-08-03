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

package com.android.car.obd2;

import android.util.JsonWriter;
import android.util.Log;

import java.io.IOException;
import java.util.Optional;

/**
 * Helper class containing common utility methods shared across {@link Obd2FreezeFrameGenerator} and
 * {@link Obd2LiveFrameGenerator}.
 */
final class Obd2FrameGeneratorUtils {

    private static final String TAG = Obd2FrameGeneratorUtils.class.getSimpleName();

    static <V extends Number> Optional<V> tryRunCommand(Obd2Connection conn,
            Obd2Command<V> command) {
        try {
            return command.run(conn);
        } catch (InterruptedException e) {
            // Preserve interrupt status
            Thread.currentThread().interrupt();
            Log.w(TAG, "Failed to run command for pid " + command.getPid()
                    + " due to interrupted thread", e);
            return Optional.empty();
        } catch (Exception e) {
            Log.w(TAG, "Failed to run command for pid " + command.getPid() + " due to exception",
                    e);
            return Optional.empty();
        }
    }

    static void writeResultIdValue(JsonWriter jsonWriter, int pid, Number value) {
        try {
            jsonWriter.beginObject();
            jsonWriter.name("id").value(pid);
            jsonWriter.name("value").value(value);
            jsonWriter.endObject();
        } catch (IOException e) {
            Log.w(TAG, "Failed to write value for pid " + pid + " due to exception (skipping)", e);
        }
    }

    private Obd2FrameGeneratorUtils() {
    }
}
