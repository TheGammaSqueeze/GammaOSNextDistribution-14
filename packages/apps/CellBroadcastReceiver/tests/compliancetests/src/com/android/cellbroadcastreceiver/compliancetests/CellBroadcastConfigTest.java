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

import static org.junit.Assert.assertEquals;

import android.text.TextUtils;
import android.util.ArraySet;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Iterator;
import java.util.Set;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

@RunWith(JUnitParamsRunner.class)
public class CellBroadcastConfigTest extends CellBroadcastBaseTest {
    private static final String TAG = "CellBroadcastConfigTest";

    @Test
    @Parameters(method = "paramsCarrierAndMccMncForTest")
    public void testCellBroadcastRange(String carrierName, String mccMnc) throws Throwable {
        logd("CellBroadcastConfigTest#testCellBroadcastRange");

        setSimInfo(carrierName, mccMnc);

        logd("Check Broadcast Channel Configs");
        Set<Integer> outputConfigs = sMockModemManager.getGsmBroadcastConfig(sSlotId);
        verifyOutput(outputConfigs, carrierName);
    }

    private void verifyOutput(Set<Integer> outputSet, String carrierName) throws JSONException {
        logd("verifyOutput");

        JSONObject channelsForCarrier = sChannelsObject.getJSONObject(carrierName);
        Set<Integer> expectedChannels = new ArraySet<>();
        for (Iterator<String> iterator = channelsForCarrier.keys(); iterator.hasNext();) {
            String channelId = iterator.next();
            JSONObject object = channelsForCarrier.getJSONObject(channelId);
            String defaultValue = object.getString(CHANNEL_DEFAULT_VALUE_FIELD);
            if (!TextUtils.isEmpty(defaultValue) && defaultValue.equals("false")) {
                logd("default configuration is off, let's skip " + channelId);
                // skip
            } else {
                logd("Expected channel " + channelId);
                Integer i = Integer.parseInt(channelId);
                String endChannel = null;
                try {
                    endChannel = object.getString("end_channel");
                } catch (Exception JSONException) {
                }
                if (TextUtils.isEmpty(endChannel)) {
                    expectedChannels.add(i);
                } else {
                    logd("endChannel " + endChannel);
                    Integer end = Integer.parseInt(endChannel);
                    for (int j = i.intValue(); j <= end; j++) {
                        expectedChannels.add(j);
                    }
                }
            }
        }

        assertEquals("Check Channel Configs for " + carrierName,
                expectedChannels, outputSet);
    }
}
