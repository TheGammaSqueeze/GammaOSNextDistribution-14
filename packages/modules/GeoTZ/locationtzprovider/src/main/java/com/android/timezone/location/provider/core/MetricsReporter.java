/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.timezone.location.provider.core;

import static com.android.timezone.location.provider.core.OfflineLocationTimeZoneDelegate.ListenModeEnum;

/**
 * An interface that abstracts the metrics reporting infrastructure being used and what metrics
 * are reported.
 */
public abstract class MetricsReporter {

    public static final int LISTENING_STOPPED_REASON_TIMED_OUT = 1;
    public static final int LISTENING_STOPPED_REASON_CANCELLED = 2;
    public static final int LISTENING_STOPPED_REASON_LOCATION_OBTAINED = 3;

    /** Reports a period spent listening for location. */
    public void reportLocationListeningCompletedEvent(
            @ListenModeEnum int listenMode,
            long requestedDurationMillis, long actualDurationMillis,
            int listeningStoppedReason) {}
}
