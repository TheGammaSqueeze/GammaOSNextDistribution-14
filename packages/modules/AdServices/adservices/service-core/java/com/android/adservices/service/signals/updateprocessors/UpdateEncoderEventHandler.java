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

import android.adservices.common.AdTechIdentifier;
import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.android.adservices.data.signals.DBEncoderEndpoint;
import com.android.adservices.data.signals.EncoderEndpointsDao;
import com.android.adservices.data.signals.EncoderLogicHandler;
import com.android.adservices.data.signals.ProtectedSignalsDatabase;
import com.android.adservices.service.devapi.DevContext;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FluentFuture;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Takes appropriate action be it update or download encoder based on {@link UpdateEncoderEvent} */
public class UpdateEncoderEventHandler {
    @NonNull private final EncoderEndpointsDao mEncoderEndpointsDao;
    @NonNull private final EncoderLogicHandler mEncoderLogicHandler;
    private List<Observer> mUpdatesObserver;

    @VisibleForTesting
    public UpdateEncoderEventHandler(
            @NonNull EncoderEndpointsDao encoderEndpointsDao,
            @NonNull EncoderLogicHandler encoderLogicHandler) {
        Objects.requireNonNull(encoderEndpointsDao);
        Objects.requireNonNull(encoderLogicHandler);
        mEncoderEndpointsDao = encoderEndpointsDao;
        mEncoderLogicHandler = encoderLogicHandler;
        mUpdatesObserver = new ArrayList<>();
    }

    public UpdateEncoderEventHandler(@NonNull Context context) {
        this(
                ProtectedSignalsDatabase.getInstance(context).getEncoderEndpointsDao(),
                new EncoderLogicHandler(context));
    }

    /**
     * Handles different type of {@link UpdateEncoderEvent} based on the event type
     *
     * @param buyer Ad tech responsible for this update event
     * @param event an {@link UpdateEncoderEvent}
     * @param devContext development context used for testing network calls
     * @throws IllegalArgumentException if uri is null for registering encoder or the event type is
     *     not recognized
     */
    public void handle(
            @NonNull AdTechIdentifier buyer,
            @NonNull UpdateEncoderEvent event,
            @NonNull DevContext devContext)
            throws IllegalArgumentException {
        Objects.requireNonNull(buyer);
        Objects.requireNonNull(event);
        Objects.requireNonNull(devContext);

        switch (event.getUpdateType()) {
            case REGISTER:
                Uri uri = event.getEncoderEndpointUri();
                if (uri == null) {
                    throw new IllegalArgumentException(
                            "Uri cannot be null for event: " + event.getUpdateType());
                }
                DBEncoderEndpoint endpoint =
                        DBEncoderEndpoint.builder()
                                .setBuyer(buyer)
                                .setCreationTime(Instant.now())
                                .setDownloadUri(uri)
                                .build();

                DBEncoderEndpoint previousRegisteredEncoder =
                        mEncoderEndpointsDao.getEndpoint(buyer);
                mEncoderEndpointsDao.registerEndpoint(endpoint);

                if (previousRegisteredEncoder == null) {
                    // We immediately download and update if no previous encoder existed
                    FluentFuture<Boolean> downloadAndUpdate =
                            mEncoderLogicHandler.downloadAndUpdate(buyer, devContext);
                    notifyObservers(buyer, event.getUpdateType().toString(), downloadAndUpdate);
                }
                break;
            default:
                throw new IllegalArgumentException(
                        "Unexpected value for update event type: " + event.getUpdateType());
        }
    }

    /**
     * @param observer that will be notified of the update events
     */
    public void addObserver(Observer observer) {
        mUpdatesObserver.add(observer);
    }

    /**
     * @param buyer buyer for which the update happens
     * @param eventType the type of event that got completed
     * @param event the actual event result
     */
    public void notifyObservers(AdTechIdentifier buyer, String eventType, FluentFuture<?> event) {
        mUpdatesObserver.parallelStream().forEach(o -> o.update(buyer, eventType, event));
    }

    /**
     * An observer interface that helps subscribe to the update encoder events. Helps get notified
     * when an event gets completed rather than polling the DB state.
     */
    public interface Observer {
        /**
         * @param buyer buyer for which the update happens
         * @param eventType the type of event that got completed
         * @param event the actual event result
         */
        void update(AdTechIdentifier buyer, String eventType, FluentFuture<?> event);
    }
}
