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

package com.android.adservices.service.measurement.actions;

import android.net.Uri;

import com.android.adservices.service.measurement.E2ETest.TestFormatJsonMapping;

import org.json.JSONException;
import org.json.JSONObject;

public final class InstallApp implements Action {
    // For multiple runs, we may cycle through the list of responses multiple times for each uri.
    public final Uri mUri;
    public final long mTimestamp;

    public InstallApp(JSONObject obj) throws JSONException {
        mUri = Uri.parse(obj.getString(TestFormatJsonMapping.INSTALLS_URI_KEY));
        mTimestamp = obj.getLong(TestFormatJsonMapping.INSTALLS_TIMESTAMP_KEY);
    }

    @Override
    public long getComparable() {
        return mTimestamp;
    }
}
