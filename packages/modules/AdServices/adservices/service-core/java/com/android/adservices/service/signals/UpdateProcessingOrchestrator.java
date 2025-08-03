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
import android.annotation.NonNull;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.signals.DBProtectedSignal;
import com.android.adservices.data.signals.ProtectedSignalsDao;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.signals.evict.SignalEvictionController;
import com.android.adservices.service.signals.updateprocessors.UpdateEncoderEvent;
import com.android.adservices.service.signals.updateprocessors.UpdateEncoderEventHandler;
import com.android.adservices.service.signals.updateprocessors.UpdateOutput;
import com.android.adservices.service.signals.updateprocessors.UpdateProcessorSelector;
import com.android.internal.annotations.VisibleForTesting;


import org.json.JSONException;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Applies JSON signal updates to the DB. */
public class UpdateProcessingOrchestrator {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    @VisibleForTesting
    public static final String COLLISION_ERROR =
            "Updates JSON attempts to perform multiple operations on a single key";

    @NonNull private final ProtectedSignalsDao mProtectedSignalsDao;
    @NonNull private final UpdateProcessorSelector mUpdateProcessorSelector;
    @NonNull private final UpdateEncoderEventHandler mUpdateEncoderEventHandler;
    @NonNull private final SignalEvictionController mSignalEvictionController;

    public UpdateProcessingOrchestrator(
            @NonNull ProtectedSignalsDao protectedSignalsDao,
            @NonNull UpdateProcessorSelector updateProcessorSelector,
            @NonNull UpdateEncoderEventHandler updateEncoderEventHandler,
            @NonNull SignalEvictionController signalEvictionController) {
        Objects.requireNonNull(protectedSignalsDao);
        Objects.requireNonNull(updateProcessorSelector);
        Objects.requireNonNull(updateEncoderEventHandler);
        Objects.requireNonNull(signalEvictionController);
        mProtectedSignalsDao = protectedSignalsDao;
        mUpdateProcessorSelector = updateProcessorSelector;
        mUpdateEncoderEventHandler = updateEncoderEventHandler;
        mSignalEvictionController = signalEvictionController;
    }

    /** Takes a signal update JSON and adds/removes signals based on it. */
    public void processUpdates(
            AdTechIdentifier adtech,
            String packageName,
            Instant creationTime,
            JSONObject json,
            DevContext devContext) {
        sLogger.v("Processing signal updates for " + adtech);
        try {
            // Load the current signals, organizing them into a map for quick access
            List<DBProtectedSignal> currentSignalsList =
                    mProtectedSignalsDao.getSignalsByBuyer(adtech);
            Map<ByteBuffer, Set<DBProtectedSignal>> currentSignalsMap =
                    getCurrentSignalsMap(currentSignalsList);

            /*
             * Contains the following:
             * List of signals to add. Each update processor can append to this and the signals will
             * be added after all the processors are run.
             *
             * List of signals to remove. Each update processor can append to this and the signals
             * will be removed after all the update processors are run.
             *
             * Running set of keys interacted with, kept to ensure no key is modified twice.
             *
             * An update encoder event, in case the update processors reported an update in encoder
             * endpoint.
             */
            UpdateOutput combinedUpdates = runProcessors(json, currentSignalsMap);

            List<DBProtectedSignal> updatedSignals =
                    updateProtectedSignalInMemory(
                            adtech, packageName, creationTime, currentSignalsList, combinedUpdates);

            sLogger.v(
                    "Finished parsing JSON %d signals to add, and %d signals to remove",
                    combinedUpdates.getToAdd().size(), combinedUpdates.getToRemove().size());

            mSignalEvictionController.evict(adtech, updatedSignals, combinedUpdates);

            writeChanges(adtech, creationTime, combinedUpdates, devContext);
        } catch (JSONException e) {
            throw new IllegalArgumentException("Couldn't unpack signal updates JSON", e);
        }
    }

    private List<DBProtectedSignal> updateProtectedSignalInMemory(
            AdTechIdentifier adTech,
            String packageName,
            Instant creationTime,
            List<DBProtectedSignal> currentSignalList,
            UpdateOutput combinedOutput) {
        List<DBProtectedSignal> updatedSignalList = new ArrayList<>();

        for (DBProtectedSignal signal : currentSignalList) {
            if (!combinedOutput.getToRemove().contains(signal)) {
                updatedSignalList.add(signal);
            }
        }

        updatedSignalList.addAll(
                combinedOutput.getToAdd().stream()
                        .map(
                                builder ->
                                        builder.setBuyer(adTech)
                                                .setPackageName(packageName)
                                                .setCreationTime(creationTime)
                                                .build())
                        .collect(Collectors.toList()));
        return updatedSignalList;
    }

    /**
     * @param currentSignalsList The current signal list for a buyer
     * @return A map from keys to the set of all signals associated with that key for the given
     *     buyer.
     */
    private Map<ByteBuffer, Set<DBProtectedSignal>> getCurrentSignalsMap(
            List<DBProtectedSignal> currentSignalsList) {
        Map<ByteBuffer, Set<DBProtectedSignal>> toReturn = new HashMap<>();
        for (DBProtectedSignal signal : currentSignalsList) {
            ByteBuffer wrappedKey = ByteBuffer.wrap(signal.getKey());
            if (!toReturn.containsKey(wrappedKey)) {
                toReturn.put(wrappedKey, new HashSet<>());
            }
            toReturn.get(wrappedKey).add(signal);
        }
        return toReturn;
    }

    private UpdateOutput runProcessors(
            JSONObject json, Map<ByteBuffer, Set<DBProtectedSignal>> currentSignalsMap)
            throws JSONException {

        UpdateOutput combinedUpdates = new UpdateOutput();
        sLogger.v("Running update processors");
        // Run each of the update processors
        for (Iterator<String> iter = json.keys(); iter.hasNext(); ) {
            String key = iter.next();
            sLogger.v("Running update processor %s", key);
            UpdateOutput output =
                    mUpdateProcessorSelector
                            .getUpdateProcessor(key)
                            .processUpdates(json.get(key), currentSignalsMap);
            combinedUpdates.getToAdd().addAll(output.getToAdd());
            combinedUpdates.getToRemove().addAll(output.getToRemove());
            if (!Collections.disjoint(combinedUpdates.getKeysTouched(), output.getKeysTouched())) {
                throw new IllegalArgumentException(COLLISION_ERROR);
            }
            combinedUpdates.getKeysTouched().addAll(output.getKeysTouched());

            UpdateEncoderEvent outPutEvent = output.getUpdateEncoderEvent();
            if (outPutEvent != null) {
                combinedUpdates.setUpdateEncoderEvent(outPutEvent);
            }
        }
        return combinedUpdates;
    }

    private void writeChanges(
            AdTechIdentifier adTech,
            Instant creationTime,
            UpdateOutput combinedUpdates,
            DevContext devContext) {
        /* Modify the DB based on the output of the update processors. Might be worth skipping
         * this is both signalsToAdd and signalsToDelete are empty.
         */
        mProtectedSignalsDao.insertAndDelete(
                adTech,
                creationTime,
                combinedUpdates.getToAdd().stream()
                        .map(DBProtectedSignal.Builder::build)
                        .collect(Collectors.toList()),
                combinedUpdates.getToRemove());

        // There is a valid possibility where there is no update for encoder
        if (combinedUpdates.getUpdateEncoderEvent() != null) {
            mUpdateEncoderEventHandler.handle(
                    adTech, combinedUpdates.getUpdateEncoderEvent(), devContext);
        }
    }
}
