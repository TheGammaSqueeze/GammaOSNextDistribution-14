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
package com.android.time.cts.fake_tzps_app.tzps;

import static android.app.time.cts.shell.FakeTimeZoneProviderAppShellHelper.PROVIDER_STATE_CERTAIN;
import static android.app.time.cts.shell.FakeTimeZoneProviderAppShellHelper.PROVIDER_STATE_DISABLED;
import static android.app.time.cts.shell.FakeTimeZoneProviderAppShellHelper.PROVIDER_STATE_INITIALIZING;
import static android.app.time.cts.shell.FakeTimeZoneProviderAppShellHelper.PROVIDER_STATE_PERM_FAILED;

import android.app.time.cts.shell.FakeTimeZoneProviderAppShellHelper;
import android.os.SystemClock;
import android.service.timezone.TimeZoneProviderService;
import android.service.timezone.TimeZoneProviderStatus;
import android.service.timezone.TimeZoneProviderSuggestion;

import com.android.time.cts.fake_tzps_app.fixture.FakeTimeZoneProviderRegistry;

import java.util.List;
import java.util.Objects;

/**
 * A base class for fake implementations of {@link TimeZoneProviderService} that can be queried /
 * poked during tests. Each instance registers itself with {@link FakeTimeZoneProviderRegistry} on
 * construction to enable interaction from tests.
 */
public class FakeTimeZoneProviderService extends TimeZoneProviderService {

    private final String mId;
    private int mState = PROVIDER_STATE_DISABLED;

    protected FakeTimeZoneProviderService(String id) {
        mId = Objects.requireNonNull(id);
        FakeTimeZoneProviderRegistry.getInstance().registerFakeTimeZoneProviderService(id, this);
    }

    @Override
    public void onStartUpdates(long initializationTimeoutMillis) {
        mState = PROVIDER_STATE_INITIALIZING;
    }

    @Override
    public void onStopUpdates() {
        mState = PROVIDER_STATE_DISABLED;
    }

    // Fake behavior methods.
    public int getState() {
        return mState;
    }

    public void fakeReportUncertain(TimeZoneProviderStatus providerStatus) {
        reportUncertain(providerStatus);
        mState = FakeTimeZoneProviderAppShellHelper.PROVIDER_STATE_UNCERTAIN;
    }

    public void fakeReportUncertainLegacy() {
        reportUncertain();
        mState = FakeTimeZoneProviderAppShellHelper.PROVIDER_STATE_UNCERTAIN;
    }

    public void fakeReportPermanentFailure() {
        reportPermanentFailure(new RuntimeException("Fake permanent failure"));
        mState = PROVIDER_STATE_PERM_FAILED;
    }

    public void fakeReportSuggestion(List<String> timeZoneIds,
            TimeZoneProviderStatus providerStatus) {
        TimeZoneProviderSuggestion suggestion = new TimeZoneProviderSuggestion.Builder()
                .setTimeZoneIds(timeZoneIds)
                .setElapsedRealtimeMillis(SystemClock.elapsedRealtime())
                .build();
        reportSuggestion(suggestion, providerStatus);
        mState = PROVIDER_STATE_CERTAIN;
    }

    public void fakeReportSuggestionLegacy(List<String> timeZoneIds) {
        TimeZoneProviderSuggestion suggestion = new TimeZoneProviderSuggestion.Builder()
                .setTimeZoneIds(timeZoneIds)
                .setElapsedRealtimeMillis(SystemClock.elapsedRealtime())
                .build();
        reportSuggestion(suggestion);
        mState = PROVIDER_STATE_CERTAIN;
    }
}
