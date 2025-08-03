/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.server.wifi.hotspot2;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.wifi.Clock;
import com.android.server.wifi.hotspot2.anqp.ANQPElement;
import com.android.server.wifi.hotspot2.anqp.Constants;
import com.android.server.wifi.hotspot2.anqp.HSWanMetricsElement;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Class for maintaining ANQP elements and managing the lifetime of the elements.
 */
public class ANQPData {
    /**
     * Entry lifetime.
     */
    @VisibleForTesting
    public static final long DATA_LIFETIME_MILLISECONDS = 3_600_000L; // One hour
    public static final long DATA_SHORT_LIFETIME_MILLISECONDS = 600_000L; // Ten minutes

    private final Clock mClock;
    private final Map<Constants.ANQPElementType, ANQPElement> mANQPElements;
    private long mExpiryTime;

    public ANQPData(Clock clock, Map<Constants.ANQPElementType, ANQPElement> anqpElements) {
        mClock = clock;
        mANQPElements = new HashMap<>();
        long dataLifetime = DATA_LIFETIME_MILLISECONDS;

        if (anqpElements != null) {
            mANQPElements.putAll(anqpElements);

            HSWanMetricsElement hsWanMetricsElement =
                    (HSWanMetricsElement) anqpElements.get(Constants.ANQPElementType.HSWANMetrics);

            if (hsWanMetricsElement != null) {
                // If the WAN Metrics ANQP-element is initialized, and signals that the AP is at
                // capacity or its link status is down, then send a new ANQP-request sooner, because
                // its status may change.
                if (hsWanMetricsElement.isElementInitialized()
                        && (hsWanMetricsElement.getStatus() != HSWanMetricsElement.LINK_STATUS_UP
                        || hsWanMetricsElement.isAtCapacity())) {
                    dataLifetime = DATA_SHORT_LIFETIME_MILLISECONDS;
                }
            }
        }
        mExpiryTime = mClock.getElapsedSinceBootMillis() + dataLifetime;
    }

    /**
     * Update an entry with post association ANQP elelemtns
     *
     * @param anqpElements ANQP elements to add
     */
    public void update(Map<Constants.ANQPElementType, ANQPElement> anqpElements) {
        mANQPElements.putAll(anqpElements);
        mExpiryTime = mClock.getElapsedSinceBootMillis() + DATA_LIFETIME_MILLISECONDS;
    }

    /**
     * Return the ANQP elements.
     *
     * @return Map of ANQP elements
     */
    public Map<Constants.ANQPElementType, ANQPElement> getElements() {
        return Collections.unmodifiableMap(mANQPElements);
    }

    /**
     * Check if this entry is expired at the specified time.
     *
     * @param at The time to check for
     * @return true if it is expired at the given time
     */
    public boolean expired(long at) {
        return mExpiryTime <= at;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(mANQPElements.size()).append(" elements, ");
        long now = mClock.getElapsedSinceBootMillis();
        sb.append(" expires in ").append(Utils.toHMS(mExpiryTime - now)).append(' ');
        sb.append(expired(now) ? 'x' : '-').append("\n");
        for (Map.Entry<Constants.ANQPElementType, ANQPElement> entry: mANQPElements.entrySet()) {
            sb.append(entry.getValue()).append("\n");
        }
        return sb.toString();
    }
}
