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

import org.json.JSONArray;
import org.json.JSONException;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Set;

/**
 * Removes the signal for a key.
 *
 * <p>The value of this is a list of base 64 strings corresponding to the keys of the signals that
 * should be deleted.
 */
public class Remove implements UpdateProcessor {

    private static final String REMOVE = "remove";

    @Override
    public String getName() {
        return REMOVE;
    }

    @Override
    public UpdateOutput processUpdates(
            Object updates, Map<ByteBuffer, Set<DBProtectedSignal>> current) throws JSONException {
        UpdateOutput toReturn = new UpdateOutput();
        JSONArray updatesArray = UpdateProcessorUtils.castToJSONArray(REMOVE, updates);
        for (int i = 0; i < updatesArray.length(); i++) {
            ByteBuffer key = UpdateProcessorUtils.decodeKey(REMOVE, updatesArray.getString(i));
            UpdateProcessorUtils.touchKey(key, toReturn.getKeysTouched());
            // Remove all signals for the key
            if (current.containsKey(key)) {
                toReturn.getToRemove().addAll(current.get(key));
            }
        }
        return toReturn;
    }
}
