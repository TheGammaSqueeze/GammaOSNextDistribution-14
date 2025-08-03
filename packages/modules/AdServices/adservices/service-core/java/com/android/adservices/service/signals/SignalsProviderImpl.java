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

package com.android.adservices.service.signals;

import android.adservices.common.AdTechIdentifier;

import androidx.annotation.NonNull;

import com.android.adservices.data.signals.DBProtectedSignal;
import com.android.adservices.data.signals.ProtectedSignalsDao;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Reads signals from Signals DB and collects them into a map with base64 encoded String keys */
public class SignalsProviderImpl implements SignalsProvider {

    @NonNull private ProtectedSignalsDao mProtectedSignalsDao;

    public SignalsProviderImpl(@NonNull ProtectedSignalsDao protectedSignalsDao) {
        mProtectedSignalsDao = protectedSignalsDao;
    }

    @Override
    public Map<String, List<ProtectedSignal>> getSignals(AdTechIdentifier buyer) {
        List<DBProtectedSignal> dbSignals = mProtectedSignalsDao.getSignalsByBuyer(buyer);

        Map<String, List<ProtectedSignal>> signalsMap = new HashMap<>();

        for (DBProtectedSignal dbSignal : dbSignals) {
            String base64EncodedKey = Base64.getEncoder().encodeToString(dbSignal.getKey());

            ProtectedSignal protectedSignal =
                    ProtectedSignal.builder()
                            .setBase64EncodedValue(
                                    Base64.getEncoder().encodeToString(dbSignal.getValue()))
                            .setPackageName(dbSignal.getPackageName())
                            .setCreationTime(dbSignal.getCreationTime())
                            .build();

            signalsMap.putIfAbsent(base64EncodedKey, new ArrayList<>());
            signalsMap.get(base64EncodedKey).add(protectedSignal);
        }
        return signalsMap;
    }
}
