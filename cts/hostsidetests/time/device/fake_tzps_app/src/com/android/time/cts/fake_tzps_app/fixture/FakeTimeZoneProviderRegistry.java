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
package com.android.time.cts.fake_tzps_app.fixture;

import com.android.time.cts.fake_tzps_app.tzps.FakeTimeZoneProviderService;

import java.util.HashMap;
import java.util.Map;

/**
 * A singleton registry of fake {@link android.service.timezone.TimeZoneProviderService} instances.
 */
public class FakeTimeZoneProviderRegistry {

    private static final FakeTimeZoneProviderRegistry sInstance =
            new FakeTimeZoneProviderRegistry();

    private final Map<String, FakeTimeZoneProviderService> fakeTimeZoneProviderServiceMap =
            new HashMap<>();

    private FakeTimeZoneProviderRegistry() {
    }

    public static FakeTimeZoneProviderRegistry getInstance() {
        return sInstance;
    }

    public synchronized void registerFakeTimeZoneProviderService(String id,
            FakeTimeZoneProviderService fakeTimeZoneProviderService) {
        fakeTimeZoneProviderServiceMap.put(id, fakeTimeZoneProviderService);
    }

    public synchronized FakeTimeZoneProviderService getFakeTimeZoneProviderService(String id) {
        return fakeTimeZoneProviderServiceMap.get(id);
    }
}
