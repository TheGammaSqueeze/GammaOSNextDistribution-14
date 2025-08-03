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

package com.android.adservices.service.measurement.actions;

import com.android.adservices.service.measurement.E2ETest;

import org.json.JSONObject;

/** Config used to specify configuration for URLs in registration list. */
public class UriConfig {
    private final boolean mShouldEnroll;

    public UriConfig(JSONObject uriObj) {
        mShouldEnroll = uriObj.optBoolean(E2ETest.TestFormatJsonMapping.ENROLL, true);
    }

    /** Should the URL be enrolled before the request. */
    public boolean shouldEnroll() {
        return mShouldEnroll;
    }
}
