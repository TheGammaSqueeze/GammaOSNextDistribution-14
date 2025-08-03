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

package com.android.adservices.service.signals.updateprocessors;

import com.android.adservices.data.signals.DBProtectedSignal;
import com.android.internal.annotations.VisibleForTesting;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Appends a new signal/signals to a time series of signals, removing the oldest signals to make
 * room for the new ones if the size of the series exceeds the given maximum.
 *
 * <p>The value for this is a JSON object where the JSON keys are base 64 strings corresponding to
 * the signal key to append to and the values are objects with two fields: "values" and
 * "maxSignals". "values" is a list of base 64 strings corresponding to signal values to append to
 * the time series. "maxSignals" is the maximum number of values that are allowed in this
 * timeseries. If the current number of signals associated with the key exceeds maxSignals the
 * oldest signals will be removed. Note that you can append to a key added by put.
 */
public class Append implements UpdateProcessor {
    @VisibleForTesting
    public static final String TOO_MANY_SIGNALS_ERROR =
            "Attempting to append %d than with a max_signals of %d";

    private static final String MAX_SIGNALS = "max_signals";
    private static final String VALUES = "values";

    private static final String APPEND = "append";

    @Override
    public String getName() {
        return APPEND;
    }

    @Override
    public UpdateOutput processUpdates(
            Object updates, Map<ByteBuffer, Set<DBProtectedSignal>> current) throws JSONException {
        UpdateOutput toReturn = new UpdateOutput();
        JSONObject updatesObject = UpdateProcessorUtils.castToJSONObject(APPEND, updates);
        // Iterate over the keys.
        for (Iterator<String> iter = updatesObject.keys(); iter.hasNext(); ) {
            String stringKey = iter.next();
            ByteBuffer key = UpdateProcessorUtils.decodeKey(APPEND, stringKey);
            JSONObject update = updatesObject.getJSONObject(stringKey);
            processKey(key, update, current, toReturn);
        }
        return toReturn;
    }

    /** Process the update for one key. */
    private void processKey(
            ByteBuffer key,
            JSONObject update,
            Map<ByteBuffer, Set<DBProtectedSignal>> current,
            UpdateOutput toReturn)
            throws JSONException {
        UpdateProcessorUtils.touchKey(key, toReturn.getKeysTouched());
        int maxSignals = update.getInt(MAX_SIGNALS);
        JSONArray values = update.getJSONArray(VALUES);
        // Check that the JSON isn't trying to add more than it's maximum allowed signals
        if (values.length() > maxSignals) {
            throw new IllegalArgumentException(
                    String.format(TOO_MANY_SIGNALS_ERROR, values.length(), maxSignals));
        }
        // Delete enough signals to make room for the new ones.
        deleteSignals(key, maxSignals, values, current, toReturn);

        // Add all the signals
        addSignals(UpdateProcessorUtils.getByteArrayFromBuffer(key), values, toReturn);
    }

    /** Delete excess signals to make room for the new ones. */
    private void deleteSignals(
            ByteBuffer key,
            int maxSignals,
            JSONArray values,
            Map<ByteBuffer, Set<DBProtectedSignal>> current,
            UpdateOutput toReturn) {
        int leftoverSpace = maxSignals - values.length();
        if (current.containsKey(key)) {
            List<DBProtectedSignal> timeSeries = new ArrayList<>(current.get(key));
            if (timeSeries.size() > leftoverSpace) {
                timeSeries.sort(Comparator.comparing(DBProtectedSignal::getCreationTime));
                // Add the oldest signals that don't fit in the leftover space to the remove list.
                toReturn.getToRemove()
                        .addAll(timeSeries.subList(0, timeSeries.size() - leftoverSpace));
            }
        }
    }

    /** Add all the new signals. */
    private void addSignals(byte[] key, JSONArray values, UpdateOutput toReturn)
            throws JSONException {
        // Add the new signals.
        for (int i = 0; i < values.length(); i++) {
            DBProtectedSignal.Builder newSignalBuilder =
                    DBProtectedSignal.builder()
                            .setKey(key)
                            .setValue(
                                    UpdateProcessorUtils.decodeValue(APPEND, values.getString(i)));
            toReturn.getToAdd().add(newSignalBuilder);
        }
    }
}
