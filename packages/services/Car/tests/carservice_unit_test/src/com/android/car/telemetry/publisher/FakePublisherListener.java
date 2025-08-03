/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.car.telemetry.publisher;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.car.telemetry.TelemetryProto;

import java.util.List;

/**
 * Test implementation of
 * {@link com.android.car.telemetry.publisher.AbstractPublisher.PublisherListener}.
 * This class is used for all PublisherTests.
 */
public class FakePublisherListener implements AbstractPublisher.PublisherListener {
    // Default is null, value is set in onConfigFinished
    public TelemetryProto.MetricsConfig mFinishedConfig;

    // Default is null, values are set in onPublisherFailure
    public List<TelemetryProto.MetricsConfig> mFailedConfigs;
    public Throwable mPublisherFailure;

    @Override
    public void onPublisherFailure(@NonNull List<TelemetryProto.MetricsConfig> affectedConfigs,
            @Nullable Throwable error) {
        mFailedConfigs = affectedConfigs;
        mPublisherFailure = error;
    }

    @Override
    public void onConfigFinished(@NonNull TelemetryProto.MetricsConfig metricsConfig) {
        mFinishedConfig = metricsConfig;
    }
}
