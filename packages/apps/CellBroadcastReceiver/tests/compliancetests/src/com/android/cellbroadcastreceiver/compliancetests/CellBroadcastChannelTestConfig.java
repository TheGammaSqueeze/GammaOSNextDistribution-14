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

import android.text.TextUtils;

import org.json.JSONException;
import org.json.JSONObject;

public class CellBroadcastChannelTestConfig implements ITestConfig {
    private static final String CHANNEL_DEFAULT_VALUE_FIELD = "default_value";
    private static final String CHANNEL_TITLE = "title";
    private static final String CHANNEL_FILTER_LANGUAGE = "filter_language";
    private static final String CHANNEL_ALERT_TYPE = "alert_type";
    private static final String CHANNEL_TEST_MODE = "test_mode";
    private static final String CHANNEL_DISPLAY = "display";
    private static final String CHANNEL_WARNING_TYPE = "warning_type";

    public boolean mChannelDefaultValue = true;
    public String mExpectedTitle;
    public String mFilteredLanguage;
    public boolean mIgnoreMessageByLanguageFilter;
    public boolean mFilteredLanguageBySecondLanguagePref;
    public boolean mAlertTypeIsNotification;
    public boolean mIsEnabledOnTestMode;
    public boolean mNeedDisplay = true;
    public String mWarningType;

    public CellBroadcastChannelTestConfig(JSONObject channelsObject,
            String carrierName, String channel) throws JSONException {
        JSONObject channelsForCarrier = channelsObject.getJSONObject(carrierName);
        JSONObject object = channelsForCarrier.getJSONObject(channel);
        String defaultValue = object.getString(CHANNEL_DEFAULT_VALUE_FIELD);
        if (!TextUtils.isEmpty(defaultValue) && defaultValue.equals("false")) {
            mChannelDefaultValue = false;
        }
        mExpectedTitle = object.getString(CHANNEL_TITLE);
        mFilteredLanguage = getObjectString(object, CHANNEL_FILTER_LANGUAGE);
        mIgnoreMessageByLanguageFilter = false;
        if (!TextUtils.isEmpty(mFilteredLanguage)
                && mFilteredLanguage.equals("language_setting")) {
            mIgnoreMessageByLanguageFilter = true;
        } else if (!TextUtils.isEmpty(mFilteredLanguage)
                && mFilteredLanguage.equals("second_language_pref")) {
            mFilteredLanguageBySecondLanguagePref = true;
        }
        String alertType = getObjectString(object, CHANNEL_ALERT_TYPE);
        mAlertTypeIsNotification = false;
        if (!TextUtils.isEmpty(alertType) && alertType.equals("notification")) {
            mAlertTypeIsNotification = true;
        }
        String testModeString = getObjectString(object, CHANNEL_TEST_MODE);
        if (!TextUtils.isEmpty(testModeString) && testModeString.equals("true")) {
            mIsEnabledOnTestMode = true;
        }
        String displayString = getObjectString(object, CHANNEL_DISPLAY);
        if (!TextUtils.isEmpty(displayString) && displayString.equals("false")) {
            mNeedDisplay = false;
        }
        mWarningType = getObjectString(object, CHANNEL_WARNING_TYPE);
    }
}
