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

package com.android.timezone.location.provider;

import android.content.Context;
import android.service.timezone.TimeZoneProviderService;

import androidx.annotation.NonNull;

import com.android.timezone.location.provider.core.Environment;
import com.android.timezone.location.provider.core.OfflineLocationTimeZoneDelegate;
import com.android.timezone.location.provider.core.TimeZoneProviderResult;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.time.Duration;

/**
 * A Location Time Zone Provider implementation that uses only Android public SDK APIs and on-device
 * data to determine the current time zone(s) for a location.
 *
 * <p>This implementation can be deployed to run as either the current user (e.g. it could run in a
 * user-specific process like GMS core), or always as the system user (e.g. it can run in a single
 * user process like system server). It relies on being stopped by the system server when the user
 * changes. No location state is retained when the provider is stopped.
 *
 * <p>See {@link OfflineLocationTimeZoneDelegate} for implementation details.
 *
 * <p>The provider is configured via a "offlineltzprovider.properties" resource file. See
 * {@link EnvironmentImpl} for details.
 */
public final class OfflineLocationTimeZoneProviderService extends TimeZoneProviderService {

    private static final String ATTRIBUTION_TAG = "OfflineLocationTimeZoneProviderService";

    // NonNull after onCreate()
    private OfflineLocationTimeZoneDelegate mDelegate;

    @Override
    public void onCreate() {
        Context attributionContext = createAttributionContext(ATTRIBUTION_TAG);
        Environment environment = new EnvironmentImpl(
                attributionContext, this::reportTimeZoneProviderEvent);
        mDelegate = OfflineLocationTimeZoneDelegate.create(environment);
    }

    @Override
    public void onDestroy() {
        mDelegate.onDestroy();
    }

    @Override
    public void onStartUpdates(long initializationTimeoutMillis) {
        mDelegate.onStartUpdates(Duration.ofMillis(initializationTimeoutMillis));
    }

    private void reportTimeZoneProviderEvent(
            @NonNull TimeZoneProviderResult timeZoneProviderResult) {
        switch (timeZoneProviderResult.getType()) {
            case TimeZoneProviderResult.RESULT_TYPE_SUGGESTION: {
                reportSuggestion(timeZoneProviderResult.getSuggestion());
                break;
            }
            case TimeZoneProviderResult.RESULT_TYPE_UNCERTAIN: {
                reportUncertain();
                break;
            }
            case TimeZoneProviderResult.RESULT_TYPE_PERMANENT_FAILURE: {
                reportPermanentFailure(timeZoneProviderResult.getFailureCause());
                break;
            }
            default: {
                throw new IllegalArgumentException("Unknown result type=" + timeZoneProviderResult);
            }
        }
    }

    @Override
    public void onStopUpdates() {
        mDelegate.onStopUpdates();
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        mDelegate.dump(writer);
    }
}
