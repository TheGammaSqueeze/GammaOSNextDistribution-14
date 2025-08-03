/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package android.healthconnect.cts.utils;

import android.health.connect.datatypes.DataOrigin;
import android.health.connect.datatypes.Device;
import android.health.connect.datatypes.HeartRateRecord;
import android.health.connect.datatypes.HeartRateRecord.HeartRateSample;
import android.health.connect.datatypes.InstantRecord;
import android.health.connect.datatypes.IntervalRecord;
import android.health.connect.datatypes.Metadata;
import android.health.connect.datatypes.Record;

import androidx.annotation.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * A class to provide {@code toString()} implementations mainly for debugging purpose in tests.
 * Later on, when this is in a decent state, we could also share or copy this to prod code.
 */
public final class ToStringUtils {
    private ToStringUtils() {}

    /** An implementation of {@code toString()} for any {@link Record} for debugging purposes. */
    public static String recordToString(Record record) {
        StringBuilder result = new StringBuilder();
        int baseIndentation = 3;

        result.append("\n");
        addLine(result, baseIndentation, record.getClass().getSimpleName());

        addLine(result, baseIndentation + 2, "RECORDING TYPE", record.getRecordType());

        addLine(result, baseIndentation + 2, "TIME");
        if (record instanceof InstantRecord instantRecord) {
            addLinesForTime(result, baseIndentation + 4, "time", instantRecord.getTime());
            addLine(result, baseIndentation + 4, "zoneOffset", instantRecord.getZoneOffset());
        } else if (record instanceof IntervalRecord intervalRecord) {
            addLinesForTime(
                    result, baseIndentation + 4, "startTime", intervalRecord.getStartTime());
            addLinesForTime(result, baseIndentation + 4, "endTime", intervalRecord.getEndTime());
            addLine(
                    result,
                    baseIndentation + 4,
                    "startZoneOffset",
                    intervalRecord.getStartZoneOffset());
            addLine(
                    result,
                    baseIndentation + 4,
                    "endZoneOffset",
                    intervalRecord.getEndZoneOffset());
        }

        Metadata metadata = record.getMetadata();
        addLine(result, baseIndentation + 2, "METADATA");
        addLine(result, baseIndentation + 4, "id", metadata.getId());
        addLine(result, baseIndentation + 4, "clientRecordId", metadata.getClientRecordId());
        addLine(result, baseIndentation + 4, "recordingMethod", metadata.getRecordingMethod());
        addLine(
                result,
                baseIndentation + 4,
                "clientRecordingVersion",
                metadata.getClientRecordVersion());
        addLine(
                result,
                baseIndentation + 4,
                "dataOrigin",
                dataOriginToString(metadata.getDataOrigin()));
        addLine(result, baseIndentation + 4, "device", deviceToString(metadata.getDevice()));

        addLine(result, baseIndentation + 2, "DATA");
        if (record instanceof HeartRateRecord heartRateRecord) {
            return heartRateRecordToString(result, baseIndentation + 4, heartRateRecord);
        }

        return record.toString();
    }

    private static String heartRateRecordToString(
            StringBuilder stringBuilder, int indentation, HeartRateRecord heartRateRecord) {
        List<HeartRateSample> samples = heartRateRecord.getSamples();
        for (int i = 0; i < samples.size(); i++) {
            addLine(stringBuilder, indentation, "Sample #" + i);
            addLinesForTime(stringBuilder, indentation + 2, "time", samples.get(i).getTime());
            addLine(
                    stringBuilder,
                    indentation + 2,
                    "beatPerMinute",
                    samples.get(i).getBeatsPerMinute());
        }
        return stringBuilder.toString();
    }

    private static StringBuilder addLinesForTime(
            StringBuilder stringBuilder, int indentation, String header, Instant time) {
        addLine(stringBuilder, indentation, header, time.toString());
        addLine(stringBuilder, indentation, header + " - epochMillis", time.toEpochMilli());
        return stringBuilder;
    }

    private static String deviceToString(@Nullable Device device) {
        if (device == null) {
            return "";
        }
        return device.getManufacturer() + " - " + device.getModel() + " - " + device.getType();
    }

    private static String dataOriginToString(@Nullable DataOrigin dataOrigin) {
        if (dataOrigin == null) {
            return "";
        }
        return "- packageName: " + dataOrigin.getPackageName();
    }

    private static StringBuilder addLine(
            StringBuilder stringBuilder, int indentation, String header) {
        return addLine(stringBuilder, indentation, header, null);
    }

    private static StringBuilder addLine(
            StringBuilder stringBuilder, int indentation, String header, @Nullable Object content) {
        stringBuilder.append("=".repeat(Math.max(0, indentation)));
        stringBuilder.append(" ").append(header);
        if (content != null) {
            stringBuilder.append(":").append(" ").append(content);
        }
        return stringBuilder.append("\n");
    }
}
