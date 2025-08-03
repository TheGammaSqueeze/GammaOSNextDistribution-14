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

package com.android.adservices.service.measurement.reporting;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.Uri;
import android.util.Pair;

import com.android.adservices.service.measurement.util.UnsignedLong;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/** Report related utility methods. */
public class ReportUtil {
    /**
     * Prepares an alphabetical ordered list of attribution destinations for report JSON. If any
     * elements are found to be null, they are added at last.
     *
     * @param destinations a list of attribution destinations
     * @return an Object that is either String or JSONArray
     */
    @Nullable
    public static Object serializeAttributionDestinations(@NonNull List<Uri> destinations) {
        if (destinations.size() == 0) {
            throw new IllegalArgumentException("Destinations list is empty");
        }

        if (destinations.size() == 1) {
            return destinations.get(0).toString();
        } else {
            List<Uri> sortedDestinations = new ArrayList<>(destinations);
            sortedDestinations.sort(Comparator.comparing(Uri::toString));
            return new JSONArray(
                    sortedDestinations.stream().map(Uri::toString).collect(Collectors.toList()));
        }
    }

    /**
     * Prepares a list of {@code UnsignedLong}s for report JSON.
     *
     * @param unsignedLongs a list of {@code UnsignedLong}s
     * @return a JSONArray
     */
    @Nullable
    public static JSONArray serializeUnsignedLongs(@NonNull List<UnsignedLong> unsignedLongs) {
        return new JSONArray(
                unsignedLongs.stream().map(UnsignedLong::toString).collect(Collectors.toList()));
    }

    /**
     * Prepare summary bucket for report JSON
     *
     * @param summaryBucket the summary bucket
     * @return the encoded summary bucket in format [start, end]
     */
    @Nullable
    public static JSONArray serializeSummaryBucket(@NonNull Pair<Long, Long> summaryBucket) {
        JSONArray result = new JSONArray();
        result.put(summaryBucket.first);
        result.put(summaryBucket.second);
        return result;
    }
}
