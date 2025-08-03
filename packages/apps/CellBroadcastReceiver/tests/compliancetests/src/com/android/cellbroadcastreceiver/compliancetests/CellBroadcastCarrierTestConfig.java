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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class CellBroadcastCarrierTestConfig implements ITestConfig {
    private static final String CARRIER_MCCMNC_FIELD = "mccmnc";
    private static final String CARRIER_DISABLE_NAVIGATION = "disable_navigation";
    private static final String CARRIER_FULLSCREEN = "fullscreen";
    private static final String CARRIER_LANGUAGE = "language";
    private static final String CARRIER_CHECK_SETTING_MAIN_LANG = "check_setting_with_main_lang";

    public String mMccMnc;
    public boolean mDisableNavigation;
    public boolean mNeedFullScreen;
    public String mLanguageTag;
    public boolean mCheckSettingWithMainLanguage = true;

    public CellBroadcastCarrierTestConfig(JSONObject carriersObject, String carrierName)
            throws JSONException {
        JSONObject carrierObject = carriersObject.getJSONObject(carrierName);
        JSONArray mccMncList = carrierObject.getJSONArray(CARRIER_MCCMNC_FIELD);
        mMccMnc = mccMncList.getString(0);
        String disableNavigationString =
                getObjectString(carrierObject, CARRIER_DISABLE_NAVIGATION);
        mDisableNavigation = false;
        if (!TextUtils.isEmpty(disableNavigationString) && disableNavigationString.equals("true")) {
            mDisableNavigation = true;
        }
        String fullScreenString = getObjectString(carrierObject, CARRIER_FULLSCREEN);
        mNeedFullScreen = false;
        if (!TextUtils.isEmpty(fullScreenString) && fullScreenString.equals("true")) {
            mNeedFullScreen = true;
        }
        mLanguageTag = getObjectString(carrierObject, CARRIER_LANGUAGE);
        String checkSettingWithMainLanguageTag = getObjectString(carrierObject,
                CARRIER_CHECK_SETTING_MAIN_LANG);
        if (checkSettingWithMainLanguageTag != null
                && checkSettingWithMainLanguageTag.equals("false")) {
            mCheckSettingWithMainLanguage = false;
        }
    }
}
