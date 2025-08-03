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

package com.android.cellbroadcastreceiver.compliancetests;

import org.json.JSONException;
import org.json.JSONObject;

public class CellBroadcastSettingTestConfig implements ITestConfig {
    private static final String SETTING_TOGGLE_AVAIL = "toggle_avail";
    private static final String SETTING_DEFAULT_VALUE = "default_value";
    private static final String SETTING_SUMMARY = "summary";

    public boolean mIsToggleAvailability = true;
    public boolean mExpectedSwitchValue;
    public String mSummary;

    public CellBroadcastSettingTestConfig(JSONObject settingsForCarrier, String settingName)
            throws JSONException {
        JSONObject object = settingsForCarrier.getJSONObject(settingName);
        String toggleAvail = object.getString(SETTING_TOGGLE_AVAIL);
        String defaultToggle = object.getString(SETTING_DEFAULT_VALUE);
        if (toggleAvail != null && toggleAvail.equals("false")) {
            mIsToggleAvailability = false;
        }
        mExpectedSwitchValue = defaultToggle != null && defaultToggle.equals("true");
        mSummary = getObjectString(object, SETTING_SUMMARY);
    }
}
