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

package com.android.adservices.data.common;

import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.room.TypeConverter;

import com.android.adservices.LoggerFactory;

import org.json.JSONArray;

import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Room DB type converters for FLEDGE.
 *
 * <p>Register custom type converters here.
 */
public class FledgeRoomConverters {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    private FledgeRoomConverters() {}

    /** Serialize {@link Instant} to Long. */
    @TypeConverter
    @Nullable
    public static Long serializeInstant(@Nullable Instant instant) {
        return Optional.ofNullable(instant).map(Instant::toEpochMilli).orElse(null);
    }

    /** Deserialize {@link Instant} from long. */
    @TypeConverter
    @Nullable
    public static Instant deserializeInstant(@Nullable Long epochMilli) {
        return Optional.ofNullable(epochMilli).map(Instant::ofEpochMilli).orElse(null);
    }

    /** Deserialize {@link Uri} from String. */
    @TypeConverter
    @Nullable
    public static Uri deserializeUri(@Nullable String uri) {
        return Optional.ofNullable(uri).map(Uri::parse).orElse(null);
    }

    /** Serialize {@link Uri} to String. */
    @TypeConverter
    @Nullable
    public static String serializeUri(@Nullable Uri uri) {
        return Optional.ofNullable(uri).map(Uri::toString).orElse(null);
    }

    /** Serialize an {@link AdTechIdentifier} to String. */
    @TypeConverter
    @Nullable
    public static String serializeAdTechIdentifier(@Nullable AdTechIdentifier adTechIdentifier) {
        return Optional.ofNullable(adTechIdentifier).map(AdTechIdentifier::toString).orElse(null);
    }

    /** Deserialize an {@link AdTechIdentifier} from a String. */
    @TypeConverter
    @Nullable
    public static AdTechIdentifier deserializeAdTechIdentifier(@Nullable String adTechIdentifier) {
        return Optional.ofNullable(adTechIdentifier).map(AdTechIdentifier::fromString).orElse(null);
    }

    /** Serialize an {@link AdSelectionSignals} to String. */
    @TypeConverter
    @Nullable
    public static String serializeAdSelectionSignals(@Nullable AdSelectionSignals signals) {
        return Optional.ofNullable(signals).map(AdSelectionSignals::toString).orElse(null);
    }

    /** Deserialize an {@link AdSelectionSignals} from a String. */
    @TypeConverter
    @Nullable
    public static AdSelectionSignals deserializeAdSelectionSignals(@Nullable String signals) {
        return Optional.ofNullable(signals).map(AdSelectionSignals::fromString).orElse(null);
    }

    /** Serialize a {@link Set} of Strings into a JSON array as a String. */
    @TypeConverter
    @Nullable
    public static String serializeStringSet(@Nullable Set<String> stringSet) {
        if (stringSet == null) {
            return null;
        }

        JSONArray jsonSet = new JSONArray(stringSet);
        return jsonSet.toString();
    }

    /** Deserialize a {@link Set} of Strings from a JSON array. */
    @TypeConverter
    @Nullable
    public static Set<String> deserializeStringSet(@Nullable String serializedSet) {
        if (serializedSet == null) {
            return null;
        }

        Set<String> outputSet = new HashSet<>();
        JSONArray jsonSet;
        try {
            jsonSet = new JSONArray(serializedSet);
        } catch (Exception exception) {
            sLogger.d(exception, "Error deserializing set of strings from DB; returning null set");
            return null;
        }

        for (int arrayIndex = 0; arrayIndex < jsonSet.length(); arrayIndex++) {
            String currentString;
            try {
                currentString = jsonSet.getString(arrayIndex);
            } catch (Exception exception) {
                // getString() coerces elements into Strings, so this should only happen if we get
                // out of bounds
                sLogger.d(
                        exception,
                        "Error deserializing set string #%d from DB; skipping any other elements",
                        arrayIndex);
                break;
            }
            outputSet.add(currentString);
        }

        return outputSet;
    }

    /** Serialize a {@link Set} of Integers into a JSON array as a String. */
    @TypeConverter
    @Nullable
    public static String serializeIntegerSet(@Nullable Set<Integer> integerSet) {
        if (integerSet == null) {
            return null;
        }

        JSONArray jsonSet = new JSONArray(integerSet);
        return jsonSet.toString();
    }

    /** Deserialize a {@link Set} of Strings from a JSON array. */
    @TypeConverter
    @Nullable
    public static Set<Integer> deserializeIntegerSet(@Nullable String serializedSet) {
        if (serializedSet == null) {
            return null;
        }

        Set<Integer> outputSet = new HashSet<>();
        JSONArray jsonSet;
        try {
            jsonSet = new JSONArray(serializedSet);
        } catch (Exception exception) {
            sLogger.d(exception, "Error deserializing set of ints from DB; returning null set");
            return null;
        }

        for (int arrayIndex = 0; arrayIndex < jsonSet.length(); arrayIndex++) {
            int currentInt;
            try {
                currentInt = jsonSet.getInt(arrayIndex);
            } catch (Exception exception) {
                sLogger.d(
                        exception,
                        "Error deserializing set int #%d from DB; skipping element from %s",
                        arrayIndex,
                        serializedSet);
                continue;
            }
            outputSet.add(currentInt);
        }

        return outputSet;
    }
}
