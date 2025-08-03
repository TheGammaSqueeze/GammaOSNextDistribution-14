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
package com.android.geotz.app;

import com.android.timezone.location.provider.core.MetricsReporter;
import com.android.timezone.location.provider.core.OfflineLocationTimeZoneDelegate.ListenModeEnum;

/**
 * The implementation of {@link MetricsReporter} used for the app deployment
 * of {@link com.android.timezone.location.provider.OfflineLocationTimeZoneProviderService}.
 */
public class MetricsReporterImpl extends MetricsReporter {

    @Override
    public void reportLocationListeningCompletedEvent(@ListenModeEnum int listenMode,
            long requestedDurationMillis, long actualTimeListeningMillis,
            int listeningStoppedReason) {
        // No-op
    }
}
