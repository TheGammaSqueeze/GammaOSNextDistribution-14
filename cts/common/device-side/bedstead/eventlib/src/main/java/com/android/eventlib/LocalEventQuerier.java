/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.eventlib;

import android.content.Context;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;


/**
 * Implementation of {@link EventQuerier} which queries data about the current package.
 */
public class LocalEventQuerier<E extends Event, F extends EventLogsQuery> implements EventQuerier<E>, Events.EventListener {
    private final EventLogsQuery<E, F> mEventLogsQuery;
    private final Events mEvents;
    private final BlockingDeque<Event> mFetchedEvents;

    LocalEventQuerier(Context context, EventLogsQuery<E, F> eventLogsQuery) {
        mEventLogsQuery = eventLogsQuery;
        mEvents = Events.getInstance(context, /* needsHistory= */ true);
        mFetchedEvents = new LinkedBlockingDeque<>(mEvents.registerEventListener(this));
    }

    @Override
    public E poll(Instant earliestLogTime, Duration timeout) {
        return poll(earliestLogTime, timeout, /* skip= */ 0);
    }

    /**
     * Poll for a result, skipping the first {@code skip} matching results.
     *
     * <p>See {@link #poll(Instant, Duration)}.
     */
    public E poll(Instant earliestLogTime, Duration timeout, int skip) {
        Instant endTime = Instant.now().plus(timeout);
        while (true) {
            Event event = null;
            try {
                Duration remainingTimeout = Duration.between(Instant.now(), endTime);
                event = mFetchedEvents.pollFirst(remainingTimeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                return null;
            }

            if (event == null) {
                // Timed out waiting for event
                return null;
            }

            if (mEventLogsQuery.eventClass().isInstance(event)) {
                if (event.mTimestamp.isBefore(earliestLogTime)) {
                    continue;
                }

                E typedEvent = (E) event;
                if (mEventLogsQuery.filterAll(typedEvent)) {
                    if (skip > 0) {
                        skip--;
                        continue;
                    }

                    return typedEvent;
                }
            }
        }
    }

    @Override
    public void onNewEvent(Event event) {
        mFetchedEvents.addLast(event);
    }
}
